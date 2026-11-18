package savanna;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.Map;

/** The single TV2 background path shared by overview, inspect and PNG output. */
public final class TerrainVisualBackgroundPainter
{
    private TerrainVisualBackgroundPainter()
    {
    }

    public static void paint(TerrainMap terrain, Graphics2D graphics,
                             int pixelWidth, int pixelHeight)
    {
        if(terrain == null || graphics == null) {
            throw new IllegalArgumentException("Terrain and graphics are required.");
        }
        terrain.drawBaseBackground(graphics, pixelWidth, pixelHeight);
        TerrainVisualKit kit = TerrainVisualKit.tv2Packaged();
        Map<String, BufferedImage> assets = TerrainVisualAssets.loadAll(kit);
        VisualGridGeometry geometry = VisualGridGeometry.forTerrain(terrain,
            pixelWidth, pixelHeight);
        TerrainVisualComposer.TerrainVisualComposition composition =
            TerrainVisualComposer.compose(terrain, geometry,
                new VisualWaterMask(terrain, pixelWidth, pixelHeight), kit);
        TerrainVisualContourBandPainter.WaterContour water =
            TerrainVisualContourBandPainter.waterContour(composition);
        paintSurfaces(graphics, composition, assets, water.getArea());
        paintBandsAndDecorations(graphics, composition, assets, water);
    }

    private static void paintSurfaces(Graphics2D graphics,
                                      TerrainVisualComposer.TerrainVisualComposition composition,
                                      Map<String, BufferedImage> assets,
                                      Area waterArea)
    {
        for(TerrainType type : TerrainType.values()) {
            TerrainVisualKit.MaterialDescriptor descriptor = composition.getKit().getSurface(type);
            // TV2's PNG is the primary material, not a tint over the old
            // procedural swatch.  Land is cut away from the visual-water
            // footprint; water uses that same footprint as its own surface.
            Area target = type == TerrainType.WATERHOLE
                ? new Area(waterArea) : surfaceLandArea(composition, type, waterArea);
            paintTexture(graphics, assets.get(descriptor.getId()), target, 1.0f,
                descriptor, composition.getGeometry());
        }
    }

    private static void paintBandsAndDecorations(Graphics2D graphics,
        TerrainVisualComposer.TerrainVisualComposition composition,
        Map<String, BufferedImage> assets,
        TerrainVisualContourBandPainter.WaterContour water)
    {
        TerrainVisualKit kit = composition.getKit();
        Area waterArea = water.getArea();
        for(TerrainVisualKit.MaterialDescriptor descriptor :
            kit.getByRole(TerrainVisualKit.LayerRole.TRANSITION)) {
            Area allowed = allowedArea(composition, descriptor, false, waterArea);
            TerrainVisualContourBandPainter.paint(graphics,
                TerrainVisualContourBandPainter.transitionLayout(composition, descriptor),
                descriptor, assets.get(descriptor.getId()), allowed, .55f,
                composition.getGeometry());
        }
        for(TerrainVisualKit.MaterialDescriptor descriptor :
            kit.getByRole(TerrainVisualKit.LayerRole.SHORELINE)) {
            Area allowed = allowedArea(composition, descriptor, true, waterArea);
            TerrainVisualContourBandPainter.paint(graphics,
                TerrainVisualContourBandPainter.shorelineLayout(water), descriptor,
                assets.get(descriptor.getId()), allowed, .72f,
                composition.getGeometry());
        }
        for(TerrainVisualKit.MaterialDescriptor descriptor :
            kit.getByRole(TerrainVisualKit.LayerRole.DECORATION)) {
            paintDecorationPatches(graphics, composition, descriptor,
                assets.get(descriptor.getId()), allowedArea(composition, descriptor,
                    false, waterArea));
        }
    }

    static Area allowedArea(TerrainVisualComposer.TerrainVisualComposition composition,
                            TerrainVisualKit.MaterialDescriptor descriptor,
                            boolean shoreline, Area waterArea)
    {
        if(waterArea == null) throw new IllegalArgumentException("Water area is required.");
        Area result;
        if(shoreline) {
            result = cellArea(composition.getShorelineCells(), composition.getGeometry());
        }
        else if(descriptor.getRole() == TerrainVisualKit.LayerRole.DECORATION) {
            result = cellArea(composition.getSurfaceMask(descriptor.getTerrainType()),
                composition.getGeometry());
        }
        else if(descriptor.getId().equals("transition-grass-bush")) {
            result = cellArea(composition.getTopology().transitionMask(
                TerrainType.GRASSLAND, TerrainType.BUSH), composition.getGeometry());
        }
        else {
            result = new Area();
            for(Map.Entry<TerrainVisualComposer.TransitionKey,
                TerrainVisualComposer.CellMask> entry : composition.getTopology()
                    .getTransitionMasks().entrySet()) {
                TerrainVisualComposer.TransitionKey key = entry.getKey();
                if(key.getFirst() != TerrainType.WATERHOLE &&
                   key.getSecond() != TerrainType.WATERHOLE) {
                    result.add(cellArea(entry.getValue(), composition.getGeometry()));
                }
            }
        }
        result.subtract(new Area(waterArea));
        return result;
    }

    private static Area surfaceLandArea(TerrainVisualComposer.TerrainVisualComposition composition,
                                        TerrainType type, Area waterArea)
    {
        Area result = cellArea(composition.getSurfaceMask(type),
            composition.getGeometry());
        result.subtract(new Area(waterArea));
        return result;
    }

    private static Area cellArea(TerrainVisualComposer.CellMask mask,
                                 VisualGridGeometry geometry)
    {
        Area result = new Area();
        for(int row = 0; row < mask.rows(); row++) {
            int col = 0;
            while(col < mask.cols()) {
                if(!mask.contains(row, col)) { col++; continue; }
                int start = col;
                while(col < mask.cols() && mask.contains(row, col)) col++;
                Rectangle first = geometry.cellBounds(row, start);
                Rectangle last = geometry.cellBounds(row, col - 1);
                result.add(new Area(new Rectangle(first.x, first.y,
                    last.x + last.width - first.x, first.height)));
            }
        }
        return result;
    }

    private static void paintTexture(Graphics2D graphics, BufferedImage image,
                                     Area area, float alpha,
                                     TerrainVisualKit.MaterialDescriptor descriptor,
                                     VisualGridGeometry geometry)
    {
        if(image == null || area.isEmpty()) return;
        Shape oldClip = graphics.getClip();
        Composite oldComposite = graphics.getComposite();
        try {
            graphics.clip(area);
            graphics.setComposite(AlphaComposite.SrcOver.derive(alpha));
            graphics.setPaint(new TexturePaint(image, textureAnchorFor(descriptor, geometry)));
            Rectangle bounds = area.getBounds();
            graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }
        finally {
            graphics.setComposite(oldComposite);
            graphics.setClip(oldClip);
        }
    }

    /** World-origin texture anchor derived from declared material cell size. */
    static Rectangle textureAnchorFor(TerrainVisualKit.MaterialDescriptor descriptor,
                                      VisualGridGeometry geometry)
    {
        if(descriptor == null || geometry == null) {
            throw new IllegalArgumentException("Material descriptor and geometry are required.");
        }
        return new Rectangle(0, 0,
            Math.max(1, (int)Math.round(descriptor.getWorldWidthCells() *
                geometry.pixelWidth() / geometry.cols())),
            Math.max(1, (int)Math.round(descriptor.getWorldHeightCells() *
                geometry.pixelHeight() / geometry.rows())));
    }

    /** Places transparent decorations at stable cell anchors; never tiles them. */
    private static void paintDecorationPatches(Graphics2D graphics,
        TerrainVisualComposer.TerrainVisualComposition composition,
        TerrainVisualKit.MaterialDescriptor descriptor, BufferedImage image, Area allowed)
    {
        if(image == null || allowed.isEmpty()) return;
        VisualGridGeometry geometry = composition.getGeometry();
        int spacing = descriptor.getTerrainType() == TerrainType.GRASSLAND ? 53
            : descriptor.getTerrainType() == TerrainType.BUSH ? 97 : 127;
        int imageWidth = Math.max(1, (int)Math.round(descriptor.getWorldWidthCells() *
            geometry.averageCellSize()));
        int imageHeight = Math.max(1, (int)Math.round(descriptor.getWorldHeightCells() *
            geometry.averageCellSize()));
        Shape oldClip = graphics.getClip();
        Composite oldComposite = graphics.getComposite();
        try {
            graphics.clip(allowed);
            graphics.setComposite(AlphaComposite.SrcOver.derive(.80f));
            for(int row = 0; row < geometry.rows(); row++) {
                for(int col = 0; col < geometry.cols(); col++) {
                    if(!composition.getSurfaceMask(descriptor.getTerrainType()).contains(row, col) ||
                       Math.floorMod(row * 31 + col * 17 + descriptor.getId().hashCode(), spacing) != 0) {
                        continue;
                    }
                    Rectangle cell = geometry.cellBounds(row, col);
                    int x = cell.x + (cell.width - imageWidth) / 2;
                    int y = cell.y + (cell.height - imageHeight) / 2;
                    graphics.drawImage(image, x, y, imageWidth, imageHeight, null);
                }
            }
        }
        finally {
            graphics.setComposite(oldComposite);
            graphics.setClip(oldClip);
        }
    }
}
