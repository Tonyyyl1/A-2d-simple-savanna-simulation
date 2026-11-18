package savanna;

/** Physical result of the movement reservation made during one step. */
public enum MovementDisposition
{
    NOT_ATTEMPTED,
    MOVED,
    STAYED,
    REROUTED,
    BLOCKED,
    CONFLICT_LOST;

    public static MovementDisposition from(TransitionStatus status)
    {
        if(status == null) {
            return NOT_ATTEMPTED;
        }
        switch(status) {
            case MOVED:         return MOVED;
            case STAYED:        return STAYED;
            case REROUTED:      return REROUTED;
            case BLOCKED:       return BLOCKED;
            case CONFLICT_LOST: return CONFLICT_LOST;
            default:            return NOT_ATTEMPTED;
        }
    }

    public boolean moved()
    {
        return this == MOVED || this == REROUTED;
    }
}
