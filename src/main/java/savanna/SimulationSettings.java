package savanna;

/**
 * Shared runtime settings for visual updates and reporting.
 */
public class SimulationSettings
{
    /** Legacy source-compatible aliases; CFG0 defaults live in the registry. */
    public static final int VISUAL_REFRESH_INTERVAL =
        ConfigDescriptorRegistry.VISIBLE_CADENCE.getDefaultValue();
    public static final int RECORD_INTERVAL =
        ConfigDescriptorRegistry.REPORT_CADENCE.getDefaultValue();
    /** Deprecated: production chart updates follow the resolved report hook. */
    @Deprecated
    public static final int CHART_UPDATE_INTERVAL =
        ConfigDescriptorRegistry.CHART_CADENCE.getDefaultValue();

    private SimulationSettings()
    {
    }
}
