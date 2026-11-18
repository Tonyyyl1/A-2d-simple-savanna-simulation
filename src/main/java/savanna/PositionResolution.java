package savanna;

/** Immutable result for one actor in a total position plan. */
public final class PositionResolution
{
    private final PositionActorKey actor;
    private final PositionClaimKind claimKind;
    private final Location requested;
    private final Location destination;
    private final PositionCandidateSource candidateSource;
    private final int candidateIndex;
    private final PositionPlanDisposition disposition;
    private final PositionResolutionReason reason;

    PositionResolution(PositionActorKey actor, PositionClaimKind claimKind,
                       Location requested, Location destination,
                       PositionCandidateSource candidateSource,
                       int candidateIndex, PositionPlanDisposition disposition,
                       PositionResolutionReason reason)
    {
        if(actor == null || claimKind == null || disposition == null ||
           reason == null || candidateIndex < -1 ||
           (disposition == PositionPlanDisposition.COMMITTED &&
            destination == null) ||
           ((disposition == PositionPlanDisposition.REJECTED ||
             disposition == PositionPlanDisposition.RELEASED) &&
            destination != null)) {
            throw new IllegalArgumentException("Invalid position resolution");
        }
        this.actor = actor;
        this.claimKind = claimKind;
        this.requested = copy(requested);
        this.destination = copy(destination);
        this.candidateSource = candidateSource;
        this.candidateIndex = candidateIndex;
        this.disposition = disposition;
        this.reason = reason;
    }

    public PositionActorKey getActor() { return actor; }
    public PositionClaimKind getClaimKind() { return claimKind; }
    public Location getRequested() { return copy(requested); }
    public Location getDestination() { return copy(destination); }
    public PositionCandidateSource getCandidateSource() { return candidateSource; }
    public int getCandidateIndex() { return candidateIndex; }
    public PositionPlanDisposition getDisposition() { return disposition; }
    public PositionResolutionReason getReason() { return reason; }

    public boolean isCommitted()
    {
        return disposition == PositionPlanDisposition.COMMITTED;
    }

    private static Location copy(Location location)
    {
        return location;
    }
}
