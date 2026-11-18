package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable detailed-renderer model for one RenderFrame and viewport. */
public final class InspectRenderResult
{
    private final List<SceneDirector.Scene> scenes;
    private final List<SceneDirector.SceneActor> currentActors;
    private final List<HistoricalEventOverlay.Entry> historicalEntries;

    public InspectRenderResult(List<SceneDirector.Scene> scenes,
                               List<SceneDirector.SceneActor> currentActors,
                               List<HistoricalEventOverlay.Entry> historicalEntries)
    {
        this.scenes = Collections.unmodifiableList(new ArrayList<>(scenes));
        this.currentActors = Collections.unmodifiableList(
            new ArrayList<>(currentActors));
        this.historicalEntries = Collections.unmodifiableList(
            new ArrayList<>(historicalEntries));
    }

    public List<SceneDirector.Scene> getScenes() { return scenes; }
    public List<SceneDirector.SceneActor> getCurrentActors() { return currentActors; }
    public List<HistoricalEventOverlay.Entry> getHistoricalEntries()
    {
        return historicalEntries;
    }
}
