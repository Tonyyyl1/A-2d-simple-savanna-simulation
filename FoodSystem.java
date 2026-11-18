import java.util.List;

/**
 * Interface for plant growth and grazing.
 */
public interface FoodSystem
{
    void grow(SimulationContext context, Field field);

    Location chooseGrazingLocation(SavannahAnimal herbivore, List<Location> locations,
                                   SimulationContext context);

    void feedHerbivoreAt(SavannahAnimal herbivore, Location location, SimulationContext context);

    double getFoodLevelAt(Location location);

    double getAverageFoodLevel();
}
