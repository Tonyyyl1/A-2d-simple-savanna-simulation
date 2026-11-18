package savanna;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;

/**
 * Owns the ordinary overview frame while it is built and after it is
 * published. A completed frame is immutable from this class's point of view:
 * its graphics context is disposed before the frame can become current.
 */
public final class OverviewFrameBuffer
{
    private CompletedFrame currentFrame;

    /**
     * Starts a private, unpublished overlay image. The caller owns the
     * returned writable frame until it completes or aborts it.
     */
    public WritableFrame beginFrame(long generation, int step, int width,
                                    int height, BufferedImage terrainImage,
                                    TerrainMap terrainMap,
                                    VisualGridGeometry geometry,
                                    VisualWaterMask visualWaterMask)
    {
        return new WritableFrame(generation, step, width, height, terrainImage,
                                 terrainMap, geometry, visualWaterMask);
    }

    /**
     * Seals layers produced by an external renderer. The renderer has already
     * disposed its Graphics2D, so no second overlay allocation or full-image
     * copy is needed before the EDT commit.
     */
    public CompletedFrame completedFrame(long generation, int step, int width,
                                         int height, BufferedImage terrainImage,
                                         TerrainMap terrainMap,
                                         VisualGridGeometry geometry,
                                         VisualWaterMask visualWaterMask,
                                         BufferedImage overlayImage,
                                         int drawnAnimalCount,
                                         boolean overlayComplete)
    {
        if(width <= 0 || height <= 0 || terrainImage == null ||
           overlayImage == null || geometry == null ||
           terrainImage.getWidth() != width ||
           terrainImage.getHeight() != height ||
           overlayImage.getWidth() != width ||
           overlayImage.getHeight() != height ||
           geometry.pixelWidth() != width ||
           geometry.pixelHeight() != height) {
            throw new IllegalArgumentException(
                "Completed renderer layers must match the target dimensions.");
        }
        return new CompletedFrame(generation, step, width, height,
            terrainImage, terrainMap, geometry, visualWaterMask, overlayImage,
            Math.max(0, drawnAnimalCount), overlayComplete);
    }

    /**
     * Publishes one completed frame. The front reference belongs to the EDT
     * so paints can observe either the previous complete frame or this one.
     */
    public void commit(CompletedFrame frame)
    {
        requireEdt("commit");
        if(frame == null || !frame.isComplete() || !frame.isOverlayComplete()) {
            throw new IllegalArgumentException(
                "Only a complete frame with a finished overlay can be committed.");
        }
        if(currentFrame != null &&
           frame.getGeneration() < currentFrame.getGeneration()) {
            throw new IllegalArgumentException(
                "An expired frame generation cannot replace the front frame.");
        }
        currentFrame = frame;
    }

    /**
     * Returns the frame to be used for one paint pass. Callers should retain
     * this one local reference for the whole pass.
     */
    public CompletedFrame currentFrame()
    {
        requireEdt("read the front frame");
        return currentFrame;
    }

    private static void requireEdt(String action)
    {
        if(!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                "The EDT must " + action + " for OverviewFrameBuffer.");
        }
    }

    /**
     * An unpublished overlay image. It cannot be committed directly and it
     * releases its Graphics2D on both success and failure paths.
     */
    public static final class WritableFrame
    {
        private final long generation;
        private final int step;
        private final int width;
        private final int height;
        private final BufferedImage terrainImage;
        private final TerrainMap terrainMap;
        private final VisualGridGeometry geometry;
        private final VisualWaterMask visualWaterMask;
        private final BufferedImage overlayImage;
        private Graphics2D graphics;
        private boolean closed;

        private WritableFrame(long generation, int step, int width, int height,
                              BufferedImage terrainImage, TerrainMap terrainMap,
                              VisualGridGeometry geometry,
                              VisualWaterMask visualWaterMask)
        {
            if(width <= 0 || height <= 0 || terrainImage == null ||
               geometry == null || terrainImage.getWidth() != width ||
               terrainImage.getHeight() != height ||
               geometry.pixelWidth() != width ||
               geometry.pixelHeight() != height) {
                throw new IllegalArgumentException(
                    "A frame needs terrain and geometry with the target dimensions.");
            }
            this.generation = generation;
            this.step = step;
            this.width = width;
            this.height = height;
            this.terrainImage = terrainImage;
            this.terrainMap = terrainMap;
            this.geometry = geometry;
            this.visualWaterMask = visualWaterMask;
            overlayImage = new BufferedImage(width, height,
                                             BufferedImage.TYPE_INT_ARGB);
            graphics = overlayImage.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                      RenderingHints.VALUE_ANTIALIAS_ON);
        }

        public Graphics2D graphics()
        {
            if(closed || graphics == null) {
                throw new IllegalStateException(
                    "A completed or aborted frame has no writable graphics.");
            }
            return graphics;
        }

        public CompletedFrame complete(int drawnAnimalCount,
                                       boolean overlayComplete)
        {
            if(closed) {
                throw new IllegalStateException("This frame has already been closed.");
            }
            closeGraphics();
            return new CompletedFrame(generation, step, width, height,
                                      terrainImage, terrainMap, geometry,
                                      visualWaterMask, overlayImage,
                                      Math.max(0, drawnAnimalCount),
                                      overlayComplete);
        }

        public void abort()
        {
            if(!closed) {
                closeGraphics();
            }
        }

        private void closeGraphics()
        {
            graphics.dispose();
            graphics = null;
            closed = true;
        }
    }

    /**
     * A read-only ordinary overview frame. No Graphics2D is retained after
     * construction, which prevents an already-published image being cleared
     * or reused as a back buffer.
     */
    public static final class CompletedFrame
    {
        private final long generation;
        private final int step;
        private final int width;
        private final int height;
        private final BufferedImage terrainImage;
        private final TerrainMap terrainMap;
        private final VisualGridGeometry geometry;
        private final VisualWaterMask visualWaterMask;
        private final BufferedImage overlayImage;
        private final int drawnAnimalCount;
        private final boolean overlayComplete;

        private CompletedFrame(long generation, int step, int width, int height,
                               BufferedImage terrainImage, TerrainMap terrainMap,
                               VisualGridGeometry geometry,
                               VisualWaterMask visualWaterMask,
                               BufferedImage overlayImage,
                               int drawnAnimalCount,
                               boolean overlayComplete)
        {
            this.generation = generation;
            this.step = step;
            this.width = width;
            this.height = height;
            this.terrainImage = terrainImage;
            this.terrainMap = terrainMap;
            this.geometry = geometry;
            this.visualWaterMask = visualWaterMask;
            this.overlayImage = overlayImage;
            this.drawnAnimalCount = drawnAnimalCount;
            this.overlayComplete = overlayComplete;
        }

        public long getGeneration()
        {
            return generation;
        }

        public int getStep()
        {
            return step;
        }

        public int getWidth()
        {
            return width;
        }

        public int getHeight()
        {
            return height;
        }

        public BufferedImage getTerrainImage()
        {
            return terrainImage;
        }

        public TerrainMap getTerrainMap()
        {
            return terrainMap;
        }

        public VisualGridGeometry getGeometry()
        {
            return geometry;
        }

        public VisualWaterMask getVisualWaterMask()
        {
            return visualWaterMask;
        }

        public BufferedImage getOverlayImage()
        {
            return overlayImage;
        }

        public int getDrawnAnimalCount()
        {
            return drawnAnimalCount;
        }

        public boolean isOverlayComplete()
        {
            return overlayComplete;
        }

        private boolean isComplete()
        {
            return terrainImage != null && overlayImage != null &&
                   terrainImage.getWidth() == width &&
                   terrainImage.getHeight() == height &&
                   overlayImage.getWidth() == width &&
                   overlayImage.getHeight() == height && geometry != null &&
                   geometry.pixelWidth() == width &&
                   geometry.pixelHeight() == height;
        }
    }
}
