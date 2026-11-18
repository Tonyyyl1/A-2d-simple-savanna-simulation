import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Modular 2.5D texture provider for terrain rendering.
 *
 * Each {@link TerrainType} owns one self-contained paint method that builds a
 * tileable texture swatch (flat base colour plus type-specific marks, with no
 * lighting). The region-wide 2.5D lighting and elevation shadows are applied by
 * {@link TerrainMap} when the swatch is stretched/tiled across a region, so the
 * tiles here stay lighting-neutral and reusable.
 *
 * To change how one terrain looks, edit only its paint method.
 */
public class TerrainTileSet
{
    private static final int TILE = 64;

    private final Map<TerrainType, TexturePaint> cache =
        new EnumMap<>(TerrainType.class);

    /**
     * Return a cached, tileable texture for the given terrain type.
     */
    public TexturePaint paintFor(TerrainType type)
    {
        TexturePaint cached = cache.get(type);
        if(cached != null) {
            return cached;
        }
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
            TILE, TILE, java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(type.getColor());
        g2.fillRect(0, 0, TILE, TILE);

        // Deterministic per-type texture so the map never flickers between runs.
        Random random = new Random(0x5A11E + type.ordinal() * 977L);
        switch(type) {
            case GRASSLAND: paintGrassland(g2, random); break;
            case BUSH:      paintBush(g2, random);      break;
            case DRY_SOIL:  paintDrySoil(g2, random);   break;
            case WATERHOLE: paintWaterRipple(g2, random); break;
            case OPEN_PLAIN:
            default:        paintOpenPlain(g2, random); break;
        }
        g2.dispose();

        TexturePaint paint = new TexturePaint(image, new Rectangle(0, 0, TILE, TILE));
        cache.put(type, paint);
        return paint;
    }

    private void paintOpenPlain(Graphics2D g2, Random random)
    {
        // Flat, sparse: a few faint horizontal grass dashes only.
        Color base = TerrainType.OPEN_PLAIN.getColor();
        for(int i = 0; i < 16; i++) {
            int x = random.nextInt(TILE);
            int y = random.nextInt(TILE);
            int len = 4 + random.nextInt(6);
            g2.setColor(i % 2 == 0 ? shade(base, 1.10) : shade(base, 0.90));
            g2.fillRect(x, y, len, 1);
        }
    }

    private void paintGrassland(Graphics2D g2, Random random)
    {
        // Dense short vertical blades, alternating darker/lighter greens.
        Color base = TerrainType.GRASSLAND.getColor();
        for(int i = 0; i < 150; i++) {
            int x = random.nextInt(TILE);
            int y = random.nextInt(TILE);
            int h = 2 + random.nextInt(4);
            g2.setColor(random.nextBoolean() ? shade(base, 1.16) : shade(base, 0.80));
            g2.drawLine(x, y, x, y - h);
        }
    }

    private void paintBush(Graphics2D g2, Random random)
    {
        // Clustered dark canopy blobs with small highlights for volume.
        Color base = TerrainType.BUSH.getColor();
        for(int i = 0; i < 26; i++) {
            int x = random.nextInt(TILE);
            int y = random.nextInt(TILE);
            int r = 4 + random.nextInt(7);
            g2.setColor(shade(base, 0.66));
            g2.fill(new Ellipse2D.Double(x - r, y - r * 0.6, r * 2.0, r * 1.2));
            g2.setColor(shade(base, 1.22));
            g2.fill(new Ellipse2D.Double(x - r * 0.5, y - r * 0.6,
                                         r * 0.7, r * 0.45));
        }
    }

    private void paintDrySoil(Graphics2D g2, Random random)
    {
        // Cracked earth: dark fissures with a thin sunlit lip, plus speckle.
        Color base = TerrainType.DRY_SOIL.getColor();
        for(int i = 0; i < 60; i++) {
            int x = random.nextInt(TILE);
            int y = random.nextInt(TILE);
            g2.setColor(random.nextBoolean() ? shade(base, 1.12) : shade(base, 0.86));
            g2.fillRect(x, y, 1, 1);
        }
        for(int i = 0; i < 6; i++) {
            double x = random.nextInt(TILE);
            double y = random.nextInt(TILE);
            Path2D crack = new Path2D.Double();
            crack.moveTo(x, y);
            for(int seg = 0; seg < 3; seg++) {
                x += -6 + random.nextInt(13);
                y += 3 + random.nextInt(6);
                crack.lineTo(x, y);
            }
            g2.setColor(shade(base, 0.55));
            g2.draw(crack);
        }
    }

    private void paintWaterRipple(Graphics2D g2, Random random)
    {
        // Fallback only; the waterhole normally uses TerrainMap's fine renderer.
        Color base = TerrainType.WATERHOLE.getColor();
        for(int i = 0; i < 8; i++) {
            int y = random.nextInt(TILE);
            g2.setColor(shade(base, 1.18));
            g2.drawLine(0, y, TILE, y + (random.nextBoolean() ? 1 : -1));
        }
    }

    private static Color shade(Color color, double factor)
    {
        int r = clamp((int)Math.round(color.getRed() * factor));
        int g = clamp((int)Math.round(color.getGreen() * factor));
        int b = clamp((int)Math.round(color.getBlue() * factor));
        return new Color(r, g, b);
    }

    private static int clamp(int value)
    {
        return Math.max(0, Math.min(255, value));
    }
}
