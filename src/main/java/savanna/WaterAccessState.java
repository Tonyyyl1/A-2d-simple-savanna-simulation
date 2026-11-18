package savanna;

/** Persistent post-drink water-access lifecycle. */
public enum WaterAccessState
{
    NORMAL,
    /** Occupying a shore slot for a multi-step drinking session (M2-2). */
    DRINKING,
    DEPARTING,
    COOLDOWN
}
