package savanna;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/** Pure heatmap painter: it reads only a frozen snapshot and frame geometry. */
public final class HeatmapRenderer
{
    private HeatmapRenderer()
    {
    }

    public static void render(Graphics2D graphics, HeatmapSnapshot snapshot,
                              TerrainMap terrain, VisualGridGeometry geometry,
                              Rectangle viewport,
                              SimulationEvent.EventType eventType)
    {
        render(graphics, snapshot == null ? null : snapshot.select(
            new HeatmapSelection(HeatmapWindow.WHOLE_RUN, eventType, null)),
            terrain, geometry, viewport);
    }

    public static void render(Graphics2D graphics, HeatmapSelectionFacts facts,
                              TerrainMap terrain, VisualGridGeometry geometry,
                              Rectangle viewport)
    {
        if(graphics == null || facts == null || terrain == null ||
           geometry == null || viewport == null) {
            return;
        }
        int maximum = facts.getMaximum();
        if(maximum <= 0) {
            return;
        }
        Rectangle visible = viewport.intersection(new Rectangle(0, 0,
            terrain.getWidth(), terrain.getDepth()));
        Composite oldComposite = graphics.getComposite();
        try {
            for(int row = visible.y; row < visible.y + visible.height; row++) {
                for(int col = visible.x; col < visible.x + visible.width; col++) {
                    Location location = new Location(row, col);
                    if(!terrain.isPassable(location)) {
                        continue;
                    }
                    int count = facts.getCountAt(location);
                    if(count <= 0) {
                        continue;
                    }
                    double ratio = Math.min(1.0, (double)count / maximum);
                    if(facts.getSelection().getEventType() ==
                       SimulationEvent.EventType.DRINK) {
                        ratio = Math.sqrt(ratio);
                    }
                    Rectangle cell = geometry.cellBounds(row, col);
                    graphics.setComposite(AlphaComposite.SrcOver.derive(
                        facts.getSelection().getEventType() ==
                            SimulationEvent.EventType.DRINK
                            ? (float)(0.32 + ratio * 0.55)
                            : (float)(0.18 + ratio * 0.48)));
                    graphics.setColor(colorFor(ratio));
                    graphics.fillRect(cell.x, cell.y, cell.width, cell.height);
                }
            }
        }
        finally {
            graphics.setComposite(oldComposite);
        }
    }

    private static Color colorFor(double ratio)
    {
        if(ratio < 0.45) {
            return blend(new Color(255, 231, 94), new Color(246, 145, 45),
                         ratio / 0.45);
        }
        return blend(new Color(246, 145, 45), new Color(203, 35, 42),
                     (ratio - 0.45) / 0.55);
    }

    private static Color blend(Color first, Color second, double ratio)
    {
        double clamped = Math.max(0.0, Math.min(1.0, ratio));
        return new Color((int)Math.round(first.getRed() +
                             (second.getRed() - first.getRed()) * clamped),
                         (int)Math.round(first.getGreen() +
                             (second.getGreen() - first.getGreen()) * clamped),
                         (int)Math.round(first.getBlue() +
                             (second.getBlue() - first.getBlue()) * clamped));
    }
}
