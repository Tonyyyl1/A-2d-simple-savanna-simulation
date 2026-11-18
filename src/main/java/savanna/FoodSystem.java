package savanna;

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

    /** Biomass as a fraction of capacity within {@code range} of {@code center}. */
    double getLocalFill(Location center, int range);
}
