package savanna;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Long-running event accumulator with bounded sparse rolling buckets.
 * Buckets retain packed primitive facts rather than SimulationEvent objects.
 */
public class EventAccumulator implements SimulationEventListener
{
    private final Map<SimulationEvent.EventType, Integer> countsByType;
    private final IntCounter countsByLocation;
    private final Map<SimulationEvent.EventType, IntCounter> countsByTypeLocation;
    private final IntCounter wholeRunPackedCounts;
    private final IntCounter last100PackedCounts;
    private final IntCounter last1000PackedCounts;
    private final IntCounter wholeRunTotals;
    private final IntCounter last100Totals;
    private final IntCounter last1000Totals;
    private final PackedBucket[] buckets;
    private final int shortWindow;
    private final int longWindow;
    private int totalEvents;
    private int last100Total;
    private int last1000Total;
    private int advancedStep;

    public EventAccumulator()
    {
        this(ObservationConfig.defaults());
    }

    /** Uses the resolved observation windows; UI enum names remain legacy labels. */
    public EventAccumulator(ObservationConfig config)
    {
        ObservationConfig resolved = config == null ? ObservationConfig.defaults()
                                                    : config;
        shortWindow = resolved.getHeatmapShortWindow();
        longWindow = resolved.getHeatmapLongWindow();
        countsByType = new EnumMap<>(SimulationEvent.EventType.class);
        countsByLocation = new IntCounter();
        countsByTypeLocation = new EnumMap<>(SimulationEvent.EventType.class);
        wholeRunPackedCounts = new IntCounter();
        last100PackedCounts = new IntCounter();
        last1000PackedCounts = new IntCounter();
        wholeRunTotals = new IntCounter();
        last100Totals = new IntCounter();
        last1000Totals = new IntCounter();
        buckets = new PackedBucket[longWindow];
        for(int index = 0; index < buckets.length; index++) {
            buckets[index] = new PackedBucket();
        }
    }

    public synchronized void onEvent(SimulationEvent event)
    {
        if(event == null) return;
        totalEvents++;
        countsByType.put(event.type, getCount(event.type) + 1);
        if(event.row >= 0 && event.col >= 0) {
            int cell = cellKey(event.row, event.col);
            countsByLocation.add(cell, 1);
            IntCounter typeCounts = countsByTypeLocation.get(event.type);
            if(typeCounts == null) {
                typeCounts = new IntCounter();
                countsByTypeLocation.put(event.type, typeCounts);
            }
            typeCounts.add(cell, 1);
        }
        recordWindowFact(event);
    }

    public synchronized void clear()
    {
        countsByType.clear();
        countsByLocation.clear();
        countsByTypeLocation.clear();
        wholeRunPackedCounts.clear();
        last100PackedCounts.clear();
        last1000PackedCounts.clear();
        wholeRunTotals.clear();
        last100Totals.clear();
        last1000Totals.clear();
        for(PackedBucket bucket : buckets) bucket.clear();
        totalEvents = 0;
        last100Total = 0;
        last1000Total = 0;
        advancedStep = 0;
    }

    public synchronized int getTotalEvents() { return totalEvents; }
    public synchronized int getCount(SimulationEvent.EventType type)
    {
        return countsByType.getOrDefault(type, 0);
    }
    public synchronized int getCountAt(Location location)
    {
        if(location == null || location.row() < 0 || location.col() < 0) return 0;
        return countsByLocation.get(cellKey(location.row(), location.col()));
    }
    public synchronized int getCountAt(Location location, SimulationEvent.EventType type)
    {
        if(type == null) return getCountAt(location);
        if(location == null || location.row() < 0 || location.col() < 0) return 0;
        IntCounter typeCounts = countsByTypeLocation.get(type);
        return typeCounts == null ? 0 : typeCounts.get(cellKey(location.row(), location.col()));
    }
    public synchronized int getMaxCount() { return countsByLocation.max(); }
    public synchronized int getMaxCount(SimulationEvent.EventType type)
    {
        if(type == null) return getMaxCount();
        IntCounter typeCounts = countsByTypeLocation.get(type);
        return typeCounts == null ? 0 : typeCounts.max();
    }
    public synchronized Map<SimulationEvent.EventType, Integer> getCountsByType()
    {
        return Collections.unmodifiableMap(new EnumMap<>(countsByType));
    }
    public synchronized Map<Location, Integer> getCountsByLocation()
    {
        Map<Location, Integer> copy = new HashMap<>();
        for(Map.Entry<Integer, Integer> entry : countsByLocation.toMap().entrySet()) {
            int key = entry.getKey().intValue();
            copy.put(new Location(key >>> 16, key & 0xffff), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    /** Freeze complete canonical facts for every O0b3 selection at frameStep. */
    public synchronized HeatmapSnapshot freeze(int frameStep)
    {
        advanceWindowsTo(Math.max(0, frameStep));
        Map<HeatmapWindow, Map<Integer, Integer>> packed =
            new EnumMap<>(HeatmapWindow.class);
        packed.put(HeatmapWindow.WHOLE_RUN, wholeRunPackedCounts.toMap());
        packed.put(HeatmapWindow.LAST_100, last100PackedCounts.toMap());
        packed.put(HeatmapWindow.LAST_1000, last1000PackedCounts.toMap());
        Map<HeatmapWindow, Map<Integer, Integer>> totals =
            new EnumMap<>(HeatmapWindow.class);
        totals.put(HeatmapWindow.WHOLE_RUN, wholeRunTotals.toMap());
        totals.put(HeatmapWindow.LAST_100, last100Totals.toMap());
        totals.put(HeatmapWindow.LAST_1000, last1000Totals.toMap());
        Map<HeatmapWindow, Integer> starts = new EnumMap<>(HeatmapWindow.class);
        int normalizedStep = Math.max(0, frameStep);
        starts.put(HeatmapWindow.WHOLE_RUN, normalizedStep <= 0 ? 0 : 1);
        starts.put(HeatmapWindow.LAST_100, windowStart(normalizedStep, shortWindow));
        starts.put(HeatmapWindow.LAST_1000, windowStart(normalizedStep, longWindow));
        return HeatmapSnapshot.fromAccumulator(normalizedStep, packed, totals, starts);
    }

    private void recordWindowFact(SimulationEvent event)
    {
        if(event.step <= 0) return;
        advanceWindowsTo(event.step);
        int packed = HeatmapSnapshot.pack(event.row, event.col, event.type,
                                          event.actorSpecies);
        int dimension = HeatmapSnapshot.dimensionOf(packed);
        wholeRunTotals.add(dimension, 1);
        wholeRunPackedCounts.add(packed, 1);
        PackedBucket bucket = buckets[event.step % longWindow];
        bucket.add(packed);
        last100Total++;
        last1000Total++;
        last100Totals.add(dimension, 1);
        last1000Totals.add(dimension, 1);
        last100PackedCounts.add(packed, 1);
        last1000PackedCounts.add(packed, 1);
    }

    private void advanceWindowsTo(int targetStep)
    {
        if(targetStep <= advancedStep) return;
        for(int step = advancedStep + 1; step <= targetStep; step++) {
            expire(step - shortWindow, last100PackedCounts, last100Totals, true);
            expire(step - longWindow, last1000PackedCounts, last1000Totals, false);
            if(step > longWindow) buckets[step % longWindow].clear();
        }
        advancedStep = targetStep;
    }

    private void expire(int expiredStep, IntCounter counts,
                        IntCounter totals, boolean shortWindow)
    {
        if(expiredStep <= 0) return;
        PackedBucket bucket = buckets[expiredStep % longWindow];
        for(int index = 0; index < bucket.size; index++) {
            int packed = bucket.values[index];
            counts.decrement(packed);
            totals.decrement(HeatmapSnapshot.dimensionOf(packed));
        }
        if(shortWindow) last100Total = Math.max(0, last100Total - bucket.size);
        else last1000Total = Math.max(0, last1000Total - bucket.size);
    }

    
    /** Row and column share one int key: rows and columns never reach 65,536. */
    private static int cellKey(int row, int col)
    {
        return (row << 16) | (col & 0xffff);
    }

    private static int windowStart(int frameStep, int windowLength)
    {
        if(frameStep <= 0) return 0;
        return Math.max(1, frameStep - windowLength + 1);
    }

    
    
    /** Primitive packed-event bucket; it never retains SimulationEvent objects. */
    private static final class PackedBucket
    {
        private int[] values = new int[16];
        private int size;

        void add(int value)
        {
            if(size == values.length) {
                values = Arrays.copyOf(values,
                    values.length + Math.max(16, values.length / 2));
            }
            values[size++] = value;
        }

        void clear() { size = 0; }
    }
}
