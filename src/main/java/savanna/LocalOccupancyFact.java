package savanna;

import java.util.Objects;

/** Immutable topology-local occupancy facts for one focal species profile. */
public final class LocalOccupancyFact
{
    private final Location origin;
    private final int range;
    private final String profileKey;
    private final int passableCellCount;
    private final int livingOccupantCount;
    private final int conspecificCount;
    private final int predatorCount;
    private final int ediblePreyCount;

    public LocalOccupancyFact(Location origin, int range, String profileKey,
                              int passableCellCount,
                              int livingOccupantCount,
                              int conspecificCount, int predatorCount,
                              int ediblePreyCount)
    {
        if(origin == null || range < 0 || profileKey == null ||
           profileKey.isBlank() || passableCellCount < 0 ||
           livingOccupantCount < 0 || conspecificCount < 0 ||
           predatorCount < 0 || ediblePreyCount < 0) {
            throw new IllegalArgumentException("invalid local-occupancy fact");
        }
        this.origin = copy(origin);
        this.range = range;
        this.profileKey = profileKey;
        this.passableCellCount = passableCellCount;
        this.livingOccupantCount = livingOccupantCount;
        this.conspecificCount = conspecificCount;
        this.predatorCount = predatorCount;
        this.ediblePreyCount = ediblePreyCount;
    }

    public Location getOrigin() { return copy(origin); }

    public int getRange() { return range; }

    public String getProfileKey() { return profileKey; }

    public int getPassableCellCount() { return passableCellCount; }

    public int getLivingOccupantCount() { return livingOccupantCount; }

    public int getConspecificCount() { return conspecificCount; }

    public int getPredatorCount() { return predatorCount; }

    public int getEdiblePreyCount() { return ediblePreyCount; }

    @Override
    public boolean equals(Object other)
    {
        if(this == other) return true;
        if(!(other instanceof LocalOccupancyFact)) return false;
        LocalOccupancyFact fact = (LocalOccupancyFact)other;
        return range == fact.range &&
               passableCellCount == fact.passableCellCount &&
               livingOccupantCount == fact.livingOccupantCount &&
               conspecificCount == fact.conspecificCount &&
               predatorCount == fact.predatorCount &&
               ediblePreyCount == fact.ediblePreyCount &&
               origin.equals(fact.origin) &&
               profileKey.equals(fact.profileKey);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(origin, range, profileKey, passableCellCount,
            livingOccupantCount, conspecificCount, predatorCount,
            ediblePreyCount);
    }

    private static Location copy(Location value)
    {
        return new Location(value.row(), value.col());
    }
}
