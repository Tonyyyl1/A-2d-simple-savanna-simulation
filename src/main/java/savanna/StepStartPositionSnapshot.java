package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable step-start facts for living adults only. */
public final class StepStartPositionSnapshot
{
    private final Map<Long, Location> originsByAdultId;
    private final Map<Location, Long> ownerByOrigin;
    private final List<Long> adultIds;

    private StepStartPositionSnapshot(Map<Long, Location> origins,
                                      Map<Location, Long> owners,
                                      List<Long> adultIds)
    {
        originsByAdultId = Collections.unmodifiableMap(
            new LinkedHashMap<>(origins));
        ownerByOrigin = Collections.unmodifiableMap(new LinkedHashMap<>(owners));
        this.adultIds = Collections.unmodifiableList(new ArrayList<>(adultIds));
    }

    /**
     * Creates a snapshot from already-filtered living facts. Dead animals do
     * not appear here and therefore do not protect an old location.
     */
    public static StepStartPositionSnapshot of(Map<Long, Location> origins)
    {
        Objects.requireNonNull(origins, "origins are required");
        List<Long> ids = new ArrayList<>(origins.keySet());
        Collections.sort(ids);
        Map<Long, Location> copiedOrigins = new LinkedHashMap<>();
        Map<Location, Long> owners = new LinkedHashMap<>();
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
            Long previous = owners.put(copied, id);
            if(previous != null) {
                throw new IllegalArgumentException(
                    "Duplicate living origin " + copied + " for " +
                    previous + " and " + id);
            }
            copiedOrigins.put(id, copied);
        }
        return new StepStartPositionSnapshot(copiedOrigins, owners, ids);
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
        return ownerByOrigin.get(location);
    }

    public List<Long> getAdultIds()
    {
        return adultIds;
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
        return location == null ? null :
            new Location(location.row(), location.col());
    }
}
