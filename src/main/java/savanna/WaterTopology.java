package savanna;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable map-identity water facts shared by planning, thirst, and per-step
 * congestion assessment. Dynamic load, reservations, and service stay outside
 * this object.
 */
public final class WaterTopology
{
    private static final int CACHE_CAPACITY = 16;
    private static final Map<String, WaterTopology> CACHE =
        new LinkedHashMap<String, WaterTopology>(CACHE_CAPACITY, 0.75f, true)
        {
            @Override
            protected boolean removeEldestEntry(
                Map.Entry<String, WaterTopology> eldest)
            {
                return size() > CACHE_CAPACITY;
            }
        };

    private final String identity;
    private final int depth;
    private final int width;
    private final WaterSourceIndex sourceIndex;
    private final int[][] shoreDistance;
    private final Map<String, int[][]> distancesByPool;

    private WaterTopology(TerrainMap terrain, String identity)
    {
        this.identity = identity;
        depth = terrain.getDepth();
        width = terrain.getWidth();
        sourceIndex = WaterSourceIndex.build(terrain);
        shoreDistance = computeShoreDistances(terrain);
        Map<String, int[][]> byPool = new LinkedHashMap<>();
        for(String poolId : sourceIndex.getPoolIds()) {
            byPool.put(poolId, computePoolDistances(terrain, poolId));
        }
        distancesByPool = java.util.Collections.unmodifiableMap(byPool);
    }

    public static WaterTopology forTerrain(TerrainMap terrain)
    {
        if(terrain == null) {
            throw new IllegalArgumentException("terrain is required");
        }
        String identity = identityOf(terrain);
        synchronized(CACHE) {
            WaterTopology cached = CACHE.get(identity);
            if(cached != null) return cached;
            WaterTopology created = new WaterTopology(terrain, identity);
            CACHE.put(identity, created);
            return created;
        }
    }

    public String getIdentity() { return identity; }
    public int getDepth() { return depth; }
    public int getWidth() { return width; }
    public WaterSourceIndex getSourceIndex() { return sourceIndex; }

    public int distanceToShore(Location location)
    {
        if(!inBounds(location)) return Integer.MAX_VALUE;
        return shoreDistance[location.row()][location.col()];
    }

    public int distanceToPool(Location location, String poolId)
    {
        int[][] distances = distancesByPool.get(poolId);
        if(distances == null || !inBounds(location)) return Integer.MAX_VALUE;
        return distances[location.row()][location.col()];
    }

    public int countDrinkableShores()
    {
        int count = 0;
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                if(shoreDistance[row][col] == 0) count++;
            }
        }
        return count;
    }

    public int maximumReachableShoreDistance()
    {
        int maximum = 0;
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                int distance = shoreDistance[row][col];
                if(distance != Integer.MAX_VALUE) maximum = Math.max(maximum, distance);
            }
        }
        return maximum;
    }

    public boolean matches(TerrainMap terrain)
    {
        return terrain != null && identity.equals(identityOf(terrain));
    }

    private static String identityOf(TerrainMap terrain)
    {
        return terrain.getGenerationProfile() + "|" + terrain.getGeneratorVersion() +
            "|" + terrain.getDepth() + "x" + terrain.getWidth() + "|" +
            terrain.getFingerprint();
    }

    private int[][] computeShoreDistances(TerrainMap terrain)
    {
        int[][] distances = emptyDistances();
        int[] queue = new int[depth * width];
        int head = 0;
        int tail = 0;
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                if(sourceIndex.isDrinkableShoreAt(row, col)) {
                    distances[row][col] = 0;
                    queue[tail++] = row * width + col;
                }
            }
        }
        floodPassable(terrain, distances, queue, head, tail);
        return distances;
    }

    private int[][] computePoolDistances(TerrainMap terrain, String poolId)
    {
        int[][] distances = emptyDistances();
        int[] queue = new int[depth * width];
        int head = 0;
        int tail = 0;
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                if(poolId.equals(sourceIndex.shorePoolAt(row, col))) {
                    distances[row][col] = 0;
                    queue[tail++] = row * width + col;
                }
            }
        }
        floodPassable(terrain, distances, queue, head, tail);
        return distances;
    }

    private void floodPassable(TerrainMap terrain, int[][] distances, int[] queue,
                               int head, int tail)
    {
        while(head < tail) {
            int encoded = queue[head++];
            int row = encoded / width;
            int col = encoded % width;
            int nextDistance = distances[row][col] + 1;
            for(int rowOffset = -1; rowOffset <= 1; rowOffset++) {
                for(int colOffset = -1; colOffset <= 1; colOffset++) {
                    if(rowOffset == 0 && colOffset == 0) continue;
                    int nextRow = row + rowOffset;
                    int nextCol = col + colOffset;
                    if(nextRow >= 0 && nextRow < depth && nextCol >= 0 &&
                       nextCol < width && distances[nextRow][nextCol] >
                       nextDistance && terrain.isPassableAt(nextRow, nextCol)) {
                        distances[nextRow][nextCol] = nextDistance;
                        queue[tail++] = nextRow * width + nextCol;
                    }
                }
            }
        }
    }

    private int[][] emptyDistances()
    {
        int[][] result = new int[depth][width];
        for(int row = 0; row < depth; row++) {
            java.util.Arrays.fill(result[row], Integer.MAX_VALUE);
        }
        return result;
    }

    private boolean inBounds(Location location)
    {
        return location != null && location.row() >= 0 && location.row() < depth &&
            location.col() >= 0 && location.col() < width;
    }
}
