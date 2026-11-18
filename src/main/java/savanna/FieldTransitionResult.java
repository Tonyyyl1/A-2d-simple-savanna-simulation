package savanna;

/** Immutable outcome of a movement or spawn reservation. */
public final class FieldTransitionResult
{
    private final TransitionStatus status;
    private final Location requestedLocation;
    private final Location destination;
    private final boolean usedSearchFallback;

    public FieldTransitionResult(TransitionStatus status,
                                 Location requestedLocation,
                                 Location destination,
                                 boolean usedSearchFallback)
    {
        this.status = status;
        this.requestedLocation = requestedLocation;
        this.destination = destination;
        this.usedSearchFallback = usedSearchFallback;
    }

    public TransitionStatus getStatus()
    {
        return status;
    }

    public Location getRequestedLocation()
    {
        return requestedLocation;
    }

    public Location getDestination()
    {
        return destination;
    }

    public boolean usedSearchFallback()
    {
        return usedSearchFallback;
    }

    public boolean isSuccessful()
    {
        return status == TransitionStatus.MOVED ||
               status == TransitionStatus.STAYED ||
               status == TransitionStatus.REROUTED ||
               status == TransitionStatus.SPAWNED;
    }
}
