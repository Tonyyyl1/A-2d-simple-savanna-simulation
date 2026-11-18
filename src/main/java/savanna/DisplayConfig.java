package savanna;

/** Immutable display timing defaults.  It has no ecological read path. */
public final class DisplayConfig
{
    private final int boundaryDelayMillis;
    private final int inspectTickMillis;
    private final int inspectHoldMillis;
    private final int inspectAmbientCycleMillis;
    private final double inspectZoomRatio;

    public DisplayConfig(int boundaryDelayMillis, int inspectTickMillis,
                         int inspectHoldMillis, int inspectAmbientCycleMillis,
                         double inspectZoomRatio)
    {
        ConfigDescriptorRegistry.BOUNDARY_DELAY.requireValid(boundaryDelayMillis);
        ConfigDescriptorRegistry.INSPECT_TICK_PERIOD.requireValid(inspectTickMillis);
        ConfigDescriptorRegistry.INSPECT_HOLD_DURATION.requireValid(inspectHoldMillis);
        ConfigDescriptorRegistry.INSPECT_AMBIENT_CYCLE.requireValid(
            inspectAmbientCycleMillis);
        ConfigDescriptorRegistry.INSPECT_ZOOM_RATIO.requireValid(inspectZoomRatio);
        this.boundaryDelayMillis = boundaryDelayMillis;
        this.inspectTickMillis = inspectTickMillis;
        this.inspectHoldMillis = inspectHoldMillis;
        this.inspectAmbientCycleMillis = inspectAmbientCycleMillis;
        this.inspectZoomRatio = inspectZoomRatio;
    }

    public static DisplayConfig defaults()
    {
        return new DisplayConfig(
            ConfigDescriptorRegistry.BOUNDARY_DELAY.getDefaultValue(),
            ConfigDescriptorRegistry.INSPECT_TICK_PERIOD.getDefaultValue(),
            ConfigDescriptorRegistry.INSPECT_HOLD_DURATION.getDefaultValue(),
            ConfigDescriptorRegistry.INSPECT_AMBIENT_CYCLE.getDefaultValue(),
            ConfigDescriptorRegistry.INSPECT_ZOOM_RATIO.getDefaultValue());
    }

    public int getBoundaryDelayMillis() { return boundaryDelayMillis; }
    public int getInspectTickMillis() { return inspectTickMillis; }
    public int getInspectHoldMillis() { return inspectHoldMillis; }
    public int getInspectAmbientCycleMillis() { return inspectAmbientCycleMillis; }
    public double getInspectZoomRatio() { return inspectZoomRatio; }
}
