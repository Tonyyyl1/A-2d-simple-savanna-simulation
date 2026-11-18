package savanna;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
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
        List<Location> result = new ArrayList<>(
            topology.getAdjacentPassable(origin));
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
        Queue<Location> pending = new ArrayDeque<>();
        Map<Location, Integer> distances = new HashMap<>();
        List<Location> result = new ArrayList<>();
        pending.add(copy(origin));
        distances.put(copy(origin), Integer.valueOf(0));
        while(!pending.isEmpty()) {
            Location current = pending.remove();
            int distance = distances.get(current).intValue();
            if(distance >= range) {
                continue;
            }
            for(Location neighbor : topology.getAdjacentPassable(current)) {
                if(distances.containsKey(neighbor)) {
                    continue;
                }
                int nextDistance = distance + 1;
                distances.put(neighbor, Integer.valueOf(nextDistance));
                pending.add(neighbor);
                if(nextDistance > 0 && nextDistance <= range) {
                    result.add(neighbor);
                }
            }
        }
        if(random != null) {
            Collections.shuffle(result, random);
        }
        return result;
    }

    public boolean isSnapshotOccupied(Location location)
    {
        return snapshot.ownerAt(location) != null;
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
        return new Location(location.row(), location.col());
    }
}
