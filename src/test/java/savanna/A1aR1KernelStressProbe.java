package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reproducible R1 bounded-work evidence for the 10,000-claimant fixture. */
public final class A1aR1KernelStressProbe
{
    private A1aR1KernelStressProbe()
    {
    }

    public static void main(String[] args)
    {
        PositionKernelPolicy policy = PositionKernelPolicyRegistry.current();
        long topologyStarted = System.nanoTime();
        PositionTopology topology = PositionTopology.rectangular(110, 110,
            Collections.<Location>emptySet(), policy);
        long topologyNanos = System.nanoTime() - topologyStarted;
        Map<Long, Location> origins = new LinkedHashMap<>();
        List<PositionClaim> claims = new ArrayList<>();
        Location target = new Location(105, 105);
        long id = 1L;
        for(int row = 0; row < 100; row++) {
            for(int col = 0; col < 100; col++) {
                origins.put(id, new Location(row, col));
                claims.add(PositionClaim.move(id, PositionPriority.ORDINARY_MOVE,
                    target, Collections.<Location>emptyList(), 0, 0));
                id++;
            }
        }
        long resolveStarted = System.nanoTime();
        StepStartPositionSnapshot snapshot = StepStartPositionSnapshot.of(origins);
        PositionPlan plan = new PurePositionReservationResolver(policy).resolve(
            snapshot, PositionClaimBatch.of(claims), topology,
            Collections.<Location>emptySet(), 500L);
        long resolveNanos = System.nanoTime() - resolveStarted;
        PositionResolutionMetrics metrics = plan.getMetrics();
        boolean valid = PositionPlanValidator.validate(snapshot, plan, topology,
            Collections.<Location>emptySet()).isValid();
        if(!valid || metrics.getClaimants() != 10000 ||
           metrics.getCandidateVisits() > metrics.getCandidateVisitLimit()) {
            throw new AssertionError("R1 kernel stress invariant failed");
        }
        System.out.println("A1A_R1_KERNEL_STRESS PASS policy=" +
            policy.getVersion() + " claimants=" + metrics.getClaimants() +
            " candidateVisits=" + metrics.getCandidateVisits() +
            " candidateVisitLimit=" + metrics.getCandidateVisitLimit() +
            " proposals=" + metrics.getProposals() +
            " contentions=" + metrics.getContentions() +
            " requeues=" + metrics.getRequeues() +
            " topologyFallbacks=" + topology.getPrecomputedFallbackCount() +
            " topologyNanos=" + topologyNanos +
            " resolveNanos=" + resolveNanos + " planValid=" + valid);
    }
}
