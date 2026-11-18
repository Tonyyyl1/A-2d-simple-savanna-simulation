import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.Map;

/**
 * Shared ordinary field rendering helpers.
 *
 * SimulatorView owns Swing layers, viewport transforms and overlays. This
 * renderer owns the reusable animal marker drawing so future snapshot tools can
 * use the same geometry and status marks as the live view.
 */
public class FieldRenderer
{
    private static final Color UNKNOWN_COLOR = Color.gray;
    private static final Color INFECTED_COLOR = new Color(210, 35, 42);
    private static final Color THIRST_COLOR = new Color(43, 136, 210);

    private final Map<String, Color> colors;

    public FieldRenderer(Map<String, Color> colors)
    {
        this.colors = colors;
    }

    public void drawUnknownMark(Graphics2D graphics, int row, int col,
                                VisualGridGeometry geometry)
    {
        Rectangle cell = geometry.cellBounds(row, col);
        graphics.setColor(UNKNOWN_COLOR);
        graphics.fillRect(cell.x, cell.y, Math.max(1, cell.width - 1),
                          Math.max(1, cell.height - 1));
    }

    public void drawAnimal(Graphics2D graphics, Location location,
                           SavannahAnimal animal,
                           VisualGridGeometry geometry)
    {
        VisualFootprint.OrdinaryMarker marker =
            VisualFootprint.ordinaryMarker(location, geometry);
        int left = marker.left;
        int top = marker.top;
        int cellWidth = marker.cellWidth;
        int cellHeight = marker.cellHeight;
        int symbolSize = marker.symbolSize;
        int symbolLeft = marker.symbolLeft;
        int symbolTop = marker.symbolTop;
        Color color = getColor(animal.getProfile().getName(), animal);

        graphics.setColor(new Color(20, 18, 12, 60));
        graphics.fillOval(symbolLeft + 1, symbolTop + 2,
                          symbolSize, Math.max(2, symbolSize / 2));
        graphics.setColor(color);
        if(animal.getProfile().isPredator()) {
            Polygon triangle = new Polygon();
            triangle.addPoint(symbolLeft + symbolSize / 2, symbolTop);
            triangle.addPoint(symbolLeft, symbolTop + symbolSize);
            triangle.addPoint(symbolLeft + symbolSize, symbolTop + symbolSize);
            graphics.fillPolygon(triangle);
            graphics.setColor(new Color(25, 25, 25, 120));
            graphics.drawPolygon(triangle);
        }
        else {
            graphics.fillOval(symbolLeft, symbolTop, symbolSize, symbolSize);
            graphics.setColor(new Color(25, 25, 25, 115));
            graphics.drawOval(symbolLeft, symbolTop, symbolSize, symbolSize);
        }

        if(animal.isSurvivalCritical()) {
            graphics.setColor(new Color(255, 145, 42, 210));
            graphics.drawOval(symbolLeft - 1, symbolTop - 1,
                              symbolSize + 2, symbolSize + 2);
        }

        if(animal.getStaminaStage() == StaminaStage.LOW && cellHeight >= 5) {
            int barWidth = Math.max(2, (int)Math.round(cellWidth *
                (animal.getStaminaPercent() / 100.0)));
            graphics.setColor(new Color(35, 35, 35, 120));
            graphics.fillRect(left, top + cellHeight - 2, cellWidth, 2);
            graphics.setColor(new Color(245, 211, 76, 230));
            graphics.fillRect(left, top + cellHeight - 2, barWidth, 2);
        }

        if(animal.isThirsty()) {
            int dropSize = Math.max(3, symbolSize / 3);
            graphics.setColor(THIRST_COLOR);
            graphics.fillOval(symbolLeft, symbolTop, dropSize, dropSize + 1);
        }

        if(animal.isInfected()) {
            graphics.setColor(INFECTED_COLOR);
            int dotSize = Math.max(2, symbolSize / 3);
            graphics.fillOval(symbolLeft + symbolSize - dotSize,
                              symbolTop, dotSize, dotSize);
        }
    }

    private Color getColor(String speciesName, SavannahAnimal animal)
    {
        Color color = colors == null ? null : colors.get(speciesName);
        if(color != null) {
            return color;
        }
        return animal == null ? UNKNOWN_COLOR : animal.getProfile().getColor();
    }
}
