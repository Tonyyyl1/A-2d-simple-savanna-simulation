package savanna;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

/** Deterministic acceptance probe for the v1.7 shared render-fact pipeline. */
public final class RenderPipelineProbe
{
    private RenderPipelineProbe()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("Shared render pipeline probe");
        TestSupport.startGroup("Shared render pipeline");
        run();
        TestSupport.finishGroup();
        TestSupport.finishSuite();
        if(TestSupport.hasFailures()) {
            System.exit(1);
        }
    }

    public static void run()
    {
        Field field = new Field(8, 10);
        SimulationContext context = new SimulationContext(8, 10);
        Location start = firstPassable(context.getTerrainMap());
        Gazelle gazelle = new Gazelle(false, start);
        field.placeAnimal(gazelle, gazelle.getLocation());
        context.startStep(12, field);
        context.recordEvent(SimulationEvent.graze(12, gazelle,
                                                   gazelle.getLocation()));
        RenderFrame frame = RenderFrame.capture(12, field, context, 500, 320);

        TestSupport.assertEquals("render frame captures terrain identity",
                                 context.getTerrainMap(), frame.terrainMap);
        TestSupport.assertEquals("render frame captures one current animal", 1,
                                 frame.animals.size());
        RenderAnimalSnapshot fact = frame.animals.get(0);
        TestSupport.assertEquals("render fact captures current row", start.row(), fact.row);
        TestSupport.assertEquals("render fact captures current action",
                                 gazelle.getLastBehaviorAction(), fact.actionType);

        gazelle.setLocation(new Location(1, 1));
        TestSupport.assertEquals("render fact remains immutable after Field mutation",
                                 start.row(), fact.row);

        RenderOptions options = new RenderOptions(500, 320, false);
        OverviewRenderer overview = new OverviewRenderer();
        OverviewRenderResult result = overview.render(frame, options);
        TestSupport.assertEquals("overview renderer keeps requested width", 500,
                                 result.getImage().getWidth());
        TestSupport.assertEquals("overview renderer keeps requested height", 320,
                                 result.getImage().getHeight());
        TestSupport.assertTrue("overview renderer draws current animal facts",
                               result.getDrawnAnimalCount() > 0);
        verifyShorelinePixelsStayOffWater();

        InspectLayout layout = new InspectLayout(new Rectangle(0, 0, 10, 8));
        InspectRenderResult inspect = new InspectRenderer().render(frame, layout);
        TestSupport.assertEquals("inspect renderer uses the same current manifest",
                                 fact.animalId,
                                 inspect.getCurrentActors().get(0).animalId);

        SimulationEvent historicalDeath = new SimulationEvent(
            SimulationEvent.EventType.DEATH, 11, 2, 2, -1, -1, 9999L, -1L,
            SpeciesRegistry.ZEBRA, "", DeathCause.PREDATION);
        RenderFrame replay = frame.withEventSummary(
            java.util.Arrays.asList(frame.eventSummary.get(0), historicalDeath));
        InspectRenderResult replayInspect = new InspectRenderer().render(replay, layout);
        TestSupport.assertFalse("historical actor stays out of current inspect manifest",
            containsActor(replayInspect.getCurrentActors(), 9999L));
        List<HistoricalEventOverlay.Entry> historical =
            new HistoricalEventOverlay().entries(replay, layout.getViewportCells());
        TestSupport.assertTrue("historical event is exposed by its own overlay",
                              containsHistorical(historical, historicalDeath));

        for(SimulationEvent.EventType type : SimulationEvent.EventType.values()) {
            EventMetadata metadata = EventMetadataRegistry.forType(type);
            TestSupport.assertTrue("event metadata covers " + type,
                metadata != null && !metadata.getEnglishName().isEmpty() &&
                !metadata.getChineseName().isEmpty() && metadata.getColor() != null);
        }

        BufferedImage snapshot = RenderSnapshotTool.render(frame, 420, 260);
        TestSupport.assertEquals("snapshot tool uses requested width", 420,
                                 snapshot.getWidth());
        TestSupport.assertEquals("snapshot tool uses requested height", 260,
                                 snapshot.getHeight());
    }

    private static boolean containsActor(List<SceneDirector.SceneActor> actors,
                                         long animalId)
    {
        for(SceneDirector.SceneActor actor : actors) {
            if(actor.animalId == animalId) {
                return true;
            }
        }
        return false;
    }

    private static void verifyShorelinePixelsStayOffWater()
    {
        SimulationConfig config = SimulationConfig.builder().mapScale(1)
            .thirstEnabled(true).build();
        SimulationContext context = new SimulationContext(40, 60, config);
        Location shore = null;
        for(int row = 0; row < 40 && shore == null; row++) {
            for(int col = 0; col < 60; col++) {
                Location candidate = new Location(row, col);
                if(context.getThirstSystem().isDrinkableShore(candidate)) {
                    shore = candidate;
                    break;
                }
            }
        }
        Field field = new Field(40, 60);
        Gazelle gazelle = new Gazelle(false, shore);
        field.placeAnimal(gazelle, shore);
        context.startStep(0, field);
        RenderFrame frame = RenderFrame.capture(0, field, context, 60, 40);
        OverviewRenderResult rendered = new OverviewRenderer().render(frame,
            new RenderOptions(600, 400, false, false));
        BufferedImage overlay = rendered.getOverlayImage();
        VisualGridGeometry geometry = VisualGridGeometry.forTerrain(
            context.getTerrainMap(), overlay.getWidth(), overlay.getHeight());
        int visibleMarkerPixels = 0;
        int markerPixelsOnWater = 0;
        for(int y = 0; y < overlay.getHeight(); y++) {
            for(int x = 0; x < overlay.getWidth(); x++) {
                if(((overlay.getRGB(x, y) >>> 24) & 0xff) == 0) continue;
                visibleMarkerPixels++;
                if(geometry.isWaterPixel(context.getTerrainMap(), x, y)) {
                    markerPixelsOnWater++;
                }
            }
        }
        TestSupport.assertTrue("shared renderer keeps shoreline marker visible",
                               visibleMarkerPixels > 0);
        TestSupport.assertEquals("shared renderer clips shoreline marker off water",
                                 0, markerPixelsOnWater);
    }

    private static Location firstPassable(TerrainMap terrain)
    {
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location location = new Location(row, col);
                if(terrain.isPassable(location)) {
                    return location;
                }
            }
        }
        throw new IllegalStateException("Test terrain has no passable cell.");
    }

    private static boolean containsHistorical(
        List<HistoricalEventOverlay.Entry> entries, SimulationEvent expected)
    {
        for(HistoricalEventOverlay.Entry entry : entries) {
            if(entry.getEvent() == expected) {
                return true;
            }
        }
        return false;
    }
}
