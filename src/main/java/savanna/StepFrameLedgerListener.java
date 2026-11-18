package savanna;

/** Read-only observer for one fully assembled immutable S0-S4 frame ledger. */
@FunctionalInterface
public interface StepFrameLedgerListener
{
    void onStepFrameLedger(StepFrameLedger ledger);
}
