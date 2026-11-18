package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Independent producer schedule for observation sinks. */
public final class ObservationSchedule
{
    private final ObservationConfig config;

    public ObservationSchedule(ObservationConfig config)
    {
        this.config = config == null ? ObservationConfig.defaults() : config;
    }

    public boolean isVisibleBoundary(int step)
    {
        return isBoundary(step, config.getVisibleCadence());
    }

    public boolean isReportBoundary(int step)
    {
        return isBoundary(step, config.getReportCadence());
    }

    public boolean isChartBoundary(int step)
    {
        return isBoundary(step, config.getChartCadence());
    }

    public boolean isDiagnosticBoundary(int step)
    {
        return isBoundary(step, config.getDiagnosticCadence());
    }

    /** Returns scheduled checkpoints plus exactly one final checkpoint. */
    public List<Integer> checkpoints(int finalStep, int cadence)
    {
        if(finalStep < 0) {
            throw new IllegalArgumentException("final step must be non-negative");
        }
        if(cadence <= 0) {
            throw new IllegalArgumentException("cadence must be positive");
        }
        List<Integer> checkpoints = new ArrayList<>();
        checkpoints.add(0);
        for(int step = cadence; step <= finalStep; step += cadence) {
            checkpoints.add(step);
        }
        if(checkpoints.get(checkpoints.size() - 1) != finalStep) {
            checkpoints.add(finalStep);
        }
        return Collections.unmodifiableList(checkpoints);
    }

    /** Start of the current visible producer interval, without a /100 rule. */
    public int visibleIntervalStart(int step)
    {
        if(step <= 0) return 0;
        int cadence = config.getVisibleCadence();
        return ((step - 1) / cadence) * cadence + 1;
    }

    private static boolean isBoundary(int step, int cadence)
    {
        return step == 0 || (step > 0 && step % cadence == 0);
    }
}
