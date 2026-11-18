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
    private final Map<Location, Integer> countsByLocation;
    private final Map<SimulationEvent.EventType, Map<Location, Integer>>
        countsByTypeLocation;
    private final Map<Integer, Integer> wholeRunPackedCounts;
    private final Map<Integer, Integer> last100PackedCounts;
    private final Map<Integer, Integer> last1000PackedCounts;
    private final Map<Integer, Integer> wholeRunTotals;
    private final Map<Integer, Integer> last100Totals;
    private final Map<Integer, Integer> last1000Totals;
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
        countsByLocation = new HashMap<>();
        countsByTypeLocation = new EnumMap<>(SimulationEvent.EventType.class);
        wholeRunPackedCounts = new HashMap<>();
        last100PackedCounts = new HashMap<>();
        last1000PackedCounts = new HashMap<>();
        wholeRunTotals = new HashMap<>();
        last100Totals = new HashMap<>();
        last1000Totals = new HashMap<>();
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
            Location location = new Location(event.row, event.col);
            countsByLocation.put(location, getCountAt(location) + 1);
            Map<Location, Integer> typeCounts = countsByTypeLocation.get(event.type);
            if(typeCounts == null) {
                typeCounts = new HashMap<>();
                countsByTypeLocation.put(event.type, typeCounts);
            }
            typeCounts.put(location, typeCounts.getOrDefault(location, 0) + 1);
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
        return countsByLocation.getOrDefault(location, 0);
    }
    public synchronized int getCountAt(Location location, SimulationEvent.EventType type)
    {
        if(type == null) return getCountAt(location);
        Map<Location, Integer> typeCounts = countsByTypeLocation.get(type);
        return typeCounts == null ? 0 : typeCounts.getOrDefault(location, 0);
    }
    public synchronized int getMaxCount() { return maxCount(countsByLocation); }
    public synchronized int getMaxCount(SimulationEvent.EventType type)
    {
        if(type == null) return getMaxCount();
        Map<Location, Integer> typeCounts = countsByTypeLocation.get(type);
        return typeCounts == null ? 0 : maxCount(typeCounts);
    }
    public synchronized Map<SimulationEvent.EventType, Integer> getCountsByType()
    {
        return Collections.unmodifiableMap(new EnumMap<>(countsByType));
    }
    public synchronized Map<Location, Integer> getCountsByLocation()
    {
        return Collections.unmodifiableMap(new HashMap<>(countsByLocation));
    }

    /** Freeze complete canonical facts for every O0b3 selection at frameStep. */
    public synchronized HeatmapSnapshot freeze(int frameStep)
    {
        advanceWindowsTo(Math.max(0, frameStep));
        Map<HeatmapWindow, Map<Integer, Integer>> packed =
            new EnumMap<>(HeatmapWindow.class);
        packed.put(HeatmapWindow.WHOLE_RUN, new HashMap<>(wholeRunPackedCounts));
        packed.put(HeatmapWindow.LAST_100, new HashMap<>(last100PackedCounts));
        packed.put(HeatmapWindow.LAST_1000, new HashMap<>(last1000PackedCounts));
        Map<HeatmapWindow, Map<Integer, Integer>> totals =
            new EnumMap<>(HeatmapWindow.class);
        totals.put(HeatmapWindow.WHOLE_RUN, new HashMap<>(wholeRunTotals));
        totals.put(HeatmapWindow.LAST_100, new HashMap<>(last100Totals));
        totals.put(HeatmapWindow.LAST_1000, new HashMap<>(last1000Totals));
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
        increment(wholeRunTotals, dimension, 1);
        increment(wholeRunPackedCounts, packed, 1);
        PackedBucket bucket = buckets[event.step % longWindow];
        bucket.add(packed);
        last100Total++;
        last1000Total++;
        increment(last100Totals, dimension, 1);
        increment(last1000Totals, dimension, 1);
        increment(last100PackedCounts, packed, 1);
        increment(last1000PackedCounts, packed, 1);
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

    private void expire(int expiredStep, Map<Integer, Integer> counts,
                        Map<Integer, Integer> totals, boolean shortWindow)
    {
        if(expiredStep <= 0) return;
        PackedBucket bucket = buckets[expiredStep % longWindow];
        for(int index = 0; index < bucket.size; index++) {
            int packed = bucket.values[index];
            decrement(counts, packed);
            decrement(totals, HeatmapSnapshot.dimensionOf(packed));
        }
        if(shortWindow) last100Total = Math.max(0, last100Total - bucket.size);
        else last1000Total = Math.max(0, last1000Total - bucket.size);
    }

    private static void increment(Map<Integer, Integer> map, int key, int amount)
    {
        map.put(key, map.getOrDefault(key, 0) + amount);
    }

    private static int windowStart(int frameStep, int windowLength)
    {
        if(frameStep <= 0) return 0;
        return Math.max(1, frameStep - windowLength + 1);
    }

    private static void decrement(Map<Integer, Integer> map, int key)
    {
        Integer prior = map.get(key);
        if(prior == null || prior <= 1) map.remove(key);
        else map.put(key, prior - 1);
    }

    private static int maxCount(Map<Location, Integer> counts)
    {
        int max = 0;
        for(Integer count : counts.values()) if(count != null && count > max) max = count;
        return max;
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
