package savanna;

import java.awt.Color;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Renders the ordinary lightweight view exclusively from an immutable frame. */
public final class OverviewRenderer
{
    private static final int AGGREGATE_CELL_SIZE = 8;
    private final TerrainBackgroundCache terrainBackgroundCache;

    public OverviewRenderer()
    {
        this(TerrainBackgroundCache.shared());
    }

    /** Package-visible injection keeps the zero-step cache contract testable. */
    OverviewRenderer(TerrainBackgroundCache terrainBackgroundCache)
    {
        if(terrainBackgroundCache == null) {
            throw new IllegalArgumentException("A terrain background cache is required.");
        }
        this.terrainBackgroundCache = terrainBackgroundCache;
    }

    public OverviewRenderResult render(RenderFrame frame, RenderOptions options)
    {
        if(frame == null || options == null) {
            throw new IllegalArgumentException("Overview rendering needs a frame and options.");
        }
        int width = options.getWidth();
        int height = options.getHeight();
        BufferedImage terrain = terrainFor(frame.terrainMap, width, height);

        BufferedImage overlay = new BufferedImage(width, height,
                                                   BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = overlay.createGraphics();
        int drawn = 0;
        int priorityOverlayCount = 0;
        Map<String, Integer> drawnBySpecies = new LinkedHashMap<>();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                      RenderingHints.VALUE_ANTIALIAS_ON);
            VisualGridGeometry geometry = frame.terrainMap == null
                ? new VisualGridGeometry(Math.max(1, frame.panelHeight),
                    Math.max(1, frame.panelWidth), width, height)
                : VisualGridGeometry.forTerrain(frame.terrainMap, width, height);
            if(options.isHeatmapIncluded()) {
                HeatmapRenderer.render(graphics, frame.heatmapSnapshot == null ? null
                    : frame.heatmapSnapshot.select(options.getHeatmapSelection()),
                    frame.terrainMap, geometry,
                    new Rectangle(0, 0, geometry.cols(), geometry.rows()));
            }
            Map<Long, Integer> density = aggregateDensity(frame);
            for(RenderAnimalSnapshot animal : frame.animals) {
                if(!animal.alive || animal.location() == null ||
                   !isInside(frame, animal) || !shouldDraw(animal, density)) {
                    continue;
                }
                if(frame.terrainMap != null &&
                   !frame.terrainMap.isPassable(animal.location())) {
                    continue;
                }
                drawAnimal(graphics, animal, geometry, frame.terrainMap);
                drawn++;
                drawnBySpecies.put(animal.species,
                    drawnBySpecies.getOrDefault(animal.species, 0) + 1);
                if(isPriorityOverlay(animal)) priorityOverlayCount++;
            }
            if(options.isEnvironmentOverlayIncluded()) {
                drawWeatherAndTime(graphics, width, height, frame.metrics);
            }
            if(options.isMetricPanelIncluded()) {
                drawMetricPanel(graphics, width, frame);
            }
        }
        finally {
            graphics.dispose();
        }
        return new OverviewRenderResult(terrain, overlay, drawn, drawnBySpecies,
                                        priorityOverlayCount);
    }

    private BufferedImage terrainFor(TerrainMap terrainMap, int width, int height)
    {
        return terrainBackgroundCache.imageFor(terrainMap, width, height);
    }

    private boolean isInside(RenderFrame frame, RenderAnimalSnapshot animal)
    {
        if(frame.terrainMap != null) {
            return animal.row >= 0 && animal.row < frame.terrainMap.getDepth() &&
                   animal.col >= 0 && animal.col < frame.terrainMap.getWidth();
        }
        return animal.row >= 0 && animal.row < frame.panelHeight &&
               animal.col >= 0 && animal.col < frame.panelWidth;
    }

    private Map<Long, Integer> aggregateDensity(RenderFrame frame)
    {
        Map<Long, Integer> counts = new HashMap<>();
        for(RenderAnimalSnapshot animal : frame.animals) {
            long key = aggregateKey(animal.row, animal.col);
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        return counts;
    }

    private long aggregateKey(int row, int col)
    {
        long aggregateRow = Math.max(0, row) / AGGREGATE_CELL_SIZE;
        long aggregateCol = Math.max(0, col) / AGGREGATE_CELL_SIZE;
        return (aggregateRow << 32) ^ aggregateCol;
    }

    private boolean shouldDraw(RenderAnimalSnapshot animal,
                               Map<Long, Integer> density)
    {
        int population = density.getOrDefault(aggregateKey(animal.row, animal.col), 0);
        if(population <= 5 || animal.predator || animal.infected ||
           animal.survivalCritical || animal.thirsty ||
           animal.staminaStage == StaminaStage.LOW) {
            return true;
        }
        int spacing = Math.min(7, Math.max(2, population / 4));
        return Math.abs(animal.col * 31 + animal.row * 17) % spacing == 0;
    }

    private boolean isPriorityOverlay(RenderAnimalSnapshot animal)
    {
        return animal.predator || animal.infected || animal.survivalCritical ||
               animal.thirsty || animal.staminaStage == StaminaStage.LOW;
    }

    private void drawAnimal(Graphics2D graphics, RenderAnimalSnapshot animal,
                            VisualGridGeometry geometry, TerrainMap terrain)
    {
        Location location = animal.location();
        VisualFootprint.OrdinaryMarker marker =
            VisualFootprint.ordinaryMarker(location, geometry);
        int size = marker.symbolSize;
        int left = marker.symbolLeft;
        int top = marker.symbolTop;
        SpeciesProfile profile = SpeciesRegistry.getProfile(animal.species);
        Color color = profile == null ? Color.gray : profile.getColor();
        graphics.setColor(new Color(20, 18, 12, 60));
        graphics.fillOval(left + 1, top + 2, size, Math.max(2, size / 2));
        graphics.setColor(color);
        if(animal.predator) {
            Polygon triangle = new Polygon();
            triangle.addPoint(left + size / 2, top);
            triangle.addPoint(left, top + size);
            triangle.addPoint(left + size, top + size);
            graphics.fillPolygon(triangle);
        }
        else {
            graphics.fillOval(left, top, size, size);
        }
        graphics.setColor(new Color(25, 25, 25, 115));
        if(animal.predator) {
            graphics.drawLine(left + size / 2, top, left, top + size);
            graphics.drawLine(left, top + size, left + size, top + size);
            graphics.drawLine(left + size, top + size, left + size / 2, top);
        }
        else {
            graphics.drawOval(left, top, size, size);
        }
        if(animal.survivalCritical && size >= 6) {
            graphics.setColor(new Color(255, 145, 42, 210));
            graphics.drawOval(left - 1, top - 1, size + 2, size + 2);
        }
        if(animal.staminaStage == StaminaStage.LOW && marker.cellHeight >= 5) {
            int bar = Math.max(2, (int)Math.round(marker.cellWidth *
                animal.staminaPercent / 100.0));
            graphics.setColor(new Color(35, 35, 35, 120));
            graphics.fillRect(marker.left, marker.top + marker.cellHeight - 2,
                              marker.cellWidth, 2);
            graphics.setColor(new Color(245, 211, 76, 230));
            graphics.fillRect(marker.left, marker.top + marker.cellHeight - 2,
                              bar, 2);
        }
        if(animal.thirsty) {
            int drop = size <= 5 ? 1 : Math.max(3, size / 3);
            graphics.setColor(new Color(43, 136, 210));
            graphics.fillOval(size <= 5 ? left + size - 1 : left, top,
                              drop, drop + 1);
        }
        if(animal.actionType != null) {
            graphics.setColor(BehaviorVisuals.colorFor(animal.actionType));
            graphics.fillRect(left, top + size - (size <= 5 ? 1 : 2),
                              size <= 5 ? 2 : Math.max(3, size / 2),
                              size <= 5 ? 1 : 2);
        }
        if(animal.infected) {
            graphics.setColor(new Color(210, 35, 42));
            int dot = Math.max(2, size / 3);
            graphics.fillOval(left + size - dot, top, dot, dot);
        }
        if(terrain != null) {
            clearWaterPixels(graphics, marker, geometry, terrain);
        }
    }

    /**
     * Erases only marker pixels that overlap visual water. Horizontal runs are
     * much cheaper than constructing thousands of one-pixel Area objects, and
     * weather/metric overlays are painted afterwards so only animal ink is
     * clipped.
     */
    private void clearWaterPixels(Graphics2D graphics,
                                  VisualFootprint.OrdinaryMarker marker,
                                  VisualGridGeometry geometry,
                                  TerrainMap terrain)
    {
        int minX = marker.left - 2;
        int minY = marker.top - 2;
        int maxX = Math.max(marker.left + marker.cellWidth + 2,
                            marker.symbolLeft + marker.symbolSize + 2);
        int maxY = Math.max(marker.top + marker.cellHeight + 2,
                            marker.symbolTop + marker.symbolSize + 3);
        Composite previous = graphics.getComposite();
        graphics.setComposite(AlphaComposite.Clear);
        for(int y = minY; y <= maxY; y++) {
            int runStart = -1;
            for(int x = minX; x <= maxX + 1; x++) {
                boolean water = x <= maxX &&
                    geometry.isWaterPixel(terrain, x, y);
                if(water && runStart < 0) {
                    runStart = x;
                }
                else if(!water && runStart >= 0) {
                    graphics.fillRect(runStart, y, x - runStart, 1);
                    runStart = -1;
                }
            }
        }
        graphics.setComposite(previous);
    }

    private void drawWeatherAndTime(Graphics2D graphics, int width, int height,
                                    RenderMetrics metrics)
    {
        Color tint;
        if(metrics.weatherType == WeatherType.RAIN) tint = new Color(66, 110, 155, 44);
        else if(metrics.weatherType == WeatherType.FOG) tint = new Color(226, 229, 220, 76);
        else if(metrics.weatherType == WeatherType.DROUGHT) tint = new Color(221, 145, 68, 50);
        else tint = new Color(255, 241, 174, 20);
        graphics.setColor(tint);
        graphics.fillRect(0, 0, width, height);
        if(metrics.dayPhase == DayPhase.NIGHT) {
            graphics.setColor(new Color(19, 32, 62, 82));
            graphics.fillRect(0, 0, width, height);
        }
        else if(metrics.dayPhase == DayPhase.DAWN || metrics.dayPhase == DayPhase.DUSK) {
            graphics.setColor(new Color(232, 122, 54, 42));
            graphics.fillRect(0, 0, width, height);
        }
    }

    private void drawMetricPanel(Graphics2D graphics, int targetWidth,
                                 RenderFrame frame)
    {
        RenderMetrics metrics = frame.metrics;
        int panelWidth = Math.min(Math.max(230, targetWidth / 3), 330);
        int panelHeight = 214;
        int left = 8;
        int top = 8;
        graphics.setColor(new Color(255, 249, 225, 205));
        graphics.fillRoundRect(left, top, panelWidth, panelHeight, 8, 8);
        graphics.setColor(new Color(70, 57, 37, 150));
        graphics.drawRoundRect(left, top, panelWidth, panelHeight, 8, 8);
        Font old = graphics.getFont();
        graphics.setFont(old.deriveFont(Font.BOLD, 12.0f));
        graphics.setColor(new Color(45, 37, 28));
        graphics.drawString("Step " + frame.step + "  " + metrics.timeText,
                            left + 10, top + 18);
        graphics.setFont(old.deriveFont(11.0f));
        drawMetricBar(graphics, left + 10, top + 32, panelWidth - 20,
                      "Grass", metrics.grassPercent, new Color(94, 151, 62));
        drawMetricBar(graphics, left + 10, top + 48, panelWidth - 20,
                      "Disease", metrics.diseasePercent, new Color(210, 35, 42));
        drawMetricBar(graphics, left + 10, top + 64, panelWidth - 20,
                      "Survival", metrics.averageSurvival, new Color(72, 137, 190));
        drawMetricBar(graphics, left + 10, top + 80, panelWidth - 20,
                      "Stamina", metrics.averageStamina, new Color(222, 178, 53));
        int[] actions = new int[ActionType.values().length];
        for(RenderAnimalSnapshot animal : frame.animals) {
            if(animal.actionType != null) actions[animal.actionType.ordinal()]++;
        }
        drawFitted(graphics, "Display outcomes: " + BehaviorVisuals.compactSummary(actions),
                   left + 10, top + 104, panelWidth - 20);
        drawFitted(graphics, "Signals: " + metrics.signals,
                   left + 10, top + 120, panelWidth - 20);
        String[] provenance = provenanceLines(frame);
        drawFitted(graphics, provenance[0], left + 10, top + 136, panelWidth - 20);
        drawFitted(graphics, provenance[1], left + 10, top + 152, panelWidth - 20);
        drawFitted(graphics, provenance[2], left + 10, top + 168, panelWidth - 20);
        drawFitted(graphics, provenance[3], left + 10, top + 184, panelWidth - 20);
        drawFitted(graphics, provenance[4], left + 10, top + 200, panelWidth - 20);
        graphics.setFont(old);
    }

    static String[] provenanceLines(RenderFrame frame)
    {
        RenderProvenance value = frame == null || frame.provenance == null
            ? RenderProvenance.unavailable() : frame.provenance;
        return new String[] {
            "Build / class: " + value.getBuildId() + " / " +
                value.getCandidateClass(),
            "Main / test manifest: " + value.getShortSourceHash() + " / " +
                value.getShortTestSourceHash(),
            "Config: " + value.getShortConfigurationHash(),
            "Ecology / terrain seed: " + value.getEcologyRandomSeed() + " / " +
                value.getTerrainSeed(),
            "Terrain: " + value.getTerrainGeneratorVersion() + " fp " +
                value.getShortTerrainFingerprint()
        };
    }

    private void drawMetricBar(Graphics2D graphics, int left, int top,
                               int width, String label, int percent, Color color)
    {
        int labelWidth = 54;
        int barWidth = Math.max(1, width - labelWidth - 34);
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.setColor(new Color(45, 37, 28));
        graphics.drawString(label, left, top + 9);
        graphics.setColor(new Color(64, 58, 45, 62));
        graphics.fillRoundRect(left + labelWidth, top, barWidth, 10, 5, 5);
        graphics.setColor(color);
        graphics.fillRoundRect(left + labelWidth, top,
            Math.max(1, barWidth * Math.min(100, Math.max(0, percent)) / 100),
            10, 5, 5);
        String value = percent + "%";
        graphics.setColor(new Color(45, 37, 28));
        graphics.drawString(value, left + width - metrics.stringWidth(value), top + 9);
    }

    private void drawFitted(Graphics2D graphics, String text, int x,
                            int baseline, int maxWidth)
    {
        String fitted = text;
        FontMetrics metrics = graphics.getFontMetrics();
        while(fitted.length() > 3 && metrics.stringWidth(fitted) > maxWidth) {
            fitted = fitted.substring(0, fitted.length() - 4) + "...";
        }
        graphics.drawString(fitted, x, baseline);
    }
}
