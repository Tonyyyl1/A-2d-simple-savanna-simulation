package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Immutable request for one adult move/stay or one provisional spawn slot. */
public final class PositionClaim
{
    private final PositionActorKey actor;
    private final PositionClaimKind kind;
    private final PositionPriority priority;
    private final Location requested;
    private final List<Location> explicitFallbacks;
    private final Location searchOrigin;
    private final int searchCandidateBudget;
    private final int waitingDebt;

    public PositionClaim(PositionActorKey actor, PositionClaimKind kind,
                         PositionPriority priority, Location requested,
                         List<Location> explicitFallbacks,
                         Location searchOrigin, int searchCandidateBudget,
                         int waitingDebt)
    {
        this.actor = Objects.requireNonNull(actor, "position actor is required");
        this.kind = Objects.requireNonNull(kind, "claim kind is required");
        this.priority = Objects.requireNonNull(priority,
            "position priority is required");
        if(searchCandidateBudget < 0 || waitingDebt < 0) {
            throw new IllegalArgumentException(
                "Search budget and waiting debt must be non-negative");
        }
        if((kind == PositionClaimKind.SPAWN) != actor.isSpawn()) {
            throw new IllegalArgumentException(
                "Spawn keys and SPAWN claims must agree");
        }
        if(kind == PositionClaimKind.SPAWN && priority != PositionPriority.SPAWN) {
            throw new IllegalArgumentException(
                "Spawn claims must use the spawn priority bucket");
        }
        if(kind != PositionClaimKind.SPAWN && priority == PositionPriority.SPAWN) {
            throw new IllegalArgumentException(
                "Adult claims cannot use the spawn priority bucket");
        }
        this.requested = copy(requested);
        this.explicitFallbacks = immutableUniqueLocations(explicitFallbacks);
        this.searchOrigin = copy(searchOrigin);
        this.searchCandidateBudget = searchCandidateBudget;
        this.waitingDebt = waitingDebt;
    }

    public static PositionClaim stay(long adultId, PositionPriority priority,
                                     int waitingDebt)
    {
        return new PositionClaim(PositionActorKey.adult(adultId),
            PositionClaimKind.STAY, priority, null,
            Collections.<Location>emptyList(), null, 0, waitingDebt);
    }

    /** Same as {@link #stay(long, PositionPriority, int)} with a prebuilt adult key. */
    public static PositionClaim stay(PositionActorKey adultKey,
                                     PositionPriority priority, int waitingDebt)
    {
        requireAdultKey(adultKey);
        return new PositionClaim(adultKey,
            PositionClaimKind.STAY, priority, null,
            Collections.<Location>emptyList(), null, 0, waitingDebt);
    }

    /** Same as {@link #move(long, PositionPriority, Location, List, int, int)}
     *  with a prebuilt adult key. */
    public static PositionClaim move(PositionActorKey adultKey,
                                     PositionPriority priority,
                                     Location requested,
                                     List<Location> explicitFallbacks,
                                     int searchCandidateBudget, int waitingDebt)
    {
        requireAdultKey(adultKey);
        return new PositionClaim(adultKey,
            PositionClaimKind.MOVE, priority, requested, explicitFallbacks,
            null, searchCandidateBudget, waitingDebt);
    }

    private static void requireAdultKey(PositionActorKey adultKey)
    {
        if(adultKey == null || !adultKey.isAdult()) {
            throw new IllegalArgumentException("An adult position key is required");
        }
    }

    public static PositionClaim move(long adultId, PositionPriority priority,
                                     Location requested,
                                     List<Location> explicitFallbacks,
                                     int searchCandidateBudget, int waitingDebt)
    {
        return new PositionClaim(PositionActorKey.adult(adultId),
            PositionClaimKind.MOVE, priority, requested, explicitFallbacks,
            null, searchCandidateBudget, waitingDebt);
    }

    public static PositionClaim spawn(long parentId, int litterOrdinal,
                                      Location requested,
                                      List<Location> explicitFallbacks,
                                      Location searchOrigin,
                                      int searchCandidateBudget,
                                      int waitingDebt)
    {
        return new PositionClaim(PositionActorKey.spawn(parentId, litterOrdinal),
            PositionClaimKind.SPAWN, PositionPriority.SPAWN, requested,
            explicitFallbacks, searchOrigin, searchCandidateBudget,
            waitingDebt);
    }

    public PositionActorKey getActor()
    {
        return actor;
    }

    public PositionClaimKind getKind()
    {
        return kind;
    }

    public PositionPriority getPriority()
    {
        return priority;
    }

    public Location getRequested()
    {
        return copy(requested);
    }

    public List<Location> getExplicitFallbacks()
    {
        return explicitFallbacks;
    }

    /** Explicit search source for spawn; adults default to their snapshot origin. */
    public Location getSearchOrigin()
    {
        return copy(searchOrigin);
    }

    public int getSearchCandidateBudget()
    {
        return searchCandidateBudget;
    }

    public int getWaitingDebt()
    {
        return waitingDebt;
    }

    /** Conservative count used to prove cursor visits remain bounded. */
    public int declaredCandidateUpperBound(boolean adult)
    {
        return 1 + explicitFallbacks.size() + searchCandidateBudget +
            (adult ? 1 : 0);
    }

    private static List<Location> immutableUniqueLocations(List<Location> values)
    {
        LinkedHashSet<Location> unique = new LinkedHashSet<>();
        if(values != null) {
            for(Location value : values) {
                if(value != null) {
                    unique.add(copy(value));
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(unique));
    }

    private static Location copy(Location location)
    {
        return location;
    }
}
