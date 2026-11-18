package savanna;

/**
 * Frozen W3a departure values. The M2-2 finite water service values (drink
 * per step, completion level, queue radius, wait and retarget exclusion) are
 * tunable and live in {@link EcologyParameters}.
 */
public final class WaterBehaviorPolicy
{
    public static final int CLEARANCE_DISTANCE = 2;
    public static final int COOLDOWN_STEPS = 6;

    private WaterBehaviorPolicy()
    {
    }
}
