package savanna;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable, exactly conserved state of one birth-resource transaction. */
public final class BirthResourceTransactionState
{
    private final BirthResourceTransactionPhase phase;
    private final String transactionId;
    private final long parentId;
    private final ResourceVector maternalDebit;
    private final ResourceVector registeredExternalAllowance;
    private final ResourceVector conceptionInvestment;
    private final Map<Integer, ResourceVector> reserveByOrdinal;
    private final Map<Integer, ResourceVector> escrowByOrdinal;
    private final Set<Integer> pendingOrdinals;
    private final Set<Integer> committedOrdinals;
    private final Set<Integer> lostOrdinals;
    private final ResourceVector committedChildReserve;
    private final ResourceVector explicitLoss;
    private final Map<String, BirthResourceTransactionCommand>
        processedCommands;

    private BirthResourceTransactionState(
        BirthResourceTransactionPhase phase,
        String transactionId,
        long parentId,
        ResourceVector maternalDebit,
        ResourceVector registeredExternalAllowance,
        ResourceVector conceptionInvestment,
        Map<Integer, ResourceVector> reserveByOrdinal,
        Map<Integer, ResourceVector> escrowByOrdinal,
        Set<Integer> pendingOrdinals,
        Set<Integer> committedOrdinals,
        Set<Integer> lostOrdinals,
        ResourceVector committedChildReserve,
        ResourceVector explicitLoss,
        Map<String, BirthResourceTransactionCommand> processedCommands)
    {
        this.phase = Objects.requireNonNull(
            phase, "transaction phase is required");
        this.transactionId = transactionId;
        this.parentId = parentId;
        this.maternalDebit = Objects.requireNonNull(
            maternalDebit, "maternal debit is required");
        this.registeredExternalAllowance = Objects.requireNonNull(
            registeredExternalAllowance,
            "registered external allowance is required");
        this.conceptionInvestment = Objects.requireNonNull(
            conceptionInvestment, "conception investment is required");
        this.reserveByOrdinal = immutableResources(reserveByOrdinal);
        this.escrowByOrdinal = immutableResources(escrowByOrdinal);
        this.pendingOrdinals = immutableOrdinals(pendingOrdinals);
        this.committedOrdinals = immutableOrdinals(committedOrdinals);
        this.lostOrdinals = immutableOrdinals(lostOrdinals);
        this.committedChildReserve = Objects.requireNonNull(
            committedChildReserve, "committed child reserve is required");
        this.explicitLoss = Objects.requireNonNull(
            explicitLoss, "explicit loss is required");
        this.processedCommands =
            immutableCommands(processedCommands);
        validate();
    }

    public static BirthResourceTransactionState initial()
    {
        return new BirthResourceTransactionState(
            BirthResourceTransactionPhase.NEW,
            null,
            0L,
            ResourceVector.ZERO,
            ResourceVector.ZERO,
            ResourceVector.ZERO,
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptySet(),
            Collections.emptySet(),
            Collections.emptySet(),
            ResourceVector.ZERO,
            ResourceVector.ZERO,
            Collections.emptyMap());
    }

    static BirthResourceTransactionState reserved(
        BirthResourceTransactionPlan plan,
        Map<String, BirthResourceTransactionCommand> processedCommands)
    {
        Set<Integer> pending =
            new LinkedHashSet<>(plan.getEscrowByOrdinal().keySet());
        return new BirthResourceTransactionState(
            BirthResourceTransactionPhase.RESERVED,
            plan.getTransactionId(),
            plan.getParentId(),
            plan.getMaternalDebit(),
            plan.getRegisteredExternalAllowance(),
            plan.getConceptionInvestment(),
            plan.getEscrowByOrdinal(),
            plan.getEscrowByOrdinal(),
            pending,
            Collections.emptySet(),
            Collections.emptySet(),
            ResourceVector.ZERO,
            ResourceVector.ZERO,
            processedCommands);
    }

    BirthResourceTransactionState evolve(
        BirthResourceTransactionPhase nextPhase,
        Map<Integer, ResourceVector> nextEscrowByOrdinal,
        Set<Integer> nextPendingOrdinals,
        Set<Integer> nextCommittedOrdinals,
        Set<Integer> nextLostOrdinals,
        ResourceVector nextCommittedChildReserve,
        ResourceVector nextExplicitLoss,
        Map<String, BirthResourceTransactionCommand> nextProcessedCommands)
    {
        return new BirthResourceTransactionState(
            nextPhase,
            transactionId,
            parentId,
            maternalDebit,
            registeredExternalAllowance,
            conceptionInvestment,
            reserveByOrdinal,
            nextEscrowByOrdinal,
            nextPendingOrdinals,
            nextCommittedOrdinals,
            nextLostOrdinals,
            nextCommittedChildReserve,
            nextExplicitLoss,
            nextProcessedCommands);
    }

    private void validate()
    {
        if(phase == BirthResourceTransactionPhase.NEW) {
            if(transactionId != null || parentId != 0L ||
               !maternalDebit.isZero() ||
               !registeredExternalAllowance.isZero() ||
               !conceptionInvestment.isZero() ||
               !reserveByOrdinal.isEmpty() ||
               !escrowByOrdinal.isEmpty() ||
               !pendingOrdinals.isEmpty() ||
               !committedOrdinals.isEmpty() ||
               !lostOrdinals.isEmpty() ||
               !committedChildReserve.isZero() ||
               !explicitLoss.isZero() ||
               !processedCommands.isEmpty()) {
                throw new IllegalStateException(
                    "new transaction state must be empty");
            }
            return;
        }

        if(transactionId == null || transactionId.isBlank() ||
           parentId <= 0L) {
            throw new IllegalStateException(
                "reserved transaction identity is incomplete");
        }
        if(!pendingOrdinals.equals(escrowByOrdinal.keySet())) {
            throw new IllegalStateException(
                "pending ordinals must exactly match escrow ordinals");
        }
        requireDisjoint(pendingOrdinals, committedOrdinals,
            "pending and committed ordinals overlap");
        requireDisjoint(pendingOrdinals, lostOrdinals,
            "pending and lost ordinals overlap");
        requireDisjoint(committedOrdinals, lostOrdinals,
            "committed and lost ordinals overlap");

        Set<Integer> partition = new TreeSet<>();
        partition.addAll(pendingOrdinals);
        partition.addAll(committedOrdinals);
        partition.addAll(lostOrdinals);
        if(!partition.equals(reserveByOrdinal.keySet())) {
            throw new IllegalStateException(
                "ordinal lifecycle partition is incomplete");
        }

        ResourceVector expectedCommitted =
            sumOrdinals(committedOrdinals);
        ResourceVector expectedLoss = sumOrdinals(lostOrdinals);
        if(!committedChildReserve.equals(expectedCommitted) ||
           !explicitLoss.equals(expectedLoss)) {
            throw new IllegalStateException(
                "ordinal resources do not match lifecycle buckets");
        }

        ResourceVector source = maternalDebit.plus(
            registeredExternalAllowance);
        ResourceVector allocation = conceptionInvestment
            .plus(BirthResourceTransactionPlan.sum(escrowByOrdinal))
            .plus(committedChildReserve)
            .plus(explicitLoss);
        if(!source.equals(allocation)) {
            throw new IllegalStateException(
                "transaction resource conservation failed");
        }

        for(Map.Entry<String, BirthResourceTransactionCommand> entry :
            processedCommands.entrySet()) {
            BirthResourceTransactionCommand command = entry.getValue();
            if(!entry.getKey().equals(command.getCommandId()) ||
               command.getType() == BirthResourceCommandType.PLAN ||
               !transactionId.equals(command.getTransactionId())) {
                throw new IllegalStateException(
                    "processed command history is inconsistent");
            }
        }
        if(phase == BirthResourceTransactionPhase.CLOSED &&
           !pendingOrdinals.isEmpty()) {
            throw new IllegalStateException(
                "closed transaction cannot retain pending ordinals");
        }
    }

    private ResourceVector sumOrdinals(Set<Integer> ordinals)
    {
        ResourceVector total = ResourceVector.ZERO;
        for(Integer ordinal : ordinals) {
            ResourceVector resource = reserveByOrdinal.get(ordinal);
            if(resource == null) {
                throw new IllegalStateException(
                    "ordinal has no original reserve: " + ordinal);
            }
            total = total.plus(resource);
        }
        return total;
    }

    private static void requireDisjoint(Set<Integer> first,
                                        Set<Integer> second,
                                        String message)
    {
        for(Integer ordinal : first) {
            if(second.contains(ordinal)) {
                throw new IllegalStateException(message);
            }
        }
    }

    private static Map<Integer, ResourceVector> immutableResources(
        Map<Integer, ResourceVector> resources)
    {
        Objects.requireNonNull(resources, "ordinal resources are required");
        TreeMap<Integer, ResourceVector> sorted = new TreeMap<>();
        for(Map.Entry<Integer, ResourceVector> entry :
            resources.entrySet()) {
            Integer ordinal = Objects.requireNonNull(
                entry.getKey(), "resource ordinal is required");
            if(ordinal.intValue() < 0) {
                throw new IllegalArgumentException(
                    "resource ordinal must be non-negative");
            }
            sorted.put(ordinal, Objects.requireNonNull(
                entry.getValue(), "ordinal resource is required"));
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Set<Integer> immutableOrdinals(Set<Integer> ordinals)
    {
        Objects.requireNonNull(ordinals, "ordinal set is required");
        TreeSet<Integer> sorted = new TreeSet<>();
        for(Integer ordinal : ordinals) {
            if(ordinal == null || ordinal.intValue() < 0) {
                throw new IllegalArgumentException(
                    "ordinal must be non-negative");
            }
            sorted.add(ordinal);
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }

    private static Map<String, BirthResourceTransactionCommand>
        immutableCommands(
            Map<String, BirthResourceTransactionCommand> commands)
    {
        Objects.requireNonNull(commands, "processed commands are required");
        LinkedHashMap<String, BirthResourceTransactionCommand> copy =
            new LinkedHashMap<>();
        for(Map.Entry<String, BirthResourceTransactionCommand> entry :
            commands.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(),
                         "processed command ID is required"),
                     Objects.requireNonNull(entry.getValue(),
                         "processed command is required"));
        }
        return Collections.unmodifiableMap(copy);
    }

    BirthResourceTransactionCommand getProcessedCommand(String commandId)
    {
        return processedCommands.get(commandId);
    }

    Map<String, BirthResourceTransactionCommand> copyProcessedCommands()
    {
        return new LinkedHashMap<>(processedCommands);
    }

    Map<Integer, ResourceVector> copyEscrowByOrdinal()
    {
        return new LinkedHashMap<>(escrowByOrdinal);
    }

    Set<Integer> copyPendingOrdinals()
    {
        return new LinkedHashSet<>(pendingOrdinals);
    }

    Set<Integer> copyCommittedOrdinals()
    {
        return new LinkedHashSet<>(committedOrdinals);
    }

    Set<Integer> copyLostOrdinals()
    {
        return new LinkedHashSet<>(lostOrdinals);
    }

    ResourceVector getOriginalReserve(int ordinal)
    {
        return reserveByOrdinal.get(Integer.valueOf(ordinal));
    }

    public BirthResourceTransactionPhase getPhase()
    {
        return phase;
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

    public Set<Integer> getPendingOrdinals()
    {
        return pendingOrdinals;
    }

    public Set<Integer> getCommittedOrdinals()
    {
        return committedOrdinals;
    }

    public Set<Integer> getLostOrdinals()
    {
        return lostOrdinals;
    }

    public ResourceVector getCommittedChildReserve()
    {
        return committedChildReserve;
    }

    public ResourceVector getExplicitLoss()
    {
        return explicitLoss;
    }

    public int getProcessedCommandCount()
    {
        return processedCommands.size();
    }

    @Override
    public boolean equals(Object other)
    {
        if(this == other) return true;
        if(!(other instanceof BirthResourceTransactionState)) return false;
        BirthResourceTransactionState state =
            (BirthResourceTransactionState)other;
        return parentId == state.parentId &&
            phase == state.phase &&
            Objects.equals(transactionId, state.transactionId) &&
            maternalDebit.equals(state.maternalDebit) &&
            registeredExternalAllowance.equals(
                state.registeredExternalAllowance) &&
            conceptionInvestment.equals(state.conceptionInvestment) &&
            reserveByOrdinal.equals(state.reserveByOrdinal) &&
            escrowByOrdinal.equals(state.escrowByOrdinal) &&
            pendingOrdinals.equals(state.pendingOrdinals) &&
            committedOrdinals.equals(state.committedOrdinals) &&
            lostOrdinals.equals(state.lostOrdinals) &&
            committedChildReserve.equals(state.committedChildReserve) &&
            explicitLoss.equals(state.explicitLoss) &&
            processedCommands.equals(state.processedCommands);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(phase, transactionId, parentId, maternalDebit,
            registeredExternalAllowance, conceptionInvestment,
            reserveByOrdinal, escrowByOrdinal, pendingOrdinals,
            committedOrdinals, lostOrdinals, committedChildReserve,
            explicitLoss, processedCommands);
    }
}
