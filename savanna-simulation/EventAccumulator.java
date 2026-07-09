import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Long-running event accumulator that is not limited by recentEvents' FIFO
 * window. It can back heat maps, replay indexes and whole-run summaries.
 */
public class EventAccumulator implements SimulationEventListener
{
    private final Map<SimulationEvent.EventType, Integer> countsByType;
    private final Map<Location, Integer> countsByLocation;
    private final Map<SimulationEvent.EventType, Map<Location, Integer>>
        countsByTypeLocation;
    private int totalEvents;

    public EventAccumulator()
    {
        countsByType = new EnumMap<>(SimulationEvent.EventType.class);
        countsByLocation = new HashMap<>();
        countsByTypeLocation = new EnumMap<>(SimulationEvent.EventType.class);
        totalEvents = 0;
    }

    public synchronized void onEvent(SimulationEvent event)
    {
        if(event == null) {
            return;
        }
        totalEvents++;
        countsByType.put(event.type, getCount(event.type) + 1);
        if(event.row >= 0 && event.col >= 0) {
            Location location = new Location(event.row, event.col);
            countsByLocation.put(location, getCountAt(location) + 1);
            Map<Location, Integer> typeCounts =
                countsByTypeLocation.get(event.type);
            if(typeCounts == null) {
                typeCounts = new HashMap<>();
                countsByTypeLocation.put(event.type, typeCounts);
            }
            Integer count = typeCounts.get(location);
            typeCounts.put(location, count == null ? 1 : count + 1);
        }
    }

    public synchronized void clear()
    {
        countsByType.clear();
        countsByLocation.clear();
        countsByTypeLocation.clear();
        totalEvents = 0;
    }

    public synchronized int getTotalEvents()
    {
        return totalEvents;
    }

    public synchronized int getCount(SimulationEvent.EventType type)
    {
        Integer count = countsByType.get(type);
        return count == null ? 0 : count;
    }

    public synchronized int getCountAt(Location location)
    {
        Integer count = countsByLocation.get(location);
        return count == null ? 0 : count;
    }

    public synchronized int getCountAt(Location location,
                                       SimulationEvent.EventType type)
    {
        if(type == null) {
            return getCountAt(location);
        }
        Map<Location, Integer> typeCounts = countsByTypeLocation.get(type);
        if(typeCounts == null) {
            return 0;
        }
        Integer count = typeCounts.get(location);
        return count == null ? 0 : count;
    }

    public synchronized int getMaxCount()
    {
        return maxCount(countsByLocation);
    }

    public synchronized int getMaxCount(SimulationEvent.EventType type)
    {
        if(type == null) {
            return getMaxCount();
        }
        Map<Location, Integer> typeCounts = countsByTypeLocation.get(type);
        return typeCounts == null ? 0 : maxCount(typeCounts);
    }

    public synchronized Map<SimulationEvent.EventType, Integer> getCountsByType()
    {
        return Collections.unmodifiableMap(
            new EnumMap<>(countsByType));
    }

    public synchronized Map<Location, Integer> getCountsByLocation()
    {
        return Collections.unmodifiableMap(new HashMap<>(countsByLocation));
    }

    private int maxCount(Map<Location, Integer> counts)
    {
        int max = 0;
        for(Integer count : counts.values()) {
            if(count != null && count > max) {
                max = count;
            }
        }
        return max;
    }
}
