package savanna;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable and total resolution of one position-claim batch. */
public final class PositionPlan
{
    private final String policyVersion;
    private final long stepSalt;
    /** Lookup tables, never exposed: every plan reads them only with get. */
    private final Map<PositionActorKey, PositionResolution> resolutionsByActor;
    private final Map<PositionActorKey, Integer> nextWaitingDebtByActor;
    private final PositionResolutionMetrics metrics;
    /** The key-ordered views of the same maps, built the first time a caller asks. */
    private volatile Map<PositionActorKey, PositionResolution> sortedResolutions;
    private volatile Map<PositionActorKey, Integer> sortedNextWaitingDebt;

    PositionPlan(String policyVersion, long stepSalt,
                 Map<PositionActorKey, PositionResolution> resolutions,
                 Map<PositionActorKey, Integer> nextWaitingDebt,
                 PositionResolutionMetrics metrics)
    {
        this(policyVersion, stepSalt, resolutions, nextWaitingDebt, metrics, true);
    }

    /**
     * Builds a plan that keeps the two maps it is given instead of copying them; the
     * resolver creates them fresh and never touches them again.
     */
    static PositionPlan adopting(String policyVersion, long stepSalt,
                                 Map<PositionActorKey, PositionResolution> resolutions,
                                 Map<PositionActorKey, Integer> nextWaitingDebt,
                                 PositionResolutionMetrics metrics)
    {
        return new PositionPlan(policyVersion, stepSalt, resolutions,
            nextWaitingDebt, metrics, false);
    }

    private PositionPlan(String policyVersion, long stepSalt,
                         Map<PositionActorKey, PositionResolution> resolutions,
                         Map<PositionActorKey, Integer> nextWaitingDebt,
                         PositionResolutionMetrics metrics, boolean copy)
    {
        if(policyVersion == null || resolutions == null ||
           nextWaitingDebt == null || metrics == null) {
            throw new IllegalArgumentException("Position plan facts are required");
        }
        this.policyVersion = policyVersion;
        this.stepSalt = stepSalt;
        resolutionsByActor = copy ? new LinkedHashMap<>(resolutions) : resolutions;
        nextWaitingDebtByActor = copy ? new LinkedHashMap<>(nextWaitingDebt)
                                      : nextWaitingDebt;
        this.metrics = metrics;
    }

    /**
     * The key-ordered views are built on demand, so a step that only looks results up
     * never sorts them. Iteration order is the natural key order (adults before spawns, then ID, then ordinal).
     * One array sort replaces building a TreeMap and copying it again; keys are unique, so the
     * resulting order is identical.
     */
    @SuppressWarnings("unchecked")
    private static <V> Map<PositionActorKey, V> sortedUnmodifiableCopy(
        Map<PositionActorKey, V> source)
    {
        Map.Entry<PositionActorKey, V>[] entries =
            source.entrySet().toArray(new Map.Entry[0]);
        Arrays.sort(entries, Map.Entry.comparingByKey());
        Map<PositionActorKey, V> sorted =
            new LinkedHashMap<>((int)(entries.length / 0.75f) + 1);
        for(Map.Entry<PositionActorKey, V> entry : entries) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(sorted);
    }

    public String getPolicyVersion() { return policyVersion; }
    public long getStepSalt() { return stepSalt; }
    public Map<PositionActorKey, PositionResolution> getResolutions()
    {
        Map<PositionActorKey, PositionResolution> view = sortedResolutions;
        if(view == null) {
            view = sortedUnmodifiableCopy(resolutionsByActor);
            sortedResolutions = view;
        }
        return view;
    }
    public PositionResolution getResolution(PositionActorKey actor)
    {
        return resolutionsByActor.get(actor);
    }
    public Location destinationForAdult(long adultId)
    {
        PositionResolution resolution = resolutionsByActor.get(
            PositionActorKey.adult(adultId));
        return resolution == null ? null : resolution.getDestination();
    }
    public int nextWaitingDebtFor(PositionActorKey actor)
    {
        Integer value = nextWaitingDebtByActor.get(actor);
        return value == null ? 0 : value.intValue();
    }
    public Map<PositionActorKey, Integer> getNextWaitingDebt()
    {
        Map<PositionActorKey, Integer> view = sortedNextWaitingDebt;
        if(view == null) {
            view = sortedUnmodifiableCopy(nextWaitingDebtByActor);
            sortedNextWaitingDebt = view;
        }
        return view;
    }
    public PositionResolutionMetrics getMetrics() { return metrics; }
}
