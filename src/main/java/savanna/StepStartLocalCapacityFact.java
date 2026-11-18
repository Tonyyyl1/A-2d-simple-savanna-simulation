package savanna;

/** Raw SOC1-aF breeding-range occupancy numerators and denominators. */
public final class StepStartLocalCapacityFact
{
    private final int step;
    private final long actorId;
    private final String speciesKey;
    private final Location origin;
    private final int breedingRange;
    private final int passableCellCount;
    private final int livingOccupantCount;
    private final int conspecificCount;
    private final int predatorCount;
    private final int ediblePreyCount;

    StepStartLocalCapacityFact(int step, SpatialAnimalFact actor,
                               int breedingRange,
                               LocalOccupancyFact occupancy)
    {
        if(step < 0 || actor == null || breedingRange < 0 ||
           occupancy == null) {
            throw new IllegalArgumentException(
                "complete local-capacity inputs are required");
        }
        this.step = step;
        actorId = actor.getId();
        speciesKey = actor.getSpecies();
        origin = copy(actor.getLocation());
        this.breedingRange = breedingRange;
        passableCellCount = occupancy.getPassableCellCount();
        livingOccupantCount = occupancy.getLivingOccupantCount();
        conspecificCount = occupancy.getConspecificCount();
        predatorCount = occupancy.getPredatorCount();
        ediblePreyCount = occupancy.getEdiblePreyCount();
    }

    public int getStep() { return step; }

    public long getActorId() { return actorId; }

    public String getSpeciesKey() { return speciesKey; }

    public Location getOrigin() { return copy(origin); }

    public int getBreedingRange() { return breedingRange; }

    public int getPassableCellCount() { return passableCellCount; }

    public int getLivingOccupantCount() { return livingOccupantCount; }

    public int getConspecificCount() { return conspecificCount; }

    public int getPredatorCount() { return predatorCount; }

    public int getEdiblePreyCount() { return ediblePreyCount; }

    public int getOccupancyNumerator() { return livingOccupantCount; }

    public int getOccupancyDenominator() { return passableCellCount; }

    private static Location copy(Location location)
    {
        return java.util.Objects.requireNonNull(location);
    }
}
