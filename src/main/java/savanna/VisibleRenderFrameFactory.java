package savanna;

/** Freezes same-step whole-run heatmap facts only for a visible render frame. */
public final class VisibleRenderFrameFactory
{
    private VisibleRenderFrameFactory()
    {
    }

    public static RenderFrame capture(int step, Field field,
                                      SimulationContext context,
                                      EventAccumulator accumulator,
                                      int panelWidth, int panelHeight)
    {
        HeatmapSnapshot snapshot = accumulator == null
            ? HeatmapSnapshot.emptyAtStep(step) : accumulator.freeze(step);
        return RenderFrame.capture(step, field, context, panelWidth, panelHeight)
            .withHeatmapSnapshot(snapshot);
    }
}
