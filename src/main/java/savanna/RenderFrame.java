package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable simulation boundary shared by every renderer and snapshot tool. */
public final class RenderFrame
{
    public final int step;
    public final long publishedAtNanos;
    public final long terrainRevision;
    public final int panelWidth;
    public final int panelHeight;
    public final TerrainMap terrainMap;
    public final RenderMetrics metrics;
    public final RenderProvenance provenance;
    public final List<RenderAnimalSnapshot> animals;
    /** Complete, immutable producer-defined event facts for this frame interval. */
    public final FrameEventSummary frameEventSummary;
    /** Null on ordinary per-step frames; frozen only for a visible heatmap frame. */
    public final HeatmapSnapshot heatmapSnapshot;
    /**
     * Deterministic bounded representative samples retained for legacy inspect
     * replay callers.  Use {@link #frameEventSummary} for interval facts.
     */
    @Deprecated
    public final List<SimulationEvent> eventSummary;

    /** Compatibility constructor for existing A2 tests. */
    public RenderFrame(int step, long publishedAtNanos, long terrainRevision,
                       int panelWidth, int panelHeight,
                       List<RenderAnimalSnapshot> animals,
                       List<SimulationEvent> eventSummary)
    {
        this(step, publishedAtNanos, terrainRevision, panelWidth, panelHeight,
             null, RenderMetrics.empty(), RenderProvenance.unavailable(),
             animals, FrameEventSummary.fromEvents(step, eventSummary), null);
    }

    public RenderFrame(int step, long publishedAtNanos, long terrainRevision,
                       int panelWidth, int panelHeight, TerrainMap terrainMap,
                       RenderMetrics metrics,
                       List<RenderAnimalSnapshot> animals,
                       List<SimulationEvent> eventSummary)
    {
        this(step, publishedAtNanos, terrainRevision, panelWidth, panelHeight,
             terrainMap, metrics, RenderProvenance.unavailable(), animals,
             FrameEventSummary.fromEvents(step, eventSummary), null);
    }

    public RenderFrame(int step, long publishedAtNanos, long terrainRevision,
                       int panelWidth, int panelHeight, TerrainMap terrainMap,
                       RenderMetrics metrics, RenderProvenance provenance,
                       List<RenderAnimalSnapshot> animals,
                       List<SimulationEvent> eventSummary)
    {
        this(step, publishedAtNanos, terrainRevision, panelWidth, panelHeight,
             terrainMap, metrics, provenance, animals,
             FrameEventSummary.fromEvents(step, eventSummary), null);
    }

    public RenderFrame(int step, long publishedAtNanos, long terrainRevision,
                       int panelWidth, int panelHeight, TerrainMap terrainMap,
                       RenderMetrics metrics, RenderProvenance provenance,
                       List<RenderAnimalSnapshot> animals,
                       FrameEventSummary frameEventSummary)
    {
        this(step, publishedAtNanos, terrainRevision, panelWidth, panelHeight,
             terrainMap, metrics, provenance, animals, frameEventSummary, null);
    }

    public RenderFrame(int step, long publishedAtNanos, long terrainRevision,
                       int panelWidth, int panelHeight, TerrainMap terrainMap,
                       RenderMetrics metrics, RenderProvenance provenance,
                       List<RenderAnimalSnapshot> animals,
                       FrameEventSummary frameEventSummary,
                       HeatmapSnapshot heatmapSnapshot)
    {
        this.step = step;
        this.publishedAtNanos = publishedAtNanos;
        this.terrainRevision = terrainRevision;
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.terrainMap = terrainMap;
        this.metrics = metrics == null ? RenderMetrics.empty() : metrics;
        this.provenance = provenance == null ? RenderProvenance.unavailable()
                                             : provenance;
        this.animals = Collections.unmodifiableList(new ArrayList<>(animals));
        this.frameEventSummary = frameEventSummary == null
            ? FrameEventSummary.emptyAtStep(step) : frameEventSummary;
        if(heatmapSnapshot != null && heatmapSnapshot.getFrameStep() != step) {
            throw new IllegalArgumentException(
                "Heatmap snapshot step must equal render-frame step");
        }
        this.heatmapSnapshot = heatmapSnapshot;
        this.eventSummary = this.frameEventSummary.getRepresentativeEvents();
    }

    public static RenderFrame capture(int step, Field field,
                                      SimulationContext context,
                                      int panelWidth, int panelHeight)
    {
        FrameEventSummary eventFacts = context == null
            ? FrameEventSummary.emptyAtStep(step) : context.getFrameEventSummary();
        List<SimulationEvent> events = eventFacts.getRepresentativeEvents();
        List<RenderAnimalSnapshot> snapshots = new ArrayList<>();
        if(field != null) {
            for(Animal animal : field.getAnimals()) {
                if(!(animal instanceof SavannahAnimal) || !animal.isAlive() ||
                   animal.getLocation() == null) {
                    continue;
                }
                SavannahAnimal savannah = (SavannahAnimal)animal;
                snapshots.add(RenderAnimalSnapshot.capture(savannah,
                    lastBehavior(savannah.getId(), events)));
            }
        }
        TerrainMap terrain = context == null ? null : context.getTerrainMap();
        long terrainRevision = terrain == null ? 0L : terrain.getSeed();
        return new RenderFrame(step, System.nanoTime(), terrainRevision,
            panelWidth, panelHeight, terrain, RenderMetrics.capture(field, context),
            context == null ? RenderProvenance.unavailable()
                            : context.getRenderProvenance(), snapshots, eventFacts);
    }

    public RenderFrame withEventSummary(List<SimulationEvent> events)
    {
        return new RenderFrame(step, publishedAtNanos, terrainRevision,
            panelWidth, panelHeight, terrainMap, metrics, provenance, animals,
            FrameEventSummary.fromEvents(step, events), heatmapSnapshot);
    }

    public RenderFrame withFrameEventSummary(FrameEventSummary eventFacts)
    {
        return new RenderFrame(step, publishedAtNanos, terrainRevision,
            panelWidth, panelHeight, terrainMap, metrics, provenance, animals,
            eventFacts, heatmapSnapshot);
    }

    public RenderFrame withHeatmapSnapshot(HeatmapSnapshot snapshot)
    {
        return new RenderFrame(step, publishedAtNanos, terrainRevision,
            panelWidth, panelHeight, terrainMap, metrics, provenance, animals,
            frameEventSummary, snapshot);
    }

    public RenderAnimalSnapshot animalById(long animalId)
    {
        for(RenderAnimalSnapshot animal : animals) {
            if(animal.animalId == animalId && animal.alive) {
                return animal;
            }
        }
        return null;
    }

    private static String lastBehavior(long animalId, List<SimulationEvent> events)
    {
        for(int index = events.size() - 1; index >= 0; index--) {
            SimulationEvent event = events.get(index);
            if(event.actorId == animalId || event.targetId == animalId) {
                return event.type.name();
            }
        }
        return "";
    }
}
