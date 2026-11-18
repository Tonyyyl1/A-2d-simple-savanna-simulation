package savanna;

import java.util.Collections;

/**
 * Zero-step contract for sharing one completed terrain bitmap between the
 * overview, inspect frame boundary and fixed-PNG paths.
 */
public final class TerrainBackgroundCacheTests
{
    private TerrainBackgroundCacheTests()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("Terrain background cache tests");
        TestSupport.startGroup("TV3 shared terrain background cache");
        run();
        TestSupport.finishGroup();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    public static void run()
    {
        verifyDefaultRenderPathsShareOneBackground();

        TerrainBackgroundCache cache = new TerrainBackgroundCache();
        TerrainMap terrain = TerrainMap.proceduralPreview(80, 120, 4201L);
        RenderFrame frame = frameFor(terrain);
        int width = 480;
        int height = 320;
        OverviewRenderResult overview = new OverviewRenderer(cache).render(frame,
            new RenderOptions(width, height, false, false));

        OverviewFrameBuffer.CompletedFrame inspectFrame = inspectFrameFor(frame,
            overview, width, height);
        TestSupport.assertTrue("TV3 inspect frame retains overview background object",
            overview.getTerrainImage() == inspectFrame.getTerrainImage());

        OverviewRenderResult fixedPng = RenderSnapshotTool.renderLayers(frame,
            width, height, false, HeatmapSelection.defaultSelection(), cache);
        TestSupport.assertTrue("TV3 fixed PNG reuses overview background object",
            overview.getTerrainImage() == fixedPng.getTerrainImage());
        TestSupport.assertEquals("TV3 cache has one entry after three render paths", 1,
            cache.entryCount());

        TerrainMap reconstructed = TerrainMap.proceduralPreview(80, 120, 4201L);
        TestSupport.assertTrue("TV3 reconstructed preview is a distinct map object",
            terrain != reconstructed);
        OverviewRenderResult equivalent = new OverviewRenderer(cache).render(
            frameFor(reconstructed), new RenderOptions(width, height, false, false));
        TestSupport.assertTrue("TV3 logical terrain identity reuses cached bitmap",
            overview.getTerrainImage() == equivalent.getTerrainImage());
        TestSupport.assertEquals("TV3 equivalent preview does not add a cache entry", 1,
            cache.entryCount());

        OverviewRenderResult resized = new OverviewRenderer(cache).render(frame,
            new RenderOptions(600, 400, false, false));
        TestSupport.assertTrue("TV3 resize receives a distinct terrain bitmap",
            overview.getTerrainImage() != resized.getTerrainImage());

        TerrainMap differentSeed = TerrainMap.proceduralPreview(80, 120, 9317L);
        OverviewRenderResult different = new OverviewRenderer(cache).render(
            frameFor(differentSeed), new RenderOptions(width, height, false, false));
        TestSupport.assertTrue("TV3 different seed receives a distinct terrain bitmap",
            overview.getTerrainImage() != different.getTerrainImage());
        TestSupport.assertEquals("TV3 resize and seed changes create exact cache entries", 3,
            cache.entryCount());
        verifySeedCannotAliasThroughFingerprint();
    }

    private static void verifyDefaultRenderPathsShareOneBackground()
    {
        TerrainMap terrain = TerrainMap.proceduralPreview(80, 120, 17689L);
        RenderFrame frame = frameFor(terrain);
        int width = 480;
        int height = 320;
        OverviewRenderResult overview = new OverviewRenderer().render(frame,
            new RenderOptions(width, height, false, false));
        OverviewFrameBuffer.CompletedFrame inspectFrame = inspectFrameFor(frame,
            overview, width, height);
        OverviewRenderResult fixedPng = RenderSnapshotTool.renderLayers(frame,
            width, height, false, HeatmapSelection.defaultSelection());
        TestSupport.assertTrue("TV3 default inspect path retains overview background object",
            overview.getTerrainImage() == inspectFrame.getTerrainImage());
        TestSupport.assertTrue("TV3 default fixed PNG path shares overview background object",
            overview.getTerrainImage() == fixedPng.getTerrainImage());
    }

    private static void verifySeedCannotAliasThroughFingerprint()
    {
        TerrainBackgroundCache cache = new TerrainBackgroundCache();
        TerrainMap first = new FingerprintAliasTerrainMap(80, 120, 4201L,
            "forced-test-fingerprint");
        TerrainMap second = new FingerprintAliasTerrainMap(80, 120, 9317L,
            "forced-test-fingerprint");
        TestSupport.assertEquals("TV3 seed-key fixture has a forced fingerprint alias",
            first.getFingerprint(), second.getFingerprint());
        OverviewRenderResult firstResult = new OverviewRenderer(cache).render(
            frameFor(first), new RenderOptions(480, 320, false, false));
        OverviewRenderResult secondResult = new OverviewRenderer(cache).render(
            frameFor(second), new RenderOptions(480, 320, false, false));
        TestSupport.assertTrue("TV3 terrain seed prevents a fingerprint cache alias",
            firstResult.getTerrainImage() != secondResult.getTerrainImage());
        TestSupport.assertEquals("TV3 seed-key fixture creates two cache entries", 2,
            cache.entryCount());
    }

    private static RenderFrame frameFor(TerrainMap terrain)
    {
        return new RenderFrame(0, 0L, terrain.getSeed(), terrain.getWidth(),
            terrain.getDepth(), terrain, RenderMetrics.empty(),
            RenderProvenance.unavailable(),
            Collections.<RenderAnimalSnapshot>emptyList(),
            Collections.<SimulationEvent>emptyList());
    }

    private static OverviewFrameBuffer.CompletedFrame inspectFrameFor(
        RenderFrame frame, OverviewRenderResult overview, int width, int height)
    {
        VisualGridGeometry geometry = VisualGridGeometry.forTerrain(frame.terrainMap,
            width, height);
        VisualWaterMask waterMask = new VisualWaterMask(frame.terrainMap, width, height);
        return new OverviewFrameBuffer().completedFrame(0L, frame.step, width, height,
            overview.getTerrainImage(), frame.terrainMap, geometry, waterMask,
            overview.getOverlayImage(), overview.getDrawnAnimalCount(), true);
    }

    /** Deliberately aliases a fingerprint so the cache's seed key is exercised. */
    private static final class FingerprintAliasTerrainMap extends TerrainMap
    {
        private final String forcedFingerprint;

        private FingerprintAliasTerrainMap(int depth, int width, long seed,
                                           String forcedFingerprint)
        {
            super(depth, width, seed, TerrainGenerationProfile.PROCEDURAL_V2);
            this.forcedFingerprint = forcedFingerprint;
        }

        @Override
        public String getFingerprint()
        {
            return forcedFingerprint;
        }
    }
}
