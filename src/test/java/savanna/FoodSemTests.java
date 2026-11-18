package savanna;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** FOOD-SEM contract for the selected REACH_AND_GRAZE semantics. */
public final class FoodSemTests
{
    private static final double EPSILON = 0.000001;

    private FoodSemTests() {}

    public static void main(String[] args)
    {
        TestSupport.startSuite("FOOD-SEM REACH_AND_GRAZE fixtures");
        run();
        TestSupport.finishSuite();
        if(TestSupport.hasFailures()) System.exit(1);
    }

    public static void run()
    {
        TestSupport.startGroup("FOOD-SEM REACH_AND_GRAZE contract");
        testGrazeEffectsRemainAtInteractionCellAfterResolverReroute();
        testReconciliationRejectsCommitCoupledFaults();
        TestSupport.finishGroup();
    }

    private static void testGrazeEffectsRemainAtInteractionCellAfterResolverReroute()
    {
        SimulationConfig config = SimulationConfig.builder().mapScale(1)
            .thirstEnabled(true).randomSeed(1111L).terrainSeed(20260629L)
            .build();
        SimulationContext context = new SimulationContext(24, 24, config);
        FixtureCells cells = findFixtureCells(context);
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Gazelle grazer = new Gazelle(false, cells.grazerOrigin,
            new Random(71L), identities);
        Gazelle waterClaimant = new Gazelle(false, cells.waterOrigin,
            new Random(73L), identities);
        TestSupport.setField(grazer, "foodLevel", Integer.valueOf(10));
        TestSupport.setHydration(waterClaimant, 0.1);

        savanna.Field field = new savanna.Field(24, 24);
        field.placeAnimal(grazer, cells.grazerOrigin);
        field.placeAnimal(waterClaimant, cells.waterOrigin);
        context.startStep(8, field);
        GrasslandFoodSystem grass = (GrasslandFoodSystem)context.getFoodSystem();
        zeroGrass(grass);
        grassGrid(grass)[cells.interaction.row()][cells.interaction.col()] = 1.0;

        PositionStepCoordinator coordinator = new PositionStepCoordinator(
            field, context);
        context.reserveCriticalWaterMove(waterClaimant, cells.interaction);
        List<TransitionObservation> transitions = new ArrayList<>();
        context.addTransitionObservationListener(transitions::add);

        int foodBefore = grazer.getFoodLevel();
        double staminaBefore = grazer.getStaminaPercent();
        double interactionGrassBefore = grass.getFoodLevelAt(cells.interaction);
        coordinator.propose(grazer, coordinator.createAvailabilityView());
        int foodAfterProposal = grazer.getFoodLevel();
        double staminaAfterProposal = grazer.getStaminaPercent();
        double interactionGrassAfter = grass.getFoodLevelAt(cells.interaction);
        SimulationEvent preCommitGraze = grazeEvent(context, grazer.getId());

        TestSupport.assertEquals(
            "FOOD-SEM proposal keeps animal at step-start coordinate",
            cells.grazerOrigin, grazer.getLocation());
        TestSupport.assertEquals(
            "FOOD-SEM proposal has no position plan before resolver", null,
            context.getLastPositionPlan());
        TestSupport.assertEquals(
            "FOOD-SEM proposal emits one graze event before commit", 1,
            grazeEventCount(context, grazer.getId()));
        TestSupport.assertEquals(
            "FOOD-SEM precommit event records interaction cell",
            cells.interaction, eventLocation(preCommitGraze));
        TestSupport.assertTrue(
            "FOOD-SEM interaction grass is debited before resolver",
            interactionGrassAfter < interactionGrassBefore);
        TestSupport.assertTrue(
            "FOOD-SEM animal receives food before resolver",
            foodAfterProposal > foodBefore);
        TestSupport.assertClose(
            "FOOD-SEM graze stamina debit occurs before resolver",
            staminaBefore - SavannahAnimal.STAMINA_GRAZE_COST,
            staminaAfterProposal, EPSILON);

        coordinator.propose(waterClaimant,
            coordinator.createAvailabilityView());
        savanna.Field committed = coordinator.resolveAndCommit();
        PositionResolution resolution = context.getLastPositionPlan()
            .getResolution(PositionActorKey.adult(grazer.getId()));
        TransitionObservation transition = transitionFor(transitions,
            grazer.getId());
        Location finalLocation = grazer.getLocation();
        SimulationEvent committedGraze = grazeEvent(context, grazer.getId());
        BehaviorOutcome outcome = grazer.getLastBehaviorOutcome();

        TestSupport.assertEquals(
            "FOOD-SEM higher-priority claimant wins interaction cell",
            cells.interaction, waterClaimant.getLocation());
        TestSupport.assertFalse(
            "FOOD-SEM grazed animal may finish at another cell",
            cells.interaction.equals(finalLocation));
        TestSupport.assertEquals(
            "FOOD-SEM position plan keeps interaction cell as request",
            cells.interaction, resolution.getRequested());
        TestSupport.assertFalse(
            "FOOD-SEM resolver uses a non-requested destination",
            resolution.getCandidateSource() == PositionCandidateSource.REQUESTED);
        TestSupport.assertEquals(
            "FOOD-SEM transition identifies resolver reroute",
            TransitionStatus.REROUTED, transition.getStatus());
        TestSupport.assertEquals(
            "FOOD-SEM resolver does not duplicate graze event", 1,
            grazeEventCount(context, grazer.getId()));
        TestSupport.assertEquals(
            "FOOD-SEM event remains at interaction cell after reroute",
            cells.interaction, eventLocation(committedGraze));
        TestSupport.assertClose(
            "FOOD-SEM final cell receives no grass debit", 0.0,
            grass.getFoodLevelAt(finalLocation), EPSILON);
        TestSupport.assertTrue(
            "FOOD-SEM completed outcome retains GRAZE effect",
            outcome.getExecutedEffects().contains(ExecutedEffect.GRAZE));
        TestSupport.assertEquals(
            "FOOD-SEM completed outcome retains reroute disposition",
            MovementDisposition.REROUTED, outcome.getMovementDisposition());
        TestSupport.assertTrue(
            "FOOD-SEM committed field remains internally consistent",
            committed.hasConsistentState());

        Ledger ledger = new Ledger(cells.interaction, finalLocation,
            eventLocation(committedGraze), interactionGrassBefore,
            interactionGrassAfter, 0.0, grass.getFoodLevelAt(finalLocation),
            foodBefore, foodAfterProposal, staminaBefore, staminaAfterProposal,
            grazeEventCount(context, grazer.getId()));
        TestSupport.assertTrue(
            "FOOD-SEM interaction/resource/event ledger reconciles",
            reconciles(ledger));
    }

    private static void testReconciliationRejectsCommitCoupledFaults()
    {
        TestSupport.assertFalse(
            "FOOD-SEM rejects event relocated to final cell",
            reconciles(faultLedger("event-at-final")));
        TestSupport.assertFalse(
            "FOOD-SEM rejects missing interaction grass debit",
            reconciles(faultLedger("missing-interaction-debit")));
        TestSupport.assertFalse(
            "FOOD-SEM rejects grass debit moved to final cell",
            reconciles(faultLedger("final-cell-debit")));
        TestSupport.assertFalse(
            "FOOD-SEM rejects missing animal food credit",
            reconciles(faultLedger("missing-food-credit")));
        TestSupport.assertFalse(
            "FOOD-SEM rejects missing graze stamina debit",
            reconciles(faultLedger("missing-stamina-debit")));
        TestSupport.assertFalse(
            "FOOD-SEM rejects duplicate graze events",
            reconciles(faultLedger("duplicate-event")));
    }

    static boolean reconciles(Ledger ledger)
    {
        return ledger != null && ledger.interaction != null &&
            ledger.finalLocation != null && ledger.eventLocation != null &&
            !ledger.interaction.equals(ledger.finalLocation) &&
            ledger.interaction.equals(ledger.eventLocation) &&
            ledger.interactionGrassAfter < ledger.interactionGrassBefore &&
            Math.abs(ledger.finalGrassAfter - ledger.finalGrassBefore) <= EPSILON &&
            ledger.foodAfter > ledger.foodBefore &&
            ledger.staminaAfter < ledger.staminaBefore &&
            ledger.grazeEventCount == 1;
    }

    static Ledger faultLedger(String mode)
    {
        Location interaction = new Location(1, 1);
        Location finalLocation = new Location(1, 2);
        Location eventLocation = interaction;
        double interactionBefore = 1.0;
        double interactionAfter = 0.75;
        double finalBefore = 0.40;
        double finalAfter = 0.40;
        int foodBefore = 10;
        int foodAfter = 18;
        double staminaBefore = 100.0;
        double staminaAfter = 99.65;
        int events = 1;
        if("event-at-final".equals(mode)) eventLocation = finalLocation;
        else if("missing-interaction-debit".equals(mode)) interactionAfter = 1.0;
        else if("final-cell-debit".equals(mode)) finalAfter = 0.20;
        else if("missing-food-credit".equals(mode)) foodAfter = foodBefore;
        else if("missing-stamina-debit".equals(mode)) staminaAfter = staminaBefore;
        else if("duplicate-event".equals(mode)) events = 2;
        else if(!"none".equals(mode)) {
            throw new IllegalArgumentException("Unknown FOOD-SEM fault: " + mode);
        }
        return new Ledger(interaction, finalLocation, eventLocation,
            interactionBefore, interactionAfter, finalBefore, finalAfter,
            foodBefore, foodAfter, staminaBefore, staminaAfter, events);
    }

    private static FixtureCells findFixtureCells(SimulationContext context)
    {
        TerrainMap terrain = context.getTerrainMap();
        ThirstSystem thirst = context.getThirstSystem();
        for(int row = 1; row < terrain.getDepth() - 1; row++) {
            for(int col = 1; col < terrain.getWidth() - 1; col++) {
                Location interaction = new Location(row, col);
                if(!terrain.isPassable(interaction)) continue;
                List<Location> neighbors = passableNeighbors(terrain, interaction);
                for(Location waterOrigin : neighbors) {
                    if(thirst.distanceToShore(waterOrigin) <= 0 ||
                       thirst.distanceToShore(waterOrigin) == Integer.MAX_VALUE) {
                        continue;
                    }
                    for(Location grazerOrigin : neighbors) {
                        if(grazerOrigin.equals(waterOrigin)) continue;
                        int availableFallbacks = 0;
                        for(Location fallback : passableNeighbors(terrain,
                                grazerOrigin)) {
                            if(!fallback.equals(interaction) &&
                               !fallback.equals(waterOrigin)) {
                                availableFallbacks++;
                            }
                        }
                        if(availableFallbacks >= 2) {
                            return new FixtureCells(grazerOrigin, waterOrigin,
                                interaction);
                        }
                    }
                }
            }
        }
        throw new IllegalStateException(
            "FOOD-SEM fixture requires two origins and one fallback");
    }

    private static List<Location> passableNeighbors(TerrainMap terrain,
                                                    Location origin)
    {
        List<Location> result = new ArrayList<>();
        for(int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            for(int colOffset = -1; colOffset <= 1; colOffset++) {
                if(rowOffset == 0 && colOffset == 0) continue;
                Location candidate = new Location(origin.row() + rowOffset,
                    origin.col() + colOffset);
                if(candidate.row() >= 0 && candidate.row() < terrain.getDepth() &&
                   candidate.col() >= 0 && candidate.col() < terrain.getWidth() &&
                   terrain.isPassable(candidate)) {
                    result.add(candidate);
                }
            }
        }
        return result;
    }

    private static void zeroGrass(GrasslandFoodSystem grass)
    {
        double[][] values = grassGrid(grass);
        for(int row = 0; row < values.length; row++) {
            java.util.Arrays.fill(values[row], 0.0);
        }
    }

    private static double[][] grassGrid(GrasslandFoodSystem grass)
    {
        try {
            Field field = GrasslandFoodSystem.class.getDeclaredField("grass");
            field.setAccessible(true);
            return (double[][])field.get(grass);
        }
        catch(ReflectiveOperationException exception) {
            throw new IllegalStateException(
                "Could not access FOOD-SEM grass fixture", exception);
        }
    }

    private static int grazeEventCount(SimulationContext context, long actorId)
    {
        int count = 0;
        for(SimulationEvent event : context.getCurrentEvents()) {
            if(event.type == SimulationEvent.EventType.GRAZE &&
               event.actorId == actorId) count++;
        }
        return count;
    }

    private static SimulationEvent grazeEvent(SimulationContext context,
                                               long actorId)
    {
        for(SimulationEvent event : context.getCurrentEvents()) {
            if(event.type == SimulationEvent.EventType.GRAZE &&
               event.actorId == actorId) return event;
        }
        return null;
    }

    private static Location eventLocation(SimulationEvent event)
    {
        return event == null ? null : new Location(event.row, event.col);
    }

    private static TransitionObservation transitionFor(
        List<TransitionObservation> transitions, long actorId)
    {
        for(TransitionObservation transition : transitions) {
            if(transition.getAnimalId() == actorId) return transition;
        }
        return null;
    }

    static final class Ledger
    {
        private final Location interaction;
        private final Location finalLocation;
        private final Location eventLocation;
        private final double interactionGrassBefore;
        private final double interactionGrassAfter;
        private final double finalGrassBefore;
        private final double finalGrassAfter;
        private final int foodBefore;
        private final int foodAfter;
        private final double staminaBefore;
        private final double staminaAfter;
        private final int grazeEventCount;

        private Ledger(Location interaction, Location finalLocation,
                       Location eventLocation, double interactionGrassBefore,
                       double interactionGrassAfter, double finalGrassBefore,
                       double finalGrassAfter, int foodBefore, int foodAfter,
                       double staminaBefore, double staminaAfter,
                       int grazeEventCount)
        {
            this.interaction = interaction;
            this.finalLocation = finalLocation;
            this.eventLocation = eventLocation;
            this.interactionGrassBefore = interactionGrassBefore;
            this.interactionGrassAfter = interactionGrassAfter;
            this.finalGrassBefore = finalGrassBefore;
            this.finalGrassAfter = finalGrassAfter;
            this.foodBefore = foodBefore;
            this.foodAfter = foodAfter;
            this.staminaBefore = staminaBefore;
            this.staminaAfter = staminaAfter;
            this.grazeEventCount = grazeEventCount;
        }
    }

    private static final class FixtureCells
    {
        private final Location grazerOrigin;
        private final Location waterOrigin;
        private final Location interaction;

        private FixtureCells(Location grazerOrigin, Location waterOrigin,
                             Location interaction)
        {
            this.grazerOrigin = grazerOrigin;
            this.waterOrigin = waterOrigin;
            this.interaction = interaction;
        }
    }
}
