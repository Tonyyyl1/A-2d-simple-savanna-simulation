package savanna;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * Deterministic TV1 sample renderer for a {@link TerrainVisualComposition}.
 *
 * <p>This is deliberately a preview renderer, not a second production
 * background path: TV2 will connect one shared painter to TerrainMap's
 * completed frame.  Its value now is concrete reviewable output while keeping
 * every pixel decision tied to the existing terrain, geometry and visual-water
 * mask.  It never reads ecological RNG.</p>
 */
public final class TerrainVisualPreviewRenderer
{
    private TerrainVisualPreviewRenderer()
    {
    }

    public static BufferedImage render(
        TerrainVisualComposer.TerrainVisualComposition composition)
    {
        if(composition == null) {
            throw new IllegalArgumentException("composition is required");
        }
        VisualGridGeometry geometry = composition.getGeometry();
        BufferedImage image = renderBase(composition);
        paintTransitionsAndShore(image, composition);
        paintTransparentDecorations(image, composition);
        return image;
    }

    /** Uses the mature TerrainMap painter as the TV1 surface baseline. */
    public static BufferedImage renderBase(
        TerrainVisualComposer.TerrainVisualComposition composition)
    {
        VisualGridGeometry geometry = composition.getGeometry();
        BufferedImage image = new BufferedImage(geometry.pixelWidth(),
            geometry.pixelHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        composition.getTerrain().drawBackground(graphics, geometry.pixelWidth(),
            geometry.pixelHeight());
        graphics.dispose();
        return image;
    }

    private static void paintTransitionsAndShore(BufferedImage image,
        TerrainVisualComposer.TerrainVisualComposition composition)
    {
        TerrainVisualComposer.TerrainTopology topology = composition.getTopology();
        VisualGridGeometry geometry = composition.getGeometry();
        for(int y = 0; y < image.getHeight(); y++) {
            for(int x = 0; x < image.getWidth(); x++) {
                int row = geometry.cellRowAtPixel(y);
                int col = geometry.cellColAtPixel(x);
                if(composition.allowsShorelinePixel(x, y) &&
                   ((x + y * 3) % 13 == 0)) {
                    overlay(image, x, y, new Color(238, 220, 157, 150));
                    continue;
                }
                if(hasLandTransition(topology, composition, row, col, x, y) &&
                   ((x * 7 + y) % 23 == 0)) {
                    overlay(image, x, y, new Color(91, 72, 42, 72));
                }
            }
        }
    }

    private static boolean hasLandTransition(
        TerrainVisualComposer.TerrainTopology topology,
        TerrainVisualComposer.TerrainVisualComposition composition,
        int row, int col, int x, int y)
    {
        for(Map.Entry<TerrainVisualComposer.TransitionKey,
            TerrainVisualComposer.CellMask> entry : topology.getTransitionMasks().entrySet()) {
            TerrainVisualComposer.TransitionKey key = entry.getKey();
            if(key.getFirst() != TerrainType.WATERHOLE &&
               key.getSecond() != TerrainType.WATERHOLE &&
               entry.getValue().contains(row, col) &&
               composition.allowsTransitionPixel(key.getFirst(), key.getSecond(), x, y)) {
                return true;
            }
        }
        return false;
    }

    private static void paintTransparentDecorations(BufferedImage image,
        TerrainVisualComposer.TerrainVisualComposition composition)
    {
        TerrainVisualKit kit = composition.getKit();
        for(TerrainVisualKit.MaterialDescriptor decoration :
            kit.getByRole(TerrainVisualKit.LayerRole.DECORATION)) {
            Color tint = decoration.getTerrainType() == TerrainType.BUSH
                ? new Color(37, 73, 31, 96)
                : decoration.getTerrainType() == TerrainType.DRY_SOIL
                    ? new Color(99, 72, 44, 92)
                    : new Color(56, 108, 41, 76);
            for(int y = 0; y < image.getHeight(); y++) {
                for(int x = 0; x < image.getWidth(); x++) {
                    if(composition.allowsDecorationPixel(decoration, x, y) &&
                       ((x * 19 + y * 11 + decoration.getId().hashCode()) & 127) == 0) {
                        overlay(image, x, y, tint);
                    }
                }
            }
        }
    }

    private static void overlay(BufferedImage image, int x, int y, Color over)
    {
        int original = image.getRGB(x, y);
        int alpha = over.getAlpha();
        int inverse = 255 - alpha;
        int red = (over.getRed() * alpha + ((original >>> 16) & 255) * inverse) / 255;
        int green = (over.getGreen() * alpha + ((original >>> 8) & 255) * inverse) / 255;
        int blue = (over.getBlue() * alpha + (original & 255) * inverse) / 255;
        image.setRGB(x, y, new Color(red, green, blue).getRGB());
    }

}
