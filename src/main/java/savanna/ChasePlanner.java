package savanna;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Plans one step of a real chase (plan v3.2 B-1): a predator that is out of
 * contact with its chosen prey moves at most {@code speed} land steps along a
 * path that shortens the land distance to the prey, instead of jumping onto the
 * prey's cell. The plan is a pure function of terrain and positions; it draws
 * no random numbers, and its total order does not depend on candidate order.
 */
final class ChasePlanner
{
    /** The requested cell and the cells to try when it is taken; both closer to the prey. */
    static final class Plan
    {
        private final Location target;
        private final List<Location> fallbacks;

        Plan(Location target, List<Location> fallbacks)
        {
            this.target = target;
            this.fallbacks = fallbacks;
        }

        /** The cell to move to, or null when no reachable cell is closer to the prey. */
        Location getTarget() { return target; }

        List<Location> getFallbacks() { return fallbacks; }
    }

    private ChasePlanner()
    {
    }

    static Plan plan(TerrainMap terrain, PositionAvailabilityView availability,
                     Location origin, Location prey, int speed, int searchRange)
    {
        int window = Math.max(searchRange, speed) + speed;
        TerrainNavigator.Reachability toPrey =
            TerrainNavigator.reachableDistances(terrain, prey, window);
        int originDistance = toPrey.distanceTo(origin);
        int bound = originDistance < 0 ? Integer.MAX_VALUE : originDistance;
        List<Location> candidates = availability.reachableCandidates(origin, speed, null);
        List<Location> closer = new ArrayList<>();
        for(Location candidate : candidates) {
            int distance = toPrey.distanceTo(candidate);
            if(distance >= 1 && distance < bound) closer.add(candidate);
        }
        closer.sort(Comparator
            .comparingInt((Location cell) -> toPrey.distanceTo(cell))
            .thenComparingInt(cell -> chebyshev(origin, cell))
            .thenComparingInt(Location::row)
            .thenComparingInt(Location::col));
        if(closer.isEmpty()) return new Plan(null, new ArrayList<>());
        Location target = closer.get(0);
        return new Plan(target, new ArrayList<>(closer.subList(1, closer.size())));
    }

    private static int chebyshev(Location first, Location second)
    {
        return Math.max(Math.abs(first.row() - second.row()),
            Math.abs(first.col() - second.col()));
    }
}
