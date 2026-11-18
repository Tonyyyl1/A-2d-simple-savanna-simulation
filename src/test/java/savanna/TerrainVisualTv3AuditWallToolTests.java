package savanna;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Deterministic, zero-step checks for the full TV3 visual audit wall. */
public final class TerrainVisualTv3AuditWallToolTests
{
    private static int checks;

    private TerrainVisualTv3AuditWallToolTests()
    {
    }

    public static void main(String[] args) throws Exception
    {
        run();
        System.out.println("TerrainVisualTv3AuditWallToolTests passed " + checks +
            " checks");
    }

    static void run() throws IOException
    {
        TerrainProceduralGeometryReport.Report report =
            TerrainProceduralGeometryReport.collectDefault();
        TerrainBackgroundCache cache = new TerrainBackgroundCache();
        TerrainVisualTv3AuditWallTool.AuditWall wall =
            TerrainVisualTv3AuditWallTool.create(report, cache);

        check("TV3 visual wall includes every fixed audit seed",
            wall.getMapCount() == TerrainProceduralGeometryReport.DEFAULT_SAMPLE_SIZE);
        check("TV3 visual wall uses ten rows for its ten columns",
            wall.getRowCount() == 10);
        check("TV3 visual wall has the declared contact dimensions",
            wall.getImage().getWidth() == TerrainVisualTv3AuditWallTool.COLUMNS *
                TerrainVisualTv3AuditWallTool.MAP_WIDTH &&
            wall.getImage().getHeight() == 10 * (TerrainVisualTv3AuditWallTool.MAP_HEIGHT +
                TerrainVisualTv3AuditWallTool.LABEL_HEIGHT));
        check("TV3 visual wall manifest records the full fixed sample",
            wall.getManifest().contains("full fixed development-audit set: `100` seeds"));
        check("TV3 visual wall manifest records the zero-step boundary",
            wall.getManifest().contains("ecology steps: `0`"));
        check("TV3 visual wall manifest keeps the first fixed seed",
            wall.getManifest().contains("|0|4201|"));
        check("TV3 visual wall manifest keeps the final fixed seed",
            wall.getManifest().contains("|99|788182|"));
        check("TV3 visual wall manifest lists all one hundred maps",
            tableRows(wall.getManifest()) == 100);
        check("TV3 visual wall preserves the bounded background cache",
            cache.entryCount() == 12);

        Path temporary = Files.createTempDirectory("savanna-tv3-visual-wall-");
        try {
            List<Path> files = TerrainVisualTv3AuditWallTool.write(wall, temporary);
            check("TV3 visual wall writes image and manifest", files.size() == 2 &&
                Files.isRegularFile(files.get(0)) && Files.isRegularFile(files.get(1)));
            boolean rejected = false;
            try {
                TerrainVisualTv3AuditWallTool.write(wall, temporary);
            }
            catch(IOException expected) {
                rejected = true;
            }
            check("TV3 visual wall refuses to overwrite an audit artifact", rejected);
        }
        finally {
            Files.deleteIfExists(temporary.resolve(
                TerrainVisualTv3AuditWallTool.WALL_FILE_NAME));
            Files.deleteIfExists(temporary.resolve(
                TerrainVisualTv3AuditWallTool.MANIFEST_FILE_NAME));
            Files.deleteIfExists(temporary);
        }
    }

    private static int tableRows(String manifest)
    {
        int result = 0;
        for(String line : manifest.split("\\n")) {
            if(line.matches("\\|[0-9]+\\|.*")) result++;
        }
        return result;
    }

    private static void check(String label, boolean condition)
    {
        checks++;
        if(!condition) throw new AssertionError(label);
    }
}
