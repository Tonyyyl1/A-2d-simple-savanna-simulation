package savanna;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * FOOD-SEM contract for EAT_IN_PLACE (plan v3.0 M1-1, scenario E04): a
 * herbivore eats only in its step-start cell; the patch it chooses is a move
 * request whose grass stays untouched, including when the claim is lost.
 */
public final class FoodSemTests
{
    private static final double EPSILON = 0.000001;
    private static final double ORIGIN_GRASS = 0.60;

    private FoodSemTests() {}

    public static void main(String[] args)
    {
        TestSupport.startSuite("FOOD-SEM EAT_IN_PLACE fixtures");
        run();
        TestSupport.finishSuite();
        if(TestSupport.hasFailures()) System.exit(1);
    }

    public static void run()
    {
        TestSupport.startGroup("FOOD-SEM EAT_IN_PLACE contract");
        testLostClaimConsumesOnlyStepStartCell();
        testReconciliationRejectsMisplacedConsumption();
        TestSupport.finishGroup();
    }

    private static void testLostClaimConsumesOnlyStepStartCell()
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
        grassGrid(grass)[cells.grazerOrigin.row()][cells.grazerOrigin.col()] =
            ORIGIN_GRASS;

        PositionStepCoordinator coordinator = new PositionStepCoordinator(
            field, context);
        context.reserveCriticalWaterMove(waterClaimant, cells.interaction);
        List<TransitionObservation> transitions = new ArrayList<>();
        context.addTransitionObservationListener(transitions::add);

        int foodBefore = grazer.getFoodLevel();
        double staminaBefore = grazer.getStaminaPercent();
        double consumedBefore = grass.getConsumedBiomass();
        coordinator.propose(grazer, coordinator.createAvailabilityView());
        int foodAfterProposal = grazer.getFoodLevel();
        double staminaAfterProposal = grazer.getStaminaPercent();
        double originGrassAfter = grass.getFoodLevelAt(cells.grazerOrigin);
        SimulationEvent preCommitGraze = grazeEvent(context, grazer.getId());

        TestSupport.assertEquals(
            "FOOD-SEM proposal keeps animal at step-start coordinate",
            cells.grazerOrigin, grazer.getLocation());
        TestSupport.assertEquals(
            "FOOD-SEM proposal has no position plan before resolver", null,
            context.getLastPositionPlan());
        TestSupport.assertEquals(
            "FOOD-SEM proposal emits one graze event", 1,
            grazeEventCount(context, grazer.getId()));
        TestSupport.assertEquals(
            "FOOD-SEM graze event records the step-start cell",
            cells.grazerOrigin, eventLocation(preCommitGraze));
        TestSupport.assertTrue(
            "FOOD-SEM step-start cell grass is debited",
            originGrassAfter < ORIGIN_GRASS);
        TestSupport.assertClose(
            "FOOD-SEM requested patch is untouched before resolver", 1.0,
            grass.getFoodLevelAt(cells.interaction), EPSILON);
        TestSupport.assertTrue(
            "FOOD-SEM animal receives food from its own cell",
            foodAfterProposal > foodBefore);
        TestSupport.assertClose(
            "FOOD-SEM graze stamina debit occurs once",
            staminaBefore - SavannahAnimal.STAMINA_GRAZE_COST,
            staminaAfterProposal, EPSILON);
        TestSupport.assertClose(
            "FOOD-SEM biomass ledger records exactly the eaten amount",
            ORIGIN_GRASS - originGrassAfter,
            grass.getConsumedBiomass() - consumedBefore, EPSILON);

        coordinator.propose(waterClaimant,
            coordinator.createAvailabilityView());
        savanna.Field committed = coordinator.resolveAndCommit();
        PositionResolution resolution = context.getLastPositionPlan()
            .getResolution(PositionActorKey.adult(grazer.getId()));
        TransitionObservation transition = transitionFor(transitions,
            grazer.getId());
        Location finalLocation = grazer.getLocation();
        BehaviorOutcome outcome = grazer.getLastBehaviorOutcome();

        TestSupport.assertEquals(
            "FOOD-SEM higher-priority claimant wins the requested patch",
            cells.interaction, waterClaimant.getLocation());
        TestSupport.assertEquals(
            "FOOD-SEM position plan keeps the patch as request",
            cells.interaction, resolution.getRequested());
        TestSupport.assertEquals(
            "FOOD-SEM transition identifies resolver reroute",
            TransitionStatus.REROUTED, transition.getStatus());
        TestSupport.assertClose(
            "FOOD-SEM E04 lost claim never consumes the requested patch", 1.0,
            grass.getFoodLevelAt(cells.interaction), EPSILON);
        TestSupport.assertClose(
            "FOOD-SEM final cell receives no grass debit", 0.0,
            grass.getFoodLevelAt(finalLocation), EPSILON);
        TestSupport.assertEquals(
            "FOOD-SEM resolver does not duplicate graze event", 1,
            grazeEventCount(context, grazer.getId()));
        TestSupport.assertTrue(
            "FOOD-SEM completed outcome retains GRAZE effect",
            outcome.getExecutedEffects().contains(ExecutedEffect.GRAZE));
        TestSupport.assertTrue(
            "FOOD-SEM committed field remains internally consistent",
            committed.hasConsistentState());

        Ledger ledger = new Ledger(cells.grazerOrigin, cells.interaction,
            finalLocation, eventLocation(grazeEvent(context, grazer.getId())),
            ORIGIN_GRASS, originGrassAfter, 1.0,
            grass.getFoodLevelAt(cells.interaction), 0.0,
            grass.getFoodLevelAt(finalLocation), foodBefore, foodAfterProposal,
            staminaBefore, staminaAfterProposal,
            grazeEventCount(context, grazer.getId()));
        TestSupport.assertTrue(
            "FOOD-SEM origin/request/final ledger reconciles",
            reconciles(ledger));
    }

    private static void testReconciliationRejectsMisplacedConsumption()
    {
        TestSupport.assertTrue("FOOD-SEM fault-free ledger reconciles",
            reconciles(faultLedger("none")));
        for(String mode : new String[] {"event-at-request", "missing-origin-debit",
                "request-debit", "final-cell-debit", "missing-food-credit",
                "missing-stamina-debit", "duplicate-event"}) {
            TestSupport.assertFalse("FOOD-SEM rejects " + mode,
                reconciles(faultLedger(mode)));
        }
    }

    static boolean reconciles(Ledger ledger)
    {
        return ledger != null && ledger.origin != null &&
            ledger.requested != null && ledger.finalLocation != null &&
            !ledger.requested.equals(ledger.finalLocation) &&
            ledger.origin.equals(ledger.eventLocation) &&
            ledger.originAfter < ledger.originBefore &&
            Math.abs(ledger.requestedAfter - ledger.requestedBefore) <= EPSILON &&
            Math.abs(ledger.finalAfter - ledger.finalBefore) <= EPSILON &&
            ledger.foodAfter > ledger.foodBefore &&
            ledger.staminaAfter < ledger.staminaBefore &&
            ledger.grazeEventCount == 1;
    }

    static Ledger faultLedger(String mode)
    {
        Location origin = new Location(1, 0);
        Location requested = new Location(1, 1);
        Location finalLocation = new Location(1, 2);
        Location eventLocation = origin;
        double originAfter = 0.45;
        double requestedAfter = 1.0;
        double finalAfter = 0.40;
        int foodAfter = 18;
        double staminaAfter = 99.65;
        int events = 1;
        if("event-at-request".equals(mode)) eventLocation = requested;
        else if("missing-origin-debit".equals(mode)) originAfter = 0.60;
        else if("request-debit".equals(mode)) requestedAfter = 0.75;
        else if("final-cell-debit".equals(mode)) finalAfter = 0.20;
        else if("missing-food-credit".equals(mode)) foodAfter = 10;
        else if("missing-stamina-debit".equals(mode)) staminaAfter = 100.0;
        else if("duplicate-event".equals(mode)) events = 2;
        else if(!"none".equals(mode)) {
            throw new IllegalArgumentException("Unknown FOOD-SEM fault: " + mode);
        }
        return new Ledger(origin, requested, finalLocation, eventLocation,
            0.60, originAfter, 1.0, requestedAfter, 0.40, finalAfter,
            10, foodAfter, 100.0, staminaAfter, events);
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
        private final Location origin;
        private final Location requested;
        private final Location finalLocation;
        private final Location eventLocation;
        private final double originBefore;
        private final double originAfter;
        private final double requestedBefore;
        private final double requestedAfter;
        private final double finalBefore;
        private final double finalAfter;
        private final int foodBefore;
        private final int foodAfter;
        private final double staminaBefore;
        private final double staminaAfter;
        private final int grazeEventCount;

        private Ledger(Location origin, Location requested,
                       Location finalLocation, Location eventLocation,
                       double originBefore, double originAfter,
                       double requestedBefore, double requestedAfter,
                       double finalBefore, double finalAfter, int foodBefore,
                       int foodAfter, double staminaBefore,
                       double staminaAfter, int grazeEventCount)
        {
            this.origin = origin;
            this.requested = requested;
            this.finalLocation = finalLocation;
            this.eventLocation = eventLocation;
            this.originBefore = originBefore;
            this.originAfter = originAfter;
            this.requestedBefore = requestedBefore;
            this.requestedAfter = requestedAfter;
            this.finalBefore = finalBefore;
            this.finalAfter = finalAfter;
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
