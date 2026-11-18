package savanna;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Controlled SOC1-aF registry, capacity, and candidate-order fixtures. */
public final class Soc1aFactTests
{
    private static final String EXPECTED_SCHEMA_HASH =
        "7b9ca4486b1220d2c8ce313998101262027ed723334941482ca159343c83043f";

    private Soc1aFactTests()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("SOC1-aF fact fixtures");
        run();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    public static void run()
    {
        TestSupport.startGroup("SOC1-aF registry and capacity facts");
        testRegistryCompleteAndUnique();
        testExistingProfileValuesOnly();
        testNoUnidentifiedPolicyNumber();
        CapacitySetup capacity = testStepStartCapacityTopologyExact();
        testDecisionCandidateExactNoSecondShuffle();
        testRawNumeratorDenominatorRetained(capacity);
        testCanonicalSchemaHash();
        testPreyFactOnlyLegacyAdapter();
        testZeroBehaviorAndRngDiff();
        testLegacyProjectionTransparency();
        TestSupport.finishGroup("SOC1AF_FIXTURES");
    }

    private static void testRegistryCompleteAndUnique()
    {
        SpeciesEcologyFactRegistry registry =
            SpeciesEcologyFactRegistry.capture();
        Set<String> keys = new HashSet<>();
        boolean complete = true;
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String key = factory.getProfile().getName();
            complete = complete && registry.get(key) != null;
            keys.add(key);
        }
        TestSupport.assertEquals("SOC1AF F01 registry count matches factories",
            SpeciesRegistry.getFactories().size(), registry.size());
        TestSupport.assertEquals("SOC1AF F01 registry keys are unique",
            registry.size(), keys.size());
        TestSupport.assertTrue("SOC1AF F01 every registered species is present",
            complete);
    }

    private static void testExistingProfileValuesOnly()
    {
        SpeciesEcologyFactRegistry registry =
            SpeciesEcologyFactRegistry.capture();
        boolean exact = true;
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            SpeciesProfile source = factory.getProfile();
            SpeciesEcologyFactProfile fact = registry.get(source.getName());
            List<String> expectedDiet = new ArrayList<>();
            for(SpeciesFactory candidate : SpeciesRegistry.getFactories()) {
                if(source.canEat(candidate.getProfile().getName())) {
                    expectedDiet.add(candidate.getProfile().getName());
                }
            }
            Collections.sort(expectedDiet);
            exact = exact && fact != null &&
                fact.isPredator() == source.isPredator() &&
                fact.getEdibleSpeciesSorted().equals(expectedDiet) &&
                fact.getBreedingRange() == source.getBreedingRange() &&
                fact.getSearchRange() == source.getSearchRange() &&
                fact.getMaxFoodLevel() == source.getMaxFoodLevel() &&
                fact.getFoodValue() == source.getFoodValue() &&
                fact.getBreedingAge() == source.getBreedingAge() &&
                fact.getAdultAge() == source.getAdultAge() &&
                fact.getMaxLitterSize() == source.getMaxLitterSize() &&
                fact.getFoundingPopulation() ==
                    source.getFoundingPopulation();
        }
        TestSupport.assertTrue("SOC1AF F02 all values are exact profile projections",
            exact);
    }

    private static void testNoUnidentifiedPolicyNumber()
    {
        Set<String> actual = new HashSet<>();
        for(java.lang.reflect.Field field :
            SpeciesEcologyFactProfile.class.getDeclaredFields()) {
            if(!Modifier.isStatic(field.getModifiers())) {
                actual.add(field.getName());
            }
        }
        Set<String> allowed = new HashSet<>(Arrays.asList(
            "speciesKey", "predator", "reproductionAdapter",
            "edibleSpeciesSorted", "breedingRange", "searchRange",
            "maxFoodLevel", "foodValue", "breedingAge", "adultAge",
            "maxLitterSize", "foundingPopulation"));
        TestSupport.assertEquals("SOC1AF F03 schema has existing-value fields only",
            allowed, actual);
    }

    private static CapacitySetup testStepStartCapacityTopologyExact()
    {
        TerrainMap terrain = blockedTerrain(9, 9);
        Location origin = new Location(4, 1);
        List<Location> passable = Arrays.asList(
            origin, new Location(3, 1), new Location(2, 2),
            new Location(2, 3), new Location(3, 4), new Location(4, 4),
            new Location(5, 4));
        for(Location location : passable) {
            setTerrain(terrain, location, TerrainType.OPEN_PLAIN);
        }
        Field field = new Field(9, 9);
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Lion lion = new Lion(false, origin, new Random(1L), identities);
        Gazelle gazelle = new Gazelle(false, new Location(4, 4),
            new Random(2L), identities);
        Lion conspecific = new Lion(false, new Location(5, 4),
            new Random(3L), identities);
        field.placeAnimal(lion, lion.getLocation());
        field.placeAnimal(gazelle, gazelle.getLocation());
        field.placeAnimal(conspecific, conspecific.getLocation());
        SpatialEcologyView spatial = SpatialEcologyIndex.capture(9, field,
            terrain);
        SpeciesEcologyFactRegistry registry =
            SpeciesEcologyFactRegistry.capture();
        Soc1aFactView soc = Soc1aFactIndex.capture(spatial, registry);
        StepStartLocalCapacityFact capacity =
            soc.getLocalCapacity(lion.getId());
        SpeciesEcologyFactProfile profile =
            registry.get(SpeciesRegistry.LION);
        LocalOccupancyFact direct = spatial.getLocalOccupancy(origin,
            profile.getBreedingRange(), profile.getSpeciesKey());

        TestSupport.assertEquals("SOC1AF F04 capacity uses profile breeding range",
            profile.getBreedingRange(), capacity.getBreedingRange());
        TestSupport.assertEquals("SOC1AF F04 passable topology count exact",
            direct.getPassableCellCount(), capacity.getPassableCellCount());
        TestSupport.assertEquals("SOC1AF F04 living occupancy exact",
            direct.getLivingOccupantCount(),
            capacity.getLivingOccupantCount());
        TestSupport.assertEquals("SOC1AF F04 conspecific count exact",
            direct.getConspecificCount(), capacity.getConspecificCount());
        TestSupport.assertEquals("SOC1AF F04 predator count exact",
            direct.getPredatorCount(), capacity.getPredatorCount());
        TestSupport.assertEquals("SOC1AF F04 edible prey count exact",
            direct.getEdiblePreyCount(), capacity.getEdiblePreyCount());
        return new CapacitySetup(capacity, soc);
    }

    private static void testDecisionCandidateExactNoSecondShuffle()
    {
        SimulationContext context = new SimulationContext(12, 12,
            SimulationConfig.baseline());
        Field field = new Field(12, 12);
        Location origin = firstPassable(context.getTerrainMap());
        Cheetah actor = new Cheetah(false, origin, new Random(4L));
        field.placeAnimal(actor, origin);
        context.startStep(1, field);
        RecordingBreedingSystem breeding = new RecordingBreedingSystem();
        TestSupport.setField(context, "breedingSystem", breeding);
        FactCollector collector = new FactCollector();
        context.addDecisionBirthCandidateFactListener(collector);
        List<Location> candidates = firstPassableLocations(
            context.getTerrainMap(), origin, 3);
        List<Location> expected = new ArrayList<>(candidates);
        BehaviorIntent intent = new BehaviorIntent(actor.getId(),
            ActionType.BREED, BehaviorReason.BREEDING, origin);
        context.getActionExecutor().proposeBreedingAction(intent, actor,
            StepStartInteractionGeometry.capture(field), candidates, context);

        TestSupport.assertEquals("SOC1AF F05 one exact candidate fact emitted", 1,
            collector.facts.size());
        DecisionBirthCandidateFact fact = collector.facts.get(0);
        TestSupport.assertEquals("SOC1AF F05 fact preserves passed order",
            expected, fact.getOrderedLocations());
        TestSupport.assertEquals("SOC1AF F05 breeding receives that same order",
            expected, breeding.received);
        TestSupport.assertEquals("SOC1AF F05 actual count retained",
            expected.size(), fact.getActualDecisionCandidateCount());
        TestSupport.assertEquals("SOC1AF F05 repeat capture digest is canonical",
            fact.getOrderDigest(), DecisionBirthCandidateFact.capture(1, actor,
                expected).getOrderDigest());
    }

    private static void testRawNumeratorDenominatorRetained(
        CapacitySetup setup)
    {
        StepStartLocalCapacityFact fact = setup.fact;
        TestSupport.assertEquals("SOC1AF F06 occupancy numerator is raw living count",
            fact.getLivingOccupantCount(), fact.getOccupancyNumerator());
        TestSupport.assertEquals("SOC1AF F06 occupancy denominator is raw passable count",
            fact.getPassableCellCount(), fact.getOccupancyDenominator());
        TestSupport.assertEquals("SOC1AF F06 one actual capacity key cached", 1,
            setup.view.getCachedCapacityFactCount());
    }

    private static void testCanonicalSchemaHash()
    {
        SpeciesEcologyFactRegistry first =
            SpeciesEcologyFactRegistry.capture();
        SpeciesEcologyFactRegistry second =
            SpeciesEcologyFactRegistry.capture();
        String hash = first.getSchemaHash();
        TestSupport.assertEquals("SOC1AF F07 canonical serialization repeats",
            first.getCanonicalSerialization(),
            second.getCanonicalSerialization());
        TestSupport.assertEquals("SOC1AF F07 canonical schema hash repeats",
            hash, second.getSchemaHash());
        TestSupport.assertEquals("SOC1AF F07 schema hash is frozen",
            EXPECTED_SCHEMA_HASH, hash);
        TestSupport.assertEquals("SOC1AF F07 SHA-256 has 64 hex characters", 64,
            hash.length());
        TestSupport.assertTrue("SOC1AF F07 schema version is serialized",
            first.getCanonicalSerialization().startsWith(
                SpeciesEcologyFactRegistry.SCHEMA_VERSION + "\n"));
        System.out.println("SOC1AF_SCHEMA_HASH value=" + hash);
    }

    private static void testPreyFactOnlyLegacyAdapter()
    {
        SpeciesEcologyFactRegistry registry =
            SpeciesEcologyFactRegistry.capture();
        boolean exact = true;
        for(SpeciesEcologyFactProfile profile : registry.getProfiles()) {
            SpeciesReproductionAdapter expected = profile.isPredator()
                ? SpeciesReproductionAdapter.PREDATOR_FACT_ONLY
                : SpeciesReproductionAdapter.LEGACY_GLOBAL;
            exact = exact && profile.getReproductionAdapter() == expected;
        }
        TestSupport.assertTrue("SOC1AF F08 prey remains named LEGACY_GLOBAL",
            exact);
    }

    private static void testZeroBehaviorAndRngDiff()
    {
        TerrainMap terrain = openTerrain(7, 7);
        Field field = new Field(7, 7);
        Lion lion = new Lion(false, new Location(3, 3), new Random(5L));
        field.placeAnimal(lion, lion.getLocation());
        Soc1aFactView view = Soc1aFactIndex.capture(
            SpatialEcologyIndex.capture(1, field, terrain),
            SpeciesEcologyFactRegistry.capture());
        Randomizer.reset(991122L);
        view.getLocalCapacity(lion.getId());
        long afterObserved = Randomizer.getRandom().nextLong();
        Randomizer.reset(991122L);
        long withoutObserved = Randomizer.getRandom().nextLong();
        TestSupport.assertTrue("SOC1AF F09 capacity facts consume zero shared RNG",
            afterObserved == withoutObserved);
    }

    private static void testLegacyProjectionTransparency()
    {
        Trace baseline = runTrace(false);
        Trace observed = runTrace(true);
        TestSupport.assertEquals("SOC1AF F10 per-step ecology projection exact",
            baseline.fingerprints, observed.fingerprints);
        TestSupport.assertTrue("SOC1AF F10 observer captures capacity facts",
            observed.capacityFacts > 0);
        TestSupport.assertTrue("SOC1AF F10 observer captures decision candidates",
            observed.candidateFacts > 0);
    }

    private static Trace runTrace(boolean observe)
    {
        Randomizer.reset();
        Simulator simulator = new Simulator(SimulationConfig.builder()
            .randomSeed(1111L).terrainSeed(20260629L).build(), false);
        simulator.setVerbose(false);
        FactCollector collector = new FactCollector();
        if(observe) {
            simulator.getContext().addDecisionBirthCandidateFactListener(
                collector);
        }
        List<String> fingerprints = new ArrayList<>();
        int capacityFacts = 0;
        for(int step = 1; step <= 20 && simulator.getField().isViable(); step++) {
            simulator.simulateOneStep();
            if(observe) {
                Soc1aFactView facts = simulator.getContext().getSoc1aFactView();
                for(SpatialAnimalFact animal : simulator.getContext()
                    .getSpatialEcologyView().getAnimalFacts()) {
                    facts.getLocalCapacity(animal.getId());
                    capacityFacts++;
                }
            }
            fingerprints.add(SimulationFingerprint.capture(simulator));
        }
        return new Trace(fingerprints, capacityFacts, collector.facts.size());
    }

    private static List<Location> firstPassableLocations(TerrainMap terrain,
                                                         Location excluded,
                                                         int count)
    {
        List<Location> result = new ArrayList<>();
        for(int row = 0; row < terrain.getDepth() && result.size() < count;
            row++) {
            for(int col = 0; col < terrain.getWidth() &&
                result.size() < count; col++) {
                Location location = new Location(row, col);
                if(terrain.isPassable(location) &&
                   !location.equals(excluded)) result.add(location);
            }
        }
        if(result.size() != count) {
            throw new IllegalStateException("not enough fixture locations");
        }
        return result;
    }

    private static TerrainMap openTerrain(int depth, int width)
    {
        TerrainMap terrain = new TerrainMap(depth, width, 8101L);
        fillTerrain(terrain, TerrainType.OPEN_PLAIN);
        return terrain;
    }

    private static TerrainMap blockedTerrain(int depth, int width)
    {
        TerrainMap terrain = new TerrainMap(depth, width, 8102L);
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
        throw new IllegalStateException("no passable fixture location");
    }

    private static final class RecordingBreedingSystem
        implements BreedingSystem
    {
        private List<Location> received = Collections.emptyList();

        @Override
        public void tryBreed(SavannahAnimal parent, Field currentField,
                             Field nextFieldState, SimulationContext context,
                             List<Location> freeLocations)
        {
        }

        @Override
        public List<PositionClaim> propose(SavannahAnimal parent,
                                           InteractionGeometry geometry,
                                           SimulationContext context,
                                           List<Location> candidates)
        {
            received = new ArrayList<>(candidates);
            return Collections.emptyList();
        }
    }

    private static final class FactCollector
        implements DecisionBirthCandidateFactListener
    {
        private final List<DecisionBirthCandidateFact> facts =
            new ArrayList<>();

        @Override
        public void onDecisionBirthCandidates(DecisionBirthCandidateFact fact)
        {
            facts.add(fact);
        }
    }

    private static final class CapacitySetup
    {
        private final StepStartLocalCapacityFact fact;
        private final Soc1aFactView view;

        CapacitySetup(StepStartLocalCapacityFact fact, Soc1aFactView view)
        {
            this.fact = fact;
            this.view = view;
        }
    }

    private static final class Trace
    {
        private final List<String> fingerprints;
        private final int capacityFacts;
        private final int candidateFacts;

        Trace(List<String> fingerprints, int capacityFacts,
              int candidateFacts)
        {
            this.fingerprints = fingerprints;
            this.capacityFacts = capacityFacts;
            this.candidateFacts = candidateFacts;
        }
    }
}
