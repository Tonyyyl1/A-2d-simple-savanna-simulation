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

    boolean isDrinkableShore(Location location);

    HydrationAssessment assess(SavannahAnimal animal, SimulationContext context);

    double getEffectiveDrain(SavannahAnimal animal, SimulationContext context);

    /** Read-only distance query used by diagnostics and behavior probes. */
    int distanceToShore(Location location);

    int countDrinkableShores();

    int maximumReachableShoreDistance();
}
