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
}
