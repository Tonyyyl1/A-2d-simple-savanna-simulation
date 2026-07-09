import java.awt.Rectangle;

/**
 * Shared mapping between Field grid coordinates and unzoomed render pixels.
 *
 * Terrain and animal rendering must use this same mapping. It mirrors
 * TerrainMap's rounded cell boundaries so non-divisible view sizes do not
 * drift between terrain, animals, hit testing and probes.
 */
public class VisualGridGeometry
{
    private final int rows;
    private final int cols;
    private final int pixelWidth;
    private final int pixelHeight;

    public VisualGridGeometry(int rows, int cols, int pixelWidth,
                              int pixelHeight)
    {
        this.rows = Math.max(1, rows);
        this.cols = Math.max(1, cols);
        this.pixelWidth = Math.max(1, pixelWidth);
        this.pixelHeight = Math.max(1, pixelHeight);
    }

    public static VisualGridGeometry forTerrain(TerrainMap terrain,
                                                int pixelWidth,
                                                int pixelHeight)
    {
        return new VisualGridGeometry(terrain.getDepth(), terrain.getWidth(),
                                      pixelWidth, pixelHeight);
    }

    public static VisualGridGeometry preferredForTerrain(TerrainMap terrain)
    {
        return forTerrain(terrain, terrain.getWidth() * 10,
                          terrain.getDepth() * 10);
    }

    public Rectangle cellBounds(int row, int col)
    {
        int x0 = (int)Math.round((double)col * pixelWidth / cols);
        int x1 = (int)Math.round((double)(col + 1) * pixelWidth / cols);
        int y0 = (int)Math.round((double)row * pixelHeight / rows);
        int y1 = (int)Math.round((double)(row + 1) * pixelHeight / rows);
        return new Rectangle(x0, y0, Math.max(1, x1 - x0),
                             Math.max(1, y1 - y0));
    }

    public double pixelXForGridCol(double col)
    {
        return col * pixelWidth / cols;
    }

    public double pixelYForGridRow(double row)
    {
        return row * pixelHeight / rows;
    }

    public double gridColForPixel(double x)
    {
        return x * cols / pixelWidth;
    }

    public double gridRowForPixel(double y)
    {
        return y * rows / pixelHeight;
    }

    public int cellColAtPixel(double x)
    {
        return clampCell((int)Math.floor(gridColForPixel(x)), cols);
    }

    public int cellRowAtPixel(double y)
    {
        return clampCell((int)Math.floor(gridRowForPixel(y)), rows);
    }

    public double cellWidthAt(int col)
    {
        return cellBounds(0, clampCell(col, cols)).getWidth();
    }

    public double cellHeightAt(int row)
    {
        return cellBounds(clampCell(row, rows), 0).getHeight();
    }

    public double averageCellSize()
    {
        return Math.min((double)pixelWidth / cols,
                        (double)pixelHeight / rows);
    }

    public boolean isWaterPixel(TerrainMap terrain, int x, int y)
    {
        double col = gridColForPixel(clampPixel(x, pixelWidth) + 0.5);
        double row = gridRowForPixel(clampPixel(y, pixelHeight) + 0.5);
        return terrain.isVisuallyWaterAt(row, col);
    }

    public int rows()
    {
        return rows;
    }

    public int cols()
    {
        return cols;
    }

    public int pixelWidth()
    {
        return pixelWidth;
    }

    public int pixelHeight()
    {
        return pixelHeight;
    }

    private int clampCell(int value, int max)
    {
        return Math.max(0, Math.min(max - 1, value));
    }

    private int clampPixel(int value, int max)
    {
        return Math.max(0, Math.min(max - 1, value));
    }
}
