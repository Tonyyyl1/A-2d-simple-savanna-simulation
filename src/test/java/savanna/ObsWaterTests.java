package savanna;

import java.util.List;

/** OBS-WATER contracts for pure prepass decisions and single-owner events. */
public final class ObsWaterTests
{
    private ObsWaterTests() {}

    public static void main(String[] args)
    {
        TestSupport.startSuite("OBS-WATER fixtures");
        run();
        TestSupport.finishSuite();
        if(TestSupport.hasFailures()) System.exit(1);
    }

    public static void run()
    {
        TestSupport.startGroup("OBS-WATER target and event causality");
        testCriticalPrepassDoesNotCommitTarget();
        testTrueRetargetAndDeficitAreEachRecordedOnce();
        P1CriticalWaterReservationTests.run();
        TestSupport.finishGroup();
    }

    private static void testCriticalPrepassDoesNotCommitTarget()
    {
        Simulator simulator = new Simulator(SimulationConfig.builder()
            .mapScale(1).thirstEnabled(true).randomSeed(1111L)
            .terrainSeed(20260629L).build(), false);
        simulator.setVerbose(false);
        SimulationContext context = simulator.getContext();
        SavannahAnimal animal = nonShoreAnimal(simulator);
        TestSupport.setHydration(animal, 0.1);
        PositionStepCoordinator coordinator = new PositionStepCoordinator(
            simulator.getField(), context);
        PositionAvailabilityView availability =
            coordinator.createAvailabilityView();

        TestSupport.invokePrivate(simulator,
            "prepareCriticalWaterReservations",
            new Class<?>[] { List.class, PositionAvailabilityView.class },
            new Object[] { java.util.List.of(animal), availability });

        TestSupport.assertEquals(
            "OBS-WATER prepass leaves target ownership to behavior phase",
            WaterSourceIndex.NONE, animal.getWaterTargetPoolId());
        TestSupport.assertFalse(
            "OBS-WATER prepass leaves deficit state uncommitted",
            animal.hasWaterArrivalDeficit());
        TestSupport.assertEquals("OBS-WATER prepass emits no causal event", 0,
            waterEventCount(context));

        List<Location> candidates = TerrainNavigator.freeAdjacent(
            simulator.getField(), context.getTerrainMap(), animal.getLocation(),
            context.getRandom());
        BehaviorIntent intent = context.getThirstBehaviorProvider().propose(
            animal, candidates, context);

        TestSupport.assertTrue("OBS-WATER behavior produces water intent",
                               intent != null);
        TestSupport.assertFalse("OBS-WATER behavior commits a real pool target",
            WaterSourceIndex.NONE.equals(animal.getWaterTargetPoolId()));
        TestSupport.assertTrue("OBS-WATER behavior commits arrival deficit",
                               animal.hasWaterArrivalDeficit());
        TestSupport.assertEquals(
            "OBS-WATER NONE to deficit target records exactly one event", 1,
            eventCount(context,
                SimulationEvent.EventType.WATER_ARRIVAL_DEFICIT));

        context.getThirstBehaviorProvider().propose(animal, candidates, context);
        TestSupport.assertEquals(
            "OBS-WATER same target does not repeat arrival deficit", 1,
            eventCount(context,
                SimulationEvent.EventType.WATER_ARRIVAL_DEFICIT));
        TestSupport.assertEquals(
            "OBS-WATER same target does not invent retarget", 0,
            eventCount(context, SimulationEvent.EventType.WATER_RETARGET));
    }

    private static void testTrueRetargetAndDeficitAreEachRecordedOnce()
    {
        SimulationConfig config = SimulationConfig.builder().mapScale(1)
            .thirstEnabled(true).randomSeed(1111L).terrainSeed(20260629L)
            .build();
        SimulationContext context = new SimulationContext(80, 120, config);
        Location location = firstNonShore(context);
        Gazelle animal = new Gazelle(false, location);
        Field field = new Field(80, 120);
        field.placeAnimal(animal, location);
        context.startStep(1, field);
        TestSupport.setHydration(animal, 0.1);
        animal.setWaterTarget("UNREACHABLE-POOL", false);
        List<Location> candidates = TerrainNavigator.freeAdjacent(field,
            context.getTerrainMap(), location, context.getRandom());

        context.getThirstBehaviorProvider().propose(animal, candidates, context);

        TestSupport.assertEquals("OBS-WATER true retarget records once", 1,
            eventCount(context, SimulationEvent.EventType.WATER_RETARGET));
        TestSupport.assertEquals(
            "OBS-WATER retarget with deficit records one deficit", 1,
            eventCount(context,
                SimulationEvent.EventType.WATER_ARRIVAL_DEFICIT));
        context.getThirstBehaviorProvider().propose(animal, candidates, context);
        TestSupport.assertEquals("OBS-WATER retained target keeps retarget once", 1,
            eventCount(context, SimulationEvent.EventType.WATER_RETARGET));
        TestSupport.assertEquals(
            "OBS-WATER retained deficit target keeps deficit once", 1,
            eventCount(context,
                SimulationEvent.EventType.WATER_ARRIVAL_DEFICIT));
    }

    private static SavannahAnimal nonShoreAnimal(Simulator simulator)
    {
        ThirstSystem thirst = simulator.getContext().getThirstSystem();
        for(Animal actor : simulator.getField().getAnimals()) {
            if(actor instanceof SavannahAnimal && actor.isAlive() &&
               thirst.distanceToShore(actor.getLocation()) > 0 &&
               thirst.distanceToShore(actor.getLocation()) < Integer.MAX_VALUE) {
                return (SavannahAnimal)actor;
            }
        }
        throw new IllegalStateException("fixture has no non-shore animal");
    }

    private static Location firstNonShore(SimulationContext context)
    {
        ThirstSystem thirst = context.getThirstSystem();
        TerrainMap terrain = context.getTerrainMap();
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location location = new Location(row, col);
                int distance = thirst.distanceToShore(location);
                if(terrain.isPassable(location) && distance > 0 &&
                   distance < Integer.MAX_VALUE) return location;
            }
        }
        throw new IllegalStateException("fixture has no non-shore location");
    }

    private static int waterEventCount(SimulationContext context)
    {
        return eventCount(context,
                   SimulationEvent.EventType.WATER_ARRIVAL_DEFICIT) +
               eventCount(context, SimulationEvent.EventType.WATER_RETARGET);
    }

    private static int eventCount(SimulationContext context,
                                  SimulationEvent.EventType type)
    {
        int count = 0;
        for(SimulationEvent event : context.getCurrentEvents()) {
            if(event.type == type) count++;
        }
        return count;
    }
}
