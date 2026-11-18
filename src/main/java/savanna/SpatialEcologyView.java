package savanna;

import java.util.List;

/**
 * Read-only step-start spatial facts. Implementations may memoize queries
 * inside the current step but cannot expose live simulation objects.
 */
public interface SpatialEcologyView
{
    int getStep();

    int getDepth();

    int getWidth();

    int getCapturedAnimalCount();

    List<SpatialAnimalFact> getAnimalFacts();

    SpatialAnimalFact getAnimalFact(long animalId);

    SpatialAnimalFact getAnimalFactAt(Location location);

    ReachableResourceFact getReachableResources(Location origin, int range,
                                                SpeciesProfile profile);

    ReachableResourceFact getReachableResources(Location origin, int range,
                                                String profileKey);

    LocalOccupancyFact getLocalOccupancy(Location origin, int range,
                                         SpeciesProfile profile);

    LocalOccupancyFact getLocalOccupancy(Location origin, int range,
                                         String profileKey);

    SpatialQueryLedger getQueryLedger();
}
