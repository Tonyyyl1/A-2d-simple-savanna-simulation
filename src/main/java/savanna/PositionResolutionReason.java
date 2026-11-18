package savanna;

/** Auditable reason for a committed destination or rejected spawn token. */
public enum PositionResolutionReason
{
    REQUESTED,
    EXPLICIT_FALLBACK,
    SEARCH_FALLBACK,
    OWNER_STAY_PROTECTION,
    HIGHER_PRIORITY_LOSS,
    EQUAL_PRIORITY_ROTATION_LOSS,
    FIXED_OBSTACLE,
    NO_PASSABLE_CANDIDATE,
    INVALID_REQUESTED,
    CLAIM_CANCELLED
}
