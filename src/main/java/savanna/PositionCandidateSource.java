package savanna;

/** Ordered source of a candidate emitted by a position cursor. */
public enum PositionCandidateSource
{
    REQUESTED,
    EXPLICIT_FALLBACK,
    SEARCH_FALLBACK,
    ORIGIN_STAY
}
