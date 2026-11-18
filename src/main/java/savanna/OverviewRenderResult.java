package savanna;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Completed ordinary rendering result with separately composable layers. */
public final class OverviewRenderResult
{
    private final BufferedImage terrainImage;
    private final BufferedImage overlayImage;
    private final int drawnAnimalCount;
    private final Map<String, Integer> drawnBySpecies;
    private final int priorityOverlayCount;

    public OverviewRenderResult(BufferedImage terrainImage,
                                BufferedImage overlayImage,
                                int drawnAnimalCount)
    {
        this(terrainImage, overlayImage, drawnAnimalCount,
             Collections.<String, Integer>emptyMap(), 0);
    }

    public OverviewRenderResult(BufferedImage terrainImage,
                                BufferedImage overlayImage,
                                int drawnAnimalCount,
                                Map<String, Integer> drawnBySpecies,
                                int priorityOverlayCount)
    {
        this.terrainImage = terrainImage;
        this.overlayImage = overlayImage;
        this.drawnAnimalCount = Math.max(0, drawnAnimalCount);
        this.drawnBySpecies = Collections.unmodifiableMap(
            new LinkedHashMap<>(drawnBySpecies));
        this.priorityOverlayCount = Math.max(0, priorityOverlayCount);
    }

    public BufferedImage getTerrainImage() { return terrainImage; }
    public BufferedImage getOverlayImage() { return overlayImage; }
    public int getDrawnAnimalCount() { return drawnAnimalCount; }
    public Map<String, Integer> getDrawnBySpecies() { return drawnBySpecies; }
    public int getPriorityOverlayCount() { return priorityOverlayCount; }

    public BufferedImage getImage()
    {
        BufferedImage combined = new BufferedImage(terrainImage.getWidth(),
            terrainImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = combined.createGraphics();
        try {
            graphics.drawImage(terrainImage, 0, 0, null);
            graphics.drawImage(overlayImage, 0, 0, null);
        }
        finally {
            graphics.dispose();
        }
        return combined;
    }
}
