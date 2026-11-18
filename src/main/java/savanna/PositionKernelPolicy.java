package savanna;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, non-tunable R1 policy descriptor for the pure position kernel.
 *
 * <p>The policy is deliberately not part of {@link SimulationConfig}: R1 is
 * not wired into the simulator and may not change ecological tuning. Its
 * version, priority matrix, fallback bound, search radius and waiting-debt
 * bound are centrally registered by {@link PositionKernelPolicyRegistry}.</p>
 */
public final class PositionKernelPolicy
{
    private final String version;
    private final Map<PositionPriority, Integer> priorityRanks;
    private final int fallbackCandidateBudget;
    private final int searchRadius;
    private final int waitingDebtCap;

    PositionKernelPolicy(String version,
                         Map<PositionPriority, Integer> priorityRanks,
                         int fallbackCandidateBudget, int searchRadius,
                         int waitingDebtCap)
    {
        if(version == null || version.trim().isEmpty() ||
           fallbackCandidateBudget <= 0 || searchRadius <= 0 ||
           waitingDebtCap <= 0) {
            throw new IllegalArgumentException("Invalid position kernel policy");
        }
        Objects.requireNonNull(priorityRanks, "priority ranks are required");
        EnumMap<PositionPriority, Integer> ranks =
            new EnumMap<>(PositionPriority.class);
        for(PositionPriority priority : PositionPriority.values()) {
            Integer rank = priorityRanks.get(priority);
            if(rank == null || rank.intValue() <= 0) {
                throw new IllegalArgumentException(
                    "Missing positive priority rank for " + priority);
            }
            ranks.put(priority, rank);
        }
        this.version = version;
        this.priorityRanks = Collections.unmodifiableMap(ranks);
        this.fallbackCandidateBudget = fallbackCandidateBudget;
        this.searchRadius = searchRadius;
        this.waitingDebtCap = waitingDebtCap;
    }

    public String getVersion()
    {
        return version;
    }

    public int rank(PositionPriority priority)
    {
        Integer rank = priorityRanks.get(priority);
        if(rank == null) {
            throw new IllegalArgumentException("Unknown position priority " +
                priority);
        }
        return rank.intValue();
    }

    public Map<PositionPriority, Integer> getPriorityRanks()
    {
        return priorityRanks;
    }

    public int getFallbackCandidateBudget()
    {
        return fallbackCandidateBudget;
    }

    public int getSearchRadius()
    {
        return searchRadius;
    }

    public int getWaitingDebtCap()
    {
        return waitingDebtCap;
    }
}
