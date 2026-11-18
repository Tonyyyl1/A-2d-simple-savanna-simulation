package savanna;

import java.util.List;

/**
 * Optional experimental water and thirst behaviour.
 */
public interface ThirstSystem
{
    void applyThirst(SavannahAnimal animal, SimulationContext context);

    boolean needsWater(SavannahAnimal animal);

    boolean tryDrink(SavannahAnimal animal, SimulationContext context);

    Location stepTowardWater(SavannahAnimal animal, List<Location> freeLocations);

    Location stepTowardWater(SavannahAnimal animal, List<Location> freeLocations,
                             int step);

    boolean isDrinkableShore(Location location);

    HydrationAssessment assess(SavannahAnimal animal, SimulationContext context);

    /** Read-only target decision; it must not mutate the animal or emit events. */
    WaterTargetDecision decideWaterTarget(SavannahAnimal animal,
                                          SimulationContext context);

    /** Authoritative behavior-phase target commit. */
    WaterTargetDecision selectWaterTarget(SavannahAnimal animal,
                                          SimulationContext context);

    int distanceToTargetPool(Location location, String poolId);

    double getEffectiveDrain(SavannahAnimal animal, SimulationContext context);

    /** Read-only distance query used by diagnostics and behavior probes. */
    int distanceToShore(Location location);

    int countDrinkableShores();

    int maximumReachableShoreDistance();
}
