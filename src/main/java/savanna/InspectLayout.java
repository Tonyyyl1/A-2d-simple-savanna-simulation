package savanna;

import java.awt.Rectangle;

/** Immutable viewport input for the detailed inspect renderer. */
public final class InspectLayout
{
    private final Rectangle viewportCells;

    public InspectLayout(Rectangle viewportCells)
    {
        if(viewportCells == null || viewportCells.width <= 0 ||
           viewportCells.height <= 0) {
            throw new IllegalArgumentException("Inspect layout needs a non-empty viewport.");
        }
        this.viewportCells = new Rectangle(viewportCells);
    }

    public Rectangle getViewportCells()
    {
        return new Rectangle(viewportCells);
    }
}
