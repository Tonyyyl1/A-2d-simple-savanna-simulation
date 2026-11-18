package savanna;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Chooses a free, passable destination without mutating field state. */
public final class MovementResolver
{
    public Resolution resolve(Field targetField, TerrainMap terrain,
                              Location requested,
                              List<Location> alternatives,
                              Location searchOrigin, int maxSearchRange)
    {
        Set<Location> examined = new HashSet<>();
        boolean conflict = false;

        if(requested != null) {
            examined.add(requested);
            if(isFreePassable(targetField, terrain, requested)) {
                return new Resolution(requested, false, false);
            }
            conflict = isOccupied(targetField, requested);
        }

        if(alternatives != null) {
            for(Location alternative : alternatives) {
                if(alternative == null || !examined.add(alternative)) {
                    continue;
                }
                if(isFreePassable(targetField, terrain, alternative)) {
                    return new Resolution(alternative, conflict, false);
                }
                conflict = conflict || isOccupied(targetField, alternative);
            }
        }

        Location nearest = TerrainNavigator.nearestFreePassable(
            targetField, terrain, searchOrigin, maxSearchRange, examined);
        if(nearest != null) {
            return new Resolution(nearest, conflict, true);
        }
        return new Resolution(null, conflict, true);
    }

    private boolean isFreePassable(Field field, TerrainMap terrain,
                                   Location location)
    {
        return inBounds(terrain, location) && terrain.isPassable(location) &&
               (field.getAnimalAt(location) == null ||
                !field.getAnimalAt(location).isAlive());
    }

    private boolean isOccupied(Field field, Location location)
    {
        Animal occupant = field == null ? null : field.getAnimalAt(location);
        return occupant != null && occupant.isAlive();
    }

    private boolean inBounds(TerrainMap terrain, Location location)
    {
        return terrain != null && location != null &&
               location.row() >= 0 && location.row() < terrain.getDepth() &&
               location.col() >= 0 && location.col() < terrain.getWidth();
    }

    public static final class Resolution
    {
        private final Location destination;
        private final boolean encounteredConflict;
        private final boolean usedSearchFallback;

        private Resolution(Location destination, boolean encounteredConflict,
                           boolean usedSearchFallback)
        {
            this.destination = destination;
            this.encounteredConflict = encounteredConflict;
            this.usedSearchFallback = usedSearchFallback;
        }

        public Location getDestination()
        {
            return destination;
        }

        public boolean encounteredConflict()
        {
            return encounteredConflict;
        }

        public boolean usedSearchFallback()
        {
            return usedSearchFallback;
        }
    }
}
