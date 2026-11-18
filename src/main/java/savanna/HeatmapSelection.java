package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable window, event and actor-species heatmap selection. */
public final class HeatmapSelection
{
    private final HeatmapWindow window;
    private final SimulationEvent.EventType eventType;
    private final String actorSpecies;

    public HeatmapSelection(HeatmapWindow window,
                            SimulationEvent.EventType eventType,
                            String actorSpecies)
    {
        this.window = window == null ? HeatmapWindow.LAST_1000 : window;
        this.eventType = eventType;
        this.actorSpecies = actorSpecies == null || actorSpecies.isEmpty()
            ? null : actorSpecies;
    }

    public static HeatmapSelection defaultSelection()
    {
        return new HeatmapSelection(HeatmapWindow.LAST_1000, null, null);
    }

    public HeatmapWindow getWindow() { return window; }
    public SimulationEvent.EventType getEventType() { return eventType; }
    public String getActorSpecies() { return actorSpecies; }

    public String getEventLabel()
    {
        return eventType == null ? "All / 全部"
            : EventMetadataRegistry.forType(eventType).getBilingualName();
    }

    public String getSpeciesLabel()
    {
        return actorSpecies == null ? "All species / 全部物种" : actorSpecies;
    }

    public static List<String> actorSpeciesChoices()
    {
        List<String> choices = new ArrayList<>();
        choices.add("All species / 全部物种");
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            choices.add(factory.getProfile().getName());
        }
        return Collections.unmodifiableList(choices);
    }

    @Override
    public boolean equals(Object other)
    {
        if(this == other) return true;
        if(!(other instanceof HeatmapSelection)) return false;
        HeatmapSelection selection = (HeatmapSelection)other;
        return window == selection.window && eventType == selection.eventType &&
               Objects.equals(actorSpecies, selection.actorSpecies);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(window, eventType, actorSpecies);
    }
}
