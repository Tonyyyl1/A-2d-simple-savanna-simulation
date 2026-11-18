package savanna;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable-terrain helper for bounded topology queries (plan v3.2 A-3b).
 *
 * <p>Terrain never changes after {@link TerrainMap} construction, so the
 * impassable-cell prefix sums are built once and reused by every step. A
 * clipped square window without impassable cells needs no search: with
 * eight-neighbour moves every cell's land distance equals its Chebyshev
 * distance. Windows that contain impassable cells fall back to
 * {@link TerrainNavigator#reachableDistances} and the result is kept in a
 * bounded cross-step cache.</p>
 */
final class TerrainPassabilityIndex
{
    private static final int WINDOW_CACHE_LIMIT = 8192;
    private static final long RANGE_KEY_SPAN = 4096L;

    private final TerrainMap terrain;
    private final int depth;
    private final int width;
    /** Blocked-cell prefix sums, (depth + 1) x (width + 1), row-major. */
    private final int[] blocked;
    private final int passableCellCount;
    private final Map<Long, Window> slowWindows = new LinkedHashMap<>();

    TerrainPassabilityIndex(TerrainMap terrain)
    {
        this.terrain = terrain;
        depth = terrain.getDepth();
        width = terrain.getWidth();
        int stride = width + 1;
        blocked = new int[(depth + 1) * stride];
        int passable = 0;
        for(int row = 0; row < depth; row++) {
            int rowBlocked = 0;
            for(int col = 0; col < width; col++) {
                if(terrain.isPassableAt(row, col)) passable++;
                else rowBlocked++;
                blocked[(row + 1) * stride + col + 1] =
                    blocked[row * stride + col + 1] + rowBlocked;
            }
        }
        passableCellCount = passable;
    }

    int getPassableCellCount()
    {
        return passableCellCount;
    }

    private int blockedIn(int firstRow, int lastRow, int firstCol, int lastCol)
    {
        int stride = width + 1;
        return blocked[(lastRow + 1) * stride + lastCol + 1] -
            blocked[firstRow * stride + lastCol + 1] -
            blocked[(lastRow + 1) * stride + firstCol] +
            blocked[firstRow * stride + firstCol];
    }

    /** The reachable window around {@code origin} within {@code range} land steps. */
    synchronized Window window(Location origin, int range)
    {
        int firstRow = Math.max(0, origin.row() - range);
        int lastRow = Math.min(depth - 1, origin.row() + range);
        int firstCol = Math.max(0, origin.col() - range);
        int lastCol = Math.min(width - 1, origin.col() + range);
        if(blockedIn(firstRow, lastRow, firstCol, lastCol) == 0) {
            return new Window(origin.row(), origin.col(), firstRow, firstCol,
                lastRow - firstRow + 1, lastCol - firstCol + 1, null,
                (lastRow - firstRow + 1) * (lastCol - firstCol + 1));
        }
        boolean cacheable = range >= 0 && range < RANGE_KEY_SPAN;
        long key = ((long)origin.row() * width + origin.col()) * RANGE_KEY_SPAN + range;
        if(cacheable) {
            Window cached = slowWindows.get(key);
            if(cached != null) return cached;
        }
        TerrainNavigator.Reachability reachability =
            TerrainNavigator.reachableDistances(terrain, origin, range);
        int localDepth = lastRow - firstRow + 1;
        int localWidth = lastCol - firstCol + 1;
        int[] distance = new int[localDepth * localWidth];
        int reachable = 0;
        for(int row = 0; row < localDepth; row++) {
            for(int col = 0; col < localWidth; col++) {
                int value = reachability.distanceTo(
                    new Location(firstRow + row, firstCol + col));
                if(value < 0 || value > range) value = -1;
                else reachable++;
                distance[row * localWidth + col] = value;
            }
        }
        Window result = new Window(origin.row(), origin.col(), firstRow, firstCol,
            localDepth, localWidth, distance, reachable);
        if(cacheable) {
            if(slowWindows.size() >= WINDOW_CACHE_LIMIT) {
                java.util.Iterator<Long> eldest = slowWindows.keySet().iterator();
                eldest.next();
                eldest.remove();
            }
            slowWindows.put(key, result);
        }
        return result;
    }

    /**
     * Land distances in a clipped square window; {@code distance == null}
     * means the window has no impassable cell (Chebyshev distances).
     */
    static final class Window
    {
        private final int originRow;
        private final int originCol;
        final int firstRow;
        final int firstCol;
        final int localDepth;
        final int localWidth;
        private final int[] distance;
        final int reachableCount;

        Window(int originRow, int originCol, int firstRow, int firstCol,
               int localDepth, int localWidth, int[] distance, int reachableCount)
        {
            this.originRow = originRow;
            this.originCol = originCol;
            this.firstRow = firstRow;
            this.firstCol = firstCol;
            this.localDepth = localDepth;
            this.localWidth = localWidth;
            this.distance = distance;
            this.reachableCount = reachableCount;
        }

        /** Land distance to a cell inside the window, or -1 when unreachable. */
        int distanceAt(int row, int col)
        {
            if(distance == null) {
                return Math.max(Math.abs(row - originRow), Math.abs(col - originCol));
            }
            return distance[(row - firstRow) * localWidth + (col - firstCol)];
        }
    }
}
