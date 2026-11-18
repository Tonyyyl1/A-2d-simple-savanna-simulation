package savanna;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;

/** Zero-step geometry and repeatability checks for the TV3 preview generator. */
public final class TerrainProceduralPreviewTests
{
    private static final long[] SEEDS = {4201L, 9317L, 17689L, 24107L};
    private static int checks;

    private TerrainProceduralPreviewTests()
    {
    }

    public static void main(String[] args)
    {
        verifyLegacyIsUnchanged();
        TerrainMap[] maps = new TerrainMap[SEEDS.length];
        for(int index = 0; index < SEEDS.length; index++) {
            maps[index] = TerrainMap.proceduralPreview(80, 120, SEEDS[index]);
            verifyMap(maps[index]);
            TerrainMap repeated = TerrainMap.proceduralPreview(80, 120,
                SEEDS[index]);
            check("same seed has exact procedural fingerprint " + SEEDS[index],
                maps[index].getFingerprint().equals(repeated.getFingerprint()));
            check("same seed is cell-identical " + SEEDS[index],
                differences(maps[index], repeated) == 0);
        }
        for(int index = 1; index < maps.length; index++) {
            int difference = differences(maps[0], maps[index]);
            check("procedural seed has visible logical difference " + SEEDS[index],
                difference >= maps[0].getDepth() * maps[0].getWidth() / 5);
        }
        System.out.println("TerrainProceduralPreviewTests passed " + checks + " checks");
    }

    private static void verifyLegacyIsUnchanged()
    {
        TerrainMap legacy = new TerrainMap(80, 120, TerrainMap.DEFAULT_SEED);
        check("legacy profile remains default", legacy.getGenerationProfile() ==
            TerrainGenerationProfile.LEGACY_V1);
        check("legacy version remains exact", TerrainMap.GENERATOR_VERSION.equals(
            legacy.getGeneratorVersion()));
        check("legacy default fingerprint remains frozen",
            "bd258ccf51259bb7350942b8bfb9c3e1ad9c022f7dbad5009b9d4a4ca6dd775b".equals(
                legacy.getFingerprint()));
    }

    private static void verifyMap(TerrainMap map)
    {
        check("procedural profile", map.getGenerationProfile() ==
            TerrainGenerationProfile.PROCEDURAL_V2);
        check("procedural version", "PROCEDURAL_V2".equals(map.getGeneratorVersion()));
        Map<TerrainType, Integer> counts = map.countTerrainTypes();
        int cells = map.getDepth() * map.getWidth();
        for(TerrainType type : TerrainType.values()) {
            check("procedural map includes " + type, counts.get(type) > 0);
        }
        check("procedural water stays below four percent",
            counts.get(TerrainType.WATERHOLE) * 100 < cells * 4);
        check("procedural map keeps two separated water bodies",
            componentCount(map, TerrainType.WATERHOLE) == 2);
        check("passable land remains connected", componentCountNot(map,
            TerrainType.WATERHOLE) == 1);
        check("every passable cell can reach a shore", allLandReachesShore(map));
    }

    private static int differences(TerrainMap first, TerrainMap second)
    {
        int result = 0;
        for(int row = 0; row < first.getDepth(); row++) {
            for(int col = 0; col < first.getWidth(); col++) {
                if(first.getTerrainAt(new Location(row, col)) !=
                   second.getTerrainAt(new Location(row, col))) result++;
            }
        }
        return result;
    }

    private static int componentCount(TerrainMap map, TerrainType target)
    {
        return componentCount(map, target, true);
    }

    private static int componentCountNot(TerrainMap map, TerrainType target)
    {
        return componentCount(map, target, false);
    }

    private static int componentCount(TerrainMap map, TerrainType target, boolean equal)
    {
        boolean[][] seen = new boolean[map.getDepth()][map.getWidth()];
        int count = 0;
        for(int row = 0; row < map.getDepth(); row++) {
            for(int col = 0; col < map.getWidth(); col++) {
                if(seen[row][col] || matches(map, row, col, target) != equal) continue;
                count++;
                flood(map, row, col, target, equal, seen);
            }
        }
        return count;
    }

    private static boolean allLandReachesShore(TerrainMap map)
    {
        boolean[][] reached = new boolean[map.getDepth()][map.getWidth()];
        ArrayDeque<Location> queue = new ArrayDeque<Location>();
        for(int row = 0; row < map.getDepth(); row++) {
            for(int col = 0; col < map.getWidth(); col++) {
                Location location = new Location(row, col);
                if(map.isPassable(location) && isShore(map, row, col)) {
                    reached[row][col] = true;
                    queue.addLast(location);
                }
            }
        }
        while(!queue.isEmpty()) {
            Location point = queue.removeFirst();
            for(int[] move : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                int row = point.row() + move[0];
                int col = point.col() + move[1];
                if(row < 0 || row >= map.getDepth() || col < 0 || col >= map.getWidth() ||
                   reached[row][col] || !map.isPassable(new Location(row, col))) continue;
                reached[row][col] = true;
                queue.addLast(new Location(row, col));
            }
        }
        for(int row = 0; row < map.getDepth(); row++) {
            for(int col = 0; col < map.getWidth(); col++) {
                if(map.isPassable(new Location(row, col)) && !reached[row][col]) return false;
            }
        }
        return !queue.isEmpty() || hasAnyShore(map);
    }

    private static boolean hasAnyShore(TerrainMap map)
    {
        for(int row = 0; row < map.getDepth(); row++) {
            for(int col = 0; col < map.getWidth(); col++) {
                if(isShore(map, row, col)) return true;
            }
        }
        return false;
    }

    private static boolean isShore(TerrainMap map, int row, int col)
    {
        for(int[] move : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            int nextRow = row + move[0];
            int nextCol = col + move[1];
            if(nextRow >= 0 && nextRow < map.getDepth() && nextCol >= 0 &&
               nextCol < map.getWidth() &&
               map.getTerrainAt(new Location(nextRow, nextCol)) == TerrainType.WATERHOLE) {
                return true;
            }
        }
        return false;
    }

    private static void flood(TerrainMap map, int row, int col, TerrainType target,
                              boolean equal, boolean[][] seen)
    {
        ArrayDeque<Location> queue = new ArrayDeque<Location>();
        queue.addLast(new Location(row, col));
        seen[row][col] = true;
        while(!queue.isEmpty()) {
            Location point = queue.removeFirst();
            for(int[] move : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                int nextRow = point.row() + move[0];
                int nextCol = point.col() + move[1];
                if(nextRow < 0 || nextRow >= map.getDepth() || nextCol < 0 ||
                   nextCol >= map.getWidth() || seen[nextRow][nextCol] ||
                   matches(map, nextRow, nextCol, target) != equal) continue;
                seen[nextRow][nextCol] = true;
                queue.addLast(new Location(nextRow, nextCol));
            }
        }
    }

    private static boolean matches(TerrainMap map, int row, int col, TerrainType target)
    {
        return map.getTerrainAt(new Location(row, col)) == target;
    }

    private static void check(String label, boolean condition)
    {
        checks++;
        if(!condition) throw new AssertionError(label);
    }
}
