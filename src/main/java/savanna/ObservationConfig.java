package savanna;

/** Immutable, validated observation cadence and bounded-retention settings. */
public final class ObservationConfig
{
    private final int visibleCadence;
    private final int reportCadence;
    private final int chartCadence;
    private final int diagnosticCadence;
    private final int heatmapShortWindow;
    private final int heatmapLongWindow;
    private final int representativeEventCapacity;
    private final int recentEventCapacity;
    private final int detailEventCapacity;

    public ObservationConfig(int visibleCadence, int reportCadence,
                             int chartCadence, int diagnosticCadence,
                             int heatmapShortWindow, int heatmapLongWindow,
                             int representativeEventCapacity,
                             int recentEventCapacity, int detailEventCapacity)
    {
        ConfigDescriptorRegistry.VISIBLE_CADENCE.requireValid(visibleCadence);
        ConfigDescriptorRegistry.REPORT_CADENCE.requireValid(reportCadence);
        ConfigDescriptorRegistry.CHART_CADENCE.requireValid(chartCadence);
        ConfigDescriptorRegistry.DIAGNOSTIC_CADENCE.requireValid(diagnosticCadence);
        ConfigDescriptorRegistry.HEATMAP_SHORT_WINDOW.requireValid(heatmapShortWindow);
        ConfigDescriptorRegistry.HEATMAP_LONG_WINDOW.requireValid(heatmapLongWindow);
        ConfigDescriptorRegistry.REPRESENTATIVE_EVENT_CAPACITY.requireValid(
            representativeEventCapacity);
        ConfigDescriptorRegistry.RECENT_EVENT_CAPACITY.requireValid(recentEventCapacity);
        ConfigDescriptorRegistry.DETAIL_EVENT_CAPACITY.requireValid(detailEventCapacity);
        if(heatmapShortWindow >= heatmapLongWindow) {
            throw new IllegalArgumentException("observation.heatmapWindows must be ordered " +
                "short<long: " + heatmapShortWindow + "," + heatmapLongWindow);
        }
        this.visibleCadence = visibleCadence;
        this.reportCadence = reportCadence;
        this.chartCadence = chartCadence;
        this.diagnosticCadence = diagnosticCadence;
        this.heatmapShortWindow = heatmapShortWindow;
        this.heatmapLongWindow = heatmapLongWindow;
        this.representativeEventCapacity = representativeEventCapacity;
        this.recentEventCapacity = recentEventCapacity;
        this.detailEventCapacity = detailEventCapacity;
    }

    public static ObservationConfig defaults()
    {
        return new ObservationConfig(
            ConfigDescriptorRegistry.VISIBLE_CADENCE.getDefaultValue(),
            ConfigDescriptorRegistry.REPORT_CADENCE.getDefaultValue(),
            ConfigDescriptorRegistry.CHART_CADENCE.getDefaultValue(),
            ConfigDescriptorRegistry.DIAGNOSTIC_CADENCE.getDefaultValue(),
            ConfigDescriptorRegistry.HEATMAP_SHORT_WINDOW.getDefaultValue(),
            ConfigDescriptorRegistry.HEATMAP_LONG_WINDOW.getDefaultValue(),
            ConfigDescriptorRegistry.REPRESENTATIVE_EVENT_CAPACITY.getDefaultValue(),
            ConfigDescriptorRegistry.RECENT_EVENT_CAPACITY.getDefaultValue(),
            ConfigDescriptorRegistry.DETAIL_EVENT_CAPACITY.getDefaultValue());
    }

    public int getVisibleCadence() { return visibleCadence; }
    public int getReportCadence() { return reportCadence; }
    public int getChartCadence() { return chartCadence; }
    public int getDiagnosticCadence() { return diagnosticCadence; }
    public int getHeatmapShortWindow() { return heatmapShortWindow; }
    public int getHeatmapLongWindow() { return heatmapLongWindow; }
    public int getRepresentativeEventCapacity() { return representativeEventCapacity; }
    public int getRecentEventCapacity() { return recentEventCapacity; }
    public int getDetailEventCapacity() { return detailEventCapacity; }
}
