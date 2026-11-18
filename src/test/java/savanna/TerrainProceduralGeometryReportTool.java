package savanna;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Writes the fixed, zero-step TV3 development geometry audit. */
public final class TerrainProceduralGeometryReportTool
{
    private TerrainProceduralGeometryReportTool()
    {
    }

    public static void main(String[] args) throws IOException
    {
        if(args.length > 1) {
            throw new IllegalArgumentException("Usage: TerrainProceduralGeometryReportTool [output-directory]");
        }
        Path output = Path.of(args.length == 0 ? "build/terrain-geometry-tv3" : args[0]);
        TerrainProceduralGeometryReport.Report report =
            TerrainProceduralGeometryReport.collectDefault();
        List<Path> files = TerrainProceduralGeometryReport.write(report, output);
        System.out.println("TV3_GEOMETRY_REPORT version=" +
            TerrainProceduralGeometryReport.REPORT_VERSION + " profile=" +
            TerrainGenerationProfile.PROCEDURAL_V2 + " seeds=" + report.getMaps().size() +
            " pairs=" + report.getPairs().size() + " constraints=" +
            (report.constraintsPassed() ? "PASS" : "FAIL") + " ecologySteps=0");
        for(Path file : files) {
            System.out.println("TV3_GEOMETRY_REPORT_FILE " + file);
        }
        if(!report.constraintsPassed()) {
            throw new IllegalStateException("TV3 geometry report contains failures: " +
                report.getReportFailures());
        }
    }
}
