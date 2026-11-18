package savanna;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Observation-only, one-process timing sample for the A1a-R0 denominator. */
public final class PositionCostProbe
{
    private PositionCostProbe() {}

    public static void main(String[] args)
    {
        int steps = argument(args, 0, 1000);
        long ecologySeed = longArgument(args, 1,
            SimulationConfig.DEFAULT_RANDOM_SEED);
        long terrainSeed = longArgument(args, 2,
            SimulationConfig.DEFAULT_TERRAIN_SEED);
        if(steps <= 0) {
            throw new IllegalArgumentException("steps must be positive");
        }

        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(ecologySeed).terrainSeed(terrainSeed).build();
        Randomizer.reset();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        long gcCountBefore = gcCount();
        long gcMillisBefore = gcMillis();
        long peakUsedBytes = usedBytes();
        List<Long> stepNanos = new ArrayList<>();
        long started = System.nanoTime();
        for(int index = 1; index <= steps && simulator.getField().isViable();
            index++) {
            long stepStarted = System.nanoTime();
            simulator.simulateOneStep();
            stepNanos.add(System.nanoTime() - stepStarted);
            peakUsedBytes = Math.max(peakUsedBytes, usedBytes());
        }
        long elapsedNanos = System.nanoTime() - started;
        Collections.sort(stepNanos);
        System.out.println("POSITION_COST_BASELINE schema=R0" +
            " ecologySeed=" + ecologySeed + " terrainSeed=" + terrainSeed +
            " requestedSteps=" + steps + " completedSteps=" + simulator.getStep() +
            " elapsedNanos=" + elapsedNanos +
            " medianStepNanos=" + percentile(stepNanos, 0.50) +
            " p95StepNanos=" + percentile(stepNanos, 0.95) +
            " maxStepNanos=" + percentile(stepNanos, 1.00) +
            " peakUsedBytes=" + peakUsedBytes +
            " gcCountDelta=" + difference(gcCount(), gcCountBefore) +
            " gcMillisDelta=" + difference(gcMillis(), gcMillisBefore) +
            " snapshotNanos=LEGACY_UNAVAILABLE" +
            " proposalNanos=LEGACY_UNAVAILABLE" +
            " candidateNanos=LEGACY_UNAVAILABLE" +
            " resolveNanos=LEGACY_UNAVAILABLE" +
            " commitNanos=LEGACY_UNAVAILABLE" +
            " fingerprint=" + SimulationFingerprint.capture(simulator));
    }

    private static int argument(String[] args, int index, int fallback)
    {
        return args.length > index ? Integer.parseInt(args[index]) : fallback;
    }

    private static long longArgument(String[] args, int index, long fallback)
    {
        return args.length > index ? Long.parseLong(args[index]) : fallback;
    }

    private static long percentile(List<Long> values, double percentile)
    {
        if(values.isEmpty()) return 0L;
        int index = (int)Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }

    private static long usedBytes()
    {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static long gcCount()
    {
        long total = 0L;
        for(GarbageCollectorMXBean bean :
                ManagementFactory.getGarbageCollectorMXBeans()) {
            if(bean.getCollectionCount() >= 0L) total += bean.getCollectionCount();
        }
        return total;
    }

    private static long gcMillis()
    {
        long total = 0L;
        for(GarbageCollectorMXBean bean :
                ManagementFactory.getGarbageCollectorMXBeans()) {
            if(bean.getCollectionTime() >= 0L) total += bean.getCollectionTime();
        }
        return total;
    }

    private static long difference(long after, long before)
    {
        return after >= before ? after - before : -1L;
    }
}
