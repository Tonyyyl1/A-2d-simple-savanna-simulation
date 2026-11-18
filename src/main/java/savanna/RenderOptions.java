package savanna;

/** Immutable ordinary-renderer output options. */
public final class RenderOptions
{
    private final int width;
    private final int height;
    private final boolean includeMetricPanel;
    private final boolean includeEnvironmentOverlay;
    private final boolean includeHeatmap;
    private final HeatmapSelection heatmapSelection;

    public RenderOptions(int width, int height, boolean includeMetricPanel)
    {
        this(width, height, includeMetricPanel, true, false, null);
    }

    public RenderOptions(int width, int height, boolean includeMetricPanel,
                         boolean includeEnvironmentOverlay)
    {
        this(width, height, includeMetricPanel, includeEnvironmentOverlay,
             false, null);
    }

    public RenderOptions(int width, int height, boolean includeMetricPanel,
                         boolean includeEnvironmentOverlay,
                         boolean includeHeatmap,
                         HeatmapSelection heatmapSelection)
    {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.includeMetricPanel = includeMetricPanel;
        this.includeEnvironmentOverlay = includeEnvironmentOverlay;
        this.includeHeatmap = includeHeatmap;
        this.heatmapSelection = heatmapSelection == null
            ? HeatmapSelection.defaultSelection() : heatmapSelection;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean isMetricPanelIncluded() { return includeMetricPanel; }
    public boolean isEnvironmentOverlayIncluded() { return includeEnvironmentOverlay; }
    public boolean isHeatmapIncluded() { return includeHeatmap; }
    public HeatmapSelection getHeatmapSelection() { return heatmapSelection; }
}
