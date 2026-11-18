package savanna;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Deterministic B3 red/green gate for the read-only display fact sidecar. */
public final class DisplayFactAuditProbe
{
    private DisplayFactAuditProbe()
    {
    }

    public static void run()
    {
        testDefaultFactsHaveNoDifference();
        testMissingIdIsRejected();
        testSpeciesMismatchIsRejected();
        testDeadActorIsRejected();
        testOutsideViewportActorIsRejected();
        testPositionMismatchIsRejected();
        testSnapshotIsImmutable();
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("B3 display fact audit probe");
        TestSupport.startGroup("Display fact audit");
        run();
        TestSupport.finishGroup();
        TestSupport.finishSuite();
        if(TestSupport.hasFailures()) {
            System.exit(1);
        }
    }

    private static void testDefaultFactsHaveNoDifference()
    {
        Simulator simulator = new Simulator(false);
        Field field = simulator.getField();
        Rectangle viewport = new Rectangle(0, 0, field.getWidth(), field.getDepth());
        List<DisplayFactAudit.AnimalFact> fieldFacts =
            DisplayFactAudit.captureFieldFacts(field, viewport);
        List<SceneDirector.SceneActor> actors = SceneDirector.buildBehaviorActors(
            Collections.<SimulationEvent>emptyList(), field,
            simulator.getContext().getTerrainMap(), viewport);
        DisplayFactAudit audit = new DisplayFactAudit(0, 1L, viewport,
            fieldFacts, new ArrayList<>(fieldFacts),
            DisplayFactAudit.captureInspectActorInputs(field, actors));

        TestSupport.assertTrue("default ordinary audit has zero differences",
                               audit.compareOrdinaryMarkerInputs().isConsistent());
        TestSupport.assertTrue("default inspect audit has zero differences",
                               audit.compareInspectActorInputs().isConsistent());
    }

    private static void testMissingIdIsRejected()
    {
        DisplayFactAudit audit = auditWith(singleFact(), Collections.emptyList(),
                                            Collections.emptyList());
        assertDifference("ordinary missing ID is rejected",
                         audit.compareOrdinaryMarkerInputs(),
                         DisplayFactAudit.DifferenceType.MISSING_ID);
    }

    private static void testSpeciesMismatchIsRejected()
    {
        DisplayFactAudit.AnimalFact fact = singleFact();
        DisplayFactAudit.AnimalFact wrongSpecies = new DisplayFactAudit.AnimalFact(
            fact.animalId, SpeciesRegistry.ZEBRA, fact.row, fact.col, true);
        DisplayFactAudit audit = auditWith(fact, List.of(wrongSpecies),
                                            Collections.emptyList());
        assertDifference("ordinary species mismatch is rejected",
                         audit.compareOrdinaryMarkerInputs(),
                         DisplayFactAudit.DifferenceType.SPECIES_MISMATCH);
    }

    private static void testDeadActorIsRejected()
    {
        DisplayFactAudit.AnimalFact fact = singleFact();
        DisplayFactAudit.AnimalFact dead = new DisplayFactAudit.AnimalFact(
            fact.animalId, fact.species, fact.row, fact.col, false);
        DisplayFactAudit audit = auditWith(fact, List.of(fact), List.of(dead));
        assertDifference("dead inspect actor is rejected",
                         audit.compareInspectActorInputs(),
                         DisplayFactAudit.DifferenceType.DEAD_ACTOR);
    }

    private static void testOutsideViewportActorIsRejected()
    {
        DisplayFactAudit.AnimalFact fact = singleFact();
        DisplayFactAudit.AnimalFact outside = new DisplayFactAudit.AnimalFact(
            fact.animalId, fact.species, 9, 9, true);
        DisplayFactAudit audit = auditWith(fact, List.of(fact), List.of(outside));
        assertDifference("outside inspect actor is rejected",
                         audit.compareInspectActorInputs(),
                         DisplayFactAudit.DifferenceType.VIEWPORT_OUTSIDE);
    }

    private static void testPositionMismatchIsRejected()
    {
        DisplayFactAudit.AnimalFact fact = singleFact();
        DisplayFactAudit.AnimalFact wrongPosition = new DisplayFactAudit.AnimalFact(
            fact.animalId, fact.species, 1, 2, true);
        DisplayFactAudit audit = auditWith(fact, List.of(wrongPosition),
                                            Collections.emptyList());
        assertDifference("ordinary position mismatch is rejected",
                         audit.compareOrdinaryMarkerInputs(),
                         DisplayFactAudit.DifferenceType.POSITION_MISMATCH);
    }

    private static void testSnapshotIsImmutable()
    {
        DisplayFactAudit.AnimalFact fact = singleFact();
        List<DisplayFactAudit.AnimalFact> mutable = new ArrayList<>();
        mutable.add(fact);
        DisplayFactAudit audit = new DisplayFactAudit(7, 4L,
            new Rectangle(0, 0, 3, 3), mutable, mutable,
            Collections.emptyList());
        mutable.clear();
        TestSupport.assertEquals("audit keeps copied field facts", 1,
                                 audit.getFieldFacts().size());
        boolean immutable = false;
        try {
            audit.getOrdinaryMarkerInputs().add(fact);
        }
        catch(UnsupportedOperationException expected) {
            immutable = true;
        }
        TestSupport.assertTrue("audit input lists are immutable", immutable);
    }

    private static DisplayFactAudit auditWith(DisplayFactAudit.AnimalFact field,
                                               List<DisplayFactAudit.AnimalFact> ordinary,
                                               List<DisplayFactAudit.AnimalFact> inspect)
    {
        return new DisplayFactAudit(7, 4L, new Rectangle(0, 0, 3, 3),
            List.of(field), ordinary, inspect);
    }

    private static DisplayFactAudit.AnimalFact singleFact()
    {
        return new DisplayFactAudit.AnimalFact(42L, SpeciesRegistry.GAZELLE,
                                               1, 1, true);
    }

    private static void assertDifference(String name,
                                         DisplayFactAudit.Comparison comparison,
                                         DisplayFactAudit.DifferenceType expected)
    {
        boolean found = false;
        for(DisplayFactAudit.Difference difference : comparison.getDifferences()) {
            if(difference.type == expected) {
                found = true;
                break;
            }
        }
        TestSupport.assertTrue(name, found);
    }
}
