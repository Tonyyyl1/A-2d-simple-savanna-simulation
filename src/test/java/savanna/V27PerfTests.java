package savanna;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/** Fixed-input equivalence fixtures for V2.7 performance-only changes. */
public final class V27PerfTests
{
    private V27PerfTests()
    {
    }

    public static void run()
    {
        TestSupport.startGroup("V2.7 performance identity equivalence");
        testDirectLowercaseHexEncoding();
        testHuntAndFrameSha256Identity();
        testLegacyTerrainFingerprint();
        testFixedSimulationFingerprintTrace();
        testLocalReachabilityMatchesLegacyFullMapReference();
        testDetachedPassableRegionStaysUnreachable();
        testStaticWaterTopologyUsesMapIdentityOnly();
        testCurrentEventActorIndexPreservesExactBoundary();
        testLivePopulationTracksCommittedDeathsAndBirths();
        testLivePopulationCoversEveryDeathPathExactlyOnce();
        testInteractionGeometryRetainsCapturedLivingPopulation();
        testHeadlessSnapshotDemandIsExplicitAndReportIndependent();
        testResolvedTerrainProfileDrivesSimulationMapIdentity();
        testProceduralSimulationFrameSharesOverviewAndPngTerrainIdentity();
        V27DiagnosticsToolTests.run();
        TestSupport.finishGroup("V27_PERF");
    }

    private static void testDirectLowercaseHexEncoding()
    {
        TestSupport.assertEquals("V27 PERF direct hex preserves all nibble boundaries",
            "00010f107f80ff", HexEncoding.lowercase(new byte[] {
                0x00, 0x01, 0x0f, 0x10, 0x7f, (byte)0x80, (byte)0xff
            }));
        TestSupport.assertEquals("V27 PERF direct hex preserves empty input", "",
            HexEncoding.lowercase(new byte[0]));
    }

    private static void testHuntAndFrameSha256Identity()
    {
        TestSupport.assertEquals("V27 PERF HUNT SHA-256 empty input is byte-exact",
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            HuntOpportunityFact.sha256(""));
        TestSupport.assertEquals("V27 PERF frame SHA-256 abc is byte-exact",
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            HuntOpportunityFact.sha256("abc"));
    }

    private static void testLegacyTerrainFingerprint()
    {
        TerrainMap legacy = new TerrainMap(80, 120, TerrainMap.DEFAULT_SEED);
        TestSupport.assertEquals("V27 PERF legacy terrain fingerprint is byte-exact",
            "bd258ccf51259bb7350942b8bfb9c3e1ad9c022f7dbad5009b9d4a4ca6dd775b",
            legacy.getFingerprint());
    }

    private static void testFixedSimulationFingerprintTrace()
    {
        SimulationConfig config = SimulationConfig.builder().randomSeed(1111L)
            .terrainSeed(20260629L).build();
        Randomizer.reset();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        String[] expected = {
            // Deterministic steps 1-3, updated whenever EcologyRules.VERSION
            // changes. Intentional trace migration: v3.2-c4-l4
            "929286d8d01332fe925a3fbef336b57409e1b22ce14d77382e6a0c7bb2f7423a",
            "31554b2f38bc624fd8185f108b7429f06bc2b6e4e2f50077fbc6548304f2b741",
            "5f3d35db85543f414a93855fa720f1cab9f7f8d40b162c987eede4a2efea08db"
        };
        for(int index = 0; index < expected.length; index++) {
            simulator.simulateOneStep();
            TestSupport.assertEquals("V27 PERF fixed simulation fingerprint step " +
                (index + 1), expected[index],
                SimulationFingerprint.capture(simulator));
        }
    }

    private static void testLocalReachabilityMatchesLegacyFullMapReference()
    {
        TerrainMap terrain = terrain(
            "#########",
            "#.......#",
            "#######.#",
            "#.......#",
            "#.#######",
            "#.......#",
            "#########");
        Location origin = new Location(1, 1);
        int range = 12;
        int[][] expected = legacyDistances(terrain, origin, range);
        TerrainNavigator.Reachability actual = TerrainNavigator.reachableDistances(
            terrain, origin, range);
        assertDistances("V27 PERF local workspace", expected, actual);

        List<Location> expectedReachable = locationsWithin(expected, 1, range);
        TestSupport.assertEquals("V27 PERF local workspace preserves row-major candidate order",
            expectedReachable, TerrainNavigator.reachableWithin(terrain, origin,
                range));

        Field field = new Field(terrain.getDepth(), terrain.getWidth());
        Location occupied = new Location(1, 2);
        Gazelle blocker = new Gazelle(false, occupied);
        field.placeAnimal(blocker, occupied);
        List<Location> expectedFree = new ArrayList<>();
        for(Location location : expectedReachable) {
            if(!location.equals(occupied)) expectedFree.add(location);
        }
        Collections.shuffle(expectedFree, new Random(77L));
        TestSupport.assertEquals("V27 PERF local workspace preserves shuffled free candidate order",
            expectedFree, TerrainNavigator.freeReachableWithin(field, terrain,
                origin, range, new Random(77L)));
    }

    private static void testDetachedPassableRegionStaysUnreachable()
    {
        TerrainMap terrain = terrain(
            "...####",
            "...####",
            "...####",
            "####...",
            "####...",
            "####...");
        Location origin = new Location(1, 1);
        int range = 8;
        int[][] expected = legacyDistances(terrain, origin, range);
        TerrainNavigator.Reachability actual = TerrainNavigator.reachableDistances(
            terrain, origin, range);
        assertDistances("V27 PERF detached passable region", expected, actual);
        TestSupport.assertEquals("V27 PERF detached passable target remains unreachable",
            -1, actual.distanceTo(new Location(4, 5)));
        TestSupport.assertEquals("V27 PERF water target remains unreachable", -1,
            actual.distanceTo(new Location(2, 3)));
    }

    private static void testStaticWaterTopologyUsesMapIdentityOnly()
    {
        TerrainMap first = new TerrainMap(80, 120, 20260629L);
        TerrainMap sameIdentity = new TerrainMap(80, 120, 20260629L);
        TerrainMap differentIdentity = new TerrainMap(80, 120, 20260630L);
        WaterTopology firstTopology = WaterTopology.forTerrain(first);
        TestSupport.assertTrue("V27 PERF water topology reuses matching map identity",
            firstTopology == WaterTopology.forTerrain(sameIdentity));
        TestSupport.assertFalse("V27 PERF water topology rejects changed map identity",
            firstTopology == WaterTopology.forTerrain(differentIdentity));
        TestSupport.assertTrue("V27 PERF water topology records matching map identity",
            firstTopology.matches(sameIdentity));
        TestSupport.assertFalse("V27 PERF water topology invalidates changed map identity",
            firstTopology.matches(differentIdentity));

        SimulationConfig config = SimulationConfig.builder().mapScale(1)
            .thirstEnabled(true).randomSeed(1111L).terrainSeed(20260629L).build();
        SimulationContext context = new SimulationContext(80, 120, config);
        Field field = new Field(80, 120);
        context.startStep(1, field);
        TestSupport.assertTrue("V27 PERF context shares one static water topology with thirst",
            context.getWaterTopology() != null && context.getWaterholeCongestionAssessment()
                .getTopology() == context.getWaterTopology());
        TestSupport.assertEquals("V27 PERF shared static water topology keeps pool count", 2,
            context.getWaterTopology().getSourceIndex().getPoolCount());
    }

    private static void testCurrentEventActorIndexPreservesExactBoundary()
    {
        Field field = new Field(4, 4);
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Gazelle first = new Gazelle(false, new Location(1, 1), new Random(1L),
            identities);
        Gazelle second = new Gazelle(false, new Location(2, 2), new Random(2L),
            identities);
        field.placeAnimal(first, first.getLocation());
        field.placeAnimal(second, second.getLocation());
        SimulationContext context = new SimulationContext(4, 4);
        context.startStep(1, field);
        context.recordEvent(SimulationEvent.graze(1, first, first.getLocation()));
        int boundary = context.getCurrentEvents().size();
        context.recordEvent(SimulationEvent.drink(1, second, second.getLocation()));
        context.recordEvent(SimulationEvent.drink(1, first, first.getLocation()));
        TestSupport.assertFalse("V27 PERF actor event index excludes earlier event",
            context.hasCurrentActorEventSince(boundary,
                SimulationEvent.EventType.GRAZE, first.getId()));
        TestSupport.assertTrue("V27 PERF actor event index includes later matching event",
            context.hasCurrentActorEventSince(boundary,
                SimulationEvent.EventType.DRINK, first.getId()));
        TestSupport.assertFalse("V27 PERF actor event index excludes other actor",
            context.hasCurrentActorEventSince(boundary,
                SimulationEvent.EventType.DRINK, second.getId() + 1000L));
        context.startStep(2, field);
        TestSupport.assertFalse("V27 PERF actor event index resets each step",
            context.hasCurrentActorEventSince(0,
                SimulationEvent.EventType.DRINK, first.getId()));
    }

    private static void testLivePopulationTracksCommittedDeathsAndBirths()
    {
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Field field = new Field(4, 4);
        Gazelle parent = new Gazelle(false, new Location(1, 1), new Random(1L),
            identities);
        Zebra zebra = new Zebra(false, new Location(2, 2), new Random(2L),
            identities);
        field.placeAnimal(parent, parent.getLocation());
        field.placeAnimal(zebra, zebra.getLocation());
        SimulationContext context = new SimulationContext(4, 4);
        context.startStep(1, field);
        TestSupport.assertEquals("V27 PERF live population begins at step-start count", 1,
            context.getLivePopulation(SpeciesRegistry.GAZELLE));
        TestSupport.assertTrue("V27 PERF live population records first death",
            context.recordDeath(parent, DeathCause.PREDATION));
        TestSupport.assertEquals("V27 PERF live population excludes same-step death", 0,
            context.getLivePopulation(SpeciesRegistry.GAZELLE));
        TestSupport.assertEquals("V27 PERF step-start population remains auditable", 1,
            context.getStepStartPopulation(SpeciesRegistry.GAZELLE));
        TestSupport.assertFalse("V27 PERF repeated death cannot decrement twice",
            context.recordDeath(parent, DeathCause.PREDATION));
        TestSupport.assertEquals("V27 PERF repeated death retains live count", 0,
            context.getLivePopulation(SpeciesRegistry.GAZELLE));
        Gazelle child = new Gazelle(false, new Location(1, 2), new Random(3L),
            identities);
        field.placeAnimal(child, child.getLocation());
        context.recordCommittedBirthFact(parent, child, 1, child.getLocation());
        TestSupport.assertEquals("V27 PERF committed birth updates live population", 1,
            context.getLivePopulation(SpeciesRegistry.GAZELLE));
        TestSupport.assertEquals("V27 PERF other species live count is unchanged", 1,
            context.getLivePopulation(SpeciesRegistry.ZEBRA));
    }

    private static void testLivePopulationCoversEveryDeathPathExactlyOnce()
    {
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Field field = new Field(6, 6);
        List<Gazelle> animals = new ArrayList<>();
        DeathCause[] causes = { DeathCause.PREDATION, DeathCause.DISEASE,
            DeathCause.AGE, DeathCause.STARVATION, DeathCause.DEHYDRATION };
        for(int index = 0; index < causes.length; index++) {
            Gazelle animal = new Gazelle(false, new Location(1 + index, 1),
                new Random(10L + index), identities);
            field.placeAnimal(animal, animal.getLocation());
            animals.add(animal);
        }
        SimulationContext context = new SimulationContext(6, 6);
        context.startStep(1, field);
        for(int index = 0; index < causes.length; index++) {
            TestSupport.assertTrue("V27 PERF live population accepts " + causes[index],
                context.recordDeath(animals.get(index), causes[index]));
            TestSupport.assertEquals("V27 PERF live population decrements " +
                causes[index], causes.length - index - 1,
                context.getLivePopulation(SpeciesRegistry.GAZELLE));
        }
        TestSupport.assertFalse("V27 PERF repeated terminal death remains rejected",
            context.recordDeath(animals.get(0), DeathCause.PREDATION));
        TestSupport.assertEquals("V27 PERF repeated terminal death keeps zero", 0,
            context.getLivePopulation(SpeciesRegistry.GAZELLE));
    }

    private static void testInteractionGeometryRetainsCapturedLivingPopulation()
    {
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Field field = new Field(4, 4);
        Gazelle first = new Gazelle(false, new Location(1, 1), new Random(1L),
            identities);
        Zebra second = new Zebra(false, new Location(2, 2), new Random(2L),
            identities);
        field.placeAnimal(first, first.getLocation());
        field.placeAnimal(second, second.getLocation());
        InteractionGeometry geometry = StepStartInteractionGeometry.capture(field);
        TestSupport.assertEquals("V27 PERF interaction geometry preserves captured living count",
            2, geometry.getLivingPopulation());
    }

    private static void testHeadlessSnapshotDemandIsExplicitAndReportIndependent()
    {
        Simulator simulator = new Simulator(SimulationConfig.baseline(), false);
        simulator.setVerbose(false);
        TestSupport.assertFalse("V27 PERF headless simulation starts without frame demand",
            simulator.isRenderFrameCaptureEnabled());
        TestSupport.assertFalse("V27 PERF headless simulation has no durable report sink",
            simulator.hasPersistentReportRecorder());
        simulator.simulateOneStep();
        TestSupport.assertFalse("V27 PERF headless step does not invent frame demand",
            simulator.isRenderFrameCaptureEnabled());
        RenderFrame current = simulator.getRenderFramePublisher().latest();
        TestSupport.assertTrue("V27 PERF explicit publisher access enables frame demand",
            simulator.isRenderFrameCaptureEnabled());
        TestSupport.assertEquals("V27 PERF explicit publisher receives current frame", 1,
            current.step);
        TestSupport.assertTrue("V27 PERF explicit latest frame is a visible snapshot",
            current.heatmapSnapshot != null && current.heatmapSnapshot.getFrameStep() == 1);
    }

    private static void testResolvedTerrainProfileDrivesSimulationMapIdentity()
    {
        SimulationConfig legacy = SimulationConfig.builder().mapScale(1)
            .terrainSeed(20260629L)
            .terrainGenerationProfile(TerrainGenerationProfile.LEGACY_V1).build();
        SimulationConfig procedural = SimulationConfig.builder().mapScale(1)
            .terrainSeed(4201L)
            .terrainGenerationProfile(TerrainGenerationProfile.PROCEDURAL_V2).build();
        ResolvedRuntimeConfig resolved = ResolvedRuntimeConfig.fromLegacy(procedural);
        TestSupport.assertTrue("V27 TERRAIN canonical config records profile",
            resolved.getCanonicalForm().contains(
                "ecology.terrainGenerationProfile=PROCEDURAL_V2"));
        TestSupport.assertEquals("V27 TERRAIN canonical profile round-trip",
            TerrainGenerationProfile.PROCEDURAL_V2,
            ResolvedRuntimeConfig.fromCanonicalForm(resolved.getCanonicalForm())
                .getEcologyConfig().getTerrainGenerationProfile());
        SimulationContext legacyContext = new SimulationContext(80, 120, legacy);
        SimulationContext proceduralContext = new SimulationContext(80, 120, resolved);
        TestSupport.assertEquals("V27 TERRAIN legacy config preserves legacy map",
            TerrainGenerationProfile.LEGACY_V1,
            legacyContext.getTerrainMap().getGenerationProfile());
        TestSupport.assertEquals("V27 TERRAIN resolved procedural profile reaches simulation",
            TerrainGenerationProfile.PROCEDURAL_V2,
            proceduralContext.getTerrainMap().getGenerationProfile());
        TestSupport.assertEquals("V27 TERRAIN procedural simulation uses requested seed", 4201L,
            proceduralContext.getTerrainMap().getSeed());
        TestSupport.assertTrue("V27 TERRAIN profile changes logical map identity",
            !legacyContext.getTerrainMap().getFingerprint().equals(
                proceduralContext.getTerrainMap().getFingerprint()));
    }

    private static void testProceduralSimulationFrameSharesOverviewAndPngTerrainIdentity()
    {
        SimulationConfig config = SimulationConfig.builder().mapScale(1)
            .terrainSeed(4201L)
            .terrainGenerationProfile(TerrainGenerationProfile.PROCEDURAL_V2).build();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        RenderFrame frame = simulator.getRenderFramePublisher().latest();
        TerrainBackgroundCache cache = new TerrainBackgroundCache();
        OverviewRenderResult overview = new OverviewRenderer(cache).render(frame,
            new RenderOptions(480, 320, false, false));
        OverviewRenderResult png = RenderSnapshotTool.renderLayers(frame, 480, 320,
            false, HeatmapSelection.defaultSelection(), cache);
        TestSupport.assertEquals("V27 TERRAIN procedural frame carries resolved profile",
            TerrainGenerationProfile.PROCEDURAL_V2,
            frame.terrainMap.getGenerationProfile());
        TestSupport.assertEquals("V27 TERRAIN provenance matches procedural generator",
            TerrainGenerationProfile.PROCEDURAL_V2.getGeneratorVersion(),
            frame.provenance.getTerrainGeneratorVersion());
        TestSupport.assertEquals("V27 TERRAIN provenance records resolved profile",
            TerrainGenerationProfile.PROCEDURAL_V2.name(),
            frame.provenance.getTerrainProfile());
        TestSupport.assertEquals("V27 TERRAIN overview and PNG use simulation fingerprint",
            frame.terrainMap.getFingerprint(), frame.provenance.getTerrainFingerprint());
        TestSupport.assertTrue("V27 TERRAIN overview and PNG share terrain bitmap",
            overview.getTerrainImage() == png.getTerrainImage());
    }

    private static TerrainMap terrain(String... rows)
    {
        TerrainType[][] cells = new TerrainType[rows.length][rows[0].length()];
        for(int row = 0; row < rows.length; row++) {
            if(rows[row].length() != cells[row].length) {
                throw new IllegalArgumentException("test terrain must be rectangular");
            }
            for(int col = 0; col < cells[row].length; col++) {
                cells[row][col] = rows[row].charAt(col) == '#'
                    ? TerrainType.WATERHOLE : TerrainType.OPEN_PLAIN;
            }
        }
        return new FixedTerrainMap(cells);
    }

    private static int[][] legacyDistances(TerrainMap terrain, Location origin,
                                           int range)
    {
        int depth = terrain.getDepth();
        int width = terrain.getWidth();
        int[][] distances = new int[depth][width];
        for(int row = 0; row < depth; row++) {
            java.util.Arrays.fill(distances[row], -1);
        }
        if(origin == null || origin.row() < 0 || origin.row() >= depth ||
           origin.col() < 0 || origin.col() >= width ||
           !terrain.isPassable(origin)) return distances;
        Queue<Location> queue = new ArrayDeque<>();
        distances[origin.row()][origin.col()] = 0;
        queue.add(origin);
        while(!queue.isEmpty()) {
            Location current = queue.remove();
            int currentDistance = distances[current.row()][current.col()];
            if(currentDistance >= range) continue;
            for(int rowDelta = -1; rowDelta <= 1; rowDelta++) {
                for(int colDelta = -1; colDelta <= 1; colDelta++) {
                    if(rowDelta == 0 && colDelta == 0) continue;
                    int row = current.row() + rowDelta;
                    int col = current.col() + colDelta;
                    Location next = new Location(row, col);
                    if(row >= 0 && row < depth && col >= 0 && col < width &&
                       distances[row][col] < 0 && terrain.isPassable(next)) {
                        distances[row][col] = currentDistance + 1;
                        queue.add(next);
                    }
                }
            }
        }
        return distances;
    }

    private static void assertDistances(String name, int[][] expected,
                                        TerrainNavigator.Reachability actual)
    {
        for(int row = 0; row < expected.length; row++) {
            for(int col = 0; col < expected[row].length; col++) {
                TestSupport.assertEquals(name + " distance " + row + "," + col,
                    expected[row][col], actual.distanceTo(new Location(row, col)));
            }
        }
    }

    private static List<Location> locationsWithin(int[][] distances, int minimum,
                                                  int maximum)
    {
        List<Location> result = new ArrayList<>();
        for(int row = 0; row < distances.length; row++) {
            for(int col = 0; col < distances[row].length; col++) {
                if(distances[row][col] >= minimum &&
                   distances[row][col] <= maximum) {
                    result.add(new Location(row, col));
                }
            }
        }
        return result;
    }

    private static final class FixedTerrainMap extends TerrainMap
    {
        private final TerrainType[][] cells;

        private FixedTerrainMap(TerrainType[][] cells)
        {
            super(cells.length, cells[0].length, 1L);
            this.cells = cells;
        }

        @Override
        public boolean isPassable(Location location)
        {
            return location != null && isPassableAt(location.row(),
                location.col());
        }

        @Override
        boolean isPassableAt(int row, int col)
        {
            return row >= 0 && row < cells.length && col >= 0 &&
                col < cells[row].length && cells[row][col] != TerrainType.WATERHOLE;
        }
    }
}
