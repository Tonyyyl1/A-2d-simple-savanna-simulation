package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Read-only position facts available while an R2 step is being proposed.
 *
 * <p>The view deliberately has no {@link Field} reference. It exposes only
 * step-start identity, immutable terrain topology, and the predeclared
 * critical-water reservations. A candidate being visible here is not a
 * reservation: all adult and spawn contention is decided exactly once by the
 * position resolver after proposal collection.</p>
 */
public final class PositionAvailabilityView
{
    private final StepStartPositionSnapshot snapshot;
    private final PositionTopology topology;
    private final Map<Location, Long> criticalWaterReservations;
    private final int step;
    private final PositionCandidateObservationListener candidateListener;

    public PositionAvailabilityView(StepStartPositionSnapshot snapshot,
                                    PositionTopology topology,
                                    Map<Location, Long> reservations)
    {
        this(snapshot, topology, reservations, 0, null);
    }

    PositionAvailabilityView(StepStartPositionSnapshot snapshot,
                             PositionTopology topology,
                             Map<Location, Long> reservations, int step,
                             PositionCandidateObservationListener listener)
    {
        this.snapshot = Objects.requireNonNull(snapshot,
            "step-start snapshot is required");
        this.topology = Objects.requireNonNull(topology,
            "position topology is required");
        criticalWaterReservations = immutableReservations(reservations);
        this.step = step;
        candidateListener = listener;
    }

    public StepStartPositionSnapshot getSnapshot()
    {
        return snapshot;
    }

    public PositionTopology getTopology()
    {
        return topology;
    }

    /**
     * Returns all passable adjacent candidates in the same shuffle shape as
     * the legacy navigator, then removes cells pre-reserved for another
     * critical-water claimant. Snapshot occupants remain visible because they
     * may vacate during the one later resolution pass.
     */
    public List<Location> adjacentCandidates(long actorId, Location origin,
                                             Random random)
    {
        return adjacentCandidates(
            PositionCandidateObservationPhase.PROPOSAL_ADJACENT,
            actorId, origin, random);
    }

    /**
     * Same candidate query with an observation-only phase label.
     */
    public List<Location> adjacentCandidates(
        PositionCandidateObservationPhase phase, long actorId, Location origin,
        Random random)
    {
        List<Location> result = topology.newAdjacentPassableList(origin);
        int passableCount = result.size();
        int snapshotOccupiedCount = 0;
        int reservedForOtherCount = 0;
        for(Location candidate : result) {
            if(isSnapshotOccupied(candidate)) {
                snapshotOccupiedCount++;
            }
            if(reservedForOther(candidate, actorId)) {
                reservedForOtherCount++;
            }
        }
        int shuffleInputSize = result.size();
        if(random != null) {
            Collections.shuffle(result, random);
        }
        result.removeIf(location -> reservedForOther(location, actorId));
        if(candidateListener != null) {
            candidateListener.onPositionCandidates(
                new PositionCandidateObservation(step, phase, actorId, origin,
                    passableCount, snapshotOccupiedCount,
                    reservedForOtherCount, random != null, shuffleInputSize,
                    result));
        }
        return result;
    }

    /**
     * Provides a passable, connected, bounded movement/grazing view without
     * consulting a projected field. Unlike adjacent candidates this does not
     * apply critical-water reservations; legacy urgent grazing did not do so
     * either.
     */
    public List<Location> reachableCandidates(Location origin, int range,
                                              Random random)
    {
        if(origin == null || range <= 0 || !topology.isPassable(origin)) {
            return Collections.emptyList();
        }
        // Breadth-first over the passable cells within `range` steps. Every cell it can
        // reach lies in the origin's (2 * range + 1)-square, clamped to the field, so the
        // distances live in a window of that size: 0 = not reached yet, else distance + 1.
        // The result is in discovery order (FIFO queue, neighbours in topology order).
        int firstRow = Math.max(0, origin.row() - range);
        int firstCol = Math.max(0, origin.col() - range);
        int rows = (int)Math.min(topology.getDepth() - 1L,
            origin.row() + (long)range) - firstRow + 1;
        int cols = (int)Math.min(topology.getWidth() - 1L,
            origin.col() + (long)range) - firstCol + 1;
        int[] reached = new int[rows * cols];
        Location[] pending = new Location[rows * cols];
        int head = 0;
        int tail = 0;
        List<Location> result = new ArrayList<>();
        // asked once: the answer cannot change inside one step, and the loop below is hot
        final boolean audit = RuntimeAudit.enabled();
        pending[tail++] = copy(origin);
        reached[(origin.row() - firstRow) * cols + (origin.col() - firstCol)] = 1;
        while(head < tail) {
            Location current = pending[head++];
            int distance = reached[(current.row() - firstRow) * cols +
                                   (current.col() - firstCol)] - 1;
            if(distance >= range) {
                continue;
            }
            for(Location neighbor : topology.getAdjacentPassable(current)) {
                if(audit &&
                   (neighbor.row() < firstRow || neighbor.row() >= firstRow + rows ||
                    neighbor.col() < firstCol || neighbor.col() >= firstCol + cols)) {
                    throw new IllegalStateException(
                        "reachable-candidate neighbour outside its distance window: " +
                        neighbor + " from " + origin + " range " + range);
                }
                int slot = (neighbor.row() - firstRow) * cols +
                           (neighbor.col() - firstCol);
                if(reached[slot] != 0) {
                    continue;
                }
                reached[slot] = distance + 2;
                pending[tail++] = neighbor;
                // distance < range, so the neighbour is 1..range steps away.
                result.add(neighbor);
            }
        }
        if(random != null) {
            Collections.shuffle(result, random);
        }
        return result;
    }

    public boolean isSnapshotOccupied(Location location)
    {
        return snapshot.isOccupied(location);
    }

    public boolean reservedForOther(Location location, long actorId)
    {
        Long owner = criticalWaterReservations.get(location);
        return owner != null && owner.longValue() != actorId;
    }

    private static Map<Location, Long> immutableReservations(
        Map<Location, Long> reservations)
    {
        if(reservations == null || reservations.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Location, Long> copied = new HashMap<>();
        for(Map.Entry<Location, Long> entry : reservations.entrySet()) {
            Location location = entry.getKey();
            Long owner = entry.getValue();
            if(location != null && owner != null && owner.longValue() > 0L) {
                copied.put(copy(location), owner);
            }
        }
        return Collections.unmodifiableMap(copied);
    }

    private static Location copy(Location location)
    {
        return java.util.Objects.requireNonNull(location);
    }
}
