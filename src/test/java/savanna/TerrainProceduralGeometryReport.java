package savanna;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic, zero-step geometry audit for the preview-only
 * {@link TerrainGenerationProfile#PROCEDURAL_V2} map generator.
 *
 * <p>The audit set is intentionally a named development sample, not a
 * retrospective T1 preregistration or an ecology qualification matrix.  It
 * creates {@link TerrainMap#proceduralPreview(int, int, long)} maps only and
 * never constructs a {@code SimulationContext} or reads the ecology RNG.</p>
 */
final class TerrainProceduralGeometryReport
{
    static final String REPORT_VERSION = "TV3-PROCEDURAL-GEOMETRY-1";
    static final int DEFAULT_DEPTH = 80;
    static final int DEFAULT_WIDTH = 120;
    static final int DEFAULT_SAMPLE_SIZE = 100;
    static final long DEFAULT_SEED_START = 4201L;
    static final long DEFAULT_SEED_INCREMENT = 7919L;
    private static final int[][] CARDINAL_DIRECTIONS = {
        {-1, 0}, {1, 0}, {0, -1}, {0, 1}
    };
    private static final TerrainType[] CSV_TERRAIN_ORDER = {
        TerrainType.GRASSLAND,
        TerrainType.OPEN_PLAIN,
        TerrainType.DRY_SOIL,
        TerrainType.BUSH,
        TerrainType.WATERHOLE
    };

    private TerrainProceduralGeometryReport()
    {
    }

    static long[] defaultSeeds()
    {
        long[] result = new long[DEFAULT_SAMPLE_SIZE];
        for(int index = 0; index < result.length; index++) {
            result[index] = DEFAULT_SEED_START + DEFAULT_SEED_INCREMENT * index;
        }
        return result;
    }

    static Report collectDefault()
    {
        return collect(DEFAULT_DEPTH, DEFAULT_WIDTH, defaultSeeds());
    }

    static Report collect(int depth, int width, long[] seeds)
    {
        if(depth <= 0 || width <= 0) {
            throw new IllegalArgumentException("Terrain dimensions must be positive.");
        }
        if(seeds == null || seeds.length == 0) {
            throw new IllegalArgumentException("At least one terrain seed is required.");
        }
        long[] frozenSeeds = seeds.clone();
        List<TerrainMap> maps = new ArrayList<TerrainMap>();
        List<MapMetrics> metrics = new ArrayList<MapMetrics>();
        for(long seed : frozenSeeds) {
            TerrainMap map = TerrainMap.proceduralPreview(depth, width, seed);
            maps.add(map);
            metrics.add(measure(map));
        }

        List<PairMetrics> pairs = new ArrayList<PairMetrics>();
        for(int first = 0; first < maps.size(); first++) {
            for(int second = first + 1; second < maps.size(); second++) {
                pairs.add(new PairMetrics(maps.get(first).getSeed(),
                    maps.get(second).getSeed(), logicalDifferences(maps.get(first),
                        maps.get(second)), depth * width));
            }
        }
        return new Report(depth, width, frozenSeeds, metrics, pairs);
    }

    static List<Path> write(Report report, Path outputDirectory) throws IOException
    {
        if(report == null || outputDirectory == null) {
            throw new IllegalArgumentException("Report and output directory are required.");
        }
        Files.createDirectories(outputDirectory);
        List<Path> files = Arrays.asList(
            outputDirectory.resolve("terrain-tv3-procedural-v2-geometry-summary.md"),
            outputDirectory.resolve("terrain-tv3-procedural-v2-geometry.csv"),
            outputDirectory.resolve("terrain-tv3-procedural-v2-pairwise.csv"),
            outputDirectory.resolve("terrain-tv3-procedural-v2-seeds.txt"));
        for(Path file : files) {
            if(Files.exists(file)) {
                throw new IOException("Refusing to overwrite existing geometry report: " + file);
            }
        }
        Files.writeString(files.get(0), report.summaryMarkdown(), StandardCharsets.UTF_8);
        Files.writeString(files.get(1), report.perSeedCsv(), StandardCharsets.UTF_8);
        Files.writeString(files.get(2), report.pairwiseCsv(), StandardCharsets.UTF_8);
        Files.writeString(files.get(3), report.seedManifest(), StandardCharsets.UTF_8);
        return Collections.unmodifiableList(files);
    }

    private static MapMetrics measure(TerrainMap map)
    {
        int cells = map.getDepth() * map.getWidth();
        int[] counts = new int[TerrainType.values().length];
        int waterRowTotal = 0;
        int waterColTotal = 0;
        for(int row = 0; row < map.getDepth(); row++) {
            for(int col = 0; col < map.getWidth(); col++) {
                TerrainType type = map.getTerrainAt(new Location(row, col));
                counts[type.ordinal()]++;
                if(type == TerrainType.WATERHOLE) {
                    waterRowTotal += row;
                    waterColTotal += col;
                }
            }
        }
        Components water = components(map, true);
        Components land = components(map, false);
        ShoreMetrics shore = shoreMetrics(map);
        List<String> failures = new ArrayList<String>();
        if(map.getGenerationProfile() != TerrainGenerationProfile.PROCEDURAL_V2) {
            failures.add("profile");
        }
        if(!TerrainGenerationProfile.PROCEDURAL_V2.getGeneratorVersion().equals(
            map.getGeneratorVersion())) {
            failures.add("generatorVersion");
        }
        for(TerrainType type : TerrainType.values()) {
            if(counts[type.ordinal()] == 0) failures.add("missing-" + type.name());
        }
        if(water.count != 2) failures.add("waterComponents=" + water.count);
        if(counts[TerrainType.WATERHOLE.ordinal()] * 100 >= cells * 4) {
            failures.add("waterAtOrAboveFourPercent");
        }
        if(land.count != 1) failures.add("landComponents=" + land.count);
        if(!shore.allPassableReachShore) failures.add("unreachablePassableLand");

        int waterCells = counts[TerrainType.WATERHOLE.ordinal()];
        return new MapMetrics(map.getSeed(), map.getFingerprint(), counts,
            water.count, water.largestSize(), land.count, land.largestSize(),
            shore.shoreCells, shore.maxDistance, shore.allPassableReachShore,
            waterCells == 0 ? 0.0 : (double)waterRowTotal / waterCells,
            waterCells == 0 ? 0.0 : (double)waterColTotal / waterCells,
            water.signature(), failures);
    }

    private static Components components(TerrainMap map, boolean water)
    {
        boolean[][] seen = new boolean[map.getDepth()][map.getWidth()];
        List<Component> result = new ArrayList<Component>();
        for(int row = 0; row < map.getDepth(); row++) {
            for(int col = 0; col < map.getWidth(); col++) {
                if(seen[row][col] || isWater(map, row, col) != water) continue;
                result.add(floodComponent(map, row, col, water, seen));
            }
        }
        result.sort(Comparator.comparingInt(Component::getCells).reversed()
            .thenComparingDouble(Component::getCentroidRow)
            .thenComparingDouble(Component::getCentroidCol));
        return new Components(result);
    }

    private static Component floodComponent(TerrainMap map, int startRow,
                                            int startCol, boolean water,
                                            boolean[][] seen)
    {
        ArrayDeque<Location> queue = new ArrayDeque<Location>();
        queue.addLast(new Location(startRow, startCol));
        seen[startRow][startCol] = true;
        int cells = 0;
        int rowTotal = 0;
        int colTotal = 0;
        while(!queue.isEmpty()) {
            Location point = queue.removeFirst();
            cells++;
            rowTotal += point.row();
            colTotal += point.col();
            for(int[] direction : CARDINAL_DIRECTIONS) {
                int row = point.row() + direction[0];
                int col = point.col() + direction[1];
                if(row < 0 || row >= map.getDepth() || col < 0 ||
                   col >= map.getWidth() || seen[row][col] ||
                   isWater(map, row, col) != water) {
                    continue;
                }
                seen[row][col] = true;
                queue.addLast(new Location(row, col));
            }
        }
        return new Component(cells, (double)rowTotal / cells,
            (double)colTotal / cells);
    }

    private static ShoreMetrics shoreMetrics(TerrainMap map)
    {
        int[][] distance = new int[map.getDepth()][map.getWidth()];
        for(int row = 0; row < distance.length; row++) {
            Arrays.fill(distance[row], -1);
        }
        ArrayDeque<Location> queue = new ArrayDeque<Location>();
        int shoreCells = 0;
        for(int row = 0; row < map.getDepth(); row++) {
            for(int col = 0; col < map.getWidth(); col++) {
                if(!isWater(map, row, col) && isShore(map, row, col)) {
                    distance[row][col] = 0;
                    queue.addLast(new Location(row, col));
                    shoreCells++;
                }
            }
        }
        while(!queue.isEmpty()) {
            Location point = queue.removeFirst();
            for(int[] direction : CARDINAL_DIRECTIONS) {
                int row = point.row() + direction[0];
                int col = point.col() + direction[1];
                if(row < 0 || row >= map.getDepth() || col < 0 ||
                   col >= map.getWidth() || distance[row][col] >= 0 ||
                   isWater(map, row, col)) {
                    continue;
                }
                distance[row][col] = distance[point.row()][point.col()] + 1;
                queue.addLast(new Location(row, col));
            }
        }
        boolean allReached = shoreCells > 0;
        int maxDistance = 0;
        for(int row = 0; row < map.getDepth(); row++) {
            for(int col = 0; col < map.getWidth(); col++) {
                if(!isWater(map, row, col)) {
                    allReached &= distance[row][col] >= 0;
                    maxDistance = Math.max(maxDistance, distance[row][col]);
                }
            }
        }
        return new ShoreMetrics(shoreCells, maxDistance, allReached);
    }

    private static boolean isShore(TerrainMap map, int row, int col)
    {
        for(int[] direction : CARDINAL_DIRECTIONS) {
            int nextRow = row + direction[0];
            int nextCol = col + direction[1];
            if(nextRow >= 0 && nextRow < map.getDepth() && nextCol >= 0 &&
               nextCol < map.getWidth() && isWater(map, nextRow, nextCol)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWater(TerrainMap map, int row, int col)
    {
        return map.getTerrainAt(new Location(row, col)) == TerrainType.WATERHOLE;
    }

    private static int logicalDifferences(TerrainMap first, TerrainMap second)
    {
        int differences = 0;
        for(int row = 0; row < first.getDepth(); row++) {
            for(int col = 0; col < first.getWidth(); col++) {
                if(first.getTerrainAt(new Location(row, col)) !=
                   second.getTerrainAt(new Location(row, col))) {
                    differences++;
                }
            }
        }
        return differences;
    }

    static final class Report
    {
        private final int depth;
        private final int width;
        private final long[] seeds;
        private final List<MapMetrics> maps;
        private final List<PairMetrics> pairs;
        private final List<String> reportFailures;

        private Report(int depth, int width, long[] seeds, List<MapMetrics> maps,
                       List<PairMetrics> pairs)
        {
            this.depth = depth;
            this.width = width;
            this.seeds = seeds.clone();
            this.maps = Collections.unmodifiableList(new ArrayList<MapMetrics>(maps));
            this.pairs = Collections.unmodifiableList(new ArrayList<PairMetrics>(pairs));
            reportFailures = collectReportFailures();
        }

        int getDepth() { return depth; }
        int getWidth() { return width; }
        long[] getSeeds() { return seeds.clone(); }
        List<MapMetrics> getMaps() { return maps; }
        List<PairMetrics> getPairs() { return pairs; }
        List<String> getReportFailures() { return reportFailures; }

        boolean constraintsPassed()
        {
            return reportFailures.isEmpty();
        }

        int distinctFingerprintCount()
        {
            Set<String> fingerprints = new HashSet<String>();
            for(MapMetrics map : maps) fingerprints.add(map.getFingerprint());
            return fingerprints.size();
        }

        String summaryMarkdown()
        {
            Distribution waterCells = distribution(metricWaterCells());
            Distribution shoreCells = distribution(metricShoreCells());
            Distribution maxShoreDistance = distribution(metricMaxShoreDistance());
            Distribution differences = distribution(pairFractions());
            StringBuilder result = new StringBuilder();
            result.append("# TV3 PROCEDURAL_V2 ").append(seeds.length)
                .append("-seed 静态几何报告\n\n");
            result.append("- reportVersion: `").append(REPORT_VERSION).append("`\n");
            result.append("- profile / generator: `PROCEDURAL_V2`\n");
            result.append("- dimensions: `").append(depth).append("x").append(width)
                .append("`\n");
            result.append("- seeds: `").append(seeds.length).append("` development-audit samples, formula `")
                .append(DEFAULT_SEED_START).append(" + index * ")
                .append(DEFAULT_SEED_INCREMENT).append("`\n");
            result.append("- ecology steps: `0`; no SimulationContext or ecology RNG was used.\n");
            result.append("- qualification status: development observation only; this is not a T1 preregistration, a TV4 matrix, or an ecology gate.\n\n");

            result.append("## Generator-contract observations\n\n");
            result.append("- maps passing preview geometry checks: `")
                .append(maps.size() - failingMapCount()).append("/").append(maps.size())
                .append("`\n");
            result.append("- distinct terrain fingerprints: `").append(distinctFingerprintCount())
                .append("/").append(maps.size()).append("`\n");
            result.append("- pairwise comparisons: `").append(pairs.size()).append("`\n");
            result.append("- report failures: `").append(reportFailures.isEmpty() ? "none" :
                join(reportFailures, "; ")).append("`\n\n");

            result.append("## Distribution\n\n");
            result.append("| Metric | Min | Median | Max |\n|---|---:|---:|---:|\n");
            result.append(row("Water cells", waterCells, false));
            result.append(row("Shore cells", shoreCells, false));
            result.append(row("Maximum passable-to-shore distance", maxShoreDistance, false));
            result.append(row("Pairwise logical-grid difference", differences, true));
            result.append("\n");
            result.append("The pairwise values are reported for inspection only. The historical 10%/20% suggestion has not been presented as a frozen pass/fail threshold.\n\n");
            result.append("## Files\n\n");
            result.append("- `terrain-tv3-procedural-v2-geometry.csv`: one row per seed.\n");
            result.append("- `terrain-tv3-procedural-v2-pairwise.csv`: all unordered seed pairs.\n");
            result.append("- `terrain-tv3-procedural-v2-seeds.txt`: exact development-audit seed set.\n");
            return result.toString();
        }

        String perSeedCsv()
        {
            StringBuilder result = new StringBuilder();
            result.append("seed,fingerprint,grassland_cells,open_plain_cells,dry_soil_cells,bush_cells,")
                .append("waterhole_cells,water_percent,water_components,largest_water_component_cells,")
                .append("land_components,largest_land_component_cells,shore_cells,max_shore_distance,")
                .append("all_passable_reach_shore,water_centroid_row,water_centroid_col,")
                .append("water_component_signature,preview_geometry_status\n");
            for(MapMetrics map : maps) {
                result.append(map.getSeed()).append(',').append(map.getFingerprint());
                for(TerrainType type : CSV_TERRAIN_ORDER) {
                    result.append(',').append(map.getTerrainCount(type));
                }
                result.append(',').append(decimal(map.waterFraction(depth * width) * 100.0));
                result.append(',').append(map.getWaterComponents());
                result.append(',').append(map.getLargestWaterComponentCells());
                result.append(',').append(map.getLandComponents());
                result.append(',').append(map.getLargestLandComponentCells());
                result.append(',').append(map.getShoreCells());
                result.append(',').append(map.getMaxShoreDistance());
                result.append(',').append(map.isAllPassableReachShore());
                result.append(',').append(decimal(map.getWaterCentroidRow()));
                result.append(',').append(decimal(map.getWaterCentroidCol()));
                result.append(',').append(csv(map.getWaterComponentSignature()));
                result.append(',').append(csv(map.geometryStatus())).append('\n');
            }
            return result.toString();
        }

        String pairwiseCsv()
        {
            StringBuilder result = new StringBuilder();
            result.append("seed_a,seed_b,differing_cells,difference_fraction,difference_percent\n");
            for(PairMetrics pair : pairs) {
                result.append(pair.getFirstSeed()).append(',').append(pair.getSecondSeed())
                    .append(',').append(pair.getDifferingCells()).append(',')
                    .append(decimal(pair.getDifferenceFraction())).append(',')
                    .append(decimal(pair.getDifferenceFraction() * 100.0)).append('\n');
            }
            return result.toString();
        }

        String seedManifest()
        {
            StringBuilder result = new StringBuilder();
            result.append("reportVersion=").append(REPORT_VERSION).append('\n');
            result.append("profile=PROCEDURAL_V2\n");
            result.append("dimensions=").append(depth).append('x').append(width).append('\n');
            result.append("samplePurpose=development-audit-not-preregistration\n");
            result.append("seedFormula=").append(DEFAULT_SEED_START).append(" + index * ")
                .append(DEFAULT_SEED_INCREMENT).append('\n');
            result.append("seedCount=").append(seeds.length).append('\n');
            for(int index = 0; index < seeds.length; index++) {
                result.append(index).append('=').append(seeds[index]).append('\n');
            }
            return result.toString();
        }

        private List<String> collectReportFailures()
        {
            List<String> result = new ArrayList<String>();
            for(MapMetrics map : maps) {
                if(!map.getGeometryFailures().isEmpty()) {
                    result.add("seed " + map.getSeed() + ": " +
                        join(map.getGeometryFailures(), "|"));
                }
            }
            if(distinctFingerprintCount() != maps.size()) {
                result.add("fingerprintCollision");
            }
            for(PairMetrics pair : pairs) {
                if(pair.getDifferingCells() == 0) {
                    result.add("zeroLogicalDifference=" + pair.getFirstSeed() + "/" +
                        pair.getSecondSeed());
                }
            }
            return Collections.unmodifiableList(result);
        }

        private int failingMapCount()
        {
            int result = 0;
            for(MapMetrics map : maps) {
                if(!map.getGeometryFailures().isEmpty()) result++;
            }
            return result;
        }

        private double[] metricWaterCells()
        {
            double[] result = new double[maps.size()];
            for(int index = 0; index < maps.size(); index++) {
                result[index] = maps.get(index).getTerrainCount(TerrainType.WATERHOLE);
            }
            return result;
        }

        private double[] metricShoreCells()
        {
            double[] result = new double[maps.size()];
            for(int index = 0; index < maps.size(); index++) {
                result[index] = maps.get(index).getShoreCells();
            }
            return result;
        }

        private double[] metricMaxShoreDistance()
        {
            double[] result = new double[maps.size()];
            for(int index = 0; index < maps.size(); index++) {
                result[index] = maps.get(index).getMaxShoreDistance();
            }
            return result;
        }

        private double[] pairFractions()
        {
            double[] result = new double[pairs.size()];
            for(int index = 0; index < pairs.size(); index++) {
                result[index] = pairs.get(index).getDifferenceFraction();
            }
            return result;
        }
    }

    static final class MapMetrics
    {
        private final long seed;
        private final String fingerprint;
        private final int[] terrainCounts;
        private final int waterComponents;
        private final int largestWaterComponentCells;
        private final int landComponents;
        private final int largestLandComponentCells;
        private final int shoreCells;
        private final int maxShoreDistance;
        private final boolean allPassableReachShore;
        private final double waterCentroidRow;
        private final double waterCentroidCol;
        private final String waterComponentSignature;
        private final List<String> geometryFailures;

        private MapMetrics(long seed, String fingerprint, int[] terrainCounts,
                           int waterComponents, int largestWaterComponentCells,
                           int landComponents, int largestLandComponentCells,
                           int shoreCells, int maxShoreDistance,
                           boolean allPassableReachShore, double waterCentroidRow,
                           double waterCentroidCol, String waterComponentSignature,
                           List<String> geometryFailures)
        {
            this.seed = seed;
            this.fingerprint = fingerprint;
            this.terrainCounts = terrainCounts.clone();
            this.waterComponents = waterComponents;
            this.largestWaterComponentCells = largestWaterComponentCells;
            this.landComponents = landComponents;
            this.largestLandComponentCells = largestLandComponentCells;
            this.shoreCells = shoreCells;
            this.maxShoreDistance = maxShoreDistance;
            this.allPassableReachShore = allPassableReachShore;
            this.waterCentroidRow = waterCentroidRow;
            this.waterCentroidCol = waterCentroidCol;
            this.waterComponentSignature = waterComponentSignature;
            this.geometryFailures = Collections.unmodifiableList(
                new ArrayList<String>(geometryFailures));
        }

        long getSeed() { return seed; }
        String getFingerprint() { return fingerprint; }
        int getTerrainCount(TerrainType type) { return terrainCounts[type.ordinal()]; }
        int getWaterComponents() { return waterComponents; }
        int getLargestWaterComponentCells() { return largestWaterComponentCells; }
        int getLandComponents() { return landComponents; }
        int getLargestLandComponentCells() { return largestLandComponentCells; }
        int getShoreCells() { return shoreCells; }
        int getMaxShoreDistance() { return maxShoreDistance; }
        boolean isAllPassableReachShore() { return allPassableReachShore; }
        double getWaterCentroidRow() { return waterCentroidRow; }
        double getWaterCentroidCol() { return waterCentroidCol; }
        String getWaterComponentSignature() { return waterComponentSignature; }
        List<String> getGeometryFailures() { return geometryFailures; }

        double waterFraction(int allCells)
        {
            return (double)getTerrainCount(TerrainType.WATERHOLE) / allCells;
        }

        String geometryStatus()
        {
            return geometryFailures.isEmpty() ? "PASS" : join(geometryFailures, "|");
        }
    }

    static final class PairMetrics
    {
        private final long firstSeed;
        private final long secondSeed;
        private final int differingCells;
        private final int allCells;

        private PairMetrics(long firstSeed, long secondSeed, int differingCells,
                            int allCells)
        {
            this.firstSeed = firstSeed;
            this.secondSeed = secondSeed;
            this.differingCells = differingCells;
            this.allCells = allCells;
        }

        long getFirstSeed() { return firstSeed; }
        long getSecondSeed() { return secondSeed; }
        int getDifferingCells() { return differingCells; }
        double getDifferenceFraction() { return (double)differentCells() / allCells; }

        private int differentCells()
        {
            return differingCells;
        }
    }

    private static final class Components
    {
        private final List<Component> values;
        private final int count;

        private Components(List<Component> values)
        {
            this.values = Collections.unmodifiableList(new ArrayList<Component>(values));
            count = values.size();
        }

        private int largestSize()
        {
            return values.isEmpty() ? 0 : values.get(0).getCells();
        }

        private String signature()
        {
            StringBuilder result = new StringBuilder();
            for(int index = 0; index < values.size(); index++) {
                if(index > 0) result.append('|');
                Component component = values.get(index);
                result.append(component.getCells()).append('@')
                    .append(decimal(component.getCentroidRow())).append('/')
                    .append(decimal(component.getCentroidCol()));
            }
            return result.toString();
        }
    }

    private static final class Component
    {
        private final int cells;
        private final double centroidRow;
        private final double centroidCol;

        private Component(int cells, double centroidRow, double centroidCol)
        {
            this.cells = cells;
            this.centroidRow = centroidRow;
            this.centroidCol = centroidCol;
        }

        private int getCells() { return cells; }
        private double getCentroidRow() { return centroidRow; }
        private double getCentroidCol() { return centroidCol; }
    }

    private static final class ShoreMetrics
    {
        private final int shoreCells;
        private final int maxDistance;
        private final boolean allPassableReachShore;

        private ShoreMetrics(int shoreCells, int maxDistance,
                             boolean allPassableReachShore)
        {
            this.shoreCells = shoreCells;
            this.maxDistance = maxDistance;
            this.allPassableReachShore = allPassableReachShore;
        }
    }

    private static final class Distribution
    {
        private final double min;
        private final double median;
        private final double max;

        private Distribution(double min, double median, double max)
        {
            this.min = min;
            this.median = median;
            this.max = max;
        }
    }

    private static Distribution distribution(double[] values)
    {
        if(values == null || values.length == 0) return new Distribution(0.0, 0.0, 0.0);
        double[] ordered = values.clone();
        Arrays.sort(ordered);
        int middle = ordered.length / 2;
        double median = ordered.length % 2 == 0
            ? (ordered[middle - 1] + ordered[middle]) / 2.0 : ordered[middle];
        return new Distribution(ordered[0], median, ordered[ordered.length - 1]);
    }

    private static String row(String label, Distribution distribution,
                              boolean percentage)
    {
        return "| " + label + " | " + display(distribution.min, percentage) + " | " +
            display(distribution.median, percentage) + " | " +
            display(distribution.max, percentage) + " |\n";
    }

    private static String display(double value, boolean percentage)
    {
        return percentage ? decimal(value * 100.0) + "%" : decimal(value);
    }

    private static String decimal(double value)
    {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String csv(String value)
    {
        if(value == null) return "";
        if(value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String join(List<String> values, String separator)
    {
        StringBuilder result = new StringBuilder();
        for(int index = 0; index < values.size(); index++) {
            if(index > 0) result.append(separator);
            result.append(values.get(index));
        }
        return result.toString();
    }
}
