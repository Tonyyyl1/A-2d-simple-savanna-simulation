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
        testBreedingRequiresMate();
        testSimulationEventBus();
        testEventListenerReceivesRecordedEvents();
        testEventListenerSurvivesSimulatorReset();
        testEventAccumulatorOutlivesRecentEventWindow();
        testEventAccumulatorCountsLocationsByType();
        testEventAccumulatorClearResetsCounts();
        testSimulationSnapshotIsImmutable();
        testDiseaseLifecycleEvents();
        testMovementEventsAndScenes();
        testAmbientInspectActors();
        testTerrainMapGeneration();
        testWaterSafetyAudit();
        testVisualGridGeometryMatchesTerrainCells();
        testOrdinaryFootprintUsesSharedGeometry();
        testInspectFootprintUsesSharedGeometry();
        testVisualWaterMaskCoversShorelineFootprint();
        testFieldRendererAcceptsSharedGeometry();
        testAnimalStatusLogRows();
        testThirstSystemDefaultsOff();
        testThirstSystemDrainsAndDrinksFromShore();
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

    private static void testGrassFeeding()
    {
        Randomizer.reset();
        Field field = new Field(3, 3);
        Gazelle gazelle = new Gazelle(false, new Location(1, 1));
        TestSupport.setField(gazelle, "foodLevel", 1);
        field.placeAnimal(gazelle, gazelle.getLocation());
        SimulationContext context = new SimulationContext(3, 3);
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
        SimulationContext context = new SimulationContext(3, 3);
        Field field = new Field(3, 3);
        context.startStep(0, field);
        SavannahDiseaseSystem disease = new SavannahDiseaseSystem();
        boolean transmitted = false;
        for(int attempt = 0; attempt < 80 && !transmitted; attempt++) {
            Gazelle prey = new Gazelle(false, new Location(1, 1));
            Lion predator = new Lion(false, new Location(1, 2));
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
        Lion predator = new Lion(false, new Location(1, 1));
        Gazelle prey = new Gazelle(false, new Location(1, 2));

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

    private static void testBreedingRequiresMate()
    {
        Randomizer.reset();
        MateFindingBreedingSystem breeding = new MateFindingBreedingSystem();
        TestAnimal female = createTestAnimal(Sex.FEMALE, new Location(2, 2));
        TestAnimal male = createTestAnimal(Sex.MALE, new Location(2, 3));
        Field currentField = new Field(5, 5);
        currentField.placeAnimal(female, female.getLocation());
        SimulationContext context = new SimulationContext(5, 5);
        context.startStep(0, currentField);

        Field noMateNext = new Field(5, 5);
        breeding.tryBreed(female, currentField, noMateNext, context,
                          noMateNext.getFreeAdjacentLocations(female.getLocation()));
        TestSupport.assertEquals("female without mate has no child",
                                 0, noMateNext.getLivingPopulation());

        currentField.placeAnimal(male, male.getLocation());
        context.startStep(1, currentField);
        Field mateNext = new Field(5, 5);
        breeding.tryBreed(female, currentField, mateNext, context,
                          mateNext.getFreeAdjacentLocations(female.getLocation()));
        TestSupport.assertTrue("female with adult male nearby can breed",
                               mateNext.getLivingPopulation() > 0);
        TestSupport.assertTrue("birth records event",
            hasEvent(context, SimulationEvent.EventType.BIRTH));
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

        Gazelle gazelle = new Gazelle(false, new Location(0, 0));
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
        Gazelle gazelle = new Gazelle(false, new Location(1, 1));
        Lion lion = new Lion(false, new Location(2, 2));

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
        TestSupport.assertTrue("disease death records event",
            hasEvent(deathContext, SimulationEvent.EventType.DISEASE_DEATH));
    }

    private static void testMovementEventsAndScenes()
    {
        Randomizer.reset();
        Field currentField = new Field(10, 10);
        TestAnimal mover = createTestAnimal(Sex.FEMALE, new Location(0, 9));
        currentField.placeAnimal(mover, mover.getLocation());
        SimulationContext context = new SimulationContext(10, 10);
        context.startStep(8, currentField);
        Field nextField = new Field(10, 10);
        mover.act(currentField, nextField, context);
        TestSupport.assertTrue("real movement records move event",
            hasEvent(context, SimulationEvent.EventType.MOVE));

        Randomizer.reset();
        Field restingField = new Field(10, 10);
        Lion restingLion = new Lion(false, new Location(0, 9));
        restingField.placeAnimal(restingLion, restingLion.getLocation());
        SimulationContext restingContext = new SimulationContext(10, 10);
        restingContext.startStep(8, restingField);
        restingLion.act(restingField, new Field(10, 10), restingContext);
        TestSupport.assertFalse("resting in place records no move event",
            hasEvent(restingContext, SimulationEvent.EventType.MOVE));

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
    }

    private static void testAmbientInspectActors()
    {
        Randomizer.reset();
        Field field = new Field(5, 5);
        for(int row = 0; row < 5; row++) {
            for(int col = 0; col < 5; col++) {
                Location location = new Location(row, col);
                SavannahAnimal animal = (row + col) % 3 == 0
                    ? new Lion(false, location)
                    : new Gazelle(false, location);
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
        TestSupport.setField(gazelle, "hydration", 30.0);
        double beforeDrain = gazelle.getHydrationPercent();
        context.getThirstSystem().applyThirst(gazelle, context);
        TestSupport.assertTrue("enabled thirst drains hydration",
                               gazelle.getHydrationPercent() < beforeDrain);
        TestSupport.assertTrue("shore animal drinks",
                               context.getThirstSystem().tryDrink(gazelle,
                                                                  context));
        TestSupport.assertClose("drinking restores hydration",
                                SavannahAnimal.MAX_HYDRATION,
                                gazelle.getHydrationPercent(), 0.001);
        TestSupport.assertTrue("drinking records event",
            hasEvent(context, SimulationEvent.EventType.DRINK));

        Location nonShore = firstNonShorePassable(context.getThirstSystem(),
            context.getTerrainMap());
        TestSupport.assertTrue("test map has non-shore passable cell",
                               nonShore != null);
        Gazelle seeker = new Gazelle(false, nonShore);
        Location next = context.getThirstSystem().stepTowardWater(seeker,
            TerrainNavigator.freeAdjacent(field, context.getTerrainMap(),
                                          nonShore, context.getRandom()));
        TestSupport.assertTrue("thirst system can choose a waterward step",
                               next != null);
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

    private static void testTerminalDiagnostics()
    {
        Field field = new Field(5, 5);
        Gazelle gazelle = new Gazelle(false, new Location(1, 1));
        Lion lion = new Lion(false, new Location(2, 2));
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

        Buffalo buffalo = new Buffalo(false, new Location(3, 3));
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
        SimulationContext context = new SimulationContext(3, 3, config);
        Field field = new Field(3, 3);
        context.startStep(0, field);
        SavannahDiseaseSystem disease = new SavannahDiseaseSystem();
        int transmitted = 0;
        for(int attempt = 0; attempt < 80; attempt++) {
            Gazelle prey = new Gazelle(false, new Location(1, 1));
            Lion predator = new Lion(false, new Location(1, 2));
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

    private static TestAnimal createTestAnimal(Sex sex, Location location)
    {
        for(int index = 0; index < 100; index++) {
            TestAnimal animal = new TestAnimal(false, location);
            if(animal.getSex() == sex) {
                TestSupport.setField(animal, "age", TEST_PROFILE.getAdultAge());
                TestSupport.setField(animal, "foodLevel", TEST_PROFILE.getMaxFoodLevel());
                TestSupport.setField(animal, "stamina", 100.0);
                return animal;
            }
        }
        throw new IllegalStateException("Could not create test animal with sex " + sex);
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

        public SavannahAnimal createChild(Location location)
        {
            return new TestAnimal(false, location);
        }
    }
}
