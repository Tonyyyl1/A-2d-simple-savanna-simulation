package savanna;

import java.util.ArrayList;
import java.util.List;

/**
 * Focused tests for small simulation rules.
 */
public class SimulationUnitTests
{
    public static void run()
    {
        TestSupport.startGroup("Unit tests");
        Randomizer.reset();
        testSimulationSettings();
        Cfg0RuntimeConfigTests.run();
        TestSupport.finishGroup();

        PositionKernelTests.run();
        A1aR2PositionBoardTests.run();

        TestSupport.startGroup("Unit tests continued");
        DisplayContractProbe.run();
        OverviewFrameAtomicityProbe.run();
        testClock();
        testSurvivalPressure();
        testSurvivalAssessment();
        testHydrationStages();
        testBehaviorIntentPipeline();
        testBehaviorOutcome();
        testFieldTransitionPipeline();
        testO0aTransitionObservation();
        testO0aWaterSourceIndex();
        testO0aSimulationFingerprint();
        testO0b1FrameEventSummary();
        HeatmapSnapshotProbe.run();
        HeatmapWindowProbe.run();
        testStaminaStagesAndBurst();
        testStarvationNeedsThreeSteps();
        testDeathCauseClassification();
        testFeedingClearsStarvation();
        testReportInterval();
        TestSupport.finishGroup();
    }

    private static void testSimulationSettings()
    {
        TestSupport.assertEquals("visual simulation refresh interval is 100",
                                 100, SimulationSettings.VISUAL_REFRESH_INTERVAL);
        TestSupport.assertEquals("record interval is 100",
                                 100, SimulationSettings.RECORD_INTERVAL);
        TestSupport.assertEquals("chart update interval is 100",
                                 100, SimulationSettings.CHART_UPDATE_INTERVAL);
    }

    private static void testClock()
    {
        SimulationClock clock = new SimulationClock();
        clock.setStep(0);
        TestSupport.assertEquals("step 0 is hour 0", 0, clock.getHour());
        TestSupport.assertEquals("hour 0 is night", DayPhase.NIGHT, clock.getPhase());
        clock.setStep(5);
        TestSupport.assertEquals("hour 5 is dawn", DayPhase.DAWN, clock.getPhase());
        clock.setStep(8);
        TestSupport.assertEquals("hour 8 is day", DayPhase.DAY, clock.getPhase());
        clock.setStep(18);
        TestSupport.assertEquals("hour 18 is dusk", DayPhase.DUSK, clock.getPhase());
        clock.setStep(24);
        TestSupport.assertEquals("24 steps wraps to hour 0", 0, clock.getHour());
    }

    private static void testSurvivalPressure()
    {
        Gazelle gazelle = new Gazelle(false, new Location(1, 1));
        int maxFood = gazelle.getProfile().getMaxFoodLevel();

        TestSupport.setField(gazelle, "foodLevel", maxFood * 2);
        TestSupport.assertClose("survival percent is capped at 100",
                                100.0, gazelle.getSurvivalPercent(), 0.01);
        TestSupport.assertClose("normal survival has no pressure",
                                0.0, gazelle.getSurvivalPressure(), 0.01);

        TestSupport.setField(gazelle, "foodLevel", maxFood / 2);
        TestSupport.assertRange("mid survival is between 30 and 70",
                                gazelle.getSurvivalPercent(), 30.0, 70.0);
        TestSupport.assertClose("50 percent survival has 0.5 pressure",
                                0.5, gazelle.getSurvivalPressure(), 0.12);

        TestSupport.setField(gazelle, "foodLevel", -10);
        TestSupport.assertClose("survival percent is capped at 0",
                                0.0, gazelle.getSurvivalPercent(), 0.01);
        TestSupport.assertClose("critical survival has full pressure",
                                1.0, gazelle.getSurvivalPressure(), 0.01);
        TestSupport.assertTrue("critical survival is detected",
                               gazelle.isSurvivalCritical());
    }

    private static void testStaminaStagesAndBurst()
    {
        Gazelle gazelle = new Gazelle(false, new Location(1, 1));
        TestSupport.setField(gazelle, "stamina", 80.0);
        TestSupport.assertEquals("80 stamina is high", StaminaStage.HIGH,
                                 gazelle.getStaminaStage());
        TestSupport.setField(gazelle, "stamina", 50.0);
        TestSupport.assertEquals("50 stamina is medium", StaminaStage.MEDIUM,
                                 gazelle.getStaminaStage());
        TestSupport.setField(gazelle, "stamina", 10.0);
        TestSupport.assertEquals("10 stamina is low", StaminaStage.LOW,
                                 gazelle.getStaminaStage());

        TestSupport.setField(gazelle, "stamina", 50.0);
        TestSupport.setField(gazelle, "foodLevel", gazelle.getProfile().getMaxFoodLevel());
        double normalMultiplier = gazelle.getEffectiveStaminaMultiplier();
        TestSupport.setField(gazelle, "foodLevel", 1);
        double survivalMultiplier = gazelle.getEffectiveStaminaMultiplier();
        TestSupport.assertTrue("critical survival gives temporary stamina burst",
                               survivalMultiplier > normalMultiplier);

        double beforeSpend = gazelle.getStaminaPercent();
        gazelle.spendStamina(5.0);
        TestSupport.assertClose("spending stamina reduces stamina",
                                beforeSpend - 5.0, gazelle.getStaminaPercent(), 0.01);
    }

    private static void testHydrationStages()
    {
        HydrationAssessment normal = new HydrationAssessment(85.0, 40,
                                                               0.24, 8.0);
        HydrationAssessment thirsty = new HydrationAssessment(65.0, 40,
                                                               0.24, 8.0);
        HydrationAssessment critical = new HydrationAssessment(30.0, 40,
                                                               0.24, 8.0);
        TestSupport.assertEquals("hydration normal stage",
                                 HydrationStage.NORMAL, normal.getStage());
        TestSupport.assertEquals("hydration thirsty stage",
                                 HydrationStage.THIRSTY, thirsty.getStage());
        TestSupport.assertEquals("hydration critical stage",
                                 HydrationStage.CRITICAL, critical.getStage());
        TestSupport.assertTrue("thirsty stage is water priority",
                               thirsty.isWaterPriority());
        TestSupport.assertTrue("critical stage is water priority",
                               critical.isWaterPriority());
    }

    private static void testBehaviorIntentPipeline()
    {
        BehaviorResolver resolver = new BehaviorResolver();
        BehaviorIntent food = new BehaviorIntent(7L, ActionType.GRAZE,
            BehaviorReason.FOOD, new Location(1, 1));
        BehaviorIntent thirst = new BehaviorIntent(7L, ActionType.SEEK_WATER,
            BehaviorReason.THIRST, new Location(1, 2));
        BehaviorIntent critical = new BehaviorIntent(7L, ActionType.DRINK,
            BehaviorReason.CRITICAL_DEHYDRATION, new Location(1, 1));
        BehaviorIntent criticalFood = new BehaviorIntent(7L, ActionType.GRAZE,
            BehaviorReason.CRITICAL_FOOD, new Location(1, 1));
        BehaviorIntent breeding = new BehaviorIntent(7L, ActionType.BREED,
            BehaviorReason.BREEDING, new Location(1, 3));
        BehaviorIntent selected = resolver.resolve(
            java.util.List.of(food, thirst, critical));
        TestSupport.assertEquals("critical dehydration wins behavior resolver",
                                 critical, selected);
        TestSupport.assertEquals("critical food outranks ordinary thirst",
            criticalFood, resolver.resolve(java.util.List.of(food, thirst, criticalFood)));
        TestSupport.assertEquals("food action outranks breeding",
            food, resolver.resolve(java.util.List.of(food, breeding)));
        TestSupport.assertEquals("intent keeps action type", ActionType.DRINK,
                                 selected.getActionType());
        Location copy = selected.getDestination();
        TestSupport.assertEquals("intent keeps destination row", 1, copy.row());
        TestSupport.assertEquals("intent keeps destination col", 1, copy.col());

        SimulationContext disabled = new SimulationContext(3, 3,
            SimulationConfig.baseline());
        Gazelle gazelle = new Gazelle(false, new Location(1, 1));
        BehaviorIntent noIntent = disabled.getThirstBehaviorProvider().propose(
            gazelle, java.util.Collections.<Location>emptyList(), disabled);
        TestSupport.assertEquals("thirst provider is inert when thirst is off",
                                 null, noIntent);

        MovementBehaviorProvider movement = new MovementBehaviorProvider();
        BehaviorIntent move = movement.propose(gazelle,
            java.util.List.of(new Location(1, 2)), true);
        BehaviorIntent rest = movement.propose(gazelle,
            java.util.List.of(new Location(1, 2)), false);
        BehaviorIntent wait = movement.propose(gazelle,
            java.util.Collections.<Location>emptyList(), false, false, null);
        TestSupport.assertEquals("movement provider proposes ordinary move",
                                 ActionType.MOVE, move.getActionType());
        TestSupport.assertEquals("movement intent keeps first free location",
                                 new Location(1, 2), move.getDestination());
        TestSupport.assertEquals("movement provider names rest", ActionType.REST,
                                 rest.getActionType());
        TestSupport.assertEquals("rest intent keeps current location",
                                 gazelle.getLocation(), rest.getDestination());
        TestSupport.assertEquals("blocked fallback names wait", ActionType.WAIT,
                                 wait.getActionType());
        TestSupport.assertEquals("wait uses blocked reason", BehaviorReason.BLOCKED,
                                 wait.getReason());
        TestSupport.assertTrue("behavior visual labels are bilingual",
            BehaviorVisuals.describe(ActionType.SEEK_WATER,
                BehaviorReason.THIRST).contains("寻水"));
        TestSupport.assertTrue("behavior summary names active actions",
            BehaviorVisuals.compactSummary(new int[] { 1, 2, 0, 3, 0, 0, 0 })
                .contains("graze 3"));
    }

    private static void testBehaviorOutcome()
    {
        BehaviorIntent seek = new BehaviorIntent(7L, ActionType.SEEK_WATER,
            BehaviorReason.THIRST, new Location(1, 2));
        BehaviorOutcome outcome = new BehaviorOutcome(seek,
            java.util.List.of(ExecutedEffect.MOVE, ExecutedEffect.DRINK),
            MovementDisposition.MOVED, ActionType.DRINK, false);
        TestSupport.assertEquals("outcome keeps selected primary intent", seek,
                                 outcome.getPrimaryIntent());
        TestSupport.assertEquals("outcome keeps ordered executed effects",
                                 ExecutedEffect.MOVE,
                                 outcome.getExecutedEffects().get(0));
        TestSupport.assertEquals("outcome keeps physical transition",
                                 MovementDisposition.MOVED,
                                 outcome.getMovementDisposition());
        TestSupport.assertEquals("drink is the final display outcome",
                                 ActionType.DRINK, outcome.getDisplayAction());
        TestSupport.assertFalse("drink does not receive rest credit",
                                outcome.receivesRestCredit());
    }

    private static void testO0aTransitionObservation()
    {
        TerrainMap terrain = new TerrainMap(30, 30, 20260629L);
        Field current = new Field(30, 30);
        Field next = new Field(30, 30);
        Location origin = firstPassableWithPassableNeighbor(terrain);
        Location requested = firstPassableNeighbor(terrain, origin);
        Gazelle gazelle = new Gazelle(false, origin);
        current.placeAnimal(gazelle, origin);
        SimulationContext context = new SimulationContext(30, 30,
            SimulationConfig.builder().terrainSeed(20260629L).build());
        context.startStep(7, current);
        java.util.List<TransitionObservation> observations = new ArrayList<>();
        context.addTransitionObservationListener(observations::add);
        FieldTransitionResult result = context.getFieldTransition(next).tryMove(
            gazelle, requested, java.util.List.of(origin), 2);
        TestSupport.assertTrue("O0a transition move succeeds", result.isSuccessful());
        TestSupport.assertEquals("O0a transition observer sees one fact", 1,
                                 observations.size());
        TransitionObservation observation = observations.get(0);
        TestSupport.assertEquals("O0a observation keeps animal ID", gazelle.getId(),
                                 observation.getAnimalId());
        TestSupport.assertEquals("O0a observation keeps requested location", requested,
                                 observation.getRequested());
        TestSupport.assertEquals("O0a observation has current step", 7,
                                 observation.getStep());
    }

    private static void testO0aWaterSourceIndex()
    {
        TerrainMap terrain = new TerrainMap(240, 360, 20260629L);
        WaterSourceIndex index = new WaterSourceIndex(terrain);
        TestSupport.assertEquals("O0a water index has two pools", 2,
                                 index.getPoolCount());
        TestSupport.assertEquals("O0a pool IDs are stable", "POOL-01",
                                 index.getPoolIds().get(0));
        TestSupport.assertTrue("O0a first pool has shore cells",
                               index.getShoreCellCount("POOL-01") > 0);
        TestSupport.assertTrue("O0a second pool has shore cells",
                               index.getShoreCellCount("POOL-02") > 0);
    }

    private static void testO0aSimulationFingerprint()
    {
        SimulationConfig config = SimulationConfig.builder().mapScale(1)
            .randomSeed(1111L).terrainSeed(20260629L).thirstEnabled(true).build();
        List<String> first = fingerprintRun(config, 10);
        List<String> second = fingerprintRun(config, 10);
        for(int step = 0; step < first.size(); step++) {
            TestSupport.assertEquals("O0a same-seed fingerprint step " + step,
                                     first.get(step), second.get(step));
        }
        WaterBehaviorRegressionProbe.verifyProbeTransparency(10, 1111L, 20260629L);
        TestSupport.assertTrue("O0a probe transparency completed", true);
    }

    private static void testO0b1FrameEventSummary()
    {
        BoundedEventDeque<Integer> queue = new BoundedEventDeque<>(3);
        queue.addLast(1);
        queue.addLast(2);
        queue.addLast(3);
        queue.addLast(4);
        TestSupport.assertEquals("O0b1 deque keeps its configured capacity", 3,
                                 queue.size());
        TestSupport.assertEquals("O0b1 deque evicts oldest in FIFO order", 2,
                                 queue.snapshot().get(0).intValue());
        TestSupport.assertEquals("O0b1 deque preserves newest order", 4,
                                 queue.snapshot().get(2).intValue());
        TestSupport.assertTrue("O0b1 deque snapshot is immutable",
                               rejectsListMutation(queue.snapshot()));

        TestSupport.assertEquals("O0b1 reset interval starts at zero", 0,
                                 FrameEventSummary.startStepFor(0));
        TestSupport.assertEquals("O0b1 first interval starts at one", 1,
                                 FrameEventSummary.startStepFor(1));
        TestSupport.assertEquals("O0b1 step 100 interval starts at one", 1,
                                 FrameEventSummary.startStepFor(100));
        TestSupport.assertEquals("O0b1 step 101 interval starts at 101", 101,
                                 FrameEventSummary.startStepFor(101));
        TestSupport.assertEquals("O0b1 step 200 interval starts at 101", 101,
                                 FrameEventSummary.startStepFor(200));

        Field field = new Field(3, 3);
        Gazelle gazelle = new Gazelle(false, new Location(1, 1));
        field.placeAnimal(gazelle, gazelle.getLocation());
        SimulationContext context = new SimulationContext(3, 3);
        EventAccumulator listener = new EventAccumulator();
        context.addEventListener(listener);

        for(int step = 1; step <= 100; step++) {
            context.startStep(step, field);
            if(step == 1) {
                context.recordEvent(SimulationEvent.drink(step, gazelle,
                                                           gazelle.getLocation()));
            }
            if(step == 100) {
                for(int event = 0;
                    event < FrameEventSummary.MAX_REPRESENTATIVE_EVENTS + 605;
                    event++) {
                    context.recordEvent(SimulationEvent.graze(step, gazelle,
                                                               gazelle.getLocation()));
                }
            }
        }
        RenderFrame frame100 = RenderFrame.capture(100, field, context, 90, 90);
        FrameEventSummary summary100 = frame100.frameEventSummary;
        int firstIntervalCount = FrameEventSummary.MAX_REPRESENTATIVE_EVENTS + 606;
        TestSupport.assertEquals("O0b1 step 100 summary starts at one", 1,
                                 summary100.getStartStep());
        TestSupport.assertEquals("O0b1 step 100 summary ends at 100", 100,
                                 summary100.getEndStep());
        TestSupport.assertEquals("O0b1 dense interval keeps exact total",
                                 firstIntervalCount,
                                 (int)summary100.getTotalEventCount());
        TestSupport.assertEquals("O0b1 type x species count is exact",
            FrameEventSummary.MAX_REPRESENTATIVE_EVENTS + 605,
            (int)summary100.getCount(SimulationEvent.EventType.GRAZE,
                                     SpeciesRegistry.GAZELLE));
        TestSupport.assertEquals("O0b1 listener reconciles complete interval",
                                 listener.getTotalEvents(),
                                 (int)summary100.getTotalEventCount());
        TestSupport.assertEquals("O0b1 samples stay bounded",
            FrameEventSummary.MAX_REPRESENTATIVE_EVENTS,
            summary100.getRepresentativeEvents().size());
        TestSupport.assertTrue("O0b1 dense interval declares sample truncation",
                               summary100.isRepresentativeSamplesTruncated());
        TestSupport.assertTrue("O0b1 count map is immutable",
                               rejectsMapMutation(summary100));
        TestSupport.assertEquals("O0b1 render samples are not recent-event window",
            FrameEventSummary.MAX_REPRESENTATIVE_EVENTS,
            frame100.eventSummary.size());
        TestSupport.assertEquals("O0b1 recent events retain diagnostic capacity", 600,
                                 context.getRecentEvents().size());

        context.startStep(101, field);
        context.recordEvent(SimulationEvent.drink(101, gazelle,
                                                   gazelle.getLocation()));
        for(int step = 102; step <= 137; step++) {
            context.startStep(step, field);
        }
        RenderFrame frame137 = RenderFrame.capture(137, field, context, 90, 90);
        TestSupport.assertEquals("O0b1 final non-boundary frame starts at 101", 101,
                                 frame137.frameEventSummary.getStartStep());
        TestSupport.assertEquals("O0b1 final non-boundary frame ends at 137", 137,
                                 frame137.frameEventSummary.getEndStep());
        TestSupport.assertEquals("O0b1 later facts cannot change frame 100", 
                                 firstIntervalCount,
                                 (int)frame100.frameEventSummary.getTotalEventCount());

        for(int step = 138; step <= 200; step++) {
            context.startStep(step, field);
        }
        context.recordEvent(SimulationEvent.graze(200, gazelle,
                                                   gazelle.getLocation()));
        RenderFrame frame200 = RenderFrame.capture(200, field, context, 90, 90);
        TestSupport.assertEquals("O0b1 step 200 summary starts at 101", 101,
                                 frame200.frameEventSummary.getStartStep());
        TestSupport.assertEquals("O0b1 step 200 summary ends at 200", 200,
                                 frame200.frameEventSummary.getEndStep());
        TestSupport.assertEquals("O0b1 step 200 keeps both interval events", 2,
                                 (int)frame200.frameEventSummary.getTotalEventCount());

        context.startStep(0, field);
        FrameEventSummary reset = context.getFrameEventSummary();
        TestSupport.assertEquals("O0b1 reset frame starts at zero", 0,
                                 reset.getStartStep());
        TestSupport.assertEquals("O0b1 reset frame ends at zero", 0,
                                 reset.getEndStep());
        TestSupport.assertEquals("O0b1 reset frame has no inherited events", 0,
                                 (int)reset.getTotalEventCount());
    }

    private static boolean rejectsListMutation(List<?> values)
    {
        try {
            values.add(null);
            return false;
        }
        catch(UnsupportedOperationException expected) {
            return true;
        }
    }

    private static boolean rejectsMapMutation(FrameEventSummary summary)
    {
        try {
            summary.getCounts().put(new FrameEventSummary.CountKey(
                SimulationEvent.EventType.HUNT, SpeciesRegistry.LION), 1L);
            return false;
        }
        catch(UnsupportedOperationException expected) {
            return true;
        }
    }

    private static List<String> fingerprintRun(SimulationConfig config, int steps)
    {
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        List<String> fingerprints = new ArrayList<>();
        fingerprints.add(SimulationFingerprint.capture(simulator));
        for(int step = 0; step < steps; step++) {
            simulator.simulateOneStep();
            fingerprints.add(SimulationFingerprint.capture(simulator));
        }
        return fingerprints;
    }

    private static Location firstPassableWithPassableNeighbor(TerrainMap terrain)
    {
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location location = new Location(row, col);
                if(terrain.isPassable(location) &&
                   firstPassableNeighbor(terrain, location) != null) {
                    return location;
                }
            }
        }
        throw new IllegalStateException("No passable terrain pair found.");
    }

    private static Location firstPassableNeighbor(TerrainMap terrain, Location origin)
    {
        for(int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            for(int colOffset = -1; colOffset <= 1; colOffset++) {
                if(rowOffset == 0 && colOffset == 0) continue;
                Location location = new Location(origin.row() + rowOffset,
                    origin.col() + colOffset);
                if(location.row() >= 0 && location.row() < terrain.getDepth() &&
                   location.col() >= 0 && location.col() < terrain.getWidth() &&
                   terrain.isPassable(location)) {
                    return location;
                }
            }
        }
        return null;
    }

    private static void testFieldTransitionPipeline()
    {
        TerrainMap terrain = new TerrainMap(24, 24, 9173L);
        List<Location> passable = new ArrayList<>();
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location location = new Location(row, col);
                if(terrain.isPassable(location)) {
                    passable.add(location);
                }
            }
        }
        Location origin = passable.get(0);
        Location requested = passable.get(1);
        Location alternate = passable.get(2);
        Field target = new Field(24, 24);
        Gazelle blocker = new Gazelle(false, requested);
        Gazelle mover = new Gazelle(false, origin);
        target.placeAnimal(blocker, requested);

        FieldTransition transition = new FieldTransition(target, terrain);
        FieldTransitionResult rerouted = transition.tryMove(mover, requested,
            java.util.List.of(alternate), 0);
        TestSupport.assertEquals("occupied destination is explicitly rerouted",
                                 TransitionStatus.REROUTED,
                                 rerouted.getStatus());
        TestSupport.assertEquals("reroute keeps existing occupant", blocker,
                                 target.getAnimalAt(requested));
        TestSupport.assertEquals("reroute reserves alternate destination", mover,
                                 target.getAnimalAt(alternate));
        TestSupport.assertEquals("transition counts rerouted moves", 1,
                                 transition.getCount(TransitionStatus.REROUTED));
        TestSupport.assertTrue("rerouted target field stays consistent",
                               target.hasConsistentState());

        Gazelle rejected = new Gazelle(false, new Location(0, 0));
        TestSupport.assertFalse("field reservation refuses silent replacement",
                                target.tryPlaceAnimal(rejected, requested));
        TestSupport.assertTrue("legacy placement reports collision explicitly",
                               placeRejectsCollision(target, rejected, requested));

        Gazelle child = new Gazelle(false, requested);
        FieldTransitionResult spawnConflict = transition.trySpawn(child, requested);
        TestSupport.assertEquals("spawn conflict has explicit result",
                                 TransitionStatus.CONFLICT_LOST,
                                 spawnConflict.getStatus());
        TestSupport.assertFalse("unreserved child is not left alive",
                                child.isAlive());
        TestSupport.assertEquals("spawn conflict never replaces occupant", blocker,
                                 target.getAnimalAt(requested));

        Field empty = new Field(24, 24);
        Location searchOrigin = findPassableWithNeighbour(terrain);
        Location nearest = TerrainNavigator.nearestFreePassable(
            empty, terrain, searchOrigin, 4);
        int distance = TerrainNavigator.reachableDistances(terrain,
            searchOrigin, 4).distanceTo(nearest);
        TestSupport.assertEquals("nearest fallback uses path distance", 1,
                                 distance);
    }

    private static boolean placeRejectsCollision(Field field, Animal animal,
                                                 Location location)
    {
        try {
            field.placeAnimal(animal, location);
            return false;
        }
        catch(IllegalStateException expected) {
            return true;
        }
    }

    private static Location findPassableWithNeighbour(TerrainMap terrain)
    {
        for(int row = 1; row < terrain.getDepth() - 1; row++) {
            for(int col = 1; col < terrain.getWidth() - 1; col++) {
                Location origin = new Location(row, col);
                if(!terrain.isPassable(origin)) {
                    continue;
                }
                for(int dr = -1; dr <= 1; dr++) {
                    for(int dc = -1; dc <= 1; dc++) {
                        Location neighbour = new Location(row + dr, col + dc);
                        if((dr != 0 || dc != 0) &&
                           terrain.isPassable(neighbour)) {
                            return origin;
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("test terrain has no passable neighbour");
    }

    private static void testSurvivalAssessment()
    {
        SurvivalAssessment assessment = new SurvivalAssessment(80.0, 35.0);
        TestSupport.assertClose("overall survival uses lower resource",
                               35.0, assessment.getOverallSurvivalPercent(), 0.01);
        TestSupport.assertClose("food pressure stays resource-specific",
                               0.0, assessment.getFoodPressure(), 0.01);
        TestSupport.assertClose("hydration pressure stays resource-specific",
                               0.875, assessment.getHydrationPressure(), 0.02);

        Gazelle gazelle = new Gazelle(false, new Location(1, 1));
        SimulationContext off = new SimulationContext(5, 5,
            SimulationConfig.baseline());
        TestSupport.setField(gazelle, "foodLevel", gazelle.getProfile().getMaxFoodLevel() / 2);
        SurvivalAssessment disabled = off.assessSurvival(gazelle);
        TestSupport.assertClose("thirst off hydration survival is 100",
                               100.0, disabled.getHydrationSurvivalPercent(), 0.01);
        TestSupport.assertClose("thirst off overall equals food",
                               disabled.getFoodSurvivalPercent(),
                               disabled.getOverallSurvivalPercent(), 0.01);
    }

    private static void testStarvationNeedsThreeSteps()
    {
        Lion lion = new Lion(false, new Location(1, 1));
        Field field = new Field(3, 3);
        field.placeAnimal(lion, lion.getLocation());
        SimulationContext context = new SimulationContext(3, 3);
        context.startStep(1, field);
        TestSupport.setField(lion, "foodLevel", 0);
        TestSupport.setField(lion, "starvingSteps", 0);

        TestSupport.invokePrivate(lion, "useEnergy",
            new Class<?>[] { SimulationContext.class }, new Object[] { context });
        TestSupport.assertTrue("first starving step keeps animal alive", lion.isAlive());
        TestSupport.assertEquals("first starving counter is 1", 1,
                                 lion.getStarvingSteps());

        TestSupport.invokePrivate(lion, "useEnergy",
            new Class<?>[] { SimulationContext.class }, new Object[] { context });
        TestSupport.assertTrue("second starving step keeps animal alive", lion.isAlive());
        TestSupport.assertEquals("second starving counter is 2", 2,
                                 lion.getStarvingSteps());

        TestSupport.invokePrivate(lion, "useEnergy",
            new Class<?>[] { SimulationContext.class }, new Object[] { context });
        TestSupport.assertFalse("third starving step kills animal", lion.isAlive());
        TestSupport.assertEquals("food exhaustion records starvation cause",
                                 DeathCause.STARVATION, lion.getDeathCause());
        TestSupport.assertEquals("starvation total is counted", 1,
                                 context.getDeathCount(DeathCause.STARVATION));
    }

    private static void testDeathCauseClassification()
    {
        Field ageField = new Field(4, 4);
        Gazelle old = new Gazelle(false, new Location(0, 0));
        ageField.placeAnimal(old, old.getLocation());
        SimulationContext ageContext = new SimulationContext(4, 4);
        ageContext.startStep(1, ageField);
        TestSupport.setField(old, "age", old.getProfile().getMaxAge());
        TestSupport.invokePrivate(old, "incrementAge",
            new Class<?>[] { SimulationContext.class },
            new Object[] { ageContext });
        TestSupport.assertEquals("age limit records age cause", DeathCause.AGE,
                                 old.getDeathCause());
        TestSupport.assertTrue("age death emits typed death event",
                               hasDeathEvent(ageContext, DeathCause.AGE));
        old.setDead(DeathCause.PREDATION);
        TestSupport.assertEquals("first death cause cannot be overwritten",
                                 DeathCause.AGE, old.getDeathCause());

    }

    private static boolean hasDeathEvent(SimulationContext context,
                                         DeathCause cause)
    {
        for(SimulationEvent event : context.getCurrentEvents()) {
            if(event.type == SimulationEvent.EventType.DEATH &&
               event.deathCause == cause) {
                return true;
            }
        }
        return false;
    }

    private static void testFeedingClearsStarvation()
    {
        Gazelle gazelle = new Gazelle(false, new Location(1, 1));
        TestSupport.setField(gazelle, "foodLevel", -2);
        TestSupport.setField(gazelle, "starvingSteps", 2);
        gazelle.feed(10);
        TestSupport.assertTrue("feeding restores food above zero",
                               gazelle.getFoodLevel() > 0);
        TestSupport.assertEquals("feeding clears starving counter",
                                 0, gazelle.getStarvingSteps());
    }

    private static void testReportInterval()
    {
        Field field = new Field(3, 3);
        SimulationContext context = new SimulationContext(3, 3);
        CountingRecorder recorder =
            new CountingRecorder("/private/tmp/savanna-alltests-report.html");
        context.startStep(0, field);
        recorder.start(field, context);
        for(int step = 1; step <= 250; step++) {
            context.startStep(step, field);
            recorder.recordStep(step, field, context);
        }
        recorder.finish(field, context, "unit interval test");
        TestSupport.assertEquals("interval report records start, 100, 200, final",
                                 4, recorder.getRecordedSteps().size());
        TestSupport.assertEquals("first report step is 0",
                                 0, recorder.getRecordedSteps().get(0).intValue());
        TestSupport.assertEquals("second report step is 100",
                                 100, recorder.getRecordedSteps().get(1).intValue());
        TestSupport.assertEquals("third report step is 200",
                                 200, recorder.getRecordedSteps().get(2).intValue());
        TestSupport.assertEquals("final report step is 250",
                                 250, recorder.getRecordedSteps().get(3).intValue());
    }

    private static class CountingRecorder extends StepReportRecorder
    {
        private final List<Integer> recordedSteps = new ArrayList<>();

        CountingRecorder(String reportFileName)
        {
            super(reportFileName);
        }

        protected void onStepRecorded(StepSnapshot snapshot)
        {
            recordedSteps.add(snapshot.getStep());
        }

        List<Integer> getRecordedSteps()
        {
            return recordedSteps;
        }
    }
}
