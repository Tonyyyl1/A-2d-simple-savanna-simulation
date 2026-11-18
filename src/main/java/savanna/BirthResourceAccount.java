package savanna;

/**
 * Run-wide totals of the production birth transactions (plan v3.0 M1-2).
 * Reserved maternal food always equals committed child food plus explicit
 * loss plus still-pending escrow.
 */
public final class BirthResourceAccount
{
    private long maternalFood;
    private long allowanceHydrationMicros;
    private long allowanceStaminaMicros;
    private long committedFood;
    private long lostFood;
    private int conceptions;
    private int committedBirths;
    private int lostOrdinals;
    private long provisionedFood;
    private int provisions;

    void record(BirthResourceTransactionResult result)
    {
        if(result == null || result.isReplayed()) return;
        for(BirthResourceLedgerEntry entry : result.getLedgerEntries()) {
            ResourceVector amount = entry.getAmount();
            switch(entry.getCommandType()) {
                case RESERVE_CONCEPTION:
                    conceptions++;
                    maternalFood += amount.getFoodUnits();
                    allowanceHydrationMicros += amount.getHydrationMicros();
                    allowanceStaminaMicros += amount.getStaminaMicros();
                    break;
                case COMMIT_ORDINAL:
                    committedBirths++;
                    committedFood += amount.getFoodUnits();
                    break;
                case RECORD_PREGNANCY_LOSS:
                    lostOrdinals++;
                    lostFood += amount.getFoodUnits();
                    break;
                default:
                    break;
            }
        }
    }

    /** D6: conserved mother-to-cub food transfers after kills. */
    void recordProvision(int food)
    {
        provisions++;
        provisionedFood += food;
    }

    public long getProvisionedFood() { return provisionedFood; }
    public int getProvisions() { return provisions; }
    public long getMaternalFood() { return maternalFood; }
    public long getAllowanceHydrationMicros() { return allowanceHydrationMicros; }
    public long getAllowanceStaminaMicros() { return allowanceStaminaMicros; }
    public long getCommittedFood() { return committedFood; }
    public long getLostFood() { return lostFood; }
    public long getPendingFood() { return maternalFood - committedFood - lostFood; }
    public int getConceptions() { return conceptions; }
    public int getCommittedBirths() { return committedBirths; }
    public int getLostOrdinals() { return lostOrdinals; }

    @Override
    public String toString()
    {
        return "conceptions=" + conceptions + " births=" + committedBirths +
               " lostOrdinals=" + lostOrdinals + " maternalFood=" + maternalFood +
               " committedFood=" + committedFood + " lostFood=" + lostFood +
               " pendingFood=" + getPendingFood() + " provisions=" + provisions +
               " provisionedFood=" + provisionedFood;
    }
}
