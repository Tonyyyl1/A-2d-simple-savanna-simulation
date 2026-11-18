package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable diagnostic fact emitted after an R2 adjacent-candidate query.
 *
 * <p>This fact is deliberately observational: it is created only when a
 * listener is registered, after the candidate result has been decided, and
 * it is never consulted by movement, ecology, or reservation logic.</p>
 */
public final class PositionCandidateObservation
{
    private final int step;
    private final PositionCandidateObservationPhase phase;
    private final long actorId;
    private final Location origin;
    private final int passableCount;
    private final int snapshotOccupiedCount;
    private final int reservedForOtherCount;
    private final boolean shuffleRequested;
    private final int shuffleInputSize;
    private final List<Location> candidates;

    PositionCandidateObservation(int step,
                                 PositionCandidateObservationPhase phase,
                                 long actorId, Location origin,
                                 int passableCount, int snapshotOccupiedCount,
                                 int reservedForOtherCount,
                                 boolean shuffleRequested, int shuffleInputSize,
                                 List<Location> candidates)
    {
        this.step = step;
        this.phase = phase == null
            ? PositionCandidateObservationPhase.PROPOSAL_ADJACENT : phase;
        this.actorId = actorId;
        this.origin = copy(origin);
        this.passableCount = passableCount;
        this.snapshotOccupiedCount = snapshotOccupiedCount;
        this.reservedForOtherCount = reservedForOtherCount;
        this.shuffleRequested = shuffleRequested;
        this.shuffleInputSize = shuffleInputSize;
        List<Location> copied = new ArrayList<>();
        if(candidates != null) {
            for(Location candidate : candidates) {
                copied.add(copy(candidate));
            }
        }
        this.candidates = Collections.unmodifiableList(copied);
    }

    public int getStep() { return step; }
    public PositionCandidateObservationPhase getPhase() { return phase; }
    public long getActorId() { return actorId; }
    public Location getOrigin() { return copy(origin); }
    public int getPassableCount() { return passableCount; }
    public int getSnapshotOccupiedCount() { return snapshotOccupiedCount; }
    public int getReservedForOtherCount() { return reservedForOtherCount; }
    public boolean isShuffleRequested() { return shuffleRequested; }
    public int getShuffleInputSize() { return shuffleInputSize; }

    public List<Location> getCandidates()
    {
        // Location is an immutable record and the list was copied/frozen in
        // the constructor, so returning it preserves the diagnostic boundary
        // without allocating a second list for every observed query.
        return candidates;
    }

    private static Location copy(Location location)
    {
        return location == null ? null
            : new Location(location.row(), location.col());
    }
}
