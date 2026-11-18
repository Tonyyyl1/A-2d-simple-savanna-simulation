package savanna;

/** v3.2 V3-1: relief invariants R1-R4 on the default map, procedural maps and many seeds. */
public final class TerrainReliefTests
{
    private TerrainReliefTests()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("V3-1 terrain relief");
        run();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    public static void run()
    {
        TestSupport.startGroup("V3-1 terrain relief");
        check("default LEGACY_V1 map", new TerrainMap(240, 360, 20260629L, TerrainGenerationProfile.LEGACY_V1), 20260629L);
        for(long seed : new long[] {4201L, 9317L, 17689L}) {
            check("PROCEDURAL_V2 terrain seed " + seed,
                new TerrainMap(240, 360, seed, TerrainGenerationProfile.PROCEDURAL_V2), seed);
        }
        boolean allOk = true;
        for(int index = 0; index < 20; index++) {
            allOk &= quiet(new TerrainMap(120, 180, 1000L + index * 37L, TerrainGenerationProfile.LEGACY_V1),
                1000L + index * 37L);
            allOk &= quiet(new TerrainMap(120, 180, 5000L + index * 91L, TerrainGenerationProfile.PROCEDURAL_V2),
                5000L + index * 91L);
        }
        TestSupport.assertTrue("R1-R4 hold on 20 extra seeds of each profile", allOk);
        TestSupport.finishGroup("V3_1_RELIEF");
    }

    private static void check(String label, TerrainMap terrain, long seed)
    {
        TerrainRelief relief = TerrainRelief.generate(terrain, seed);
        String[] failures = violations(terrain, relief, seed);
        TestSupport.assertTrue("V3-1 " + label + ": R1 R2 R3 R4 hold" +
            (failures[0].isEmpty() ? "" : " (" + failures[0] + ")"), failures[0].isEmpty());
        TestSupport.assertTrue("V3-1 " + label + " has at least one water body", relief.getBodyCount() >= 1);
    }

    private static boolean quiet(TerrainMap terrain, long seed)
    {
        return violations(terrain, TerrainRelief.generate(terrain, seed), seed)[0].isEmpty();
    }

    /** One-element array holding a description of the first violation found, or the empty string. */
    private static String[] violations(TerrainMap terrain, TerrainRelief relief, long seed)
    {
        int depth = terrain.getDepth();
        int width = terrain.getWidth();
        // R4: reproducible and bounded.
        TerrainRelief again = TerrainRelief.generate(terrain, seed);
        if(!java.util.Arrays.equals(relief.heights(), again.heights())) return new String[] {"R4 not reproducible"};
        for(float value : relief.heights()) {
            if(value < -10.0f || value > 40.0f || Float.isNaN(value)) return new String[] {"R4 range " + value};
        }
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                boolean land = terrain.isPassableAt(row, col);
                int own = relief.bodyAt(row, col);
                if(land != (own == 0)) return new String[] {"body map disagrees with terrain at " + row + "," + col};
                if(!land) {
                    // R2: water cell centres are well below their level.
                    if(relief.centerHeight(row, col) > relief.getWaterLevel(own) - 0.25f + 1e-4f) {
                        return new String[] {"R2 at " + row + "," + col};
                    }
                    continue;
                }
                // R1: land within 3 cells of water is above the level of the nearest body.
                int nearest = nearestBody(relief, row, col, 3);
                if(nearest > 0 && relief.centerHeight(row, col) < relief.getWaterLevel(nearest) + 0.15f - 1e-4f) {
                    return new String[] {"R1 at " + row + "," + col};
                }
                // R3: steps to 8-neighbour land cells stay dry near water.
                for(int dr = 0; dr <= 1; dr++) {
                    for(int dc = -1; dc <= 1; dc++) {
                        if(dr == 0 && dc <= 0) continue;
                        int nr = row + dr;
                        int nc = col + dc;
                        if(nr < 0 || nr >= depth || nc < 0 || nc >= width || !terrain.isPassableAt(nr, nc)) continue;
                        int body = nearestBody(relief, row, col, TerrainRelief.NEAR_WATER_MARGIN_CELLS);
                        if(body == 0 || nearestBody(relief, nr, nc, TerrainRelief.NEAR_WATER_MARGIN_CELLS) != body) continue;
                        for(int k = 0; k <= 8; k++) {
                            double t = k / 8.0;
                            double h = relief.heightAt(row + 0.5 + dr * t, col + 0.5 + dc * t);
                            if(h < relief.getWaterLevel(body) + 0.05 - 1e-4) {
                                return new String[] {"R3 between " + row + "," + col + " and " + nr + "," + nc + " body " + body + " k " + k + " h " + h + " W " + relief.getWaterLevel(body)};
                            }
                        }
                    }
                }
            }
        }
        return new String[] {""};
    }

    private static int nearestBody(TerrainRelief relief, int row, int col, int radius)
    {
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for(int dr = -radius; dr <= radius; dr++) {
            for(int dc = -radius; dc <= radius; dc++) {
                int r = row + dr;
                int c = col + dc;
                if(r < 0 || r >= relief.getDepth() || c < 0 || c >= relief.getWidth()) continue;
                int id = relief.bodyAt(r, c);
                int distance = Math.max(Math.abs(dr), Math.abs(dc));
                if(id != 0 && distance < bestDistance) {
                    best = id;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }
}
