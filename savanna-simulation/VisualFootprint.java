import java.awt.Rectangle;

/**
 * Shared visible footprint calculations for ordinary markers and inspect
 * actors. Rendering, hit testing and water probes all call this class.
 */
public class VisualFootprint
{
    private VisualFootprint() {}

    public static class OrdinaryMarker
    {
        public final int left;
        public final int top;
        public final int cellWidth;
        public final int cellHeight;
        public final int symbolLeft;
        public final int symbolTop;
        public final int symbolSize;

        private OrdinaryMarker(int left, int top, int cellWidth, int cellHeight,
                               int symbolLeft, int symbolTop, int symbolSize)
        {
            this.left = left;
            this.top = top;
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            this.symbolLeft = symbolLeft;
            this.symbolTop = symbolTop;
            this.symbolSize = symbolSize;
        }
    }

    public static OrdinaryMarker ordinaryMarker(Location location,
                                                VisualGridGeometry geometry)
    {
        Rectangle cell = geometry.cellBounds(location.row(), location.col());
        int cellWidth = Math.max(1, cell.width - 1);
        int cellHeight = Math.max(1, cell.height - 1);
        int symbolSize = Math.max(3, Math.min(cellWidth, cellHeight) - 2);
        int symbolLeft = cell.x + Math.max(0, (cellWidth - symbolSize) / 2);
        int symbolTop = cell.y + Math.max(0, (cellHeight - symbolSize) / 2);
        return new OrdinaryMarker(cell.x, cell.y, cellWidth, cellHeight,
                                  symbolLeft, symbolTop, symbolSize);
    }

    public static boolean ordinaryTouchesWater(Location location,
                                               SavannahAnimal animal,
                                               VisualGridGeometry geometry,
                                               TerrainMap terrain)
    {
        if(location == null || terrain == null || geometry == null) {
            return false;
        }
        OrdinaryMarker marker = ordinaryMarker(location, geometry);
        Rectangle bounds = ordinaryBounds(marker, animal);
        for(int y = bounds.y; y <= bounds.y + bounds.height; y++) {
            for(int x = bounds.x; x <= bounds.x + bounds.width; x++) {
                if(geometry.isWaterPixel(terrain, x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Rectangle ordinaryBounds(OrdinaryMarker marker,
                                           SavannahAnimal animal)
    {
        int minX = marker.symbolLeft;
        int minY = marker.symbolTop;
        int maxX = marker.symbolLeft + marker.symbolSize;
        int maxY = marker.symbolTop + marker.symbolSize;

        minX = Math.min(minX, marker.symbolLeft + 1);
        minY = Math.min(minY, marker.symbolTop + 2);
        maxX = Math.max(maxX, marker.symbolLeft + 1 + marker.symbolSize);
        maxY = Math.max(maxY, marker.symbolTop + 2 +
                        Math.max(2, marker.symbolSize / 2));

        if(animal != null && animal.isSurvivalCritical()) {
            minX = Math.min(minX, marker.symbolLeft - 1);
            minY = Math.min(minY, marker.symbolTop - 1);
            maxX = Math.max(maxX, marker.symbolLeft + marker.symbolSize + 1);
            maxY = Math.max(maxY, marker.symbolTop + marker.symbolSize + 1);
        }

        if(animal != null && animal.getStaminaStage() == StaminaStage.LOW &&
           marker.cellHeight >= 5) {
            minX = Math.min(minX, marker.left);
            minY = Math.min(minY, marker.top + marker.cellHeight - 2);
            maxX = Math.max(maxX, marker.left + marker.cellWidth);
            maxY = Math.max(maxY, marker.top + marker.cellHeight);
        }

        if(animal != null && animal.isInfected()) {
            int dotSize = Math.max(2, marker.symbolSize / 3);
            minX = Math.min(minX, marker.symbolLeft + marker.symbolSize - dotSize);
            minY = Math.min(minY, marker.symbolTop);
            maxX = Math.max(maxX, marker.symbolLeft + marker.symbolSize);
            maxY = Math.max(maxY, marker.symbolTop + dotSize);
        }

        return new Rectangle(minX, minY, Math.max(1, maxX - minX),
                             Math.max(1, maxY - minY));
    }

    public static boolean inspectActorTouchesWater(double row, double col,
                                                   double scale,
                                                   TerrainMap terrain,
                                                   VisualGridGeometry geometry)
    {
        return inspectCenterTouchesWater(row + 0.5, col + 0.5, scale,
                                         terrain, geometry);
    }

    public static boolean inspectCenterTouchesWater(double centerRow,
                                                    double centerCol,
                                                    double scale,
                                                    TerrainMap terrain,
                                                    VisualGridGeometry geometry)
    {
        if(terrain == null || geometry == null) {
            return false;
        }
        double centerX = geometry.pixelXForGridCol(centerCol);
        double centerY = geometry.pixelYForGridRow(centerRow);
        double radius = inspectIconPixels(scale, geometry) / 2.0;
        double step = Math.max(1.0, radius / 6.0);
        for(double dy = -radius; dy <= radius + 0.001; dy += step) {
            for(double dx = -radius; dx <= radius + 0.001; dx += step) {
                if(dx * dx + dy * dy > radius * radius) {
                    continue;
                }
                int x = (int)Math.round(centerX + dx);
                int y = (int)Math.round(centerY + dy);
                if(geometry.isWaterPixel(terrain, x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int inspectIconPixels(double scale,
                                        VisualGridGeometry geometry)
    {
        return (int)Math.max(8.0, geometry.averageCellSize() * 1.6 * scale);
    }
}
