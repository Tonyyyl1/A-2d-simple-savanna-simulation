package savanna;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds detailed inspect actors exclusively from an immutable RenderFrame. */
public final class InspectRenderer
{
    private static final int MAX_SPECIAL_SCENES = 3;

    public InspectRenderResult render(RenderFrame frame, InspectLayout layout)
    {
        if(frame == null || layout == null) {
            throw new IllegalArgumentException("Inspect rendering needs a frame and layout.");
        }
        Rectangle viewport = layout.getViewportCells();
        Map<Long, SimulationEvent> latestEvents = latestEvents(
            frame.frameEventSummary.getRepresentativeEvents());
        List<RenderAnimalSnapshot> visible = new ArrayList<>();
        for(RenderAnimalSnapshot animal : frame.animals) {
            if(animal.alive && viewport.contains(animal.col, animal.row)) {
                visible.add(animal);
            }
        }
        visible.sort(Comparator.comparingLong(animal -> animal.animalId));

        List<SceneDirector.SceneActor> currentActors = new ArrayList<>();
        for(RenderAnimalSnapshot animal : visible) {
            currentActors.add(ambientActor(animal,
                latestEvents.get(animal.animalId), frame.terrainMap));
        }
        List<SceneDirector.Scene> scenes = specialScenes(frame, viewport);
        List<HistoricalEventOverlay.Entry> historical =
            new HistoricalEventOverlay().entries(frame, viewport);
        return new InspectRenderResult(scenes, currentActors, historical);
    }

    private List<SceneDirector.Scene> specialScenes(RenderFrame frame,
                                                    Rectangle viewport)
    {
        List<SimulationEvent> candidates = new ArrayList<>(
            frame.frameEventSummary.getRepresentativeEvents());
        candidates.sort(Comparator
            .comparingInt((SimulationEvent event) -> event.step).reversed()
            .thenComparingLong(event -> event.actorId));
        List<SceneDirector.Scene> scenes = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for(SimulationEvent event : candidates) {
            if(scenes.size() >= MAX_SPECIAL_SCENES || event == null ||
               !eventVisible(event, viewport)) {
                continue;
            }
            RenderAnimalSnapshot actor = currentMatch(frame, event.actorId,
                                                       event.actorSpecies, viewport);
            if(actor == null || used.contains(actor.animalId)) {
                continue;
            }
            EventMetadata metadata = EventMetadataRegistry.forType(event.type);
            if("NONE".equals(metadata.getSceneKind()) ||
               "DEATH".equals(metadata.getSceneKind())) {
                continue;
            }
            SceneDirector.SceneActor sceneActor = eventActor(actor, event,
                                                             metadata,
                                                             frame.terrainMap);
            scenes.add(new SceneDirector.Scene(
                java.util.List.of(sceneActor), Collections.emptyList(),
                event.type == SimulationEvent.EventType.MOVE ? 760.0 : 900.0,
                event));
            used.add(actor.animalId);
        }
        return scenes;
    }

    private RenderAnimalSnapshot currentMatch(RenderFrame frame, long id,
                                               String species,
                                               Rectangle viewport)
    {
        RenderAnimalSnapshot animal = frame.animalById(id);
        if(animal == null || !animal.species.equals(species) ||
           !viewport.contains(animal.col, animal.row)) {
            return null;
        }
        return animal;
    }

    private SceneDirector.SceneActor eventActor(RenderAnimalSnapshot animal,
                                                SimulationEvent event,
                                                EventMetadata metadata,
                                                TerrainMap terrain)
    {
        List<SceneDirector.Keyframe> frames = new ArrayList<>();
        double startRow = event.type == SimulationEvent.EventType.MOVE &&
            event.fromRow >= 0 ? event.fromRow : animal.row;
        double startCol = event.type == SimulationEvent.EventType.MOVE &&
            event.fromCol >= 0 ? event.fromCol : animal.col;
        double endRow = animal.row;
        double endCol = animal.col;
        frames.add(keyframe(0, startRow, startCol, 1.0, null, null, terrain));
        frames.add(keyframe(380, (startRow + endRow) / 2.0,
            (startCol + endCol) / 2.0, 1.08,
            metadata.getIconText(), metadata.getColor(), terrain));
        frames.add(keyframe(760, endRow, endCol, 1.0,
            metadata.getIconText(), metadata.getColor(), terrain));
        return new SceneDirector.SceneActor(animal.species,
            Collections.unmodifiableList(new ArrayList<>(frames)),
            speedFactor(animal), animal.survivalCritical, animal.animalId);
    }

    private SceneDirector.SceneActor ambientActor(RenderAnimalSnapshot animal,
                                                  SimulationEvent event,
                                                  TerrainMap terrain)
    {
        String marker = event == null ? actionMarker(animal.actionType) :
            EventMetadataRegistry.forType(event.type).getIconText();
        Color color = event == null
            ? (animal.actionType == null ? Color.white :
                BehaviorVisuals.colorFor(animal.actionType))
            : EventMetadataRegistry.forType(event.type).getColor();
        double angle = (animal.animalId % 16) * Math.PI / 8.0;
        double dr = Math.sin(angle) * (animal.predator ? 0.55 : 0.34);
        double dc = Math.cos(angle) * (animal.predator ? 0.55 : 0.34);
        List<SceneDirector.Keyframe> frames = new ArrayList<>();
        frames.add(keyframe(0, animal.row, animal.col, 1.0, marker, color, terrain));
        frames.add(keyframe(1200, animal.row + dr, animal.col + dc, 1.04,
                            null, null, terrain));
        frames.add(keyframe(2400, animal.row, animal.col, 0.98,
                            marker, color, terrain));
        return new SceneDirector.SceneActor(animal.species,
            Collections.unmodifiableList(new ArrayList<>(frames)),
            speedFactor(animal), animal.survivalCritical, animal.animalId);
    }

    private SceneDirector.Keyframe keyframe(double time, double row, double col,
                                            double scale, String marker,
                                            Color color, TerrainMap terrain)
    {
        double[] safe = safePosition(row, col, terrain);
        return new SceneDirector.Keyframe(time, safe[0], safe[1], scale, 0.95,
                                          0.0, marker, color);
    }

    private double[] safePosition(double row, double col, TerrainMap terrain)
    {
        if(terrain == null) return new double[] { row, col };
        int baseRow = Math.max(0, Math.min(terrain.getDepth() - 1,
                                          (int)Math.round(row)));
        int baseCol = Math.max(0, Math.min(terrain.getWidth() - 1,
                                          (int)Math.round(col)));
        Location base = new Location(baseRow, baseCol);
        if(terrain.isPassable(base) &&
           !SceneDirector.visualFootprintTouchesWater(baseRow, baseCol, terrain)) {
            return new double[] { row, col };
        }
        for(int radius = 1; radius <= 24; radius++) {
            for(int dr = -radius; dr <= radius; dr++) {
                for(int dc = -radius; dc <= radius; dc++) {
                    if(Math.max(Math.abs(dr), Math.abs(dc)) != radius) continue;
                    int nextRow = Math.max(0, Math.min(terrain.getDepth() - 1,
                                                      baseRow + dr));
                    int nextCol = Math.max(0, Math.min(terrain.getWidth() - 1,
                                                      baseCol + dc));
                    Location candidate = new Location(nextRow, nextCol);
                    if(terrain.isPassable(candidate) &&
                       !SceneDirector.visualFootprintTouchesWater(nextRow,
                                                                  nextCol,
                                                                  terrain)) {
                        return new double[] { nextRow, nextCol };
                    }
                }
            }
        }
        return new double[] { row, col };
    }

    private Map<Long, SimulationEvent> latestEvents(List<SimulationEvent> events)
    {
        Map<Long, SimulationEvent> latest = new HashMap<>();
        for(SimulationEvent event : events) {
            remember(latest, event.actorId, event);
            remember(latest, event.targetId, event);
        }
        return latest;
    }

    private void remember(Map<Long, SimulationEvent> latest, long id,
                          SimulationEvent event)
    {
        if(id < 0) return;
        SimulationEvent previous = latest.get(id);
        if(previous == null || event.step >= previous.step) latest.put(id, event);
    }

    private boolean eventVisible(SimulationEvent event, Rectangle viewport)
    {
        return viewport.contains(event.col, event.row) ||
            (event.type == SimulationEvent.EventType.MOVE && event.fromRow >= 0 &&
             viewport.contains(event.fromCol, event.fromRow));
    }

    private double speedFactor(RenderAnimalSnapshot animal)
    {
        double factor = 1.0;
        if(animal.infected) factor = Math.min(factor, 0.5);
        if(animal.staminaStage == StaminaStage.LOW) factor = Math.min(factor, 0.65);
        return factor;
    }

    private String actionMarker(ActionType action)
    {
        return action == null ? null : action.name().toLowerCase();
    }
}
