package savanna;

import java.util.Objects;

/** Creates bounded lazy cursors from the registered policy and topology. */
public final class PositionCandidatePolicy
{
    private final PositionTopology topology;
    private final PositionKernelPolicy policy;

    public PositionCandidatePolicy(PositionTopology topology,
                                   PositionKernelPolicy policy)
    {
        this.topology = Objects.requireNonNull(topology, "topology is required");
        this.policy = Objects.requireNonNull(policy, "position policy is required");
        if(!policy.getVersion().equals(topology.getPolicy().getVersion())) {
            throw new IllegalArgumentException(
                "Topology and candidate policy versions differ");
        }
    }

    public PositionCandidateCursor cursorFor(PositionClaim claim,
                                             Location adultOrigin)
    {
        Objects.requireNonNull(claim, "position claim is required");
        if(claim.getSearchCandidateBudget() >
           policy.getFallbackCandidateBudget()) {
            throw new IllegalArgumentException(
                "Claim search budget exceeds registered position policy");
        }
        if(claim.getWaitingDebt() > policy.getWaitingDebtCap()) {
            throw new IllegalArgumentException(
                "Claim waiting debt exceeds registered position policy");
        }
        return new PositionCandidateCursor(claim, topology, adultOrigin);
    }
}
