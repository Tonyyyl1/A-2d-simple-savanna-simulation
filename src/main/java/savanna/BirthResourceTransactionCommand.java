package savanna;

import java.util.Objects;

/** Immutable input command for the pure birth-resource reducer. */
public final class BirthResourceTransactionCommand
{
    private final String commandId;
    private final BirthResourceCommandType type;
    private final String transactionId;
    private final BirthResourceTransactionPlan plan;
    private final Integer ordinal;

    private BirthResourceTransactionCommand(
        String commandId,
        BirthResourceCommandType type,
        String transactionId,
        BirthResourceTransactionPlan plan,
        Integer ordinal)
    {
        if(commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException("command ID is required");
        }
        this.commandId = commandId;
        this.type = Objects.requireNonNull(type, "command type is required");
        if(transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException(
                "transaction ID is required");
        }
        if(ordinal != null && ordinal.intValue() < 0) {
            throw new IllegalArgumentException(
                "ordinal must be non-negative");
        }
        this.transactionId = transactionId;
        this.plan = plan;
        this.ordinal = ordinal;
        validateShape();
    }

    public static BirthResourceTransactionCommand plan(
        String commandId, BirthResourceTransactionPlan plan)
    {
        Objects.requireNonNull(plan, "resource plan is required");
        return new BirthResourceTransactionCommand(commandId,
            BirthResourceCommandType.PLAN, plan.getTransactionId(),
            plan, null);
    }

    public static BirthResourceTransactionCommand reserveConception(
        String commandId, BirthResourceTransactionPlan plan)
    {
        Objects.requireNonNull(plan, "resource plan is required");
        return new BirthResourceTransactionCommand(commandId,
            BirthResourceCommandType.RESERVE_CONCEPTION,
            plan.getTransactionId(), plan, null);
    }

    public static BirthResourceTransactionCommand commitOrdinal(
        String commandId, String transactionId, int ordinal)
    {
        return ordinalCommand(commandId,
            BirthResourceCommandType.COMMIT_ORDINAL,
            transactionId, ordinal);
    }

    public static BirthResourceTransactionCommand keepPending(
        String commandId, String transactionId, int ordinal)
    {
        return ordinalCommand(commandId,
            BirthResourceCommandType.KEEP_PENDING,
            transactionId, ordinal);
    }

    public static BirthResourceTransactionCommand recordPregnancyLoss(
        String commandId, String transactionId, int ordinal)
    {
        return ordinalCommand(commandId,
            BirthResourceCommandType.RECORD_PREGNANCY_LOSS,
            transactionId, ordinal);
    }

    public static BirthResourceTransactionCommand close(
        String commandId, String transactionId)
    {
        return new BirthResourceTransactionCommand(commandId,
            BirthResourceCommandType.CLOSE, transactionId, null, null);
    }

    private static BirthResourceTransactionCommand ordinalCommand(
        String commandId, BirthResourceCommandType type,
        String transactionId, int ordinal)
    {
        return new BirthResourceTransactionCommand(commandId, type,
            transactionId, null, Integer.valueOf(ordinal));
    }

    private void validateShape()
    {
        boolean planCommand = type == BirthResourceCommandType.PLAN ||
            type == BirthResourceCommandType.RESERVE_CONCEPTION;
        boolean ordinalCommand =
            type == BirthResourceCommandType.COMMIT_ORDINAL ||
            type == BirthResourceCommandType.KEEP_PENDING ||
            type == BirthResourceCommandType.RECORD_PREGNANCY_LOSS;
        if(planCommand != (plan != null) ||
           ordinalCommand != (ordinal != null) ||
           type == BirthResourceCommandType.CLOSE &&
               (plan != null || ordinal != null)) {
            throw new IllegalArgumentException(
                "command payload does not match command type");
        }
        if(plan != null &&
           !transactionId.equals(plan.getTransactionId())) {
            throw new IllegalArgumentException(
                "command and plan transaction IDs differ");
        }
    }

    public String getCommandId()
    {
        return commandId;
    }

    public BirthResourceCommandType getType()
    {
        return type;
    }

    public String getTransactionId()
    {
        return transactionId;
    }

    public BirthResourceTransactionPlan getPlan()
    {
        return plan;
    }

    public Integer getOrdinal()
    {
        return ordinal;
    }

    @Override
    public boolean equals(Object other)
    {
        if(this == other) return true;
        if(!(other instanceof BirthResourceTransactionCommand)) return false;
        BirthResourceTransactionCommand command =
            (BirthResourceTransactionCommand)other;
        return commandId.equals(command.commandId) &&
            type == command.type &&
            transactionId.equals(command.transactionId) &&
            Objects.equals(plan, command.plan) &&
            Objects.equals(ordinal, command.ordinal);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(commandId, type, transactionId, plan, ordinal);
    }
}
