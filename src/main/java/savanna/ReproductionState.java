package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One female's production reproduction state (plan v3.0 M1-2): open,
 * pregnant (a reserved {@link BirthResourceTransaction}) or in postpartum
 * cooldown. Every resource movement goes through the transaction reducer,
 * so maternal debit always equals committed child reserve plus explicit
 * loss plus pending escrow.
 */
public final class ReproductionState
{
    private BirthResourceTransactionState transaction =
        BirthResourceTransactionState.initial();
    private int conceptionStep = -1;
    private int dueStep = -1;
    private int cooldownUntilStep = Integer.MIN_VALUE;
    private int commandSequence;

    public boolean isPregnant()
    {
        return transaction.getPhase() == BirthResourceTransactionPhase.RESERVED;
    }

    public boolean isDue(int step)
    {
        return isPregnant() && step >= dueStep;
    }

    public boolean mayConceive(int step)
    {
        return !isPregnant() && step >= cooldownUntilStep;
    }

    public int getConceptionStep() { return conceptionStep; }
    public int getDueStep() { return dueStep; }
    public int getCooldownUntilStep() { return cooldownUntilStep; }

    /** Pending litter ordinals in ascending order. */
    public List<Integer> getPendingOrdinals()
    {
        if(!isPregnant()) return Collections.emptyList();
        List<Integer> pending = new ArrayList<>(transaction.getPendingOrdinals());
        Collections.sort(pending);
        return pending;
    }

    ResourceVector escrowOf(int ordinal)
    {
        return transaction.getOriginalReserve(ordinal);
    }

    /** Reserves maternal food and the newborn allowance for {@code litter} ordinals. */
    BirthResourceTransactionResult conceive(SavannahAnimal mother, int step,
                                            int litter)
    {
        if(!mayConceive(step) || litter < 1) {
            throw new IllegalStateException("conception is not permitted now");
        }
        SpeciesProfile profile = mother.getProfile();
        ResourceVector escrow = ReproductionPolicy.ordinalEscrow(profile);
        Map<Integer, ResourceVector> escrowByOrdinal = new LinkedHashMap<>();
        ResourceVector maternal = ResourceVector.ZERO;
        ResourceVector allowance = ResourceVector.ZERO;
        for(int ordinal = 0; ordinal < litter; ordinal++) {
            escrowByOrdinal.put(Integer.valueOf(ordinal), escrow);
            maternal = maternal.plus(new ResourceVector(escrow.getFoodUnits(),
                0L, 0L, 0L));
            allowance = allowance.plus(ReproductionPolicy.newbornAllowance());
        }
        String transactionId = "birth:" + mother.getId() + ':' + step;
        BirthResourceTransactionPlan plan = new BirthResourceTransactionPlan(
            transactionId, mother.getId(), maternal, allowance,
            ResourceVector.ZERO, escrowByOrdinal);
        transaction = BirthResourceTransaction.transition(
            BirthResourceTransactionState.initial(),
            BirthResourceTransactionCommand.plan(nextCommandId(transactionId),
                plan)).getState();
        BirthResourceTransactionResult result = apply(
            BirthResourceTransactionCommand.reserveConception(
                nextCommandId(transactionId), plan));
        conceptionStep = step;
        dueStep = step + ReproductionPolicy.gestationSteps(profile);
        return result;
    }

    BirthResourceTransactionResult commitOrdinal(int ordinal)
    {
        return apply(BirthResourceTransactionCommand.commitOrdinal(
            nextCommandId(transaction.getTransactionId()),
            transaction.getTransactionId(), ordinal));
    }

    BirthResourceTransactionResult recordLoss(int ordinal)
    {
        return apply(BirthResourceTransactionCommand.recordPregnancyLoss(
            nextCommandId(transaction.getTransactionId()),
            transaction.getTransactionId(), ordinal));
    }

    /** Loses every pending ordinal (mother died or the birth wait expired). */
    List<BirthResourceTransactionResult> loseAllPending()
    {
        List<BirthResourceTransactionResult> results = new ArrayList<>();
        for(Integer ordinal : getPendingOrdinals()) {
            results.add(recordLoss(ordinal.intValue()));
        }
        return results;
    }

    /** Closes a fully resolved pregnancy and starts the postpartum cooldown. */
    boolean closeIfResolved(int step, SpeciesProfile profile)
    {
        if(!isPregnant() || !transaction.getPendingOrdinals().isEmpty()) {
            return false;
        }
        apply(BirthResourceTransactionCommand.close(
            nextCommandId(transaction.getTransactionId()),
            transaction.getTransactionId()));
        cooldownUntilStep = step + ReproductionPolicy.cooldownSteps(profile);
        transaction = BirthResourceTransactionState.initial();
        conceptionStep = -1;
        dueStep = -1;
        return true;
    }

    private BirthResourceTransactionResult apply(
        BirthResourceTransactionCommand command)
    {
        BirthResourceTransactionResult result =
            BirthResourceTransaction.transition(transaction, command);
        transaction = result.getState();
        return result;
    }

    private String nextCommandId(String transactionId)
    {
        commandSequence++;
        return transactionId + "#" + commandSequence;
    }
}
