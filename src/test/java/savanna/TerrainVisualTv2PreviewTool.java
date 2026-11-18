package savanna;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Writes fixed, zero-step TV2 shared-background PNGs for visual review. */
public final class TerrainVisualTv2PreviewTool
{
    private static final long LEGACY_REFERENCE_SEED = 20260629L;

    private TerrainVisualTv2PreviewTool()
    {
    }

    public static void main(String[] args) throws IOException
    {
        Path output = Path.of(args.length == 0 ? "build/terrain-preview-tv2" : args[0]);
        Files.createDirectories(output);
        long[] seeds = seeds(args);
        for(long seed : seeds) {
            TerrainMap terrain = new TerrainMap(80, 120, seed);
            BufferedImage image = new BufferedImage(1200, 800,
                BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics = image.createGraphics();
            try { terrain.drawBackground(graphics, image.getWidth(), image.getHeight()); }
            finally { graphics.dispose(); }
            Path file = output.resolve("terrain-tv2-legacy-seed-" + seed + ".png");
            if(!ImageIO.write(image, "png", file.toFile())) {
                throw new IOException("PNG writer unavailable for " + file);
            }
            System.out.println("TV2_PREVIEW generator=" + TerrainMap.GENERATOR_VERSION +
                " seed=" + seed + " fingerprint=" + terrain.getFingerprint() +
                " file=" + file);
        }
        if(seeds.length > 1) {
            System.out.println("TV2_PREVIEW_NOTE LEGACY_V1 seed outputs are visual review only; " +
                "they are not evidence of a procedural-map profile.");
        }
    }

    private static long[] seeds(String[] args)
    {
        if(args.length <= 1) return new long[] {LEGACY_REFERENCE_SEED};
        long[] values = new long[args.length - 1];
        for(int index = 1; index < args.length; index++) {
            values[index - 1] = Long.parseLong(args[index]);
        }
        return values;
    }
}
