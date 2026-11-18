package savanna;

import java.util.List;
import java.util.Random;

/**
 * The named spatial facts an ecological interaction may read during one step.
 *
 * <p>An interaction geometry is not a {@link Field}: it cannot place, remove,
 * reserve, or otherwise own positions. Implementations may retain references
 * to animals so liveness and physiology remain current, but their
 * location-to-animal mapping is immutable for the geometry's lifetime.</p>
 */
public interface InteractionGeometry
{
    Animal getAnimalAt(Location location);

    List<Location> getLocationsWithin(Location origin, int range, Random random);

    int getLivingPopulation();

    int getDepth();

    int getWidth();
}
