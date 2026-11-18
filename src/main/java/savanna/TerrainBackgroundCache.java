package savanna;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reuses completed terrain layers across ordinary, inspect and PNG rendering.
 *
 * <p>The cached image is a finished background only: callers may composite it
 * into a new image but must never draw onto it.  The key deliberately uses the
 * logical terrain identity rather than {@link TerrainMap} object identity, so
 * separately reconstructed previews of the same versioned seed can share one
 * background without conflating a different generator, kit, size or grid.</p>
 */
final class TerrainBackgroundCache
{
    private static final int MAX_ENTRIES = 12;
    private static final TerrainBackgroundCache SHARED = new TerrainBackgroundCache();

    private final Map<CacheKey, BufferedImage> images =
        new LinkedHashMap<CacheKey, BufferedImage>(16, .75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, BufferedImage> eldest)
            {
                return size() > MAX_ENTRIES;
            }
        };

    static TerrainBackgroundCache shared()
    {
        return SHARED;
    }

    synchronized BufferedImage imageFor(TerrainMap terrainMap, int width, int height)
    {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        CacheKey key = CacheKey.forTerrain(terrainMap, safeWidth, safeHeight);
        BufferedImage cached = images.get(key);
        if(cached != null) {
            return cached;
        }

        BufferedImage terrain = new BufferedImage(safeWidth, safeHeight,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = terrain.createGraphics();
        try {
            if(terrainMap != null) {
                terrainMap.drawBackground(graphics, safeWidth, safeHeight);
            }
            else {
                graphics.setColor(TerrainType.OPEN_PLAIN.getColor());
                graphics.fillRect(0, 0, safeWidth, safeHeight);
            }
        }
        finally {
            graphics.dispose();
        }
        images.put(key, terrain);
        return terrain;
    }

    synchronized int entryCount()
    {
        return images.size();
    }

    private static final class CacheKey
    {
        private final String terrainFingerprint;
        private final String generatorVersion;
        private final String generationProfile;
        private final long terrainSeed;
        private final String kitVersion;
        private final int terrainDepth;
        private final int terrainWidth;
        private final int pixelWidth;
        private final int pixelHeight;

        private CacheKey(String terrainFingerprint, String generatorVersion,
                         String generationProfile, long terrainSeed, String kitVersion,
                         int terrainDepth, int terrainWidth,
                         int pixelWidth, int pixelHeight)
        {
            this.terrainFingerprint = terrainFingerprint;
            this.generatorVersion = generatorVersion;
            this.generationProfile = generationProfile;
            this.terrainSeed = terrainSeed;
            this.kitVersion = kitVersion;
            this.terrainDepth = terrainDepth;
            this.terrainWidth = terrainWidth;
            this.pixelWidth = pixelWidth;
            this.pixelHeight = pixelHeight;
        }

        private static CacheKey forTerrain(TerrainMap terrain, int width, int height)
        {
            if(terrain == null) {
                return new CacheKey("NO_TERRAIN", "NO_GENERATOR",
                    "NO_PROFILE", 0L, TerrainVisualKit.TV2_PACKAGED_VERSION,
                    0, 0, width, height);
            }
            return new CacheKey(terrain.getFingerprint(),
                terrain.getGeneratorVersion(),
                terrain.getGenerationProfile().name(),
                terrain.getSeed(),
                TerrainVisualKit.TV2_PACKAGED_VERSION, terrain.getDepth(),
                terrain.getWidth(), width, height);
        }

        @Override
        public boolean equals(Object other)
        {
            if(this == other) return true;
            if(!(other instanceof CacheKey)) return false;
            CacheKey that = (CacheKey)other;
            return terrainSeed == that.terrainSeed &&
                   terrainDepth == that.terrainDepth &&
                   terrainWidth == that.terrainWidth &&
                   pixelWidth == that.pixelWidth &&
                   pixelHeight == that.pixelHeight &&
                   Objects.equals(terrainFingerprint, that.terrainFingerprint) &&
                   Objects.equals(generatorVersion, that.generatorVersion) &&
                   Objects.equals(generationProfile, that.generationProfile) &&
                   Objects.equals(kitVersion, that.kitVersion);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(terrainFingerprint, generatorVersion,
                generationProfile, terrainSeed, kitVersion, terrainDepth, terrainWidth,
                pixelWidth, pixelHeight);
        }
    }
}
