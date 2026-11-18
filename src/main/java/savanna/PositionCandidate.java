package savanna;

/** One unique candidate emitted lazily by a {@link PositionCandidateCursor}. */
public record PositionCandidate(Location location,
                                PositionCandidateSource source,
                                int index)
{
}
