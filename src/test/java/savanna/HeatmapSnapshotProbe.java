package savanna;

import java.awt.image.BufferedImage;

/** Deterministic O0b2 proof for immutable same-step heatmap render facts. */
public final class HeatmapSnapshotProbe
{
    private HeatmapSnapshotProbe()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("O0b2 heatmap snapshot probe");
        TestSupport.startGroup("O0b2 heatmap snapshot");
        run();
        TestSupport.finishGroup();
        TestSupport.finishSuite();
        if(TestSupport.hasFailures()) {
            System.exit(1);
        }
    }

    public static void run()
    {
        EventAccumulator accumulator = new EventAccumulator();
        Field field = new Field(12, 12);
        SimulationContext context = new SimulationContext(12, 12);
        Location shared = firstPassable(context.getTerrainMap(), null);
        Location other = firstPassable(context.getTerrainMap(), shared);
        for(int index = 0; index < 3; index++) {
            accumulator.onEvent(event(SimulationEvent.EventType.GRAZE, shared));
        }
        for(int index = 0; index < 2; index++) {
            accumulator.onEvent(event(SimulationEvent.EventType.HUNT, shared));
        }
        accumulator.onEvent(event(SimulationEvent.EventType.DRINK, other));
        HeatmapSnapshot snapshot = accumulator.freeze(100);
        TestSupport.assertEquals("O0b2 snapshot step", 100,
                                 snapshot.getFrameStep());
        TestSupport.assertEquals("O0b2 whole-run start step", 1,
                                 snapshot.getStatisticsStartStep());
        TestSupport.assertEquals("O0b2 whole-run end step", 100,
                                 snapshot.getStatisticsEndStep());
        TestSupport.assertEquals("O0b2 snapshot keeps all event totals", 6,
                                 snapshot.getTotalEventCount());
        TestSupport.assertEquals("O0b2 all selector count", 5,
                                 snapshot.getCountAt(shared, null));
        TestSupport.assertEquals("O0b2 typed selector count", 3,
            snapshot.getCountAt(shared, SimulationEvent.EventType.GRAZE));
        TestSupport.assertEquals("O0b2 all selector maximum", 5,
                                 snapshot.getMaxCount(null));
        TestSupport.assertEquals("O0b2 typed selector maximum", 3,
            snapshot.getMaxCount(SimulationEvent.EventType.GRAZE));
        TestSupport.assertEquals("O0b2 all nonzero cells", 2,
                                 snapshot.getNonZeroCellCount());
        TestSupport.assertTrue("O0b2 snapshot cell map is immutable",
                               rejectsCellMapMutation(snapshot));

        accumulator.onEvent(event(SimulationEvent.EventType.GRAZE, shared));
        TestSupport.assertEquals("O0b2 later live event cannot alter snapshot", 6,
                                 snapshot.getTotalEventCount());
        TestSupport.assertEquals("O0b2 later live event cannot alter pixels facts", 5,
                                 snapshot.getCountAt(shared, null));

        context.startStep(100, field);
        RenderFrame frame = VisibleRenderFrameFactory.capture(100, field, context,
            accumulator, 240, 180);
        TestSupport.assertEquals("O0b2 visible frame owns same-step snapshot", 100,
                                 frame.heatmapSnapshot.getFrameStep());
        TestSupport.assertEquals("O0b2 frame snapshot includes freeze-time total", 7,
                                 frame.heatmapSnapshot.getTotalEventCount());
        accumulator.onEvent(event(SimulationEvent.EventType.GRAZE, shared));
        TestSupport.assertEquals("O0b2 published frame remains frozen", 7,
                                 frame.heatmapSnapshot.getTotalEventCount());
        TestSupport.assertTrue("O0b2 mismatched snapshot is rejected",
                               rejectsMismatchedFrameSnapshot(frame));

        HeatmapSelection whole = new HeatmapSelection(HeatmapWindow.WHOLE_RUN,
                                                       null, null);
        BufferedImage first = RenderSnapshotTool.render(frame, 240, 180, true, whole);
        BufferedImage second = RenderSnapshotTool.render(frame, 240, 180, true, whole);
        BufferedImage withoutHeatmap = RenderSnapshotTool.render(frame, 240, 180,
            false, whole);
        TestSupport.assertEquals("O0b2 fixed PNG is stable for frozen frame",
                                 Long.valueOf(pixelHash(first)),
                                 Long.valueOf(pixelHash(second)));
        TestSupport.assertFalse("O0b2 fixed PNG heatmap uses frame snapshot",
            pixelHash(first) == pixelHash(withoutHeatmap));

        HeatmapSnapshot reset = new EventAccumulator().freeze(0);
        TestSupport.assertEquals("O0b2 reset snapshot step", 0,
                                 reset.getFrameStep());
        TestSupport.assertEquals("O0b2 reset snapshot starts at zero", 0,
                                 reset.getStatisticsStartStep());
        TestSupport.assertEquals("O0b2 reset snapshot is empty", 0,
                                 reset.getTotalEventCount());
        TestSupport.assertTrue("O0b2 GUI no longer retains live accumulator",
                               simulatorViewHasNoLiveAccumulator());
        verifySimulatorVisibleFrameBoundaries();
    }

    private static SimulationEvent event(SimulationEvent.EventType type,
                                         Location location)
    {
        return new SimulationEvent(type, 100, location.row(), location.col(),
            -1, -1, 1L, -1L, SpeciesRegistry.GAZELLE, "");
    }

    private static boolean rejectsCellMapMutation(HeatmapSnapshot snapshot)
    {
        try {
            snapshot.getCounts(null).put(new Location(0, 0), 1);
            return false;
        }
        catch(UnsupportedOperationException expected) {
            return true;
        }
    }

    private static boolean rejectsMismatchedFrameSnapshot(RenderFrame frame)
    {
        try {
            frame.withHeatmapSnapshot(HeatmapSnapshot.emptyAtStep(frame.step + 1));
            return false;
        }
        catch(IllegalArgumentException expected) {
            return true;
        }
    }

    private static long pixelHash(BufferedImage image)
    {
        long hash = 1469598103934665603L;
        for(int row = 0; row < image.getHeight(); row++) {
            for(int col = 0; col < image.getWidth(); col++) {
                hash ^= image.getRGB(col, row);
                hash *= 1099511628211L;
            }
        }
        return hash;
    }

    private static boolean simulatorViewHasNoLiveAccumulator()
    {
        try {
            for(java.lang.reflect.Field field : Class.forName("savanna.SimulatorView$FieldView")
                .getDeclaredFields()) {
                if(field.getType() == EventAccumulator.class) {
                    return false;
                }
            }
            return true;
        }
        catch(ClassNotFoundException e) {
            return false;
        }
    }

    private static void verifySimulatorVisibleFrameBoundaries()
    {
        Simulator simulator = new Simulator(SimulationConfig.baseline(), false);
        RenderFrame resetFrame = simulator.getRenderFramePublisher().latest();
        TestSupport.assertEquals("O0b2 simulator reset frame is synchronized", 0,
                                 resetFrame.heatmapSnapshot.getFrameStep());
        for(int step = 1; step <= 100; step++) {
            simulator.simulateOneStep();
        }
        RenderFrame boundary = simulator.getRenderFramePublisher().latest();
        TestSupport.assertEquals("O0b2 step 100 frame has matching snapshot", 100,
                                 boundary.heatmapSnapshot.getFrameStep());
        simulator.simulateOneStep();
        TestSupport.assertEquals("O0b2 ordinary step frame omits heatmap", null,
                                 simulator.getRenderFramePublisher().latest().heatmapSnapshot);
        RenderFrame finalFrame = simulator.captureVisibleRenderFrame();
        TestSupport.assertEquals("O0b2 explicit final frame is synchronized", 101,
                                 finalFrame.heatmapSnapshot.getFrameStep());
        simulator.reset();
        RenderFrame afterReset = simulator.getRenderFramePublisher().latest();
        TestSupport.assertEquals("O0b2 reset does not inherit prior heatmap", 0,
            afterReset.heatmapSnapshot.getTotalEventCount());
    }

    private static Location firstPassable(TerrainMap terrain, Location excluded)
    {
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location location = new Location(row, col);
                if(!location.equals(excluded) && terrain.isPassable(location)) {
                    return location;
                }
            }
        }
        throw new IllegalStateException("test terrain has no usable land cell");
    }
}
