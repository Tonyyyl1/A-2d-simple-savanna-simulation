package savanna;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Writes a full, deterministic contact wall for the fixed TV3 development
 * geometry-audit set.  This is visual-review evidence only: it never creates
 * a simulation context or runs an ecology step.
 */
public final class TerrainVisualTv3AuditWallTool
{
    static final String ARTIFACT_VERSION = "TV3-VISUAL-AUDIT-WALL-1";
    static final String WALL_FILE_NAME = "terrain-tv3-procedural-v2-100-seed-wall.png";
    static final String MANIFEST_FILE_NAME = "terrain-tv3-procedural-v2-100-seed-wall.md";
    static final int COLUMNS = 10;
    static final int MAP_WIDTH = 240;
    static final int MAP_HEIGHT = 160;
    static final int LABEL_HEIGHT = 34;

    private TerrainVisualTv3AuditWallTool()
    {
    }

    public static void main(String[] args) throws IOException
    {
        if(args.length > 1) {
            throw new IllegalArgumentException(
                "Usage: TerrainVisualTv3AuditWallTool [output-directory]");
        }
        Path output = Path.of(args.length == 0
            ? "build/terrain-preview-tv3-100-seeds-20260907" : args[0]);
        TerrainProceduralGeometryReport.Report report =
            TerrainProceduralGeometryReport.collectDefault();
        if(!report.constraintsPassed()) {
            throw new IllegalStateException("TV3 geometry audit failed: " +
                report.getReportFailures());
        }
        AuditWall wall = create(report, TerrainBackgroundCache.shared());
        List<Path> files = write(wall, output);
        System.out.println("TV3_VISUAL_AUDIT_WALL version=" + ARTIFACT_VERSION +
            " profile=" + TerrainGenerationProfile.PROCEDURAL_V2 +
            " seeds=" + wall.getMapCount() + " layout=" + COLUMNS + "x" +
            wall.getRowCount() + " pixels=" + wall.getImage().getWidth() + "x" +
            wall.getImage().getHeight() + " ecologySteps=0");
        for(Path file : files) {
            System.out.println("TV3_VISUAL_AUDIT_WALL_FILE " + file);
        }
    }

    static AuditWall createDefault(TerrainBackgroundCache cache)
    {
        return create(TerrainProceduralGeometryReport.collectDefault(), cache);
    }

    static AuditWall create(TerrainProceduralGeometryReport.Report report,
                            TerrainBackgroundCache cache)
    {
        if(report == null || cache == null) {
            throw new IllegalArgumentException("A geometry report and cache are required.");
        }
        if(!report.constraintsPassed()) {
            throw new IllegalArgumentException("The visual wall requires a passing geometry report.");
        }
        List<TerrainProceduralGeometryReport.MapMetrics> maps = report.getMaps();
        if(maps.isEmpty()) {
            throw new IllegalArgumentException("The visual wall requires at least one map.");
        }

        int rows = (maps.size() + COLUMNS - 1) / COLUMNS;
        BufferedImage image = new BufferedImage(COLUMNS * MAP_WIDTH,
            rows * (MAP_HEIGHT + LABEL_HEIGHT), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(29, 34, 29));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            for(int index = 0; index < maps.size(); index++) {
                TerrainProceduralGeometryReport.MapMetrics metrics = maps.get(index);
                TerrainMap terrain = TerrainMap.proceduralPreview(report.getDepth(),
                    report.getWidth(), metrics.getSeed());
                if(!terrain.getFingerprint().equals(metrics.getFingerprint())) {
                    throw new IllegalStateException("Preview fingerprint drift for seed " +
                        metrics.getSeed());
                }
                int left = index % COLUMNS * MAP_WIDTH;
                int top = index / COLUMNS * (MAP_HEIGHT + LABEL_HEIGHT);
                graphics.drawImage(cache.imageFor(terrain, MAP_WIDTH, MAP_HEIGHT),
                    left, top, null);
                label(graphics, metrics, index, left, top + MAP_HEIGHT);
            }
        }
        finally {
            graphics.dispose();
        }
        return new AuditWall(image, manifest(report), maps.size(), rows);
    }

    static List<Path> write(AuditWall wall, Path outputDirectory) throws IOException
    {
        if(wall == null || outputDirectory == null) {
            throw new IllegalArgumentException("A visual wall and output directory are required.");
        }
        Files.createDirectories(outputDirectory);
        Path imageFile = outputDirectory.resolve(WALL_FILE_NAME);
        Path manifestFile = outputDirectory.resolve(MANIFEST_FILE_NAME);
        if(Files.exists(imageFile) || Files.exists(manifestFile)) {
            throw new IOException("Refusing to overwrite TV3 visual audit wall in " +
                outputDirectory);
        }
        try(OutputStream output = Files.newOutputStream(imageFile,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            if(!ImageIO.write(wall.getImage(), "png", output)) {
                throw new IOException("PNG writer unavailable for " + imageFile);
            }
        }
        Files.writeString(manifestFile, wall.getManifest(), StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        List<Path> files = new ArrayList<Path>();
        files.add(imageFile);
        files.add(manifestFile);
        return Collections.unmodifiableList(files);
    }

    private static void label(Graphics2D graphics,
                              TerrainProceduralGeometryReport.MapMetrics metrics,
                              int index, int left, int top)
    {
        graphics.setColor(new Color(25, 29, 24));
        graphics.fillRect(left, top, MAP_WIDTH, LABEL_HEIGHT);
        graphics.setColor(new Color(235, 242, 224));
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        graphics.drawString("#" + String.format(Locale.ROOT, "%02d", index) +
            " seed=" + metrics.getSeed(), left + 5, top + 13);
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        graphics.drawString("water=" + metrics.getTerrainCount(TerrainType.WATERHOLE) +
            " shore=" + metrics.getShoreCells() +
            " max=" + metrics.getMaxShoreDistance(), left + 5, top + 28);
    }

    private static String manifest(TerrainProceduralGeometryReport.Report report)
    {
        StringBuilder result = new StringBuilder();
        result.append("# TV3 PROCEDURAL_V2 100-seed visual audit wall\n\n");
        result.append("- artifactVersion: `").append(ARTIFACT_VERSION).append("`\n");
        result.append("- sourceReportVersion: `")
            .append(TerrainProceduralGeometryReport.REPORT_VERSION).append("`\n");
        result.append("- profile / generator: `PROCEDURAL_V2`\n");
        result.append("- dimensions: `").append(report.getDepth()).append('x')
            .append(report.getWidth()).append("`\n");
        result.append("- full fixed development-audit set: `")
            .append(report.getMaps().size()).append("` seeds; no selected subset\n");
        result.append("- contact layout: `").append(COLUMNS).append('x')
            .append((report.getMaps().size() + COLUMNS - 1) / COLUMNS).append("`\n");
        result.append("- ecology steps: `0`; no SimulationContext or ecology RNG was used.\n");
        result.append("- qualification status: development visual-review artifact only; not a T1 preregistration, TV4 matrix, or ecology gate.\n\n");
        result.append("| index | seed | fingerprint | water cells | shore cells | max shore distance | geometry |\n");
        result.append("|---:|---:|---|---:|---:|---:|---|\n");
        List<TerrainProceduralGeometryReport.MapMetrics> maps = report.getMaps();
        for(int index = 0; index < maps.size(); index++) {
            TerrainProceduralGeometryReport.MapMetrics metrics = maps.get(index);
            result.append('|').append(index).append('|').append(metrics.getSeed()).append('|')
                .append(metrics.getFingerprint()).append('|')
                .append(metrics.getTerrainCount(TerrainType.WATERHOLE)).append('|')
                .append(metrics.getShoreCells()).append('|')
                .append(metrics.getMaxShoreDistance()).append('|')
                .append(metrics.geometryStatus()).append("|\n");
        }
        return result.toString();
    }

    static final class AuditWall
    {
        private final BufferedImage image;
        private final String manifest;
        private final int mapCount;
        private final int rowCount;

        private AuditWall(BufferedImage image, String manifest, int mapCount,
                          int rowCount)
        {
            this.image = image;
            this.manifest = manifest;
            this.mapCount = mapCount;
            this.rowCount = rowCount;
        }

        BufferedImage getImage() { return image; }
        String getManifest() { return manifest; }
        int getMapCount() { return mapCount; }
        int getRowCount() { return rowCount; }
    }
}
