package savanna;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.Map;

/** Static, zero-step TV2 resource and shared-background acceptance checks. */
public final class TerrainVisualTv2Tests
{
    private static int checks;

    private TerrainVisualTv2Tests()
    {
    }

    public static void main(String[] args)
    {
        TerrainVisualKit kit = TerrainVisualKit.tv2Packaged();
        check("TV2 version", TerrainVisualKit.TV2_PACKAGED_VERSION.equals(kit.getVersion()));
        Map<String, BufferedImage> assets = TerrainVisualAssets.loadAll(kit);
        check("TV2 loads all declared assets", assets.size() == kit.getDescriptors().size());
        for(TerrainVisualKit.MaterialDescriptor descriptor : kit.getDescriptors().values()) {
            BufferedImage asset = assets.get(descriptor.getId());
            check("TV2 resource is packaged " + descriptor.getId(),
                descriptor.isPackagedResource());
            check("TV2 resource dimensions " + descriptor.getId(), asset != null &&
                asset.getWidth() == descriptor.getPixelWidth() &&
                asset.getHeight() == descriptor.getPixelHeight());
        }
        verifyWorldTextureScale(kit);

        TerrainMap terrain = new TerrainMap(80, 120, TerrainMap.DEFAULT_SEED);
        verifyContourBands(terrain, kit);
        String fingerprint = terrain.getFingerprint();
        BufferedImage legacy = image(480, 320);
        BufferedImage shared = image(480, 320);
        drawLegacy(terrain, legacy);
        drawShared(terrain, shared);
        check("TV2 leaves legacy terrain fingerprint unchanged",
            fingerprint.equals(terrain.getFingerprint()));
        check("TV2 shared output is visibly layered", differsOutsideWater(terrain, legacy, shared));
        verifyWaterUsesPrimaryMaterial(terrain, legacy, shared);

        BufferedImage repeated = image(480, 320);
        drawShared(terrain, repeated);
        check("TV2 shared painter is deterministic", identical(shared, repeated));
        System.out.println("TerrainVisualTv2Tests passed " + checks + " checks");
    }

    private static void verifyWaterUsesPrimaryMaterial(TerrainMap terrain,
                                                       BufferedImage legacy,
                                                       BufferedImage shared)
    {
        VisualWaterMask water = new VisualWaterMask(terrain, shared.getWidth(),
            shared.getHeight());
        int protectedPixels = 0;
        int changedPixels = 0;
        for(int y = 0; y < shared.getHeight(); y++) {
            for(int x = 0; x < shared.getWidth(); x++) {
                if(water.isWaterPixel(x, y)) {
                    protectedPixels++;
                    if(legacy.getRGB(x, y) != shared.getRGB(x, y)) changedPixels++;
                }
            }
        }
        check("TV2 fixture has shared water pixels", protectedPixels > 0);
        check("TV2 water uses its packaged primary surface", changedPixels > 0);
    }

    private static boolean differsOutsideWater(TerrainMap terrain,
                                               BufferedImage first,
                                               BufferedImage second)
    {
        VisualWaterMask water = new VisualWaterMask(terrain, first.getWidth(),
            first.getHeight());
        for(int y = 0; y < first.getHeight(); y++) {
            for(int x = 0; x < first.getWidth(); x++) {
                if(!water.isWaterPixel(x, y) && first.getRGB(x, y) != second.getRGB(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void verifyWorldTextureScale(TerrainVisualKit kit)
    {
        TerrainVisualKit.MaterialDescriptor grass = kit.getSurface(TerrainType.GRASSLAND);
        VisualGridGeometry small = new VisualGridGeometry(80, 120, 480, 320);
        VisualGridGeometry large = new VisualGridGeometry(80, 120, 960, 640);
        java.awt.Rectangle smallAnchor = TerrainVisualBackgroundPainter
            .textureAnchorFor(grass, small);
        java.awt.Rectangle largeAnchor = TerrainVisualBackgroundPainter
            .textureAnchorFor(grass, large);
        check("TV2 texture anchor spans declared world width at small size",
            smallAnchor.width == 24 && smallAnchor.height == 24);
        check("TV2 texture anchor follows resize without world drift",
            largeAnchor.width == 48 && largeAnchor.height == 48);
        TerrainVisualKit.MaterialDescriptor shore = kit.getDescriptors().get("shore-water");
        check("TV2 contour band width follows declared world height at small size",
            TerrainVisualContourBandPainter.bandWidthPixels(shore, small) == 8.0);
        check("TV2 contour band width follows resize without world drift",
            TerrainVisualContourBandPainter.bandWidthPixels(shore, large) == 16.0);
    }

    private static void verifyContourBands(TerrainMap terrain,
                                           TerrainVisualKit kit)
    {
        boolean[][] concaveFixture = {
            {false, false, false, false},
            {false, true,  true,  false},
            {false, true,  false, false},
            {false, false, false, false}
        };
        TerrainVisualContourBandPainter.WaterContour fixture =
            TerrainVisualContourBandPainter.waterContour(concaveFixture);
        TerrainVisualContourBandPainter.BandLayout fixtureLayout =
            TerrainVisualContourBandPainter.shorelineLayout(fixture);
        check("TV2 contour fixture has horizontal tangents",
            fixtureLayout.horizontalSegmentCount() > 0);
        check("TV2 contour fixture has vertical tangents",
            fixtureLayout.verticalSegmentCount() > 0);
        check("TV2 contour fixture explicitly identifies outer corners",
            fixtureLayout.cornerCount(TerrainVisualContourBandPainter.CornerKind.OUTER) > 0);
        check("TV2 contour fixture explicitly identifies inner corners",
            fixtureLayout.cornerCount(TerrainVisualContourBandPainter.CornerKind.INNER) > 0);

        VisualGridGeometry geometry = new VisualGridGeometry(terrain.getDepth(),
            terrain.getWidth(), 480, 320);
        TerrainVisualComposer.TerrainVisualComposition composition =
            TerrainVisualComposer.compose(terrain, geometry,
                new VisualWaterMask(terrain, 480, 320), kit);
        TerrainVisualContourBandPainter.WaterContour water =
            TerrainVisualContourBandPainter.waterContour(composition);
        TerrainVisualContourBandPainter.BandLayout shoreline =
            TerrainVisualContourBandPainter.shorelineLayout(water);
        check("TV2 water contour has horizontal tangent strips",
            shoreline.horizontalSegmentCount() > 0);
        check("TV2 water contour has vertical tangent strips",
            shoreline.verticalSegmentCount() > 0);

        TerrainVisualKit.MaterialDescriptor shore = kit.getDescriptors().get("shore-water");
        Area shoreAllowed = TerrainVisualBackgroundPainter.allowedArea(composition,
            shore, true, water.getArea());
        Area shoreFootprint = shoreline.footprint(
            TerrainVisualContourBandPainter.bandWidthPixels(shore, geometry),
            shoreAllowed);
        check("TV2 shoreline footprint never enters shared visual water",
            !touchesWater(shoreFootprint, water, geometry));
        verifyPaintedShorelineStaysOnLand(shoreline, shore, water, shoreAllowed,
            geometry, kit);

        TerrainVisualKit.MaterialDescriptor land = kit.getDescriptors().get("transition-land");
        TerrainVisualContourBandPainter.BandLayout transitions =
            TerrainVisualContourBandPainter.transitionLayout(composition, land);
        check("TV2 land transitions have horizontal tangent strips",
            transitions.horizontalSegmentCount() > 0);
        check("TV2 land transitions have vertical tangent strips",
            transitions.verticalSegmentCount() > 0);
        check("TV2 transition strips follow shared logical edges",
            transitionSegmentsSeparateLandTypes(transitions, composition));
        Area transitionAllowed = TerrainVisualBackgroundPainter.allowedArea(composition,
            land, false, water.getArea());
        Area transitionFootprint = transitions.footprint(
            TerrainVisualContourBandPainter.bandWidthPixels(land, geometry),
            transitionAllowed);
        check("TV2 transition footprint never enters shared visual water",
            !touchesWater(transitionFootprint, water, geometry));
    }

    private static void verifyPaintedShorelineStaysOnLand(
        TerrainVisualContourBandPainter.BandLayout shoreline,
        TerrainVisualKit.MaterialDescriptor shore,
        TerrainVisualContourBandPainter.WaterContour water, Area allowed,
        VisualGridGeometry geometry, TerrainVisualKit kit)
    {
        BufferedImage image = image(geometry.pixelWidth(), geometry.pixelHeight());
        Graphics2D graphics = image.createGraphics();
        try {
            TerrainVisualContourBandPainter.paint(graphics, shoreline, shore,
                TerrainVisualAssets.loadAll(kit).get(shore.getId()), allowed, .72f,
                geometry);
        }
        finally {
            graphics.dispose();
        }
        int painted = 0;
        boolean enteredWater = false;
        for(int y = 0; y < image.getHeight(); y++) {
            for(int x = 0; x < image.getWidth(); x++) {
                if((image.getRGB(x, y) >>> 24) != 0) {
                    painted++;
                    enteredWater |= water.isWaterPixel(x, y);
                }
            }
        }
        check("TV2 shoreline painter emits a visible contour band", painted > 0);
        check("TV2 shoreline painter clips every painted pixel to land", !enteredWater);
    }

    private static boolean touchesWater(Area area,
                                        TerrainVisualContourBandPainter.WaterContour water,
                                        VisualGridGeometry geometry)
    {
        Rectangle bounds = area.getBounds();
        for(int y = Math.max(0, bounds.y); y < Math.min(geometry.pixelHeight(),
            bounds.y + bounds.height); y++) {
            for(int x = Math.max(0, bounds.x); x < Math.min(geometry.pixelWidth(),
                bounds.x + bounds.width); x++) {
                if(area.contains(x + .5, y + .5) && water.isWaterPixel(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean transitionSegmentsSeparateLandTypes(
        TerrainVisualContourBandPainter.BandLayout layout,
        TerrainVisualComposer.TerrainVisualComposition composition)
    {
        VisualGridGeometry geometry = composition.getGeometry();
        TerrainVisualComposer.TerrainTopology topology = composition.getTopology();
        for(TerrainVisualContourBandPainter.Segment segment : layout.getSegments()) {
            double x = (segment.getX1() + segment.getX2()) / 2.0;
            double y = (segment.getY1() + segment.getY2()) / 2.0;
            TerrainType first;
            TerrainType second;
            if(segment.isHorizontal()) {
                first = topology.terrainAt(geometry.cellRowAtPixel(y - .5),
                    geometry.cellColAtPixel(x));
                second = topology.terrainAt(geometry.cellRowAtPixel(y + .5),
                    geometry.cellColAtPixel(x));
            }
            else {
                first = topology.terrainAt(geometry.cellRowAtPixel(y),
                    geometry.cellColAtPixel(x - .5));
                second = topology.terrainAt(geometry.cellRowAtPixel(y),
                    geometry.cellColAtPixel(x + .5));
            }
            if(first == second || first == TerrainType.WATERHOLE ||
               second == TerrainType.WATERHOLE) {
                return false;
            }
        }
        return !layout.getSegments().isEmpty();
    }

    private static BufferedImage image(int width, int height)
    {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    private static void drawLegacy(TerrainMap terrain, BufferedImage image)
    {
        Graphics2D graphics = image.createGraphics();
        try { terrain.drawLegacyBackground(graphics, image.getWidth(), image.getHeight()); }
        finally { graphics.dispose(); }
    }

    private static void drawShared(TerrainMap terrain, BufferedImage image)
    {
        Graphics2D graphics = image.createGraphics();
        try { terrain.drawBackground(graphics, image.getWidth(), image.getHeight()); }
        finally { graphics.dispose(); }
    }

    private static boolean identical(BufferedImage first, BufferedImage second)
    {
        for(int y = 0; y < first.getHeight(); y++) {
            for(int x = 0; x < first.getWidth(); x++) {
                if(first.getRGB(x, y) != second.getRGB(x, y)) return false;
            }
        }
        return true;
    }

    private static void check(String label, boolean condition)
    {
        checks++;
        if(!condition) throw new AssertionError(label);
    }
}
