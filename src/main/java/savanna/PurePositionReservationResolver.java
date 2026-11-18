package savanna;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * R1's pure, deterministic position resolver.
 *
 * <p>It accepts only immutable position facts, claims, topology and a step
 * salt. It never reads or mutates Simulator, Field, Animal, resources, events
 * or an ecological random source. Candidate expansion is delegated to a
 * precomputed {@link PositionTopology}; this class performs no BFS.</p>
 */
public final class PurePositionReservationResolver
{
    private final PositionKernelPolicy policy;

    public PurePositionReservationResolver(PositionKernelPolicy policy)
    {
        this.policy = Objects.requireNonNull(policy, "position policy is required");
    }

    public PositionPlan resolve(StepStartPositionSnapshot snapshot,
                                PositionClaimBatch batch,
                                PositionTopology topology,
                                Set<Location> fixedOccupancy,
                                long stepSalt)
    {
        Objects.requireNonNull(snapshot, "step-start snapshot is required");
        Objects.requireNonNull(batch, "position claim batch is required");
        Objects.requireNonNull(topology, "position topology is required");
        if(!policy.getVersion().equals(topology.getPolicy().getVersion())) {
            throw new IllegalArgumentException(
                "Resolver and topology position policies differ");
        }
        Set<Location> fixed = immutableFixed(fixedOccupancy);
        validateSnapshot(snapshot, topology, fixed);

        PositionCandidatePolicy candidatePolicy = new PositionCandidatePolicy(
            topology, policy);
        Map<PositionActorKey, PositionClaim> explicit = explicitClaims(snapshot,
            batch);
        List<ClaimRuntime> runtimes = new ArrayList<>();
        Map<PositionActorKey, PositionResolution> directResolutions =
            new LinkedHashMap<>();
        Map<PositionActorKey, Integer> directDebt = new LinkedHashMap<>();
        long candidateLimit = 0L;

        for(long adultId : snapshot.getAdultIds()) {
            PositionActorKey actor = PositionActorKey.adult(adultId);
            Location origin = snapshot.originOf(adultId);
            if(batch.isReleased(actor)) {
                directResolutions.put(actor, new PositionResolution(actor,
                    PositionClaimKind.STAY, origin, null, null, -1,
                    PositionPlanDisposition.RELEASED,
                    PositionResolutionReason.CLAIM_CANCELLED));
                directDebt.put(actor, 0);
                continue;
            }
            if(batch.isCancelled(actor)) {
                PositionClaim cancelledStay = PositionClaim.stay(adultId,
                    PositionPriority.ORDINARY_MOVE, 0);
                ClaimRuntime runtime = new ClaimRuntime(cancelledStay, origin,
                    true, false, candidatePolicy);
                runtimes.add(runtime);
                candidateLimit += runtime.candidateLimit();
                continue;
            }
            PositionClaim claim = explicit.get(actor);
            boolean implicitStay = claim == null;
            if(implicitStay) {
                claim = PositionClaim.stay(adultId,
                    PositionPriority.ORDINARY_MOVE, 0);
            }
            claim = normalizeAdultClaim(claim, origin);
            ClaimRuntime runtime = new ClaimRuntime(claim, origin, false,
                implicitStay, candidatePolicy);
            runtimes.add(runtime);
            candidateLimit += runtime.candidateLimit();
        }

        for(PositionClaim claim : batch.getClaims()) {
            if(!claim.getActor().isSpawn()) {
                continue;
            }
            ClaimRuntime runtime = new ClaimRuntime(claim, null, false, false,
                candidatePolicy);
            runtimes.add(runtime);
            candidateLimit += runtime.candidateLimit();
        }
        for(PositionActorKey actor : batch.getCancelled()) {
            if(!actor.isSpawn()) {
                continue;
            }
            directResolutions.put(actor, new PositionResolution(actor,
                PositionClaimKind.SPAWN, null, null, null, -1,
                PositionPlanDisposition.REJECTED,
                PositionResolutionReason.CLAIM_CANCELLED));
            directDebt.put(actor, 0);
        }

        Comparator<ClaimRuntime> order = claimOrder(stepSalt);
        runtimes.sort(order);
        Queue<ClaimRuntime> pending = new ArrayDeque<>(runtimes);
        Map<Location, ClaimRuntime> holderByCell = new HashMap<>();
        Map<PositionActorKey, Assignment> assignmentByActor = new HashMap<>();
        ResolutionCounters counters = new ResolutionCounters();

        while(!pending.isEmpty()) {
            ClaimRuntime claimant = pending.remove();
            resolveClaimant(claimant, snapshot, topology, fixed, holderByCell,
                assignmentByActor, pending, counters, stepSalt);
        }

        Map<PositionActorKey, PositionResolution> resolutions =
            new LinkedHashMap<>(directResolutions);
        Map<PositionActorKey, Integer> nextDebt = new LinkedHashMap<>(directDebt);
        for(ClaimRuntime runtime : runtimes) {
            Assignment assignment = assignmentByActor.get(runtime.actor());
            PositionResolution resolution = buildResolution(runtime, assignment);
            resolutions.put(runtime.actor(), resolution);
            nextDebt.put(runtime.actor(), nextWaitingDebt(runtime, resolution));
        }

        PositionResolutionMetrics metrics = new PositionResolutionMetrics(
            runtimes.size() + directResolutions.size(), counters.candidateVisits,
            candidateLimit, counters.proposals, counters.contentions,
            counters.requeues);
        PositionPlan plan = new PositionPlan(policy.getVersion(), stepSalt,
            resolutions, nextDebt, metrics);
        PositionPlanValidator.requireValid(snapshot, plan, topology, fixed,
            batch.getReleased());
        return plan;
    }

    private Map<PositionActorKey, PositionClaim> explicitClaims(
        StepStartPositionSnapshot snapshot, PositionClaimBatch batch)
    {
        Map<PositionActorKey, PositionClaim> result = new LinkedHashMap<>();
        for(PositionClaim claim : batch.getClaims()) {
            PositionActorKey actor = claim.getActor();
            if(actor.isAdult() && !snapshot.containsAdult(actor.getAdultId())) {
                throw new IllegalArgumentException(
                    "Claim references a non-living adult: " + actor);
            }
            if(claim.getWaitingDebt() > policy.getWaitingDebtCap()) {
                throw new IllegalArgumentException(
                    "Claim waiting debt exceeds registered policy: " + actor);
            }
            if(claim.getSearchCandidateBudget() >
               policy.getFallbackCandidateBudget()) {
                throw new IllegalArgumentException(
                    "Claim candidate budget exceeds registered policy: " + actor);
            }
            result.put(actor, claim);
        }
        for(PositionActorKey actor : batch.getCancelled()) {
            if(actor.isAdult() && !snapshot.containsAdult(actor.getAdultId())) {
                throw new IllegalArgumentException(
                    "Cancellation references a non-living adult: " + actor);
            }
        }
        for(PositionActorKey actor : batch.getReleased()) {
            if(!actor.isAdult() || !snapshot.containsAdult(actor.getAdultId())) {
                throw new IllegalArgumentException(
                    "Release references a non-living step-start adult: " + actor);
            }
        }
        return result;
    }

    private PositionClaim normalizeAdultClaim(PositionClaim claim,
                                               Location origin)
    {
        if(claim.getKind() != PositionClaimKind.STAY) {
            return claim;
        }
        Location requested = claim.getRequested();
        if(requested != null && !requested.equals(origin)) {
            throw new IllegalArgumentException(
                "STAY claim must request its own step-start origin");
        }
        if(!claim.getExplicitFallbacks().isEmpty() ||
           claim.getSearchCandidateBudget() != 0) {
            throw new IllegalArgumentException(
                "STAY claim cannot carry fallback candidates");
        }
        return new PositionClaim(claim.getActor(), PositionClaimKind.STAY,
            claim.getPriority(), origin, Collections.<Location>emptyList(),
            null, 0, claim.getWaitingDebt());
    }

    private void resolveClaimant(ClaimRuntime claimant,
                                 StepStartPositionSnapshot snapshot,
                                 PositionTopology topology,
                                 Set<Location> fixed,
                                 Map<Location, ClaimRuntime> holderByCell,
                                 Map<PositionActorKey, Assignment> assignments,
                                 Queue<ClaimRuntime> pending,
                                 ResolutionCounters counters, long stepSalt)
    {
        if(assignments.containsKey(claimant.actor())) {
            throw new IllegalStateException("Assigned claimant was requeued: " +
                claimant.actor());
        }
        while(claimant.cursor.hasNext()) {
            PositionCandidate candidate = claimant.cursor.next();
            counters.candidateVisits++;
            Location cell = candidate.location();
            if(!topology.isPassable(cell)) {
                claimant.noteImpassable(candidate);
                continue;
            }
            if(fixed.contains(cell)) {
                claimant.noteFixed(candidate);
                continue;
            }
            counters.proposals++;
            ClaimRuntime incumbent = holderByCell.get(cell);
            if(incumbent == null) {
                Assignment assignment = new Assignment(cell, candidate);
                holderByCell.put(cell, claimant);
                assignments.put(claimant.actor(), assignment);
                return;
            }
            counters.contentions++;
            ClaimRuntime winner = betterForCell(claimant, incumbent, cell,
                snapshot, stepSalt);
            ClaimRuntime loser = winner == claimant ? incumbent : claimant;
            loser.noteContentionLoss(winner, cell, snapshot, policy);
            if(winner == claimant) {
                assignments.remove(incumbent.actor());
                holderByCell.put(cell, claimant);
                assignments.put(claimant.actor(), new Assignment(cell, candidate));
                pending.add(incumbent);
                counters.requeues++;
                return;
            }
        }
    }

    private ClaimRuntime betterForCell(ClaimRuntime challenger,
                                       ClaimRuntime incumbent, Location cell,
                                       StepStartPositionSnapshot snapshot,
                                       long stepSalt)
    {
        boolean challengerOwner = challenger.ownsOrigin(cell, snapshot);
        boolean incumbentOwner = incumbent.ownsOrigin(cell, snapshot);
        if(challengerOwner != incumbentOwner) {
            return challengerOwner ? challenger : incumbent;
        }
        int priority = Integer.compare(policy.rank(challenger.priority()),
            policy.rank(incumbent.priority()));
        if(priority != 0) {
            return priority > 0 ? challenger : incumbent;
        }
        int debt = Integer.compare(challenger.waitingDebt(),
            incumbent.waitingDebt());
        if(debt != 0) {
            return debt > 0 ? challenger : incumbent;
        }
        int rotation = Long.compareUnsigned(rotationKey(challenger.actor(), stepSalt),
            rotationKey(incumbent.actor(), stepSalt));
        if(rotation != 0) {
            return rotation < 0 ? challenger : incumbent;
        }
        return challenger.actor().compareTo(incumbent.actor()) <= 0
            ? challenger : incumbent;
    }

    private Comparator<ClaimRuntime> claimOrder(long stepSalt)
    {
        return (first, second) -> {
            int priority = Integer.compare(policy.rank(second.priority()),
                policy.rank(first.priority()));
            if(priority != 0) {
                return priority;
            }
            int debt = Integer.compare(second.waitingDebt(), first.waitingDebt());
            if(debt != 0) {
                return debt;
            }
            int rotation = Long.compareUnsigned(rotationKey(first.actor(), stepSalt),
                rotationKey(second.actor(), stepSalt));
            return rotation != 0 ? rotation :
                first.actor().compareTo(second.actor());
        };
    }

    private static long rotationKey(PositionActorKey actor, long stepSalt)
    {
        long value = actor.getParentOrAdultId() ^
            (stepSalt * 0x9E3779B97F4A7C15L);
        value ^= ((long)actor.getLitterOrdinal() + 0x632BE59BD9B4E019L);
        value ^= actor.isSpawn() ? 0x94D049BB133111EBL : 0L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ (value >>> 33);
    }

    private PositionResolution buildResolution(ClaimRuntime runtime,
                                               Assignment assignment)
    {
        if(assignment == null) {
            return new PositionResolution(runtime.actor(), runtime.kind(),
                runtime.requested(), null, null, -1,
                PositionPlanDisposition.REJECTED, runtime.rejectionReason());
        }
        PositionCandidate candidate = assignment.candidate;
        PositionResolutionReason reason = runtime.commitReason(candidate);
        return new PositionResolution(runtime.actor(), runtime.kind(),
            runtime.requested(), assignment.destination, candidate.source(),
            candidate.index(), PositionPlanDisposition.COMMITTED, reason);
    }

    private int nextWaitingDebt(ClaimRuntime runtime,
                                PositionResolution resolution)
    {
        boolean blockedByContention = runtime.hasContentionLoss() &&
            (!resolution.isCommitted() ||
             (runtime.isAdult() && runtime.origin().equals(
                 resolution.getDestination()) &&
              runtime.kind() == PositionClaimKind.MOVE));
        if(!blockedByContention) {
            return 0;
        }
        return Math.min(policy.getWaitingDebtCap(), runtime.waitingDebt() + 1);
    }

    private void validateSnapshot(StepStartPositionSnapshot snapshot,
                                  PositionTopology topology,
                                  Set<Location> fixed)
    {
        for(long adultId : snapshot.getAdultIds()) {
            Location origin = snapshot.originOf(adultId);
            if(!topology.isPassable(origin)) {
                throw new IllegalArgumentException(
                    "Living step-start origin is not passable: " + adultId +
                    '@' + origin);
            }
            if(fixed.contains(origin)) {
                throw new IllegalArgumentException(
                    "Fixed occupancy overlaps living step-start origin: " +
                    origin);
            }
        }
    }

    private static Set<Location> immutableFixed(Set<Location> fixedOccupancy)
    {
        if(fixedOccupancy == null || fixedOccupancy.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Location> copy = new HashSet<>();
        for(Location location : fixedOccupancy) {
            if(location != null) {
                copy.add(new Location(location.row(), location.col()));
            }
        }
        return Collections.unmodifiableSet(copy);
    }

    private static final class Assignment
    {
        private final Location destination;
        private final PositionCandidate candidate;

        private Assignment(Location destination, PositionCandidate candidate)
        {
            this.destination = destination;
            this.candidate = candidate;
        }
    }

    private static final class ResolutionCounters
    {
        private long candidateVisits;
        private long proposals;
        private long contentions;
        private long requeues;
    }

    private static final class ClaimRuntime
    {
        private final PositionClaim claim;
        private final Location origin;
        private final boolean cancelled;
        private final boolean implicitStay;
        private final PositionCandidateCursor cursor;
        private boolean sawFixed;
        private boolean sawImpassable;
        private boolean ownerProtectionObserved;
        private PositionResolutionReason lastContentionReason;

        private ClaimRuntime(PositionClaim claim, Location origin,
                             boolean cancelled, boolean implicitStay,
                             PositionCandidatePolicy candidatePolicy)
        {
            this.claim = claim;
            this.origin = origin;
            this.cancelled = cancelled;
            this.implicitStay = implicitStay;
            cursor = candidatePolicy.cursorFor(claim, origin);
        }

        private PositionActorKey actor() { return claim.getActor(); }
        private PositionClaimKind kind() { return claim.getKind(); }
        private PositionPriority priority() { return claim.getPriority(); }
        private int waitingDebt() { return claim.getWaitingDebt(); }
        private Location requested() { return claim.getRequested(); }
        private Location origin() { return origin; }
        private boolean isAdult() { return actor().isAdult(); }
        private long candidateLimit()
        {
            return claim.declaredCandidateUpperBound(isAdult());
        }

        private boolean ownsOrigin(Location cell,
                                   StepStartPositionSnapshot snapshot)
        {
            if(!isAdult() || origin == null || !origin.equals(cell)) {
                return false;
            }
            Long owner = snapshot.ownerAt(cell);
            return owner != null && owner.longValue() == actor().getAdultId();
        }

        private void noteFixed(PositionCandidate candidate)
        {
            sawFixed = true;
        }

        private void noteImpassable(PositionCandidate candidate)
        {
            sawImpassable = true;
        }

        private void noteContentionLoss(ClaimRuntime winner, Location cell,
                                        StepStartPositionSnapshot snapshot,
                                        PositionKernelPolicy policy)
        {
            if(winner.ownsOrigin(cell, snapshot)) {
                ownerProtectionObserved = true;
                lastContentionReason =
                    PositionResolutionReason.OWNER_STAY_PROTECTION;
            }
            else if(policy.rank(winner.priority()) != policy.rank(priority())) {
                lastContentionReason =
                    PositionResolutionReason.HIGHER_PRIORITY_LOSS;
            }
            else {
                lastContentionReason =
                    PositionResolutionReason.EQUAL_PRIORITY_ROTATION_LOSS;
            }
        }

        private boolean hasContentionLoss()
        {
            return lastContentionReason != null;
        }

        private PositionResolutionReason commitReason(PositionCandidate candidate)
        {
            if(cancelled) {
                return PositionResolutionReason.CLAIM_CANCELLED;
            }
            if(implicitStay) {
                return PositionResolutionReason.REQUESTED;
            }
            if(candidate.source() == PositionCandidateSource.REQUESTED) {
                return PositionResolutionReason.REQUESTED;
            }
            if(candidate.source() == PositionCandidateSource.EXPLICIT_FALLBACK) {
                return PositionResolutionReason.EXPLICIT_FALLBACK;
            }
            if(candidate.source() == PositionCandidateSource.SEARCH_FALLBACK) {
                return PositionResolutionReason.SEARCH_FALLBACK;
            }
            if(lastContentionReason != null) {
                return lastContentionReason;
            }
            if(ownerProtectionObserved) {
                return PositionResolutionReason.OWNER_STAY_PROTECTION;
            }
            if(sawFixed) {
                return PositionResolutionReason.FIXED_OBSTACLE;
            }
            if(sawImpassable) {
                return PositionResolutionReason.INVALID_REQUESTED;
            }
            return PositionResolutionReason.NO_PASSABLE_CANDIDATE;
        }

        private PositionResolutionReason rejectionReason()
        {
            if(cancelled) {
                return PositionResolutionReason.CLAIM_CANCELLED;
            }
            if(lastContentionReason != null) {
                return lastContentionReason;
            }
            if(sawFixed) {
                return PositionResolutionReason.FIXED_OBSTACLE;
            }
            if(sawImpassable) {
                return PositionResolutionReason.NO_PASSABLE_CANDIDATE;
            }
            return PositionResolutionReason.NO_PASSABLE_CANDIDATE;
        }
    }
}
