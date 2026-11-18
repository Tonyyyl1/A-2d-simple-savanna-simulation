package savanna;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Immutable and total resolution of one position-claim batch. */
public final class PositionPlan
{
    private final String policyVersion;
    private final long stepSalt;
    private final Map<PositionActorKey, PositionResolution> resolutions;
    private final Map<PositionActorKey, Integer> nextWaitingDebt;
    private final PositionResolutionMetrics metrics;

    PositionPlan(String policyVersion, long stepSalt,
                 Map<PositionActorKey, PositionResolution> resolutions,
                 Map<PositionActorKey, Integer> nextWaitingDebt,
                 PositionResolutionMetrics metrics)
    {
        if(policyVersion == null || resolutions == null ||
           nextWaitingDebt == null || metrics == null) {
            throw new IllegalArgumentException("Position plan facts are required");
        }
        this.policyVersion = policyVersion;
        this.stepSalt = stepSalt;
        this.resolutions = Collections.unmodifiableMap(
            new LinkedHashMap<>(new TreeMap<>(resolutions)));
        this.nextWaitingDebt = Collections.unmodifiableMap(
            new LinkedHashMap<>(new TreeMap<>(nextWaitingDebt)));
        this.metrics = metrics;
    }

    public String getPolicyVersion() { return policyVersion; }
    public long getStepSalt() { return stepSalt; }
    public Map<PositionActorKey, PositionResolution> getResolutions()
    {
        return resolutions;
    }
    public PositionResolution getResolution(PositionActorKey actor)
    {
        return resolutions.get(actor);
    }
    public Location destinationForAdult(long adultId)
    {
        PositionResolution resolution = resolutions.get(
            PositionActorKey.adult(adultId));
        return resolution == null ? null : resolution.getDestination();
    }
    public int nextWaitingDebtFor(PositionActorKey actor)
    {
        Integer value = nextWaitingDebt.get(actor);
        return value == null ? 0 : value.intValue();
    }
    public Map<PositionActorKey, Integer> getNextWaitingDebt()
    {
        return nextWaitingDebt;
    }
    public PositionResolutionMetrics getMetrics() { return metrics; }
}
