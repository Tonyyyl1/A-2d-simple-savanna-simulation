package savanna;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A1/B1 audit probe. It observes the render inputs without changing either
 * renderer or simulation state.
 */
public class RenderConsistencyProbe
{
    public static void main(String[] args)
    {
        int checks = 0;
        checks += checkCurrentActorsStayInCurrentFacts();
        checks += checkHistoricalDeadActorIsNotCurrent();
        checks += checkSpeciesMismatchIsNotCurrent();
        checks += checkTargetSpeciesMismatchIsNotCurrent();
        checks += checkActorOutsideViewportIsNotCurrent();
        checks += checkCurrentMoveAnimationIsRetained();
        checks += checkZoomSequence();
        checks += checkViewportRoundTrip();
        System.out.println("RenderConsistencyProbe passed " + checks + "/8 checks");
    }

    private static int checkCurrentActorsStayInCurrentFacts()
    {
        Simulator simulator = new Simulator(false);
        Field field = simulator.getField();
        Map<Long, String> current = new HashMap<>();
        for(Animal animal : field.getAnimals()) {
            if(animal.isAlive() && animal instanceof SavannahAnimal) {
                current.put(animal.getId(),
                           ((SavannahAnimal)animal).getProfile().getName());
            }
        }
        List<SceneDirector.SceneActor> actors = SceneDirector.buildBehaviorActors(
            java.util.Collections.emptyList(), field, null,
            new Rectangle(0, 0, field.getWidth(), field.getDepth()));
        for(SceneDirector.SceneActor actor : actors) {
            if(!current.containsKey(actor.animalId) ||
               !current.get(actor.animalId).equals(actor.species)) {
                throw new AssertionError("current actor is absent or species-mismatched: " +
                                         actor.animalId);
            }
        }
        return 1;
    }

    private static int checkHistoricalDeadActorIsNotCurrent()
    {
        Field field = new Field(3, 3);
        Gazelle dead = new Gazelle(false, new Location(1, 1));
        field.placeAnimal(dead, dead.getLocation());
        TestSupport.invokePrivate(dead, "setDead", new Class<?>[0], new Object[0]);
        field.removeAnimal(dead);
        SimulationEvent historicalMove = new SimulationEvent(
            SimulationEvent.EventType.MOVE, 2, 1, 1, 0, 0, dead.getId(), -1,
            SpeciesRegistry.GAZELLE, "");
        List<SceneDirector.Scene> scenes = SceneDirector.buildScene(
            List.of(historicalMove), field, null, new Rectangle(0, 0, 3, 3));
        if(!scenes.isEmpty()) {
            throw new AssertionError("dead historical actor entered current scene");
        }
        return 1;
    }

    private static int checkSpeciesMismatchIsNotCurrent()
    {
        Field field = new Field(3, 3);
        Gazelle gazelle = new Gazelle(false, new Location(1, 2));
        field.placeAnimal(gazelle, gazelle.getLocation());
        SimulationEvent wrongSpecies = new SimulationEvent(
            SimulationEvent.EventType.MOVE, 3, 1, 2, 1, 1,
            gazelle.getId(), -1, SpeciesRegistry.ZEBRA, "");
        List<SceneDirector.Scene> scenes = SceneDirector.buildScene(
            List.of(wrongSpecies), field, null, new Rectangle(0, 0, 3, 3));
        if(!scenes.isEmpty()) {
            throw new AssertionError("species-mismatched event entered current scene");
        }
        return 1;
    }

    private static int checkCurrentMoveAnimationIsRetained()
    {
        Field field = new Field(3, 3);
        Gazelle gazelle = new Gazelle(false, new Location(1, 2));
        field.placeAnimal(gazelle, gazelle.getLocation());
        SimulationEvent move = SimulationEvent.move(4, gazelle,
            new Location(1, 1), gazelle.getLocation());
        List<SceneDirector.Scene> scenes = SceneDirector.buildScene(
            List.of(move), field, null, new Rectangle(0, 0, 3, 3));
        if(scenes.size() != 1 || scenes.get(0).actors.size() != 1) {
            throw new AssertionError("current living move scene was removed");
        }
        SceneDirector.SceneActor actor = scenes.get(0).actors.get(0);
        boolean moved = Math.abs(actor.at(0).row - actor.at(760).row) > 0.01 ||
                        Math.abs(actor.at(0).col - actor.at(760).col) > 0.01;
        if(actor.animalId != gazelle.getId() ||
           !SpeciesRegistry.GAZELLE.equals(actor.species) || !moved) {
            throw new AssertionError("current living move animation changed");
        }
        return 1;
    }

    private static int checkActorOutsideViewportIsNotCurrent()
    {
        Field field = new Field(5, 5);
        Gazelle gazelle = new Gazelle(false, new Location(4, 4));
        field.placeAnimal(gazelle, gazelle.getLocation());
        SimulationEvent oldViewportMove = new SimulationEvent(
            SimulationEvent.EventType.MOVE, 4, 1, 1, 1, 0,
            gazelle.getId(), -1, SpeciesRegistry.GAZELLE, "");
        List<SceneDirector.Scene> scenes = SceneDirector.buildScene(
            List.of(oldViewportMove), field, null, new Rectangle(0, 0, 3, 3));
        if(!scenes.isEmpty()) {
            throw new AssertionError("out-of-viewport actor entered current scene");
        }
        return 1;
    }

    private static int checkTargetSpeciesMismatchIsNotCurrent()
    {
        Field field = new Field(3, 3);
        // Distinct ids matter here: animals built without an allocator all get id 1.
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Gazelle source = new Gazelle(false, new Location(1, 1), new java.util.Random(1L), identities);
        Zebra target = new Zebra(false, new Location(1, 2), new java.util.Random(2L), identities);
        field.placeAnimal(source, source.getLocation());
        field.placeAnimal(target, target.getLocation());
        SimulationEvent wrongTargetSpecies = new SimulationEvent(
            SimulationEvent.EventType.INFECTION, 4, 1, 2, 1, 1,
            source.getId(), target.getId(), SpeciesRegistry.GAZELLE,
            SpeciesRegistry.GAZELLE);
        List<SceneDirector.Scene> scenes = SceneDirector.buildScene(
            List.of(wrongTargetSpecies), field, null,
            new Rectangle(0, 0, 3, 3));
        if(!scenes.isEmpty()) {
            throw new AssertionError("species-mismatched target entered current scene");
        }
        return 1;
    }

    private static int checkZoomSequence()
    {
        ViewportTransform transform = new ViewportTransform();
        double[] requested = {1.0, 2.49, 2.5, 4.0, 6.0, 1.0};
        for(int index = 1; index < requested.length; index++) {
            transform.zoomBy(requested[index] / requested[index - 1],
                              300, 200, 600, 400, 1200, 800);
            if(index < requested.length - 1 &&
               Math.abs(transform.getZoom() - requested[index]) > 0.01) {
                throw new AssertionError("zoom sequence drifted at " + requested[index]);
            }
        }
        if(!transform.isOverview()) {
            throw new AssertionError("zoom sequence did not return to overview");
        }
        return 1;
    }

    private static int checkViewportRoundTrip()
    {
        int[][] sizes = {{3600, 2400}, {1732, 948}, {1387, 829},
                         {932, 712}, {640, 480}};
        for(int[] size : sizes) {
            VisualGridGeometry geometry = new VisualGridGeometry(240, 360,
                                                                  size[0], size[1]);
            for(int row : new int[] {0, 119, 239}) {
                for(int col : new int[] {0, 179, 359}) {
                    java.awt.Rectangle cell = geometry.cellBounds(row, col);
                    if(cell.x < 0 || cell.y < 0 ||
                       cell.x + cell.width > size[0] ||
                       cell.y + cell.height > size[1]) {
                        throw new AssertionError("viewport cell out of bounds");
                    }
                }
            }
        }
        return 1;
    }
}
