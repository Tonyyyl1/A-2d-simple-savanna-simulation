package savanna;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Writes deterministic, zero-step TV1 preview PNGs for visual review. */
public final class TerrainVisualPreviewTool
{
    private static final long LEGACY_REFERENCE_SEED = 20260629L;

    private TerrainVisualPreviewTool()
    {
    }

    public static void main(String[] args) throws IOException
    {
        Path output = Path.of(args.length == 0 ? "build/terrain-preview-tv1" : args[0]);
        Files.createDirectories(output);
        TerrainVisualKit kit = TerrainVisualKit.tv1Prototype();
        long[] seeds = seeds(args);
        for(long seed : seeds) {
            TerrainMap terrain = new TerrainMap(80, 120, seed);
            VisualGridGeometry geometry = VisualGridGeometry.forTerrain(terrain, 1200, 800);
            VisualWaterMask mask = new VisualWaterMask(terrain, 1200, 800);
            TerrainVisualComposer.TerrainVisualComposition composition =
                TerrainVisualComposer.compose(terrain, geometry, mask, kit);
            BufferedImage preview = TerrainVisualPreviewRenderer.render(composition);
            Path file = output.resolve("terrain-tv1-legacy-seed-" + seed + ".png");
            if(!ImageIO.write(preview, "png", file.toFile())) {
                throw new IOException("PNG writer unavailable for " + file);
            }
            System.out.println("TV1_PREVIEW seed=" + seed + " file=" + file +
                " pixels=" + preview.getWidth() + "x" + preview.getHeight());
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
