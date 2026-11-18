package savanna;

/**
 * Versioned semantic buckets for the R1 position-priority matrix.
 * Numerical ranking lives only in {@link PositionKernelPolicy}; this enum
 * remains a meaning-bearing tuple component rather than a scattered constant.
 */
public enum PositionPriority
{
    CRITICAL_WATER,
    WATER_DEPARTURE,
    PREDATOR_SPACING,
    SURVIVAL_MOVE,
    ORDINARY_MOVE,
    SPAWN
}
