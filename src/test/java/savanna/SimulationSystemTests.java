package savanna;

import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests interactions between the main ecological systems.
 */
public class SimulationSystemTests
{
    public static void run()
    {
        TestSupport.startGroup("System tests");
        Randomizer.reset();
        testWeatherModifiers();
        testGrassFeeding();
        testDiseasePredationTransmission();
        testStaminaAffectsPredationContest();
        testPredationDeathCause();
        testBreedingRequiresMate();
        testSimulationEventBus();
        testEventListenerReceivesRecordedEvents();
        testEventListenerSurvivesSimulatorReset();
        testEventAccumulatorOutlivesRecentEventWindow();
        testEventAccumulatorCountsLocationsByType();
        testEventAccumulatorClearResetsCounts();
        testSimulationSnapshotIsImmutable();
        testRenderFrameIsImmutableAndCurrentOnly();
        RenderPipelineProbe.run();
        TerrainBackgroundCacheTests.run();
        DisplayFactAuditProbe.run();
        testDiseaseLifecycleEvents();
        testMovementEventsAndScenes();
        testHistoricalActorsStayOutOfCurrentScenes();
        testAmbientInspectActors();
        testTerrainMapGeneration();
        testWaterSafetyAudit();
        testVisualGridGeometryMatchesTerrainCells();
        testOrdinaryFootprintUsesSharedGeometry();
        testInspectFootprintUsesSharedGeometry();
        testVisualWaterMaskCoversShorelineFootprint();
        testFieldRendererAcceptsSharedGeometry();
        testOrdinaryAnimalLayerHasVisiblePixels();
        testAnimalStatusLogRows();
        testThirstSystemDefaultsOff();
        testHydrationPolicyDefaults();
        testThirstSystemDrainsAndDrinksFromShore();
        testDeterministicDehydrationGrace();
        testW1BehaviorOutcomes();
        testFoodBehaviorProvider();
        testTerminalDiagnostics();
        testSimulationConfigScaling();
        testDiseaseMultiplierPressure();
        testViewportInteractionControls();
        TestSupport.finishGroup();
    }

    private static void testWeatherModifiers()
    {
        TestSupport.assertTrue("rain increases infection risk",
            WeatherType.RAIN.getInfectionModifier() > WeatherType.CLEAR.getInfectionModifier());
        TestSupport.assertTrue("rain increases plant growth",
            WeatherType.RAIN.getPlantGrowthModifier() > WeatherType.CLEAR.getPlantGrowthModifier());
        TestSupport.assertTrue("fog reduces visibility",
            WeatherType.FOG.getVisibilityModifier() < WeatherType.CLEAR.getVisibilityModifier());

        SeasonalWeatherSystem weather = new SeasonalWeatherSystem();
        Lion lion = new Lion(false, new Location(1, 1));
        TestSupport.setField(weather, "currentWeather", WeatherType.CLEAR);
        int clearRange = weather.getHuntingRange(lion);
        TestSupport.setField(weather, "currentWeather", WeatherType.FOG);
        int fogRange = weather.getHuntingRange(lion);
        TestSupport.assertTrue("fog lowers predator hunting range", fogRange < clearRange);
    }

    private static void testRenderFrameIsImmutableAndCurrentOnly()
    {
        Field field = new Field(3, 3);
        Gazelle gazelle = new Gazelle(false, new Location(1, 1));
        field.placeAnimal(gazelle, gazelle.getLocation());
        SimulationContext context = new SimulationContext(3, 3);
        context.startStep(4, field);
        RenderFrame frame = RenderFrame.capture(4, field, context, 300, 300);
        TestSupport.assertEquals("render frame keeps current animal", 1,
                                 frame.animals.size());
        TestSupport.assertEquals("render frame keeps animal species",
                                 SpeciesRegistry.GAZELLE,
                                 frame.animals.get(0).species);
        TestSupport.assertTrue("render frame animal list is immutable",
                               listRejectsRenderMutation(frame.animals));
        RenderFramePublisher publisher = new RenderFramePublisher();
        publisher.publish(frame);
        TestSupport.assertEquals("publisher exposes latest frame", frame,
                                 publisher.latest());
    }

    private static boolean listRejectsRenderMutation(
        List<RenderAnimalSnapshot> snapshots)
    {
        try {
            snapshots.add(null);
            return false;
        }
        catch(UnsupportedOperationException expected) {
            return true;
        }
    }

    private static void testGrassFeeding()
    {
        Randomizer.reset();
        // A tiny legacy map is all water; use a grassland cell of a 24x24 map.
        SimulationContext context = new SimulationContext(24, 24);
        GrasslandFoodSystem grass = (GrasslandFoodSystem)context.getFoodSystem();
        Location cell = null;
        for(int row = 0; row < 24 && cell == null; row++) {
            for(int col = 0; col < 24 && cell == null; col++) {
                Location candidate = new Location(row, col);
                if(context.getTerrainMap().getTerrainAt(candidate) == TerrainType.GRASSLAND &&
                   grass.getCapacityAt(candidate) > 0.0) cell = candidate;
            }
        }
        Field field = new Field(24, 24);
        Gazelle gazelle = new Gazelle(false, cell);
        TestSupport.setField(gazelle, "foodLevel", 1);
        field.placeAnimal(gazelle, gazelle.getLocation());
        context.startStep(0, field);
        int beforeFood = gazelle.getFoodLevel();
        double beforeGrass = context.getFoodSystem().getFoodLevelAt(gazelle.getLocation());
        context.getFoodSystem().feedHerbivoreAt(gazelle, gazelle.getLocation(), context);
        TestSupport.assertTrue("grazing increases herbivore food",
                               gazelle.getFoodLevel() > beforeFood);
        TestSupport.assertTrue("grazing consumes local grass",
            context.getFoodSystem().getFoodLevelAt(gazelle.getLocation()) < beforeGrass);
        TestSupport.assertTrue("grazing records event",
            hasEvent(context, SimulationEvent.EventType.GRAZE));
    }

    private static void testDiseasePredationTransmission()
    {
        Randomizer.reset();
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        SimulationContext context = new SimulationContext(3, 3);
        Field field = new Field(3, 3);
        context.startStep(0, field);
        SavannahDiseaseSystem disease = new SavannahDiseaseSystem();
        boolean transmitted = false;
        for(int attempt = 0; attempt < 80 && !transmitted; attempt++) {
            Gazelle prey = new Gazelle(false, new Location(1, 1),
                Randomizer.getRandom(), identities);
            Lion predator = new Lion(false, new Location(1, 2),
                Randomizer.getRandom(), identities);
            prey.infect(1.0);
            disease.afterPredation(prey, predator, context);
            transmitted = predator.isInfected();
        }
        TestSupport.assertTrue("infected prey can transmit disease to predator",
                               transmitted);
        TestSupport.assertTrue("infection transmission records event",
            hasEvent(context, SimulationEvent.EventType.INFECTION));
    }

    private static void testStaminaAffectsPredationContest()
    {
        FoodChainPredationSystem predation = new FoodChainPredationSystem();
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Lion predator = new Lion(false, new Location(1, 1),
            Randomizer.getRandom(), identities);
        Gazelle prey = new Gazelle(false, new Location(1, 2),
            Randomizer.getRandom(), identities);

        TestSupport.setField(predator, "stamina", 95.0);
        TestSupport.setField(prey, "stamina", 20.0);
        double predatorAdvantage = TestSupport.invokePrivateDouble(predation,
            "staminaContestModifier",
            new Class<?>[] { SavannahAnimal.class, SavannahAnimal.class },
            new Object[] { predator, prey });

        TestSupport.setField(predator, "stamina", 20.0);
        TestSupport.setField(prey, "stamina", 95.0);
        double preyAdvantage = TestSupport.invokePrivateDouble(predation,
            "staminaContestModifier",
            new Class<?>[] { SavannahAnimal.class, SavannahAnimal.class },
            new Object[] { predator, prey });

        TestSupport.assertTrue("predator stamina advantage improves contest",
                               predatorAdvantage > 1.0);
        TestSupport.assertTrue("prey stamina advantage reduces contest",
                               preyAdvantage < 1.0);
    }

    private static void testPredationDeathCause()
    {
        SimulationContext context = new SimulationContext(24, 24);
        Location[] pair = adjacentPassablePair(context.getTerrainMap());
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Lion predator = new Lion(false, pair[0], Randomizer.getRandom(),
                                 identities);
        Gazelle prey = new Gazelle(false, pair[1], Randomizer.getRandom(),
                                  identities);
        TestSupport.setField(predator, "age",
                             predator.getProfile().getAdultAge());
        TestSupport.setField(predator, "foodLevel", 0);
        Field field = new Field(24, 24);
        field.placeAnimal(predator, predator.getLocation());
        field.placeAnimal(prey, prey.getLocation());
        context.startStep(1, field);
        java.util.Random alwaysSucceeds = new java.util.Random(1L) {
            @Override
            public double nextDouble()
            {
                return 0.0;
            }
        };
        FoodChainPredationSystem predation =
            new FoodChainPredationSystem(alwaysSucceeds);
        predation.hunt(predator, field, context);
        TestSupport.assertFalse("successful predation kills prey", prey.isAlive());
        TestSupport.assertEquals("predation records prey death cause",
                                 DeathCause.PREDATION, prey.getDeathCause());
        TestSupport.assertEquals("predation death is counted", 1,
            context.getDeathCount(DeathCause.PREDATION));
        TestSupport.assertTrue("predation emits typed death event",
                               hasDeathEvent(context, DeathCause.PREDATION));
    }

    private static Location[] adjacentPassablePair(TerrainMap terrain)
    {
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location first = new Location(row, col);
                if(!terrain.isPassable(first)) {
                    continue;
                }
                for(int dr = -1; dr <= 1; dr++) {
                    for(int dc = -1; dc <= 1; dc++) {
                        Location second = new Location(row + dr, col + dc);
                        if((dr != 0 || dc != 0) && second.row() >= 0 &&
                           second.row() < terrain.getDepth() &&
                           second.col() >= 0 && second.col() < terrain.getWidth() &&
                           terrain.isPassable(second)) {
                            return new Location[] { first, second };
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("test terrain has no adjacent land pair");
    }

    private static void testBreedingRequiresMate()
    {
        Randomizer.reset();
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        TestAnimal noMateFemale = breedingAnimal(Sex.FEMALE, new Location(2, 2),
            identities);
        Field noMateField = new Field(24, 24);
        noMateField.placeAnimal(noMateFemale, noMateFemale.getLocation());
        SimulationContext noMateContext = breedingContext();
        noMateContext.startStep(0, noMateField);
        PositionStepCoordinator noMatePositions = new PositionStepCoordinator(
            noMateField, noMateContext);
        noMatePositions.propose(noMateFemale, noMatePositions.createAvailabilityView());
        TestSupport.assertFalse("pipeline breeding without mate records no precommit birth",
            hasEvent(noMateContext, SimulationEvent.EventType.BIRTH));
        Field noMateCommitted = noMatePositions.resolveAndCommitCompleteStep();
        TestSupport.assertEquals("pipeline female without mate has no child", 1,
            noMateCommitted.getLivingPopulation());
        TestSupport.assertFalse("pipeline female without mate records no birth",
            hasEvent(noMateContext, SimulationEvent.EventType.BIRTH));

        TestAnimal female = breedingAnimal(Sex.FEMALE, new Location(2, 2), identities);
        TestAnimal male = breedingAnimal(Sex.MALE, new Location(2, 3), identities);
        Field mateField = new Field(24, 24);
        mateField.placeAnimal(female, female.getLocation());
        mateField.placeAnimal(male, male.getLocation());
        SimulationContext mateContext = breedingContext();
        mateContext.startStep(1, mateField);
        PositionStepCoordinator matePositions = new PositionStepCoordinator(mateField,
            mateContext);
        PositionAvailabilityView availability = matePositions.createAvailabilityView();
        matePositions.propose(female, availability);
        matePositions.propose(male, availability);
        TestSupport.assertFalse("pipeline breeding defers birth until commit",
            hasEvent(mateContext, SimulationEvent.EventType.BIRTH));
        Field mateCommitted = matePositions.resolveAndCommitCompleteStep();
        // Plan v3.0 M1-2: a mated female conceives a paid pregnancy and gives
        // birth only when it is due.
        ReproductionState pregnancy = female.getReproductionState();
        TestSupport.assertTrue("pipeline mated female conceives a paid pregnancy",
            pregnancy.isPregnant());
        TestSupport.assertEquals("pipeline conception creates no child yet", 2,
            mateCommitted.getLivingPopulation());
        TestSupport.assertFalse("pipeline conception records no birth",
            hasEvent(mateContext, SimulationEvent.EventType.BIRTH));
        TestSupport.assertEquals("pipeline conception debits the litter reserve",
            Long.valueOf((long)pregnancy.getPendingOrdinals().size() *
                ReproductionPolicy.childReserveFood(female.getProfile())),
            Long.valueOf(mateContext.getBirthAccount().getMaternalFood()));

        mateContext.startStep(pregnancy.getDueStep(), mateCommitted);
        PositionStepCoordinator duePositions = new PositionStepCoordinator(
            mateCommitted, mateContext);
        PositionAvailabilityView dueView = duePositions.createAvailabilityView();
        duePositions.propose(female, dueView);
        duePositions.propose(male, dueView);
        Field born = duePositions.resolveAndCommitCompleteStep();
        TestSupport.assertTrue("pipeline due pregnancy has a child",
            born.getLivingPopulation() > 2);
        TestSupport.assertTrue("pipeline due commit records birth",
            hasEvent(mateContext, SimulationEvent.EventType.BIRTH));
    }

    private static void testSimulationEventBus()
    {
        Randomizer.reset();
        Field field = new Field(1, 1);
        SimulationContext context = new SimulationContext(1, 1);
        Gazelle gazelle = new Gazelle(false, new Location(0, 0));
        context.startStep(0, field);
        context.recordEvent(SimulationEvent.graze(0, gazelle, new Location(0, 0)));
        TestSupport.assertEquals("current events records same step event",
                                 1, context.getCurrentEvents().size());
        TestSupport.assertEquals("recent events includes current event",
                                 1, context.getRecentEvents().size());

        context.startStep(1, field);
        TestSupport.assertEquals("startStep rotates previous events",
                                 1, context.getPreviousEvents().size());
        TestSupport.assertEquals("startStep clears current events",
                                 0, context.getCurrentEvents().size());

        for(int index = 0; index < 605; index++) {
            context.recordEvent(SimulationEvent.graze(index, gazelle,
                                                      new Location(0, 0)));
        }
        TestSupport.assertEquals("recent events are bounded",
                                 600, context.getRecentEvents().size());
        TestSupport.assertEquals("recent events drop oldest first",
            5, context.getRecentEvents().get(0).step);
    }

    private static void testEventListenerReceivesRecordedEvents()
    {
        Randomizer.reset();
        SimulationContext context = new SimulationContext(3, 3);
        EventAccumulator accumulator = new EventAccumulator();
        context.addEventListener(accumulator);

        Gazelle gazelle = new Gazelle(false, new Location(0, 0));
        context.recordEvent(SimulationEvent.graze(7, gazelle,
                                                  new Location(0, 0)));

        TestSupport.assertEquals("listener sees recorded event", 1,
                                 accumulator.getTotalEvents());
        TestSupport.assertEquals("listener counts event type", 1,
            accumulator.getCount(SimulationEvent.EventType.GRAZE));
        TestSupport.assertEquals("listener counts event location", 1,
                                 accumulator.getCountAt(new Location(0, 0)));
    }

    private static void testEventListenerSurvivesSimulatorReset()
    {
        Randomizer.reset();
        Simulator simulator = new Simulator(SimulationConfig.baseline(), false);
        simulator.setVerbose(false);
        EventAccumulator accumulator = new EventAccumulator();
        simulator.addEventListener(accumulator);
        simulator.reset();

        Gazelle gazelle = new Gazelle(false, new Location(0, 0),
            Randomizer.getRandom(), simulator.getIdentityAllocator());
        simulator.getContext().recordEvent(SimulationEvent.graze(1, gazelle,
                                                                 new Location(0, 0)));

        TestSupport.assertEquals("simulator listener survives reset", 1,
                                 accumulator.getTotalEvents());
    }

    private static void testEventAccumulatorOutlivesRecentEventWindow()
    {
        Randomizer.reset();
        SimulationContext context = new SimulationContext(3, 3);
        EventAccumulator accumulator = new EventAccumulator();
        context.addEventListener(accumulator);
        Gazelle gazelle = new Gazelle(false, new Location(0, 0));

        for(int index = 0; index < 650; index++) {
            context.recordEvent(SimulationEvent.graze(index, gazelle,
                                                      new Location(index % 3, 0)));
        }

        TestSupport.assertEquals("recent events stay capped", 600,
                                 context.getRecentEvents().size());
        TestSupport.assertEquals("accumulator keeps whole run", 650,
                                 accumulator.getTotalEvents());
        TestSupport.assertEquals("accumulator keeps whole type count", 650,
            accumulator.getCount(SimulationEvent.EventType.GRAZE));
    }

    private static void testEventAccumulatorCountsLocationsByType()
    {
        Randomizer.reset();
        EventAccumulator accumulator = new EventAccumulator();
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Gazelle gazelle = new Gazelle(false, new Location(1, 1),
            Randomizer.getRandom(), identities);
        Lion lion = new Lion(false, new Location(2, 2),
            Randomizer.getRandom(), identities);

        accumulator.onEvent(SimulationEvent.graze(1, gazelle,
                                                  new Location(1, 1)));
        accumulator.onEvent(SimulationEvent.graze(2, gazelle,
                                                  new Location(1, 1)));
        accumulator.onEvent(SimulationEvent.hunt(3, lion, gazelle));

        TestSupport.assertEquals("all heat count includes every type at location",
                                 3, accumulator.getCountAt(new Location(1, 1)));
        TestSupport.assertEquals("typed heat count filters graze", 2,
            accumulator.getCountAt(new Location(1, 1),
                                   SimulationEvent.EventType.GRAZE));
        TestSupport.assertEquals("typed heat count filters hunt", 1,
            accumulator.getCountAt(new Location(1, 1),
                                   SimulationEvent.EventType.HUNT));
        TestSupport.assertEquals("typed heat max is available", 2,
            accumulator.getMaxCount(SimulationEvent.EventType.GRAZE));
    }

    private static void testEventAccumulatorClearResetsCounts()
    {
        Randomizer.reset();
        EventAccumulator accumulator = new EventAccumulator();
        Gazelle gazelle = new Gazelle(false, new Location(0, 0));
        accumulator.onEvent(SimulationEvent.graze(1, gazelle,
                                                  new Location(0, 0)));
        accumulator.clear();

        TestSupport.assertEquals("clear resets total events", 0,
                                 accumulator.getTotalEvents());
        TestSupport.assertEquals("clear resets location count", 0,
                                 accumulator.getCountAt(new Location(0, 0)));
        TestSupport.assertEquals("clear resets max count", 0,
            accumulator.getMaxCount(SimulationEvent.EventType.GRAZE));
    }

    private static void testSimulationSnapshotIsImmutable()
    {
        Randomizer.reset();
        Field field = new Field(3, 3);
        Gazelle gazelle = new Gazelle(false, new Location(1, 1));
        field.placeAnimal(gazelle, gazelle.getLocation());
        SimulationContext context = new SimulationContext(3, 3);
        context.startStep(12, field);
        SnapshotRecorder recorder = new SnapshotRecorder();
        SimulationSnapshot snapshot = recorder.capture(12, field, context);
        AnimalSnapshot animalSnapshot = snapshot.getAnimals().get(0);

        TestSupport.setField(gazelle, "foodLevel", 1);
        gazelle.setDead();

        TestSupport.assertEquals("snapshot recorder stores one frame", 1,
                                 recorder.size());
        TestSupport.assertEquals("snapshot keeps animal row", 1,
                                 animalSnapshot.row);
        TestSupport.assertTrue("snapshot keeps original alive state",
                               animalSnapshot.alive);
        TestSupport.assertTrue("snapshot list is immutable",
                               listRejectsMutation(snapshot.getAnimals()));
    }

    private static void testDiseaseLifecycleEvents()
    {
        Randomizer.reset();
        Field recoveryField = new Field(1, 1);
        Gazelle recovering = new Gazelle(false, new Location(0, 0));
        recovering.infect(0.35);
        recoveryField.placeAnimal(recovering, recovering.getLocation());
        SimulationContext recoveryContext = new SimulationContext(1, 1,
            new SimulationConfig(1, 1.0, 1.0, 1.0, 1.0, 0.0001));
        recoveryContext.startStep(0, recoveryField);
        SavannahDiseaseSystem recoveryDisease = new SavannahDiseaseSystem();
        for(int attempt = 0; attempt < 1000 && recovering.isInfected() &&
            recovering.isAlive(); attempt++) {
            recoveryDisease.progressDisease(recovering, recoveryContext,
                                            recoveryField);
        }
        TestSupport.assertTrue("recovery records event",
            hasEvent(recoveryContext, SimulationEvent.EventType.RECOVERY));

        Randomizer.reset();
        Field deathField = new Field(1, 1);
        Gazelle dying = new Gazelle(false, new Location(0, 0));
        dying.infect(1.0);
        deathField.placeAnimal(dying, dying.getLocation());
        SimulationContext deathContext = new SimulationContext(1, 1,
            new SimulationConfig(1, 1.0, 1.0, 1.0, 1.0, 1000.0));
        deathContext.startStep(0, deathField);
        SavannahDiseaseSystem deathDisease = new SavannahDiseaseSystem();
        for(int attempt = 0; attempt < 20 && dying.isAlive(); attempt++) {
            deathDisease.progressDisease(dying, deathContext, deathField);
        }
        TestSupport.assertTrue("disease death records typed event",
            hasDeathEvent(deathContext, DeathCause.DISEASE));
        TestSupport.assertEquals("disease death keeps cause on animal",
                                 DeathCause.DISEASE, dying.getDeathCause());
        TestSupport.assertEquals("disease death count comes from context", 1,
            deathContext.getDeathCount(DeathCause.DISEASE));
    }

    private static void testMovementEventsAndScenes()
    {
        Randomizer.reset();
        Field currentField = new Field(10, 10);
        TestAnimal mover = createTestAnimal(Sex.FEMALE, new Location(0, 9));
        currentField.placeAnimal(mover, mover.getLocation());
        SimulationContext context = new SimulationContext(10, 10);
        context.startStep(8, currentField);
        completePipelineStep(currentField, context);
        TestSupport.assertTrue("real movement records move event",
            hasEvent(context, SimulationEvent.EventType.MOVE));

        Randomizer.reset();
        Field restingField = new Field(10, 10);
        Lion restingLion = new Lion(false, new Location(0, 9));
        restingField.placeAnimal(restingLion, restingLion.getLocation());
        SimulationContext restingContext = new SimulationContext(10, 10);
        restingContext.startStep(8, restingField);
        completePipelineStep(restingField, restingContext);
        TestSupport.assertFalse("resting in place records no move event",
            hasEvent(restingContext, SimulationEvent.EventType.MOVE));
        BehaviorOutcome restOutcome = restingLion.getLastBehaviorOutcome();
        TestSupport.assertEquals("intentional rest displays rest", ActionType.REST,
                                 restOutcome.getDisplayAction());
        TestSupport.assertTrue("intentional rest records rest effect",
            restOutcome.getExecutedEffects().contains(ExecutedEffect.REST));
        TestSupport.assertTrue("intentional rest receives recovery credit",
                               restOutcome.receivesRestCredit());
        TestSupport.assertEquals("only intentional rest increments rest hours", 1,
                                 TestSupport.getIntField(restingLion,
                                                         "dailyRestHours"));

        Field sceneField = new Field(3, 3);
        Gazelle gazelle = new Gazelle(false, new Location(1, 2));
        sceneField.placeAnimal(gazelle, gazelle.getLocation());
        SimulationEvent move = SimulationEvent.move(1, gazelle,
            new Location(1, 1), new Location(1, 2));
        List<SceneDirector.Scene> scenes = SceneDirector.buildScene(
            java.util.List.of(move), sceneField, null, new java.awt.Rectangle(0, 0, 3, 3));
        TestSupport.assertEquals("move scene is built", 1, scenes.size());
        SceneDirector.SceneActor actor = scenes.get(0).actors.get(0);
        TestSupport.assertClose("move actor starts at from col",
                                1.0, actor.at(0).col, 0.01);
        TestSupport.assertClose("move actor ends at destination col",
                                2.0, actor.at(760).col, 0.01);

        SimulationEvent drink = SimulationEvent.drink(1, gazelle,
            gazelle.getLocation());
        List<SceneDirector.Scene> drinkScenes = SceneDirector.buildScene(
            java.util.List.of(drink), sceneField, null,
            new java.awt.Rectangle(0, 0, 3, 3));
        TestSupport.assertEquals("drink scene is built", 1, drinkScenes.size());
        TestSupport.assertEquals("drink scene keeps current species",
                                 SpeciesRegistry.GAZELLE,
                                 drinkScenes.get(0).actors.get(0).species);
        SceneDirector.SceneActor drinkActor = drinkScenes.get(0).actors.get(0);
        TestSupport.assertEquals("drink scene keeps living animal ID",
                                 Long.valueOf(gazelle.getId()),
                                 Long.valueOf(drinkActor.animalId));
        TestSupport.assertTrue("current living drink animation is retained",
            Math.abs(drinkActor.at(0).scale - drinkActor.at(450).scale) > 0.01);

        SimulationEvent graze = SimulationEvent.graze(2, gazelle,
            gazelle.getLocation());
        List<SceneDirector.Scene> grazeScenes = SceneDirector.buildScene(
            java.util.List.of(graze), sceneField, null,
            new java.awt.Rectangle(0, 0, 3, 3));
        TestSupport.assertEquals("graze scene keeps one current actor", 1,
                                 grazeScenes.size());
        SceneDirector.SceneActor grazeActor = grazeScenes.get(0).actors.get(0);
        TestSupport.assertTrue("current living graze animation is retained",
            Math.abs(grazeActor.at(0).row - grazeActor.at(360).row) > 0.01);
    }

    private static void testAmbientInspectActors()
    {
        Randomizer.reset();
        Field field = new Field(5, 5);
        AnimalIdentityAllocator ambientIdentities =
            new AnimalIdentityAllocator();
        for(int row = 0; row < 5; row++) {
            for(int col = 0; col < 5; col++) {
                Location location = new Location(row, col);
                SavannahAnimal animal = (row + col) % 3 == 0
                    ? new Lion(false, location, Randomizer.getRandom(),
                               ambientIdentities)
                    : new Gazelle(false, location, Randomizer.getRandom(),
                                  ambientIdentities);
                field.placeAnimal(animal, location);
            }
        }
        List<SceneDirector.SceneActor> actors = SceneDirector.buildAmbientActors(
            field, new java.awt.Rectangle(0, 0, 5, 5));
        TestSupport.assertEquals("ambient inspect shows all visible actors",
                                 25, actors.size());
        SceneDirector.Keyframe start = actors.get(0).at(0);
        SceneDirector.Keyframe middle = actors.get(0).at(1200);
        boolean moved = Math.abs(start.row - middle.row) > 0.01 ||
                        Math.abs(start.col - middle.col) > 0.01;
        TestSupport.assertTrue("ambient inspect actors are animated", moved);

        Field sparseField = new Field(5, 5);
        AnimalIdentityAllocator sparseIdentities =
            new AnimalIdentityAllocator();
        Gazelle inside = new Gazelle(false, new Location(0, 0),
            Randomizer.getRandom(), sparseIdentities);
        Zebra outside = new Zebra(false, new Location(4, 4),
            Randomizer.getRandom(), sparseIdentities);
        sparseField.placeAnimal(inside, inside.getLocation());
        sparseField.placeAnimal(outside, outside.getLocation());
        List<SceneDirector.SceneActor> sparseActors =
            SceneDirector.buildAmbientActors(sparseField,
                new java.awt.Rectangle(0, 0, 1, 1));
        TestSupport.assertEquals("inspect does not expand beyond viewport",
                                 1, sparseActors.size());

        Field observedField = new Field(3, 3);
        Gazelle mover = new Gazelle(false, new Location(1, 2));
        observedField.placeAnimal(mover, mover.getLocation());
        SimulationEvent move = SimulationEvent.move(7, mover,
            new Location(1, 1), new Location(1, 2));
        List<SceneDirector.SceneActor> observedActors =
            SceneDirector.buildBehaviorActors(java.util.List.of(move),
                observedField, new java.awt.Rectangle(0, 0, 3, 3));
        TestSupport.assertClose("behavior actor uses observed move start",
                                1.0, observedActors.get(0).at(0).col, 0.01);

        SimulationEvent graze = SimulationEvent.graze(8, mover,
            mover.getLocation());
        List<SceneDirector.SceneActor> grazeActors =
            SceneDirector.buildBehaviorActors(java.util.List.of(graze),
                observedField, new java.awt.Rectangle(0, 0, 3, 3));
        TestSupport.assertTrue("behavior actor renders observed grazing",
                               grazeActors.get(0).at(0).marker.equals("eat"));

        SimulationEvent sameStepMove = SimulationEvent.move(8, mover,
            new Location(1, 1), new Location(1, 2));
        List<SceneDirector.SceneActor> sameStepActors =
            SceneDirector.buildBehaviorActors(java.util.List.of(graze, sameStepMove),
                observedField, new java.awt.Rectangle(0, 0, 3, 3));
        TestSupport.assertTrue("grazing beats same-step movement in behavior view",
                               sameStepActors.get(0).at(0).marker.equals("eat"));
    }

    private static void testHistoricalActorsStayOutOfCurrentScenes()
    {
        Rectangle viewport = new Rectangle(0, 0, 3, 3);

        Field deadField = new Field(3, 3);
        Gazelle dead = new Gazelle(false, new Location(1, 1));
        deadField.placeAnimal(dead, dead.getLocation());
        TestSupport.invokePrivate(dead, "setDead", new Class<?>[0],
                                  new Object[0]);
        deadField.removeAnimal(dead);
        SimulationEvent deadMove = new SimulationEvent(
            SimulationEvent.EventType.MOVE, 2, 1, 1, 0, 0, dead.getId(), -1,
            SpeciesRegistry.GAZELLE, "");
        TestSupport.assertTrue("dead historical move creates no current scene",
            SceneDirector.buildScene(java.util.List.of(deadMove), deadField,
                                     null, viewport).isEmpty());

        Field currentField = new Field(5, 5);
        AnimalIdentityAllocator currentIdentities =
            new AnimalIdentityAllocator();
        Gazelle current = new Gazelle(false, new Location(1, 2),
            Randomizer.getRandom(), currentIdentities);
        currentField.placeAnimal(current, current.getLocation());
        SimulationEvent wrongSpecies = new SimulationEvent(
            SimulationEvent.EventType.MOVE, 3, 1, 2, 1, 1,
            current.getId(), -1, SpeciesRegistry.ZEBRA, "");
        TestSupport.assertTrue("species-mismatched event creates no current scene",
            SceneDirector.buildScene(java.util.List.of(wrongSpecies), currentField,
                                     null, viewport).isEmpty());

        SimulationEvent oldViewportMove = new SimulationEvent(
            SimulationEvent.EventType.MOVE, 4, 1, 1, 1, 0,
            current.getId(), -1, SpeciesRegistry.GAZELLE, "");
        currentField.removeAnimal(current);
        current.setLocation(new Location(4, 4));
        currentField.placeAnimal(current, current.getLocation());
        TestSupport.assertTrue("actor outside current viewport creates no scene",
            SceneDirector.buildScene(java.util.List.of(oldViewportMove), currentField,
                                     null, viewport).isEmpty());

        currentField.removeAnimal(current);
        current.setLocation(new Location(1, 1));
        currentField.placeAnimal(current, current.getLocation());
        Zebra target = new Zebra(false, new Location(1, 2),
            Randomizer.getRandom(), currentIdentities);
        currentField.placeAnimal(target, target.getLocation());
        SimulationEvent wrongTargetSpecies = new SimulationEvent(
            SimulationEvent.EventType.INFECTION, 4, 1, 2, 1, 1,
            current.getId(), target.getId(), SpeciesRegistry.GAZELLE,
            SpeciesRegistry.GAZELLE);
        TestSupport.assertTrue("target species mismatch creates no current scene",
            SceneDirector.buildScene(java.util.List.of(wrongTargetSpecies),
                                     currentField, null, viewport).isEmpty());

        currentField.removeAnimal(current);
        current.setLocation(new Location(1, 2));
        currentField.removeAnimal(target);
        currentField.placeAnimal(current, current.getLocation());
        SimulationEvent currentMove = SimulationEvent.move(5, current,
            new Location(1, 1), current.getLocation());
        List<SceneDirector.Scene> currentScenes = SceneDirector.buildScene(
            java.util.List.of(currentMove), currentField, null, viewport);
        TestSupport.assertEquals("current living move keeps one scene", 1,
                                 currentScenes.size());
        SceneDirector.SceneActor actor = currentScenes.get(0).actors.get(0);
        TestSupport.assertEquals("current scene keeps living animal ID",
                                 Long.valueOf(current.getId()),
                                 Long.valueOf(actor.animalId));
        TestSupport.assertEquals("current scene keeps current species",
                                 SpeciesRegistry.GAZELLE, actor.species);
        boolean moved = Math.abs(actor.at(0).row - actor.at(760).row) > 0.01 ||
                        Math.abs(actor.at(0).col - actor.at(760).col) > 0.01;
        TestSupport.assertTrue("current living move animation is retained", moved);
    }

    private static void testTerrainMapGeneration()
    {
        TerrainMap first = new TerrainMap(80, 120, 12345L);
        TerrainMap second = new TerrainMap(80, 120, 12345L);

        TestSupport.assertEquals("terrain depth matches field depth",
                                 80, first.getDepth());
        TestSupport.assertEquals("terrain width matches field width",
                                 120, first.getWidth());
        TestSupport.assertEquals("same seed gives same terrain counts",
                                 first.countTerrainTypes(), second.countTerrainTypes());
        TestSupport.assertEquals("same seed gives same sampled terrain",
                                 first.getTerrainAt(new Location(34, 38)),
                                 second.getTerrainAt(new Location(34, 38)));

        Map<TerrainType, Integer> counts = first.countTerrainTypes();
        for(TerrainType type : TerrainType.values()) {
            TestSupport.assertTrue(type.getDisplayName() + " terrain exists",
                                   counts.get(type) > 0);
        }
        TestSupport.assertEquals("terrain has exactly two separate water pools",
                                 2, countWaterBodies(first));
        TestSupport.assertTrue("two pools remain small relative to the map",
            counts.get(TerrainType.WATERHOLE) < (80 * 120 * 4) / 100);

        TestSupport.assertTrue("terrain lookup clamps outside locations",
                               first.getTerrainAt(new Location(-20, 999)) != null);
        TestSupport.assertTrue("null terrain lookup returns a default",
                               first.getTerrainAt(null) != null);

        SimulationContext context = new SimulationContext(17, 23);
        TestSupport.assertEquals("context terrain depth matches simulation",
                                 17, context.getTerrainMap().getDepth());
        TestSupport.assertEquals("context terrain width matches simulation",
                                 23, context.getTerrainMap().getWidth());
    }

    private static int countWaterBodies(TerrainMap terrain)
    {
        boolean[][] visited = new boolean[terrain.getDepth()][terrain.getWidth()];
        int bodies = 0;
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location start = new Location(row, col);
                if(visited[row][col] ||
                   terrain.getTerrainAt(start) != TerrainType.WATERHOLE) {
                    continue;
                }
                bodies++;
                java.util.ArrayDeque<Location> pending =
                    new java.util.ArrayDeque<>();
                pending.add(start);
                visited[row][col] = true;
                while(!pending.isEmpty()) {
                    Location current = pending.removeFirst();
                    for(int[] direction : directions) {
                        int nextRow = current.row() + direction[0];
                        int nextCol = current.col() + direction[1];
                        if(nextRow < 0 || nextRow >= terrain.getDepth() ||
                           nextCol < 0 || nextCol >= terrain.getWidth() ||
                           visited[nextRow][nextCol]) {
                            continue;
                        }
                        Location next = new Location(nextRow, nextCol);
                        if(terrain.getTerrainAt(next) == TerrainType.WATERHOLE) {
                            visited[nextRow][nextCol] = true;
                            pending.add(next);
                        }
                    }
                }
            }
        }
        return bodies;
    }

    private static void testWaterSafetyAudit()
    {
        Simulator simulator = new Simulator(SimulationConfig.default3x(), false);
        TestSupport.assertEquals("step 0 has no animals on water",
            0, WaterSafetyProbe.countLivingAnimalsOnWater(simulator.getField(),
                simulator.getContext().getTerrainMap()));
        TestSupport.assertEquals("step 0 has no visual actors on water",
            0, WaterSafetyProbe.countVisualActorSamplesOnWater(
                simulator.getField(), simulator.getContext().getTerrainMap(),
                simulator.getContext().getRecentEvents(), 200));
        TestSupport.assertEquals("step 0 has no ordinary visual markers on water",
            0, WaterSafetyProbe.countOrdinaryVisualSamplesOnWater(
                simulator.getField(), simulator.getContext().getTerrainMap()));
        TestSupport.assertEquals("step 0 shoreline markers remain visible",
            0, WaterSafetyProbe.countInvisibleShoreMarkers(
                simulator.getContext().getTerrainMap()));
        for(int index = 1; index <= 1000 && simulator.getField().isViable(); index++) {
            simulator.simulateOneStep();
            if(index % 100 == 0) {
                TestSupport.assertEquals("step " + index + " has no animals on water",
                    0, WaterSafetyProbe.countLivingAnimalsOnWater(simulator.getField(),
                        simulator.getContext().getTerrainMap()));
                TestSupport.assertEquals("step " + index + " has no visual actors on water",
                    0, WaterSafetyProbe.countVisualActorSamplesOnWater(
                        simulator.getField(), simulator.getContext().getTerrainMap(),
                        simulator.getContext().getRecentEvents(), 200));
                TestSupport.assertEquals("step " + index + " has no ordinary visual markers on water",
                    0, WaterSafetyProbe.countOrdinaryVisualSamplesOnWater(
                        simulator.getField(), simulator.getContext().getTerrainMap()));
            }
        }
    }

    private static void testVisualWaterMaskCoversShorelineFootprint()
    {
        TerrainMap terrain = new TerrainMap(80, 120, 12345L);
        VisualWaterMask mask = new VisualWaterMask(terrain, 1200, 800);
        boolean catchesShoreline = false;
        boolean allowsDryLand = false;
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location location = new Location(row, col);
                if(!terrain.isPassable(location)) {
                    continue;
                }
                Gazelle gazelle = new Gazelle(false, location);
                boolean touches =
                    mask.ordinaryAnimalFootprintTouchesWater(location, gazelle);
                catchesShoreline = catchesShoreline || touches;
                allowsDryLand = allowsDryLand || !touches;
            }
        }
        TestSupport.assertTrue("visual mask catches passable shoreline footprints",
                               catchesShoreline);
        TestSupport.assertTrue("visual mask still allows dry-land markers",
                               allowsDryLand);
    }

    private static void testFieldRendererAcceptsSharedGeometry()
    {
        VisualGridGeometry geometry = new VisualGridGeometry(5, 5, 50, 50);
        Gazelle gazelle = new Gazelle(false, new Location(2, 2));
        FieldRenderer renderer = new FieldRenderer(java.util.Map.of(
            SpeciesRegistry.GAZELLE,
            SpeciesRegistry.getProfile(SpeciesRegistry.GAZELLE).getColor()));
        java.awt.Rectangle bounds = VisualFootprint.ordinaryBounds(
            VisualFootprint.ordinaryMarker(gazelle.getLocation(), geometry),
            gazelle);
        TestSupport.assertTrue("field renderer is available",
                               renderer != null);
        TestSupport.assertTrue("ordinary footprint stays inside shared geometry",
                               bounds.x >= 0 && bounds.y >= 0 &&
                               bounds.x + bounds.width <= geometry.pixelWidth() &&
                               bounds.y + bounds.height <= geometry.pixelHeight());
    }

    private static void testOrdinaryAnimalLayerHasVisiblePixels()
    {
        VisualGridGeometry geometry = new VisualGridGeometry(5, 5, 100, 100);
        FieldRenderer renderer = new FieldRenderer(java.util.Map.of(
            SpeciesRegistry.LION, SpeciesRegistry.getProfile(SpeciesRegistry.LION).getColor(),
            SpeciesRegistry.CHEETAH, SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH).getColor(),
            SpeciesRegistry.ZEBRA, SpeciesRegistry.getProfile(SpeciesRegistry.ZEBRA).getColor(),
            SpeciesRegistry.BUFFALO, SpeciesRegistry.getProfile(SpeciesRegistry.BUFFALO).getColor(),
            SpeciesRegistry.GAZELLE, SpeciesRegistry.getProfile(SpeciesRegistry.GAZELLE).getColor()));
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
            100, 100, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        SavannahAnimal[] animals = {
            new Lion(false, new Location(0, 0), Randomizer.getRandom(), identities),
            new Cheetah(false, new Location(0, 1), Randomizer.getRandom(), identities),
            new Zebra(false, new Location(1, 0), Randomizer.getRandom(), identities),
            new Buffalo(false, new Location(1, 1), Randomizer.getRandom(), identities),
            new Gazelle(false, new Location(2, 2), Randomizer.getRandom(), identities)
        };
        for(SavannahAnimal animal : animals) {
            renderer.drawAnimal(graphics, animal.getLocation(), animal, geometry);
        }
        graphics.dispose();
        int visiblePixels = 0;
        for(int row = 0; row < image.getHeight(); row++) {
            for(int col = 0; col < image.getWidth(); col++) {
                if((image.getRGB(col, row) >>> 24) != 0) {
                    visiblePixels++;
                }
            }
        }
        TestSupport.assertTrue("ordinary animal layer has visible pixels",
                               visiblePixels > 0);
    }

    private static void testVisualGridGeometryMatchesTerrainCells()
    {
        VisualGridGeometry geometry = new VisualGridGeometry(240, 360,
                                                             1199, 803);
        int previousRight = 0;
        boolean horizontalContinuous = true;
        for(int col = 0; col < 360; col++) {
            java.awt.Rectangle cell = geometry.cellBounds(0, col);
            horizontalContinuous = horizontalContinuous &&
                                   previousRight == cell.x;
            previousRight = cell.x + cell.width;
        }
        TestSupport.assertTrue("geometry horizontal cells are continuous",
                               horizontalContinuous);
        TestSupport.assertEquals("geometry covers full pixel width",
                                 1199, previousRight);

        int previousBottom = 0;
        boolean verticalContinuous = true;
        for(int row = 0; row < 240; row++) {
            java.awt.Rectangle cell = geometry.cellBounds(row, 0);
            verticalContinuous = verticalContinuous &&
                                 previousBottom == cell.y;
            previousBottom = cell.y + cell.height;
        }
        TestSupport.assertTrue("geometry vertical cells are continuous",
                               verticalContinuous);
        TestSupport.assertEquals("geometry covers full pixel height",
                                 803, previousBottom);
    }

    private static void testOrdinaryFootprintUsesSharedGeometry()
    {
        TerrainMap terrain = new TerrainMap(80, 120, 12345L);
        VisualGridGeometry geometry = new VisualGridGeometry(80, 120,
                                                             1199, 803);
        VisualWaterMask mask = new VisualWaterMask(terrain, 1199, 803);
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location location = new Location(row, col);
                if(!terrain.isPassable(location)) {
                    continue;
                }
                Gazelle gazelle = new Gazelle(false, location);
                boolean direct = VisualFootprint.ordinaryTouchesWater(
                    location, gazelle, geometry, terrain);
                boolean viaMask = mask.ordinaryAnimalFootprintTouchesWater(
                    location, gazelle);
                TestSupport.assertEquals("ordinary mask delegates shared footprint",
                                         direct, viaMask);
                return;
            }
        }
    }

    private static void testInspectFootprintUsesSharedGeometry()
    {
        TerrainMap terrain = new TerrainMap(80, 120, 12345L);
        VisualGridGeometry geometry = new VisualGridGeometry(80, 120,
                                                             1199, 803);
        boolean foundWater = false;
        boolean foundLand = false;
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                boolean touches = VisualFootprint.inspectActorTouchesWater(
                    row, col, 1.0, terrain, geometry);
                if(terrain.isPassable(new Location(row, col))) {
                    foundLand = foundLand || !touches;
                }
                else {
                    foundWater = foundWater || touches;
                }
            }
        }
        TestSupport.assertTrue("inspect footprint detects visual water",
                               foundWater);
        TestSupport.assertTrue("inspect footprint allows safe land",
                               foundLand);
    }

    private static void testAnimalStatusLogRows()
    {
        Randomizer.reset();
        Field field = new Field(5, 5);
        Gazelle gazelle = new Gazelle(false, new Location(2, 2));
        field.placeAnimal(gazelle, gazelle.getLocation());
        SimulationContext context = new SimulationContext(5, 5);
        context.startStep(42, field);
        context.recordEvent(SimulationEvent.graze(42, gazelle,
                                                  gazelle.getLocation()));

        String[][] rows = AnimalStatusLog.rowsFor(gazelle, context, field);
        TestSupport.assertTrue("status log includes bilingual species",
            rowValue(rows, "Species / 物种").contains("Gazelle / 瞪羚"));
        TestSupport.assertTrue("status log includes bilingual stamina",
            rowValue(rows, "Stamina / 体力").contains("%"));
        TestSupport.assertTrue("status log includes translated recent event",
            rowValue(rows, "Recent activity / 最近行为").contains("进食"));
        TestSupport.assertTrue("status log shows thirst disabled by default",
            rowValue(rows, "Thirst system / 口渴系统").contains("未启用"));
        TestSupport.assertTrue("status log exposes display outcome",
            rowValue(rows, "Display outcome / 展示结果").contains("尚无行为决策"));
    }

    private static void testThirstSystemDefaultsOff()
    {
        SimulationConfig config = SimulationConfig.default3x();
        TestSupport.assertFalse("thirst system defaults off",
                                config.isThirstEnabled());
        SimulationContext context = new SimulationContext(10, 10, config);
        TestSupport.assertFalse("context does not create thirst system by default",
                                context.isThirstEnabled());
        TestSupport.assertTrue("config summary records thirst off",
                               config.describe().contains("thirst=off"));
    }

    private static void testHydrationPolicyDefaults()
    {
        HydrationPolicy disabled = SimulationConfig.default3x().getHydrationPolicy();
        TestSupport.assertFalse("default hydration policy is disabled",
                                disabled.isEnabled());
        HydrationPolicy policy = SimulationConfig.builder().thirstEnabled(true)
            .build().getHydrationPolicy();
        TestSupport.assertTrue("enabled config carries hydration policy",
                               policy.isEnabled());
        TestSupport.assertClose("policy carries the calibrated base drain",
                                EcologyParameters.current().get(
                                    EcologyParameters.Key.HYDRATION_BASE_DRAIN),
                                policy.getBaseDrain(), 0.0001);
        TestSupport.assertClose("policy preserves W4 critical reserve", 12.0,
                                policy.getCriticalReserve(), 0.0001);
        TestSupport.assertEquals("policy preserves W4 dehydration grace", 18,
            policy.getDehydrationGraceSteps());
        Gazelle gazelle = new Gazelle(false, new Location(0, 0));
        TestSupport.assertClose("default need profile uses the calibrated base drain",
                                EcologyParameters.current().get(
                                    EcologyParameters.Key.HYDRATION_BASE_DRAIN),
            policy.baseDrainFor(gazelle), 0.0001);
        TestSupport.assertEquals("default keeps explicit water-food secondary",
            WaterFoodSecondaryPolicy.LEGACY_EXPLICIT,
            SimulationConfig.builder().thirstEnabled(true).build()
                .getWaterFoodSecondaryPolicy());
        TestSupport.assertEquals("config preserves water-only policy",
            WaterFoodSecondaryPolicy.WATER_ONLY,
            SimulationConfig.builder().thirstEnabled(true)
                .waterFoodSecondaryPolicy(WaterFoodSecondaryPolicy.WATER_ONLY)
                .build().getWaterFoodSecondaryPolicy());
    }

    private static void testThirstSystemDrainsAndDrinksFromShore()
    {
        SimulationConfig config = SimulationConfig.builder()
            .mapScale(1)
            .thirstEnabled(true)
            .build();
        SimulationContext context = new SimulationContext(40, 60, config);
        Field field = new Field(40, 60);
        Location shore = firstDrinkableShore(context.getThirstSystem(),
                                            context.getTerrainMap());
        TestSupport.assertTrue("test map has drinkable shore", shore != null);

        Gazelle gazelle = new Gazelle(false, shore);
        field.placeAnimal(gazelle, shore);
        context.startStep(3, field);
        TestSupport.setHydration(gazelle, 30.0);
        double beforeDrain = gazelle.getHydrationPercent();
        context.getThirstSystem().applyThirst(gazelle, context);
        TestSupport.assertTrue("enabled thirst drains hydration",
                               gazelle.getHydrationPercent() < beforeDrain);
        double beforeDrink = gazelle.getHydrationPercent();
        TestSupport.assertTrue("shore animal drinks",
                               context.getThirstSystem().tryDrink(gazelle,
                                                                  context));
        // Plan v3.0 M2-2: one service step restores a fixed share.
        TestSupport.assertClose("drinking restores one service step of hydration",
                                beforeDrink + EcologyParameters.defaults()
                                    .get(EcologyParameters.Key.DRINK_PERCENT_PER_STEP),
                                gazelle.getHydrationPercent(), 0.001);
        TestSupport.assertTrue("drinking records event",
            hasEvent(context, SimulationEvent.EventType.DRINK));

        TestSupport.setHydration(gazelle, 30.0);
        BehaviorIntent drinkIntent = context.getThirstBehaviorProvider().propose(
            gazelle, TerrainNavigator.freeAdjacent(field, context.getTerrainMap(),
                                                   shore, context.getRandom()), context);
        TestSupport.assertEquals("thirst provider proposes drink on shore",
                                 ActionType.DRINK, drinkIntent.getActionType());
        TestSupport.assertTrue("action executor completes drink intent",
            context.getActionExecutor().executeImmediate(drinkIntent, gazelle, context));

        Location nonShore = firstNonShorePassable(context.getThirstSystem(),
            context.getTerrainMap());
        TestSupport.assertTrue("test map has non-shore passable cell",
                               nonShore != null);
        Gazelle seeker = new Gazelle(false, nonShore);
        TestSupport.setHydration(seeker, 50.0);
        Location next = context.getThirstSystem().stepTowardWater(seeker,
            TerrainNavigator.freeAdjacent(field, context.getTerrainMap(),
                                          nonShore, context.getRandom()));
        TestSupport.assertTrue("thirst system can choose a waterward step",
                               next != null);
        BehaviorIntent seekIntent = context.getThirstBehaviorProvider().propose(
            seeker, TerrainNavigator.freeAdjacent(field, context.getTerrainMap(),
                                                  nonShore, context.getRandom()), context);
        TestSupport.assertEquals("thirst provider proposes seek-water off shore",
                                 ActionType.SEEK_WATER, seekIntent.getActionType());
        TestSupport.assertTrue("seek-water intent keeps a waterward destination",
                               seekIntent.getDestination() != null);
    }

    private static void testDeterministicDehydrationGrace()
    {
        SimulationConfig config = SimulationConfig.builder().mapScale(1)
            .thirstEnabled(true).build();

        SimulationContext graceContext = new SimulationContext(40, 60, config);
        Field graceField = new Field(40, 60);
        Location nonShore = firstNonShorePassable(graceContext.getThirstSystem(),
            graceContext.getTerrainMap());
        Gazelle graceAnimal = new Gazelle(false, nonShore);
        graceField.placeAnimal(graceAnimal, nonShore);
        graceContext.startStep(1, graceField);
        TestSupport.setHydration(graceAnimal, 0.0);
        TestSupport.setDehydrationSteps(graceAnimal,
            SavannahAnimal.DEHYDRATION_CAUSE_STEPS - 2);
        graceContext.getThirstSystem().applyThirst(graceAnimal, graceContext);
        TestSupport.assertTrue("grace minus one stays alive", graceAnimal.isAlive());
        TestSupport.assertEquals("grace minus one advances zero-water clock",
            SavannahAnimal.DEHYDRATION_CAUSE_STEPS - 1,
            graceAnimal.getDehydrationSteps());

        SimulationContext deathContext = new SimulationContext(40, 60, config);
        Field deathField = new Field(40, 60);
        Location deathLocation = firstNonShorePassable(deathContext.getThirstSystem(),
            deathContext.getTerrainMap());
        Gazelle dehydrated = new Gazelle(false, deathLocation);
        deathField.placeAnimal(dehydrated, deathLocation);
        deathContext.startStep(2, deathField);
        TestSupport.setHydration(dehydrated, 0.0);
        TestSupport.setDehydrationSteps(dehydrated,
            SavannahAnimal.DEHYDRATION_CAUSE_STEPS - 1);
        deathContext.getThirstSystem().applyThirst(dehydrated, deathContext);
        TestSupport.assertFalse("grace boundary kills off shore", dehydrated.isAlive());
        TestSupport.assertEquals("zero-water exhaustion records dehydration",
            DeathCause.DEHYDRATION, dehydrated.getDeathCause());
        TestSupport.assertEquals("dehydration is counted independently", 1,
            deathContext.getDeathCount(DeathCause.DEHYDRATION));
        deathContext.getThirstSystem().applyThirst(dehydrated, deathContext);
        TestSupport.assertEquals("dehydration death records once", 1,
            deathContext.getDeathCount(DeathCause.DEHYDRATION));
        TestSupport.assertTrue("death summary names dehydration",
            deathContext.getDeathSummary().contains("DEHYDRATION=1"));

        SimulationContext shoreContext = new SimulationContext(40, 60, config);
        Field shoreField = new Field(40, 60);
        Location shore = firstDrinkableShore(shoreContext.getThirstSystem(),
            shoreContext.getTerrainMap());
        Gazelle shoreAnimal = new Gazelle(false, shore);
        shoreField.placeAnimal(shoreAnimal, shore);
        shoreContext.startStep(3, shoreField);
        TestSupport.setHydration(shoreAnimal, 0.0);
        TestSupport.setDehydrationSteps(shoreAnimal,
            SavannahAnimal.DEHYDRATION_CAUSE_STEPS - 1);
        shoreContext.getThirstSystem().applyThirst(shoreAnimal, shoreContext);
        TestSupport.assertTrue("shore drink survives grace boundary", shoreAnimal.isAlive());
        TestSupport.assertClose("shore drink restores one service step",
            EcologyParameters.defaults().get(
                EcologyParameters.Key.DRINK_PERCENT_PER_STEP),
            shoreAnimal.getHydrationPercent(), 0.001);
        TestSupport.assertEquals("shore drink resets zero-water clock", 0,
            shoreAnimal.getDehydrationSteps());

        SimulationContext priorityContext = new SimulationContext(40, 60, config);
        Field priorityCurrent = new Field(40, 60);
        Location priorityLocation = firstNonShorePassable(
            priorityContext.getThirstSystem(), priorityContext.getTerrainMap());
        Gazelle priorityAnimal = new Gazelle(false, priorityLocation);
        priorityCurrent.placeAnimal(priorityAnimal, priorityLocation);
        priorityContext.startStep(4, priorityCurrent);
        TestSupport.setHydration(priorityAnimal, 0.0);
        TestSupport.setDehydrationSteps(priorityAnimal,
            SavannahAnimal.DEHYDRATION_CAUSE_STEPS - 1);
        TestSupport.setField(priorityAnimal, "foodLevel", 0);
        TestSupport.setField(priorityAnimal, "starvingSteps", 2);
        Field priorityCommitted = completePipelineStep(priorityCurrent, priorityContext);
        TestSupport.assertEquals("dehydration precedes starvation at boundary",
            DeathCause.DEHYDRATION, priorityAnimal.getDeathCause());
        TestSupport.assertTrue("dehydration step performs no behavior",
            priorityAnimal.getLastBehaviorOutcome() == null);
        TestSupport.assertEquals("pipeline dehydration commit removes terminal animal", 0,
            priorityCommitted.getLivingPopulation());
    }

    private static void testW1BehaviorOutcomes()
    {
        SimulationConfig thirstConfig = SimulationConfig.builder().mapScale(1)
            .thirstEnabled(true).build();
        SimulationContext drinkContext = new SimulationContext(40, 60, thirstConfig);
        Location shore = firstDrinkableShore(drinkContext.getThirstSystem(),
                                             drinkContext.getTerrainMap());
        Field drinkCurrent = new Field(40, 60);
        Gazelle drinker = new Gazelle(false, shore);
        TestSupport.setHydration(drinker, 30.0);
        drinkCurrent.placeAnimal(drinker, shore);
        drinkContext.startStep(8, drinkCurrent);
        Field drinkCommitted = completePipelineStep(drinkCurrent, drinkContext);
        BehaviorOutcome drinkOutcome = drinker.getLastBehaviorOutcome();
        TestSupport.assertEquals("shore drink keeps water as primary intent",
            ActionType.DRINK, drinkOutcome.getPrimaryIntent().getActionType());
        TestSupport.assertTrue("shore drink records drink effect",
            drinkOutcome.getExecutedEffects().contains(ExecutedEffect.DRINK));
        TestSupport.assertEquals("shore drink displays drink", ActionType.DRINK,
                                 drinkOutcome.getDisplayAction());
        TestSupport.assertFalse("shore drink receives no rest credit",
                                drinkOutcome.receivesRestCredit());
        TestSupport.assertEquals("shore drink does not increment rest hours", 0,
            TestSupport.getIntField(drinker, "dailyRestHours"));
        RenderAnimalSnapshot drinkFact = RenderFrame.capture(8, drinkCommitted,
            drinkContext, 300, 200).animalById(drinker.getId());
        TestSupport.assertEquals("render fact keeps primary intent separate",
            ActionType.DRINK, drinkFact.primaryIntentAction);
        TestSupport.assertTrue("render fact keeps ordered effects",
            drinkFact.executedEffects.contains(ExecutedEffect.DRINK));
        TestSupport.assertEquals("render fact keeps display outcome", ActionType.DRINK,
                                 drinkFact.actionType);

        SimulationContext huntContext = new SimulationContext(30, 30);
        Location huntOrigin = firstPassableInterior(huntContext.getTerrainMap());
        Field huntCurrent = new Field(30, 30);
        Lion hunter = new Lion(false, huntOrigin);
        huntCurrent.placeAnimal(hunter, huntOrigin);
        huntContext.startStep(0, huntCurrent);
        completePipelineStep(huntCurrent, huntContext);
        BehaviorOutcome failedHunt = hunter.getLastBehaviorOutcome();
        TestSupport.assertEquals("failed hunt retains hunt primary intent",
            ActionType.HUNT, failedHunt.getPrimaryIntent().getActionType());
        TestSupport.assertFalse("failed hunt has no hunt effect",
            failedHunt.getExecutedEffects().contains(ExecutedEffect.HUNT));
        TestSupport.assertTrue("failed hunt can complete a move",
            failedHunt.getExecutedEffects().contains(ExecutedEffect.MOVE));
        TestSupport.assertEquals("failed hunt then move displays move", ActionType.MOVE,
                                 failedHunt.getDisplayAction());

        SimulationContext waitContext = new SimulationContext(30, 30);
        Location waitOrigin = firstPassableInterior(waitContext.getTerrainMap());
        Field waitCurrent = new Field(30, 30);
        AnimalIdentityAllocator waitIdentities =
            new AnimalIdentityAllocator();
        Lion blockedHunter = new Lion(false, waitOrigin,
            Randomizer.getRandom(), waitIdentities);
        waitCurrent.placeAnimal(blockedHunter, waitOrigin);
        waitContext.startStep(0, waitCurrent);
        reservePassableNeighbors(waitContext, waitContext.getTerrainMap(), waitOrigin,
                                 waitIdentities);
        Field waitCommitted = completePipelineStep(waitCurrent, waitContext);
        BehaviorOutcome waitOutcome = blockedHunter.getLastBehaviorOutcome();
        TestSupport.assertTrue("occupied exits record wait effect",
            waitOutcome.getExecutedEffects().contains(ExecutedEffect.WAIT));
        TestSupport.assertEquals("occupied exits display wait", ActionType.WAIT,
                                 waitOutcome.getDisplayAction());
        TestSupport.assertFalse("occupied exits receive no rest credit",
                                waitOutcome.receivesRestCredit());
        TestSupport.assertEquals("waiting does not increment rest hours", 0,
            TestSupport.getIntField(blockedHunter, "dailyRestHours"));
        TestSupport.assertEquals("pipeline wait keeps the blocked hunter", 1,
            waitCommitted.getLivingPopulation());
    }

    private static void testFoodBehaviorProvider()
    {
        SimulationContext context = new SimulationContext(3, 3);
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Gazelle gazelle = new Gazelle(false, new Location(1, 1),
            Randomizer.getRandom(), identities);
        Lion lion = new Lion(false, new Location(1, 1),
            Randomizer.getRandom(), identities);
        TestSupport.setField(gazelle, "foodLevel", 1);
        TestSupport.setField(lion, "foodLevel", 1);

        BehaviorIntent graze = context.getFoodBehaviorProvider().propose(gazelle,
            java.util.Collections.<Location>emptyList(), context);
        TestSupport.assertEquals("food provider proposes graze for herbivore",
                                 ActionType.GRAZE, graze.getActionType());
        TestSupport.assertEquals("critical herbivore food gets critical reason",
                                 BehaviorReason.CRITICAL_FOOD, graze.getReason());

        BehaviorIntent hunt = context.getFoodBehaviorProvider().propose(lion,
            java.util.Collections.<Location>emptyList(), context);
        TestSupport.assertEquals("food provider proposes hunt for predator",
                                 ActionType.HUNT, hunt.getActionType());
        TestSupport.assertEquals("critical predator food gets critical reason",
                                 BehaviorReason.CRITICAL_FOOD, hunt.getReason());
    }

    private static Location firstDrinkableShore(ThirstSystem thirst,
                                                TerrainMap terrain)
    {
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location location = new Location(row, col);
                if(thirst.isDrinkableShore(location)) {
                    return location;
                }
            }
        }
        return null;
    }

    private static Location firstNonShorePassable(ThirstSystem thirst,
                                                  TerrainMap terrain)
    {
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location location = new Location(row, col);
                if(terrain.isPassable(location) &&
                   !thirst.isDrinkableShore(location)) {
                    return location;
                }
            }
        }
        return null;
    }

    private static Location firstPassableInterior(TerrainMap terrain)
    {
        for(int row = 1; row < terrain.getDepth() - 1; row++) {
            for(int col = 1; col < terrain.getWidth() - 1; col++) {
                Location location = new Location(row, col);
                if(terrain.isPassable(location)) {
                    return location;
                }
            }
        }
        throw new IllegalStateException("test terrain has no passable interior cell");
    }

    private static void reservePassableNeighbors(SimulationContext context,
                                                 TerrainMap terrain,
                                                 Location origin,
                                                 AnimalIdentityAllocator identities)
    {
        int ownerColumn = 0;
        for(int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            for(int colOffset = -1; colOffset <= 1; colOffset++) {
                if(rowOffset == 0 && colOffset == 0) {
                    continue;
                }
                Location candidate = new Location(origin.row() + rowOffset,
                                                   origin.col() + colOffset);
                if(terrain.isPassable(candidate)) {
                    Location ownerOrigin = new Location(terrain.getDepth() - 1,
                        ownerColumn++);
                    Gazelle owner = new Gazelle(false, ownerOrigin,
                        Randomizer.getRandom(), identities);
                    if(!context.reserveCriticalWaterMove(owner, candidate)) {
                        throw new IllegalStateException(
                            "could not reserve controlled wait candidate " + candidate);
                    }
                }
            }
        }
    }

    private static void testTerminalDiagnostics()
    {
        Field field = new Field(5, 5);
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Gazelle gazelle = new Gazelle(false, new Location(1, 1),
            Randomizer.getRandom(), identities);
        Lion lion = new Lion(false, new Location(2, 2),
            Randomizer.getRandom(), identities);
        field.placeAnimal(gazelle, gazelle.getLocation());
        field.placeAnimal(lion, lion.getLocation());
        SimulationContext context = new SimulationContext(5, 5);
        context.startStep(0, field);

        SimulationDiagnostics.DiagnosticSnapshot first =
            SimulationDiagnostics.capture(field, context);
        String firstLine = SimulationDiagnostics.toConsoleLine(0, first, null);
        TestSupport.assertTrue("terminal diagnostics include trend",
                               firstLine.contains("Trend: baseline"));
        TestSupport.assertTrue("terminal diagnostics include signals",
                               firstLine.contains("Signals:"));
        TestSupport.assertTrue("terminal diagnostics include event",
                               firstLine.contains("Event:"));
        TestSupport.assertTrue("terminal diagnostics include death totals",
                               firstLine.contains("Deaths=none"));

        Buffalo buffalo = new Buffalo(false, new Location(3, 3),
            Randomizer.getRandom(), identities);
        field.placeAnimal(buffalo, buffalo.getLocation());
        context.startStep(100, field);
        SimulationDiagnostics.DiagnosticSnapshot second =
            SimulationDiagnostics.capture(field, context);
        String secondLine = SimulationDiagnostics.toConsoleLine(100, second, first);
        TestSupport.assertTrue("terminal diagnostics show population trend",
                               secondLine.contains("pop +1"));
        TestSupport.assertTrue("terminal diagnostics keep event readout",
                               secondLine.contains("Event:"));
    }

    private static void testSimulationConfigScaling()
    {
        SimulationConfig config = new SimulationConfig(3, 0.20, 0.80, 0.70,
                                                       1.50, 2.00);
        TestSupport.assertEquals("map scale adjusts width",
                                 360, config.getScaledWidth());
        TestSupport.assertEquals("map scale adjusts depth",
                                 240, config.getScaledDepth());
        TestSupport.assertEquals("default random seed is exposed",
                                 SimulationConfig.DEFAULT_RANDOM_SEED,
                                 config.getRandomSeed());
        TestSupport.assertClose("creation multiplier scales profile",
            SpeciesRegistry.getProfile(SpeciesRegistry.GAZELLE).getCreationProbability() * 0.20,
            config.creationProbability(SpeciesRegistry.getProfile(SpeciesRegistry.GAZELLE)),
            0.00001);

        Simulator simulator = new Simulator(config, false);
        TestSupport.assertEquals("configured simulator width",
                                 360, simulator.getField().getWidth());
        TestSupport.assertEquals("configured simulator depth",
                                 240, simulator.getField().getDepth());
        TestSupport.assertEquals("context uses configured terrain seed",
                                 config.getTerrainSeed(),
                                 simulator.getContext().getTerrainMap().getSeed());

        SimulationConfig target3x = SimulationConfig.target3xBalanced();
        TestSupport.assertEquals("target 3x config scales width",
                                 360, target3x.getScaledWidth());
        TestSupport.assertTrue("target 3x boosts lion creation versus area scale",
            target3x.creationProbability(SpeciesRegistry.getProfile(SpeciesRegistry.LION)) >
            new SimulationConfig(3, 1.0 / 9.0, 1.0, 1.0, 1.0, 1.0)
                .creationProbability(SpeciesRegistry.getProfile(SpeciesRegistry.LION)));
        TestSupport.assertTrue("target 3x suppresses zebra creation versus area scale",
            target3x.creationProbability(SpeciesRegistry.getProfile(SpeciesRegistry.ZEBRA)) <
            new SimulationConfig(3, 1.0 / 9.0, 1.0, 1.0, 1.0, 1.0)
                .creationProbability(SpeciesRegistry.getProfile(SpeciesRegistry.ZEBRA)));
        TestSupport.assertTrue("target 3x suppresses prey breeding",
            target3x.breedingProbability(SpeciesRegistry.getProfile(SpeciesRegistry.GAZELLE)) <
            SpeciesRegistry.getProfile(SpeciesRegistry.GAZELLE).getBreedingProbability());
    }

    private static void testDiseaseMultiplierPressure()
    {
        int baseline = predationTransmissions(new SimulationConfig());
        int amplified = predationTransmissions(new SimulationConfig(1, 1.0, 1.0,
            1.0, 8.0, 1.0));
        TestSupport.assertTrue("disease multiplier increases transmission pressure",
                               amplified >= baseline);
    }

    private static int predationTransmissions(SimulationConfig config)
    {
        Randomizer.reset();
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        SimulationContext context = new SimulationContext(3, 3, config);
        Field field = new Field(3, 3);
        context.startStep(0, field);
        SavannahDiseaseSystem disease = new SavannahDiseaseSystem();
        int transmitted = 0;
        for(int attempt = 0; attempt < 80; attempt++) {
            Gazelle prey = new Gazelle(false, new Location(1, 1),
                Randomizer.getRandom(), identities);
            Lion predator = new Lion(false, new Location(1, 2),
                Randomizer.getRandom(), identities);
            prey.infect(1.0);
            disease.afterPredation(prey, predator, context);
            if(predator.isInfected()) {
                transmitted++;
            }
        }
        return transmitted;
    }

    private static boolean hasEvent(SimulationContext context,
                                    SimulationEvent.EventType type)
    {
        for(SimulationEvent event : context.getCurrentEvents()) {
            if(event.type == type) {
                return true;
            }
        }
        return false;
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

    private static String rowValue(String[][] rows, String metric)
    {
        for(String[] row : rows) {
            if(row.length >= 2 && metric.equals(row[0])) {
                return row[1];
            }
        }
        return "";
    }

    private static void testViewportInteractionControls()
    {
        ViewInteractionController.SizeProvider sizeProvider =
            new ViewInteractionController.SizeProvider() {
                public int viewWidth() { return 400; }
                public int viewHeight() { return 300; }
                public int contentWidth() { return 800; }
                public int contentHeight() { return 600; }
            };

        ViewInteractionController keyboard = new ViewInteractionController(
            InteractionMode.KEYBOARD_ONLY, InputBinding.defaultBinding());
        final int[] viewportChanges = { 0 };
        keyboard.addViewportChangeListener(
            new ViewInteractionController.ViewportChangeListener() {
                public void viewportChanged()
                {
                    viewportChanges[0]++;
                }
            });
        boolean zoomed = false;
        for(int index = 0; index < 6; index++) {
            zoomed = keyboard.handleKey(java.awt.event.KeyEvent.VK_Q, 400,
                                        300, sizeProvider) || zoomed;
        }
        TestSupport.assertTrue("keyboard mode handles zoom key", zoomed);
        TestSupport.assertTrue("keyboard zoom reaches inspect threshold",
                               keyboard.isInspectMode());
        TestSupport.assertTrue("viewport listener observes zoom",
                               viewportChanges[0] > 0);

        ViewInteractionController mouseOnly = new ViewInteractionController(
            InteractionMode.MOUSE_ONLY, InputBinding.defaultBinding());
        boolean handled = mouseOnly.handleKey(java.awt.event.KeyEvent.VK_Q, 400,
                                             300, sizeProvider);
        TestSupport.assertFalse("mouse-only mode ignores keyboard", handled);

        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("zoomIn", "not-a-key");
        InputBinding binding = InputBinding.fromProperties(properties);
        TestSupport.assertEquals("invalid key binding falls back to Q",
                                 java.awt.event.KeyEvent.VK_Q,
                                 binding.getZoomInKey());

        ViewInteractionController hybrid = new ViewInteractionController(
            InteractionMode.HYBRID, binding);
        double beforePan = hybrid.getTransform().getOffsetX();
        hybrid.handleKey(java.awt.event.KeyEvent.VK_Q, 400, 300, sizeProvider);
        hybrid.handleKey(java.awt.event.KeyEvent.VK_D, 400, 300, sizeProvider);
        TestSupport.assertTrue("hybrid mode updates viewport only",
                               hybrid.getTransform().getOffsetX() != beforePan ||
                               hybrid.getTransform().getZoom() > 1.0);
    }

    private static boolean listRejectsMutation(List<AnimalSnapshot> snapshots)
    {
        try {
            snapshots.add(null);
            return false;
        }
        catch(UnsupportedOperationException e) {
            return true;
        }
    }

    /** Runs one fixed-order R2 proposal and the single production commit. */
    private static Field completePipelineStep(Field currentField,
                                              SimulationContext context)
    {
        PositionStepCoordinator positions = new PositionStepCoordinator(currentField, context);
        PositionAvailabilityView availability = positions.createAvailabilityView();
        List<Animal> actors = new java.util.ArrayList<>(currentField.getAnimals());
        actors.sort((first, second) -> Long.compare(first.getId(), second.getId()));
        for(Animal actor : actors) {
            positions.propose(actor, availability);
        }
        return positions.resolveAndCommitCompleteStep();
    }

    private static TestAnimal createTestAnimal(Sex sex, Location location)
    {
        return createTestAnimal(sex, location,
                                new AnimalIdentityAllocator());
    }

    private static TestAnimal createTestAnimal(Sex sex, Location location,
                                               AnimalIdentityAllocator identities)
    {
        for(int index = 0; index < 100; index++) {
            TestAnimal animal = new TestAnimal(false, location, identities);
            if(animal.getSex() == sex) {
                TestSupport.setField(animal, "age", TEST_PROFILE.getAdultAge());
                TestSupport.setField(animal, "foodLevel", TEST_PROFILE.getMaxFoodLevel());
                TestSupport.setField(animal, "stamina", 100.0);
                return animal;
            }
        }
        throw new IllegalStateException("Could not create test animal with sex " + sex);
    }

    private static TestAnimal breedingAnimal(Sex sex, Location location,
                                             AnimalIdentityAllocator identities)
    {
        return createTestAnimal(sex, location, identities);
    }

    private static SimulationContext breedingContext()
    {
        SimulationContext context = new SimulationContext(24, 24);
        TestSupport.setField(context, "breedingSystem",
            new MateFindingBreedingSystem(new java.util.Random(1L) {
                @Override
                public double nextDouble()
                {
                    return 0.0;
                }

                @Override
                public int nextInt(int bound)
                {
                    return 0;
                }
            }));
        return context;
    }

    private static final SpeciesProfile TEST_PROFILE =
        new BasicSpeciesProfile("TestAntelope", java.awt.Color.green, false,
            Set.of(), Set.of(DayPhase.DAY, DayPhase.DAWN, DayPhase.DUSK, DayPhase.NIGHT),
            0.0, 2, 1, 2, 2, 100,
            1.0, 1, 2, 1, 50,
            20, 0.0, 0.80, 0.10, 0.30, 0.20, 10);

    private static class TestAnimal extends SavannahAnimal
    {
        TestAnimal(boolean randomAge, Location location)
        {
            super(TEST_PROFILE, randomAge, location);
        }

        TestAnimal(boolean randomAge, Location location,
                   AnimalIdentityAllocator identities)
        {
            super(TEST_PROFILE, randomAge, location, Randomizer.getRandom(),
                  identities);
        }

        public SavannahAnimal createChild(Location location)
        {
            return new TestAnimal(false, location, getIdentityAllocator());
        }
    }
}
