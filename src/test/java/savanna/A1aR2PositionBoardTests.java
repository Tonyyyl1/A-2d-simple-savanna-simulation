package savanna;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Directed contracts for the R2 single-position-board production path. */
public final class A1aR2PositionBoardTests
{
    private static final SpeciesProfile PROFILE = new BasicSpeciesProfile(
        "R2Fixture", Color.green, false, Set.of(), Collections.emptySet(),
        0.0, 1, 0, 1, 1, 100, 1.0, 1, 2, 1, 100, 1, 0.0,
        0.95, 0.0, 0.30, 0.20, 1);

    private A1aR2PositionBoardTests()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("A1a-R2 single position board fixtures");
        run();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    public static void run()
    {
        TestSupport.startGroup("A1a-R2 single position board tests");
        testReleasedOriginCanBeClaimed();
        testOccupiedCandidateIsConditionallyVacatable();
        testProposalDoesNotCommitInteractionPosition();
        testCommittedGeometryPairsCommittedCoordinates();
        testRejectedSpawnHasNoChildSideEffects();
        testSuccessfulSpawnCommitsOneCanonicalFact();
        testPredationDeathCancelsParentSpawnBeforeCommit();
        testCanonicalMoveFactsAreOneToOne();
        testThirstOffAndOnKeepOneConsistentBoard();
        TestSupport.finishGroup("A1A_R2_POSITION_BOARD_FIXTURES");
    }

    private static void testReleasedOriginCanBeClaimed()
    {
        PositionTopology topology = PositionTopology.rectangular(3, 4,
            Collections.<Location>emptySet(), PositionKernelPolicyRegistry.current());
        Map<Long, Location> origins = new LinkedHashMap<>();
        origins.put(1L, new Location(1, 1));
        origins.put(2L, new Location(1, 2));
        StepStartPositionSnapshot snapshot = StepStartPositionSnapshot.of(origins);
        PositionClaimBatch batch = PositionClaimBatch.builder()
            .release(PositionActorKey.adult(1L))
            .add(PositionClaim.move(2L, PositionPriority.ORDINARY_MOVE,
                new Location(1, 1), Collections.<Location>emptyList(), 0, 0))
            .build();
        PositionPlan plan = new PurePositionReservationResolver(
            PositionKernelPolicyRegistry.current()).resolve(snapshot, batch,
            topology, Collections.<Location>emptySet(), 71L);
        TestSupport.assertEquals("R2 released adult has no destination",
            PositionPlanDisposition.RELEASED,
            plan.getResolution(PositionActorKey.adult(1L)).getDisposition());
        TestSupport.assertEquals("R2 released origin is available to another adult",
            new Location(1, 1), plan.destinationForAdult(2L));
        TestSupport.assertTrue("R2 release-aware plan validator accepts origin release",
            PositionPlanValidator.validate(snapshot, plan, topology,
                Collections.<Location>emptySet(), batch.getReleased()).isValid());
    }

    /** R2 must retain a snapshot occupant as a resolver-validated candidate. */
    private static void testOccupiedCandidateIsConditionallyVacatable()
    {
        PositionTopology topology = PositionTopology.rectangular(3, 4,
            Collections.<Location>emptySet(), PositionKernelPolicyRegistry.current());
        Map<Long, Location> origins = new LinkedHashMap<>();
        origins.put(1L, new Location(1, 1));
        origins.put(2L, new Location(1, 2));
        StepStartPositionSnapshot snapshot = StepStartPositionSnapshot.of(origins);
        PositionAvailabilityView view = new PositionAvailabilityView(snapshot,
            topology, Collections.<Location, Long>emptyMap());
        Location occupied = new Location(1, 2);
        List<Location> candidates = view.adjacentCandidates(1L,
            new Location(1, 1), null);
        TestSupport.assertTrue("R2 marks the neighbor as snapshot occupied",
            view.isSnapshotOccupied(occupied));
        TestSupport.assertTrue("R2 retains a vacatable owner in candidate facts",
            candidates.contains(occupied));
    }

    /** No actor may move its interaction coordinate before the sole commit. */
    private static void testProposalDoesNotCommitInteractionPosition()
    {
        BreedingSetup setup = openBreedingSetup();
        Location origin = setup.coordinator.getSnapshot().originOf(
            setup.parent.getId());
        setup.coordinator.propose(setup.parent,
            setup.coordinator.createAvailabilityView());
        TestSupport.assertEquals("R2 proposal retains the step-start coordinate",
            origin, setup.parent.getLocation());
        TestSupport.assertEquals("R2 proposal has no partial position plan",
            null, setup.context.getLastPositionPlan());
    }

    /**
     * A future post-commit food/mate phase must capture a new geometry from
     * the committed board. Pairing moved coordinates with the old geometry is
     * precisely the legacy mixed-state defect R2R must avoid.
     */
    private static void testCommittedGeometryPairsCommittedCoordinates()
    {
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Lion predator = new Lion(false, new Location(1, 1), new Random(7L),
            identities);
        Gazelle prey = new Gazelle(false, new Location(1, 4), new Random(8L),
            identities);
        Field stepStart = new Field(4, 6);
        stepStart.placeAnimal(predator, predator.getLocation());
        stepStart.placeAnimal(prey, prey.getLocation());
        InteractionGeometry startGeometry = StepStartInteractionGeometry.capture(
            stepStart);

        Location committedPredator = new Location(1, 2);
        Location committedPrey = new Location(1, 3);
        predator.setLocation(committedPredator);
        prey.setLocation(committedPrey);
        Field committed = new Field(4, 6);
        committed.placeAnimal(predator, committedPredator);
        committed.placeAnimal(prey, committedPrey);
        InteractionGeometry committedGeometry =
            StepStartInteractionGeometry.capture(committed);

        TestSupport.assertTrue("R2 step-start geometry keeps original prey cell",
            startGeometry.getAnimalAt(new Location(1, 4)) == prey);
        TestSupport.assertTrue("R2 committed geometry keeps resolved prey cell",
            committedGeometry.getAnimalAt(committedPrey) == prey);
        TestSupport.assertTrue("R2 step-start geometry has no initial encounter",
            !hasEdibleNeighbor(startGeometry, new Location(1, 1), predator));
        TestSupport.assertTrue("R2 committed geometry has the resolved encounter",
            hasEdibleNeighbor(committedGeometry, committedPredator, predator));
    }

    private static boolean hasEdibleNeighbor(InteractionGeometry geometry,
                                             Location origin,
                                             SavannahAnimal predator)
    {
        for(Location location : geometry.getLocationsWithin(origin, 1,
            new Random(11L))) {
            Animal occupant = geometry.getAnimalAt(location);
            if(occupant instanceof SavannahAnimal && occupant.isAlive() &&
               predator.getProfile().canEat(((SavannahAnimal)occupant)
                   .getProfile().getName())) {
                return true;
            }
        }
        return false;
    }

    private static void testRejectedSpawnHasNoChildSideEffects()
    {
        BreedingSetup setup = blockedBreedingSetup();
        PositionStepCoordinator coordinator = setup.coordinator;
        coordinator.propose(setup.parent, coordinator.createAvailabilityView());
        double staminaBeforeCommit = setup.parent.getStaminaPercent();
        R2FixtureAnimal marker = fixtureAnimal(Sex.FEMALE,
            new Location(0, 0), setup.identities);
        long markerId = marker.getId();

        Field committed = coordinator.resolveAndCommit();
        R2FixtureAnimal sentinel = fixtureAnimal(Sex.MALE,
            new Location(0, 0), setup.identities);
        PositionPlan plan = setup.context.getLastPositionPlan();
        PositionResolution spawn = plan.getResolution(
            PositionActorKey.spawn(setup.parent.getId(), 0));

        TestSupport.assertEquals("R2 blocked spawn token is rejected",
            PositionPlanDisposition.REJECTED, spawn.getDisposition());
        TestSupport.assertEquals("R2 rejected spawn consumes no child ID",
            Long.valueOf(markerId + 1L), Long.valueOf(sentinel.getId()));
        TestSupport.assertEquals("R2 rejected spawn emits no birth event", 0,
            eventCount(setup.context, SimulationEvent.EventType.BIRTH));
        TestSupport.assertClose("R2 rejected spawn charges no mother cost",
            staminaBeforeCommit, setup.parent.getStaminaPercent(), 0.0001);
        TestSupport.assertTrue("R2 rejected spawn leaves a consistent field",
            committed.hasConsistentState());
    }

    private static void testSuccessfulSpawnCommitsOneCanonicalFact()
    {
        BreedingSetup setup = openBreedingSetup();
        List<TransitionObservation> transitions = new ArrayList<>();
        setup.context.addTransitionObservationListener(transitions::add);
        setup.coordinator.propose(setup.parent,
            setup.coordinator.createAvailabilityView());
        double staminaBeforeCommit = setup.parent.getStaminaPercent();
        R2FixtureAnimal marker = fixtureAnimal(Sex.FEMALE,
            new Location(0, 0), setup.identities);
        long markerId = marker.getId();

        Field committed = setup.coordinator.resolveAndCommit();
        R2FixtureAnimal sentinel = fixtureAnimal(Sex.MALE,
            new Location(0, 0), setup.identities);
        PositionPlan plan = setup.context.getLastPositionPlan();
        PositionResolution spawn = plan.getResolution(
            PositionActorKey.spawn(setup.parent.getId(), 0));

        TestSupport.assertEquals("R2 winning spawn token commits",
            PositionPlanDisposition.COMMITTED, spawn.getDisposition());
        TestSupport.assertEquals("R2 successful spawn consumes exactly one child ID",
            Long.valueOf(markerId + 2L), Long.valueOf(sentinel.getId()));
        TestSupport.assertEquals("R2 successful spawn emits one birth event", 1,
            eventCount(setup.context, SimulationEvent.EventType.BIRTH));
        TestSupport.assertEquals("R2 successful spawn emits one transition fact", 1,
            transitionCount(transitions, TransitionStatus.SPAWNED));
        TestSupport.assertClose("R2 successful spawn charges one mother cost",
            staminaBeforeCommit - SavannahAnimal.STAMINA_BREED_COST,
            setup.parent.getStaminaPercent(), 0.0001);
        TestSupport.assertEquals("R2 successful spawn adds one living child", 3,
            committed.getLivingPopulation());
        TestSupport.assertTrue("R2 successful spawn field/list ledger agrees",
            committed.hasConsistentState());
    }

    private static void testPredationDeathCancelsParentSpawnBeforeCommit()
    {
        BreedingSetup setup = openBreedingSetup();
        setup.coordinator.propose(setup.parent,
            setup.coordinator.createAvailabilityView());
        double staminaBeforeDeath = setup.parent.getStaminaPercent();
        R2FixtureAnimal marker = fixtureAnimal(Sex.FEMALE,
            new Location(0, 0), setup.identities);
        long markerId = marker.getId();
        setup.context.recordDeath(setup.parent, DeathCause.PREDATION);

        Field committed = setup.coordinator.resolveAndCommit();
        R2FixtureAnimal sentinel = fixtureAnimal(Sex.MALE,
            new Location(0, 0), setup.identities);
        PositionPlan plan = setup.context.getLastPositionPlan();

        TestSupport.assertEquals("R2 predation death releases parent origin",
            PositionPlanDisposition.RELEASED,
            plan.getResolution(PositionActorKey.adult(setup.parent.getId()))
                .getDisposition());
        TestSupport.assertEquals("R2 parent death cancels spawn without child ID",
            Long.valueOf(markerId + 1L), Long.valueOf(sentinel.getId()));
        TestSupport.assertEquals("R2 parent death emits no birth", 0,
            eventCount(setup.context, SimulationEvent.EventType.BIRTH));
        TestSupport.assertClose("R2 parent death has no deferred mother cost",
            staminaBeforeDeath, setup.parent.getStaminaPercent(), 0.0001);
        TestSupport.assertEquals("R2 predation death leaves only mate", 1,
            committed.getLivingPopulation());
        TestSupport.assertTrue("R2 predation death commit remains consistent",
            committed.hasConsistentState());
    }

    private static void testCanonicalMoveFactsAreOneToOne()
    {
        SimulationConfig config = SimulationConfig.builder().mapScale(1)
            .randomSeed(1111L).terrainSeed(20260629L).build();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        Map<String, Integer> moveEvents = new LinkedHashMap<>();
        Map<String, Integer> moveTransitions = new LinkedHashMap<>();
        simulator.addEventListener(event -> {
            if(event.type == SimulationEvent.EventType.MOVE) {
                incrementStepActor(moveEvents, event.step, event.actorId);
            }
        });
        simulator.getContext().addTransitionObservationListener(observation -> {
            if(observation.getStatus() == TransitionStatus.MOVED ||
               observation.getStatus() == TransitionStatus.REROUTED) {
                incrementStepActor(moveTransitions, observation.getStep(),
                    observation.getAnimalId());
            }
        });
        for(int step = 0; step < 8; step++) {
            simulator.simulateOneStep();
        }
        TestSupport.assertTrue("R2 canonical move event IDs equal transition IDs",
            moveEvents.keySet().equals(moveTransitions.keySet()));
        boolean oneEach = true;
        for(String key : moveEvents.keySet()) {
            oneEach = oneEach && moveEvents.get(key).intValue() == 1 &&
                moveTransitions.getOrDefault(key, Integer.valueOf(0)).intValue() == 1;
        }
        TestSupport.assertTrue("R2 each successful move emits one event and transition",
            oneEach);
        TestSupport.assertTrue("R2 canonical move run keeps field consistent",
            simulator.getField().hasConsistentState());
    }

    private static void testThirstOffAndOnKeepOneConsistentBoard()
    {
        boolean consistent = true;
        boolean noPositionDeaths = true;
        for(boolean thirst : new boolean[] {false, true}) {
            SimulationConfig config = SimulationConfig.builder().mapScale(1)
                .thirstEnabled(thirst).randomSeed(1111L).terrainSeed(20260629L)
                .build();
            Simulator simulator = new Simulator(config, false);
            simulator.setVerbose(false);
            for(int step = 0; step < 12 && simulator.getField().isViable(); step++) {
                simulator.simulateOneStep();
                consistent = consistent && simulator.getField().hasConsistentState();
            }
            Map<DeathCause, Integer> deaths = simulator.getContext().getDeathCounts();
            noPositionDeaths = noPositionDeaths &&
                deaths.getOrDefault(DeathCause.POSITION_CONFLICT, 0) == 0 &&
                deaths.getOrDefault(DeathCause.UNKNOWN, 0) == 0;
        }
        TestSupport.assertTrue("R2 thirst off/on retain one consistent position board",
            consistent);
        TestSupport.assertTrue("R2 thirst off/on have no position deaths",
            noPositionDeaths);
    }

    private static BreedingSetup blockedBreedingSetup()
    {
        SimulationContext context = new SimulationContext(24, 24,
            SimulationConfig.baseline());
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Field field = new Field(24, 24);
        Location origin = originWithPassableNeighbors(context.getTerrainMap());
        R2FixtureAnimal parent = fixtureAnimal(Sex.FEMALE, origin, identities);
        field.placeAnimal(parent, origin);
        boolean malePlaced = false;
        for(Location location : passableNeighbors(context.getTerrainMap(), origin)) {
            R2FixtureAnimal owner = fixtureAnimal(malePlaced ? Sex.FEMALE : Sex.MALE,
                location, identities);
            malePlaced = true;
            field.placeAnimal(owner, location);
        }
        context.startStep(1, field);
        return new BreedingSetup(context, parent, identities,
            new PositionStepCoordinator(field, context));
    }

    private static BreedingSetup openBreedingSetup()
    {
        SimulationContext context = new SimulationContext(24, 24,
            SimulationConfig.baseline());
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Field field = new Field(24, 24);
        Location origin = originWithPassableNeighbors(context.getTerrainMap());
        R2FixtureAnimal parent = fixtureAnimal(Sex.FEMALE, origin, identities);
        field.placeAnimal(parent, origin);
        Location mateLocation = passableNeighbors(context.getTerrainMap(), origin)
            .get(0);
        field.placeAnimal(fixtureAnimal(Sex.MALE, mateLocation, identities),
            mateLocation);
        context.startStep(1, field);
        return new BreedingSetup(context, parent, identities,
            new PositionStepCoordinator(field, context));
    }

    private static R2FixtureAnimal fixtureAnimal(Sex sex, Location location,
                                                 AnimalIdentityAllocator identities)
    {
        R2FixtureAnimal animal = new R2FixtureAnimal(location,
            new FixedSexRandom(sex), identities);
        TestSupport.setField(animal, "age", PROFILE.getAdultAge());
        TestSupport.setField(animal, "foodLevel", PROFILE.getMaxFoodLevel());
        TestSupport.setField(animal, "stamina", SavannahAnimal.MAX_STAMINA);
        return animal;
    }

    private static Location originWithPassableNeighbors(TerrainMap terrain)
    {
        for(int row = 1; row < terrain.getDepth() - 1; row++) {
            for(int col = 1; col < terrain.getWidth() - 1; col++) {
                Location location = new Location(row, col);
                if(terrain.isPassable(location) &&
                   !passableNeighbors(terrain, location).isEmpty()) {
                    return location;
                }
            }
        }
        throw new IllegalStateException("R2 fixture terrain has no land pair");
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

    private static int eventCount(SimulationContext context,
                                  SimulationEvent.EventType type)
    {
        int count = 0;
        for(SimulationEvent event : context.getCurrentEvents()) {
            if(event.type == type) count++;
        }
        return count;
    }

    private static int transitionCount(List<TransitionObservation> observations,
                                       TransitionStatus status)
    {
        int count = 0;
        for(TransitionObservation observation : observations) {
            if(observation.getStatus() == status) count++;
        }
        return count;
    }

    private static void incrementStepActor(Map<String, Integer> counts, int step,
                                           long id)
    {
        String key = step + ":" + id;
        counts.put(key, Integer.valueOf(counts.getOrDefault(key,
            Integer.valueOf(0)).intValue() + 1));
    }

    private static final class BreedingSetup
    {
        private final SimulationContext context;
        private final R2FixtureAnimal parent;
        private final AnimalIdentityAllocator identities;
        private final PositionStepCoordinator coordinator;

        private BreedingSetup(SimulationContext context, R2FixtureAnimal parent,
                              AnimalIdentityAllocator identities,
                              PositionStepCoordinator coordinator)
        {
            this.context = context;
            this.parent = parent;
            this.identities = identities;
            this.coordinator = coordinator;
        }
    }

    private static final class FixedSexRandom extends Random
    {
        private final boolean male;

        private FixedSexRandom(Sex sex)
        {
            male = sex == Sex.MALE;
        }

        @Override
        public boolean nextBoolean()
        {
            return male;
        }
    }

    private static final class R2FixtureAnimal extends SavannahAnimal
    {
        private R2FixtureAnimal(Location location, Random random,
                                AnimalIdentityAllocator identities)
        {
            super(PROFILE, false, location, random, identities);
        }

        @Override
        public SavannahAnimal createChild(Location location)
        {
            return new R2FixtureAnimal(location, getRandom(),
                getIdentityAllocator());
        }
    }
}
