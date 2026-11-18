package savanna;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable proposed source debit and allocation for one transaction.
 * Construction performs exact component-by-component conservation.
 */
public final class BirthResourceTransactionPlan
{
    private final String transactionId;
    private final long parentId;
    private final ResourceVector maternalDebit;
    private final ResourceVector registeredExternalAllowance;
    private final ResourceVector conceptionInvestment;
    private final Map<Integer, ResourceVector> escrowByOrdinal;

    public BirthResourceTransactionPlan(
        String transactionId,
        long parentId,
        ResourceVector maternalDebit,
        ResourceVector registeredExternalAllowance,
        ResourceVector conceptionInvestment,
        Map<Integer, ResourceVector> escrowByOrdinal)
    {
        if(transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException(
                "transaction ID is required");
        }
        if(parentId <= 0L) {
            throw new IllegalArgumentException(
                "parent ID must be positive");
        }
        this.transactionId = transactionId;
        this.parentId = parentId;
        this.maternalDebit = Objects.requireNonNull(
            maternalDebit, "maternal debit is required");
        this.registeredExternalAllowance = Objects.requireNonNull(
            registeredExternalAllowance,
            "registered external allowance is required");
        this.conceptionInvestment = Objects.requireNonNull(
            conceptionInvestment, "conception investment is required");
        this.escrowByOrdinal = immutableEscrow(escrowByOrdinal);

        ResourceVector source = maternalDebit.plus(
            registeredExternalAllowance);
        ResourceVector allocation = conceptionInvestment.plus(
            sum(this.escrowByOrdinal));
        if(!source.equals(allocation)) {
            throw new IllegalArgumentException(
                "resource plan is not exactly conserved by component");
        }
    }

    public String getTransactionId()
    {
        return transactionId;
    }

    public long getParentId()
    {
        return parentId;
    }

    public ResourceVector getMaternalDebit()
    {
        return maternalDebit;
    }

    public ResourceVector getRegisteredExternalAllowance()
    {
        return registeredExternalAllowance;
    }

    public ResourceVector getConceptionInvestment()
    {
        return conceptionInvestment;
    }

    public Map<Integer, ResourceVector> getEscrowByOrdinal()
    {
        return escrowByOrdinal;
    }

    static ResourceVector sum(Map<Integer, ResourceVector> resources)
    {
        ResourceVector result = ResourceVector.ZERO;
        for(ResourceVector resource : resources.values()) {
            result = result.plus(resource);
        }
        return result;
    }

    private static Map<Integer, ResourceVector> immutableEscrow(
        Map<Integer, ResourceVector> resources)
    {
        Objects.requireNonNull(resources, "ordinal escrow is required");
        TreeMap<Integer, ResourceVector> sorted = new TreeMap<>();
        for(Map.Entry<Integer, ResourceVector> entry :
            resources.entrySet()) {
            Integer ordinal = Objects.requireNonNull(
                entry.getKey(), "ordinal is required");
            if(ordinal.intValue() < 0) {
                throw new IllegalArgumentException(
                    "ordinal must be non-negative");
            }
            ResourceVector prior = sorted.put(ordinal,
                Objects.requireNonNull(entry.getValue(),
                    "ordinal resource is required"));
            if(prior != null) {
                throw new IllegalArgumentException(
                    "duplicate ordinal: " + ordinal);
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    @Override
    public boolean equals(Object other)
    {
        if(this == other) return true;
        if(!(other instanceof BirthResourceTransactionPlan)) return false;
        BirthResourceTransactionPlan plan =
            (BirthResourceTransactionPlan)other;
        return parentId == plan.parentId &&
            transactionId.equals(plan.transactionId) &&
            maternalDebit.equals(plan.maternalDebit) &&
            registeredExternalAllowance.equals(
                plan.registeredExternalAllowance) &&
            conceptionInvestment.equals(plan.conceptionInvestment) &&
            escrowByOrdinal.equals(plan.escrowByOrdinal);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(transactionId, parentId, maternalDebit,
            registeredExternalAllowance, conceptionInvestment,
            escrowByOrdinal);
    }
}
