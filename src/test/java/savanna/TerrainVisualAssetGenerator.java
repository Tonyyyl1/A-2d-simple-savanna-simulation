package savanna;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Creates the small, deterministic PNG material source set used by TV2.
 *
 * <p>This is a developer asset authoring tool, not a runtime fallback.  Its
 * output is checked in as {@code src/main/resources}; runtime code only loads
 * and verifies those packaged PNGs.  Keeping the generator here makes the
 * source artwork reproducible without consuming simulation RNG or depending
 * on an editor-specific binary format.</p>
 */
public final class TerrainVisualAssetGenerator
{
    private TerrainVisualAssetGenerator()
    {
    }

    public static void main(String[] args) throws IOException
    {
        Path root = Path.of(args.length == 0 ? "src/main/resources/terrain-kit/tv2"
                                             : args[0]);
        writeSurface(root.resolve("surface/grassland.png"), TerrainType.GRASSLAND);
        writeSurface(root.resolve("surface/open-plain.png"), TerrainType.OPEN_PLAIN);
        writeSurface(root.resolve("surface/dry-soil.png"), TerrainType.DRY_SOIL);
        writeSurface(root.resolve("surface/bush.png"), TerrainType.BUSH);
        writeSurface(root.resolve("surface/water.png"), TerrainType.WATERHOLE);
        writeLandEdge(root.resolve("transition/land-edge.png"));
        writeGrassBushEdge(root.resolve("transition/grass-bush.png"));
        writeShore(root.resolve("shore/water-edge.png"));
        writeDecoration(root.resolve("decor/grass-tuft.png"), TerrainType.GRASSLAND);
        writeDecoration(root.resolve("decor/bush.png"), TerrainType.BUSH);
        writeDecoration(root.resolve("decor/rock.png"), TerrainType.DRY_SOIL);
    }

    private static void writeSurface(Path path, TerrainType type) throws IOException
    {
        BufferedImage image = image(64, 64, false);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(type.getColor());
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            Random random = new Random(0x544632L + type.ordinal() * 977L);
            switch(type) {
                case GRASSLAND: grass(g, random); break;
                case BUSH: bush(g, random); break;
                case DRY_SOIL: soil(g, random); break;
                case WATERHOLE: water(g, random); break;
                case OPEN_PLAIN:
                default: plain(g, random); break;
            }
        }
        finally {
            g.dispose();
        }
        write(image, path);
    }

    private static void plain(Graphics2D g, Random random)
    {
        for(int i = 0; i < 18; i++) {
            int x = random.nextInt(64);
            int y = random.nextInt(64);
            g.setColor(shade(TerrainType.OPEN_PLAIN.getColor(), i % 2 == 0 ? 1.12 : .90));
            g.fillRoundRect(x, y, 3 + random.nextInt(7), 1, 1, 1);
        }
    }

    private static void grass(Graphics2D g, Random random)
    {
        for(int i = 0; i < 135; i++) {
            int x = random.nextInt(64);
            int y = random.nextInt(64);
            g.setColor(shade(TerrainType.GRASSLAND.getColor(), random.nextBoolean() ? 1.20 : .76));
            g.drawLine(x, y, x + (random.nextBoolean() ? 1 : -1), y - 2 - random.nextInt(4));
        }
    }

    private static void bush(Graphics2D g, Random random)
    {
        for(int i = 0; i < 24; i++) {
            int x = random.nextInt(70) - 3;
            int y = random.nextInt(70) - 3;
            int radius = 4 + random.nextInt(7);
            g.setColor(shade(TerrainType.BUSH.getColor(), .60));
            g.fill(new Ellipse2D.Double(x - radius, y - radius * .55,
                radius * 2.0, radius * 1.15));
            g.setColor(shade(TerrainType.BUSH.getColor(), 1.25));
            g.fill(new Ellipse2D.Double(x - radius * .45, y - radius * .58,
                radius * .75, radius * .40));
        }
    }

    private static void soil(Graphics2D g, Random random)
    {
        for(int i = 0; i < 75; i++) {
            g.setColor(shade(TerrainType.DRY_SOIL.getColor(), i % 3 == 0 ? 1.14 : .86));
            g.fillRect(random.nextInt(64), random.nextInt(64), 1, 1);
        }
        g.setStroke(new BasicStroke(1f));
        for(int i = 0; i < 6; i++) {
            double x = random.nextInt(64);
            double y = random.nextInt(64);
            Path2D crack = new Path2D.Double();
            crack.moveTo(x, y);
            for(int segment = 0; segment < 3; segment++) {
                x += -6 + random.nextInt(13);
                y += 3 + random.nextInt(6);
                crack.lineTo(x, y);
            }
            g.setColor(shade(TerrainType.DRY_SOIL.getColor(), .50));
            g.draw(crack);
        }
    }

    private static void water(Graphics2D g, Random random)
    {
        for(int i = 0; i < 10; i++) {
            int y = random.nextInt(64);
            g.setColor(shade(TerrainType.WATERHOLE.getColor(), 1.20));
            g.drawArc(-8, y - 3, 78, 7, 12, 155);
        }
    }

    private static void writeLandEdge(Path path) throws IOException
    {
        BufferedImage image = image(128, 32, true);
        Graphics2D g = image.createGraphics();
        try {
            for(int x = 0; x < 128; x++) {
                int alpha = 75 + (x % 23) * 3;
                g.setColor(new Color(86, 66, 37, Math.min(145, alpha)));
                g.fillRect(x, 13 + (x % 5 == 0 ? 1 : 0), 1, 4);
            }
        }
        finally { g.dispose(); }
        write(image, path);
    }

    private static void writeGrassBushEdge(Path path) throws IOException
    {
        BufferedImage image = image(128, 32, true);
        Graphics2D g = image.createGraphics();
        try {
            Random random = new Random(2207L);
            for(int x = 0; x < 128; x += 3) {
                int h = 3 + random.nextInt(7);
                g.setColor(new Color(46, 87, 37, 135));
                g.fillOval(x, 16 - h / 2, 7, h);
            }
        }
        finally { g.dispose(); }
        write(image, path);
    }

    private static void writeShore(Path path) throws IOException
    {
        BufferedImage image = image(128, 48, true);
        Graphics2D g = image.createGraphics();
        try {
            for(int x = 0; x < 128; x++) {
                int y = 18 + (int)Math.round(Math.sin(x * .19) * 2.0);
                g.setColor(new Color(243, 225, 163, 165));
                g.fillRect(x, y, 1, 3);
                if(x % 7 == 0) {
                    g.setColor(new Color(119, 89, 50, 112));
                    g.fillRect(x, y + 3, 2, 2);
                }
            }
        }
        finally { g.dispose(); }
        write(image, path);
    }

    private static void writeDecoration(Path path, TerrainType type) throws IOException
    {
        int size = type == TerrainType.BUSH ? 64 : 48;
        BufferedImage image = image(size, size, true);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            if(type == TerrainType.GRASSLAND) {
                g.setColor(new Color(42, 95, 35, 132));
                for(int offset = -9; offset <= 9; offset += 3) {
                    g.drawLine(size / 2, size - 5, size / 2 + offset, size / 2 - 4);
                }
            }
            else if(type == TerrainType.BUSH) {
                g.setColor(new Color(35, 72, 31, 185));
                g.fillOval(7, 18, 47, 33);
                g.setColor(new Color(84, 126, 59, 160));
                g.fillOval(14, 11, 32, 25);
            }
            else {
                g.setColor(new Color(93, 73, 54, 175));
                g.fillOval(11, 19, 29, 20);
                g.setColor(new Color(186, 157, 110, 150));
                g.fillOval(15, 16, 20, 12);
            }
        }
        finally { g.dispose(); }
        write(image, path);
    }

    private static BufferedImage image(int width, int height, boolean alpha)
    {
        return new BufferedImage(width, height,
            alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    }

    private static void write(BufferedImage image, Path path) throws IOException
    {
        Files.createDirectories(path.getParent());
        if(!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("PNG writer unavailable for " + path);
        }
    }

    private static Color shade(Color color, double factor)
    {
        return new Color(clamp((int)Math.round(color.getRed() * factor)),
            clamp((int)Math.round(color.getGreen() * factor)),
            clamp((int)Math.round(color.getBlue() * factor)));
    }

    private static int clamp(int value)
    {
        return Math.max(0, Math.min(255, value));
    }
}
