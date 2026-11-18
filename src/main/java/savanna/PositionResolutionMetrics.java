package savanna;

/** Bounded-work accounting for a pure resolver invocation. */
public final class PositionResolutionMetrics
{
    private final int claimants;
    private final long candidateVisits;
    private final long candidateVisitLimit;
    private final long proposals;
    private final long contentions;
    private final long requeues;

    PositionResolutionMetrics(int claimants, long candidateVisits,
                              long candidateVisitLimit, long proposals,
                              long contentions, long requeues)
    {
        this.claimants = claimants;
        this.candidateVisits = candidateVisits;
        this.candidateVisitLimit = candidateVisitLimit;
        this.proposals = proposals;
        this.contentions = contentions;
        this.requeues = requeues;
        if(claimants < 0 || candidateVisits < 0 || candidateVisitLimit < 0 ||
           proposals < 0 || contentions < 0 || requeues < 0 ||
           candidateVisits > candidateVisitLimit || proposals > candidateVisits) {
            throw new IllegalArgumentException("Invalid position resolution metrics");
        }
    }

    public int getClaimants() { return claimants; }
    public long getCandidateVisits() { return candidateVisits; }
    public long getCandidateVisitLimit() { return candidateVisitLimit; }
    public long getProposals() { return proposals; }
    public long getContentions() { return contentions; }
    public long getRequeues() { return requeues; }
}
