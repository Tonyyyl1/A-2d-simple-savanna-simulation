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

    /** Receives the facts offered by {@link #forEachAnimalFactAround}. */
    interface AnimalFactSink
    {
        void accept(SpatialAnimalFact fact);
    }

    /**
     * Offers every captured animal fact inside the square of the given radius around
     * {@code origin}, clipped to the field and visited in row-major order, to
     * {@code sink}. A non-null {@code species} offers only facts of that species and
     * {@code predatorsOnly} only predator facts; every other fact is skipped, so an
     * implementation need not build anything for cells that cannot match. Callers keep
     * whatever further conditions they had (life stage, sex, liveness).
     */
    default void forEachAnimalFactAround(Location origin, int radius, String species,
                                         boolean predatorsOnly, AnimalFactSink sink)
    {
        if(origin == null) return;
        long firstRow = Math.max(0L, (long)origin.row() - radius);
        long lastRow = Math.min(getDepth() - 1L, (long)origin.row() + radius);
        long firstCol = Math.max(0L, (long)origin.col() - radius);
        long lastCol = Math.min(getWidth() - 1L, (long)origin.col() + radius);
        for(long row = firstRow; row <= lastRow; row++) {
            for(long col = firstCol; col <= lastCol; col++) {
                SpatialAnimalFact fact =
                    getAnimalFactAt(new Location((int)row, (int)col));
                if(fact != null && (species == null || species.equals(fact.getSpecies())) &&
                   (!predatorsOnly || fact.isPredator())) {
                    sink.accept(fact);
                }
            }
        }
    }

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
