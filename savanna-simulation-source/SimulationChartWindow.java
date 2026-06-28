import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Shows interval trend charts for the visual simulation.
 */
public class SimulationChartWindow extends JFrame
{
    private final SimulationChartPanel chartPanel;

    public SimulationChartWindow()
    {
        super("Savanna Simulation Charts");
        chartPanel = new SimulationChartPanel();
        add(new JScrollPane(chartPanel), BorderLayout.CENTER);
        pack();
        setLocation(1080, 50);
        setVisible(true);
    }

    public void clear()
    {
        SwingUtilities.invokeLater(() -> chartPanel.clear());
    }

    public void addSnapshot(StepReportRecorder.StepSnapshot snapshot)
    {
        SwingUtilities.invokeLater(() -> chartPanel.addSnapshot(snapshot));
    }

    private static class SimulationChartPanel extends JPanel
    {
        private static final int CHART_HEIGHT = 145;
        private static final int LEFT_MARGIN = 68;
        private static final int RIGHT_MARGIN = 18;
        private static final int TOP_MARGIN = 24;
        private static final int BOTTOM_MARGIN = 28;
        private static final Color AXIS_COLOR = new Color(90, 90, 90);
        private static final Color GRID_COLOR = new Color(224, 224, 224);

        private final List<ChartPoint> points;
        private final Map<String, Color> speciesColors;

        SimulationChartPanel()
        {
            points = new ArrayList<>();
            speciesColors = new LinkedHashMap<>();
            for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
                SpeciesProfile profile = factory.getProfile();
                speciesColors.put(profile.getName(), profile.getColor());
            }
            setPreferredSize(new Dimension(760, CHART_HEIGHT * 5));
            setBackground(Color.white);
        }

        void clear()
        {
            points.clear();
            repaint();
        }

        void addSnapshot(StepReportRecorder.StepSnapshot snapshot)
        {
            points.add(new ChartPoint(snapshot));
            repaint();
        }

        protected void paintComponent(Graphics graphics)
        {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D)graphics;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);

            drawPopulationChart(g2, 0);
            drawSingleMetricChart(g2, 1, "Diseased animals", new Color(198, 48, 55),
                                  point -> point.diseased, false);
            drawSingleMetricChart(g2, 2, "Average stamina (%)", new Color(48, 110, 190),
                                  point -> point.averageStamina, true);
            drawSingleMetricChart(g2, 3, "Average survival (%)", new Color(38, 145, 90),
                                  point -> point.averageSurvival, true);
            drawSingleMetricChart(g2, 4, "Grass level (%)", new Color(107, 157, 45),
                                  point -> point.grassPercent, true);
        }

        private void drawPopulationChart(Graphics2D g2, int index)
        {
            int top = index * CHART_HEIGHT;
            int max = 1;
            for(ChartPoint point : points) {
                for(Integer count : point.speciesCounts.values()) {
                    max = Math.max(max, count);
                }
            }
            drawChartFrame(g2, top, "Species population", max);
            for(String species : speciesColors.keySet()) {
                drawSeries(g2, top, max, speciesColors.get(species),
                           point -> point.speciesCounts.getOrDefault(species, 0));
            }
            drawLegend(g2, top, speciesColors);
        }

        private void drawSingleMetricChart(Graphics2D g2, int index, String title,
                                           Color color, MetricReader reader,
                                           boolean fixedPercentScale)
        {
            int top = index * CHART_HEIGHT;
            int max = fixedPercentScale ? 100 : 1;
            if(!fixedPercentScale) {
                for(ChartPoint point : points) {
                    max = Math.max(max, reader.read(point));
                }
            }
            drawChartFrame(g2, top, title, max);
            drawSeries(g2, top, max, color, reader);
        }

        private void drawChartFrame(Graphics2D g2, int top, String title, int max)
        {
            int width = getWidth();
            int chartLeft = LEFT_MARGIN;
            int chartRight = width - RIGHT_MARGIN;
            int chartTop = top + TOP_MARGIN;
            int chartBottom = top + CHART_HEIGHT - BOTTOM_MARGIN;

            g2.setColor(Color.black);
            g2.drawString(title, 10, top + 17);

            g2.setColor(GRID_COLOR);
            for(int line = 0; line <= 4; line++) {
                int y = chartTop + (chartBottom - chartTop) * line / 4;
                g2.drawLine(chartLeft, y, chartRight, y);
            }

            g2.setColor(AXIS_COLOR);
            g2.drawRect(chartLeft, chartTop, chartRight - chartLeft,
                        chartBottom - chartTop);
            g2.drawString(String.valueOf(max), 10, chartTop + 4);
            g2.drawString("0", 10, chartBottom + 4);
            if(!points.isEmpty()) {
                g2.drawString("Step " + points.get(0).step, chartLeft, top + CHART_HEIGHT - 7);
                String last = "Step " + points.get(points.size() - 1).step;
                FontMetrics metrics = g2.getFontMetrics();
                g2.drawString(last, chartRight - metrics.stringWidth(last),
                              top + CHART_HEIGHT - 7);
            }
        }

        private void drawLegend(Graphics2D g2, int top, Map<String, Color> colors)
        {
            int x = LEFT_MARGIN;
            int y = top + 17;
            for(Map.Entry<String, Color> entry : colors.entrySet()) {
                g2.setColor(entry.getValue());
                g2.fillRect(x, y - 9, 10, 10);
                g2.setColor(Color.black);
                g2.drawString(entry.getKey(), x + 14, y);
                x += 92;
            }
        }

        private void drawSeries(Graphics2D g2, int top, int max, Color color,
                                MetricReader reader)
        {
            if(points.size() < 2) {
                return;
            }
            int width = getWidth();
            int chartLeft = LEFT_MARGIN;
            int chartRight = width - RIGHT_MARGIN;
            int chartTop = top + TOP_MARGIN;
            int chartBottom = top + CHART_HEIGHT - BOTTOM_MARGIN;
            int chartWidth = chartRight - chartLeft;
            int chartHeight = chartBottom - chartTop;

            g2.setColor(color);
            g2.setStroke(new BasicStroke(2.0f));
            int previousX = 0;
            int previousY = 0;
            for(int index = 0; index < points.size(); index++) {
                ChartPoint point = points.get(index);
                int x = chartLeft + chartWidth * index / Math.max(1, points.size() - 1);
                int value = Math.max(0, reader.read(point));
                int y = chartBottom -
                    (int)Math.round((Math.min(value, max) / (double)Math.max(1, max)) *
                                    chartHeight);
                if(index > 0) {
                    g2.drawLine(previousX, previousY, x, y);
                }
                previousX = x;
                previousY = y;
            }
            g2.setStroke(new BasicStroke(1.0f));
        }
    }

    private interface MetricReader
    {
        int read(ChartPoint point);
    }

    private static class ChartPoint
    {
        final int step;
        final int grassPercent;
        final int diseased;
        final int averageStamina;
        final int averageSurvival;
        final Map<String, Integer> speciesCounts;

        ChartPoint(StepReportRecorder.StepSnapshot snapshot)
        {
            step = snapshot.getStep();
            grassPercent = snapshot.getGrassPercent();
            diseased = snapshot.getDiseased();
            averageStamina = snapshot.getAverageStamina();
            averageSurvival = snapshot.getAverageSurvival();
            speciesCounts = snapshot.getSpeciesTotals();
        }
    }
}
