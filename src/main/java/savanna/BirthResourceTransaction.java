package savanna;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/** Pure reducer for the disconnected birth-resource transaction kernel. */
public final class BirthResourceTransaction
{
    private BirthResourceTransaction()
    {
    }

    public static BirthResourceTransactionResult transition(
        BirthResourceTransactionState state,
        BirthResourceTransactionCommand command)
    {
        if(state == null || command == null) {
            throw new NullPointerException(
                "state and command are required");
        }

        if(command.getType() == BirthResourceCommandType.PLAN) {
            requirePhase(state, BirthResourceTransactionPhase.NEW);
            return new BirthResourceTransactionResult(
                state, Collections.emptyList(), false);
        }

        BirthResourceTransactionCommand processed =
            state.getProcessedCommand(command.getCommandId());
        if(processed != null) {
            if(!processed.equals(command)) {
                throw new IllegalStateException(
                    "command ID was reused with a different payload");
            }
            return new BirthResourceTransactionResult(
                state, Collections.emptyList(), true);
        }

        switch(command.getType()) {
            case RESERVE_CONCEPTION:
                return reserve(state, command);
            case COMMIT_ORDINAL:
                return commit(state, command);
            case KEEP_PENDING:
                return keepPending(state, command);
            case RECORD_PREGNANCY_LOSS:
                return recordLoss(state, command);
            case CLOSE:
                return close(state, command);
            default:
                throw new IllegalStateException(
                    "unsupported command type: " + command.getType());
        }
    }

    private static BirthResourceTransactionResult reserve(
        BirthResourceTransactionState state,
        BirthResourceTransactionCommand command)
    {
        requirePhase(state, BirthResourceTransactionPhase.NEW);
        BirthResourceTransactionPlan plan = command.getPlan();
        Map<String, BirthResourceTransactionCommand> history =
            state.copyProcessedCommands();
        history.put(command.getCommandId(), command);
        BirthResourceTransactionState reserved =
            BirthResourceTransactionState.reserved(plan, history);
        ResourceVector source = plan.getMaternalDebit().plus(
            plan.getRegisteredExternalAllowance());
        return accepted(reserved, command, null, source,
            "conception investment and ordinal escrow reserved");
    }

    private static BirthResourceTransactionResult commit(
        BirthResourceTransactionState state,
        BirthResourceTransactionCommand command)
    {
        requireReservedTransaction(state, command);
        int ordinal = command.getOrdinal().intValue();
        requirePending(state, ordinal);
        ResourceVector reserve = state.getOriginalReserve(ordinal);

        Map<Integer, ResourceVector> escrow =
            state.copyEscrowByOrdinal();
        escrow.remove(Integer.valueOf(ordinal));
        Set<Integer> pending = state.copyPendingOrdinals();
        pending.remove(Integer.valueOf(ordinal));
        Set<Integer> committed = state.copyCommittedOrdinals();
        committed.add(Integer.valueOf(ordinal));
        Map<String, BirthResourceTransactionCommand> history =
            historyWith(state, command);

        BirthResourceTransactionState next = state.evolve(
            BirthResourceTransactionPhase.RESERVED,
            escrow,
            pending,
            committed,
            state.copyLostOrdinals(),
            state.getCommittedChildReserve().plus(reserve),
            state.getExplicitLoss(),
            history);
        return accepted(next, command, Integer.valueOf(ordinal), reserve,
            "ordinal reserve committed to child");
    }

    private static BirthResourceTransactionResult keepPending(
        BirthResourceTransactionState state,
        BirthResourceTransactionCommand command)
    {
        requireReservedTransaction(state, command);
        int ordinal = command.getOrdinal().intValue();
        requirePending(state, ordinal);
        BirthResourceTransactionState next = state.evolve(
            BirthResourceTransactionPhase.RESERVED,
            state.copyEscrowByOrdinal(),
            state.copyPendingOrdinals(),
            state.copyCommittedOrdinals(),
            state.copyLostOrdinals(),
            state.getCommittedChildReserve(),
            state.getExplicitLoss(),
            historyWith(state, command));
        return accepted(next, command, Integer.valueOf(ordinal),
            ResourceVector.ZERO, "ordinal remains pending with zero delta");
    }

    private static BirthResourceTransactionResult recordLoss(
        BirthResourceTransactionState state,
        BirthResourceTransactionCommand command)
    {
        requireReservedTransaction(state, command);
        int ordinal = command.getOrdinal().intValue();
        requirePending(state, ordinal);
        ResourceVector reserve = state.getOriginalReserve(ordinal);

        Map<Integer, ResourceVector> escrow =
            state.copyEscrowByOrdinal();
        escrow.remove(Integer.valueOf(ordinal));
        Set<Integer> pending = state.copyPendingOrdinals();
        pending.remove(Integer.valueOf(ordinal));
        Set<Integer> lost = state.copyLostOrdinals();
        lost.add(Integer.valueOf(ordinal));

        BirthResourceTransactionState next = state.evolve(
            BirthResourceTransactionPhase.RESERVED,
            escrow,
            pending,
            state.copyCommittedOrdinals(),
            lost,
            state.getCommittedChildReserve(),
            state.getExplicitLoss().plus(reserve),
            historyWith(state, command));
        return accepted(next, command, Integer.valueOf(ordinal), reserve,
            "ordinal reserve recorded as explicit pregnancy loss");
    }

    private static BirthResourceTransactionResult close(
        BirthResourceTransactionState state,
        BirthResourceTransactionCommand command)
    {
        requireReservedTransaction(state, command);
        if(!state.getPendingOrdinals().isEmpty()) {
            throw new IllegalStateException(
                "cannot close while ordinals remain pending");
        }
        BirthResourceTransactionState next = state.evolve(
            BirthResourceTransactionPhase.CLOSED,
            state.copyEscrowByOrdinal(),
            state.copyPendingOrdinals(),
            state.copyCommittedOrdinals(),
            state.copyLostOrdinals(),
            state.getCommittedChildReserve(),
            state.getExplicitLoss(),
            historyWith(state, command));
        return accepted(next, command, null, ResourceVector.ZERO,
            "transaction closed");
    }

    private static void requireReservedTransaction(
        BirthResourceTransactionState state,
        BirthResourceTransactionCommand command)
    {
        requirePhase(state, BirthResourceTransactionPhase.RESERVED);
        if(!command.getTransactionId().equals(state.getTransactionId())) {
            throw new IllegalStateException(
                "command targets a different transaction");
        }
    }

    private static void requirePhase(BirthResourceTransactionState state,
                                     BirthResourceTransactionPhase phase)
    {
        if(state.getPhase() != phase) {
            throw new IllegalStateException(
                "command is illegal in phase " + state.getPhase());
        }
    }

    private static void requirePending(
        BirthResourceTransactionState state, int ordinal)
    {
        if(!state.getPendingOrdinals().contains(
               Integer.valueOf(ordinal))) {
            throw new IllegalStateException(
                "ordinal is not pending: " + ordinal);
        }
    }

    private static Map<String, BirthResourceTransactionCommand>
        historyWith(BirthResourceTransactionState state,
                    BirthResourceTransactionCommand command)
    {
        Map<String, BirthResourceTransactionCommand> history =
            state.copyProcessedCommands();
        BirthResourceTransactionCommand prior =
            history.put(command.getCommandId(), command);
        if(prior != null) {
            throw new IllegalStateException(
                "command ID already exists");
        }
        return history;
    }

    private static BirthResourceTransactionResult accepted(
        BirthResourceTransactionState state,
        BirthResourceTransactionCommand command,
        Integer ordinal,
        ResourceVector amount,
        String detail)
    {
        BirthResourceLedgerEntry entry = new BirthResourceLedgerEntry(
            command.getCommandId(), command.getTransactionId(),
            command.getType(), ordinal, amount, detail);
        return new BirthResourceTransactionResult(
            state, Collections.singletonList(entry), false);
    }
}
