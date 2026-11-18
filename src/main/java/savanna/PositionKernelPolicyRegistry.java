package savanna;

import java.util.EnumMap;
import java.util.Map;

/**
 * Single R1 registry for internal position-policy constants.
 *
 * <p>These descriptors are intentionally non-tunable during R1: they are
 * typed and versioned here so later integration cannot introduce hidden
 * priority, fallback, or fairness constants into behavior code.</p>
 */
public final class PositionKernelPolicyRegistry
{
    public static final int SCHEMA_VERSION = 1;
    public static final String POLICY_VERSION = "a1a-r1-position-policy-v1";

    public static final ConfigDescriptor<Integer> FALLBACK_CANDIDATE_BUDGET =
        positive("position.kernel.fallbackCandidateBudget", 12, "cells");
    public static final ConfigDescriptor<Integer> SEARCH_RADIUS =
        positive("position.kernel.searchRadius", 8, "cells");
    public static final ConfigDescriptor<Integer> WAITING_DEBT_CAP =
        positive("position.kernel.waitingDebtCap", 16, "steps");

    private static final PositionKernelPolicy CURRENT = createCurrent();

    private PositionKernelPolicyRegistry()
    {
    }

    public static PositionKernelPolicy current()
    {
        return CURRENT;
    }

    private static PositionKernelPolicy createCurrent()
    {
        Map<PositionPriority, Integer> ranks =
            new EnumMap<>(PositionPriority.class);
        ranks.put(PositionPriority.CRITICAL_WATER, 600);
        ranks.put(PositionPriority.WATER_DEPARTURE, 500);
        ranks.put(PositionPriority.PREDATOR_SPACING, 400);
        ranks.put(PositionPriority.SURVIVAL_MOVE, 300);
        ranks.put(PositionPriority.ORDINARY_MOVE, 200);
        ranks.put(PositionPriority.SPAWN, 100);
        return new PositionKernelPolicy(POLICY_VERSION, ranks,
            FALLBACK_CANDIDATE_BUDGET.getDefaultValue(),
            SEARCH_RADIUS.getDefaultValue(),
            WAITING_DEBT_CAP.getDefaultValue());
    }

    private static ConfigDescriptor<Integer> positive(String key, int value,
                                                       String unit)
    {
        return new ConfigDescriptor<>(key, value, unit,
            candidate -> candidate != null && candidate.intValue() > 0);
    }
}
