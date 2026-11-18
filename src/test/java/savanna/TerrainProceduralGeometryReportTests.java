package savanna;

/** Deterministic zero-step checks for the fixed TV3 development geometry audit. */
public final class TerrainProceduralGeometryReportTests
{
    private static int checks;

    private TerrainProceduralGeometryReportTests()
    {
    }

    public static void main(String[] args)
    {
        long[] seeds = TerrainProceduralGeometryReport.defaultSeeds();
        check("TV3 audit uses exactly one hundred seeds",
            seeds.length == TerrainProceduralGeometryReport.DEFAULT_SAMPLE_SIZE);
        check("TV3 audit begins with the visual review seed", seeds[0] == 4201L);
        check("TV3 audit preserves its explicit final seed", seeds[seeds.length - 1] == 788182L);

        TerrainProceduralGeometryReport.Report first =
            TerrainProceduralGeometryReport.collectDefault();
        check("TV3 audit keeps the documented dimensions",
            first.getDepth() == 80 && first.getWidth() == 120);
        check("TV3 audit measures every configured seed", first.getMaps().size() == 100);
        check("TV3 audit expands every unordered pair",
            first.getPairs().size() == 4950);
        check("TV3 audit records no preview-geometry failures",
            first.constraintsPassed());
        check("TV3 audit has no terrain fingerprint collision",
            first.distinctFingerprintCount() == 100);
        for(TerrainProceduralGeometryReport.MapMetrics map : first.getMaps()) {
            check("TV3 audit keeps two water components for seed " + map.getSeed(),
                map.getWaterComponents() == 2);
            check("TV3 audit connects passable land to shore for seed " + map.getSeed(),
                map.isAllPassableReachShore());
        }
        for(TerrainProceduralGeometryReport.PairMetrics pair : first.getPairs()) {
            check("TV3 audit pair differs " + pair.getFirstSeed() + "/" +
                pair.getSecondSeed(), pair.getDifferingCells() > 0);
        }
        check("TV3 audit seed CSV has header plus one hundred rows",
            nonEmptyLineCount(first.perSeedCsv()) == 101);
        check("TV3 audit pair CSV has header plus every pair",
            nonEmptyLineCount(first.pairwiseCsv()) == 4951);
        check("TV3 audit summary states its non-qualification boundary",
            first.summaryMarkdown().contains("not a T1 preregistration"));

        TerrainProceduralGeometryReport.Report repeated =
            TerrainProceduralGeometryReport.collectDefault();
        check("TV3 audit CSV is deterministic", first.perSeedCsv().equals(repeated.perSeedCsv()));
        check("TV3 audit pair table is deterministic",
            first.pairwiseCsv().equals(repeated.pairwiseCsv()));
        check("TV3 audit summary is deterministic",
            first.summaryMarkdown().equals(repeated.summaryMarkdown()));
        System.out.println("TerrainProceduralGeometryReportTests passed " + checks + " checks");
    }

    private static int nonEmptyLineCount(String text)
    {
        int result = 0;
        for(String line : text.split("\\n")) {
            if(!line.isEmpty()) result++;
        }
        return result;
    }

    private static void check(String label, boolean condition)
    {
        checks++;
        if(!condition) throw new AssertionError(label);
    }
}
