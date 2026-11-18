package savanna;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Controlled L1-F fixtures; every query is read-only and RNG-free. */
public final class L1FSpatialEcologyTests
{
    private L1FSpatialEcologyTests()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("L1-F spatial ecology fixtures");
        run();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    public static void run()
    {
        TestSupport.startGroup("L1-F immutable spatial ecology facts");
        testCaptureOnceBeforeActions();
        testNoLiveAnimalReference();
        testEightNeighbourPassableDistance();
        testWallAndDetourNotAirDistance();
        testAllRegisteredPredatorDiets();
        testCountAndOfferedFoodExact();
        testQueryOrderIndependentZeroRng();
        testStepCacheBoundedAndReplaced();
        testD2HuntOpportunityEquivalence();
        testLegacyProjectionTransparency();
        TestSupport.finishGroup("L1F_FIXTURES");
    }

    private static void testCaptureOnceBeforeActions()
    {
        SimulationContext context = new SimulationContext(18, 18,
            SimulationConfig.baseline());
        Field field = new Field(18, 18);
        Location origin = firstPassable(context.getTerrainMap());
        Cheetah cheetah = new Cheetah(false, origin, new Random(1L));
        field.placeAnimal(cheetah, origin);

        context.startStep(1, field);
        int afterStart = context.getSpatialEcologyCaptureCount();
        SpatialEcologyView view = context.getSpatialEcologyView();
        new PositionStepCoordinator(field, context);

        TestSupport.assertEquals("L1F F01 startStep captures exactly once", 1,
            afterStart);
        TestSupport.assertEquals("L1F F01 coordinator does not recapture", 1,
            context.getSpatialEcologyCaptureCount());
        TestSupport.assertEquals("L1F F01 view is stamped before actions", 1,
            view.getStep());
        TestSupport.assertEquals("L1F F01 captures the step-start origin",
            origin, view.getAnimalFact(cheetah.getId()).getLocation());
    }

    private static void testNoLiveAnimalReference()
    {
        boolean safeFields = hasNoForbiddenReference(SpatialAnimalFact.class) &&
            hasNoForbiddenReference(ReachableResourceFact.class) &&
            hasNoForbiddenReference(LocalOccupancyFact.class) &&
            hasNoForbiddenReference(SpatialEcologyIndex.class);

        TerrainMap terrain = openTerrain(5, 5);
        Field field = new Field(5, 5);
        Cheetah cheetah = new Cheetah(false, new Location(2, 2),
            new Random(2L));
        field.placeAnimal(cheetah, cheetah.getLocation());
        SpatialEcologyView view = SpatialEcologyIndex.capture(7, field, terrain);
        SpatialAnimalFact fact = view.getAnimalFact(cheetah.getId());
        Location first = fact.getLocation();
        Location second = fact.getLocation();
        TestSupport.setField(cheetah, "age",
            SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH).getAdultAge());

        TestSupport.assertTrue("L1F F02 value/index fields hold no Animal or Field",
            safeFields);
        TestSupport.assertTrue("L1F F02 location accessor is defensive",
            first != second && first.equals(second));
        TestSupport.assertEquals("L1F F02 life stage remains capture-time fact",
            LifeStage.CUB, fact.getLifeStage());
        TestSupport.assertTrue("L1F F02 captured alive flag is immutable",
            fact.wasAliveAtCapture());
    }

    private static void testEightNeighbourPassableDistance()
    {
        TerrainMap terrain = blockedTerrain(4, 4);
        setTerrain(terrain, new Location(1, 1), TerrainType.OPEN_PLAIN);
        setTerrain(terrain, new Location(2, 2), TerrainType.OPEN_PLAIN);
        Field field = new Field(4, 4);
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Cheetah predator = new Cheetah(false, new Location(1, 1),
            new Random(3L), identities);
        Gazelle prey = new Gazelle(false, new Location(2, 2), new Random(4L),
                                   identities);
        field.placeAnimal(predator, predator.getLocation());
        field.placeAnimal(prey, prey.getLocation());

        ReachableResourceFact fact = SpatialEcologyIndex.capture(1, field,
            terrain).getReachableResources(predator.getLocation(), 1,
                predator.getProfile());
        TestSupport.assertEquals("L1F F03 diagonal is one 8-neighbour step", 1,
            fact.getNearestEdibleDistance());
        TestSupport.assertEquals("L1F F03 diagonal prey is reachable", 1,
            fact.getReachableEdibleCount());
        TestSupport.assertEquals("L1F F03 only two passable cells visited", 2,
            fact.getPassableVisitedCount());
    }

    private static void testWallAndDetourNotAirDistance()
    {
        TerrainMap terrain = blockedTerrain(7, 7);
        Location[] path = {
            new Location(3, 0), new Location(2, 0), new Location(1, 0),
            new Location(0, 0), new Location(0, 1), new Location(0, 2),
            new Location(0, 3), new Location(0, 4), new Location(1, 4),
            new Location(2, 4), new Location(3, 4)
        };
        for(Location location : path) {
            setTerrain(terrain, location, TerrainType.OPEN_PLAIN);
        }
        Field field = new Field(7, 7);
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Cheetah predator = new Cheetah(false, path[0], new Random(5L),
                                       identities);
        Gazelle prey = new Gazelle(false, path[10], new Random(6L), identities);
        field.placeAnimal(predator, path[0]);
        field.placeAnimal(prey, path[10]);
        SpatialEcologyView view = SpatialEcologyIndex.capture(1, field, terrain);

        ReachableResourceFact airRange = view.getReachableResources(path[0], 4,
            predator.getProfile());
        ReachableResourceFact pathRange = view.getReachableResources(path[0], 8,
            predator.getProfile());
        TestSupport.assertEquals("L1F F04 air-near prey excluded by wall", 0,
            airRange.getReachableEdibleCount());
        TestSupport.assertEquals("L1F F04 detour prey enters at path distance", 8,
            pathRange.getNearestEdibleDistance());
    }

    private static void testAllRegisteredPredatorDiets()
    {
        boolean allExact = true;
        int predatorProfiles = 0;
        for(SpeciesFactory consumerFactory : SpeciesRegistry.getFactories()) {
            SpeciesProfile consumer = consumerFactory.getProfile();
            if(!consumer.isPredator()) continue;
            predatorProfiles++;
            DietSetup setup = dietSetup(consumerFactory);
            ReachableResourceFact fact = setup.view.getReachableResources(
                setup.origin, 4, consumer);
            List<Long> expected = new ArrayList<>();
            for(SpatialAnimalFact animal : setup.view.getAnimalFacts()) {
                if(consumer.canEat(animal.getSpecies())) {
                    expected.add(animal.getId());
                }
            }
            Collections.sort(expected);
            allExact = allExact &&
                expected.equals(fact.getEdibleAnimalIdsSorted());
        }
        TestSupport.assertEquals("L1F F05 covers every registered predator", 2,
            predatorProfiles);
        TestSupport.assertTrue("L1F F05 each diet is SpeciesProfile.canEat exact",
            allExact);
    }

    private static void testCountAndOfferedFoodExact()
    {
        TerrainMap terrain = openTerrain(5, 5);
        Field field = new Field(5, 5);
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Lion lion = new Lion(false, new Location(2, 2), new Random(7L),
                             identities);
        Gazelle firstGazelle = new Gazelle(false, new Location(1, 1),
            new Random(8L), identities);
        Gazelle secondGazelle = new Gazelle(false, new Location(1, 2),
            new Random(9L), identities);
        Zebra zebra = new Zebra(false, new Location(2, 1), new Random(10L),
                                identities);
        field.placeAnimal(lion, lion.getLocation());
        field.placeAnimal(firstGazelle, firstGazelle.getLocation());
        field.placeAnimal(secondGazelle, secondGazelle.getLocation());
        field.placeAnimal(zebra, zebra.getLocation());
        SpatialEcologyView view = SpatialEcologyIndex.capture(1, field, terrain);
        ReachableResourceFact resource = view.getReachableResources(
            lion.getLocation(), 1, lion.getProfile());
        LocalOccupancyFact occupancy = view.getLocalOccupancy(
            lion.getLocation(), 1, lion.getProfile());
        int expectedFood =
            2 * SpeciesRegistry.getProfile(SpeciesRegistry.GAZELLE).getFoodValue() +
            SpeciesRegistry.getProfile(SpeciesRegistry.ZEBRA).getFoodValue();

        TestSupport.assertEquals("L1F F06 edible count exact", 3,
            resource.getReachableEdibleCount());
        TestSupport.assertEquals("L1F F06 offered prey food exact", expectedFood,
            resource.getReachableOfferedFood());
        TestSupport.assertEquals("L1F F06 nearest distance exact", 1,
            resource.getNearestEdibleDistance());
        TestSupport.assertEquals("L1F F06 passable denominator exact", 9,
            occupancy.getPassableCellCount());
        TestSupport.assertEquals("L1F F06 living occupants exact", 4,
            occupancy.getLivingOccupantCount());
        TestSupport.assertEquals("L1F F06 conspecific includes focal", 1,
            occupancy.getConspecificCount());
        TestSupport.assertEquals("L1F F06 predator count exact", 1,
            occupancy.getPredatorCount());
        TestSupport.assertEquals("L1F F06 occupancy edible count exact", 3,
            occupancy.getEdiblePreyCount());
    }

    private static void testQueryOrderIndependentZeroRng()
    {
        DietSetup setup = dietSetup(factoryFor(SpeciesRegistry.CHEETAH));
        SpatialEcologyView first = setup.view;
        SpatialEcologyView second = SpatialEcologyIndex.capture(1, setup.field,
            setup.terrain);

        Randomizer.reset(778899L);
        ReachableResourceFact firstResource = first.getReachableResources(
            setup.origin, 4, setup.consumer);
        LocalOccupancyFact firstOccupancy = first.getLocalOccupancy(
            setup.origin, 4, setup.consumer);
        long firstNextRandom = Randomizer.getRandom().nextLong();

        Randomizer.reset(778899L);
        LocalOccupancyFact secondOccupancy = second.getLocalOccupancy(
            setup.origin, 4, setup.consumer);
        ReachableResourceFact secondResource = second.getReachableResources(
            setup.origin, 4, setup.consumer);
        long secondNextRandom = Randomizer.getRandom().nextLong();

        TestSupport.assertEquals("L1F F07 resource result ignores query order",
            firstResource, secondResource);
        TestSupport.assertEquals("L1F F07 occupancy result ignores query order",
            firstOccupancy, secondOccupancy);
        TestSupport.assertTrue("L1F F07 query consumes zero shared RNG",
            firstNextRandom == secondNextRandom);
        TestSupport.assertEquals("L1F F07 one topology computation per key", 1,
            first.getQueryLedger().getTopologyComputationCount());
    }

    private static void testStepCacheBoundedAndReplaced()
    {
        SimulationContext context = new SimulationContext(16, 16,
            SimulationConfig.baseline());
        Field field = new Field(16, 16);
        Location origin = firstPassable(context.getTerrainMap());
        Cheetah predator = new Cheetah(false, origin, new Random(11L));
        field.placeAnimal(predator, origin);
        context.startStep(1, field);
        SpatialEcologyView first = context.getSpatialEcologyView();
        first.getReachableResources(origin, 4, predator.getProfile());
        first.getReachableResources(origin, 4, predator.getProfile());
        SpatialQueryLedger firstLedger = first.getQueryLedger();

        context.startStep(2, field);
        SpatialEcologyView second = context.getSpatialEcologyView();
        SpatialQueryLedger secondLedger = second.getQueryLedger();

        TestSupport.assertEquals("L1F F08 one actual result-cache key", 1,
            firstLedger.getResultCacheEntryCount());
        TestSupport.assertEquals("L1F F08 duplicate query is a result hit", 1,
            firstLedger.getResultCacheHitCount());
        TestSupport.assertTrue("L1F F08 one query stays within terrain budget",
            firstLedger.getMaxPassableCellsVisitedByOneComputation() <=
                firstLedger.getTerrainPassableCellCount());
        TestSupport.assertTrue("L1F F08 next step replaces the view",
            first != second);
        TestSupport.assertEquals("L1F F08 next-step result cache starts empty", 0,
            secondLedger.getResultCacheEntryCount());
        TestSupport.assertEquals("L1F F08 captures exactly once per startStep", 2,
            context.getSpatialEcologyCaptureCount());
    }

    private static void testD2HuntOpportunityEquivalence()
    {
        boolean exact = true;
        int checked = 0;
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            if(!factory.getProfile().isPredator()) continue;
            SimulationContext context = new SimulationContext(20, 20,
                SimulationConfig.baseline());
            Field field = new Field(20, 20);
            AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
            Location origin = passableOriginWithReach(context.getTerrainMap(), 8);
            SavannahAnimal predator = factory.create(false, origin,
                new Random(12L + checked), identities);
            makeAdult(predator);
            field.placeAnimal(predator, origin);
            List<Location> reachable = TerrainNavigator.reachableWithin(
                context.getTerrainMap(), origin, 4);
            int locationIndex = 0;
            for(SpeciesFactory preyFactory : SpeciesRegistry.getFactories()) {
                if(!factory.getProfile().canEat(
                    preyFactory.getProfile().getName())) continue;
                Location location = reachable.get(locationIndex++);
                SavannahAnimal prey = preyFactory.create(false, location,
                    new Random(50L + locationIndex), identities);
                field.placeAnimal(prey, location);
            }
            context.startStep(1, field);
            int range = HuntLocalOpportunity.effectiveRange(predator, context);
            HuntLocalOpportunity legacy = HuntLocalOpportunity.inspect(predator,
                StepStartInteractionGeometry.capture(field), context);
            ReachableResourceFact modern = context.getSpatialEcologyView()
                .getReachableResources(origin, range, predator.getProfile());
            List<Long> legacyIds = new ArrayList<>();
            for(HuntLocalOpportunity.Candidate candidate :
                legacy.getCandidates()) {
                legacyIds.add(candidate.getPrey().getId());
            }
            Collections.sort(legacyIds);
            exact = exact &&
                legacy.getLocalReachableEdibleCount() ==
                    modern.getReachableEdibleCount() &&
                legacyIds.equals(modern.getEdibleAnimalIdsSorted());
            checked++;
        }
        TestSupport.assertEquals("L1F F09 checks all predator profiles", 2,
            checked);
        TestSupport.assertTrue("L1F F09 D2 opportunity projection is exact",
            exact);
    }

    private static void testLegacyProjectionTransparency()
    {
        DietSetup setup = dietSetup(factoryFor(SpeciesRegistry.LION));
        Map<Long, SavannahAnimal> animals = new LinkedHashMap<>();
        for(Animal actor : setup.field.getAnimals()) {
            animals.put(actor.getId(), (SavannahAnimal)actor);
        }
        StepStartInteractionGeometry legacy =
            StepStartInteractionGeometry.capture(setup.field);
        StepStartInteractionGeometry projected =
            StepStartInteractionGeometry.fromSpatialView(setup.view, animals);
        List<Location> legacyLocations = legacy.getLocationsWithin(setup.origin,
            4, new Random(991L));
        List<Location> projectedLocations = projected.getLocationsWithin(
            setup.origin, 4, new Random(991L));
        boolean identitiesExact = true;
        for(Location location : legacyLocations) {
            Animal first = legacy.getAnimalAt(location);
            Animal second = projected.getAnimalAt(location);
            identitiesExact = identitiesExact &&
                (first == null ? second == null : second != null &&
                    first.getId() == second.getId());
        }
        TestSupport.assertEquals("L1F F10 legacy location order exact",
            legacyLocations, projectedLocations);
        TestSupport.assertEquals("L1F F10 legacy living population exact",
            legacy.getLivingPopulation(), projected.getLivingPopulation());
        TestSupport.assertTrue("L1F F10 legacy occupant identity exact",
            identitiesExact);
    }

    private static boolean hasNoForbiddenReference(Class<?> type)
    {
        for(java.lang.reflect.Field field : type.getDeclaredFields()) {
            if(Modifier.isStatic(field.getModifiers())) continue;
            Class<?> fieldType = field.getType();
            if(Animal.class.isAssignableFrom(fieldType) ||
               Field.class.isAssignableFrom(fieldType) ||
               fieldType.getSimpleName().endsWith("Listener")) {
                return false;
            }
        }
        return true;
    }

    private static DietSetup dietSetup(SpeciesFactory consumerFactory)
    {
        TerrainMap terrain = openTerrain(11, 11);
        Field field = new Field(11, 11);
        Location origin = new Location(5, 5);
        SpeciesProfile consumer = consumerFactory.getProfile();
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        SavannahAnimal focal = consumerFactory.create(false, origin,
            new Random(100L), identities);
        field.placeAnimal(focal, origin);
        int index = 0;
        Location[] locations = {
            new Location(4, 4), new Location(4, 5), new Location(4, 6),
            new Location(5, 4), new Location(5, 6)
        };
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            SavannahAnimal animal = factory.create(false, locations[index++],
                new Random(200L + index), identities);
            field.placeAnimal(animal, animal.getLocation());
        }
        SpatialEcologyView view = SpatialEcologyIndex.capture(1, field, terrain);
        return new DietSetup(terrain, field, view, origin, consumer);
    }

    private static SpeciesFactory factoryFor(String species)
    {
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            if(factory.getProfile().getName().equals(species)) return factory;
        }
        throw new IllegalArgumentException("unknown species " + species);
    }

    private static TerrainMap openTerrain(int depth, int width)
    {
        TerrainMap terrain = new TerrainMap(depth, width, 7001L);
        fillTerrain(terrain, TerrainType.OPEN_PLAIN);
        return terrain;
    }

    private static TerrainMap blockedTerrain(int depth, int width)
    {
        TerrainMap terrain = new TerrainMap(depth, width, 7002L);
        fillTerrain(terrain, TerrainType.WATERHOLE);
        return terrain;
    }

    private static void fillTerrain(TerrainMap terrain, TerrainType type)
    {
        TerrainType[][] cells = terrainCells(terrain);
        for(int row = 0; row < cells.length; row++) {
            for(int col = 0; col < cells[row].length; col++) {
                cells[row][col] = type;
            }
        }
    }

    private static void setTerrain(TerrainMap terrain, Location location,
                                   TerrainType type)
    {
        terrainCells(terrain)[location.row()][location.col()] = type;
    }

    private static TerrainType[][] terrainCells(TerrainMap terrain)
    {
        try {
            java.lang.reflect.Field field =
                TerrainMap.class.getDeclaredField("terrain");
            field.setAccessible(true);
            return (TerrainType[][])field.get(terrain);
        }
        catch(ReflectiveOperationException exception) {
            throw new IllegalStateException("cannot build fixture terrain",
                exception);
        }
    }

    private static Location firstPassable(TerrainMap terrain)
    {
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location location = new Location(row, col);
                if(terrain.isPassable(location)) return location;
            }
        }
        throw new IllegalStateException("fixture terrain has no passable cell");
    }

    private static Location passableOriginWithReach(TerrainMap terrain,
                                                    int minimum)
    {
        for(int row = 2; row < terrain.getDepth() - 2; row++) {
            for(int col = 2; col < terrain.getWidth() - 2; col++) {
                Location location = new Location(row, col);
                if(terrain.isPassable(location) &&
                   TerrainNavigator.reachableWithin(terrain, location, 4)
                       .size() >= minimum) {
                    return location;
                }
            }
        }
        throw new IllegalStateException("no sufficiently open origin");
    }

    private static void makeAdult(SavannahAnimal animal)
    {
        TestSupport.setField(animal, "age", animal.getProfile().getAdultAge());
    }

    private static final class DietSetup
    {
        private final TerrainMap terrain;
        private final Field field;
        private final SpatialEcologyView view;
        private final Location origin;
        private final SpeciesProfile consumer;

        DietSetup(TerrainMap terrain, Field field, SpatialEcologyView view,
                  Location origin, SpeciesProfile consumer)
        {
            this.terrain = terrain;
            this.field = field;
            this.view = view;
            this.origin = origin;
            this.consumer = consumer;
        }
    }
}
