package savanna;

import java.util.Objects;

/** Immutable ledger output from one accepted reducer command. */
public final class BirthResourceLedgerEntry
{
    private final String commandId;
    private final String transactionId;
    private final BirthResourceCommandType commandType;
    private final Integer ordinal;
    private final ResourceVector amount;
    private final String detail;

    BirthResourceLedgerEntry(String commandId,
                             String transactionId,
                             BirthResourceCommandType commandType,
                             Integer ordinal,
                             ResourceVector amount,
                             String detail)
    {
        if(commandId == null || commandId.isBlank() ||
           transactionId == null || transactionId.isBlank() ||
           detail == null || detail.isBlank()) {
            throw new IllegalArgumentException(
                "complete ledger identity and detail are required");
        }
        if(ordinal != null && ordinal.intValue() < 0) {
            throw new IllegalArgumentException(
                "ledger ordinal must be non-negative");
        }
        this.commandId = commandId;
        this.transactionId = transactionId;
        this.commandType = Objects.requireNonNull(
            commandType, "ledger command type is required");
        this.ordinal = ordinal;
        this.amount = Objects.requireNonNull(
            amount, "ledger amount is required");
        this.detail = detail;
    }

    public String getCommandId()
    {
        return commandId;
    }

    public String getTransactionId()
    {
        return transactionId;
    }

    public BirthResourceCommandType getCommandType()
    {
        return commandType;
    }

    public Integer getOrdinal()
    {
        return ordinal;
    }

    public ResourceVector getAmount()
    {
        return amount;
    }

    public String getDetail()
    {
        return detail;
    }
}
