package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only local edible-prey facts for one predator and one interaction view.
 *
 * <p>The deterministic inspection path consumes no ecological RNG. HUNT may
 * supply its legacy shuffled location sequence to the same classifier so its
 * existing target-order and random-operation shape remain unchanged.</p>
 */
public final class HuntLocalOpportunity
{
    private final int effectiveRange;
    private final List<Candidate> candidates;

    HuntLocalOpportunity(int effectiveRange, List<Candidate> candidates)
    {
        this.effectiveRange = Math.max(0, effectiveRange);
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
    }

    /** Inspects the current interaction geometry in deterministic coordinate order. */
    public static HuntLocalOpportunity inspect(SavannahAnimal predator,
                                               InteractionGeometry geometry,
                                               SimulationContext context)
    {
        if(!isEligiblePredator(predator) || geometry == null || context == null) {
            return empty();
        }
        int range = effectiveRange(predator, context);
        TerrainNavigator.Reachability reachability =
            TerrainNavigator.reachableDistances(context.getTerrainMap(),
                predator.getLocation(), range);
        return fromLocations(predator, geometry, context, range, reachability,
            deterministicLocations(predator.getLocation(), range, geometry));
    }

    /** Shared HUNT classifier for a legacy-shuffled location sequence. */
    static HuntLocalOpportunity fromHuntLocations(SavannahAnimal predator,
                                                   InteractionGeometry geometry,
                                                   SimulationContext context,
                                                   int range,
                                                   TerrainNavigator.Reachability reachability,
                                                   List<Location> locations)
    {
        return fromLocations(predator, geometry, context, range, reachability,
            locations);
    }

    static int effectiveRange(SavannahAnimal predator, SimulationContext context)
    {
        if(predator == null || context == null) {
            return 0;
        }
        int range = Math.max(1, (int)Math.round(context.getWeatherSystem()
            .getHuntingRange(predator) * predator.getEffectiveStaminaMultiplier()));
        return range + rangeBonus(predator.getProfile(), EcologyParameters.current()
            .getInt(EcologyParameters.Key.CHEETAH_RANGE_BONUS));
    }

    /** M3 change C1: extra hunting range cells for a Cheetah; 0 for everyone else or when off. */
    static int rangeBonus(SpeciesProfile predator, int cheetahBonus)
    {
        return SpeciesRegistry.CHEETAH.equals(predator.getName()) ? Math.max(0, cheetahBonus) : 0;
    }

    private static HuntLocalOpportunity fromLocations(SavannahAnimal predator,
                                                       InteractionGeometry geometry,
                                                       SimulationContext context,
                                                       int range,
                                                       TerrainNavigator.Reachability reachability,
                                                       List<Location> locations)
    {
        if(!isEligiblePredator(predator) || geometry == null || context == null ||
           reachability == null || locations == null) {
            return empty();
        }
        List<Candidate> result = new ArrayList<>();
        int zebraStage = EcologyParameters.current().getInt(
            EcologyParameters.Key.CHEETAH_ZEBRA_STAGE);
        for(Location location : locations) {
            Animal animal = geometry.getAnimalAt(location);
            if(!(animal instanceof SavannahAnimal)) {
                continue;
            }
            SavannahAnimal prey = (SavannahAnimal)animal;
            if(!prey.isAlive() || !canHunt(predator.getProfile(), prey,
               zebraStage) ||
               !context.getTerrainMap().isPassable(location)) {
                continue;
            }
            int distance = reachability.distanceTo(location);
            if(distance < 0) {
                continue;
            }
            result.add(new Candidate(prey, location, distance));
        }
        return new HuntLocalOpportunity(range, result);
    }

    /**
     * Diet test for one target: the profile diet, plus M3 change G, which lets
     * a Cheetah hunt young Zebra (stage 1: cubs and juveniles, 2: cubs only,
     * 0: off, the profile diet alone).
     */
    static boolean canHunt(SpeciesProfile predator, SavannahAnimal prey,
                           int zebraStage)
    {
        if(predator.canEat(prey.getProfile().getName())) return true;
        if(zebraStage <= 0 ||
           !SpeciesRegistry.CHEETAH.equals(predator.getName()) ||
           !SpeciesRegistry.ZEBRA.equals(prey.getProfile().getName())) {
            return false;
        }
        LifeStage stage = prey.getLifeStage();
        return stage == LifeStage.CUB ||
               (zebraStage == 1 && stage == LifeStage.JUVENILE);
    }

    static List<Location> deterministicLocations(Location origin,
                                                 int range,
                                                 InteractionGeometry geometry)
    {
        List<Location> result = new ArrayList<>();
        if(origin == null || geometry == null || range <= 0) {
            return result;
        }
        for(int rowOffset = -range; rowOffset <= range; rowOffset++) {
            int row = origin.row() + rowOffset;
            if(row < 0 || row >= geometry.getDepth()) {
                continue;
            }
            for(int colOffset = -range; colOffset <= range; colOffset++) {
                int col = origin.col() + colOffset;
                if(col < 0 || col >= geometry.getWidth() ||
                   (rowOffset == 0 && colOffset == 0)) {
                    continue;
                }
                result.add(new Location(row, col));
            }
        }
        return result;
    }

    private static boolean isEligiblePredator(SavannahAnimal predator)
    {
        return predator != null && predator.isAlive() &&
               predator.isAdult() && predator.getProfile().isPredator();
    }

    private static HuntLocalOpportunity empty()
    {
        return new HuntLocalOpportunity(0, Collections.<Candidate>emptyList());
    }

    public int getEffectiveRange() { return effectiveRange; }

    public int getLocalReachableEdibleCount() { return candidates.size(); }

    public boolean hasLocalReachableEdible() { return !candidates.isEmpty(); }

    List<Candidate> getCandidates() { return candidates; }

    static final class Candidate
    {
        private final SavannahAnimal prey;
        private final Location location;
        private final int distance;

        Candidate(SavannahAnimal prey, Location location, int distance)
        {
            this.prey = prey;
            this.location = location == null ? null :
                new Location(location.row(), location.col());
            this.distance = distance;
        }

        SavannahAnimal getPrey() { return prey; }

        Location getLocation()
        {
            return location == null ? null :
                new Location(location.row(), location.col());
        }

        int getDistance() { return distance; }
    }
}
