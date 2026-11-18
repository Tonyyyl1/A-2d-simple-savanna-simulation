package savanna;

/** Immutable, post-decision reservation fact for O0a observation only. */
public final class TransitionObservation
{
    private final long animalId;
    private final Location origin;
    private final Location requested;
    private final Location resolved;
    private final TransitionStatus status;
    private final boolean usedSearchFallback;
    private final int step;

    public TransitionObservation(long animalId, Location origin,
                                 Location requested, Location resolved,
                                 TransitionStatus status,
                                 boolean usedSearchFallback, int step)
    {
        this.animalId = animalId;
        this.origin = copy(origin);
        this.requested = copy(requested);
        this.resolved = copy(resolved);
        this.status = status == null ? TransitionStatus.BLOCKED : status;
        this.usedSearchFallback = usedSearchFallback;
        this.step = step;
    }

    public long getAnimalId() { return animalId; }
    public Location getOrigin() { return copy(origin); }
    public Location getRequested() { return copy(requested); }
    public Location getResolved() { return copy(resolved); }
    public TransitionStatus getStatus() { return status; }
    public boolean usedSearchFallback() { return usedSearchFallback; }
    public int getStep() { return step; }

    private static Location copy(Location location)
    {
        return location == null ? null
            : new Location(location.row(), location.col());
    }
}
