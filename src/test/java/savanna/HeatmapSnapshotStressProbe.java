package savanna;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Headless O0b2 pressure fixture.  It measures only frozen-snapshot painting;
 * it intentionally does not execute ecological decisions or consume their RNG.
 */
public final class HeatmapSnapshotStressProbe
{
    private static final int DEPTH = 240;
    private static final int WIDTH = 360;
    private static final int STEPS = 1000;
    private static final int EVENTS_PER_STEP = 10000;
    private static final int RUNS = 3;
    private static final int TOGGLES_PER_SELECTION = 100;

    private HeatmapSnapshotStressProbe()
    {
    }

    public static void main(String[] args)
    {
        compactHeap();
        long heapBefore = usedHeap();
        double worstP95 = 0.0;
        int expectedTotal = STEPS * EVENTS_PER_STEP;
        for(int run = 1; run <= RUNS; run++) {
            HeatmapSnapshot snapshot = buildSnapshot();
            require(snapshot.getTotalEventCount() == expectedTotal,
                "snapshot lost events in run " + run);
            require(snapshot.getNonZeroCellCount() == (DEPTH * WIDTH) / 5,
                "snapshot nonzero-cell count drifted in run " + run);
            double p95 = paintP95(snapshot);
            worstP95 = Math.max(worstP95, p95);
            System.out.printf("O0B3_STRESS run=%d total=%d nonzero=%d p95=%.2f ms%n",
                run, snapshot.getTotalEventCount(), snapshot.getNonZeroCellCount(), p95);
        }
        compactHeap();
        long heapDelta = Math.max(0L, usedHeap() - heapBefore);
        require(worstP95 <= 50.0,
            "snapshot paint p95 exceeds 50 ms: " + worstP95);
        require(heapDelta <= 64L * 1024L * 1024L,
            "snapshot fixture heap delta exceeds 64 MiB: " + heapDelta);
        System.out.printf("O0B3_STRESS PASS runs=%d p95=%.2f ms heapDeltaMiB=%.2f " +
                          "selection=LAST_1000/All-species toggles=%d+%d%n",
            RUNS, worstP95, heapDelta / 1048576.0, TOGGLES_PER_SELECTION,
            TOGGLES_PER_SELECTION);
    }

    private static HeatmapSnapshot buildSnapshot()
    {
        EventAccumulator accumulator = new EventAccumulator();
        SimulationEvent[] events = templates();
        for(int step = 1; step <= STEPS; step++) {
            int offset = (step * 97) % events.length;
            for(int index = 0; index < EVENTS_PER_STEP; index++) {
                SimulationEvent template = events[(offset + index) % events.length];
                accumulator.onEvent(new SimulationEvent(template.type, step,
                    template.row, template.col, -1, -1, template.actorId,
                    -1L, template.actorSpecies, ""));
            }
        }
        return accumulator.freeze(STEPS);
    }

    private static SimulationEvent[] templates()
    {
        int size = (DEPTH * WIDTH) / 5;
        SimulationEvent[] events = new SimulationEvent[size];
        for(int index = 0; index < size; index++) {
            int cell = index * 5;
            int row = cell / WIDTH;
            int col = cell % WIDTH;
            SimulationEvent.EventType type = index % 5 == 0
                ? SimulationEvent.EventType.DRINK : SimulationEvent.EventType.GRAZE;
            events[index] = new SimulationEvent(type, STEPS, row, col, -1, -1,
                index, -1L, SpeciesRegistry.GAZELLE, "");
        }
        return events;
    }

    private static double paintP95(HeatmapSnapshot snapshot)
    {
        TerrainMap terrain = new TerrainMap(DEPTH, WIDTH, 20260629L);
        VisualGridGeometry geometry = new VisualGridGeometry(DEPTH, WIDTH,
                                                              1387, 829);
        BufferedImage target = new BufferedImage(1387, 829,
                                                 BufferedImage.TYPE_INT_ARGB);
        long[] samples = new long[TOGGLES_PER_SELECTION * 2];
        for(int index = 0; index < samples.length; index++) {
            SimulationEvent.EventType selection = index % 2 == 0 ? null
                : SimulationEvent.EventType.DRINK;
            Graphics2D graphics = target.createGraphics();
            long started = System.nanoTime();
            HeatmapRenderer.render(graphics, snapshot.select(new HeatmapSelection(
                HeatmapWindow.LAST_1000, selection, null)), terrain, geometry,
                new Rectangle(0, 0, WIDTH, DEPTH));
            samples[index] = System.nanoTime() - started;
            graphics.dispose();
        }
        Arrays.sort(samples);
        return samples[(int)Math.ceil(samples.length * 0.95) - 1] / 1_000_000.0;
    }

    private static long usedHeap()
    {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void compactHeap()
    {
        System.gc();
        System.runFinalization();
        System.gc();
    }

    private static void require(boolean condition, String message)
    {
        if(!condition) {
            throw new IllegalStateException(message);
        }
    }
}
