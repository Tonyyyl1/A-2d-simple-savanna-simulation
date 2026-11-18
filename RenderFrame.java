import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable simulation boundary for future renderers. */
public final class RenderFrame
{
    public final int step;
    public final long publishedAtNanos;
    public final long terrainRevision;
    public final int panelWidth;
    public final int panelHeight;
    public final List<RenderAnimalSnapshot> animals;
    public final List<SimulationEvent> eventSummary;

    public RenderFrame(int step, long publishedAtNanos, long terrainRevision,
                       int panelWidth, int panelHeight,
                       List<RenderAnimalSnapshot> animals,
                       List<SimulationEvent> eventSummary)
    {
        this.step = step;
        this.publishedAtNanos = publishedAtNanos;
        this.terrainRevision = terrainRevision;
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.animals = Collections.unmodifiableList(new ArrayList<>(animals));
        this.eventSummary = Collections.unmodifiableList(new ArrayList<>(eventSummary));
    }

    public static RenderFrame capture(int step, Field field,
                                      SimulationContext context,
                                      int panelWidth, int panelHeight)
    {
        List<RenderAnimalSnapshot> snapshots = new ArrayList<>();
        for(Animal animal : field.getAnimals()) {
            if(!(animal instanceof SavannahAnimal) || !animal.isAlive() ||
               animal.getLocation() == null) {
                continue;
            }
            SavannahAnimal savannah = (SavannahAnimal)animal;
            Location location = savannah.getLocation();
            String hydration = hydrationStage(savannah);
            String survival = savannah.isSurvivalCritical() ? "CRITICAL" : "NORMAL";
            String behavior = lastBehavior(savannah.getId(),
                                           context == null
                                               ? Collections.emptyList()
                                               : context.getCurrentEvents());
            snapshots.add(new RenderAnimalSnapshot(
                savannah.getId(), savannah.getProfile().getName(),
                location.row(), location.col(), true, hydration,
                survival, behavior));
        }
        List<SimulationEvent> events = context == null
            ? Collections.emptyList() : context.getCurrentEvents();
        return new RenderFrame(step, System.nanoTime(), 0L,
                               panelWidth, panelHeight, snapshots, events);
    }

    private static String hydrationStage(SavannahAnimal animal)
    {
        if(animal.getHydrationPercent() <= 30.0) return HydrationStage.CRITICAL.name();
        if(animal.getHydrationPercent() <= 70.0 || animal.isThirsty()) {
            return HydrationStage.THIRSTY.name();
        }
        return HydrationStage.NORMAL.name();
    }

    private static String lastBehavior(long animalId, List<SimulationEvent> events)
    {
        for(int index = events.size() - 1; index >= 0; index--) {
            SimulationEvent event = events.get(index);
            if(event.actorId == animalId) return event.type.name();
        }
        return "";
    }
}
