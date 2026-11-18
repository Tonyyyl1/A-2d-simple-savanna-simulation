package savanna;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders TV2 contour-band materials from the same masks that define the
 * terrain surfaces.  A band never treats a whole transition cell as a flat
 * texture target: it follows the extracted boundary, rotates its source
 * material to the local tangent, and clips every pixel back to the supplied
 * shared land mask.
 *
 * <p>The water path is extracted from {@link VisualWaterMask} at the current
 * render resolution.  This deliberately preserves the visual-water buffer
 * used by marker safety; a shoreline is therefore not inferred from a second
 * ellipse or a decorative approximation.  Land transitions are extracted
 * from shared logical cell edges.  Outer corners receive a round join, inner
 * corners receive a square overlap, and junctions receive a round join, so a
 * narrow or concave area does not develop axis-aligned gaps.</p>
 */
final class TerrainVisualContourBandPainter
{
    private static final double EPSILON = 0.0001;

    private TerrainVisualContourBandPainter()
    {
    }

    static WaterContour waterContour(
        TerrainVisualComposer.TerrainVisualComposition composition)
    {
        VisualGridGeometry geometry = composition.getGeometry();
        boolean[][] samples = new boolean[geometry.pixelHeight()][geometry.pixelWidth()];
        for(int y = 0; y < geometry.pixelHeight(); y++) {
            for(int x = 0; x < geometry.pixelWidth(); x++) {
                samples[y][x] = composition.getWaterMask().isWaterPixel(x, y);
            }
        }
        return new WaterContour(samples);
    }

    /** Package-visible fixture entry point for pixel-exact contour tests. */
    static WaterContour waterContour(boolean[][] samples)
    {
        return new WaterContour(samples);
    }

    static BandLayout shorelineLayout(WaterContour water)
    {
        if(water == null) throw new IllegalArgumentException("water contour is required.");
        return water.getLayout();
    }

    static BandLayout transitionLayout(
        TerrainVisualComposer.TerrainVisualComposition composition,
        TerrainVisualKit.MaterialDescriptor descriptor)
    {
        if(composition == null || descriptor == null) {
            throw new IllegalArgumentException("Composition and material descriptor are required.");
        }
        List<Segment> raw = new ArrayList<>();
        TerrainVisualComposer.TerrainTopology topology = composition.getTopology();
        VisualGridGeometry geometry = composition.getGeometry();
        for(int row = 0; row < topology.rows(); row++) {
            for(int col = 0; col < topology.cols(); col++) {
                TerrainType current = topology.terrainAt(row, col);
                if(col + 1 < topology.cols()) {
                    TerrainType next = topology.terrainAt(row, col + 1);
                    if(matchesTransition(descriptor, current, next)) {
                        Rectangle left = geometry.cellBounds(row, col);
                        raw.add(new Segment(left.x + left.width, left.y,
                            left.x + left.width, left.y + left.height));
                    }
                }
                if(row + 1 < topology.rows()) {
                    TerrainType next = topology.terrainAt(row + 1, col);
                    if(matchesTransition(descriptor, current, next)) {
                        Rectangle top = geometry.cellBounds(row, col);
                        raw.add(new Segment(top.x, top.y + top.height,
                            top.x + top.width, top.y + top.height));
                    }
                }
            }
        }
        List<Segment> segments = mergeCollinear(raw);
        return new BandLayout(segments, transitionCorners(segments));
    }

    static double bandWidthPixels(TerrainVisualKit.MaterialDescriptor descriptor,
                                  VisualGridGeometry geometry)
    {
        if(descriptor == null || geometry == null) {
            throw new IllegalArgumentException("Material descriptor and geometry are required.");
        }
        return Math.max(1.0, descriptor.getWorldHeightCells() *
            geometry.averageCellSize());
    }

    static void paint(Graphics2D graphics, BandLayout layout,
                      TerrainVisualKit.MaterialDescriptor descriptor,
                      BufferedImage image, Area allowed, float alpha,
                      VisualGridGeometry geometry)
    {
        if(graphics == null || layout == null || descriptor == null ||
           image == null || allowed == null || allowed.isEmpty()) {
            return;
        }
        double bandWidth = bandWidthPixels(descriptor, geometry);
        for(Segment segment : layout.getSegments()) {
            paintSegment(graphics, segment, descriptor, image, allowed,
                alpha, geometry, bandWidth);
        }
        for(Corner corner : layout.getCorners()) {
            paintCorner(graphics, corner, descriptor, image, allowed,
                alpha, geometry, bandWidth);
        }
    }

    private static boolean matchesTransition(
        TerrainVisualKit.MaterialDescriptor descriptor, TerrainType first,
        TerrainType second)
    {
        if(first == second || first == TerrainType.WATERHOLE ||
           second == TerrainType.WATERHOLE) {
            return false;
        }
        if("transition-grass-bush".equals(descriptor.getId())) {
            return (first == TerrainType.GRASSLAND && second == TerrainType.BUSH) ||
                   (first == TerrainType.BUSH && second == TerrainType.GRASSLAND);
        }
        return true;
    }

    private static void paintSegment(Graphics2D graphics, Segment segment,
                                     TerrainVisualKit.MaterialDescriptor descriptor,
                                     BufferedImage image, Area allowed,
                                     float alpha, VisualGridGeometry geometry,
                                     double bandWidth)
    {
        Area clip = new Area(segmentShape(segment, bandWidth));
        clip.intersect(new Area(allowed));
        if(clip.isEmpty()) return;

        int crossPixels = Math.max(1, (int)Math.ceil(bandWidth));
        int padding = Math.max(1, (int)Math.ceil(bandWidth / 2.0));
        int alongPixels = Math.max(1, (int)Math.ceil(segment.length())) + padding * 2;
        BufferedImage strip = textureStrip(image, descriptor, geometry,
            alongPixels, crossPixels, segment.tangentCoordinate() - padding);
        AffineTransform transform = new AffineTransform();
        transform.translate(segment.getX1(), segment.getY1());
        transform.rotate(segment.tangentRadians());
        transform.translate(-padding, -crossPixels / 2.0);
        drawClipped(graphics, strip, transform, clip, alpha);
    }

    private static void paintCorner(Graphics2D graphics, Corner corner,
                                    TerrainVisualKit.MaterialDescriptor descriptor,
                                    BufferedImage image, Area allowed,
                                    float alpha, VisualGridGeometry geometry,
                                    double bandWidth)
    {
        Area clip = new Area(cornerShape(corner, bandWidth));
        clip.intersect(new Area(allowed));
        if(clip.isEmpty()) return;

        int diameter = Math.max(1, (int)Math.ceil(bandWidth * 2.0));
        BufferedImage patch = textureStrip(image, descriptor, geometry,
            diameter, diameter, corner.tangentCoordinate() - diameter / 2.0);
        AffineTransform transform = new AffineTransform();
        transform.translate(corner.getX(), corner.getY());
        transform.rotate(corner.getTangentRadians());
        transform.translate(-diameter / 2.0, -diameter / 2.0);
        drawClipped(graphics, patch, transform, clip, alpha);
    }

    private static BufferedImage textureStrip(BufferedImage image,
                                               TerrainVisualKit.MaterialDescriptor descriptor,
                                               VisualGridGeometry geometry,
                                               int width, int height,
                                               double localStart)
    {
        int tileWidth = Math.max(1, (int)Math.round(descriptor.getWorldWidthCells() *
            geometry.averageCellSize()));
        int tileHeight = Math.max(1, (int)Math.round(descriptor.getWorldHeightCells() *
            geometry.averageCellSize()));
        int phase = Math.floorMod((int)Math.round(localStart), tileWidth);
        BufferedImage result = new BufferedImage(width, height,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D local = result.createGraphics();
        try {
            local.setPaint(new TexturePaint(image,
                new Rectangle(-phase, 0, tileWidth, tileHeight)));
            local.fillRect(0, 0, width, height);
        }
        finally {
            local.dispose();
        }
        return result;
    }

    private static void drawClipped(Graphics2D graphics, BufferedImage image,
                                    AffineTransform transform, Area clip,
                                    float alpha)
    {
        Shape oldClip = graphics.getClip();
        Composite oldComposite = graphics.getComposite();
        try {
            graphics.clip(clip);
            graphics.setComposite(AlphaComposite.SrcOver.derive(alpha));
            graphics.drawImage(image, transform, null);
        }
        finally {
            graphics.setComposite(oldComposite);
            graphics.setClip(oldClip);
        }
    }

    private static Shape segmentShape(Segment segment, double bandWidth)
    {
        return new BasicStroke((float)bandWidth, BasicStroke.CAP_SQUARE,
            BasicStroke.JOIN_MITER).createStrokedShape(new Line2D.Double(
                segment.getX1(), segment.getY1(), segment.getX2(), segment.getY2()));
    }

    private static Shape cornerShape(Corner corner, double bandWidth)
    {
        double half = bandWidth / 2.0;
        if(corner.getKind() == CornerKind.INNER) {
            return new Rectangle((int)Math.floor(corner.getX() - half),
                (int)Math.floor(corner.getY() - half),
                Math.max(1, (int)Math.ceil(bandWidth)),
                Math.max(1, (int)Math.ceil(bandWidth)));
        }
        return new Ellipse2D.Double(corner.getX() - half, corner.getY() - half,
            bandWidth, bandWidth);
    }

    private static List<Segment> waterSegments(boolean[][] pixels)
    {
        int rows = pixels.length;
        int cols = pixels[0].length;
        List<Segment> raw = new ArrayList<>();
        for(int row = 0; row < rows; row++) {
            appendHorizontalWaterEdges(pixels, row, true, raw);
            appendHorizontalWaterEdges(pixels, row, false, raw);
        }
        for(int col = 0; col < cols; col++) {
            appendVerticalWaterEdges(pixels, col, true, raw);
            appendVerticalWaterEdges(pixels, col, false, raw);
        }
        return mergeCollinear(raw);
    }

    private static void appendHorizontalWaterEdges(boolean[][] pixels, int row,
                                                   boolean top,
                                                   List<Segment> output)
    {
        int start = -1;
        int cols = pixels[0].length;
        for(int col = 0; col <= cols; col++) {
            boolean exposed = col < cols && pixels[row][col] &&
                (top ? row == 0 || !pixels[row - 1][col]
                     : row + 1 == pixels.length || !pixels[row + 1][col]);
            if(exposed && start < 0) start = col;
            if(!exposed && start >= 0) {
                int y = top ? row : row + 1;
                output.add(new Segment(start, y, col, y));
                start = -1;
            }
        }
    }

    private static void appendVerticalWaterEdges(boolean[][] pixels, int col,
                                                 boolean left,
                                                 List<Segment> output)
    {
        int start = -1;
        int rows = pixels.length;
        for(int row = 0; row <= rows; row++) {
            boolean exposed = row < rows && pixels[row][col] &&
                (left ? col == 0 || !pixels[row][col - 1]
                      : col + 1 == pixels[0].length || !pixels[row][col + 1]);
            if(exposed && start < 0) start = row;
            if(!exposed && start >= 0) {
                int x = left ? col : col + 1;
                output.add(new Segment(x, start, x, row));
                start = -1;
            }
        }
    }

    private static List<Segment> mergeCollinear(List<Segment> raw)
    {
        Map<Integer, List<Segment>> horizontal = new HashMap<>();
        Map<Integer, List<Segment>> vertical = new HashMap<>();
        for(Segment segment : raw) {
            if(segment.isHorizontal()) {
                horizontal.computeIfAbsent((int)Math.round(segment.getY1()), ignored ->
                    new ArrayList<>()).add(segment.normalized());
            }
            else {
                vertical.computeIfAbsent((int)Math.round(segment.getX1()), ignored ->
                    new ArrayList<>()).add(segment.normalized());
            }
        }
        List<Segment> merged = new ArrayList<>();
        for(List<Segment> group : horizontal.values()) {
            group.sort(Comparator.comparingDouble(Segment::getX1));
            appendMerged(group, true, merged);
        }
        for(List<Segment> group : vertical.values()) {
            group.sort(Comparator.comparingDouble(Segment::getY1));
            appendMerged(group, false, merged);
        }
        merged.sort(Comparator.comparingDouble(Segment::getY1)
            .thenComparingDouble(Segment::getX1)
            .thenComparingDouble(Segment::getY2)
            .thenComparingDouble(Segment::getX2));
        return Collections.unmodifiableList(merged);
    }

    private static void appendMerged(List<Segment> group, boolean horizontal,
                                     List<Segment> output)
    {
        Segment current = null;
        for(Segment next : group) {
            if(current == null) {
                current = next;
            }
            else if(horizontal && Math.abs(current.getX2() - next.getX1()) < EPSILON) {
                current = new Segment(current.getX1(), current.getY1(),
                    next.getX2(), current.getY2());
            }
            else if(!horizontal && Math.abs(current.getY2() - next.getY1()) < EPSILON) {
                current = new Segment(current.getX1(), current.getY1(),
                    current.getX2(), next.getY2());
            }
            else {
                output.add(current);
                current = next;
            }
        }
        if(current != null) output.add(current);
    }

    private static List<Corner> waterCorners(boolean[][] pixels,
                                              List<Segment> segments)
    {
        Map<Vertex, List<Segment>> endpoints = endpointsFor(segments);
        List<Corner> result = new ArrayList<>();
        for(int y = 0; y <= pixels.length; y++) {
            for(int x = 0; x <= pixels[0].length; x++) {
                int occupied = occupiedQuadrants(pixels, x, y);
                CornerKind kind = occupied == 1 ? CornerKind.OUTER
                    : occupied == 3 ? CornerKind.INNER : null;
                if(kind != null) {
                    result.add(new Corner(x, y, kind,
                        tangentFor(endpoints.get(new Vertex(x, y)), 0.0)));
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static int occupiedQuadrants(boolean[][] pixels, int x, int y)
    {
        int result = 0;
        if(x > 0 && y > 0 && pixels[y - 1][x - 1]) result++;
        if(x < pixels[0].length && y > 0 && pixels[y - 1][x]) result++;
        if(x > 0 && y < pixels.length && pixels[y][x - 1]) result++;
        if(x < pixels[0].length && y < pixels.length && pixels[y][x]) result++;
        return result;
    }

    private static List<Corner> transitionCorners(List<Segment> segments)
    {
        Map<Vertex, List<Segment>> endpoints = endpointsFor(segments);
        List<Corner> result = new ArrayList<>();
        for(Map.Entry<Vertex, List<Segment>> entry : endpoints.entrySet()) {
            boolean horizontal = false;
            boolean vertical = false;
            for(Segment segment : entry.getValue()) {
                horizontal |= segment.isHorizontal();
                vertical |= !segment.isHorizontal();
            }
            if(horizontal && vertical) {
                result.add(new Corner(entry.getKey().x, entry.getKey().y,
                    CornerKind.JUNCTION, tangentFor(entry.getValue(), 0.0)));
            }
        }
        result.sort(Comparator.comparingDouble(Corner::getY)
            .thenComparingDouble(Corner::getX));
        return Collections.unmodifiableList(result);
    }

    private static Map<Vertex, List<Segment>> endpointsFor(List<Segment> segments)
    {
        Map<Vertex, List<Segment>> result = new HashMap<>();
        for(Segment segment : segments) {
            result.computeIfAbsent(new Vertex(segment.getX1(), segment.getY1()), ignored ->
                new ArrayList<>()).add(segment);
            result.computeIfAbsent(new Vertex(segment.getX2(), segment.getY2()), ignored ->
                new ArrayList<>()).add(segment);
        }
        return result;
    }

    private static double tangentFor(List<Segment> segments, double fallback)
    {
        if(segments == null || segments.isEmpty()) return fallback;
        return segments.get(0).tangentRadians();
    }

    static final class WaterContour
    {
        private final boolean[][] pixels;
        private final Area area;
        private final BandLayout layout;

        private WaterContour(boolean[][] samples)
        {
            pixels = copyPixels(samples);
            area = areaForPixels(pixels);
            List<Segment> segments = waterSegments(pixels);
            layout = new BandLayout(segments, waterCorners(pixels, segments));
        }

        boolean isWaterPixel(int x, int y)
        {
            return y >= 0 && y < pixels.length && x >= 0 && x < pixels[0].length &&
                   pixels[y][x];
        }

        Area getArea()
        {
            return new Area(area);
        }

        BandLayout getLayout()
        {
            return layout;
        }
    }

    static final class BandLayout
    {
        private final List<Segment> segments;
        private final List<Corner> corners;

        private BandLayout(List<Segment> segments, List<Corner> corners)
        {
            this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
            this.corners = Collections.unmodifiableList(new ArrayList<>(corners));
        }

        List<Segment> getSegments()
        {
            return segments;
        }

        List<Corner> getCorners()
        {
            return corners;
        }

        int horizontalSegmentCount()
        {
            int result = 0;
            for(Segment segment : segments) {
                if(segment.isHorizontal()) result++;
            }
            return result;
        }

        int verticalSegmentCount()
        {
            return segments.size() - horizontalSegmentCount();
        }

        int cornerCount(CornerKind kind)
        {
            int result = 0;
            for(Corner corner : corners) {
                if(corner.getKind() == kind) result++;
            }
            return result;
        }

        Area footprint(double bandWidth, Area allowed)
        {
            Area result = new Area();
            for(Segment segment : segments) {
                result.add(new Area(segmentShape(segment, bandWidth)));
            }
            for(Corner corner : corners) {
                result.add(new Area(cornerShape(corner, bandWidth)));
            }
            result.intersect(new Area(allowed));
            return result;
        }
    }

    static final class Segment
    {
        private final double x1;
        private final double y1;
        private final double x2;
        private final double y2;

        private Segment(double x1, double y1, double x2, double y2)
        {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        double getX1() { return x1; }
        double getY1() { return y1; }
        double getX2() { return x2; }
        double getY2() { return y2; }

        boolean isHorizontal()
        {
            return Math.abs(y2 - y1) < EPSILON;
        }

        double length()
        {
            return Math.hypot(x2 - x1, y2 - y1);
        }

        double tangentRadians()
        {
            return Math.atan2(y2 - y1, x2 - x1);
        }

        double tangentCoordinate()
        {
            return isHorizontal() ? Math.min(x1, x2) : Math.min(y1, y2);
        }

        Segment normalized()
        {
            if(isHorizontal() && x2 < x1) return new Segment(x2, y2, x1, y1);
            if(!isHorizontal() && y2 < y1) return new Segment(x2, y2, x1, y1);
            return this;
        }
    }

    enum CornerKind
    {
        OUTER,
        INNER,
        JUNCTION
    }

    static final class Corner
    {
        private final double x;
        private final double y;
        private final CornerKind kind;
        private final double tangentRadians;

        private Corner(double x, double y, CornerKind kind,
                       double tangentRadians)
        {
            this.x = x;
            this.y = y;
            this.kind = kind;
            this.tangentRadians = tangentRadians;
        }

        double getX() { return x; }
        double getY() { return y; }
        CornerKind getKind() { return kind; }
        double getTangentRadians() { return tangentRadians; }

        double tangentCoordinate()
        {
            return Math.abs(Math.cos(tangentRadians)) >=
                   Math.abs(Math.sin(tangentRadians)) ? x : y;
        }
    }

    private static final class Vertex
    {
        private final int x;
        private final int y;

        private Vertex(double x, double y)
        {
            this.x = (int)Math.round(x);
            this.y = (int)Math.round(y);
        }

        @Override
        public boolean equals(Object other)
        {
            if(this == other) return true;
            if(!(other instanceof Vertex)) return false;
            Vertex vertex = (Vertex)other;
            return x == vertex.x && y == vertex.y;
        }

        @Override
        public int hashCode()
        {
            return x * 31 + y;
        }
    }

    private static boolean[][] copyPixels(boolean[][] source)
    {
        if(source == null || source.length == 0 || source[0] == null ||
           source[0].length == 0) {
            throw new IllegalArgumentException("Water samples must not be empty.");
        }
        boolean[][] copy = new boolean[source.length][source[0].length];
        for(int row = 0; row < source.length; row++) {
            if(source[row] == null || source[row].length != copy[row].length) {
                throw new IllegalArgumentException("Water samples must be rectangular.");
            }
            System.arraycopy(source[row], 0, copy[row], 0, copy[row].length);
        }
        return copy;
    }

    private static Area areaForPixels(boolean[][] pixels)
    {
        Area result = new Area();
        for(int row = 0; row < pixels.length; row++) {
            int start = -1;
            for(int col = 0; col <= pixels[row].length; col++) {
                boolean water = col < pixels[row].length && pixels[row][col];
                if(water && start < 0) start = col;
                if(!water && start >= 0) {
                    result.add(new Area(new Rectangle(start, row, col - start, 1)));
                    start = -1;
                }
            }
        }
        return result;
    }
}
