package savanna;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/** Stable connected-component index for terrain water cells. */
public final class WaterSourceIndex
{
    public static final String AMBIGUOUS = "AMBIGUOUS";
    public static final String NONE = "NONE";

    private final int depth;
    private final int width;
    private final List<Pool> pools;
    private final int[][] waterPool;

    public WaterSourceIndex(TerrainMap terrain)
    {
        this(WaterTopology.forTerrain(terrain).getSourceIndex());
    }

    private WaterSourceIndex(WaterSourceIndex source)
    {
        depth = source.depth;
        width = source.width;
        pools = source.pools;
        waterPool = source.waterPool;
    }

    static WaterSourceIndex build(TerrainMap terrain)
    {
        if(terrain == null) {
            throw new IllegalArgumentException("terrain is required");
        }
        return new WaterSourceIndex(terrain, true);
    }

    private WaterSourceIndex(TerrainMap terrain, boolean build)
    {
        depth = terrain.getDepth();
        width = terrain.getWidth();
        waterPool = new int[terrain.getDepth()][terrain.getWidth()];
        for(int row = 0; row < waterPool.length; row++) {
            java.util.Arrays.fill(waterPool[row], -1);
        }
        pools = Collections.unmodifiableList(buildPools(terrain));
        for(Pool pool : pools) {
            pool.shoreCellCount = countShoreCells(pool.id);
        }
    }

    public int getPoolCount()
    {
        return pools.size();
    }

    public List<String> getPoolIds()
    {
        List<String> ids = new ArrayList<>();
        for(Pool pool : pools) {
            ids.add(pool.id);
        }
        return Collections.unmodifiableList(ids);
    }

    /** An inferred nearest pool only; O0a does not claim this is a target. */
    public String inferredNearestPool(Location location)
    {
        if(location == null || pools.isEmpty()) {
            return NONE;
        }
        int bestDistance = Integer.MAX_VALUE;
        String best = NONE;
        boolean tied = false;
        for(Pool pool : pools) {
            int distance = pool.chebyshevDistance(location);
            if(distance < bestDistance) {
                bestDistance = distance;
                best = pool.id;
                tied = false;
            }
            else if(distance == bestDistance) {
                tied = true;
            }
        }
        return tied ? AMBIGUOUS : best;
    }

    /** Stable pool owner of a passable shore cell, or AMBIGUOUS/NONE. */
    public String shorePool(Location location)
    {
        if(location == null || !inBounds(location.row(), location.col()) ||
           waterPool[location.row()][location.col()] >= 0) {
            return NONE;
        }
        return shorePoolAt(location.row(), location.col());
    }

    String shorePoolAt(int row, int col)
    {
        if(!inBounds(row, col) || waterPool[row][col] >= 0) return NONE;
        int owner = -1;
        for(int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            for(int colOffset = -1; colOffset <= 1; colOffset++) {
                if(rowOffset == 0 && colOffset == 0) {
                    continue;
                }
                int nextRow = row + rowOffset;
                int nextCol = col + colOffset;
                if(!inBounds(nextRow, nextCol) || waterPool[nextRow][nextCol] < 0) {
                    continue;
                }
                if(owner >= 0 && owner != waterPool[nextRow][nextCol]) {
                    return AMBIGUOUS;
                }
                owner = waterPool[nextRow][nextCol];
            }
        }
        return owner < 0 ? NONE : pools.get(owner).id;
    }

    public int getShoreCellCount(String poolId)
    {
        Pool pool = poolById(poolId);
        return pool == null ? 0 : pool.shoreCellCount;
    }

    boolean isDrinkableShoreAt(int row, int col)
    {
        return !NONE.equals(shorePoolAt(row, col)) ||
            AMBIGUOUS.equals(shorePoolAt(row, col));
    }

    private List<Pool> buildPools(TerrainMap terrain)
    {
        List<Component> components = new ArrayList<>();
        boolean[][] seen = new boolean[depth][width];
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                if(seen[row][col] || terrain.getTerrainAt(new Location(row, col))
                    != TerrainType.WATERHOLE) {
                    continue;
                }
                components.add(collectComponent(terrain, row, col, seen));
            }
        }
        components.sort(Comparator.comparing((Component component) -> component.minRow)
            .thenComparing(component -> component.minCol));
        List<Pool> indexed = new ArrayList<>();
        for(int index = 0; index < components.size(); index++) {
            Component component = components.get(index);
            Pool pool = new Pool(String.format("POOL-%02d", index + 1), component);
            indexed.add(pool);
            for(Location water : component.cells) {
                waterPool[water.row()][water.col()] = index;
            }
        }
        return indexed;
    }

    private Component collectComponent(TerrainMap terrain, int startRow, int startCol,
                                       boolean[][] seen)
    {
        List<Location> cells = new ArrayList<>();
        Deque<Location> queue = new ArrayDeque<>();
        queue.add(new Location(startRow, startCol));
        seen[startRow][startCol] = true;
        int minRow = startRow;
        int minCol = startCol;
        while(!queue.isEmpty()) {
            Location current = queue.removeFirst();
            cells.add(current);
            minRow = Math.min(minRow, current.row());
            minCol = Math.min(minCol, current.col());
            for(int rowOffset = -1; rowOffset <= 1; rowOffset++) {
                for(int colOffset = -1; colOffset <= 1; colOffset++) {
                    int row = current.row() + rowOffset;
                    int col = current.col() + colOffset;
                    if(!inBounds(row, col) || seen[row][col] ||
                       terrain.getTerrainAt(new Location(row, col)) !=
                           TerrainType.WATERHOLE) {
                        continue;
                    }
                    seen[row][col] = true;
                    queue.addLast(new Location(row, col));
                }
            }
        }
        return new Component(cells, minRow, minCol);
    }

    private int countShoreCells(String poolId)
    {
        int count = 0;
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                if(poolId.equals(shorePool(new Location(row, col)))) {
                    count++;
                }
            }
        }
        return count;
    }

    private Pool poolById(String id)
    {
        for(Pool pool : pools) {
            if(pool.id.equals(id)) {
                return pool;
            }
        }
        return null;
    }

    private boolean inBounds(int row, int col)
    {
        return row >= 0 && row < depth && col >= 0 && col < width;
    }

    private static final class Component
    {
        private final List<Location> cells;
        private final int minRow;
        private final int minCol;

        Component(List<Location> cells, int minRow, int minCol)
        {
            this.cells = cells;
            this.minRow = minRow;
            this.minCol = minCol;
        }
    }

    private static final class Pool
    {
        private final String id;
        private final List<Location> cells;
        private int shoreCellCount;

        Pool(String id, Component component)
        {
            this.id = id;
            this.cells = component.cells;
        }

        int chebyshevDistance(Location location)
        {
            int best = Integer.MAX_VALUE;
            for(Location water : cells) {
                int distance = Math.max(Math.abs(location.row() - water.row()),
                                        Math.abs(location.col() - water.col()));
                best = Math.min(best, distance);
            }
            return best;
        }
    }
}
