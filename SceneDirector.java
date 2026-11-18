import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds short keyframe animations from recorded simulation events.
 *
 * This class is intentionally pure/read-only. It does not call field methods
 * that shuffle locations or consume the shared Randomizer stream.
 */
public class SceneDirector
{
    private static final int MAX_SPECIAL_SCENES = 3;
    private static final int MAX_MOVE_ACTORS = 24;
    private static final int TARGET_BEHAVIOR_ACTORS = 20;
    public static final double AMBIENT_DURATION_MS = 2400.0;

    public static class Keyframe
    {
        public final double timeMs;
        public final double row;
        public final double col;
        public final double scale;
        public final double alpha;
        public final double filterAlpha;
        public final String marker;
        public final Color markerColor;

        public Keyframe(double timeMs, double row, double col, double scale,
                        double alpha, double filterAlpha, String marker,
                        Color markerColor)
        {
            this.timeMs = timeMs;
            this.row = row;
            this.col = col;
            this.scale = scale;
            this.alpha = alpha;
            this.filterAlpha = filterAlpha;
            this.marker = marker;
            this.markerColor = markerColor;
        }
    }

    public static class SceneActor
    {
        public final String species;
        public final List<Keyframe> keyframes;
        public final double speedFactor;
        public final boolean tremble;
        public final long animalId;

        public SceneActor(String species, List<Keyframe> keyframes,
                          double speedFactor, boolean tremble, long animalId)
        {
            this.species = species;
            this.keyframes = keyframes;
            this.speedFactor = speedFactor;
            this.tremble = tremble;
            this.animalId = animalId;
        }

        public Keyframe at(double tMs)
        {
            double effectiveT = tMs * speedFactor;
            if(keyframes.isEmpty()) {
                return new Keyframe(0, 0, 0, 1.0, 0.0, 0.0, null, null);
            }
            Keyframe first = keyframes.get(0);
            if(effectiveT <= first.timeMs) {
                return first;
            }
            for(int i = 1; i < keyframes.size(); i++) {
                Keyframe a = keyframes.get(i - 1);
                Keyframe b = keyframes.get(i);
                if(effectiveT <= b.timeMs) {
                    double span = b.timeMs - a.timeMs;
                    double t = span <= 0 ? 1.0 : (effectiveT - a.timeMs) / span;
                    double eased = t * t * (3.0 - 2.0 * t);
                    return lerp(a, b, eased);
                }
            }
            return keyframes.get(keyframes.size() - 1);
        }

        private static Keyframe lerp(Keyframe a, Keyframe b, double t)
        {
            double row = a.row + (b.row - a.row) * t;
            double col = a.col + (b.col - a.col) * t;
            double scale = a.scale + (b.scale - a.scale) * t;
            double alpha = a.alpha + (b.alpha - a.alpha) * t;
            double filterAlpha = a.filterAlpha + (b.filterAlpha - a.filterAlpha) * t;
            String marker = b.marker != null ? b.marker : a.marker;
            Color markerColor = b.marker != null ? b.markerColor : a.markerColor;
            return new Keyframe(0, row, col, scale, alpha, filterAlpha,
                                marker, markerColor);
        }
    }

    public static class Vfx
    {
        public final String kind;
        public final double fromRow;
        public final double fromCol;
        public final double toRow;
        public final double toCol;
        public final int cellRow;
        public final int cellCol;
        public final double startMs;
        public final double endMs;

        public Vfx(String kind, double fromRow, double fromCol,
                   double toRow, double toCol, int cellRow, int cellCol,
                   double startMs, double endMs)
        {
            this.kind = kind;
            this.fromRow = fromRow;
            this.fromCol = fromCol;
            this.toRow = toRow;
            this.toCol = toCol;
            this.cellRow = cellRow;
            this.cellCol = cellCol;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    public static class Scene
    {
        public final List<SceneActor> actors;
        public final List<Vfx> vfx;
        public final double durationMs;
        public final SimulationEvent source;

        public Scene(List<SceneActor> actors, List<Vfx> vfx,
                     double durationMs, SimulationEvent source)
        {
            this.actors = actors;
            this.vfx = vfx;
            this.durationMs = durationMs;
            this.source = source;
        }
    }

    public static List<Scene> buildScene(List<SimulationEvent> events, Field field,
                                         TerrainMap terrainMap,
                                         Rectangle viewportCells)
    {
        List<SimulationEvent> special = new ArrayList<>();
        List<SimulationEvent> moves = new ArrayList<>();
        for(SimulationEvent event : events) {
            if(!isVisible(event, viewportCells)) {
                continue;
            }
            if(event.type == SimulationEvent.EventType.MOVE) {
                moves.add(event);
            }
            else {
                special.add(event);
            }
        }

        double centerRow = viewportCells.y + viewportCells.height / 2.0;
        double centerCol = viewportCells.x + viewportCells.width / 2.0;
        special.sort(eventComparator(centerRow, centerCol));
        moves.sort(moveComparator(centerRow, centerCol));

        List<Scene> scenes = new ArrayList<>();
        Set<Long> reservedActors = new HashSet<>();
        for(SimulationEvent event : special) {
            if(scenes.size() >= MAX_SPECIAL_SCENES) {
                break;
            }
            Scene scene = buildSceneFor(event, field);
            if(scene != null) {
                scene = sanitizeScene(scene, terrainMap, field);
                scenes.add(scene);
                for(SceneActor actor : scene.actors) {
                    reservedActors.add(actor.animalId);
                }
            }
        }

        List<SceneActor> moveActors = new ArrayList<>();
        SimulationEvent firstMove = null;
        for(SimulationEvent event : moves) {
            if(moveActors.size() >= MAX_MOVE_ACTORS) {
                break;
            }
            if(reservedActors.contains(event.actorId)) {
                continue;
            }
            SceneActor actor = buildMoveActor(event, field);
            if(actor != null) {
                moveActors.add(sanitizeActor(actor, terrainMap, field));
                if(firstMove == null) {
                    firstMove = event;
                }
            }
        }
        if(!moveActors.isEmpty()) {
            scenes.add(new Scene(moveActors, new ArrayList<Vfx>(), 760, firstMove));
        }
        return scenes;
    }

    public static List<SceneActor> buildAmbientActors(Field field,
                                                      Rectangle viewportCells)
    {
        return buildBehaviorActors(new ArrayList<SimulationEvent>(), field,
                                   viewportCells);
    }

    public static List<SceneActor> buildAmbientActors(Field field,
                                                      TerrainMap terrainMap,
                                                      Rectangle viewportCells)
    {
        return buildBehaviorActors(new ArrayList<SimulationEvent>(), field,
                                   terrainMap, viewportCells);
    }

    public static List<SceneActor> buildBehaviorActors(List<SimulationEvent> events,
                                                       Field field,
                                                       Rectangle viewportCells)
    {
        return buildBehaviorActors(events, field, null, viewportCells);
    }

    public static List<SceneActor> buildBehaviorActors(List<SimulationEvent> events,
                                                       Field field,
                                                       TerrainMap terrainMap,
                                                       Rectangle viewportCells)
    {
        List<SavannahAnimal> candidates = behaviorCandidates(field, viewportCells);
        Map<Long, SimulationEvent> latestEvents = latestEventsByAnimal(events);
        double centerRow = viewportCells.y + viewportCells.height / 2.0;
        double centerCol = viewportCells.x + viewportCells.width / 2.0;
        candidates.sort(Comparator
            .comparingInt((SavannahAnimal animal) ->
                behaviorRank(latestEvents.get(animal.getId())))
            .thenComparingDouble((SavannahAnimal animal) ->
                chebyshev(animal.getLocation().row(), animal.getLocation().col(),
                          centerRow, centerCol))
            .thenComparingLong(Animal::getId));

        List<SceneActor> actors = new ArrayList<>();
        for(SavannahAnimal animal : candidates) {
            SceneActor actor = buildBehaviorActor(animal,
                latestEvents.get(animal.getId()), candidates, field);
            actors.add(sanitizeActor(actor, terrainMap, field));
        }
        return actors;
    }

    private static Scene sanitizeScene(Scene scene, TerrainMap terrainMap,
                                       Field field)
    {
        if(scene == null || terrainMap == null) {
            return scene;
        }
        List<SceneActor> actors = new ArrayList<>();
        for(SceneActor actor : scene.actors) {
            actors.add(sanitizeActor(actor, terrainMap, field));
        }
        return new Scene(actors, scene.vfx, scene.durationMs, scene.source);
    }

    private static SceneActor sanitizeActor(SceneActor actor,
                                            TerrainMap terrainMap,
                                            Field field)
    {
        if(actor == null || terrainMap == null) {
            return actor;
        }
        List<Keyframe> frames = new ArrayList<>();
        for(Keyframe frame : actor.keyframes) {
            frames.add(sanitizeKeyframe(frame, terrainMap, field));
        }
        SceneActor sanitized = new SceneActor(actor.species, frames,
            actor.speedFactor, actor.tremble, actor.animalId);
        if(actorTrajectoryTouchesWater(sanitized, terrainMap)) {
            sanitized = staticSafeActor(sanitized, terrainMap, field);
        }
        return sanitized;
    }

    private static boolean actorTrajectoryTouchesWater(SceneActor actor,
                                                       TerrainMap terrainMap)
    {
        double duration = 0.0;
        for(Keyframe frame : actor.keyframes) {
            duration = Math.max(duration, frame.timeMs);
        }
        for(int time = 0; time <= duration; time += 100) {
            Keyframe frame = actor.at(time);
            if(frame.alpha > 0.01 &&
               visualFootprintTouchesWater(frame.row, frame.col, terrainMap,
                                           frame.scale)) {
                return true;
            }
        }
        return false;
    }

    private static SceneActor staticSafeActor(SceneActor actor,
                                              TerrainMap terrainMap,
                                              Field field)
    {
        Keyframe anchor = actor.keyframes.isEmpty()
            ? new Keyframe(0, 0, 0, 1.0, 0.0, 0.0, null, null)
            : actor.keyframes.get(0);
        Location safe = nearestSafeVisualCell(anchor.row, anchor.col,
            terrainMap, field, 48);
        if(safe == null) {
            return hiddenActor(actor);
        }
        List<Keyframe> frames = new ArrayList<>();
        for(Keyframe frame : actor.keyframes) {
            frames.add(new Keyframe(frame.timeMs, safe.row(), safe.col(),
                frame.scale, frame.alpha, frame.filterAlpha, frame.marker,
                frame.markerColor));
        }
        return new SceneActor(actor.species, frames, actor.speedFactor,
                              actor.tremble, actor.animalId);
    }

    private static Keyframe sanitizeKeyframe(Keyframe frame,
                                             TerrainMap terrainMap,
                                             Field field)
    {
        if(!visualFootprintTouchesWater(frame.row, frame.col, terrainMap,
                                        frame.scale)) {
            return frame;
        }
        Location nearest = nearestSafeVisualCell(frame.row, frame.col,
            terrainMap, field, 48);
        if(nearest == null) {
            return new Keyframe(frame.timeMs, frame.row, frame.col,
                frame.scale, 0.0, frame.filterAlpha, null, null);
        }
        return new Keyframe(frame.timeMs, nearest.row(), nearest.col(),
            frame.scale, frame.alpha, frame.filterAlpha, frame.marker,
            frame.markerColor);
    }

    private static SceneActor hiddenActor(SceneActor actor)
    {
        List<Keyframe> frames = new ArrayList<>();
        for(Keyframe frame : actor.keyframes) {
            frames.add(new Keyframe(frame.timeMs, frame.row, frame.col,
                frame.scale, 0.0, frame.filterAlpha, null, null));
        }
        return new SceneActor(actor.species, frames, actor.speedFactor,
                              actor.tremble, actor.animalId);
    }

    public static boolean visualFootprintTouchesWater(double row, double col,
                                                      TerrainMap terrainMap)
    {
        return visualFootprintTouchesWater(row, col, terrainMap, 1.15);
    }

    private static boolean visualFootprintTouchesWater(double row, double col,
                                                       TerrainMap terrainMap,
                                                       double scale)
    {
        if(terrainMap == null) {
            return false;
        }
        return VisualFootprint.inspectActorTouchesWater(row, col,
            Math.max(1.15, scale),
            terrainMap, VisualGridGeometry.preferredForTerrain(terrainMap));
    }

    private static Location nearestSafeVisualCell(double row, double col,
                                                  TerrainMap terrainMap,
                                                  Field field,
                                                  int maxRange)
    {
        Location nearest = nearestCellMatching(row, col, terrainMap, field,
            maxRange, true);
        return nearest;
    }

    private static Location nearestPassableCell(double row, double col,
                                                TerrainMap terrainMap,
                                                Field field,
                                                int maxRange)
    {
        return nearestCellMatching(row, col, terrainMap, field, maxRange, false);
    }

    private static Location nearestCellMatching(double row, double col,
                                                TerrainMap terrainMap,
                                                Field field,
                                                int maxRange,
                                                boolean requireSafeFootprint)
    {
        int baseRow = clampCell((int)Math.floor(row), field.getDepth());
        int baseCol = clampCell((int)Math.floor(col), field.getWidth());
        for(int range = 0; range <= maxRange; range++) {
            for(int dr = -range; dr <= range; dr++) {
                for(int dc = -range; dc <= range; dc++) {
                    if(Math.max(Math.abs(dr), Math.abs(dc)) != range) {
                        continue;
                    }
                    int nextRow = clampCell(baseRow + dr, field.getDepth());
                    int nextCol = clampCell(baseCol + dc, field.getWidth());
                    Location location = new Location(nextRow, nextCol);
                    if(!terrainMap.isPassable(location)) {
                        continue;
                    }
                    if(requireSafeFootprint &&
                       visualFootprintTouchesWater(nextRow, nextCol, terrainMap)) {
                        continue;
                    }
                    return location;
                }
            }
        }
        return null;
    }

    private static int clampCell(int value, int max)
    {
        return Math.max(0, Math.min(max - 1, value));
    }

    private static List<SavannahAnimal> behaviorCandidates(Field field,
                                                           Rectangle viewportCells)
    {
        List<SavannahAnimal> candidates = visibleAnimals(field, viewportCells);
        int padding = 2;
        while(candidates.size() < TARGET_BEHAVIOR_ACTORS) {
            Rectangle expanded = expand(viewportCells, padding, field);
            if(expanded.equals(viewportCells) ||
               expanded.width >= field.getWidth() &&
               expanded.height >= field.getDepth()) {
                candidates = visibleAnimals(field, expanded);
                break;
            }
            candidates = visibleAnimals(field, expanded);
            padding *= 2;
        }
        return candidates;
    }

    private static Rectangle expand(Rectangle viewportCells, int padding,
                                    Field field)
    {
        int x = Math.max(0, viewportCells.x - padding);
        int y = Math.max(0, viewportCells.y - padding);
        int maxX = Math.min(field.getWidth() - 1,
            viewportCells.x + viewportCells.width - 1 + padding);
        int maxY = Math.min(field.getDepth() - 1,
            viewportCells.y + viewportCells.height - 1 + padding);
        return new Rectangle(x, y, Math.max(1, maxX - x + 1),
                             Math.max(1, maxY - y + 1));
    }

    private static Map<Long, SimulationEvent> latestEventsByAnimal(
        List<SimulationEvent> events)
    {
        Map<Long, SimulationEvent> latest = new HashMap<>();
        for(SimulationEvent event : events) {
            rememberLatest(latest, event.actorId, event);
            rememberLatest(latest, event.targetId, event);
        }
        return latest;
    }

    private static void rememberLatest(Map<Long, SimulationEvent> latest,
                                       long id, SimulationEvent event)
    {
        if(id < 0) {
            return;
        }
        SimulationEvent previous = latest.get(id);
        if(previous == null ||
           event.step > previous.step ||
           (event.step == previous.step &&
            behaviorRank(event) < behaviorRank(previous))) {
            latest.put(id, event);
        }
    }

    private static int behaviorRank(SimulationEvent event)
    {
        if(event == null) {
            return 8;
        }
        switch(event.type) {
            case HUNT:          return 0;
            case GRAZE:         return 1;
            case MOVE:          return 2;
            case BIRTH:         return 3;
            case INFECTION:     return 4;
            case DISEASE_DEATH: return 5;
            case RECOVERY:      return 6;
            default:            return 7;
        }
    }

    private static List<SavannahAnimal> visibleAnimals(Field field,
                                                       Rectangle viewportCells)
    {
        List<SavannahAnimal> animals = new ArrayList<>();
        int rowMin = Math.max(0, viewportCells.y);
        int rowMax = Math.min(field.getDepth() - 1,
            viewportCells.y + viewportCells.height - 1);
        int colMin = Math.max(0, viewportCells.x);
        int colMax = Math.min(field.getWidth() - 1,
            viewportCells.x + viewportCells.width - 1);
        for(int row = rowMin; row <= rowMax; row++) {
            for(int col = colMin; col <= colMax; col++) {
                Animal animal = field.getAnimalAt(new Location(row, col));
                if(animal instanceof SavannahAnimal && animal.isAlive()) {
                    animals.add((SavannahAnimal)animal);
                }
            }
        }
        return animals;
    }

    private static SceneActor buildBehaviorActor(SavannahAnimal animal,
                                                 SimulationEvent latestEvent,
                                                 List<SavannahAnimal> herd,
                                                 Field field)
    {
        if(latestEvent != null) {
            switch(latestEvent.type) {
                case MOVE:
                    return buildObservedMoveActor(animal, latestEvent, herd);
                case GRAZE:
                    return buildObservedGrazeActor(animal, latestEvent);
                case HUNT:
                    return buildObservedHuntActor(animal, latestEvent);
                case BIRTH:
                    return buildObservedBirthActor(animal, latestEvent);
                case INFECTION:
                    return buildObservedPulseActor(animal, "sick", 0.85);
                case RECOVERY:
                    return buildObservedPulseActor(animal, "heal", 0.0);
                case DISEASE_DEATH:
                    return buildObservedPulseActor(animal, "risk", infectedFilter(animal));
                default:
                    break;
            }
        }
        return buildStateActor(animal, herd, field);
    }

    private static SceneActor buildObservedMoveActor(SavannahAnimal animal,
                                                     SimulationEvent event,
                                                     List<SavannahAnimal> herd)
    {
        Location current = animal.getLocation();
        double fromRow = event.fromRow >= 0 ? event.fromRow : current.row();
        double fromCol = event.fromCol >= 0 ? event.fromCol : current.col();
        double toRow = current.row();
        double toCol = current.col();
        String marker = moveMarker(animal, event, herd);

        List<Keyframe> frames = new ArrayList<>();
        frames.add(new Keyframe(0, fromRow, fromCol, 0.98, 0.9,
                                infectedFilter(animal), marker,
                                markerColor(marker)));
        frames.add(new Keyframe(600, (fromRow + toRow) / 2.0,
                                (fromCol + toCol) / 2.0,
                                1.08, 1.0, infectedFilter(animal), null, null));
        frames.add(new Keyframe(1200, toRow, toCol, 1.0, 1.0,
                                infectedFilter(animal), null, null));
        frames.add(new Keyframe(2400, toRow, toCol, 0.98, 0.9,
                                infectedFilter(animal), marker,
                                markerColor(marker)));
        return new SceneActor(animal.getProfile().getName(), frames,
                              speedFactorFor(animal), trembleFor(animal),
                              animal.getId());
    }

    private static SceneActor buildObservedGrazeActor(SavannahAnimal animal,
                                                      SimulationEvent event)
    {
        Location current = animal.getLocation();
        double row = current.row();
        double col = current.col();
        List<Keyframe> frames = new ArrayList<>();
        frames.add(new Keyframe(0, row, col, 1.0, 0.95,
                                infectedFilter(animal), "eat",
                                markerColor("eat")));
        frames.add(new Keyframe(500, row + 0.25, col, 0.94, 1.0,
                                infectedFilter(animal), null, null));
        frames.add(new Keyframe(1000, row + 0.12, col + 0.08, 0.96, 1.0,
                                infectedFilter(animal), null, null));
        frames.add(new Keyframe(1600, row, col, 1.0, 0.95,
                                infectedFilter(animal), "eat",
                                markerColor("eat")));
        frames.add(new Keyframe(2400, row, col, 1.0, 0.95,
                                infectedFilter(animal), null, null));
        return new SceneActor(animal.getProfile().getName(), frames,
                              speedFactorFor(animal), trembleFor(animal),
                              animal.getId());
    }

    private static SceneActor buildObservedHuntActor(SavannahAnimal animal,
                                                     SimulationEvent event)
    {
        if(animal.getId() != event.actorId) {
            return buildObservedPulseActor(animal, "danger", infectedFilter(animal));
        }
        Location current = animal.getLocation();
        double startRow = event.fromRow >= 0 ? event.fromRow : current.row();
        double startCol = event.fromCol >= 0 ? event.fromCol : current.col();
        List<Keyframe> frames = new ArrayList<>();
        frames.add(new Keyframe(0, startRow, startCol, 1.05, 0.95, 0,
                                "hunt", markerColor("hunt")));
        frames.add(new Keyframe(650, event.row, event.col, 1.15, 1.0, 0,
                                null, null));
        frames.add(new Keyframe(1300, current.row(), current.col(), 1.02,
                                0.95, 0, "hunt", markerColor("hunt")));
        frames.add(new Keyframe(2400, current.row(), current.col(), 1.0,
                                0.9, 0, null, null));
        return new SceneActor(animal.getProfile().getName(), frames,
                              speedFactorFor(animal), trembleFor(animal),
                              animal.getId());
    }

    private static SceneActor buildObservedBirthActor(SavannahAnimal animal,
                                                      SimulationEvent event)
    {
        Location current = animal.getLocation();
        String marker = animal.getId() == event.targetId ? "born" : "birth";
        double scale = animal.getId() == event.targetId ? 0.68 : 1.0;
        List<Keyframe> frames = new ArrayList<>();
        frames.add(new Keyframe(0, current.row(), current.col(), scale,
                                0.65, infectedFilter(animal), marker,
                                markerColor("birth")));
        frames.add(new Keyframe(800, current.row(), current.col(), scale * 1.12,
                                1.0, infectedFilter(animal), null, null));
        frames.add(new Keyframe(1600, current.row(), current.col(), scale,
                                0.95, infectedFilter(animal), marker,
                                markerColor("birth")));
        frames.add(new Keyframe(2400, current.row(), current.col(), scale,
                                0.9, infectedFilter(animal), null, null));
        return new SceneActor(animal.getProfile().getName(), frames,
                              speedFactorFor(animal), trembleFor(animal),
                              animal.getId());
    }

    private static SceneActor buildObservedPulseActor(SavannahAnimal animal,
                                                      String marker,
                                                      double filterAlpha)
    {
        Location current = animal.getLocation();
        List<Keyframe> frames = new ArrayList<>();
        frames.add(new Keyframe(0, current.row(), current.col(), 1.0, 0.9,
                                filterAlpha, marker, markerColor(marker)));
        frames.add(new Keyframe(800, current.row(), current.col(), 1.12, 1.0,
                                filterAlpha, null, null));
        frames.add(new Keyframe(1600, current.row(), current.col(), 1.0, 0.95,
                                filterAlpha, marker, markerColor(marker)));
        frames.add(new Keyframe(2400, current.row(), current.col(), 1.0, 0.9,
                                filterAlpha, null, null));
        return new SceneActor(animal.getProfile().getName(), frames,
                              speedFactorFor(animal), trembleFor(animal),
                              animal.getId());
    }

    private static SceneActor buildStateActor(SavannahAnimal animal,
                                              List<SavannahAnimal> herd,
                                              Field field)
    {
        Location location = animal.getLocation();
        double row = location.row();
        double col = location.col();
        double[] vector = interactionVector(animal, herd);
        double wiggle = ((animal.getId() % 5) - 2) * 0.04;
        double midRow = clamp(row + vector[0] * 0.55 + wiggle, 0,
                              field.getDepth() - 1);
        double midCol = clamp(col + vector[1] * 0.55 - wiggle, 0,
                              field.getWidth() - 1);
        double farRow = clamp(row + vector[0], 0, field.getDepth() - 1);
        double farCol = clamp(col + vector[1], 0, field.getWidth() - 1);

        List<Keyframe> frames = new ArrayList<>();
        String marker = stateMarker(animal, herd.size());
        frames.add(new Keyframe(0, row, col, 0.98, 0.88, infectedFilter(animal),
                                marker, markerColor(marker)));
        frames.add(new Keyframe(600, midRow, midCol, 1.03, 0.95,
                                infectedFilter(animal), null, null));
        frames.add(new Keyframe(1200, farRow, farCol, 1.0, 0.95,
                                infectedFilter(animal), null, null));
        frames.add(new Keyframe(1800, midRow, midCol, 1.03, 0.95,
                                infectedFilter(animal), null, null));
        frames.add(new Keyframe(2400, row, col, 0.98, 0.88,
                                infectedFilter(animal), marker,
                                markerColor(marker)));
        return new SceneActor(animal.getProfile().getName(), frames,
                              speedFactorFor(animal), trembleFor(animal),
                              animal.getId());
    }

    private static String moveMarker(SavannahAnimal animal,
                                     SimulationEvent event,
                                     List<SavannahAnimal> herd)
    {
        if(animal.getProfile().isPredator()) {
            return "prowl";
        }
        SavannahAnimal predator = nearestPredator(animal, herd);
        if(predator != null && event.fromRow >= 0 && event.fromCol >= 0) {
            double before = chebyshev(event.fromRow, event.fromCol,
                predator.getLocation().row(), predator.getLocation().col());
            double after = chebyshev(animal.getLocation().row(),
                animal.getLocation().col(), predator.getLocation().row(),
                predator.getLocation().col());
            if(after > before) {
                return "flee";
            }
        }
        return "move";
    }

    private static String stateMarker(SavannahAnimal animal, int herdSize)
    {
        if(animal.isInfected()) {
            return "sick";
        }
        if(animal.isSurvivalCritical()) {
            return "seek";
        }
        String marker = animal.getProfile().isPredator() ? "prowl" : "forage";
        if(herdSize <= 32) {
            return marker;
        }
        int spacing = Math.min(9, Math.max(2, herdSize / 28));
        return animal.getId() % spacing == 0 ? marker : null;
    }

    private static SavannahAnimal nearestPredator(SavannahAnimal animal,
                                                  List<SavannahAnimal> herd)
    {
        SavannahAnimal best = null;
        double bestDistance = Double.MAX_VALUE;
        for(SavannahAnimal other : herd) {
            if(other.getId() == animal.getId() || !other.getProfile().isPredator()) {
                continue;
            }
            double distance = chebyshev(animal.getLocation().row(),
                animal.getLocation().col(), other.getLocation().row(),
                other.getLocation().col());
            if(distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    private static double[] interactionVector(SavannahAnimal animal,
                                              List<SavannahAnimal> herd)
    {
        SavannahAnimal target = nearestInteractionTarget(animal, herd);
        if(target == null) {
            double angle = (animal.getId() % 16) * Math.PI / 8.0;
            return new double[] { Math.sin(angle) * 0.34,
                                  Math.cos(angle) * 0.34 };
        }

        double dr = target.getLocation().row() - animal.getLocation().row();
        double dc = target.getLocation().col() - animal.getLocation().col();
        double len = Math.max(0.001, Math.hypot(dr, dc));
        double direction = animal.getProfile().isPredator() ? 1.0 : -1.0;
        double strength = animal.getProfile().isPredator() ? 0.85 : 0.62;
        return new double[] { direction * (dr / len) * strength,
                              direction * (dc / len) * strength };
    }

    private static SavannahAnimal nearestInteractionTarget(SavannahAnimal animal,
                                                           List<SavannahAnimal> herd)
    {
        SavannahAnimal best = null;
        double bestDistance = Double.MAX_VALUE;
        for(SavannahAnimal other : herd) {
            if(other.getId() == animal.getId()) {
                continue;
            }
            boolean predator = animal.getProfile().isPredator();
            boolean otherPredator = other.getProfile().isPredator();
            if(predator == otherPredator) {
                continue;
            }
            double distance = chebyshev(animal.getLocation().row(),
                animal.getLocation().col(), other.getLocation().row(),
                other.getLocation().col());
            if(distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    private static double infectedFilter(SavannahAnimal animal)
    {
        return animal.isInfected() ? 0.35 : 0.0;
    }

    private static Comparator<SimulationEvent> eventComparator(
        double centerRow, double centerCol)
    {
        return (a, b) -> {
            int priority = Integer.compare(priorityRank(a), priorityRank(b));
            if(priority != 0) {
                return priority;
            }
            int step = Integer.compare(b.step, a.step);
            if(step != 0) {
                return step;
            }
            double da = chebyshev(a.row, a.col, centerRow, centerCol);
            double db = chebyshev(b.row, b.col, centerRow, centerCol);
            int distance = Double.compare(da, db);
            if(distance != 0) {
                return distance;
            }
            return Long.compare(a.actorId, b.actorId);
        };
    }

    private static Comparator<SimulationEvent> moveComparator(
        double centerRow, double centerCol)
    {
        return (a, b) -> {
            int step = Integer.compare(b.step, a.step);
            if(step != 0) {
                return step;
            }
            double da = chebyshev(a.row, a.col, centerRow, centerCol);
            double db = chebyshev(b.row, b.col, centerRow, centerCol);
            int distance = Double.compare(da, db);
            if(distance != 0) {
                return distance;
            }
            return Long.compare(a.actorId, b.actorId);
        };
    }

    private static boolean isVisible(SimulationEvent event, Rectangle viewport)
    {
        if(viewport.contains(event.col, event.row)) {
            return true;
        }
        return event.type == SimulationEvent.EventType.MOVE &&
               event.fromRow >= 0 && event.fromCol >= 0 &&
               viewport.contains(event.fromCol, event.fromRow);
    }

    private static int priorityRank(SimulationEvent event)
    {
        switch(event.type) {
            case HUNT:          return 0;
            case INFECTION:     return event.targetId >= 0 ? 1 : 5;
            case DISEASE_DEATH: return 2;
            case BIRTH:         return 3;
            case GRAZE:         return 4;
            case RECOVERY:      return 6;
            case MOVE:          return 7;
            default:            return 99;
        }
    }

    private static Scene buildSceneFor(SimulationEvent event, Field field)
    {
        switch(event.type) {
            case HUNT:          return buildHunt(event, field);
            case GRAZE:         return buildGraze(event, field);
            case BIRTH:         return buildBirth(event, field);
            case DISEASE_DEATH: return buildDeath(event);
            case INFECTION:
                return event.targetId >= 0
                    ? buildInfectionChain(event, field)
                    : buildInfectionPulse(event, field);
            case RECOVERY:      return buildRecoveryPulse(event, field);
            default:            return null;
        }
    }

    private static Scene buildHunt(SimulationEvent event, Field field)
    {
        SavannahAnimal predator = findById(field, event.actorId);
        double predRow = event.fromRow >= 0 ? event.fromRow :
            (predator == null ? event.row : predator.getLocation().row());
        double predCol = event.fromCol >= 0 ? event.fromCol :
            (predator == null ? event.col + 1 : predator.getLocation().col());
        double preyRow = event.row;
        double preyCol = event.col;
        double dirRow = preyRow - predRow;
        double dirCol = preyCol - predCol;
        double len = Math.max(0.001, Math.hypot(dirRow, dirCol));
        double ur = dirRow / len;
        double uc = dirCol / len;

        List<Keyframe> preyFrames = new ArrayList<>();
        preyFrames.add(new Keyframe(0, preyRow + ur, preyCol + uc, 1.0, 1.0, 0, null, null));
        preyFrames.add(new Keyframe(600, preyRow, preyCol, 1.1, 1.0, 0, "X", eventColor("HUNT")));
        preyFrames.add(new Keyframe(1200, preyRow, preyCol, 0.8, 0.55, 0, null, null));
        preyFrames.add(new Keyframe(1800, preyRow, preyCol, 0.5, 0.0, 0, null, null));

        List<Keyframe> predFrames = new ArrayList<>();
        predFrames.add(new Keyframe(0, predRow, predCol, 1.0, 0.95, 0, null, null));
        predFrames.add(new Keyframe(600, preyRow + ur * 0.3, preyCol + uc * 0.3,
                                    1.0, 0.95, 0, null, null));
        predFrames.add(new Keyframe(1200, preyRow, preyCol, 1.0, 0.95, 0, null, null));
        predFrames.add(new Keyframe(1800, preyRow, preyCol, 1.0, 0.95, 0, null, null));

        List<SceneActor> actors = new ArrayList<>();
        actors.add(new SceneActor(event.targetSpecies, preyFrames, 1.0, false,
                                  event.targetId));
        actors.add(new SceneActor(event.actorSpecies, predFrames,
                                  speedFactorFor(predator),
                                  trembleFor(predator), event.actorId));
        return new Scene(actors, new ArrayList<Vfx>(), 1800, event);
    }

    private static Scene buildGraze(SimulationEvent event, Field field)
    {
        SavannahAnimal animal = findById(field, event.actorId);
        double row = event.row;
        double col = event.col;
        List<Keyframe> frames = new ArrayList<>();
        frames.add(new Keyframe(0, row, col, 1.0, 1.0, 0, null, null));
        frames.add(new Keyframe(360, row + 0.22, col, 0.95, 1.0, 0, null, null));
        frames.add(new Keyframe(720, row, col, 1.0, 1.0, 0, null, null));
        List<SceneActor> actors = new ArrayList<>();
        actors.add(new SceneActor(event.actorSpecies, frames,
                                  speedFactorFor(animal),
                                  trembleFor(animal), event.actorId));
        List<Vfx> vfx = new ArrayList<>();
        vfx.add(new Vfx("GRASS_DIM", 0, 0, 0, 0, event.row, event.col, 0, 720));
        return new Scene(actors, vfx, 720, event);
    }

    private static Scene buildBirth(SimulationEvent event, Field field)
    {
        SavannahAnimal parent = findById(field, event.actorId);
        double row = event.row;
        double col = event.col;

        List<Keyframe> parentFrames = new ArrayList<>();
        parentFrames.add(new Keyframe(0, row, col - 1.2, 1.0, 1.0, 0, null, null));
        parentFrames.add(new Keyframe(600, row, col - 0.45, 1.0, 1.0, 0, "+", eventColor("BIRTH")));
        parentFrames.add(new Keyframe(1200, row, col - 0.75, 1.0, 1.0, 0, null, null));

        List<Keyframe> childFrames = new ArrayList<>();
        childFrames.add(new Keyframe(0, row, col, 0.5, 0.0, 0, null, null));
        childFrames.add(new Keyframe(600, row, col, 0.5, 0.45, 0, null, null));
        childFrames.add(new Keyframe(1200, row, col, 0.62, 1.0, 0, null, null));

        List<SceneActor> actors = new ArrayList<>();
        actors.add(new SceneActor(event.actorSpecies, parentFrames,
                                  speedFactorFor(parent),
                                  trembleFor(parent), event.actorId));
        actors.add(new SceneActor(event.targetSpecies, childFrames, 1.0,
                                  false, event.targetId));
        return new Scene(actors, new ArrayList<Vfx>(), 1200, event);
    }

    private static Scene buildDeath(SimulationEvent event)
    {
        Color purple = new Color(160, 40, 160);
        List<Keyframe> frames = new ArrayList<>();
        frames.add(new Keyframe(0, event.row, event.col, 1.0, 1.0, 0, null, null));
        frames.add(new Keyframe(500, event.row + 0.15, event.col, 1.0, 0.7, 0, "!", purple));
        frames.add(new Keyframe(1000, event.row + 0.15, event.col, 0.75, 0.0, 0, null, null));
        List<SceneActor> actors = new ArrayList<>();
        actors.add(new SceneActor(event.actorSpecies, frames, 1.0, false,
                                  event.actorId));
        return new Scene(actors, new ArrayList<Vfx>(), 1000, event);
    }

    private static Scene buildInfectionChain(SimulationEvent event, Field field)
    {
        SavannahAnimal target = findById(field, event.targetId);
        double sourceRow = event.fromRow >= 0 ? event.fromRow : event.row;
        double sourceCol = event.fromCol >= 0 ? event.fromCol : event.col - 1;
        double targetRow = event.row;
        double targetCol = event.col;

        List<Keyframe> sourceFrames = new ArrayList<>();
        sourceFrames.add(new Keyframe(0, sourceRow, sourceCol, 1.0, 0.55, 0, null, null));
        sourceFrames.add(new Keyframe(1000, sourceRow, sourceCol, 1.0, 0.35, 0, null, null));

        List<Keyframe> targetFrames = new ArrayList<>();
        targetFrames.add(new Keyframe(0, targetRow, targetCol, 1.0, 1.0, 0.0, null, null));
        targetFrames.add(new Keyframe(500, targetRow, targetCol, 1.0, 1.0, 0.35, null, null));
        targetFrames.add(new Keyframe(1000, targetRow, targetCol, 1.0, 1.0, 0.75, null, null));

        List<SceneActor> actors = new ArrayList<>();
        actors.add(new SceneActor(event.actorSpecies, sourceFrames, 1.0,
                                  false, event.actorId));
        actors.add(new SceneActor(event.targetSpecies, targetFrames,
                                  speedFactorFor(target),
                                  trembleFor(target), event.targetId));

        List<Vfx> vfx = new ArrayList<>();
        vfx.add(new Vfx("INFECTION_ARC", sourceRow, sourceCol,
                        targetRow, targetCol, event.row, event.col, 120, 900));
        return new Scene(actors, vfx, 1000, event);
    }

    private static Scene buildInfectionPulse(SimulationEvent event, Field field)
    {
        SavannahAnimal animal = findById(field, event.actorId);
        List<Keyframe> frames = new ArrayList<>();
        frames.add(new Keyframe(0, event.row, event.col, 1.0, 1.0, 0.0, null, null));
        frames.add(new Keyframe(800, event.row, event.col, 1.0, 1.0, 0.8, null, null));
        List<SceneActor> actors = new ArrayList<>();
        actors.add(new SceneActor(event.actorSpecies, frames,
                                  speedFactorFor(animal),
                                  trembleFor(animal), event.actorId));
        return new Scene(actors, new ArrayList<Vfx>(), 800, event);
    }

    private static Scene buildRecoveryPulse(SimulationEvent event, Field field)
    {
        SavannahAnimal animal = findById(field, event.actorId);
        List<Keyframe> frames = new ArrayList<>();
        frames.add(new Keyframe(0, event.row, event.col, 1.0, 1.0, 0.8, null, null));
        frames.add(new Keyframe(800, event.row, event.col, 1.0, 1.0, 0.0, null, null));
        List<SceneActor> actors = new ArrayList<>();
        actors.add(new SceneActor(event.actorSpecies, frames,
                                  speedFactorFor(animal),
                                  trembleFor(animal), event.actorId));
        return new Scene(actors, new ArrayList<Vfx>(), 800, event);
    }

    private static SceneActor buildMoveActor(SimulationEvent event, Field field)
    {
        if(event.fromRow < 0 || event.fromCol < 0) {
            return null;
        }
        SavannahAnimal animal = findById(field, event.actorId);
        List<Keyframe> frames = new ArrayList<>();
        frames.add(new Keyframe(0, event.fromRow, event.fromCol, 1.0, 0.95, 0, null, null));
        frames.add(new Keyframe(380, (event.fromRow + event.row) / 2.0,
                                (event.fromCol + event.col) / 2.0,
                                1.06, 1.0, 0, null, null));
        frames.add(new Keyframe(760, event.row, event.col, 1.0, 0.95, 0, null, null));
        return new SceneActor(event.actorSpecies, frames,
                              speedFactorFor(animal), trembleFor(animal),
                              event.actorId);
    }

    private static SavannahAnimal findById(Field field, long id)
    {
        if(id < 0) {
            return null;
        }
        for(Animal animal : field.getAnimals()) {
            if(animal instanceof SavannahAnimal && animal.isAlive() &&
               animal.getId() == id) {
                return (SavannahAnimal)animal;
            }
        }
        return null;
    }

    private static double speedFactorFor(SavannahAnimal animal)
    {
        if(animal == null) {
            return 1.0;
        }
        double factor = 1.0;
        if(animal.isInfected()) {
            factor = Math.min(factor, 0.5);
        }
        if(animal.getStaminaStage() == StaminaStage.LOW) {
            factor = Math.min(factor, 0.65);
        }
        return factor;
    }

    private static boolean trembleFor(SavannahAnimal animal)
    {
        return animal != null && animal.isSurvivalCritical();
    }

    private static double chebyshev(double r1, double c1, double r2, double c2)
    {
        return Math.max(Math.abs(r1 - r2), Math.abs(c1 - c2));
    }

    private static double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private static Color eventColor(String kind)
    {
        switch(kind) {
            case "HUNT":  return new Color(220, 80, 60);
            case "BIRTH": return new Color(80, 200, 100);
            default:      return Color.white;
        }
    }

    private static Color markerColor(String marker)
    {
        if(marker == null) {
            return Color.white;
        }
        switch(marker) {
            case "hunt":
            case "danger":
                return new Color(230, 80, 55);
            case "flee":
            case "seek":
                return new Color(255, 170, 55);
            case "eat":
            case "forage":
                return new Color(110, 205, 90);
            case "birth":
            case "born":
            case "heal":
                return new Color(90, 205, 135);
            case "sick":
            case "risk":
                return new Color(120, 210, 90);
            case "move":
            case "prowl":
                return new Color(220, 220, 190);
            default:
                return Color.white;
        }
    }
}
