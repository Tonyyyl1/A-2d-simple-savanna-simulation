import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores immutable simulation snapshots for later timeline or replay views.
 */
public class SnapshotRecorder
{
    private final List<SimulationSnapshot> snapshots = new ArrayList<>();

    public SimulationSnapshot capture(int step, Field field,
                                      SimulationContext context)
    {
        SimulationSnapshot snapshot =
            new SimulationSnapshot(step, field, context);
        snapshots.add(snapshot);
        return snapshot;
    }

    public int size()
    {
        return snapshots.size();
    }

    public List<SimulationSnapshot> getSnapshots()
    {
        return Collections.unmodifiableList(snapshots);
    }
}
