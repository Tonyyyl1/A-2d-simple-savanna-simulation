package savanna;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Terrain-aware grass biomass (plan v3.0 M1-1).
 *
 * <p>Each cell holds biomass {@code B} in {@code [0, K(terrain)]}. Growth is
 * deterministic logistic regrowth plus a small capacity-proportional floor,
 * scaled by weather; it consumes no ecology random numbers. Waterholes have
 * zero capacity. Herbivores only eat in the cell they occupy at step start.
 * The running ledger satisfies
 * {@code initial + grown == current + consumed}.</p>
 */
public class GrasslandFoodSystem implements FoodSystem
{
    /** Growth rates and the capacity scale live in {@link EcologyParameters}. */
    static final double INITIAL_FILL = 0.75;

    private final TerrainMap terrain;
    private final double[][] capacity;
    private final double[][] grass;
    private final int passableCells;
    private final double initialBiomass;
    private double grownBiomass;
    private double consumedBiomass;
    private double averageFoodLevel;

    public GrasslandFoodSystem(TerrainMap terrain)
    {
        if(terrain == null) throw new IllegalArgumentException("terrain is required");
        this.terrain = terrain;
        int depth = terrain.getDepth();
        int width = terrain.getWidth();
        capacity = new double[depth][width];
        grass = new double[depth][width];
        int passable = 0;
        double total = 0.0;
        double scale = EcologyParameters.current()
            .get(EcologyParameters.Key.GRASS_CAPACITY_SCALE);
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                double cellCapacity = capacityOf(
                    terrain.getTerrainAt(new Location(row, col))) * scale;
                capacity[row][col] = cellCapacity;
                grass[row][col] = cellCapacity * INITIAL_FILL;
                total += grass[row][col];
                if(cellCapacity > 0.0) passable++;
            }
        }
        passableCells = Math.max(1, passable);
        initialBiomass = total;
        averageFoodLevel = total / passableCells;
    }

    /** Unscaled carrying capacity of one cell; plan v3.0 D3 table. */
    static double capacityOf(TerrainType type)
    {
        if(type == null) return 0.0;
        switch(type) {
            case GRASSLAND: return 1.00;
            case BUSH: return 0.70;
            case OPEN_PLAIN: return 0.50;
            case DRY_SOIL: return 0.20;
            case WATERHOLE:
            default: return 0.0;
        }
    }

    public void grow(SimulationContext context, Field field)
    {
        double weather = context.getWeatherSystem().getPlantGrowthModifier();
        EcologyParameters parameters = EcologyParameters.current();
        double growthRate = parameters.get(EcologyParameters.Key.GRASS_GROWTH_RATE);
        double floorRate = parameters.get(EcologyParameters.Key.GRASS_FLOOR_RATE);
        double total = 0.0;
        double grown = 0.0;
        for(int row = 0; row < grass.length; row++) {
            for(int col = 0; col < grass[row].length; col++) {
                double cellCapacity = capacity[row][col];
                if(cellCapacity <= 0.0) continue;
                double biomass = grass[row][col];
                double delta = weather * (growthRate * biomass *
                    (1.0 - biomass / cellCapacity) +
                    floorRate * cellCapacity);
                double next = Math.max(0.0, Math.min(cellCapacity, biomass + delta));
                grown += next - biomass;
                grass[row][col] = next;
                total += next;
            }
        }
        grownBiomass += grown;
        averageFoodLevel = total / passableCells;
    }

    public Location chooseGrazingLocation(SavannahAnimal herbivore,
                                          List<Location> locations,
                                          SimulationContext context)
    {
        if(locations.isEmpty()) {
            return herbivore.getLocation();
        }
        Location best = locations.get(0);
        double bestFood = grazingScore(herbivore, best, context);
        for(Location location : locations) {
            double food = grazingScore(herbivore, location, context);
            if(food > bestFood) {
                best = location;
                bestFood = food;
            }
        }
        return best;
    }

    private double grazingScore(SavannahAnimal herbivore, Location location,
                                SimulationContext context)
    {
        double food = getFoodLevelAt(location);
        double habitat = HabitatPreferences.movementModifier(herbivore,
            context.getTerrainMap().getTerrainAt(location), context);
        return food * habitat;
    }

    /**
     * Eats at {@code location}, which callers must pass as the herbivore's
     * step-start cell; grass in a cell the animal has not reached is never
     * consumed.
     */
    public void feedHerbivoreAt(SavannahAnimal herbivore, Location location, SimulationContext context)
    {
        if(herbivore.getProfile().isPredator() || location == null) {
            return;
        }
        EcologyParameters parameters = EcologyParameters.current();
        double satiation = parameters.get(EcologyParameters.Key.GRAZING_SATIATION);
        int maxFood = herbivore.getProfile().getMaxFoodLevel();
        if(satiation > 0.0 && herbivore.getFoodLevel() >= satiation * maxFood) {
            return;
        }
        herbivore.spendStamina(SavannahAnimal.STAMINA_GRAZE_COST);
        double grazingRate = herbivore.getProfile().getGrazingRate();
        if(herbivore.isInfected()) {
            grazingRate *= 0.82;
        }
        grazingRate *= 1.0 + herbivore.getSurvivalPressure() * 0.45;
        // C-1 lever 5: the same grass is converted into more (or less) food on the
        // preferred terrain; lever 1: the bite shrinks so the stomach is not overfilled.
        double efficiency = 1.0;
        double niche = parameters.get(EcologyParameters.Key.GRAZING_NICHE_PARTITION);
        if(niche > 0.0) {
            TerrainType here = context.getTerrainMap().getTerrainAt(location);
            efficiency = here == preferredTerrain(herbivore.getProfile().getName())
                ? 1.0 + niche : 1.0 - niche;
        }
        double bite = 1.0;
        if(satiation > 0.0) {
            double need = maxFood - herbivore.getFoodLevel();
            bite = Math.min(1.0, need / Math.max(1.0,
                herbivore.getProfile().getPlantFoodValue() * efficiency));
        }
        grazingRate *= bite;
        double available = grass[location.row()][location.col()];
        double eaten = Math.min(available, grazingRate);
        double fullFill = parameters.get(EcologyParameters.Key.GRAZING_FULL_INTAKE_FILL);
        if(fullFill > 0.0) {
            // N1: the thinner the grass, the smaller the bite, and the refuge is never taken.
            double cellCapacity = capacity[location.row()][location.col()];
            double refuge = parameters.get(EcologyParameters.Key.GRAZING_REFUGE_FILL);
            double fill = cellCapacity <= 0.0 ? 0.0 : available / cellCapacity;
            double intake = fullFill <= refuge ? (fill > refuge ? 1.0 : 0.0)
                : Math.max(0.0, Math.min(1.0, (fill - refuge) / (fullFill - refuge)));
            eaten = Math.max(0.0, Math.min(available - refuge * cellCapacity,
                grazingRate * intake));
        }
        grass[location.row()][location.col()] = available - eaten;
        consumedBiomass += eaten;
        int foodGained = (int)Math.round((eaten / Math.max(0.01, grazingRate)) * bite *
                                         herbivore.getProfile().getPlantFoodValue() * efficiency);
        if(foodGained > 0) {
            herbivore.feed(foodGained);
            context.recordEvent(SimulationEvent.graze(context.getStep(),
                                                      herbivore, location));
        }
    }

    private static TerrainType preferredTerrain(String species)
    {
        if(SpeciesRegistry.ZEBRA.equals(species)) return TerrainType.GRASSLAND;
        if(SpeciesRegistry.BUFFALO.equals(species)) return TerrainType.BUSH;
        if(SpeciesRegistry.GAZELLE.equals(species)) return TerrainType.OPEN_PLAIN;
        return null;
    }

    /** Frame export: round(255 x grass / capacity) per cell, row-major; 0 where capacity is 0. */
    public byte[] quantizedFill()
    {
        int depth = grass.length;
        int width = depth == 0 ? 0 : grass[0].length;
        byte[] fill = new byte[depth * width];
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                double cap = capacity[row][col];
                int value = cap <= 0.0 ? 0 : (int)Math.round(255.0 * grass[row][col] / cap);
                fill[row * width + col] = (byte)Math.max(0, Math.min(255, value));
            }
        }
        return fill;
    }

    public double getFoodLevelAt(Location location)
    {
        if(location == null) {
            return 0.0;
        }
        return grass[location.row()][location.col()];
    }

    public double getCapacityAt(Location location)
    {
        return location == null ? 0.0 : capacity[location.row()][location.col()];
    }

    public double getLocalFill(Location center, int range)
    {
        if(center == null) return 0.0;
        double biomass = 0.0;
        double cellCapacity = 0.0;
        int lastRow = Math.min(grass.length - 1, center.row() + range);
        int lastCol = Math.min(grass[0].length - 1, center.col() + range);
        for(int row = Math.max(0, center.row() - range); row <= lastRow; row++) {
            for(int col = Math.max(0, center.col() - range); col <= lastCol; col++) {
                biomass += grass[row][col];
                cellCapacity += capacity[row][col];
            }
        }
        return cellCapacity <= 0.0 ? 0.0 : biomass / cellCapacity;
    }

    /** Mean biomass over cells that can hold grass. */
    public double getAverageFoodLevel()
    {
        return averageFoodLevel;
    }

    public double getInitialBiomass() { return initialBiomass; }
    public double getGrownBiomass() { return grownBiomass; }
    public double getConsumedBiomass() { return consumedBiomass; }

    public double getTotalBiomass()
    {
        double total = 0.0;
        for(double[] row : grass) {
            for(double value : row) total += value;
        }
        return total;
    }

    public TerrainMap getTerrain() { return terrain; }

    /** Deterministic digest of the biomass layer for fingerprints. */
    public String legacyStateHash()
    {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for(int row = 0; row < grass.length; row++) {
                for(int col = 0; col < grass[row].length; col++) {
                    long bits = Double.doubleToLongBits(grass[row][col]);
                    digest.update((row + "," + col + ":" + bits + "\n")
                        .getBytes(StandardCharsets.UTF_8));
                }
            }
            byte[] bytes = digest.digest();
            return HexEncoding.lowercase(bytes);
        }
        catch(NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }
}
