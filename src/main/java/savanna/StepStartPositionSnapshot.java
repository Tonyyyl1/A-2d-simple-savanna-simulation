package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable step-start facts for living adults only. */
public final class StepStartPositionSnapshot
{
    private final Map<Long, Location> originsByAdultId;
    private final List<Long> adultIds;
    /** Position-board keys of the adults, parallel to {@link #adultIds}. */
    private final List<PositionActorKey> adultKeys;
    private final int depth;
    private final int width;
    /**
     * Per cell inside depth x width: 0 when no living adult starts there, else the
     * position of the owner in {@link #adultIds} plus one. Only get is ever used.
     */
    private final int[] ownerIndexByCell;
    /** Origins outside depth x width (never produced by a field); get only. */
    private final Map<Location, Integer> ownerIndexOutside;

    private StepStartPositionSnapshot(Map<Long, Location> origins,
                                      List<Long> adultIds,
                                      List<PositionActorKey> adultKeys,
                                      int depth, int width,
                                      int[] ownerIndexByCell,
                                      Map<Location, Integer> ownerIndexOutside)
    {
        originsByAdultId = Collections.unmodifiableMap(
            new LinkedHashMap<>(origins));
        this.adultIds = Collections.unmodifiableList(new ArrayList<>(adultIds));
        this.adultKeys = Collections.unmodifiableList(adultKeys);
        this.depth = depth;
        this.width = width;
        this.ownerIndexByCell = ownerIndexByCell;
        this.ownerIndexOutside = ownerIndexOutside;
    }

    /**
     * Creates a snapshot from already-filtered living facts. Dead animals do
     * not appear here and therefore do not protect an old location.
     */
    public static StepStartPositionSnapshot of(Map<Long, Location> origins)
    {
        Objects.requireNonNull(origins, "origins are required");
        // No field dimensions are known here: size the cell array to the origins.
        int depth = 0;
        int width = 0;
        for(Location origin : origins.values()) {
            if(origin != null && origin.row() >= 0 && origin.col() >= 0) {
                depth = Math.max(depth, origin.row() + 1);
                width = Math.max(width, origin.col() + 1);
            }
        }
        return of(origins, depth, width);
    }

    /**
     * Same as {@link #of(Map)} for a field of {@code depth} x {@code width} cells; the
     * per-cell owner table then has exactly one entry per field cell.
     */
    public static StepStartPositionSnapshot of(Map<Long, Location> origins,
                                               int depth, int width)
    {
        return of(origins, depth, width, null);
    }

    /**
     * Same as {@link #of(Map, int, int)}, taking each adult's position-board key from
     * {@code keyOf} (the coordinator passes the key cached on the animal) instead of
     * building {@link PositionActorKey#adult(long)} again. A key is compared by value, so
     * both ways are equal; a null {@code keyOf} builds the keys here.
     */
    public static StepStartPositionSnapshot of(
        Map<Long, Location> origins, int depth, int width,
        java.util.function.Function<Long, PositionActorKey> keyOf)
    {
        Objects.requireNonNull(origins, "origins are required");
        if(depth < 0 || width < 0) {
            throw new IllegalArgumentException("dimensions must not be negative");
        }
        List<Long> ids = new ArrayList<>(origins.keySet());
        Collections.sort(ids);
        Map<Long, Location> copiedOrigins = new LinkedHashMap<>();
        int[] ownerIndexByCell = new int[depth * width];
        Map<Location, Integer> outside = new HashMap<>();
        List<PositionActorKey> keys = new ArrayList<>(ids.size());
        int position = 0;
        for(Long id : ids) {
            if(id == null || id.longValue() <= 0L) {
                throw new IllegalArgumentException("Adult ID must be positive");
            }
            Location origin = origins.get(id);
            if(origin == null) {
                throw new IllegalArgumentException(
                    "Living adult requires a step-start origin: " + id);
            }
            Location copied = copy(origin);
            position++;
            int previousIndex;
            if(inside(copied, depth, width)) {
                int cell = copied.row() * width + copied.col();
                previousIndex = ownerIndexByCell[cell];
                ownerIndexByCell[cell] = position;
            }
            else {
                Integer previousBoxed = outside.put(copied, Integer.valueOf(position));
                previousIndex = previousBoxed == null ? 0 : previousBoxed.intValue();
            }
            if(previousIndex != 0) {
                throw new IllegalArgumentException(
                    "Duplicate living origin " + copied + " for " +
                    ids.get(previousIndex - 1) + " and " + id);
            }
            copiedOrigins.put(id, copied);
            PositionActorKey key = keyOf == null ? null : keyOf.apply(id);
            if(key == null) {
                key = PositionActorKey.adult(id.longValue());
            }
            else if(!key.isAdult() || key.getAdultId() != id.longValue()) {
                throw new IllegalArgumentException(
                    "Adult key does not match its adult ID: " + id);
            }
            keys.add(key);
        }
        return new StepStartPositionSnapshot(copiedOrigins, ids, keys, depth, width,
            ownerIndexByCell, outside);
    }

    public boolean containsAdult(long adultId)
    {
        return originsByAdultId.containsKey(adultId);
    }

    public Location originOf(long adultId)
    {
        return copy(originsByAdultId.get(adultId));
    }

    public Long ownerAt(Location location)
    {
        int index = ownerIndexAt(location);
        return index == 0 ? null : adultIds.get(index - 1);
    }

    /** True when a living adult started the step in {@code location}; does not box. */
    public boolean isOccupied(Location location)
    {
        return ownerIndexAt(location) != 0;
    }

    private int ownerIndexAt(Location location)
    {
        if(location == null) return 0;
        if(inside(location, depth, width)) {
            return ownerIndexByCell[location.row() * width + location.col()];
        }
        if(ownerIndexOutside.isEmpty()) return 0;
        Integer index = ownerIndexOutside.get(location);
        return index == null ? 0 : index.intValue();
    }

    private static boolean inside(Location location, int depth, int width)
    {
        return location.row() >= 0 && location.row() < depth &&
               location.col() >= 0 && location.col() < width;
    }

    public List<Long> getAdultIds()
    {
        return adultIds;
    }

    /** The adults' position-board keys, in the same (ascending ID) order as the IDs. */
    public List<PositionActorKey> getAdultKeys()
    {
        return adultKeys;
    }

    public int size()
    {
        return adultIds.size();
    }

    public Map<Long, Location> getOriginsByAdultId()
    {
        return originsByAdultId;
    }

    private static Location copy(Location location)
    {
        return location;
    }
}
