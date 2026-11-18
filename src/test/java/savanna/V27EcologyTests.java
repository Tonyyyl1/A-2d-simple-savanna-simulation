package savanna;

import java.util.Collections;
import java.util.Random;

/** Focused production-entry contracts for the currently authorized R2 slice. */
public final class V27EcologyTests
{
    private V27EcologyTests()
    {
    }

    public static void run()
    {
        TestSupport.startGroup("V2.7 R2 ecology target state");
        foodIntentBeginsAndRetainsTarget();
        targetTimeoutIsExplicitBeforeReplacement();
        targetDeathInvalidatesExistingHunterTarget();
        distantPreyCannotProduceKillOrFood();
        competingPredatorsReceiveOneKillAndOneCredit();
        TestSupport.finishGroup("V27_ECOLOGY");
    }

    private static void competingPredatorsReceiveOneKillAndOneCredit()
    {
        SimulationContext context = new SimulationContext(12, 12);
        Location[] trio = adjacentPassableTrio(context.getTerrainMap());
        TestSupport.assertTrue("R2 E02 fixture has three adjacent passable cells",
            trio != null);
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Cheetah first = new Cheetah(false, trio[0], new Random(7L), identities);
        Lion second = new Lion(false, trio[1], new Random(8L), identities);
        Gazelle prey = new Gazelle(false, trio[2], new Random(9L), identities);
        TestSupport.setField(first, "age", first.getProfile().getAdultAge());
        TestSupport.setField(second, "age", second.getProfile().getAdultAge());
        TestSupport.setField(first, "foodLevel", 0);
        TestSupport.setField(second, "foodLevel", 0);
        Field field = new Field(12, 12);
        field.placeAnimal(first, trio[0]);
        field.placeAnimal(second, trio[1]);
        field.placeAnimal(prey, trio[2]);
        context.startStep(6, field);
        InteractionGeometry geometry = StepStartInteractionGeometry.capture(field);
        HuntOpportunityFact firstFact = HuntOpportunityFact.capture(6, first,
            StepFrameLedger.frameId(6, first.getId()), geometry, context);
        HuntOpportunityFact secondFact = HuntOpportunityFact.capture(6, second,
            StepFrameLedger.frameId(6, second.getId()), geometry, context);
        FoodChainPredationSystem system = new FoodChainPredationSystem(
            new AlwaysZeroRandom());
        int firstFoodBefore = first.getFoodLevel();
        HuntResult firstResult = system.huntWithOutcome(first, geometry, context,
            firstFact);
        HuntResult secondResult = system.huntWithOutcome(second, geometry, context,
            secondFact);
        TestSupport.assertEquals("R2 E02 first competing predator wins one kill",
            HuntOutcome.SUCCESS, firstResult.getOutcome());
        TestSupport.assertEquals("R2 E02 second competing predator gets no duplicate kill",
            HuntOutcome.NO_LOCAL_REACHABLE_PREY, secondResult.getOutcome());
        TestSupport.assertFalse("R2 E02 contested prey is terminal once", prey.isAlive());
        TestSupport.assertTrue("R2 E02 winner receives one food credit",
            first.getFoodLevel() > firstFoodBefore);
        TestSupport.assertEquals("R2 E02 loser receives no food credit", 0,
            second.getFoodLevel());
        TestSupport.assertEquals("R2 E02 contested prey creates one predation death", 1,
            context.getDeathCount(DeathCause.PREDATION));
    }

    private static Location[] adjacentPassableTrio(TerrainMap terrain)
    {
        for(int row = 0; row < terrain.getDepth() - 1; row++) {
            for(int col = 0; col < terrain.getWidth() - 1; col++) {
                Location first = new Location(row, col);
                Location second = new Location(row + 1, col);
                Location third = new Location(row, col + 1);
                if(terrain.isPassable(first) && terrain.isPassable(second) &&
                   terrain.isPassable(third)) return new Location[] {first, second, third};
            }
        }
        return null;
    }

    private static final class AlwaysZeroRandom extends Random
    {
        @Override public double nextDouble() { return 0.0; }
        @Override public int nextInt(int bound) { return 0; }
    }

    private static void distantPreyCannotProduceKillOrFood()
    {
        SimulationContext context = new SimulationContext(12, 12);
        TerrainMap terrain = context.getTerrainMap();
        Location hunterLocation = null;
        Location preyLocation = null;
        outer: for(int row = 0; row < 12; row++) for(int col = 0; col < 12; col++) {
            Location first = new Location(row, col);
            if(!terrain.isPassable(first)) continue;
            for(int preyRow = 0; preyRow < 12; preyRow++) for(int preyCol = 0;
                preyCol < 12; preyCol++) {
                Location second = new Location(preyRow, preyCol);
                if(terrain.isPassable(second) && Math.max(Math.abs(row - preyRow),
                    Math.abs(col - preyCol)) == 5 && TerrainNavigator
                    .reachableDistances(terrain, first, 8).distanceTo(second) >= 0) {
                    hunterLocation = first;
                    preyLocation = second;
                    break outer;
                }
            }
        }
        TestSupport.assertTrue("R2 E02 fixture has reachable prey beyond strike reach",
            hunterLocation != null && preyLocation != null);
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Cheetah hunter = new Cheetah(false, hunterLocation, new Random(5L), identities);
        Gazelle prey = new Gazelle(false, preyLocation, new Random(6L), identities);
        TestSupport.setField(hunter, "age", hunter.getProfile().getAdultAge());
        TestSupport.setField(hunter, "foodLevel", 0);
        Field field = new Field(12, 12);
        field.placeAnimal(hunter, hunterLocation);
        field.placeAnimal(prey, preyLocation);
        context.startStep(3, field);
        int foodBefore = hunter.getFoodLevel();
        HuntResult result = new FoodChainPredationSystem(new Random(0L)).huntWithOutcome(
            hunter, StepStartInteractionGeometry.capture(field), context);
        TestSupport.assertEquals("R2 E02 distant prey produces no kill outcome",
            HuntOutcome.OUT_OF_CONTACT, result.getOutcome());
        TestSupport.assertTrue("R2 E02 distant prey remains alive", prey.isAlive());
        TestSupport.assertEquals("R2 E02 distant prey gives no food", foodBefore,
            hunter.getFoodLevel());
        TestSupport.assertEquals("R2 E02 distant prey becomes persistent target",
            prey.getId(), hunter.getProductionTarget().getTargetAnimalId());
    }

    private static void targetTimeoutIsExplicitBeforeReplacement()
    {
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Field field = new Field(6, 6);
        Gazelle gazelle = new Gazelle(false, new Location(2, 2), new Random(4L),
            identities);
        field.placeAnimal(gazelle, gazelle.getLocation());
        SimulationContext context = new SimulationContext(6, 6);
        FoodBehaviorProvider provider = new FoodBehaviorProvider();
        context.startStep(1, field);
        provider.propose(gazelle, Collections.<Location>emptyList(), context);
        context.startStep(9, field);
        provider.propose(gazelle, Collections.<Location>emptyList(), context);
        TestSupport.assertEquals("R2 E01 timed-out target records declared reason",
            ProductionTargetState.Status.TIMED_OUT,
            gazelle.getProductionTarget().getLastInvalidation());
        TestSupport.assertEquals("R2 E01 timed-out target replacement records new step",
            9, gazelle.getProductionTarget().getEstablishedStep());
    }

    private static void foodIntentBeginsAndRetainsTarget()
    {
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Field field = new Field(6, 6);
        Gazelle gazelle = new Gazelle(false, new Location(2, 2), new Random(1L),
            identities);
        field.placeAnimal(gazelle, gazelle.getLocation());
        SimulationContext context = new SimulationContext(6, 6);
        FoodBehaviorProvider provider = new FoodBehaviorProvider();
        context.startStep(4, field);
        provider.propose(gazelle, Collections.<Location>emptyList(), context);
        ProductionTargetState target = gazelle.getProductionTarget();
        TestSupport.assertEquals("R2 E01 grazer starts region target",
            ProductionTargetState.Type.GRAZE_REGION, target.getType());
        TestSupport.assertEquals("R2 E01 target records established step", 4,
            target.getEstablishedStep());
        context.startStep(5, field);
        provider.propose(gazelle, Collections.<Location>emptyList(), context);
        TestSupport.assertEquals("R2 E01 active target is not randomly recreated", 4,
            target.getEstablishedStep());
    }

    private static void targetDeathInvalidatesExistingHunterTarget()
    {
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Field field = new Field(6, 6);
        Lion hunter = new Lion(false, new Location(1, 1), new Random(2L), identities);
        Gazelle prey = new Gazelle(false, new Location(1, 2), new Random(3L), identities);
        field.placeAnimal(hunter, hunter.getLocation());
        field.placeAnimal(prey, prey.getLocation());
        SimulationContext context = new SimulationContext(6, 6);
        context.startStep(8, field);
        hunter.ensureProductionTarget(ProductionTargetState.Type.HUNT_REGION,
            hunter.getLocation(), 8, 8);
        hunter.acquireHuntTarget(prey, prey.getLocation(), 8);
        TestSupport.assertTrue("R2 E01 target initially owns prey id",
            hunter.getProductionTarget().targets(prey.getId()));
        TestSupport.assertTrue("R2 E01 unified death commits", context.recordDeath(prey,
            DeathCause.PREDATION));
        TestSupport.assertEquals("R2 E01 target death invalidates hunter target",
            ProductionTargetState.Status.TARGET_DEAD,
            hunter.getProductionTarget().getStatus());
        TestSupport.assertEquals("R2 E01 target death remains auditable after replacement",
            ProductionTargetState.Status.TARGET_DEAD,
            hunter.getProductionTarget().getLastInvalidation());
    }
}
