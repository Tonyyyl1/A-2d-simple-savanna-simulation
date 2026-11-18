package savanna;

/** Real terrain-kit and procedural-map validation for the V2.7 terrain group. */
public final class V27TerrainTests
{
    private V27TerrainTests()
    {
    }

    public static void run()
    {
        TestSupport.startGroup("V2.7 terrain identity and assets");
        TerrainVisualTv2Tests.main(new String[0]);
        TerrainProceduralPreviewTests.main(new String[0]);
        SimulationConfig config = SimulationConfig.builder().mapScale(1)
            .terrainSeed(4201L)
            .terrainGenerationProfile(TerrainGenerationProfile.PROCEDURAL_V2)
            .build();
        Simulator simulator = new Simulator(config, false);
        RenderFrame frame = simulator.getRenderFramePublisher().latest();
        TestSupport.assertEquals("V27 TERRAIN real simulation profile", 
            TerrainGenerationProfile.PROCEDURAL_V2,
            simulator.getContext().getTerrainMap().getGenerationProfile());
        TestSupport.assertEquals("V27 TERRAIN frame uses real simulation fingerprint",
            simulator.getContext().getTerrainMap().getFingerprint(),
            frame.provenance.getTerrainFingerprint());
        TestSupport.finishGroup("V27_TERRAIN");
    }
}
