package savanna;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Builds replay-only markers without adding historical actors to a current manifest. */
public final class HistoricalEventOverlay
{
    public static final class Entry
    {
        private final SimulationEvent event;
        private final String iconText;
        private final Color color;

        private Entry(SimulationEvent event, String iconText, Color color)
        {
            this.event = event;
            this.iconText = iconText;
            this.color = color;
        }

        public SimulationEvent getEvent() { return event; }
        public String getIconText() { return iconText; }
        public Color getColor() { return color; }
    }

    public List<Entry> entries(RenderFrame frame, Rectangle viewportCells)
    {
        if(frame == null || viewportCells == null) {
            return Collections.emptyList();
        }
        List<Entry> result = new ArrayList<>();
        for(SimulationEvent event : frame.frameEventSummary.getRepresentativeEvents()) {
            if(event == null || !viewportCells.contains(event.col, event.row)) {
                continue;
            }
            boolean actorMissing = frame.animalById(event.actorId) == null;
            boolean targetMissing = event.targetId >= 0 &&
                                    frame.animalById(event.targetId) == null;
            if(!actorMissing && !targetMissing &&
               event.type != SimulationEvent.EventType.DEATH &&
               event.type != SimulationEvent.EventType.DISEASE_DEATH) {
                continue;
            }
            EventMetadata metadata = EventMetadataRegistry.forType(event.type);
            result.add(new Entry(event, metadata.getIconText(), metadata.getColor()));
        }
        return Collections.unmodifiableList(result);
    }
}
