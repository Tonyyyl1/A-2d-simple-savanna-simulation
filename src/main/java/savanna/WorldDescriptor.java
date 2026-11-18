package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable description of a run's world for frame consumers (plan v3.2 V1). */
public final class WorldDescriptor
{
    /** One row of the species table. */
    public static final class SpeciesEntry
    {
        public final int code;
        public final String name;
        public final boolean predator;
        public final double adultAgeShare;
        public final double targetCount;
        public final int moveSpeed;

        SpeciesEntry(int code, String name, boolean predator, double adultAgeShare,
                     double targetCount, int moveSpeed)
        {
            this.code = code;
            this.name = name;
            this.predator = predator;
            this.adultAgeShare = adultAgeShare;
            this.targetCount = targetCount;
            this.moveSpeed = moveSpeed;
        }
    }

    public final int runGeneration;
    public final int depth;
    public final int width;
    public final long ecologySeed;
    public final long terrainSeed;
    public final String terrainProfile;
    public final String terrainFingerprint;
    public final boolean thirstEnabled;
    public final String rulesVersion;
    public final String paramsHash;
    /** One byte per cell, row-major: 0 WATERHOLE, 1 GRASSLAND, 2 BUSH, 3 OPEN_PLAIN, 4 DRY_SOIL. */
    private final byte[] terrain;
    private final byte[] heightBytes;
    private final byte[] bodyBytes;
    /** Water level of each body, index 0 is body 1. */
    private final float[] waterLevels;
    public final List<SpeciesEntry> species;

    private WorldDescriptor(int runGeneration, SimulationContext context)
    {
        this.runGeneration = runGeneration;
        TerrainMap map = context.getTerrainMap();
        depth = map.getDepth();
        width = map.getWidth();
        SimulationConfig config = context.getConfig();
        ecologySeed = config.getRandomSeed();
        terrainSeed = config.getTerrainSeed();
        terrainProfile = config.getTerrainGenerationProfile().name();
        terrainFingerprint = map.getFingerprint();
        thirstEnabled = config.isThirstEnabled();
        rulesVersion = EcologyRules.VERSION;
        paramsHash = EcologyParameters.current().hash();
        terrain = new byte[depth * width];
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                terrain[row * width + col] =
                    (byte)terrainCode(map.getTerrainAt(new Location(row, col)));
            }
        }
        TerrainRelief relief = TerrainRelief.generate(map, terrainSeed);
        float[] heights = relief.heights();
        java.nio.ByteBuffer packed = java.nio.ByteBuffer.allocate(heights.length * 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for(float value : heights) packed.putFloat(value);
        heightBytes = packed.array();
        bodyBytes = relief.bodies();
        waterLevels = new float[relief.getBodyCount()];
        for(int id = 1; id <= waterLevels.length; id++) waterLevels[id - 1] = relief.getWaterLevel(id);
        List<SpeciesEntry> entries = new ArrayList<>();
        int code = 0;
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            SpeciesProfile profile = factory.getProfile();
            entries.add(new SpeciesEntry(code++, profile.getName(), profile.isPredator(),
                (double)profile.getAdultAge() / profile.getMaxAge(),
                EcologyTargets.target(profile.getName()),
                SavannahAnimal.maxMoveDistanceOf(profile)));
        }
        species = Collections.unmodifiableList(entries);
    }

    static WorldDescriptor capture(int runGeneration, SimulationContext context)
    {
        return new WorldDescriptor(runGeneration, context);
    }

    /** Copy of the terrain grid (see {@link #terrainCode}). */
    public byte[] terrainBytes()
    {
        return terrain.clone();
    }

    /** Heights as little-endian float32, (2 depth + 1) x (2 width + 1), row-major (metres). */
    public byte[] heightBytes()
    {
        return heightBytes.clone();
    }

    /** Water body id per cell (0 = land), row-major. */
    public byte[] bodyBytes()
    {
        return bodyBytes.clone();
    }

    public float[] waterLevels()
    {
        return waterLevels.clone();
    }

    /** Explicit protocol code of a terrain type (never the enum ordinal). */
    public static int terrainCode(TerrainType type)
    {
        switch(type) {
            case WATERHOLE: return 0;
            case GRASSLAND: return 1;
            case BUSH: return 2;
            case OPEN_PLAIN: return 3;
            case DRY_SOIL: return 4;
            default: throw new IllegalArgumentException("no protocol code for " + type);
        }
    }
}
