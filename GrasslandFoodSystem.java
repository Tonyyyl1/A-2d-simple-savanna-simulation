import java.util.List;
import java.util.Random;

/**
 * Grass grows in every cell and is eaten by herbivores.
 */
public class GrasslandFoodSystem implements FoodSystem
{
    private static final double MAX_GRASS = 1.0;
    private static final double BASE_GROWTH = 0.045;

    private final Random rand;
    private final double[][] grass;
    private double averageFoodLevel;

    public GrasslandFoodSystem(int depth, int width)
    {
        this(depth, width, Randomizer.getRandom());
    }

    public GrasslandFoodSystem(int depth, int width, Random rand)
    {
        this.rand = rand;
        grass = new double[depth][width];
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                grass[row][col] = 0.55 + rand.nextDouble() * 0.40;
            }
        }
        averageFoodLevel = 0.75;
    }

    public void grow(SimulationContext context, Field field)
    {
        double growth = BASE_GROWTH * context.getWeatherSystem().getPlantGrowthModifier();
        double total = 0.0;
        for(int row = 0; row < field.getDepth(); row++) {
            for(int col = 0; col < field.getWidth(); col++) {
                double localGrowth = growth * (0.75 + rand.nextDouble() * 0.50);
                grass[row][col] = Math.min(MAX_GRASS, grass[row][col] + localGrowth);
                total += grass[row][col];
            }
        }
        averageFoodLevel = total / (field.getDepth() * field.getWidth());
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

    public void feedHerbivoreAt(SavannahAnimal herbivore, Location location, SimulationContext context)
    {
        if(herbivore.getProfile().isPredator() || location == null) {
            return;
        }
        herbivore.spendStamina(SavannahAnimal.STAMINA_GRAZE_COST);
        double grazingRate = herbivore.getProfile().getGrazingRate();
        if(herbivore.isInfected()) {
            grazingRate *= 0.82;
        }
        grazingRate *= 1.0 + herbivore.getSurvivalPressure() * 0.45;
        double available = grass[location.row()][location.col()];
        double eaten = Math.min(available, grazingRate);
        grass[location.row()][location.col()] = Math.max(0.0, available - eaten);
        int foodGained = (int)Math.round((eaten / Math.max(0.01, grazingRate)) *
                                         herbivore.getProfile().getPlantFoodValue());
        if(foodGained > 0) {
            herbivore.feed(foodGained);
            context.recordEvent(SimulationEvent.graze(context.getStep(),
                                                      herbivore, location));
        }
    }

    public double getFoodLevelAt(Location location)
    {
        if(location == null) {
            return 0.0;
        }
        return grass[location.row()][location.col()];
    }

    public double getAverageFoodLevel()
    {
        return averageFoodLevel;
    }
}
