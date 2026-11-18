package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns event retention, exact actor-event boundaries and visible-frame counts.
 * Step lists are read-only views; recent events and frame summaries are snapshots.
 * Complete interval counts never depend on the bounded recent-event buffer.
 */
final class SimulationEventJournal
{
    private final ObservationSchedule schedule;
    private final int representativeEventCapacity;
    private final BoundedEventDeque<SimulationEvent> recentEvents;
    private final List<SimulationEventListener> listeners = new ArrayList<>();
    private List<SimulationEvent> currentEvents = new ArrayList<>();
    private List<SimulationEvent> previousEvents = new ArrayList<>();
    private Map<Long, Map<SimulationEvent.EventType, List<Integer>>>
        currentEventIndicesByActor = new HashMap<>();
    private FrameEventSummary.Builder frameEventSummary;

    SimulationEventJournal(ObservationConfig config)
    {
        schedule = new ObservationSchedule(config);
        representativeEventCapacity = config.getRepresentativeEventCapacity();
        recentEvents = new BoundedEventDeque<>(config.getRecentEventCapacity());
        frameEventSummary = new FrameEventSummary.Builder(0, 0,
            representativeEventCapacity);
    }

    void startStep(int step)
    {
        int startStep = schedule.visibleIntervalStart(step);
        if(frameEventSummary.freeze().getStartStep() != startStep) {
            frameEventSummary = new FrameEventSummary.Builder(startStep, step,
                representativeEventCapacity);
        }
        else {
            frameEventSummary.advanceTo(step);
        }
        previousEvents = currentEvents;
        currentEvents = new ArrayList<>();
        currentEventIndicesByActor = new HashMap<>();
    }

    void record(SimulationEvent event)
    {
        if(event == null) return;
        int index = currentEvents.size();
        currentEvents.add(event);
        if(event.actorId > 0L && event.type != null) {
            Map<SimulationEvent.EventType, List<Integer>> byType =
                currentEventIndicesByActor.computeIfAbsent(event.actorId,
                    ignored -> new EnumMap<>(SimulationEvent.EventType.class));
            byType.computeIfAbsent(event.type, ignored -> new ArrayList<>())
                .add(index);
        }
        recentEvents.addLast(event);
        frameEventSummary.add(event);
        for(SimulationEventListener listener : listeners) {
            listener.onEvent(event);
        }
    }

    void addListener(SimulationEventListener listener)
    {
        if(listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    void removeListener(SimulationEventListener listener)
    {
        listeners.remove(listener);
    }

    List<SimulationEvent> getCurrentEvents()
    {
        return Collections.unmodifiableList(currentEvents);
    }

    boolean hasCurrentActorEventSince(int eventIndex,
                                      SimulationEvent.EventType type,
                                      long actorId)
    {
        if(eventIndex < 0 || type == null || actorId <= 0L) return false;
        Map<SimulationEvent.EventType, List<Integer>> byType =
            currentEventIndicesByActor.get(actorId);
        List<Integer> indices = byType == null ? null : byType.get(type);
        if(indices == null || indices.isEmpty()) return false;
        int low = 0;
        int high = indices.size() - 1;
        while(low <= high) {
            int middle = (low + high) >>> 1;
            if(indices.get(middle).intValue() < eventIndex) low = middle + 1;
            else high = middle - 1;
        }
        return low < indices.size();
    }

    List<SimulationEvent> getPreviousEvents()
    {
        return Collections.unmodifiableList(previousEvents);
    }

    List<SimulationEvent> getRecentEvents()
    {
        return recentEvents.snapshot();
    }

    FrameEventSummary getFrameEventSummary()
    {
        return frameEventSummary.freeze();
    }
}
