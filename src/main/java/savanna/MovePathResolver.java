package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Frame-export helper (plan v3.2 V1): one shortest land path between a move's
 * start and end, as the cells strictly between them. Read-only, no random
 * numbers; neighbours are visited in a fixed order, so the result is
 * deterministic.
 */
final class MovePathResolver
{
    /** Longest move a frame can describe: 6 steps, hence at most 5 intermediate cells. */
    static final int MAX_STEPS = 6;

    private MovePathResolver()
    {
    }

    /**
     * @return the intermediate cells (empty for a move of one cell or none), or
     *         {@code null} when no land path of at most {@link #MAX_STEPS} exists
     */
    static List<Location> between(TerrainMap terrain, Location from, Location to)
    {
        int cheb = Math.max(Math.abs(from.row() - to.row()), Math.abs(from.col() - to.col()));
        if(cheb <= 1) return Collections.emptyList();
        if(cheb > MAX_STEPS) return null;
        int side = 2 * MAX_STEPS + 1;
        int[] parent = new int[side * side];
        java.util.Arrays.fill(parent, -2);
        int baseRow = from.row() - MAX_STEPS;
        int baseCol = from.col() - MAX_STEPS;
        int[] queue = new int[side * side];
        int[] depthOf = new int[side * side];
        int head = 0;
        int tail = 0;
        int start = MAX_STEPS * side + MAX_STEPS;
        parent[start] = -1;
        queue[tail++] = start;
        int goal = (to.row() - baseRow) * side + (to.col() - baseCol);
        while(head < tail) {
            int current = queue[head++];
            if(current == goal) break;
            if(depthOf[current] >= MAX_STEPS) continue;
            int row = current / side + baseRow;
            int col = current % side + baseCol;
            for(int dr = -1; dr <= 1; dr++) {
                for(int dc = -1; dc <= 1; dc++) {
                    if(dr == 0 && dc == 0) continue;
                    int nextRow = row + dr;
                    int nextCol = col + dc;
                    int lr = nextRow - baseRow;
                    int lc = nextCol - baseCol;
                    if(lr < 0 || lr >= side || lc < 0 || lc >= side) continue;
                    int cell = lr * side + lc;
                    if(parent[cell] != -2 || !terrain.isPassableAt(nextRow, nextCol)) continue;
                    parent[cell] = current;
                    depthOf[cell] = depthOf[current] + 1;
                    queue[tail++] = cell;
                }
            }
        }
        if(parent[goal] == -2) return null;
        List<Location> path = new ArrayList<>();
        for(int cell = parent[goal]; cell != start && cell != -1; cell = parent[cell]) {
            path.add(new Location(cell / side + baseRow, cell % side + baseCol));
        }
        Collections.reverse(path);
        return path;
    }
}
