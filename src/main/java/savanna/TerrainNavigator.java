package savanna;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class TerrainNavigator
{
    private TerrainNavigator() {}

    public static List<Location> freeAdjacent(Field field, TerrainMap terrain,
                                              Location from)
    {
        return freeAdjacent(field, terrain, from, Randomizer.getRandom());
    }

    public static List<Location> freeAdjacent(Field field, TerrainMap terrain,
                                              Location from, Random rand)
    {
        List<Location> result = new ArrayList<>();
        if(from == null || terrain == null || !terrain.isPassable(from)) {
            return result;
        }
        for(int dr = -1; dr <= 1; dr++) {
            for(int dc = -1; dc <= 1; dc++) {
                if(dr == 0 && dc == 0) {
                    continue;
                }
                Location next = new Location(from.row() + dr, from.col() + dc);
                if(inBounds(terrain, next) && terrain.isPassable(next)) {
                    Animal animal = field.getAnimalAt(next);
                    if(animal == null || !animal.isAlive()) {
                        result.add(next);
                    }
                }
            }
        }
        Collections.shuffle(result, rand);
        return result;
    }

    public static List<Location> freeReachableWithin(Field field,
                                                     TerrainMap terrain,
                                                     Location from,
                                                     int range)
    {
        return freeReachableWithin(field, terrain, from, range,
                                   Randomizer.getRandom());
    }

    public static List<Location> freeReachableWithin(Field field,
                                                     TerrainMap terrain,
                                                     Location from,
                                                     int range,
                                                     Random rand)
    {
        List<Location> result = new ArrayList<>();
        for(Location location : reachableWithin(terrain, from, range)) {
            Animal animal = field.getAnimalAt(location);
            if(animal == null || !animal.isAlive()) {
                result.add(location);
            }
        }
        Collections.shuffle(result, rand);
        return result;
    }

    public static List<Location> reachableWithin(TerrainMap terrain,
                                                 Location from,
                                                 int range)
    {
        List<Location> result = new ArrayList<>();
        if(terrain == null) {
            return result;
        }
        Reachability reachability = reachableDistances(terrain, from, range);
        for(int row = reachability.getFirstRow();
            row <= reachability.getLastRow(); row++) {
            for(int col = reachability.getFirstCol();
                col <= reachability.getLastCol(); col++) {
                Location location = new Location(row, col);
                int distance = reachability.distanceTo(location);
                if(distance > 0 && distance <= range) {
                    result.add(location);
                }
            }
        }
        return result;
    }

    public static Reachability reachableDistances(TerrainMap terrain,
                                                  Location from,
                                                  int range)
    {
        int depth = terrain == null ? 0 : terrain.getDepth();
        int width = terrain == null ? 0 : terrain.getWidth();
        if(from == null || terrain == null || !inBounds(terrain, from) ||
           !terrain.isPassableAt(from.row(), from.col())) {
            return Reachability.empty(depth, width);
        }
        int boundedRange = Math.max(0, range);
        int firstRow = Math.max(0, from.row() - boundedRange);
        int lastRow = Math.min(depth - 1, from.row() + boundedRange);
        int firstCol = Math.max(0, from.col() - boundedRange);
        int lastCol = Math.min(width - 1, from.col() + boundedRange);
        int localDepth = lastRow - firstRow + 1;
        int localWidth = lastCol - firstCol + 1;
        int[][] distance = new int[localDepth][localWidth];
        for(int row = 0; row < localDepth; row++) Arrays.fill(distance[row], -1);
        int[] queue = new int[localDepth * localWidth];
        int head = 0;
        int tail = 0;
        int originRow = from.row() - firstRow;
        int originCol = from.col() - firstCol;
        distance[originRow][originCol] = 0;
        queue[tail++] = originRow * localWidth + originCol;

        while(head < tail) {
            int encoded = queue[head++];
            int localRow = encoded / localWidth;
            int localCol = encoded % localWidth;
            int currentDistance = distance[localRow][localCol];
            if(currentDistance >= range) {
                continue;
            }
            for(int dr = -1; dr <= 1; dr++) {
                for(int dc = -1; dc <= 1; dc++) {
                    if(dr == 0 && dc == 0) {
                        continue;
                    }
                    int nextRow = localRow + dr;
                    int nextCol = localCol + dc;
                    if(nextRow >= 0 && nextRow < localDepth && nextCol >= 0 &&
                       nextCol < localWidth && distance[nextRow][nextCol] < 0 &&
                       terrain.isPassableAt(firstRow + nextRow,
                           firstCol + nextCol)) {
                        distance[nextRow][nextCol] = currentDistance + 1;
                        queue[tail++] = nextRow * localWidth + nextCol;
                    }
                }
            }
        }
        return new Reachability(distance, firstRow, firstCol, depth, width);
    }

    public static Location nearestFreePassable(Field field, TerrainMap terrain,
                                               Location from, int maxRange)
    {
        return nearestFreePassable(field, terrain, from, maxRange,
                                   Collections.<Location>emptySet());
    }

    public static Location nearestFreePassable(Field field, TerrainMap terrain,
                                               Location from, int maxRange,
                                               Set<Location> excluded)
    {
        if(field == null || terrain == null || from == null || maxRange <= 0) {
            return null;
        }
        Reachability reachability = reachableDistances(terrain, from, maxRange);
        Location best = null;
        int bestDistance = Integer.MAX_VALUE;
        for(int row = reachability.getFirstRow();
            row <= reachability.getLastRow(); row++) {
            for(int col = reachability.getFirstCol();
                col <= reachability.getLastCol(); col++) {
                Location location = new Location(row, col);
                int distance = reachability.distanceTo(location);
                if(distance <= 0 || distance > maxRange ||
                   (excluded != null && excluded.contains(location))) {
                    continue;
                }
                Animal animal = field.getAnimalAt(location);
                if((animal == null || !animal.isAlive()) &&
                   (distance < bestDistance ||
                    (distance == bestDistance && compare(location, best) < 0))) {
                    best = location;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static int compare(Location first, Location second)
    {
        if(second == null) {
            return -1;
        }
        int rowComparison = Integer.compare(first.row(), second.row());
        return rowComparison != 0 ? rowComparison
                                  : Integer.compare(first.col(), second.col());
    }

    private static boolean inBounds(TerrainMap terrain, Location location)
    {
        return location.row() >= 0 && location.row() < terrain.getDepth() &&
               location.col() >= 0 && location.col() < terrain.getWidth();
    }

    public static class Reachability
    {
        private final int[][] distance;
        private final int firstRow;
        private final int firstCol;
        private final int depth;
        private final int width;

        private Reachability(int[][] distance, int firstRow, int firstCol,
                             int depth, int width)
        {
            this.distance = distance;
            this.firstRow = firstRow;
            this.firstCol = firstCol;
            this.depth = depth;
            this.width = width;
        }

        private static Reachability empty(int depth, int width)
        {
            return new Reachability(new int[0][0], 0, 0, depth, width);
        }

        public int distanceTo(Location location)
        {
            if(location == null || location.row() < 0 ||
               location.row() >= depth || location.col() < 0 ||
               location.col() >= width || distance.length == 0) {
                return -1;
            }
            int row = location.row() - firstRow;
            int col = location.col() - firstCol;
            if(row < 0 || row >= distance.length || col < 0 ||
               col >= distance[0].length) return -1;
            return distance[row][col];
        }

        int getFirstRow() { return firstRow; }
        int getLastRow() { return firstRow + distance.length - 1; }
        int getFirstCol() { return firstCol; }
        int getLastCol()
        {
            return firstCol + (distance.length == 0 ? -1 : distance[0].length - 1);
        }
    }
}
