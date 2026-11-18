package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable state and ledger output from one reducer transition. */
public final class BirthResourceTransactionResult
{
    private final BirthResourceTransactionState state;
    private final List<BirthResourceLedgerEntry> ledgerEntries;
    private final boolean replayed;

    BirthResourceTransactionResult(BirthResourceTransactionState state,
                                   List<BirthResourceLedgerEntry> entries,
                                   boolean replayed)
    {
        this.state = Objects.requireNonNull(
            state, "transaction state is required");
        Objects.requireNonNull(entries, "ledger entries are required");
        this.ledgerEntries = Collections.unmodifiableList(
            new ArrayList<>(entries));
        this.replayed = replayed;
    }

    public BirthResourceTransactionState getState()
    {
        return state;
    }

    public List<BirthResourceLedgerEntry> getLedgerEntries()
    {
        return ledgerEntries;
    }

    public boolean isReplayed()
    {
        return replayed;
    }
}
