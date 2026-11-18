package savanna;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Deterministic, region-based terrain map for the savanna background.
 *
 * The {@code terrain[][]} grid produced by {@link #classify} is the single
 * source of truth. {@link #drawBackground} derives the visual background from
 * that grid: it groups cells into per-type regions and stretches each terrain's
 * 2.5D tile (from {@link TerrainTileSet}) across the matching pixels, then adds
 * region-wide lighting and elevation shadows. The rendered background therefore
 * always matches the logical terrain.
 *
 * Phase 1 uses it for visual structure only; animal logic still runs on Field.
 */
public class TerrainMap
{
    public static final long DEFAULT_SEED = 20260629L;
    public static final String GENERATOR_VERSION = "LEGACY_V1";
    private static final double VISUAL_WATER_BUFFER_CELLS = 0.55;

    /**
     * Terrain elevations, low to high. Higher terrain is painted later (on top)
     * and casts a soft shadow onto lower neighbours, producing the 2.5D relief.
     * The waterhole is the lowest and is rendered as a sunken feature.
     */
    private static final TerrainType[] ELEVATION_LOW_TO_HIGH = {
        TerrainType.WATERHOLE,
        TerrainType.DRY_SOIL,
        TerrainType.OPEN_PLAIN,
        TerrainType.GRASSLAND,
        TerrainType.BUSH
    };

    private final int depth;
    private final int width;
    private final long seed;
    private final TerrainGenerationProfile generationProfile;
    private final ProceduralLayout proceduralLayout;
    private final TerrainType[][] terrain;
    private final TerrainTileSet tileSet;
    private final String fingerprint;
    private final double oasisCenterRow;
    private final double oasisCenterCol;
    private final double oasisRadiusRow;
    private final double oasisRadiusCol;

    private static final int WATER_BODY_COUNT = 2;
    private final double[] waterCenterRows = new double[WATER_BODY_COUNT];
    private final double[] waterCenterCols = new double[WATER_BODY_COUNT];
    private final double[] waterRadiusRows = new double[WATER_BODY_COUNT];
    private final double[] waterRadiusCols = new double[WATER_BODY_COUNT];

    public TerrainMap(int depth, int width)
    {
        this(depth, width, DEFAULT_SEED);
    }

    public TerrainMap(int depth, int width, long seed)
    {
        this(depth, width, seed, TerrainGenerationProfile.LEGACY_V1);
    }

    /** Creates a zero-step TV3 preview map without changing simulation defaults. */
    public static TerrainMap proceduralPreview(int depth, int width, long seed)
    {
        return new TerrainMap(depth, width, seed,
            TerrainGenerationProfile.PROCEDURAL_V2);
    }

    /**
     * Creates a terrain map from an explicit logical-layout profile.  Existing
     * simulation callers use the three-argument constructor and therefore
     * preserve LEGACY_V1 exactly; PROCEDURAL_V2 is preview-only until TV4.
     */
    public TerrainMap(int depth, int width, long seed,
                      TerrainGenerationProfile generationProfile)
    {
        this.depth = Math.max(1, depth);
        this.width = Math.max(1, width);
        this.seed = seed;
        this.generationProfile = generationProfile == null
            ? TerrainGenerationProfile.LEGACY_V1 : generationProfile;
        this.tileSet = new TerrainTileSet();
        terrain = new TerrainType[this.depth][this.width];

        Random random = new Random(seed);
        if(this.generationProfile.isProcedural()) {
            proceduralLayout = ProceduralLayout.create(this.depth, this.width, random);
            oasisCenterRow = proceduralLayout.waterRows[0];
            oasisCenterCol = proceduralLayout.waterCols[0];
            oasisRadiusRow = proceduralLayout.waterRadiusRows[0];
            oasisRadiusCol = proceduralLayout.waterRadiusCols[0];
            for(int index = 0; index < WATER_BODY_COUNT; index++) {
                waterCenterRows[index] = proceduralLayout.waterRows[index];
                waterCenterCols[index] = proceduralLayout.waterCols[index];
                waterRadiusRows[index] = proceduralLayout.waterRadiusRows[index];
                waterRadiusCols[index] = proceduralLayout.waterRadiusCols[index];
            }
        }
        else {
            proceduralLayout = null;
            // Keep this random sequence and every legacy constant byte-for-byte
            // compatible with the former three-argument constructor.
            oasisCenterRow = this.depth * (0.42 + random.nextDouble() * 0.05);
            oasisCenterCol = this.width * (0.32 + random.nextDouble() * 0.04);
            oasisRadiusRow = Math.max(3.0, this.depth * 0.085);
            oasisRadiusCol = Math.max(4.0, this.width * 0.075);
            waterCenterRows[0] = oasisCenterRow;
            waterCenterCols[0] = oasisCenterCol;
            waterCenterRows[1] = this.depth * (0.71 + random.nextDouble() * 0.025);
            waterCenterCols[1] = this.width * (0.72 + random.nextDouble() * 0.025);
            for(int index = 0; index < WATER_BODY_COUNT; index++) {
                waterRadiusRows[index] = Math.max(2.0, this.depth * 0.060);
                waterRadiusCols[index] = Math.max(2.5, this.width * 0.053);
            }
        }

        generate();
        smoothIsolatedCells();
        fingerprint = calculateFingerprint();
    }

    public TerrainType getTerrainAt(Location location)
    {
        if(location == null) {
            return TerrainType.OPEN_PLAIN;
        }
        int row = clamp(location.row(), 0, depth - 1);
        int col = clamp(location.col(), 0, width - 1);
        return terrain[row][col];
    }

    public boolean isPassable(Location location)
    {
        return location != null &&
               getTerrainAt(location) != TerrainType.WATERHOLE;
    }

    private TerrainPassabilityIndex passabilityIndex;

    /** Cross-step passability prefix sums and window cache (terrain is immutable). */
    synchronized TerrainPassabilityIndex passability()
    {
        if(passabilityIndex == null) passabilityIndex = new TerrainPassabilityIndex(this);
        return passabilityIndex;
    }

    /** Package-local fast path for bounded topology queries. */
    boolean isPassableAt(int row, int col)
    {
        return row >= 0 && row < depth && col >= 0 && col < width &&
               terrain[row][col] != TerrainType.WATERHOLE;
    }

    private int[][] waterDistance;

    /**
     * Chebyshev distance in cells from {@code location} to the nearest
     * waterhole cell (0 on water); {@link Integer#MAX_VALUE} when the map has
     * no water. The grid is built once, on first use, from the immutable
     * terrain.
     */
    public synchronized int chebyshevDistanceToWater(Location location)
    {
        if(waterDistance == null) {
            waterDistance = buildWaterDistance();
        }
        int row = clamp(location.row(), 0, depth - 1);
        int col = clamp(location.col(), 0, width - 1);
        return waterDistance[row][col];
    }

    private int[][] buildWaterDistance()
    {
        int[][] distance = new int[depth][width];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                if(terrain[row][col] == TerrainType.WATERHOLE) {
                    distance[row][col] = 0;
                    queue.add(new int[] {row, col});
                }
                else {
                    distance[row][col] = Integer.MAX_VALUE;
                }
            }
        }
        while(!queue.isEmpty()) {
            int[] cell = queue.poll();
            for(int rowStep = -1; rowStep <= 1; rowStep++) {
                for(int colStep = -1; colStep <= 1; colStep++) {
                    int row = cell[0] + rowStep;
                    int col = cell[1] + colStep;
                    if(row < 0 || col < 0 || row >= depth || col >= width ||
                       distance[row][col] != Integer.MAX_VALUE) continue;
                    distance[row][col] = distance[cell[0]][cell[1]] + 1;
                    queue.add(new int[] {row, col});
                }
            }
        }
        return distance;
    }

    public boolean isVisuallyWaterAt(double row, double col)
    {
        return distanceToNearestWaterCell(row, col) <= VISUAL_WATER_BUFFER_CELLS;
    }

    public int getDepth()
    {
        return depth;
    }

    public int getWidth()
    {
        return width;
    }

    public long getSeed()
    {
        return seed;
    }

    public TerrainGenerationProfile getGenerationProfile()
    {
        return generationProfile;
    }

    public String getGeneratorVersion()
    {
        return generationProfile.getGeneratorVersion();
    }

    /** SHA-256 of generator version, dimensions and the row-major logical grid. */
    public String getFingerprint()
    {
        return fingerprint;
    }

    private String calculateFingerprint()
    {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(getGeneratorVersion().getBytes(StandardCharsets.UTF_8));
            digest.update((byte)0);
            digest.update(Integer.toString(depth).getBytes(StandardCharsets.US_ASCII));
            digest.update((byte)'x');
            digest.update(Integer.toString(width).getBytes(StandardCharsets.US_ASCII));
            digest.update((byte)0);
            for(int row = 0; row < depth; row++) {
                for(int col = 0; col < width; col++) {
                    digest.update((byte)terrain[row][col].ordinal());
                }
            }
            return HexEncoding.lowercase(digest.digest());
        }
        catch(NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    public Map<TerrainType, Integer> countTerrainTypes()
    {
        Map<TerrainType, Integer> counts = new EnumMap<>(TerrainType.class);
        for(TerrainType type : TerrainType.values()) {
            counts.put(type, 0);
        }
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                TerrainType type = terrain[row][col];
                counts.put(type, counts.get(type) + 1);
            }
        }
        return counts;
    }

    /**
     * Draw the terrain background by stitching per-type 2.5D tiles over the
     * regions defined by {@code terrain[][]}.
     */
    public void drawBackground(Graphics2D g2, int pixelWidth, int pixelHeight)
    {
        TerrainVisualBackgroundPainter.paint(this, g2, pixelWidth, pixelHeight);
    }

    /**
     * The mature LEGACY_V1 base painter. TV2 calls it before applying only
     * classpath-verified art that is clipped to this map's shared masks.
     */
    void drawLegacyBackground(Graphics2D g2, int pixelWidth, int pixelHeight)
    {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                            RenderingHints.VALUE_RENDER_QUALITY);

        Map<TerrainType, Area> regions = buildRegions(pixelWidth, pixelHeight);

        // Plain base fill prevents hairline seams between rounded region rects.
        g2.setPaint(tileSet.paintFor(TerrainType.OPEN_PLAIN));
        g2.fillRect(0, 0, pixelWidth, pixelHeight);

        int rank = 0;
        for(TerrainType type : ELEVATION_LOW_TO_HIGH) {
            Area region = regions.get(type);
            if(region == null || region.isEmpty()) {
                rank++;
                continue;
            }
            if(type == TerrainType.WATERHOLE) {
                drawWaterhole(g2, region, pixelWidth, pixelHeight);
            }
            else {
                paintElevationShadow(g2, region, rank, pixelWidth);
                paintRegion(g2, region, type);
                paintEdgeHighlight(g2, region);
            }
            rank++;
        }

        drawSoftVignette(g2, pixelWidth, pixelHeight);
        drawTerrainLabels(g2, pixelWidth, pixelHeight);
    }

    /**
     * Background beneath the TV kit. Legacy keeps its historical painter;
     * procedural previews use only grid-derived regions and never inherit the
     * fixed oasis, southeast-pool or corridor artwork.
     */
    void drawBaseBackground(Graphics2D g2, int pixelWidth, int pixelHeight)
    {
        if(!generationProfile.isProcedural()) {
            drawLegacyBackground(g2, pixelWidth, pixelHeight);
            return;
        }
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY);
        Map<TerrainType, Area> regions = buildRegions(pixelWidth, pixelHeight);
        g2.setPaint(tileSet.paintFor(TerrainType.OPEN_PLAIN));
        g2.fillRect(0, 0, pixelWidth, pixelHeight);
        int rank = 0;
        for(TerrainType type : ELEVATION_LOW_TO_HIGH) {
            Area region = regions.get(type);
            if(region != null && !region.isEmpty()) {
                paintElevationShadow(g2, region, rank, pixelWidth);
                paintRegion(g2, region, type);
                paintEdgeHighlight(g2, region);
            }
            rank++;
        }
        drawSoftVignette(g2, pixelWidth, pixelHeight);
    }

    /**
     * Group same-type cells into pixel-space areas, merging horizontal runs per
     * row to keep the rectangle count (and Area cost) low.
     */
    private Map<TerrainType, Area> buildRegions(int pixelWidth, int pixelHeight)
    {
        Map<TerrainType, Area> regions = new EnumMap<>(TerrainType.class);
        for(int row = 0; row < depth; row++) {
            int y0 = (int)Math.round((double)row * pixelHeight / depth);
            int y1 = (int)Math.round((double)(row + 1) * pixelHeight / depth);
            int height = Math.max(1, y1 - y0);
            int col = 0;
            while(col < width) {
                TerrainType type = terrain[row][col];
                int start = col;
                while(col < width && terrain[row][col] == type) {
                    col++;
                }
                int x0 = (int)Math.round((double)start * pixelWidth / width);
                int x1 = (int)Math.round((double)col * pixelWidth / width);
                Rectangle run = new Rectangle(x0, y0, Math.max(1, x1 - x0), height);
                Area area = regions.get(type);
                if(area == null) {
                    area = new Area();
                    regions.put(type, area);
                }
                area.add(new Area(run));
            }
        }
        return regions;
    }

    /**
     * Fill a region with its tiled texture, then a region-wide vertical light
     * gradient (lit top, shaded bottom) for the 2.5D surface.
     */
    private void paintRegion(Graphics2D g2, Area region, TerrainType type)
    {
        Rectangle bounds = region.getBounds();
        Shape oldClip = g2.getClip();
        g2.clip(region);
        g2.setPaint(tileSet.paintFor(type));
        g2.fill(bounds);
        g2.setPaint(new GradientPaint(0, bounds.y, new Color(255, 255, 255, 30),
                                      0, bounds.y + bounds.height,
                                      new Color(0, 0, 0, 52)));
        g2.fill(bounds);
        g2.setClip(oldClip);
    }

    /**
     * Cast a soft shadow from a raised region onto the already-painted lower
     * regions below-right of it. Offset and darkness grow with elevation rank.
     */
    private void paintElevationShadow(Graphics2D g2, Area region, int rank,
                                      int pixelWidth)
    {
        int offset = Math.max(2, pixelWidth / 320) * Math.max(1, rank);
        Area shadow = new Area(region);
        shadow.transform(AffineTransform.getTranslateInstance(offset, offset));
        shadow.subtract(region);
        g2.setColor(new Color(38, 28, 16, Math.min(95, 26 + rank * 16)));
        g2.fill(shadow);
    }

    /**
     * Thin sunlit lip along the top-left edge of a region for relief.
     */
    private void paintEdgeHighlight(Graphics2D g2, Area region)
    {
        Area highlight = new Area(region);
        highlight.transform(AffineTransform.getTranslateInstance(-1.0, -1.0));
        highlight.subtract(region);
        g2.setColor(new Color(255, 250, 232, 60));
        g2.fill(highlight);
    }

    private void generate()
    {
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                terrain[row][col] = classify(row, col);
            }
        }
    }

    private TerrainType classify(int row, int col)
    {
        if(generationProfile.isProcedural()) {
            return classifyProcedural(row, col);
        }
        double x = (double)col / Math.max(1, width - 1);
        double y = (double)row / Math.max(1, depth - 1);
        double waterDistance = nearestWaterEllipseDistance(row, col);
        double oasisDistance = ellipseDistance(row, col, oasisCenterRow,
                                               oasisCenterCol, oasisRadiusRow,
                                               oasisRadiusCol);
        double southeastPoolDistance = ellipseDistance(row, col,
            waterCenterRows[1], waterCenterCols[1],
            waterRadiusRows[1], waterRadiusCols[1]);
        double corridorDistance = Math.abs(y - corridorY(x));

        if(waterDistance <= 1.0) {
            return TerrainType.WATERHOLE;
        }
        if(southeastPoolDistance <= 2.10) {
            return TerrainType.OPEN_PLAIN;
        }
        if(oasisDistance <= 2.35 || corridorDistance < 0.055) {
            return TerrainType.GRASSLAND;
        }
        if(oasisDistance <= 3.10 || corridorDistance < 0.090 ||
           x < 0.08 || y < 0.08 || (x < 0.18 && y > 0.60)) {
            return TerrainType.BUSH;
        }
        if((x > 0.70 && y > 0.58) || y > 0.86 ||
           (x > 0.84 && y > 0.38)) {
            return TerrainType.DRY_SOIL;
        }
        return TerrainType.OPEN_PLAIN;
    }

    /**
     * TV3 preview layout.  Every value derives only from the terrain seed;
     * it deliberately does not read the simulation's ecology random stream.
     */
    private TerrainType classifyProcedural(int row, int col)
    {
        double x = (double)col / Math.max(1, width - 1);
        double y = (double)row / Math.max(1, depth - 1);
        double waterDistance = proceduralWaterDistance(row, col);
        if(waterDistance <= 1.0) return TerrainType.WATERHOLE;

        double noise = 0.5 * Math.sin((x * proceduralLayout.noiseX +
            y * proceduralLayout.noiseY + proceduralLayout.noisePhase) * Math.PI) +
            0.25 * Math.sin((x * proceduralLayout.noiseY * 1.7 -
            y * proceduralLayout.noiseX * .65 + proceduralLayout.noisePhase * .43) *
            Math.PI);
        double dryDistance = normalizedEllipse(x, y, proceduralLayout.dryCenterX,
            proceduralLayout.dryCenterY, proceduralLayout.dryRadiusX,
            proceduralLayout.dryRadiusY);
        if(dryDistance <= 1.0 + noise * .10) return TerrainType.DRY_SOIL;

        double corridorY = proceduralLayout.corridorStartY +
            (proceduralLayout.corridorEndY - proceduralLayout.corridorStartY) * x +
            proceduralLayout.corridorAmplitude * Math.sin(
                (x * proceduralLayout.corridorFrequency + proceduralLayout.corridorPhase) *
                Math.PI);
        if(Math.abs(y - corridorY) < proceduralLayout.corridorHalfWidth + noise * .008) {
            return TerrainType.GRASSLAND;
        }
        if(waterDistance < 2.12 + noise * .14) return TerrainType.BUSH;

        double grassField = normalizedEllipse(x, y,
            proceduralLayout.grassCenterX, proceduralLayout.grassCenterY,
            proceduralLayout.grassRadiusX, proceduralLayout.grassRadiusY);
        if(grassField < 1.0 + noise * .12) return TerrainType.GRASSLAND;

        if(noise > .52 || (x < .11 && y > .30 && y < .86)) {
            return TerrainType.BUSH;
        }
        return TerrainType.OPEN_PLAIN;
    }

    private double proceduralWaterDistance(double row, double col)
    {
        double nearest = Double.MAX_VALUE;
        for(int index = 0; index < WATER_BODY_COUNT; index++) {
            double rowPart = (row - waterCenterRows[index]) / waterRadiusRows[index];
            double colPart = (col - waterCenterCols[index]) / waterRadiusCols[index];
            double raw = Math.sqrt(rowPart * rowPart + colPart * colPart);
            double angle = Math.atan2(rowPart, colPart);
            double wobble = 1.0 + .09 * Math.sin(angle * 3.0 +
                proceduralLayout.waterPhases[index]) + .045 * Math.sin(angle * 5.0 -
                proceduralLayout.waterPhases[index] * .7);
            nearest = Math.min(nearest, raw / wobble);
        }
        return nearest;
    }

    private static double normalizedEllipse(double x, double y, double centerX,
                                            double centerY, double radiusX,
                                            double radiusY)
    {
        double horizontal = (x - centerX) / radiusX;
        double vertical = (y - centerY) / radiusY;
        return Math.sqrt(horizontal * horizontal + vertical * vertical);
    }

    private double corridorY(double x)
    {
        return 0.22 + 0.55 * x + 0.045 * Math.sin((x * 2.8 + 0.15) * Math.PI);
    }

    private double ellipseDistance(double row, double col, double centerRow,
                                   double centerCol, double radiusRow,
                                   double radiusCol)
    {
        double rowPart = (row - centerRow) / radiusRow;
        double colPart = (col - centerCol) / radiusCol;
        return Math.sqrt(rowPart * rowPart + colPart * colPart);
    }

    private double nearestWaterEllipseDistance(double row, double col)
    {
        double nearest = Double.MAX_VALUE;
        for(int index = 0; index < WATER_BODY_COUNT; index++) {
            nearest = Math.min(nearest, ellipseDistance(row, col,
                waterCenterRows[index], waterCenterCols[index],
                waterRadiusRows[index], waterRadiusCols[index]));
        }
        return nearest;
    }

    private void smoothIsolatedCells()
    {
        TerrainType[][] copy = new TerrainType[depth][width];
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                copy[row][col] = terrain[row][col];
            }
        }

        for(int row = 1; row < depth - 1; row++) {
            for(int col = 1; col < width - 1; col++) {
                TerrainType current = copy[row][col];
                Map<TerrainType, Integer> nearby = new EnumMap<>(TerrainType.class);
                for(TerrainType type : TerrainType.values()) {
                    nearby.put(type, 0);
                }
                for(int r = row - 1; r <= row + 1; r++) {
                    for(int c = col - 1; c <= col + 1; c++) {
                        nearby.put(copy[r][c], nearby.get(copy[r][c]) + 1);
                    }
                }
                TerrainType strongest = current;
                int strongestCount = nearby.get(current);
                for(TerrainType type : TerrainType.values()) {
                    int count = nearby.get(type);
                    if(count > strongestCount) {
                        strongest = type;
                        strongestCount = count;
                    }
                }
                if(strongest != current && strongestCount >= 6) {
                    terrain[row][col] = strongest;
                }
            }
        }
    }

    /**
     * Render the waterhole as a sunken feature, clipped to its grid region so
     * the water shape always matches the logical terrain. This keeps the fine
     * radial-water detail as a deliberate exception to the tile pipeline.
     */
    private void drawWaterhole(Graphics2D g2, Area region, int pixelWidth,
                               int pixelHeight)
    {
        for(int index = 0; index < WATER_BODY_COUNT; index++) {
            Area body = new Area(region);
            double cellWidth = (double)pixelWidth / this.width;
            double cellHeight = (double)pixelHeight / this.depth;
            double centerX = (waterCenterCols[index] / this.width) * pixelWidth;
            double centerY = (waterCenterRows[index] / this.depth) * pixelHeight;
            double radiusX = (waterRadiusCols[index] / this.width) * pixelWidth;
            double radiusY = (waterRadiusRows[index] / this.depth) * pixelHeight;
            body.intersect(new Area(new Rectangle(
                (int)Math.floor(centerX - radiusX - cellWidth * 2.0),
                (int)Math.floor(centerY - radiusY - cellHeight * 2.0),
                (int)Math.ceil(radiusX * 2.0 + cellWidth * 4.0),
                (int)Math.ceil(radiusY * 2.0 + cellHeight * 4.0))));
            if(!body.isEmpty()) {
                drawSingleWaterhole(g2, body, pixelWidth, pixelHeight,
                                    centerX, centerY, radiusX, radiusY);
            }
        }
    }

    private void drawSingleWaterhole(Graphics2D g2, Area region,
                                     int pixelWidth, int pixelHeight,
                                     double centerX, double centerY,
                                     double radiusX, double radiusY)
    {

        Rectangle bounds = region.getBounds();
        Shape oldClip = g2.getClip();
        g2.clip(region);

        // Radial water body: bright shallow rim into deep centre.
        g2.setPaint(new RadialGradientPaint(
            new Point2D.Double(centerX - radiusX * 0.25, centerY - radiusY * 0.25),
            (float)Math.max(Math.max(radiusX, radiusY) * 1.25,
                            Math.max(bounds.width, bounds.height) * 0.6),
            new float[] {0.0f, 0.62f, 1.0f},
            new Color[] {
                new Color(103, 174, 190),
                TerrainType.WATERHOLE.getColor(),
                new Color(32, 83, 112)}));
        g2.fill(bounds);

        // Sunken inner shadow on the top-left lip.
        Area lip = new Area(region);
        lip.transform(AffineTransform.getTranslateInstance(
            Math.max(2, pixelWidth / 280), Math.max(2, pixelHeight / 280)));
        lip.intersect(region);
        Area innerShadow = new Area(region);
        innerShadow.subtract(lip);
        g2.setColor(new Color(20, 52, 70, 120));
        g2.fill(innerShadow);

        // Surface ripples and a sun glint.
        g2.setColor(new Color(198, 236, 237, 120));
        g2.setStroke(new BasicStroke(Math.max(1.0f, pixelWidth * 0.0016f)));
        for(int i = 0; i < 4; i++) {
            double ry = centerY - radiusY * 0.4 + i * radiusY * 0.4;
            g2.draw(new Ellipse2D.Double(centerX - radiusX * 0.7, ry,
                                         radiusX * 1.4, radiusY * 0.22));
        }
        g2.setColor(new Color(214, 244, 245, 150));
        g2.fill(new Ellipse2D.Double(centerX - radiusX * 0.62,
                                     centerY - radiusY * 0.53,
                                     radiusX * 0.7, radiusY * 0.5));
        g2.setClip(oldClip);

        // Bright shoreline rim drawn on the region outline. Keep this
        // cell-relative so 3x maps do not get a multi-cell water-looking lip.
        g2.setColor(new Color(218, 203, 143, 170));
        float cellPixels = (float)Math.max(1.0,
            Math.min((double)pixelWidth / this.width,
                     (double)pixelHeight / this.depth));
        g2.setStroke(new BasicStroke(Math.max(1.5f, cellPixels * 0.42f),
                                     BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(region);
    }

    private double distanceToNearestWaterCell(double row, double col)
    {
        int minRow = clamp((int)Math.floor(row - VISUAL_WATER_BUFFER_CELLS),
                           0, depth - 1);
        int maxRow = clamp((int)Math.floor(row + VISUAL_WATER_BUFFER_CELLS),
                           0, depth - 1);
        int minCol = clamp((int)Math.floor(col - VISUAL_WATER_BUFFER_CELLS),
                           0, width - 1);
        int maxCol = clamp((int)Math.floor(col + VISUAL_WATER_BUFFER_CELLS),
                           0, width - 1);
        double best = Double.MAX_VALUE;
        for(int nextRow = minRow; nextRow <= maxRow; nextRow++) {
            for(int nextCol = minCol; nextCol <= maxCol; nextCol++) {
                if(terrain[nextRow][nextCol] == TerrainType.WATERHOLE) {
                    best = Math.min(best,
                        distanceToCell(row, col, nextRow, nextCol));
                }
            }
        }
        return best;
    }

    private double distanceToCell(double row, double col, int cellRow,
                                  int cellCol)
    {
        double rowDistance = axisDistance(row, cellRow, cellRow + 1.0);
        double colDistance = axisDistance(col, cellCol, cellCol + 1.0);
        return Math.sqrt(rowDistance * rowDistance +
                         colDistance * colDistance);
    }

    private double axisDistance(double value, double min, double max)
    {
        if(value < min) {
            return min - value;
        }
        if(value > max) {
            return value - max;
        }
        return 0.0;
    }

    private void drawSoftVignette(Graphics2D g2, int width, int height)
    {
        g2.setColor(new Color(255, 255, 255, 18));
        g2.fillRect(0, 0, width, height);
        g2.setColor(new Color(71, 55, 33, 38));
        g2.setStroke(new BasicStroke(Math.max(8.0f, width * 0.016f)));
        g2.drawRect(2, 2, Math.max(1, width - 4), Math.max(1, height - 4));
    }

    private void drawTerrainLabels(Graphics2D g2, int width, int height)
    {
        Font oldFont = g2.getFont();
        g2.setFont(oldFont.deriveFont(Font.BOLD,
                                      Math.max(11.0f, width / 74.0f)));
        drawTerrainLabel(g2, "Oasis pool",
            waterCenterCols[0] / this.width,
            waterCenterRows[0] / this.depth, width, height);
        drawTerrainLabel(g2, "Southeast pool",
            waterCenterCols[1] / this.width,
            waterCenterRows[1] / this.depth, width, height);
        drawTerrainLabel(g2, "Grassland", 0.37, 0.34, width, height);
        drawTerrainLabel(g2, "Bush belt", 0.16, 0.67, width, height);
        drawTerrainLabel(g2, "Open plain", 0.66, 0.30, width, height);
        drawTerrainLabel(g2, "Dry soil", 0.80, 0.78, width, height);
        drawTerrainLabel(g2, "Lowland corridor", 0.58, 0.58, width, height);
        g2.setFont(oldFont);
    }

    private void drawTerrainLabel(Graphics2D g2, String text, double xRatio,
                                  double yRatio, int width, int height)
    {
        FontMetrics metrics = g2.getFontMetrics();
        int x = (int)Math.round(width * xRatio);
        int y = (int)Math.round(height * yRatio);
        int textWidth = metrics.stringWidth(text);
        int left = Math.max(6, Math.min(width - textWidth - 6,
                                        x - textWidth / 2));
        int baseline = Math.max(metrics.getAscent() + 6,
                                Math.min(height - 6, y));

        g2.setColor(new Color(255, 250, 226, 82));
        g2.drawString(text, left + 1, baseline + 1);
        g2.setColor(new Color(45, 39, 28, 88));
        g2.drawString(text, left, baseline);
    }

    private int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }

    /** Immutable procedural parameters kept out of ecology and render RNG. */
    private static final class ProceduralLayout
    {
        private final double[] waterRows = new double[WATER_BODY_COUNT];
        private final double[] waterCols = new double[WATER_BODY_COUNT];
        private final double[] waterRadiusRows = new double[WATER_BODY_COUNT];
        private final double[] waterRadiusCols = new double[WATER_BODY_COUNT];
        private final double[] waterPhases = new double[WATER_BODY_COUNT];
        private final double corridorStartY;
        private final double corridorEndY;
        private final double corridorAmplitude;
        private final double corridorFrequency;
        private final double corridorPhase;
        private final double corridorHalfWidth;
        private final double dryCenterX;
        private final double dryCenterY;
        private final double dryRadiusX;
        private final double dryRadiusY;
        private final double grassCenterX;
        private final double grassCenterY;
        private final double grassRadiusX;
        private final double grassRadiusY;
        private final double noiseX;
        private final double noiseY;
        private final double noisePhase;

        private ProceduralLayout(int depth, int width, Random random)
        {
            waterRadiusRows[0] = Math.max(2.0, depth * (.040 + random.nextDouble() * .028));
            waterRadiusCols[0] = Math.max(2.5, width * (.038 + random.nextDouble() * .038));
            waterRadiusRows[1] = Math.max(2.0, depth * (.040 + random.nextDouble() * .028));
            waterRadiusCols[1] = Math.max(2.5, width * (.038 + random.nextDouble() * .038));
            waterCols[0] = width * (.16 + random.nextDouble() * .28);
            waterRows[0] = depth * (.16 + random.nextDouble() * .63);
            double candidateCol = 0.0;
            double candidateRow = 0.0;
            for(int attempt = 0; attempt < 24; attempt++) {
                candidateCol = .56 + random.nextDouble() * .28;
                candidateRow = .15 + random.nextDouble() * .68;
                double rowGap = candidateRow - waterRows[0] / depth;
                double colGap = candidateCol - waterCols[0] / width;
                if(Math.sqrt(rowGap * rowGap + colGap * colGap) > .43) break;
            }
            waterCols[1] = width * candidateCol;
            waterRows[1] = depth * candidateRow;
            waterPhases[0] = random.nextDouble() * Math.PI * 2.0;
            waterPhases[1] = random.nextDouble() * Math.PI * 2.0;
            corridorStartY = .12 + random.nextDouble() * .56;
            corridorEndY = .18 + random.nextDouble() * .60;
            corridorAmplitude = .04 + random.nextDouble() * .12;
            corridorFrequency = 1.25 + random.nextDouble() * 2.25;
            corridorPhase = random.nextDouble() * 2.0;
            corridorHalfWidth = .032 + random.nextDouble() * .030;
            dryCenterX = .20 + random.nextDouble() * .62;
            dryCenterY = .16 + random.nextDouble() * .68;
            dryRadiusX = .16 + random.nextDouble() * .19;
            dryRadiusY = .14 + random.nextDouble() * .20;
            grassCenterX = .18 + random.nextDouble() * .64;
            grassCenterY = .16 + random.nextDouble() * .68;
            grassRadiusX = .14 + random.nextDouble() * .22;
            grassRadiusY = .12 + random.nextDouble() * .20;
            noiseX = 2.0 + random.nextDouble() * 3.0;
            noiseY = 2.0 + random.nextDouble() * 3.0;
            noisePhase = random.nextDouble() * 2.0;
        }

        static ProceduralLayout create(int depth, int width, Random random)
        {
            return new ProceduralLayout(depth, width, random);
        }
    }
}
