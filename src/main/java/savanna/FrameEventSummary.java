package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable event facts for the producer-defined visible-frame interval.
 *
 * <p>Counts are complete even when representative event samples are bounded.
 * The samples are deterministic first-recorded samples, ordered exactly as
 * events reached the simulation event bus.</p>
 */
public final class FrameEventSummary
{
    /** Small enough for inspect rendering while keeping dense-step facts exact. */
    public static final int MAX_REPRESENTATIVE_EVENTS =
        ConfigDescriptorRegistry.REPRESENTATIVE_EVENT_CAPACITY.getDefaultValue();

    /** Immutable count key: event type x actor species. */
    public static final class CountKey implements Comparable<CountKey>
    {
        private final SimulationEvent.EventType eventType;
        private final String actorSpecies;

        public CountKey(SimulationEvent.EventType eventType, String actorSpecies)
        {
            if(eventType == null) {
                throw new IllegalArgumentException("event type is required");
            }
            this.eventType = eventType;
            this.actorSpecies = actorSpecies == null ? "" : actorSpecies;
        }

        public SimulationEvent.EventType getEventType()
        {
            return eventType;
        }

        public String getActorSpecies()
        {
            return actorSpecies;
        }

        @Override
        public int compareTo(CountKey other)
        {
            int typeComparison = Integer.compare(eventType.ordinal(),
                                                  other.eventType.ordinal());
            return typeComparison != 0 ? typeComparison
                                       : actorSpecies.compareTo(other.actorSpecies);
        }

        @Override
        public boolean equals(Object other)
        {
            if(this == other) return true;
            if(!(other instanceof CountKey)) return false;
            CountKey key = (CountKey)other;
            return eventType == key.eventType && actorSpecies.equals(key.actorSpecies);
        }

        @Override
        public int hashCode()
        {
            return 31 * eventType.hashCode() + actorSpecies.hashCode();
        }

        @Override
        public String toString()
        {
            return eventType + " x " + actorSpecies;
        }
    }

    /** Mutable, package-visible collector used only while a producer interval is open. */
    static final class Builder
    {
        private final int startStep;
        private int endStep;
        private long totalEventCount;
        private boolean representativeSamplesTruncated;
        private final int sampleCapacity;
        private final Map<CountKey, Long> counts = new TreeMap<>();
        private final List<SimulationEvent> representativeEvents = new ArrayList<>();

        Builder(int startStep, int endStep, int sampleCapacity)
        {
            if(startStep < 0 || endStep < startStep || sampleCapacity <= 0) {
                throw new IllegalArgumentException("invalid frame-event interval");
            }
            this.startStep = startStep;
            this.endStep = endStep;
            this.sampleCapacity = sampleCapacity;
        }

        void advanceTo(int step)
        {
            if(step < startStep || step < endStep) {
                throw new IllegalArgumentException("frame-event interval cannot move backward");
            }
            endStep = step;
        }

        void add(SimulationEvent event)
        {
            if(event == null) return;
            CountKey key = new CountKey(event.type, event.actorSpecies);
            Long prior = counts.get(key);
            counts.put(key, prior == null ? 1L : prior + 1L);
            totalEventCount++;
            if(representativeEvents.size() < sampleCapacity) {
                representativeEvents.add(event);
            }
            else {
                representativeSamplesTruncated = true;
            }
        }

        FrameEventSummary freeze()
        {
            return new FrameEventSummary(startStep, endStep, totalEventCount,
                counts, representativeEvents, representativeSamplesTruncated);
        }
    }

    private final int startStep;
    private final int endStep;
    private final long totalEventCount;
    private final Map<CountKey, Long> counts;
    private final List<SimulationEvent> representativeEvents;
    private final boolean representativeSamplesTruncated;

    private FrameEventSummary(int startStep, int endStep, long totalEventCount,
                              Map<CountKey, Long> counts,
                              List<SimulationEvent> representativeEvents,
                              boolean representativeSamplesTruncated)
    {
        this.startStep = startStep;
        this.endStep = endStep;
        this.totalEventCount = totalEventCount;
        this.counts = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
        this.representativeEvents = Collections.unmodifiableList(
            new ArrayList<>(representativeEvents));
        this.representativeSamplesTruncated = representativeSamplesTruncated;
    }

    public static FrameEventSummary emptyAtStep(int frameStep)
    {
        return builderForFrameStep(frameStep).freeze();
    }

    public static Builder builderForFrameStep(int frameStep)
    {
        return builderForFrameStep(frameStep, ObservationConfig.defaults());
    }

    /** Compatibility helper; production contexts pass an explicit schedule. */
    public static Builder builderForFrameStep(int frameStep,
                                              ObservationConfig config)
    {
        ObservationConfig resolved = config == null ? ObservationConfig.defaults()
                                                    : config;
        if(frameStep <= 0) {
            return new Builder(0, 0, resolved.getRepresentativeEventCapacity());
        }
        ObservationSchedule schedule = new ObservationSchedule(resolved);
        return new Builder(schedule.visibleIntervalStart(frameStep), frameStep,
                           resolved.getRepresentativeEventCapacity());
    }

    /** Build a bounded compatibility summary from supplied replay events. */
    static FrameEventSummary fromEvents(int frameStep, List<SimulationEvent> events)
    {
        Builder builder = builderForFrameStep(frameStep);
        if(events != null) {
            for(SimulationEvent event : events) {
                builder.add(event);
            }
        }
        return builder.freeze();
    }

    public static int startStepFor(int frameStep)
    {
        return new ObservationSchedule(ObservationConfig.defaults())
            .visibleIntervalStart(frameStep);
    }

    public int getStartStep()
    {
        return startStep;
    }

    public int getEndStep()
    {
        return endStep;
    }

    public long getTotalEventCount()
    {
        return totalEventCount;
    }

    public long getCount(SimulationEvent.EventType type, String actorSpecies)
    {
        if(type == null) return 0L;
        Long count = counts.get(new CountKey(type, actorSpecies));
        return count == null ? 0L : count;
    }

    public Map<CountKey, Long> getCounts()
    {
        return counts;
    }

    public List<SimulationEvent> getRepresentativeEvents()
    {
        return representativeEvents;
    }

    public boolean isRepresentativeSamplesTruncated()
    {
        return representativeSamplesTruncated;
    }
}
