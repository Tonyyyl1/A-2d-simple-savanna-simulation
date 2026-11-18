package savanna;

import java.awt.Rectangle;

/**
 * Shared placement policy for ordinary markers near visual water.
 *
 * A shoreline animal remains represented at its real field cell.  The
 * renderer clips only the part of the marker that would cover water, leaving
 * the land-side pixels visible instead of hiding the whole animal.
 */
public final class SafeMarkerPlacement
{
    private SafeMarkerPlacement() {}

    public static boolean isLandPixel(VisualGridGeometry geometry,
                                      TerrainMap terrain,
                                      int x, int y)
    {
        return geometry != null && terrain != null &&
               !geometry.isWaterPixel(terrain, x, y);
    }

    public static boolean hasVisibleLandPixel(Location location,
                                              SavannahAnimal animal,
                                              VisualGridGeometry geometry,
                                              TerrainMap terrain)
    {
        if(location == null || geometry == null || terrain == null ||
           !terrain.isPassable(location)) {
            return false;
        }
        VisualFootprint.OrdinaryMarker marker =
            VisualFootprint.ordinaryMarker(location, geometry);
        Rectangle bounds = safeBounds(marker, animal);
        for(int y = bounds.y; y <= bounds.y + bounds.height; y++) {
            for(int x = bounds.x; x <= bounds.x + bounds.width; x++) {
                if(isLandPixel(geometry, terrain, x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Rectangle safeBounds(VisualFootprint.OrdinaryMarker marker,
                                       SavannahAnimal animal)
    {
        Rectangle bounds = VisualFootprint.ordinaryBounds(marker, animal);
        // At very small canvas sizes a rounded shoreline cell can map to a
        // single water pixel. Allow the marker to settle into the nearest
        // land-side pixels while retaining the true field location.
        return new Rectangle(bounds.x - 2, bounds.y - 2,
                             bounds.width + 4, bounds.height + 4);
    }
}
