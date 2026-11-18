package savanna;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable map-level passability, adjacency and bounded reachability order.
 *
 * <p>All BFS work happens while this topology is constructed. The resolver
 * receives only this immutable lookup structure and never runs a search. Each
 * cell stores at most the registered fallback budget, so retained topology
 * memory grows linearly with the terrain cell count.</p>
 */
public final class PositionTopology
{
    private final int depth;
    private final int width;
    private final boolean[] passable;
    private final int[][] adjacentPassable;
    private final int[][] orderedSearchFallbacks;
    private final PositionKernelPolicy policy;
    private final int precomputedFallbackCount;

    private PositionTopology(int depth, int width, boolean[] passable,
                             PositionKernelPolicy policy)
    {
        if(depth <= 0 || width <= 0 || passable == null ||
           passable.length != depth * width) {
            throw new IllegalArgumentException("Invalid position topology grid");
        }
        this.depth = depth;
        this.width = width;
        this.passable = Arrays.copyOf(passable, passable.length);
        this.policy = Objects.requireNonNull(policy, "position policy is required");
        adjacentPassable = buildAdjacency();
        orderedSearchFallbacks = new int[passable.length][];
        precomputedFallbackCount = precomputeSearchFallbacks();
    }

    public static PositionTopology fromTerrain(TerrainMap terrain,
                                               PositionKernelPolicy policy)
    {
        Objects.requireNonNull(terrain, "terrain is required");
        int depth = terrain.getDepth();
        int width = terrain.getWidth();
        boolean[] passable = new boolean[depth * width];
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                Location location = new Location(row, col);
                passable[row * width + col] = terrain.isPassable(location);
            }
        }
        return new PositionTopology(depth, width, passable, policy);
    }

    public static PositionTopology rectangular(int depth, int width,
                                               Set<Location> blocked,
                                               PositionKernelPolicy policy)
    {
        if(depth <= 0 || width <= 0) {
            throw new IllegalArgumentException("Topology dimensions must be positive");
        }
        boolean[] passable = new boolean[depth * width];
        Arrays.fill(passable, true);
        if(blocked != null) {
            for(Location location : blocked) {
                if(location != null && location.row() >= 0 &&
                   location.row() < depth && location.col() >= 0 &&
                   location.col() < width) {
                    passable[location.row() * width + location.col()] = false;
                }
            }
        }
        return new PositionTopology(depth, width, passable, policy);
    }

    public int getDepth()
    {
        return depth;
    }

    public int getWidth()
    {
        return width;
    }

    public PositionKernelPolicy getPolicy()
    {
        return policy;
    }

    public boolean inBounds(Location location)
    {
        return location != null && location.row() >= 0 &&
            location.row() < depth && location.col() >= 0 &&
            location.col() < width;
    }

    public boolean isPassable(Location location)
    {
        return inBounds(location) && passable[indexOf(location)];
    }

    public List<Location> getAdjacentPassable(Location location)
    {
        if(!inBounds(location)) {
            return Collections.emptyList();
        }
        return locationsFor(adjacentPassable[indexOf(location)]);
    }

    /** Returns the deterministic precomputed BFS order, capped by request. */
    public List<Location> getSearchFallbacks(Location origin, int budget)
    {
        if(budget < 0 || budget > policy.getFallbackCandidateBudget()) {
            throw new IllegalArgumentException("Search budget exceeds policy: " +
                budget);
        }
        if(!isPassable(origin) || budget == 0) {
            return Collections.emptyList();
        }
        int[] all = orderedSearchFallbacks[indexOf(origin)];
        int count = Math.min(budget, all.length);
        List<Location> result = new ArrayList<>(count);
        for(int index = 0; index < count; index++) {
            result.add(locationFor(all[index]));
        }
        return Collections.unmodifiableList(result);
    }

    public int getPrecomputedFallbackCount()
    {
        return precomputedFallbackCount;
    }

    private int[][] buildAdjacency()
    {
        int[][] adjacency = new int[passable.length][];
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                int index = indexOf(row, col);
                if(!passable[index]) {
                    adjacency[index] = new int[0];
                    continue;
                }
                int[] neighbors = new int[8];
                int count = 0;
                for(int rowDelta = -1; rowDelta <= 1; rowDelta++) {
                    for(int colDelta = -1; colDelta <= 1; colDelta++) {
                        if(rowDelta == 0 && colDelta == 0) {
                            continue;
                        }
                        int nextRow = row + rowDelta;
                        int nextCol = col + colDelta;
                        if(nextRow >= 0 && nextRow < depth && nextCol >= 0 &&
                           nextCol < width) {
                            int next = indexOf(nextRow, nextCol);
                            if(passable[next]) {
                                neighbors[count++] = next;
                            }
                        }
                    }
                }
                adjacency[index] = Arrays.copyOf(neighbors, count);
            }
        }
        return adjacency;
    }

    private int precomputeSearchFallbacks()
    {
        int[] marks = new int[passable.length];
        int[] heapNodes = new int[passable.length];
        int[] heapDistances = new int[passable.length];
        int stamp = 0;
        int total = 0;
        for(int origin = 0; origin < passable.length; origin++) {
            if(!passable[origin]) {
                orderedSearchFallbacks[origin] = new int[0];
                continue;
            }
            if(stamp == Integer.MAX_VALUE) {
                Arrays.fill(marks, 0);
                stamp = 0;
            }
            stamp++;
            int count = precomputeForOrigin(origin, marks, stamp, heapNodes,
                heapDistances);
            total += count;
        }
        return total;
    }

    private int precomputeForOrigin(int origin, int[] marks, int stamp,
                                    int[] heapNodes, int[] heapDistances)
    {
        int size = 0;
        size = push(heapNodes, heapDistances, size, origin, 0);
        marks[origin] = stamp;
        int[] candidates = new int[policy.getFallbackCandidateBudget()];
        int count = 0;
        while(size > 0 && count < candidates.length) {
            int cell = heapNodes[0];
            int distance = heapDistances[0];
            size = pop(heapNodes, heapDistances, size);
            if(distance > 0) {
                candidates[count++] = cell;
            }
            if(distance >= policy.getSearchRadius()) {
                continue;
            }
            for(int neighbor : adjacentPassable[cell]) {
                if(marks[neighbor] != stamp) {
                    marks[neighbor] = stamp;
                    size = push(heapNodes, heapDistances, size, neighbor,
                        distance + 1);
                }
            }
        }
        orderedSearchFallbacks[origin] = Arrays.copyOf(candidates, count);
        return count;
    }

    /** Binary heap ordered by path distance then row-major cell index. */
    private static int push(int[] nodes, int[] distances, int size, int node,
                            int distance)
    {
        int position = size++;
        while(position > 0) {
            int parent = (position - 1) / 2;
            if(compare(distance, node, distances[parent], nodes[parent]) >= 0) {
                break;
            }
            nodes[position] = nodes[parent];
            distances[position] = distances[parent];
            position = parent;
        }
        nodes[position] = node;
        distances[position] = distance;
        return size;
    }

    private static int pop(int[] nodes, int[] distances, int size)
    {
        int last = --size;
        if(last == 0) {
            return 0;
        }
        int node = nodes[last];
        int distance = distances[last];
        int position = 0;
        while(true) {
            int left = position * 2 + 1;
            if(left >= size) {
                break;
            }
            int right = left + 1;
            int child = right < size && compare(distances[right], nodes[right],
                distances[left], nodes[left]) < 0 ? right : left;
            if(compare(distance, node, distances[child], nodes[child]) <= 0) {
                break;
            }
            nodes[position] = nodes[child];
            distances[position] = distances[child];
            position = child;
        }
        nodes[position] = node;
        distances[position] = distance;
        return size;
    }

    private static int compare(int firstDistance, int firstNode,
                               int secondDistance, int secondNode)
    {
        int distance = Integer.compare(firstDistance, secondDistance);
        return distance != 0 ? distance : Integer.compare(firstNode, secondNode);
    }

    private List<Location> locationsFor(int[] cells)
    {
        if(cells == null || cells.length == 0) {
            return Collections.emptyList();
        }
        List<Location> result = new ArrayList<>(cells.length);
        for(int cell : cells) {
            result.add(locationFor(cell));
        }
        return Collections.unmodifiableList(result);
    }

    private int indexOf(Location location)
    {
        return indexOf(location.row(), location.col());
    }

    private int indexOf(int row, int col)
    {
        return row * width + col;
    }

    private Location locationFor(int index)
    {
        return new Location(index / width, index % width);
    }
}
