package savanna;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Produces the mask contract used by TV1 terrain art.
 *
 * <p>The production entry point accepts the existing {@link TerrainMap},
 * {@link VisualGridGeometry} and {@link VisualWaterMask}.  It does not own a
 * second terrain grid, generate random placements or draw pixels.  A later
 * renderer can therefore use its surface/transition/shoreline masks to clip
 * image tiles while retaining the same water definition used by ordinary
 * markers, inspect actors and hit testing.</p>
 */
public final class TerrainVisualComposer
{
    private static final int[][] CARDINAL_DIRECTIONS = {
        {-1, 0}, {1, 0}, {0, -1}, {0, 1}
    };

    private TerrainVisualComposer()
    {
    }

    /**
     * Builds a composition from exactly the existing logical terrain and its
     * visual-water mask.  Mismatched masks are rejected rather than silently
     * making a new water definition for artwork.
     */
    public static TerrainVisualComposition compose(TerrainMap terrain,
                                                    VisualGridGeometry geometry,
                                                    VisualWaterMask waterMask,
                                                    TerrainVisualKit kit)
    {
        if(terrain == null || geometry == null || waterMask == null || kit == null) {
            throw new IllegalArgumentException("Terrain, geometry, water mask and kit are required.");
        }
        if(waterMask.getTerrain() != terrain ||
           waterMask.getGeometry().rows() != geometry.rows() ||
           waterMask.getGeometry().cols() != geometry.cols() ||
           waterMask.getGeometry().pixelWidth() != geometry.pixelWidth() ||
           waterMask.getGeometry().pixelHeight() != geometry.pixelHeight()) {
            throw new IllegalArgumentException("Terrain visual composition requires the shared water mask.");
        }
        if(terrain.getDepth() != geometry.rows() || terrain.getWidth() != geometry.cols()) {
            throw new IllegalArgumentException("Geometry dimensions do not match TerrainMap.");
        }

        TerrainType[][] cells = new TerrainType[terrain.getDepth()][terrain.getWidth()];
        for(int row = 0; row < cells.length; row++) {
            for(int col = 0; col < cells[row].length; col++) {
                cells[row][col] = terrain.getTerrainAt(new Location(row, col));
            }
        }
        return new TerrainVisualComposition(terrain, geometry, waterMask, kit,
            topologyForGrid(cells));
    }

    /**
     * Static topology helper for TV1 fixtures.  It is intentionally separate
     * from production composition: only {@link #compose} may establish the
     * actual visual-water contract for a running map.
     */
    public static TerrainTopology topologyForGrid(TerrainType[][] grid)
    {
        TerrainType[][] cells = copyGrid(grid);
        int rows = cells.length;
        int cols = cells[0].length;
        Map<TerrainType, boolean[][]> surfaces = new EnumMap<>(TerrainType.class);
        for(TerrainType type : TerrainType.values()) {
            surfaces.put(type, new boolean[rows][cols]);
        }
        Map<TransitionKey, boolean[][]> transitions = new HashMap<>();
        for(int row = 0; row < rows; row++) {
            for(int col = 0; col < cols; col++) {
                TerrainType current = cells[row][col];
                surfaces.get(current)[row][col] = true;
                for(int[] direction : CARDINAL_DIRECTIONS) {
                    int nextRow = row + direction[0];
                    int nextCol = col + direction[1];
                    if(nextRow < 0 || nextRow >= rows || nextCol < 0 ||
                       nextCol >= cols) {
                        continue;
                    }
                    TerrainType next = cells[nextRow][nextCol];
                    if(next == current) continue;
                    TransitionKey key = new TransitionKey(current, next);
                    boolean[][] mask = transitions.get(key);
                    if(mask == null) {
                        mask = new boolean[rows][cols];
                        transitions.put(key, mask);
                    }
                    mask[row][col] = true;
                    mask[nextRow][nextCol] = true;
                }
            }
        }
        Map<TerrainType, CellMask> frozenSurfaces = new EnumMap<>(TerrainType.class);
        for(Map.Entry<TerrainType, boolean[][]> entry : surfaces.entrySet()) {
            frozenSurfaces.put(entry.getKey(), new CellMask(entry.getValue()));
        }
        Map<TransitionKey, CellMask> frozenTransitions = new HashMap<>();
        for(Map.Entry<TransitionKey, boolean[][]> entry : transitions.entrySet()) {
            frozenTransitions.put(entry.getKey(), new CellMask(entry.getValue()));
        }
        return new TerrainTopology(cells, frozenSurfaces, frozenTransitions);
    }

    /** Immutable grid mask which preserves holes, concavity and narrow runs. */
    public static final class CellMask
    {
        private final boolean[][] cells;
        private final int rows;
        private final int cols;
        private final int count;

        private CellMask(boolean[][] source)
        {
            rows = source.length;
            cols = source[0].length;
            cells = new boolean[rows][cols];
            int total = 0;
            for(int row = 0; row < rows; row++) {
                System.arraycopy(source[row], 0, cells[row], 0, cols);
                for(int col = 0; col < cols; col++) {
                    if(cells[row][col]) total++;
                }
            }
            count = total;
        }

        public int rows() { return rows; }
        public int cols() { return cols; }
        public int count() { return count; }

        public boolean contains(int row, int col)
        {
            return row >= 0 && row < rows && col >= 0 && col < cols &&
                   cells[row][col];
        }

        /** Four-neighbour components; diagonal touch does not bridge a mask. */
        public int componentCount4()
        {
            boolean[][] seen = new boolean[rows][cols];
            int components = 0;
            for(int row = 0; row < rows; row++) {
                for(int col = 0; col < cols; col++) {
                    if(!cells[row][col] || seen[row][col]) continue;
                    components++;
                    java.util.ArrayDeque<int[]> pending = new java.util.ArrayDeque<>();
                    pending.add(new int[] {row, col});
                    seen[row][col] = true;
                    while(!pending.isEmpty()) {
                        int[] point = pending.removeFirst();
                        for(int[] direction : CARDINAL_DIRECTIONS) {
                            int nextRow = point[0] + direction[0];
                            int nextCol = point[1] + direction[1];
                            if(nextRow >= 0 && nextRow < rows && nextCol >= 0 &&
                               nextCol < cols && cells[nextRow][nextCol] &&
                               !seen[nextRow][nextCol]) {
                                seen[nextRow][nextCol] = true;
                                pending.add(new int[] {nextRow, nextCol});
                            }
                        }
                    }
                }
            }
            return components;
        }
    }

    /** Logical terrain surfaces and transitions, useful for deterministic fixtures. */
    public static final class TerrainTopology
    {
        private final TerrainType[][] cells;
        private final Map<TerrainType, CellMask> surfaces;
        private final Map<TransitionKey, CellMask> transitions;

        private TerrainTopology(TerrainType[][] cells,
                                Map<TerrainType, CellMask> surfaces,
                                Map<TransitionKey, CellMask> transitions)
        {
            this.cells = cells;
            this.surfaces = Collections.unmodifiableMap(new EnumMap<>(surfaces));
            this.transitions = Collections.unmodifiableMap(new HashMap<>(transitions));
        }

        public int rows() { return cells.length; }
        public int cols() { return cells[0].length; }
        public TerrainType terrainAt(int row, int col) { return cells[row][col]; }
        public CellMask surfaceMask(TerrainType type) { return surfaces.get(type); }

        public CellMask transitionMask(TerrainType first, TerrainType second)
        {
            if(first == null || second == null || first == second) {
                return new CellMask(new boolean[rows()][cols()]);
            }
            CellMask result = transitions.get(new TransitionKey(first, second));
            return result == null ? new CellMask(new boolean[rows()][cols()]) : result;
        }

        public Map<TransitionKey, CellMask> getTransitionMasks()
        {
            return transitions;
        }
    }

    /**
     * Production composition.  Pixel predicates are the only permitted way
     * for TV1 decoration and shore art to escape a cell mask: each one also
     * asks the shared {@link VisualWaterMask} before allowing paint.
     */
    public static final class TerrainVisualComposition
    {
        private final TerrainMap terrain;
        private final VisualGridGeometry geometry;
        private final VisualWaterMask waterMask;
        private final TerrainVisualKit kit;
        private final TerrainTopology topology;
        private final CellMask shorelineCells;

        private TerrainVisualComposition(TerrainMap terrain,
                                         VisualGridGeometry geometry,
                                         VisualWaterMask waterMask,
                                         TerrainVisualKit kit,
                                         TerrainTopology topology)
        {
            this.terrain = terrain;
            this.geometry = geometry;
            this.waterMask = waterMask;
            this.kit = kit;
            this.topology = topology;
            this.shorelineCells = buildShorelineCells();
        }

        public TerrainMap getTerrain() { return terrain; }
        public VisualGridGeometry getGeometry() { return geometry; }
        public VisualWaterMask getWaterMask() { return waterMask; }
        public TerrainVisualKit getKit() { return kit; }
        public TerrainTopology getTopology() { return topology; }
        public CellMask getSurfaceMask(TerrainType type) { return topology.surfaceMask(type); }
        public CellMask getShorelineCells() { return shorelineCells; }

        /** Surface tiling is clipped to exactly the logical terrain type. */
        public boolean allowsSurfacePixel(TerrainType type, int x, int y)
        {
            if(type == null || !insidePixel(x, y)) return false;
            return terrain.getTerrainAt(locationForPixel(x, y)) == type;
        }

        /**
         * Transparent decor may only paint non-water pixels of its declared
         * terrain type.  This is intentionally stricter than the logical
         * surface mask near a water buffer.
         */
        public boolean allowsDecorationPixel(TerrainVisualKit.MaterialDescriptor material,
                                             int x, int y)
        {
            return material != null && material.getRole() == TerrainVisualKit.LayerRole.DECORATION &&
                   material.getTerrainType() != null &&
                   allowsSurfacePixel(material.getTerrainType(), x, y) &&
                   !waterMask.isWaterPixel(x, y);
        }

        /** Non-water terrain transitions are clipped to both their cell mask and land. */
        public boolean allowsTransitionPixel(TerrainType first, TerrainType second,
                                             int x, int y)
        {
            if(!insidePixel(x, y) || waterMask.isWaterPixel(x, y)) return false;
            return topology.transitionMask(first, second).contains(
                geometry.cellRowAtPixel(y), geometry.cellColAtPixel(x));
        }

        /** Shore art always remains on visible land, never on the water body. */
        public boolean allowsShorelinePixel(int x, int y)
        {
            if(!insidePixel(x, y) || waterMask.isWaterPixel(x, y)) return false;
            return shorelineCells.contains(geometry.cellRowAtPixel(y),
                                           geometry.cellColAtPixel(x));
        }

        private CellMask buildShorelineCells()
        {
            boolean[][] shore = new boolean[geometry.rows()][geometry.cols()];
            for(int row = 0; row < geometry.rows(); row++) {
                for(int col = 0; col < geometry.cols(); col++) {
                    Location location = new Location(row, col);
                    if(!terrain.isPassable(location)) continue;
                    Rectangle cell = geometry.cellBounds(row, col);
                    boolean hasWater = false;
                    boolean hasLand = false;
                    for(int y = cell.y; y < cell.y + cell.height; y++) {
                        for(int x = cell.x; x < cell.x + cell.width; x++) {
                            if(waterMask.isWaterPixel(x, y)) hasWater = true;
                            else hasLand = true;
                        }
                    }
                    shore[row][col] = hasWater && hasLand;
                }
            }
            return new CellMask(shore);
        }

        private Location locationForPixel(int x, int y)
        {
            return new Location(geometry.cellRowAtPixel(y),
                                geometry.cellColAtPixel(x));
        }

        private boolean insidePixel(int x, int y)
        {
            return x >= 0 && x < geometry.pixelWidth() &&
                   y >= 0 && y < geometry.pixelHeight();
        }
    }

    /** Normalized unordered terrain-pair key for transition masks. */
    public static final class TransitionKey
    {
        private final TerrainType first;
        private final TerrainType second;

        private TransitionKey(TerrainType first, TerrainType second)
        {
            if(first == null || second == null || first == second) {
                throw new IllegalArgumentException("A transition needs two different terrain types.");
            }
            if(first.ordinal() < second.ordinal()) {
                this.first = first;
                this.second = second;
            }
            else {
                this.first = second;
                this.second = first;
            }
        }

        public TerrainType getFirst() { return first; }
        public TerrainType getSecond() { return second; }

        @Override
        public boolean equals(Object other)
        {
            if(this == other) return true;
            if(!(other instanceof TransitionKey)) return false;
            TransitionKey key = (TransitionKey)other;
            return first == key.first && second == key.second;
        }

        @Override
        public int hashCode()
        {
            return first.ordinal() * 31 + second.ordinal();
        }
    }

    private static TerrainType[][] copyGrid(TerrainType[][] grid)
    {
        if(grid == null || grid.length == 0 || grid[0] == null || grid[0].length == 0) {
            throw new IllegalArgumentException("Terrain grid must not be empty.");
        }
        int cols = grid[0].length;
        TerrainType[][] copy = new TerrainType[grid.length][cols];
        for(int row = 0; row < grid.length; row++) {
            if(grid[row] == null || grid[row].length != cols) {
                throw new IllegalArgumentException("Terrain grid must be rectangular.");
            }
            for(int col = 0; col < cols; col++) {
                if(grid[row][col] == null) {
                    throw new IllegalArgumentException("Terrain grid contains null at " +
                                                       row + "," + col);
                }
                copy[row][col] = grid[row][col];
            }
        }
        return copy;
    }
}
