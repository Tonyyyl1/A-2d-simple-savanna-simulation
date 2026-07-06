import java.awt.Rectangle;

/**
 * Predicts whether an ordinary animal marker's final pixel footprint would
 * overlap the painted water surface, for an arbitrary panel size.
 *
 * The mask must mirror the view exactly, so the marker geometry lives in the
 * static helpers below and {@code SimulatorView.FieldView} draws through the
 * same helpers. Both layers use the continuous cell-to-pixel mapping
 * {@code pixel = cell * panelSize / gridSize}; panel sizes that are not an
 * exact multiple of the grid stay aligned with the terrain background, which
 * uses the same mapping.
 */
public class VisualWaterMask
{
    /** Extra pixels around the symbol for outline, ring, shadow and bars. */
    public static final int DECORATION_MARGIN_PIXELS = 3;

    private final TerrainMap terrain;
    private final int gridWidth;
    private final int gridHeight;
    private final int pixelWidth;
    private final int pixelHeight;

    public VisualWaterMask(TerrainMap terrain, int pixelWidth, int pixelHeight)
    {
        this.terrain = terrain;
        gridWidth = terrain.getWidth();
        gridHeight = terrain.getDepth();
        this.pixelWidth = Math.max(1, pixelWidth);
        this.pixelHeight = Math.max(1, pixelHeight);
    }

    /**
     * Symbol size used by the ordinary animal marker at this cell size.
     */
    public static int symbolSize(double cellWidth, double cellHeight)
    {
        return (int)Math.max(3,
            Math.round(Math.min(cellWidth, cellHeight)) - 2);
    }

    /**
     * Pixel bounds of the marker symbol for a cell, centred on the cell using
     * the continuous mapping. This is the drawing formula for the view and
     * the audit formula for the probe.
     */
    public static Rectangle markerBounds(int row, int col, double cellWidth,
                                         double cellHeight)
    {
        int size = symbolSize(cellWidth, cellHeight);
        int left = (int)Math.round((col + 0.5) * cellWidth - size / 2.0);
        int top = (int)Math.round((row + 0.5) * cellHeight - size / 2.0);
        return new Rectangle(left, top, size, size);
    }

    public boolean ordinaryAnimalFootprintTouchesWater(Location location)
    {
        if(location == null) {
            return false;
        }
        double cellWidth = (double)pixelWidth / gridWidth;
        double cellHeight = (double)pixelHeight / gridHeight;
        Rectangle symbol = markerBounds(location.row(), location.col(),
                                        cellWidth, cellHeight);
        int margin = DECORATION_MARGIN_PIXELS;
        int left = symbol.x - margin;
        int top = symbol.y - margin;
        int right = symbol.x + symbol.width + margin;
        int bottom = symbol.y + symbol.height + margin;

        for(int y = top; y <= bottom; y++) {
            for(int x = left; x <= right; x++) {
                if(isWaterPixel(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a panel pixel lies on the painted water surface, using the same
     * continuous pixel-to-cell mapping as the terrain background renderer.
     */
    public boolean isWaterPixel(int x, int y)
    {
        double col = ((double)Math.max(0, Math.min(pixelWidth - 1, x)) /
                      pixelWidth) * gridWidth;
        double row = ((double)Math.max(0, Math.min(pixelHeight - 1, y)) /
                      pixelHeight) * gridHeight;
        return terrain.isVisuallyWaterAt(row, col);
    }
}
