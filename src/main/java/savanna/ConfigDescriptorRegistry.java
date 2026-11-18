package savanna;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Authoritative CFG0 descriptors for tunable observation and display values. */
public final class ConfigDescriptorRegistry
{
    public static final int SCHEMA_VERSION = 1;

    public static final ConfigDescriptor<Integer> VISIBLE_CADENCE =
        positive("observation.visibleCadence", 100, "steps");
    public static final ConfigDescriptor<Integer> REPORT_CADENCE =
        positive("observation.reportCadence", 100, "steps");
    public static final ConfigDescriptor<Integer> CHART_CADENCE =
        positive("observation.chartCadence", 100, "steps");
    public static final ConfigDescriptor<Integer> DIAGNOSTIC_CADENCE =
        positive("observation.diagnosticCadence", 100, "steps");
    public static final ConfigDescriptor<Integer> HEATMAP_SHORT_WINDOW =
        positive("observation.heatmapShortWindow", 100, "steps");
    public static final ConfigDescriptor<Integer> HEATMAP_LONG_WINDOW =
        positive("observation.heatmapLongWindow", 1000, "steps");
    public static final ConfigDescriptor<Integer> REPRESENTATIVE_EVENT_CAPACITY =
        positive("observation.representativeEventCapacity", 96, "events");
    public static final ConfigDescriptor<Integer> RECENT_EVENT_CAPACITY =
        positive("observation.recentEventCapacity", 600, "events");
    public static final ConfigDescriptor<Integer> DETAIL_EVENT_CAPACITY =
        positive("observation.detailEventCapacity", 5, "events");
    public static final ConfigDescriptor<Integer> BOUNDARY_DELAY =
        nonNegative("display.boundaryDelay", 24, "ms");
    public static final ConfigDescriptor<Integer> INSPECT_TICK_PERIOD =
        positive("display.inspectTickPeriod", 33, "ms");
    public static final ConfigDescriptor<Integer> INSPECT_HOLD_DURATION =
        nonNegative("display.inspectHoldDuration", 450, "ms");
    public static final ConfigDescriptor<Integer> INSPECT_AMBIENT_CYCLE =
        positive("display.inspectAmbientCycle", 2400, "ms");
    public static final ConfigDescriptor<Double> INSPECT_ZOOM_RATIO =
        new ConfigDescriptor<>("display.inspectZoomRatio", 2.5, "ratio",
            value -> value != null && !Double.isNaN(value) &&
                !Double.isInfinite(value) && value > 0.0);

    private static final Map<String, ConfigDescriptor<?>> BY_KEY;

    static {
        Map<String, ConfigDescriptor<?>> entries = new LinkedHashMap<>();
        for(ConfigDescriptor<?> descriptor : all()) {
            if(entries.put(descriptor.getKey(), descriptor) != null) {
                throw new IllegalStateException("duplicate descriptor key " +
                    descriptor.getKey());
            }
        }
        BY_KEY = Collections.unmodifiableMap(entries);
    }

    private ConfigDescriptorRegistry() {}

    public static List<ConfigDescriptor<?>> all()
    {
        return Arrays.asList(VISIBLE_CADENCE, REPORT_CADENCE, CHART_CADENCE,
            DIAGNOSTIC_CADENCE, HEATMAP_SHORT_WINDOW, HEATMAP_LONG_WINDOW,
            REPRESENTATIVE_EVENT_CAPACITY, RECENT_EVENT_CAPACITY,
            DETAIL_EVENT_CAPACITY, BOUNDARY_DELAY, INSPECT_TICK_PERIOD,
            INSPECT_HOLD_DURATION, INSPECT_AMBIENT_CYCLE, INSPECT_ZOOM_RATIO);
    }

    public static ConfigDescriptor<?> find(String key)
    {
        return BY_KEY.get(key);
    }

    private static ConfigDescriptor<Integer> positive(String key, int value,
                                                       String unit)
    {
        return new ConfigDescriptor<>(key, value, unit,
            candidate -> candidate != null && candidate > 0);
    }

    private static ConfigDescriptor<Integer> nonNegative(String key, int value,
                                                          String unit)
    {
        return new ConfigDescriptor<>(key, value, unit,
            candidate -> candidate != null && candidate >= 0);
    }
}
