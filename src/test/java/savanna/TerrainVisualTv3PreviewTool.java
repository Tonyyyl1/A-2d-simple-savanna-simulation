package savanna;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Exports deterministic, zero-step PROCEDURAL_V2 terrain preview walls. */
public final class TerrainVisualTv3PreviewTool
{
    private static final long[] DEFAULT_SEEDS = {4201L, 9317L, 17689L, 24107L};
    private static final int MAP_WIDTH = 900;
    private static final int MAP_HEIGHT = 600;

    private TerrainVisualTv3PreviewTool()
    {
    }

    public static void main(String[] args) throws IOException
    {
        Path output = Path.of(args.length == 0 ? "build/terrain-preview-tv3" : args[0]);
        Files.createDirectories(output);
        long[] seeds = seeds(args);
        BufferedImage wall = new BufferedImage(MAP_WIDTH * 2, (MAP_HEIGHT + 38) * 2,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D wallGraphics = wall.createGraphics();
        try {
            wallGraphics.setColor(new Color(29, 34, 29));
            wallGraphics.fillRect(0, 0, wall.getWidth(), wall.getHeight());
            for(int index = 0; index < seeds.length; index++) {
                TerrainMap terrain = TerrainMap.proceduralPreview(80, 120,
                    seeds[index]);
                BufferedImage image = render(terrain);
                Path file = output.resolve("terrain-tv3-procedural-v2-seed-" +
                    seeds[index] + ".png");
                write(image, file);
                int column = index % 2;
                int row = index / 2;
                int left = column * MAP_WIDTH;
                int top = row * (MAP_HEIGHT + 38);
                wallGraphics.drawImage(image, left, top, null);
                label(wallGraphics, terrain, left + 12, top + MAP_HEIGHT + 25);
                System.out.println("TV3_PREVIEW profile=" + terrain.getGenerationProfile() +
                    " generator=" + terrain.getGeneratorVersion() + " seed=" + seeds[index] +
                    " fingerprint=" + terrain.getFingerprint() + " file=" + file);
            }
        }
        finally {
            wallGraphics.dispose();
        }
        Path wallFile = output.resolve("terrain-tv3-procedural-v2-wall.png");
        write(wall, wallFile);
        System.out.println("TV3_PREVIEW_WALL profile=" +
            TerrainGenerationProfile.PROCEDURAL_V2 + " file=" + wallFile +
            " maps=" + seeds.length + " ecologySteps=0");
    }

    private static BufferedImage render(TerrainMap terrain)
    {
        BufferedImage image = new BufferedImage(MAP_WIDTH, MAP_HEIGHT,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { terrain.drawBackground(graphics, MAP_WIDTH, MAP_HEIGHT); }
        finally { graphics.dispose(); }
        return image;
    }

    private static void label(Graphics2D graphics, TerrainMap terrain, int x, int y)
    {
        graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, 15.0f));
        graphics.setColor(new Color(235, 242, 224));
        graphics.drawString("PROCEDURAL_V2  seed=" + terrain.getSeed() +
            "  fp=" + terrain.getFingerprint().substring(0, 12), x, y);
    }

    private static long[] seeds(String[] args)
    {
        if(args.length <= 1) return DEFAULT_SEEDS.clone();
        long[] result = new long[Math.min(4, args.length - 1)];
        for(int index = 0; index < result.length; index++) {
            result[index] = Long.parseLong(args[index + 1]);
        }
        return result;
    }

    private static void write(BufferedImage image, Path file) throws IOException
    {
        if(!ImageIO.write(image, "png", file.toFile())) {
            throw new IOException("PNG writer unavailable for " + file);
        }
    }
}
