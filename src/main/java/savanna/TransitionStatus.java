package savanna;

/** Explicit result of reserving a position in the next field state. */
public enum TransitionStatus
{
    MOVED,
    STAYED,
    REROUTED,
    SPAWNED,
    BLOCKED,
    CONFLICT_LOST
}
