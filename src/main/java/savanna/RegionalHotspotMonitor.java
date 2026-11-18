package savanna;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Plan v3.0 M2-4: regional prey hotspots and the regional population ledger.
 *
 * <p>Overload uses fixed and offset 12x12 windows: windows of size 12 laid
 * on a stride-6 grid, so a herd straddling a fixed boundary is still seen
 * whole. A window holding more than {@link #OVERLOAD_PREY} prey is
 * overloaded; 8-connected overloaded windows form a hotspot. A hotspot inherits the longest span of any previous-step
 * hotspot it overlaps or touches, so drifting, splitting or merging never
 * resets its history. Per fixed (non-overlapping) 12x12 window, the ledger keeps
 * {@code end = start + births - deaths + immigration - emigration}.</p>
 *
 * <p>Pure observer: it reads committed fields only and draws no random
 * numbers.</p>
 */
public final class RegionalHotspotMonitor
{
    public static final int WINDOW = 12;
    public static final int STRIDE = WINDOW / 2;
    public static final int OVERLOAD_PREY = 30;

    private final int windowRows;
    private final int windowCols;
    private final int blockRows;
    private final int blockCols;
    private Map<Long, Integer> previousWindowById = new HashMap<>();
    private int[][] previousSpan;
    private final int[][] start;
    private final int[][] births;
    private final int[][] deaths;
    private final int[][] immigration;
    private final int[][] emigration;
    private int[][] current;
    private int maxSpan;
    private int overloadedWindowSteps;
    private int steps;
    private boolean first = true;

    public RegionalHotspotMonitor(int depth, int width)
    {
        windowRows = (depth + WINDOW - 1) / WINDOW;
        windowCols = (width + WINDOW - 1) / WINDOW;
        blockRows = (depth + STRIDE - 1) / STRIDE;
        blockCols = (width + STRIDE - 1) / STRIDE;
        previousSpan = new int[blockRows][blockCols];
        start = new int[windowRows][windowCols];
        births = new int[windowRows][windowCols];
        deaths = new int[windowRows][windowCols];
        immigration = new int[windowRows][windowCols];
        emigration = new int[windowRows][windowCols];
        current = new int[windowRows][windowCols];
    }

    /** Observes one committed field (call once per step, including step 0). */
    public void observe(Field field)
    {
        int[][] preyBlocks = new int[blockRows][blockCols];
        int[][] allCount = new int[windowRows][windowCols];
        Map<Long, Integer> windowById = new HashMap<>();
        for(Animal animal : field.getAnimals()) {
            if(!animal.isAlive() || !(animal instanceof SavannahAnimal) ||
               animal.getLocation() == null) continue;
            Location location = animal.getLocation();
            int windowRow = location.row() / WINDOW;
            int windowCol = location.col() / WINDOW;
            int window = windowRow * windowCols + windowCol;
            windowById.put(animal.getId(), window);
            allCount[windowRow][windowCol]++;
            if(!((SavannahAnimal)animal).getProfile().isPredator()) {
                preyBlocks[location.row() / STRIDE][location.col() / STRIDE]++;
            }
            if(first) continue;
            Integer before = previousWindowById.get(animal.getId());
            if(before == null) {
                births[windowRow][windowCol]++;
            }
            else if(before.intValue() != window) {
                emigration[before / windowCols][before % windowCols]++;
                immigration[windowRow][windowCol]++;
            }
        }
        if(first) {
            for(int row = 0; row < windowRows; row++) {
                start[row] = allCount[row].clone();
            }
        }
        else {
            for(Map.Entry<Long, Integer> entry : previousWindowById.entrySet()) {
                if(!windowById.containsKey(entry.getKey())) {
                    int window = entry.getValue().intValue();
                    deaths[window / windowCols][window % windowCols]++;
                }
            }
        }
        current = allCount;
        previousWindowById = windowById;
        updateHotspots(slidingWindows(preyBlocks));
        first = false;
        steps++;
    }

    /** Prey per 12x12 window whose corner sits on the stride-6 grid. */
    private int[][] slidingWindows(int[][] blocks)
    {
        int[][] windows = new int[blockRows][blockCols];
        for(int row = 0; row < blockRows; row++) {
            for(int col = 0; col < blockCols; col++) {
                int total = 0;
                for(int dr = 0; dr <= 1 && row + dr < blockRows; dr++) {
                    for(int dc = 0; dc <= 1 && col + dc < blockCols; dc++) {
                        total += blocks[row + dr][col + dc];
                    }
                }
                windows[row][col] = total;
            }
        }
        return windows;
    }

    private void updateHotspots(int[][] preyCount)
    {
        int windowRows = blockRows;
        int windowCols = blockCols;
        int[][] span = new int[windowRows][windowCols];
        boolean[][] seen = new boolean[windowRows][windowCols];
        for(int row = 0; row < windowRows; row++) {
            for(int col = 0; col < windowCols; col++) {
                if(seen[row][col] || preyCount[row][col] <= OVERLOAD_PREY) continue;
                Set<Integer> component = new HashSet<>();
                Deque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[] { row, col });
                seen[row][col] = true;
                int inherited = 0;
                while(!queue.isEmpty()) {
                    int[] cell = queue.removeFirst();
                    component.add(cell[0] * windowCols + cell[1]);
                    for(int dr = -1; dr <= 1; dr++) {
                        for(int dc = -1; dc <= 1; dc++) {
                            int r = cell[0] + dr;
                            int c = cell[1] + dc;
                            if(r < 0 || c < 0 || r >= windowRows || c >= windowCols) continue;
                            inherited = Math.max(inherited, previousSpan[r][c]);
                            if(!seen[r][c] && preyCount[r][c] > OVERLOAD_PREY) {
                                seen[r][c] = true;
                                queue.add(new int[] { r, c });
                            }
                        }
                    }
                }
                int hotspotSpan = inherited + 1;
                for(int window : component) {
                    span[window / windowCols][window % windowCols] = hotspotSpan;
                }
                overloadedWindowSteps += component.size();
                maxSpan = Math.max(maxSpan, hotspotSpan);
            }
        }
        previousSpan = span;
    }

    /** Longest continuous hotspot history (steps), across drift/split/merge. */
    public int getMaxOverloadSpan() { return maxSpan; }

    public int getOverloadedWindowSteps() { return overloadedWindowSteps; }

    public int getObservedSteps() { return steps; }

    /** True when every window satisfies end = start + births - deaths + in - out. */
    public boolean ledgerCloses()
    {
        for(int row = 0; row < windowRows; row++) {
            for(int col = 0; col < windowCols; col++) {
                if(current[row][col] != start[row][col] + births[row][col] -
                   deaths[row][col] + immigration[row][col] - emigration[row][col]) {
                    return false;
                }
            }
        }
        return true;
    }

    public int getTotalImmigration() { return sum(immigration); }

    private static int sum(int[][] values)
    {
        int total = 0;
        for(int[] row : values) {
            for(int value : row) total += value;
        }
        return total;
    }
}
