package savanna;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Focused A1a-R1 fixtures for the isolated pure position kernel. */
public final class PositionKernelTests
{
    private static final PositionKernelPolicy POLICY =
        PositionKernelPolicyRegistry.current();

    private PositionKernelTests()
    {
    }

    public static void run()
    {
        TestSupport.startGroup("A1a-R1 pure position kernel tests");
        testPolicyRegistryAndTopology();
        testSingleStayMoveAndIllegalRequest();
        testExplicitSearchAndBlockedFallbacks();
        testSwapCyclesAndBranchChain();
        testContentionPriorityPermutationAndFairness();
        testPriorityMatrixAndSpawnBoundary();
        testFixedDeadCancellationAndSpawnOutcomes();
        testExhaustiveSmallMapInvariants();
        testTenThousandClaimantBound();
        TestSupport.finishGroup();
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("A1a-R1 pure position kernel fixture suite");
        run();
        TestSupport.finishSuite();
        if(TestSupport.hasFailures()) {
            System.exit(1);
        }
        System.out.println("A1A_R1_KERNEL_FIXTURES PASS policy=" +
            POLICY.getVersion());
    }

    private static void testPolicyRegistryAndTopology()
    {
        PositionTopology topology = grid(6, 6);
        TestSupport.assertEquals("R1 policy version is registered",
            "a1a-r1-position-policy-v1", POLICY.getVersion());
        TestSupport.assertEquals("R1 priority matrix begins with critical water",
            600, POLICY.rank(PositionPriority.CRITICAL_WATER));
        TestSupport.assertEquals("R1 fallback budget is typed descriptor value",
            PositionKernelPolicyRegistry.FALLBACK_CANDIDATE_BUDGET.getDefaultValue()
                .intValue(), POLICY.getFallbackCandidateBudget());
        TestSupport.assertTrue("R1 topology precomputes bounded fallback order",
            topology.getPrecomputedFallbackCount() > 0 &&
            topology.getPrecomputedFallbackCount() <=
                36 * POLICY.getFallbackCandidateBudget());
        List<Location> fallback = topology.getSearchFallbacks(new Location(3, 3), 4);
        TestSupport.assertEquals("R1 topology preserves requested fallback cap", 4,
            fallback.size());
        TestSupport.assertEquals("R1 topology sorts equal-distance fallback row-major",
            new Location(2, 2), fallback.get(0));
    }

    private static void testSingleStayMoveAndIllegalRequest()
    {
        PositionTopology topology = grid(4, 5);
        StepStartPositionSnapshot snapshot = snapshot(
            1L, new Location(1, 1), 2L, new Location(1, 3));
        PositionClaimBatch batch = PositionClaimBatch.of(Arrays.asList(
            PositionClaim.stay(1L, PositionPriority.ORDINARY_MOVE, 0),
            PositionClaim.move(2L, PositionPriority.ORDINARY_MOVE,
                new Location(2, 3), Collections.<Location>emptyList(), 0, 0)));
        PositionPlan plan = resolve(snapshot, batch, topology, 1L);
        TestSupport.assertEquals("R1 single stay keeps origin", new Location(1, 1),
            plan.destinationForAdult(1L));
        TestSupport.assertEquals("R1 single move commits requested cell",
            new Location(2, 3), plan.destinationForAdult(2L));
        TestSupport.assertEquals("R1 single move has requested reason",
            PositionResolutionReason.REQUESTED,
            plan.getResolution(PositionActorKey.adult(2L)).getReason());

        PositionPlan illegal = resolve(snapshot(1L, new Location(1, 1)),
            PositionClaimBatch.of(Collections.singletonList(PositionClaim.move(1L,
                PositionPriority.ORDINARY_MOVE, new Location(-1, 7),
                Collections.<Location>emptyList(), 0, 0))), topology, 2L);
        PositionResolution illegalResult = illegal.getResolution(
            PositionActorKey.adult(1L));
        TestSupport.assertEquals("R1 illegal requested cell preserves origin",
            new Location(1, 1), illegalResult.getDestination());
        TestSupport.assertEquals("R1 illegal requested cell is explicit reason",
            PositionResolutionReason.INVALID_REQUESTED, illegalResult.getReason());
    }

    private static void testExplicitSearchAndBlockedFallbacks()
    {
        PositionTopology topology = grid(5, 6);
        StepStartPositionSnapshot snapshot = snapshot(
            1L, new Location(2, 1), 2L, new Location(2, 2));
        PositionClaimBatch explicitBatch = PositionClaimBatch.of(Arrays.asList(
            PositionClaim.move(1L, PositionPriority.ORDINARY_MOVE,
                new Location(2, 2), Collections.singletonList(new Location(2, 3)),
                0, 0),
            PositionClaim.stay(2L, PositionPriority.ORDINARY_MOVE, 0)));
        PositionPlan explicit = resolve(snapshot, explicitBatch, topology, 3L);
        TestSupport.assertEquals("R1 explicit fallback commits after owner stay",
            new Location(2, 3), explicit.destinationForAdult(1L));
        TestSupport.assertEquals("R1 explicit fallback has reason",
            PositionResolutionReason.EXPLICIT_FALLBACK,
            explicit.getResolution(PositionActorKey.adult(1L)).getReason());

        PositionClaimBatch searchBatch = PositionClaimBatch.of(Arrays.asList(
            PositionClaim.move(1L, PositionPriority.ORDINARY_MOVE,
                new Location(2, 2), Collections.<Location>emptyList(), 4, 0),
            PositionClaim.stay(2L, PositionPriority.ORDINARY_MOVE, 0)));
        PositionPlan search = resolve(snapshot, searchBatch, topology, 4L);
        PositionResolution searchResult = search.getResolution(
            PositionActorKey.adult(1L));
        TestSupport.assertEquals("R1 search fallback has reason",
            PositionResolutionReason.SEARCH_FALLBACK, searchResult.getReason());
        TestSupport.assertFalse("R1 search fallback avoids protected owner cell",
            new Location(2, 2).equals(searchResult.getDestination()));
        TestSupport.assertFalse("R1 search fallback is not forced origin",
            new Location(2, 1).equals(searchResult.getDestination()));

        Set<Location> fixed = Collections.singleton(new Location(2, 3));
        PositionPlan blocked = resolve(snapshot(1L, new Location(2, 1)),
            PositionClaimBatch.of(Collections.singletonList(PositionClaim.move(1L,
                PositionPriority.ORDINARY_MOVE, new Location(2, 3),
                Collections.<Location>emptyList(), 0, 0))), topology, fixed, 5L);
        PositionResolution blockedResult = blocked.getResolution(
            PositionActorKey.adult(1L));
        TestSupport.assertEquals("R1 all blocked adult retains origin",
            new Location(2, 1), blockedResult.getDestination());
        TestSupport.assertEquals("R1 fixed obstacle reason is preserved",
            PositionResolutionReason.FIXED_OBSTACLE, blockedResult.getReason());

        PositionPlan spawnBlocked = resolve(emptySnapshot(),
            PositionClaimBatch.of(Collections.singletonList(PositionClaim.spawn(7L, 0,
                new Location(2, 3), Collections.<Location>emptyList(), null, 0, 0))),
            topology, fixed, 6L);
        TestSupport.assertEquals("R1 all blocked spawn rejects token",
            PositionPlanDisposition.REJECTED,
            spawnBlocked.getResolution(PositionActorKey.spawn(7L, 0)).getDisposition());
    }

    private static void testSwapCyclesAndBranchChain()
    {
        PositionTopology topology = grid(4, 10);
        StepStartPositionSnapshot swapSnapshot = snapshot(
            1L, new Location(1, 1), 2L, new Location(1, 2));
        PositionPlan swap = resolve(swapSnapshot, PositionClaimBatch.of(Arrays.asList(
            move(1L, new Location(1, 2)), move(2L, new Location(1, 1)))),
            topology, 7L);
        TestSupport.assertEquals("R1 two-way swap commits first target",
            new Location(1, 2), swap.destinationForAdult(1L));
        TestSupport.assertEquals("R1 two-way swap commits second target",
            new Location(1, 1), swap.destinationForAdult(2L));

        for(int size = 3; size <= 8; size++) {
            Map<Long, Location> origins = new LinkedHashMap<>();
            List<PositionClaim> claims = new ArrayList<>();
            for(int index = 0; index < size; index++) {
                long id = index + 1L;
                origins.put(id, new Location(1, index + 1));
            }
            for(int index = 0; index < size; index++) {
                long id = index + 1L;
                claims.add(move(id, new Location(1, (index + 1) % size + 1)));
            }
            PositionPlan cycle = resolve(StepStartPositionSnapshot.of(origins),
                PositionClaimBatch.of(claims), topology, 10L + size);
            boolean complete = true;
            for(int index = 0; index < size; index++) {
                complete = complete && new Location(1, (index + 1) % size + 1)
                    .equals(cycle.destinationForAdult(index + 1L));
            }
            TestSupport.assertTrue("R1 " + size + "-actor cycle commits", complete);
        }

        StepStartPositionSnapshot branchSnapshot = snapshot(
            1L, new Location(2, 1), 2L, new Location(2, 2),
            3L, new Location(2, 3), 4L, new Location(2, 4));
        PositionPlan branch = resolve(branchSnapshot, PositionClaimBatch.of(Arrays.asList(
            move(1L, new Location(2, 2)), move(2L, new Location(2, 3)),
            move(3L, new Location(2, 4)),
            PositionClaim.stay(4L, PositionPriority.ORDINARY_MOVE, 0))),
            topology, 20L);
        TestSupport.assertEquals("R1 branch chain protects final stay owner",
            new Location(2, 4), branch.destinationForAdult(4L));
        TestSupport.assertTrue("R1 branch chain remains total and unique",
            PositionPlanValidator.validate(branchSnapshot, branch, topology,
                Collections.<Location>emptySet()).isValid());

        StepStartPositionSnapshot stoppedCycleSnapshot = snapshot(
            1L, new Location(3, 1), 2L, new Location(3, 2),
            3L, new Location(3, 3));
        PositionPlan stoppedCycle = resolve(stoppedCycleSnapshot,
            PositionClaimBatch.of(Arrays.asList(move(1L, new Location(3, 2)),
                move(2L, new Location(3, 3)),
                PositionClaim.stay(3L, PositionPriority.ORDINARY_MOVE, 0))),
            topology, 21L);
        TestSupport.assertEquals("R1 stay inside cycle keeps owner origin",
            new Location(3, 3), stoppedCycle.destinationForAdult(3L));
        TestSupport.assertTrue("R1 stopped cycle preserves every origin owner",
            PositionPlanValidator.validate(stoppedCycleSnapshot, stoppedCycle,
                topology, Collections.<Location>emptySet()).isValid());
    }

    private static void testContentionPriorityPermutationAndFairness()
    {
        PositionTopology topology = grid(5, 8);
        StepStartPositionSnapshot snapshot = snapshot(
            10L, new Location(1, 1), 20L, new Location(1, 2));
        Location target = new Location(3, 4);
        PositionClaim higher = PositionClaim.move(10L,
            PositionPriority.SURVIVAL_MOVE, target,
            Collections.<Location>emptyList(), 0, 0);
        PositionClaim lower = PositionClaim.move(20L,
            PositionPriority.ORDINARY_MOVE, target,
            Collections.<Location>emptyList(), 0, 0);
        PositionPlan priority = resolve(snapshot,
            PositionClaimBatch.of(Arrays.asList(lower, higher)), topology, 22L);
        TestSupport.assertEquals("R1 higher priority wins same target", target,
            priority.destinationForAdult(10L));
        TestSupport.assertEquals("R1 lower priority retains origin",
            new Location(1, 2), priority.destinationForAdult(20L));
        TestSupport.assertEquals("R1 higher-priority loss is named",
            PositionResolutionReason.HIGHER_PRIORITY_LOSS,
            priority.getResolution(PositionActorKey.adult(20L)).getReason());

        PositionClaim first = move(10L, target);
        PositionClaim second = move(20L, target);
        PositionPlan permutationA = resolve(snapshot,
            PositionClaimBatch.of(Arrays.asList(first, second)), topology, 23L);
        PositionPlan permutationB = resolve(snapshot,
            PositionClaimBatch.of(Arrays.asList(second, first)), topology, 23L);
        TestSupport.assertEquals("R1 claim input permutation keeps adult 10 result",
            permutationA.destinationForAdult(10L),
            permutationB.destinationForAdult(10L));
        TestSupport.assertEquals("R1 claim input permutation keeps adult 20 result",
            permutationA.destinationForAdult(20L),
            permutationB.destinationForAdult(20L));

        Map<PositionActorKey, Integer> debts = new HashMap<>();
        Set<PositionActorKey> winners = new HashSet<>();
        for(int step = 0; step < 3; step++) {
            PositionClaimBatch batch = spawnContention(debts, 3);
            PositionPlan plan = resolve(emptySnapshot(), batch, topology,
                30L + step);
            for(int parent = 1; parent <= 3; parent++) {
                PositionActorKey key = PositionActorKey.spawn(parent, 0);
                if(plan.getResolution(key).isCommitted()) {
                    winners.add(key);
                }
                debts.put(key, plan.nextWaitingDebtFor(key));
            }
        }
        TestSupport.assertEquals("R1 equal-priority debt gives bounded 3-way progress",
            3, winners.size());

        Map<PositionActorKey, Integer> twoDebts = new HashMap<>();
        int lowIdWins = 0;
        int highIdWins = 0;
        for(int step = 0; step < 20; step++) {
            PositionClaimBatch batch = spawnContention(twoDebts, 2, 1L, 99999L);
            PositionPlan plan = resolve(emptySnapshot(), batch, topology,
                100L + step);
            for(long parent : new long[] {1L, 99999L}) {
                PositionActorKey key = PositionActorKey.spawn(parent, 0);
                if(plan.getResolution(key).isCommitted()) {
                    if(parent == 1L) lowIdWins++; else highIdWins++;
                }
                twoDebts.put(key, plan.nextWaitingDebtFor(key));
            }
        }
        TestSupport.assertTrue("R1 fairness does not permanently favour low ID",
            lowIdWins > 0 && highIdWins > 0);
        TestSupport.assertTrue("R1 fairness remains bounded across 20 steps",
            Math.abs(lowIdWins - highIdWins) <= 1);
    }

    private static void testPriorityMatrixAndSpawnBoundary()
    {
        PositionTopology topology = grid(6, 9);
        StepStartPositionSnapshot snapshot = snapshot(
            1L, new Location(1, 1), 2L, new Location(1, 2),
            3L, new Location(1, 3), 4L, new Location(1, 4),
            5L, new Location(1, 5));
        Location target = new Location(4, 4);
        List<PositionClaim> claims = Arrays.asList(
            PositionClaim.move(1L, PositionPriority.CRITICAL_WATER, target,
                Collections.<Location>emptyList(), 0, 0),
            PositionClaim.move(2L, PositionPriority.WATER_DEPARTURE, target,
                Collections.<Location>emptyList(), 0, 0),
            PositionClaim.move(3L, PositionPriority.PREDATOR_SPACING, target,
                Collections.<Location>emptyList(), 0, 0),
            PositionClaim.move(4L, PositionPriority.SURVIVAL_MOVE, target,
                Collections.<Location>emptyList(), 0, 0),
            PositionClaim.move(5L, PositionPriority.ORDINARY_MOVE, target,
                Collections.<Location>emptyList(), 0, 0));
        PositionPlan matrix = resolve(snapshot, PositionClaimBatch.of(claims),
            topology, 150L);
        TestSupport.assertEquals("R1 critical water wins full priority matrix", target,
            matrix.destinationForAdult(1L));
        TestSupport.assertEquals("R1 water departure preserves origin after loss",
            new Location(1, 2), matrix.destinationForAdult(2L));
        TestSupport.assertEquals("R1 predator spacing preserves origin after loss",
            new Location(1, 3), matrix.destinationForAdult(3L));
        TestSupport.assertEquals("R1 survival move preserves origin after loss",
            new Location(1, 4), matrix.destinationForAdult(4L));
        TestSupport.assertEquals("R1 ordinary move preserves origin after loss",
            new Location(1, 5), matrix.destinationForAdult(5L));

        StepStartPositionSnapshot adultSnapshot = snapshot(20L,
            new Location(2, 2));
        PositionClaim adult = PositionClaim.move(20L,
            PositionPriority.ORDINARY_MOVE, target,
            Collections.<Location>emptyList(), 0, 0);
        PositionClaim spawn = PositionClaim.spawn(20L, 0, target,
            Collections.<Location>emptyList(), null, 0, 0);
        PositionPlan spawnBoundary = resolve(adultSnapshot,
            PositionClaimBatch.of(Arrays.asList(spawn, adult)), topology, 151L);
        TestSupport.assertEquals("R1 adult wins before lowest-priority spawn", target,
            spawnBoundary.destinationForAdult(20L));
        TestSupport.assertEquals("R1 spawn token loses without child side effects",
            PositionPlanDisposition.REJECTED,
            spawnBoundary.getResolution(PositionActorKey.spawn(20L, 0))
                .getDisposition());
    }

    private static void testFixedDeadCancellationAndSpawnOutcomes()
    {
        PositionTopology topology = grid(5, 6);
        Location oldDeadOwnerCell = new Location(2, 3);
        PositionPlan deadOwner = resolve(snapshot(1L, new Location(2, 1)),
            PositionClaimBatch.of(Collections.singletonList(move(1L,
                oldDeadOwnerCell))), topology, 160L);
        TestSupport.assertEquals("R1 dead owner does not protect old cell",
            oldDeadOwnerCell, deadOwner.destinationForAdult(1L));

        PositionClaimBatch cancelled = PositionClaimBatch.builder()
            .cancel(PositionActorKey.adult(1L))
            .cancel(PositionActorKey.spawn(1L, 0)).build();
        PositionPlan cancelledPlan = resolve(snapshot(1L, new Location(2, 1)),
            cancelled, topology, 161L);
        TestSupport.assertEquals("R1 cancelled adult remains at origin",
            new Location(2, 1), cancelledPlan.destinationForAdult(1L));
        TestSupport.assertEquals("R1 cancelled adult has cancellation reason",
            PositionResolutionReason.CLAIM_CANCELLED,
            cancelledPlan.getResolution(PositionActorKey.adult(1L)).getReason());
        TestSupport.assertEquals("R1 cancelled spawn is rejected",
            PositionPlanDisposition.REJECTED,
            cancelledPlan.getResolution(PositionActorKey.spawn(1L, 0))
                .getDisposition());

        Location open = new Location(3, 3);
        PositionPlan spawned = resolve(emptySnapshot(), PositionClaimBatch.of(
            Collections.singletonList(PositionClaim.spawn(8L, 0, open,
                Collections.<Location>emptyList(), null, 0, 0))), topology, 162L);
        PositionResolution token = spawned.getResolution(PositionActorKey.spawn(8L, 0));
        TestSupport.assertEquals("R1 winning spawn token commits", open,
            token.getDestination());
        TestSupport.assertEquals("R1 winning spawn token reason requested",
            PositionResolutionReason.REQUESTED, token.getReason());
    }

    private static void testExhaustiveSmallMapInvariants()
    {
        PositionTopology topology = grid(2, 2);
        StepStartPositionSnapshot snapshot = snapshot(
            1L, new Location(0, 0), 2L, new Location(0, 1),
            3L, new Location(1, 0));
        List<Location> cells = Arrays.asList(new Location(0, 0),
            new Location(0, 1), new Location(1, 0), new Location(1, 1));
        boolean allValid = true;
        for(Location first : cells) {
            for(Location second : cells) {
                for(Location third : cells) {
                    PositionPlan plan = resolve(snapshot,
                        PositionClaimBatch.of(Arrays.asList(move(1L, first),
                            move(2L, second), move(3L, third))), topology,
                        200L + first.row() * 32L + first.col() * 16L +
                            second.row() * 8L + second.col() * 4L +
                            third.row() * 2L + third.col());
                    allValid = allValid && PositionPlanValidator.validate(snapshot,
                        plan, topology, Collections.<Location>emptySet()).isValid();
                }
            }
        }
        TestSupport.assertTrue("R1 exhaustive 2x2 plans are total unique and passable",
            allValid);
    }

    private static void testTenThousandClaimantBound()
    {
        PositionTopology topology = grid(110, 110);
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
        StepStartPositionSnapshot snapshot = StepStartPositionSnapshot.of(origins);
        long started = System.nanoTime();
        PositionPlan plan = resolve(snapshot, PositionClaimBatch.of(claims),
            topology, 500L);
        long elapsedMillis = (System.nanoTime() - started) / 1000000L;
        PositionResolutionMetrics metrics = plan.getMetrics();
        TestSupport.assertEquals("R1 synthetic stress has 10000 claimants", 10000,
            metrics.getClaimants());
        TestSupport.assertTrue("R1 synthetic stress respects candidate visit declaration",
            metrics.getCandidateVisits() <= metrics.getCandidateVisitLimit());
        TestSupport.assertTrue("R1 synthetic stress visits at most two candidates each",
            metrics.getCandidateVisits() <= 20000L);
        TestSupport.assertTrue("R1 synthetic stress plan is invariant-valid",
            PositionPlanValidator.validate(snapshot, plan, topology,
                Collections.<Location>emptySet()).isValid());
        TestSupport.assertTrue("R1 synthetic stress stays bounded on this machine",
            elapsedMillis < 15000L);
    }

    private static PositionClaim move(long id, Location requested)
    {
        return PositionClaim.move(id, PositionPriority.ORDINARY_MOVE, requested,
            Collections.<Location>emptyList(), 0, 0);
    }

    private static PositionClaimBatch spawnContention(
        Map<PositionActorKey, Integer> debts, int count, long... parentIds)
    {
        List<PositionClaim> claims = new ArrayList<>();
        for(int index = 0; index < count; index++) {
            long parent = parentIds.length == 0 ? index + 1L : parentIds[index];
            PositionActorKey key = PositionActorKey.spawn(parent, 0);
            claims.add(PositionClaim.spawn(parent, 0, new Location(4, 6),
                Collections.<Location>emptyList(), null, 0,
                debts.getOrDefault(key, 0)));
        }
        return PositionClaimBatch.of(claims);
    }

    private static PositionPlan resolve(StepStartPositionSnapshot snapshot,
                                        PositionClaimBatch batch,
                                        PositionTopology topology, long salt)
    {
        return resolve(snapshot, batch, topology,
            Collections.<Location>emptySet(), salt);
    }

    private static PositionPlan resolve(StepStartPositionSnapshot snapshot,
                                        PositionClaimBatch batch,
                                        PositionTopology topology,
                                        Set<Location> fixed, long salt)
    {
        return new PurePositionReservationResolver(POLICY).resolve(snapshot,
            batch, topology, fixed, salt);
    }

    private static PositionTopology grid(int depth, int width)
    {
        return PositionTopology.rectangular(depth, width,
            Collections.<Location>emptySet(), POLICY);
    }

    private static StepStartPositionSnapshot emptySnapshot()
    {
        return StepStartPositionSnapshot.of(Collections.<Long, Location>emptyMap());
    }

    private static StepStartPositionSnapshot snapshot(Object... values)
    {
        Map<Long, Location> origins = new LinkedHashMap<>();
        for(int index = 0; index < values.length; index += 2) {
            origins.put(((Long)values[index]).longValue(),
                (Location)values[index + 1]);
        }
        return StepStartPositionSnapshot.of(origins);
    }
}
