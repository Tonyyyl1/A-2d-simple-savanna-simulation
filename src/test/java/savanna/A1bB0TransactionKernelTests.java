package savanna;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A1b-B0 disconnected birth-resource transaction fixtures. */
public final class A1bB0TransactionKernelTests
{
    private static final int PROPERTY_SEQUENCE_COUNT = 10_000;

    private A1bB0TransactionKernelTests()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("A1b-B0 transaction kernel fixtures");
        run();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    public static void run()
    {
        TestSupport.startGroup(
            "A1b-B0 disconnected birth-resource transaction kernel");
        testPlanDoesNotMutate();
        testReserveOnce();
        testCommitOneOrdinalOnce();
        testLoserKeepsPendingZeroDelta();
        testPartialLitterIndependent();
        testRetryIdempotent();
        testDoubleCommitRejected();
        testLossExplicitAndConserved();
        testOverflowNegativeAndComponentMismatchRejected();
        testCommandOrderFailsClosed();
        testPropertyConservationTenThousandSequences();
        testZeroProductionCallersAndForbiddenDependencies();
        TestSupport.finishGroup("A1BB0_FIXTURES");
    }

    private static void testPlanDoesNotMutate()
    {
        BirthResourceTransactionPlan plan = samplePlan("f01", 3);
        BirthResourceTransactionState initial =
            BirthResourceTransactionState.initial();
        BirthResourceTransactionResult result =
            BirthResourceTransaction.transition(initial,
                BirthResourceTransactionCommand.plan("f01-plan", plan));
        BirthResourceTransactionResult replay =
            BirthResourceTransaction.transition(initial,
                BirthResourceTransactionCommand.plan("f01-plan", plan));
        TestSupport.assertTrue(
            "A1BB0 F01 PLAN returns the identical immutable state",
            initial == result.getState());
        TestSupport.assertEquals(
            "A1BB0 F01 PLAN emits no accounting ledger entry", 0,
            result.getLedgerEntries().size());
        TestSupport.assertTrue(
            "A1BB0 F01 PLAN leaves all resource buckets zero",
            initial.getConceptionInvestment().isZero() &&
            initial.getEscrowByOrdinal().isEmpty() &&
            initial.getCommittedChildReserve().isZero() &&
            initial.getExplicitLoss().isZero());
        TestSupport.assertTrue(
            "A1BB0 F01 repeated PLAN is functionally idempotent",
            replay.getState() == initial &&
            replay.getLedgerEntries().isEmpty());
    }

    private static void testReserveOnce()
    {
        BirthResourceTransactionPlan plan = samplePlan("f02", 3);
        BirthResourceTransactionCommand reserve =
            BirthResourceTransactionCommand.reserveConception(
                "f02-reserve", plan);
        BirthResourceTransactionState state =
            BirthResourceTransaction.transition(
                BirthResourceTransactionState.initial(), reserve)
                .getState();

        TestSupport.assertEquals(
            "A1BB0 F02 reserve enters RESERVED exactly once",
            BirthResourceTransactionPhase.RESERVED, state.getPhase());
        TestSupport.assertEquals(
            "A1BB0 F02 reserve retains every ordinal in escrow",
            plan.getEscrowByOrdinal(), state.getEscrowByOrdinal());
        TestSupport.assertTrue(
            "A1BB0 F02 reserve is exactly conserved",
            isConserved(state));
        TestSupport.assertTrue(
            "A1BB0 F02 a second distinct reserve fails closed",
            rejects(() -> BirthResourceTransaction.transition(state,
                BirthResourceTransactionCommand.reserveConception(
                    "f02-second-reserve", plan))));
        TestSupport.assertTrue(
            "A1BB0 F02 state collections are immutable",
            rejects(() -> state.getPendingOrdinals().clear()) &&
            rejects(() -> state.getEscrowByOrdinal().clear()));
    }

    private static void testCommitOneOrdinalOnce()
    {
        BirthResourceTransactionPlan plan = samplePlan("f03", 3);
        BirthResourceTransactionState reserved = reserve(
            plan, "f03-reserve");
        ResourceVector expected = plan.getEscrowByOrdinal().get(
            Integer.valueOf(1));
        BirthResourceTransactionResult result =
            BirthResourceTransaction.transition(reserved,
                BirthResourceTransactionCommand.commitOrdinal(
                    "f03-commit-1", plan.getTransactionId(), 1));
        BirthResourceTransactionState state = result.getState();

        TestSupport.assertTrue(
            "A1BB0 F03 one ordinal moves escrow to committed reserve",
            !state.getPendingOrdinals().contains(Integer.valueOf(1)) &&
            state.getCommittedOrdinals().contains(Integer.valueOf(1)) &&
            expected.equals(state.getCommittedChildReserve()));
        TestSupport.assertEquals(
            "A1BB0 F03 commit ledger amount is the exact ordinal reserve",
            expected, result.getLedgerEntries().get(0).getAmount());
        TestSupport.assertTrue(
            "A1BB0 F03 commit remains exactly conserved",
            isConserved(state));
    }

    private static void testLoserKeepsPendingZeroDelta()
    {
        BirthResourceTransactionPlan plan = samplePlan("f04", 2);
        BirthResourceTransactionState before = reserve(
            plan, "f04-reserve");
        BirthResourceTransactionResult result =
            BirthResourceTransaction.transition(before,
                BirthResourceTransactionCommand.keepPending(
                    "f04-keep-1", plan.getTransactionId(), 1));
        BirthResourceTransactionState after = result.getState();

        boolean bucketsUnchanged =
            before.getConceptionInvestment().equals(
                after.getConceptionInvestment()) &&
            before.getEscrowByOrdinal().equals(
                after.getEscrowByOrdinal()) &&
            before.getCommittedChildReserve().equals(
                after.getCommittedChildReserve()) &&
            before.getExplicitLoss().equals(after.getExplicitLoss()) &&
            before.getPendingOrdinals().equals(
                after.getPendingOrdinals()) &&
            before.getCommittedOrdinals().equals(
                after.getCommittedOrdinals()) &&
            before.getLostOrdinals().equals(after.getLostOrdinals());
        TestSupport.assertTrue(
            "A1BB0 F04 KEEP_PENDING changes no resource or ordinal bucket",
            bucketsUnchanged);
        TestSupport.assertEquals(
            "A1BB0 F04 KEEP_PENDING ledger delta is exactly zero",
            ResourceVector.ZERO,
            result.getLedgerEntries().get(0).getAmount());
        TestSupport.assertTrue(
            "A1BB0 F04 kept ordinal remains conserved",
            isConserved(after));
    }

    private static void testPartialLitterIndependent()
    {
        BirthResourceTransactionPlan plan = samplePlan("f05", 3);
        BirthResourceTransactionState state = reserve(
            plan, "f05-reserve");
        state = BirthResourceTransaction.transition(state,
            BirthResourceTransactionCommand.commitOrdinal(
                "f05-commit-0", plan.getTransactionId(), 0)).getState();
        state = BirthResourceTransaction.transition(state,
            BirthResourceTransactionCommand.recordPregnancyLoss(
                "f05-loss-1", plan.getTransactionId(), 1)).getState();
        state = BirthResourceTransaction.transition(state,
            BirthResourceTransactionCommand.keepPending(
                "f05-keep-2", plan.getTransactionId(), 2)).getState();

        TestSupport.assertTrue(
            "A1BB0 F05 litter ordinals have independent lifecycle states",
            state.getCommittedOrdinals().equals(Set.of(0)) &&
            state.getLostOrdinals().equals(Set.of(1)) &&
            state.getPendingOrdinals().equals(Set.of(2)));
        TestSupport.assertTrue(
            "A1BB0 F05 partial litter state remains exactly conserved",
            isConserved(state));
    }

    private static void testRetryIdempotent()
    {
        BirthResourceTransactionPlan plan = samplePlan("f06", 2);
        BirthResourceTransactionCommand reserve =
            BirthResourceTransactionCommand.reserveConception(
                "f06-reserve", plan);
        BirthResourceTransactionState initial =
            BirthResourceTransactionState.initial();
        BirthResourceTransactionState reserved =
            BirthResourceTransaction.transition(initial, reserve)
                .getState();
        BirthResourceTransactionResult reserveReplay =
            BirthResourceTransaction.transition(reserved, reserve);

        BirthResourceTransactionCommand commit =
            BirthResourceTransactionCommand.commitOrdinal(
                "f06-commit-0", plan.getTransactionId(), 0);
        BirthResourceTransactionState committed =
            BirthResourceTransaction.transition(reserved, commit)
                .getState();
        BirthResourceTransactionResult commitReplay =
            BirthResourceTransaction.transition(committed, commit);

        TestSupport.assertTrue(
            "A1BB0 F06 reserve retry returns same state with no new ledger",
            reserveReplay.isReplayed() &&
            reserveReplay.getState() == reserved &&
            reserveReplay.getLedgerEntries().isEmpty());
        TestSupport.assertTrue(
            "A1BB0 F06 commit retry returns same state with no new ledger",
            commitReplay.isReplayed() &&
            commitReplay.getState() == committed &&
            commitReplay.getLedgerEntries().isEmpty());
        TestSupport.assertTrue(
            "A1BB0 F06 reused ID with changed payload fails closed",
            rejects(() -> BirthResourceTransaction.transition(committed,
                BirthResourceTransactionCommand.commitOrdinal(
                    "f06-commit-0", plan.getTransactionId(), 1))));
    }

    private static void testDoubleCommitRejected()
    {
        BirthResourceTransactionPlan plan = samplePlan("f07", 2);
        BirthResourceTransactionState state = reserve(
            plan, "f07-reserve");
        state = BirthResourceTransaction.transition(state,
            BirthResourceTransactionCommand.commitOrdinal(
                "f07-first", plan.getTransactionId(), 0)).getState();
        BirthResourceTransactionState committed = state;

        TestSupport.assertTrue(
            "A1BB0 F07 a second command cannot commit one ordinal twice",
            rejects(() -> BirthResourceTransaction.transition(committed,
                BirthResourceTransactionCommand.commitOrdinal(
                    "f07-second", plan.getTransactionId(), 0))));
    }

    private static void testLossExplicitAndConserved()
    {
        BirthResourceTransactionPlan plan = samplePlan("f08", 2);
        BirthResourceTransactionState reserved = reserve(
            plan, "f08-reserve");
        ResourceVector lostReserve = plan.getEscrowByOrdinal().get(
            Integer.valueOf(1));
        BirthResourceTransactionState state =
            BirthResourceTransaction.transition(reserved,
                BirthResourceTransactionCommand.recordPregnancyLoss(
                    "f08-loss-1", plan.getTransactionId(), 1))
                .getState();

        TestSupport.assertTrue(
            "A1BB0 F08 loss is explicit and removes only its escrow ordinal",
            state.getExplicitLoss().equals(lostReserve) &&
            state.getLostOrdinals().equals(Set.of(1)) &&
            state.getPendingOrdinals().equals(Set.of(0)));
        TestSupport.assertTrue(
            "A1BB0 F08 explicit loss remains exactly conserved",
            isConserved(state));
    }

    private static void
        testOverflowNegativeAndComponentMismatchRejected()
    {
        boolean negativeRejected = rejects(() ->
            new ResourceVector(-1L, 0L, 0L, 0L));
        boolean overflowRejected = rejects(() ->
            new ResourceVector(Long.MAX_VALUE, 0L, 0L, 0L).plus(
                new ResourceVector(1L, 0L, 0L, 0L)));

        Map<Integer, ResourceVector> escrow = new LinkedHashMap<>();
        escrow.put(Integer.valueOf(0),
            new ResourceVector(4L, 40L, 400L, 4L));
        boolean componentMismatchRejected = rejects(() ->
            new BirthResourceTransactionPlan(
                "f09-component-mismatch",
                7009L,
                new ResourceVector(5L, 50L, 500L, 5L),
                ResourceVector.ZERO,
                new ResourceVector(1L, 11L, 100L, 1L),
                escrow));

        TestSupport.assertTrue(
            "A1BB0 F09 negative fixed-point quantities are rejected",
            negativeRejected);
        TestSupport.assertTrue(
            "A1BB0 F09 fixed-point overflow is rejected",
            overflowRejected);
        TestSupport.assertTrue(
            "A1BB0 F09 cross-component mismatch cannot pass conservation",
            componentMismatchRejected);
    }

    private static void testCommandOrderFailsClosed()
    {
        BirthResourceTransactionPlan plan = samplePlan("f10", 1);
        BirthResourceTransactionState initial =
            BirthResourceTransactionState.initial();
        boolean commitBeforeReserve = rejects(() ->
            BirthResourceTransaction.transition(initial,
                BirthResourceTransactionCommand.commitOrdinal(
                    "f10-early-commit", plan.getTransactionId(), 0)));

        BirthResourceTransactionState reserved = reserve(
            plan, "f10-reserve");
        boolean planAfterReserve = rejects(() ->
            BirthResourceTransaction.transition(reserved,
                BirthResourceTransactionCommand.plan(
                    "f10-late-plan", plan)));
        boolean closeWithPending = rejects(() ->
            BirthResourceTransaction.transition(reserved,
                BirthResourceTransactionCommand.close(
                    "f10-early-close", plan.getTransactionId())));
        boolean wrongTransaction = rejects(() ->
            BirthResourceTransaction.transition(reserved,
                BirthResourceTransactionCommand.keepPending(
                    "f10-wrong-transaction", "another-transaction", 0)));

        BirthResourceTransactionState committed =
            BirthResourceTransaction.transition(reserved,
                BirthResourceTransactionCommand.commitOrdinal(
                    "f10-commit", plan.getTransactionId(), 0))
                .getState();
        BirthResourceTransactionState closed =
            BirthResourceTransaction.transition(committed,
                BirthResourceTransactionCommand.close(
                    "f10-close", plan.getTransactionId()))
                .getState();
        boolean mutateAfterClose = rejects(() ->
            BirthResourceTransaction.transition(closed,
                BirthResourceTransactionCommand.recordPregnancyLoss(
                    "f10-after-close", plan.getTransactionId(), 0)));

        TestSupport.assertTrue(
            "A1BB0 F10 illegal command order and target fail closed",
            commitBeforeReserve && planAfterReserve && closeWithPending &&
            wrongTransaction && mutateAfterClose);
        TestSupport.assertEquals(
            "A1BB0 F10 legal terminal transition reaches CLOSED",
            BirthResourceTransactionPhase.CLOSED, closed.getPhase());
    }

    private static void
        testPropertyConservationTenThousandSequences()
    {
        boolean passed = true;
        String failure = "";
        int completed = 0;
        try {
            for(int sequence = 0;
                sequence < PROPERTY_SEQUENCE_COUNT; sequence++) {
                runPropertySequence(sequence);
                completed++;
            }
        }
        catch(RuntimeException | AssertionError error) {
            passed = false;
            failure = " after " + completed + " sequences: " +
                error.getMessage();
        }
        TestSupport.assertTrue(
            "A1BB0 F11 exact conservation across " +
            PROPERTY_SEQUENCE_COUNT + " deterministic sequences" +
            failure,
            passed && completed == PROPERTY_SEQUENCE_COUNT);
    }

    private static void runPropertySequence(int sequence)
    {
        int ordinalCount = 1 + sequence % 5;
        Map<Integer, ResourceVector> escrow = new LinkedHashMap<>();
        for(int ordinal = 0; ordinal < ordinalCount; ordinal++) {
            long basis = 1L + sequence * 7L + ordinal * 11L;
            escrow.put(Integer.valueOf(ordinal), new ResourceVector(
                1L + basis % 31L,
                1L + basis % 101L,
                1L + basis % 211L,
                1L + basis % 17L));
        }
        ResourceVector conception = new ResourceVector(
            sequence % 13L,
            sequence % 29L,
            sequence % 37L,
            sequence % 7L);
        ResourceVector allocation = conception.plus(sum(escrow));
        ResourceVector external = new ResourceVector(
            allocation.getFoodUnits() / 3L,
            allocation.getHydrationMicros() / 4L,
            allocation.getStaminaMicros() / 5L,
            allocation.getReproductiveCreditUnits() / 2L);
        ResourceVector maternal = allocation.minus(external);
        String transactionId = "property-" + sequence;
        BirthResourceTransactionPlan plan =
            new BirthResourceTransactionPlan(
                transactionId, 100_000L + sequence,
                maternal, external, conception, escrow);
        BirthResourceTransactionCommand reserve =
            BirthResourceTransactionCommand.reserveConception(
                "reserve-" + sequence, plan);
        BirthResourceTransactionState state =
            BirthResourceTransaction.transition(
                BirthResourceTransactionState.initial(), reserve)
                .getState();
        requirePropertyState(state, escrow, sequence);

        for(int ordinal = 0; ordinal < ordinalCount; ordinal++) {
            int action = Math.floorMod(
                sequence * 31 + ordinal * 17, 3);
            BirthResourceTransactionCommand command;
            if(action == 0) {
                command = BirthResourceTransactionCommand.commitOrdinal(
                    "first-commit-" + sequence + '-' + ordinal,
                    transactionId, ordinal);
            }
            else if(action == 1) {
                command =
                    BirthResourceTransactionCommand
                        .recordPregnancyLoss(
                            "first-loss-" + sequence + '-' + ordinal,
                            transactionId, ordinal);
            }
            else {
                command = BirthResourceTransactionCommand.keepPending(
                    "first-keep-" + sequence + '-' + ordinal,
                    transactionId, ordinal);
            }
            state = acceptedState(state, command);
            requirePropertyState(state, escrow, sequence);
        }

        List<Integer> stillPending =
            new ArrayList<>(state.getPendingOrdinals());
        for(Integer ordinal : stillPending) {
            BirthResourceTransactionCommand command;
            if(((sequence + ordinal.intValue()) & 1) == 0) {
                command = BirthResourceTransactionCommand.commitOrdinal(
                    "final-commit-" + sequence + '-' + ordinal,
                    transactionId, ordinal.intValue());
            }
            else {
                command =
                    BirthResourceTransactionCommand
                        .recordPregnancyLoss(
                            "final-loss-" + sequence + '-' + ordinal,
                            transactionId, ordinal.intValue());
            }
            state = acceptedState(state, command);
            requirePropertyState(state, escrow, sequence);
        }

        state = acceptedState(state,
            BirthResourceTransactionCommand.close(
                "close-" + sequence, transactionId));
        requirePropertyState(state, escrow, sequence);
        if(state.getPhase() != BirthResourceTransactionPhase.CLOSED) {
            throw new AssertionError(
                "sequence did not close: " + sequence);
        }
    }

    private static BirthResourceTransactionState acceptedState(
        BirthResourceTransactionState state,
        BirthResourceTransactionCommand command)
    {
        BirthResourceTransactionResult result =
            BirthResourceTransaction.transition(state, command);
        if(result.isReplayed() ||
           result.getLedgerEntries().size() != 1) {
            throw new AssertionError(
                "accepted command did not emit exactly one ledger entry");
        }
        return result.getState();
    }

    private static void requirePropertyState(
        BirthResourceTransactionState state,
        Map<Integer, ResourceVector> originalEscrow,
        int sequence)
    {
        if(!isConserved(state) ||
           !state.getPendingOrdinals().equals(
               state.getEscrowByOrdinal().keySet())) {
            throw new AssertionError(
                "conservation or pending escrow mismatch at " + sequence);
        }
        Set<Integer> partition = new LinkedHashSet<>();
        partition.addAll(state.getPendingOrdinals());
        partition.addAll(state.getCommittedOrdinals());
        partition.addAll(state.getLostOrdinals());
        int lifecycleCount = state.getPendingOrdinals().size() +
            state.getCommittedOrdinals().size() +
            state.getLostOrdinals().size();
        if(!partition.equals(originalEscrow.keySet()) ||
           lifecycleCount != originalEscrow.size()) {
            throw new AssertionError(
                "ordinal partition mismatch at " + sequence);
        }
        ResourceVector committed = sumSelected(originalEscrow,
            state.getCommittedOrdinals());
        ResourceVector lost = sumSelected(originalEscrow,
            state.getLostOrdinals());
        if(!committed.equals(state.getCommittedChildReserve()) ||
           !lost.equals(state.getExplicitLoss())) {
            throw new AssertionError(
                "lifecycle resource bucket mismatch at " + sequence);
        }
    }

    private static void
        testZeroProductionCallersAndForbiddenDependencies()
    {
        A1bDependencyContract.Verification verification = null;
        try {
            V24GateProfile profile = V24GateProfile.load(Path.of(
                "gate-profiles", V24GateProfile.CORE_FILE),
                "8f7e15f14d7ffad2d7842f77dd69f234994ee8ee13ab9593abf53cb257f49557");
            verification = A1bDependencyContract.verify(Path.of(
                "src", "main", "java", "savanna"), profile);
        }
        catch(Exception error) {
            // Assertions below retain the canonical F12 failure IDs.
        }

        TestSupport.assertTrue(
            "A1BB0 F12 production source caller count is exactly zero",
            verification != null &&
            verification.getScan().getTransitionOwners().isEmpty() &&
            verification.getScan().getKernelConsumers().isEmpty());
        TestSupport.assertTrue(
            "A1BB0 F12 kernel has no forbidden runtime dependency",
            verification != null && verification.isPassed() &&
            verification.getScan().getForbiddenByKernelType().isEmpty());
    }

    private static BirthResourceTransactionPlan samplePlan(
        String suffix, int ordinalCount)
    {
        Map<Integer, ResourceVector> escrow = new LinkedHashMap<>();
        for(int ordinal = 0; ordinal < ordinalCount; ordinal++) {
            long multiplier = ordinal + 1L;
            escrow.put(Integer.valueOf(ordinal), new ResourceVector(
                10L * multiplier,
                100L * multiplier,
                1_000L * multiplier,
                2L * multiplier));
        }
        ResourceVector conception =
            new ResourceVector(7L, 70L, 700L, 3L);
        ResourceVector allocation = conception.plus(sum(escrow));
        ResourceVector external =
            new ResourceVector(2L, 20L, 200L, 1L);
        return new BirthResourceTransactionPlan(
            "transaction-" + suffix,
            7_000L + Integer.toUnsignedLong(suffix.hashCode()),
            allocation.minus(external),
            external,
            conception,
            escrow);
    }

    private static BirthResourceTransactionState reserve(
        BirthResourceTransactionPlan plan, String commandId)
    {
        return BirthResourceTransaction.transition(
            BirthResourceTransactionState.initial(),
            BirthResourceTransactionCommand.reserveConception(
                commandId, plan)).getState();
    }

    private static ResourceVector sum(
        Map<Integer, ResourceVector> resources)
    {
        ResourceVector total = ResourceVector.ZERO;
        for(ResourceVector resource : resources.values()) {
            total = total.plus(resource);
        }
        return total;
    }

    private static ResourceVector sumSelected(
        Map<Integer, ResourceVector> resources,
        Set<Integer> ordinals)
    {
        ResourceVector total = ResourceVector.ZERO;
        for(Integer ordinal : ordinals) {
            total = total.plus(resources.get(ordinal));
        }
        return total;
    }

    private static boolean isConserved(
        BirthResourceTransactionState state)
    {
        ResourceVector source = state.getMaternalDebit().plus(
            state.getRegisteredExternalAllowance());
        ResourceVector allocation = state.getConceptionInvestment()
            .plus(sum(state.getEscrowByOrdinal()))
            .plus(state.getCommittedChildReserve())
            .plus(state.getExplicitLoss());
        return source.equals(allocation);
    }

    private static boolean rejects(Runnable action)
    {
        try {
            action.run();
            return false;
        }
        catch(RuntimeException expected) {
            return true;
        }
    }
}
