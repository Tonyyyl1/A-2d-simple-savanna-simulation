package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** v3.2 B-1: predators chase along land at their own speed instead of teleporting. */
public final class ChaseMovementTests
{
    private static final int DAY_STEP = 34;

    private ChaseMovementTests()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("B-1 chase movement");
        run();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    public static void run()
    {
        TestSupport.startGroup("B-1 chase movement");
        testApproachStationaryPrey();
        testDetourAroundWater();
        testValidatorRejectsTeleport();
        testHungrySearchLeavesTheArea();
        TestSupport.finishGroup("B1_CHASE");
    }

    /** Six cells from still prey with speed 3: one step closes to 3, never onto the prey. */
    private static void testApproachStationaryPrey()
    {
        SimulationContext context = new SimulationContext(240, 360,
            SimulationConfig.builder().mapScale(3).randomSeed(9L).build());
        Location origin = openRegion(context.getTerrainMap(), 7, 12);
        Location preyAt = new Location(origin.row(), origin.col() + 6);
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Cheetah cheetah = new Cheetah(false, origin, new Random(3L), identities);
        TestSupport.setField(cheetah, "age", Integer.valueOf(cheetah.getProfile().getAdultAge()));
        TestSupport.setField(cheetah, "foodLevel", Integer.valueOf(20));
        Gazelle gazelle = new Gazelle(false, preyAt, new Random(4L), identities);
        List<HuntOutcome> outcomes = new ArrayList<>();
        context.addHuntObservationListener(new HuntObservationListener() {
            @Override
            public void onHuntResult(HuntObservation observation)
            {
                outcomes.add(observation.getResult().getOutcome());
            }
        });
        Location previous = origin;
        int maxStepMove = 0;
        Location afterFirst = null;
        boolean struck = false;
        for(int index = 0; index < 4 && !struck; index++) {
            savanna.Field field = new savanna.Field(240, 360);
            field.placeAnimal(cheetah, previous);
            if(gazelle.isAlive()) field.placeAnimal(gazelle, preyAt);
            context.startStep(DAY_STEP + index, field);
            PositionStepCoordinator board = new PositionStepCoordinator(field, context);
            board.propose(cheetah, board.createAvailabilityView());
            board.resolveAndCommit();
            Location now = cheetah.getLocation();
            maxStepMove = Math.max(maxStepMove, chebyshev(previous, now));
            if(index == 0) afterFirst = now;
            previous = now;
            struck = !outcomes.isEmpty() && outcomes.get(outcomes.size() - 1) != HuntOutcome.OUT_OF_CONTACT;
        }
        TestSupport.assertTrue("B1 first step: the chaser is 3 cells from still prey, not on it",
            afterFirst != null && chebyshev(afterFirst, preyAt) == 3 && !afterFirst.equals(preyAt));
        TestSupport.assertTrue("B1 no step is longer than the Cheetah speed",
            maxStepMove <= EcologyParameters.current().getInt(EcologyParameters.Key.CHEETAH_SPEED));
        TestSupport.assertTrue("B1 the first hunt result is out of contact",
            !outcomes.isEmpty() && outcomes.get(0) == HuntOutcome.OUT_OF_CONTACT);
        TestSupport.assertTrue("B1 within 4 steps the hunt reaches an attempt or a kill",
            outcomes.contains(HuntOutcome.SUCCESS) || outcomes.contains(HuntOutcome.FAILED_ATTEMPT));
    }

    /** A short water wall stands between chaser and prey: every planned cell is land and the chase arrives. */
    private static void testDetourAroundWater()
    {
        TerrainMap terrain = new TerrainMap(30, 30, 7003L);
        TerrainType[][] cells = terrainCells(terrain);
        for(TerrainType[] row : cells) java.util.Arrays.fill(row, TerrainType.OPEN_PLAIN);
        for(int row = 8; row <= 12; row++) cells[row][7] = TerrainType.WATERHOLE;
        PositionTopology topology = PositionTopology.fromTerrain(terrain,
            PositionKernelPolicyRegistry.current());
        Location prey = new Location(10, 9);
        Location position = new Location(10, 5);
        Map<Long, Location> origins = new LinkedHashMap<>();
        origins.put(1L, position);
        origins.put(2L, prey);
        PositionAvailabilityView view = new PositionAvailabilityView(
            StepStartPositionSnapshot.of(origins), topology,
            Collections.<Location, Long>emptyMap());
        boolean allLand = true;
        boolean everyStepShorter = true;
        int steps = 0;
        while(chebyshev(position, prey) > 1 && steps < 8) {
            int before = TerrainNavigator.reachableDistances(terrain, prey, 12).distanceTo(position);
            ChasePlanner.Plan plan = ChasePlanner.plan(terrain, view, position, prey, 3, 7);
            if(plan.getTarget() == null) break;
            allLand &= terrain.isPassable(plan.getTarget());
            int after = TerrainNavigator.reachableDistances(terrain, prey, 12)
                .distanceTo(plan.getTarget());
            everyStepShorter &= after >= 1 && after < before;
            position = plan.getTarget();
            steps++;
        }
        TestSupport.assertTrue("B1 detour: every planned cell is land", allLand);
        TestSupport.assertTrue("B1 detour: every step shortens the land distance", everyStepShorter);
        TestSupport.assertTrue("B1 detour: the chaser ends adjacent to the prey within 4 steps",
            chebyshev(position, prey) <= 1 && steps <= 4);
    }

    /** The production validator rejects a five-cell displacement above a limit of three. */
    private static void testValidatorRejectsTeleport()
    {
        PositionTopology topology = PositionTopology.rectangular(12, 12,
            Collections.<Location>emptySet(), PositionKernelPolicyRegistry.current());
        Map<Long, Location> origins = new LinkedHashMap<>();
        origins.put(1L, new Location(5, 1));
        StepStartPositionSnapshot snapshot = StepStartPositionSnapshot.of(origins);
        PositionClaimBatch batch = PositionClaimBatch.builder()
            .add(PositionClaim.move(1L, PositionPriority.ORDINARY_MOVE, new Location(5, 6),
                Collections.<Location>emptyList(), 0, 0))
            .build();
        PositionPlan plan = new PurePositionReservationResolver(
            PositionKernelPolicyRegistry.current()).resolve(snapshot, batch, topology,
            Collections.<Location>emptySet(), 5L);
        TestSupport.assertTrue("B1 validator: limit 3 rejects a five-cell move",
            !PositionPlanValidator.validate(snapshot, plan, topology,
                Collections.<Location>emptySet(), batch.getReleased(), id -> 3).isValid());
        TestSupport.assertTrue("B1 validator: limit 5 accepts it",
            PositionPlanValidator.validate(snapshot, plan, topology,
                Collections.<Location>emptySet(), batch.getReleased(), id -> 5).isValid());
        TestSupport.assertTrue("B1 validator: the unlimited overload still accepts it",
            PositionPlanValidator.validate(snapshot, plan, topology,
                Collections.<Location>emptySet(), batch.getReleased()).isValid());
    }

    /** B-2: with no prey anywhere, a hungry Cheetah keeps a heading and leaves the area. */
    private static void testHungrySearchLeavesTheArea()
    {
        SimulationContext context = new SimulationContext(240, 360,
            SimulationConfig.builder().mapScale(3).randomSeed(11L).build());
        Location start = openRegion(context.getTerrainMap(), 41, 41);
        start = new Location(start.row(), start.col() + 18);
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Cheetah cheetah = new Cheetah(false, start, new Random(8L), identities);
        TestSupport.setField(cheetah, "age", Integer.valueOf(cheetah.getProfile().getAdultAge()));
        Location position = start;
        for(int index = 0; index < 30; index++) {
            TestSupport.setField(cheetah, "foodLevel", Integer.valueOf(40));
            savanna.Field field = new savanna.Field(240, 360);
            field.placeAnimal(cheetah, position);
            context.startStep(30 + index, field);
            PositionStepCoordinator board = new PositionStepCoordinator(field, context);
            board.propose(cheetah, board.createAvailabilityView());
            board.resolveAndCommit();
            position = cheetah.getLocation();
        }
        TestSupport.assertTrue("B2 a hungry searcher with no prey moves at least 15 cells in 30 steps (moved " +
            chebyshev(start, position) + ")", chebyshev(start, position) >= 15);
    }

    /** Top-left cell of a {@code height} x {@code width} block of land. */
    private static Location openRegion(TerrainMap terrain, int height, int width)
    {
        for(int row = 4; row + height < terrain.getDepth() - 4; row++) {
            for(int col = 4; col + width < terrain.getWidth() - 4; col++) {
                boolean clear = true;
                for(int dr = -3; dr < height && clear; dr++) {
                    for(int dc = -3; dc < width && clear; dc++) {
                        clear = terrain.isPassable(new Location(row + dr, col + dc));
                    }
                }
                if(clear) return new Location(row + height / 2, col);
            }
        }
        throw new IllegalStateException("no open land block");
    }

    private static int chebyshev(Location first, Location second)
    {
        return Math.max(Math.abs(first.row() - second.row()),
            Math.abs(first.col() - second.col()));
    }

    private static TerrainType[][] terrainCells(TerrainMap terrain)
    {
        try {
            java.lang.reflect.Field field = TerrainMap.class.getDeclaredField("terrain");
            field.setAccessible(true);
            return (TerrainType[][])field.get(terrain);
        }
        catch(ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
