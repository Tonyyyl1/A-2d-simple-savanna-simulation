package savanna;

import java.awt.image.BufferedImage;

/**
 * Stand-alone static checks for the TV1 visual-kit contract.
 *
 * <p>It is intentionally not registered in {@link AllTests}: TV1 has no
 * resource copy/build integration yet.  This test constructs only terrain
 * masks and raster coordinates; it advances no Simulator steps and consumes
 * no ecological random stream.</p>
 */
public final class TerrainVisualKitTests
{
    private static int checks;

    private TerrainVisualKitTests()
    {
    }

    public static void main(String[] args)
    {
        testFiveSurfaceDescriptors();
        testRectangularMask();
        testConcaveMask();
        testMaskWithHole();
        testNarrowMask();
        testSharedWaterMaskComposition();
        System.out.println("TerrainVisualKitTests passed " + checks + " checks");
    }

    private static void testFiveSurfaceDescriptors()
    {
        TerrainVisualKit kit = TerrainVisualKit.tv1Prototype();
        check("TV1 kit version", TerrainVisualKit.TV1_PROTOTYPE_VERSION.equals(kit.getVersion()));
        for(TerrainType type : TerrainType.values()) {
            TerrainVisualKit.MaterialDescriptor surface = kit.getSurface(type);
            check("surface descriptor for " + type, surface != null);
            check("surface role for " + type,
                surface.getRole() == TerrainVisualKit.LayerRole.SURFACE);
            check("surface tile policy for " + type,
                surface.getPlacementPolicy() == TerrainVisualKit.PlacementPolicy.WORLD_TILE);
            check("surface path for " + type,
                surface.getPlannedAssetPath().endsWith(".png"));
            check("TV1 descriptors are not packaged assets for " + type,
                !surface.isPackagedResource());
        }
        check("kit has a shoreline descriptor",
            !kit.getByRole(TerrainVisualKit.LayerRole.SHORELINE).isEmpty());
        check("kit has transition descriptors",
            !kit.getByRole(TerrainVisualKit.LayerRole.TRANSITION).isEmpty());
        check("kit has transparent decoration descriptors",
            !kit.getByRole(TerrainVisualKit.LayerRole.DECORATION).isEmpty());
    }

    private static void testRectangularMask()
    {
        TerrainType[][] grid = filled(7, 9, TerrainType.OPEN_PLAIN);
        fill(grid, 2, 3, 4, 6, TerrainType.GRASSLAND);
        TerrainVisualComposer.TerrainTopology topology =
            TerrainVisualComposer.topologyForGrid(grid);
        TerrainVisualComposer.CellMask grass = topology.surfaceMask(TerrainType.GRASSLAND);
        check("rectangle preserves exact area", grass.count() == 12);
        check("rectangle retains interior", grass.contains(3, 4));
        check("rectangle does not expand", !grass.contains(1, 4));
        check("rectangle remains one component", grass.componentCount4() == 1);
        check("rectangle gets a land transition",
            topology.transitionMask(TerrainType.GRASSLAND, TerrainType.OPEN_PLAIN).count() > 0);
    }

    private static void testConcaveMask()
    {
        TerrainType[][] grid = filled(7, 7, TerrainType.OPEN_PLAIN);
        for(int row = 1; row <= 5; row++) grid[row][1] = TerrainType.BUSH;
        for(int col = 1; col <= 5; col++) grid[5][col] = TerrainType.BUSH;
        TerrainVisualComposer.CellMask bush = TerrainVisualComposer.topologyForGrid(grid)
            .surfaceMask(TerrainType.BUSH);
        check("concave L keeps both arms", bush.count() == 9);
        check("concave L retains corner", bush.contains(5, 1));
        check("concave L does not fill interior", !bush.contains(3, 3));
        check("concave L remains connected", bush.componentCount4() == 1);
    }

    private static void testMaskWithHole()
    {
        TerrainType[][] grid = filled(9, 9, TerrainType.OPEN_PLAIN);
        for(int row = 2; row <= 6; row++) {
            for(int col = 2; col <= 6; col++) grid[row][col] = TerrainType.DRY_SOIL;
        }
        for(int row = 3; row <= 5; row++) {
            for(int col = 3; col <= 5; col++) grid[row][col] = TerrainType.OPEN_PLAIN;
        }
        TerrainVisualComposer.TerrainTopology topology =
            TerrainVisualComposer.topologyForGrid(grid);
        TerrainVisualComposer.CellMask soil = topology.surfaceMask(TerrainType.DRY_SOIL);
        check("ring area excludes hole", soil.count() == 16);
        check("ring keeps outer contour", soil.contains(2, 4));
        check("ring hole is not painted", !soil.contains(4, 4));
        check("ring stays one connected component", soil.componentCount4() == 1);
        check("hole has its own transition boundary",
            topology.transitionMask(TerrainType.DRY_SOIL, TerrainType.OPEN_PLAIN)
                .contains(4, 3));
    }

    private static void testNarrowMask()
    {
        TerrainType[][] grid = filled(6, 10, TerrainType.OPEN_PLAIN);
        for(int col = 1; col <= 8; col++) grid[3][col] = TerrainType.GRASSLAND;
        TerrainVisualComposer.TerrainTopology topology =
            TerrainVisualComposer.topologyForGrid(grid);
        TerrainVisualComposer.CellMask grass = topology.surfaceMask(TerrainType.GRASSLAND);
        check("narrow corridor preserves one-cell width", grass.count() == 8);
        check("narrow corridor does not thicken", !grass.contains(2, 4));
        check("narrow corridor remains connected", grass.componentCount4() == 1);
        check("narrow corridor transition follows both sides",
            topology.transitionMask(TerrainType.GRASSLAND, TerrainType.OPEN_PLAIN)
                .contains(3, 4));
    }

    private static void testSharedWaterMaskComposition()
    {
        TerrainMap terrain = new TerrainMap(80, 120, 12345L);
        VisualGridGeometry geometry = VisualGridGeometry.forTerrain(terrain, 1199, 803);
        VisualWaterMask waterMask = new VisualWaterMask(terrain, 1199, 803);
        TerrainVisualKit kit = TerrainVisualKit.tv1Prototype();
        TerrainVisualComposer.TerrainVisualComposition composition =
            TerrainVisualComposer.compose(terrain, geometry, waterMask, kit);

        int surfaceCells = 0;
        for(TerrainType type : TerrainType.values()) {
            surfaceCells += composition.getSurfaceMask(type).count();
        }
        check("composition covers every logical terrain cell",
            surfaceCells == terrain.getDepth() * terrain.getWidth());
        check("legacy terrain exposes shoreline cells",
            composition.getShorelineCells().count() > 0);

        BufferedImage basePreview = TerrainVisualPreviewRenderer.renderBase(composition);
        BufferedImage preview = TerrainVisualPreviewRenderer.render(composition);
        int protectedWaterPixels = 0;
        for(int y = 0; y < geometry.pixelHeight(); y++) {
            for(int x = 0; x < geometry.pixelWidth(); x++) {
                if(waterMask.isWaterPixel(x, y)) {
                    protectedWaterPixels++;
                    check("preview surface/shore/decor never paints shared water",
                        preview.getRGB(x, y) == basePreview.getRGB(x, y));
                }
            }
        }
        check("preview has visual-water coverage", protectedWaterPixels > 0);

        for(int y = 0; y < geometry.pixelHeight(); y++) {
            for(int x = 0; x < geometry.pixelWidth(); x++) {
                if(composition.allowsShorelinePixel(x, y)) {
                    check("shoreline paint stays off shared water mask",
                        !waterMask.isWaterPixel(x, y));
                }
                for(TerrainVisualKit.MaterialDescriptor decoration :
                    kit.getByRole(TerrainVisualKit.LayerRole.DECORATION)) {
                    if(composition.allowsDecorationPixel(decoration, x, y)) {
                        check("transparent decor stays off shared water mask",
                            !waterMask.isWaterPixel(x, y));
                    }
                }
            }
        }
    }

    private static TerrainType[][] filled(int rows, int cols, TerrainType type)
    {
        TerrainType[][] result = new TerrainType[rows][cols];
        for(int row = 0; row < rows; row++) {
            for(int col = 0; col < cols; col++) result[row][col] = type;
        }
        return result;
    }

    private static void fill(TerrainType[][] grid, int rowMin, int colMin,
                             int rowMax, int colMax, TerrainType type)
    {
        for(int row = rowMin; row <= rowMax; row++) {
            for(int col = colMin; col <= colMax; col++) grid[row][col] = type;
        }
    }

    private static void check(String label, boolean condition)
    {
        checks++;
        if(!condition) throw new AssertionError(label);
    }
}
