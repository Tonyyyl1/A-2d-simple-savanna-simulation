package savanna;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/** Loads TV2 PNGs from the runtime classpath and verifies their identity. */
public final class TerrainVisualAssets
{
    private static final Map<String, BufferedImage> CACHE =
        new LinkedHashMap<String, BufferedImage>();

    private TerrainVisualAssets()
    {
    }

    public static synchronized BufferedImage image(TerrainVisualKit.MaterialDescriptor descriptor)
    {
        if(descriptor == null || !descriptor.isPackagedResource()) {
            throw new IllegalArgumentException("A packaged terrain descriptor is required.");
        }
        BufferedImage cached = CACHE.get(descriptor.getPlannedAssetPath());
        if(cached != null) return cached;
        String path = descriptor.getPlannedAssetPath();
        byte[] bytes = read(path);
        String actual = sha256(bytes);
        if(!actual.equals(descriptor.getResourceSha256())) {
            throw new IllegalStateException("Terrain asset hash mismatch for " + path);
        }
        BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        }
        catch(IOException e) {
            throw new IllegalStateException("Cannot decode terrain asset " + path, e);
        }
        if(decoded == null || decoded.getWidth() != descriptor.getPixelWidth() ||
           decoded.getHeight() != descriptor.getPixelHeight()) {
            throw new IllegalStateException("Terrain asset dimensions mismatch for " + path);
        }
        CACHE.put(path, decoded);
        return decoded;
    }

    public static synchronized Map<String, BufferedImage> loadAll(TerrainVisualKit kit)
    {
        if(kit == null) throw new IllegalArgumentException("Terrain kit is required.");
        Map<String, BufferedImage> result = new LinkedHashMap<String, BufferedImage>();
        for(TerrainVisualKit.MaterialDescriptor descriptor : kit.getDescriptors().values()) {
            result.put(descriptor.getId(), image(descriptor));
        }
        return Collections.unmodifiableMap(result);
    }

    private static byte[] read(String path)
    {
        try(InputStream input = TerrainVisualAssets.class.getClassLoader()
                .getResourceAsStream(path)) {
            if(input == null) throw new IllegalStateException("Missing terrain asset " + path);
            return input.readAllBytes();
        }
        catch(IOException e) {
            throw new IllegalStateException("Cannot read terrain asset " + path, e);
        }
    }

    private static String sha256(byte[] bytes)
    {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexEncoding.lowercase(digest);
        }
        catch(NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }
}
