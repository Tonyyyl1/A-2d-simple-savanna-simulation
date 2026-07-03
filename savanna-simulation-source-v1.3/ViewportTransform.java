import java.awt.Graphics2D;

/**
 * Pan and zoom state for rendering the field image.
 */
public class ViewportTransform
{
    private static final double MIN_ZOOM = 1.0;
    private static final double MAX_ZOOM = 8.0;

    private double zoom;
    private double offsetX;
    private double offsetY;

    public ViewportTransform()
    {
        zoom = 1.0;
        offsetX = 0.0;
        offsetY = 0.0;
    }

    public double getZoom()
    {
        return zoom;
    }

    public double getOffsetX()
    {
        return offsetX;
    }

    public double getOffsetY()
    {
        return offsetY;
    }

    public boolean isOverview()
    {
        return zoom <= 1.01;
    }

    public void reset()
    {
        zoom = 1.0;
        offsetX = 0.0;
        offsetY = 0.0;
    }

    public void pan(double deltaX, double deltaY, int viewWidth, int viewHeight,
                    int contentWidth, int contentHeight)
    {
        offsetX += deltaX;
        offsetY += deltaY;
        clamp(viewWidth, viewHeight, contentWidth, contentHeight);
    }

    public void zoomBy(double factor, int anchorX, int anchorY, int viewWidth,
                       int viewHeight, int contentWidth, int contentHeight)
    {
        double oldZoom = zoom;
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        if(Math.abs(zoom - oldZoom) < 0.0001) {
            return;
        }
        double worldX = (anchorX - offsetX) / oldZoom;
        double worldY = (anchorY - offsetY) / oldZoom;
        offsetX = anchorX - worldX * zoom;
        offsetY = anchorY - worldY * zoom;
        clamp(viewWidth, viewHeight, contentWidth, contentHeight);
    }

    public void apply(Graphics2D graphics)
    {
        graphics.translate(offsetX, offsetY);
        graphics.scale(zoom, zoom);
    }

    private void clamp(int viewWidth, int viewHeight, int contentWidth,
                       int contentHeight)
    {
        double scaledWidth = contentWidth * zoom;
        double scaledHeight = contentHeight * zoom;
        if(scaledWidth <= viewWidth) {
            offsetX = (viewWidth - scaledWidth) / 2.0;
        }
        else {
            offsetX = Math.max(viewWidth - scaledWidth, Math.min(0.0, offsetX));
        }
        if(scaledHeight <= viewHeight) {
            offsetY = (viewHeight - scaledHeight) / 2.0;
        }
        else {
            offsetY = Math.max(viewHeight - scaledHeight, Math.min(0.0, offsetY));
        }
        if(isOverview()) {
            offsetX = 0.0;
            offsetY = 0.0;
        }
    }
}
