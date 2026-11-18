package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable topology-reachable animal-resource facts for one diet query. */
public final class ReachableResourceFact
{
    public static final int NO_EDIBLE_DISTANCE = -1;

    private final Location origin;
    private final int range;
    private final String profileKey;
    private final int passableVisitedCount;
    private final int reachableEdibleCount;
    private final int reachableOfferedFood;
    private final int nearestEdibleDistance;
    private final List<Long> edibleAnimalIdsSorted;

    public ReachableResourceFact(Location origin, int range, String profileKey,
                                 int passableVisitedCount,
                                 int reachableEdibleCount,
                                 int reachableOfferedFood,
                                 int nearestEdibleDistance,
                                 List<Long> edibleAnimalIdsSorted)
    {
        if(origin == null || range < 0 || profileKey == null ||
           profileKey.isBlank() || passableVisitedCount < 0 ||
           reachableEdibleCount < 0 || reachableOfferedFood < 0 ||
           edibleAnimalIdsSorted == null ||
           reachableEdibleCount != edibleAnimalIdsSorted.size()) {
            throw new IllegalArgumentException(
                "invalid reachable-resource fact");
        }
        this.origin = copy(origin);
        this.range = range;
        this.profileKey = profileKey;
        this.passableVisitedCount = passableVisitedCount;
        this.reachableEdibleCount = reachableEdibleCount;
        this.reachableOfferedFood = reachableOfferedFood;
        this.nearestEdibleDistance = nearestEdibleDistance;
        List<Long> ids = new ArrayList<>(edibleAnimalIdsSorted);
        Collections.sort(ids);
        this.edibleAnimalIdsSorted = Collections.unmodifiableList(ids);
    }

    public Location getOrigin() { return copy(origin); }

    public int getRange() { return range; }

    public String getProfileKey() { return profileKey; }

    public String getDietKey() { return profileKey; }

    public int getPassableVisitedCount() { return passableVisitedCount; }

    public int getReachableEdibleCount() { return reachableEdibleCount; }

    public int getReachableOfferedFood() { return reachableOfferedFood; }

    public int getNearestEdibleDistance() { return nearestEdibleDistance; }

    public List<Long> getEdibleAnimalIdsSorted()
    {
        return edibleAnimalIdsSorted;
    }

    @Override
    public boolean equals(Object other)
    {
        if(this == other) return true;
        if(!(other instanceof ReachableResourceFact)) return false;
        ReachableResourceFact fact = (ReachableResourceFact)other;
        return range == fact.range &&
               passableVisitedCount == fact.passableVisitedCount &&
               reachableEdibleCount == fact.reachableEdibleCount &&
               reachableOfferedFood == fact.reachableOfferedFood &&
               nearestEdibleDistance == fact.nearestEdibleDistance &&
               origin.equals(fact.origin) &&
               profileKey.equals(fact.profileKey) &&
               edibleAnimalIdsSorted.equals(fact.edibleAnimalIdsSorted);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(origin, range, profileKey, passableVisitedCount,
            reachableEdibleCount, reachableOfferedFood, nearestEdibleDistance,
            edibleAnimalIdsSorted);
    }

    private static Location copy(Location value)
    {
        return java.util.Objects.requireNonNull(value);
    }
}
