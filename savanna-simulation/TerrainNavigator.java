import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Random;

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
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
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
        int[][] distance = new int[depth][width];
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                distance[row][col] = -1;
            }
        }
        if(from == null || terrain == null || !terrain.isPassable(from)) {
            return new Reachability(distance);
        }

        Queue<Location> queue = new ArrayDeque<>();
        distance[from.row()][from.col()] = 0;
        queue.add(from);

        while(!queue.isEmpty()) {
            Location current = queue.remove();
            int currentDistance = distance[current.row()][current.col()];
            if(currentDistance >= range) {
                continue;
            }
            for(int dr = -1; dr <= 1; dr++) {
                for(int dc = -1; dc <= 1; dc++) {
                    if(dr == 0 && dc == 0) {
                        continue;
                    }
                    Location next = new Location(current.row() + dr,
                                                 current.col() + dc);
                    if(inBounds(terrain, next) &&
                       distance[next.row()][next.col()] < 0 &&
                       terrain.isPassable(next)) {
                        distance[next.row()][next.col()] = currentDistance + 1;
                        queue.add(next);
                    }
                }
            }
        }
        return new Reachability(distance);
    }

    public static Location nearestFreePassable(Field field, TerrainMap terrain,
                                               Location from, int maxRange)
    {
        for(Location location : reachableWithin(terrain, from, maxRange)) {
            Animal animal = field.getAnimalAt(location);
            if(animal == null || !animal.isAlive()) {
                return location;
            }
        }
        return null;
    }

    private static boolean inBounds(TerrainMap terrain, Location location)
    {
        return location.row() >= 0 && location.row() < terrain.getDepth() &&
               location.col() >= 0 && location.col() < terrain.getWidth();
    }

    public static class Reachability
    {
        private final int[][] distance;

        private Reachability(int[][] distance)
        {
            this.distance = distance;
        }

        public int distanceTo(Location location)
        {
            if(location == null ||
               location.row() < 0 || location.row() >= distance.length ||
               distance.length == 0 ||
               location.col() < 0 || location.col() >= distance[0].length) {
                return -1;
            }
            return distance[location.row()][location.col()];
        }
    }
}
