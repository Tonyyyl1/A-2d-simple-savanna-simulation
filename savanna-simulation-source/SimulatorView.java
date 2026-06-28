import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A graphical view of the savanna simulation grid.
 */
public class SimulatorView extends JFrame
{
    private static final Color EMPTY_COLOR = new Color(239, 245, 230);
    private static final Color UNKNOWN_COLOR = Color.gray;
    private static final Color INFECTED_COLOR = new Color(210, 35, 42);
    private static final String STEP_PREFIX = "Step: ";
    private static final String POPULATION_PREFIX = "Population: ";

    private final JLabel stepLabel;
    private final JLabel environment;
    private final JLabel population;
    private final JButton pauseButton;
    private final JButton stopButton;
    private final FieldView fieldView;
    private final Map<String, Color> colors;
    private final FieldStats stats;
    private SimulationControlHandler controlHandler;

    public SimulatorView(int height, int width)
    {
        stats = new FieldStats();
        colors = new LinkedHashMap<>();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            setColor(factory.getProfile().getName(), factory.getProfile().getColor());
        }

        setTitle("African Savanna Simulation");
        stepLabel = new JLabel(STEP_PREFIX, JLabel.CENTER);
        environment = new JLabel("", JLabel.CENTER);
        population = new JLabel(POPULATION_PREFIX, JLabel.CENTER);
        pauseButton = new JButton("Pause");
        stopButton = new JButton("Stop & Exit");

        setLocation(100, 50);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent event)
            {
                requestStopAndExit();
            }
        });

        fieldView = new FieldView(height, width);

        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 1));
        controlsPanel.add(pauseButton);
        controlsPanel.add(stopButton);

        JPanel stepPanel = new JPanel(new BorderLayout());
        stepPanel.add(stepLabel, BorderLayout.CENTER);
        stepPanel.add(controlsPanel, BorderLayout.EAST);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(stepPanel, BorderLayout.NORTH);
        topPanel.add(environment, BorderLayout.SOUTH);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(population, BorderLayout.NORTH);
        bottomPanel.add(createLegendPanel(), BorderLayout.SOUTH);

        Container contents = getContentPane();
        contents.add(topPanel, BorderLayout.NORTH);
        contents.add(fieldView, BorderLayout.CENTER);
        contents.add(bottomPanel, BorderLayout.SOUTH);
        pauseButton.addActionListener(event -> togglePaused());
        stopButton.addActionListener(event -> requestStopAndExit());
        pack();
        setVisible(true);
    }

    /**
     * Connect the window buttons to the running simulator.
     */
    public void setControlHandler(SimulationControlHandler controlHandler)
    {
        this.controlHandler = controlHandler;
    }

    /**
     * Update the pause button to match the simulator state.
     */
    public void setPaused(boolean paused)
    {
        pauseButton.setText(paused ? "Resume" : "Pause");
    }

    /**
     * Disable buttons after the simulation is stopping.
     */
    public void setStopping()
    {
        pauseButton.setEnabled(false);
        stopButton.setEnabled(false);
        stopButton.setText("Stopping...");
    }

    /**
     * Define a color to be used for a given species.
     */
    public void setColor(String speciesName, Color color)
    {
        colors.put(speciesName, color);
    }

    /**
     * Show the current status of the field.
     */
    public void showStatus(int step, Field field, SimulationContext context)
    {
        if(!isVisible()) {
            setVisible(true);
        }

        stepLabel.setText(STEP_PREFIX + step);
        if(context != null) {
            environment.setIcon(new SwatchIcon(context.getWeatherSystem()
                                                     .getCurrentWeather().getColor()));
            environment.setText(" " + context.getEnvironmentSummary(field));
        }

        stats.reset();
        fieldView.preparePaint();

        for(int row = 0; row < field.getDepth(); row++) {
            for(int col = 0; col < field.getWidth(); col++) {
                Animal animal = field.getAnimalAt(new Location(row, col));
                if(animal != null && animal.isAlive()) {
                    stats.incrementCount(animal);
                    if(animal instanceof SavannahAnimal) {
                        SavannahAnimal savannahAnimal = (SavannahAnimal)animal;
                        fieldView.drawAnimal(col, row,
                            getColor(savannahAnimal.getProfile().getName()),
                            savannahAnimal.isInfected());
                    }
                    else {
                        fieldView.drawMark(col, row, UNKNOWN_COLOR);
                    }
                }
                else {
                    fieldView.drawMark(col, row, EMPTY_COLOR);
                }
            }
        }
        stats.countFinished();

        population.setText(POPULATION_PREFIX + stats.getPopulationDetails(field));
        fieldView.repaint();
    }

    /**
     * Determine whether the simulation should continue to run.
     */
    public boolean isViable(Field field)
    {
        return stats.isViable(field);
    }

    private Color getColor(String speciesName)
    {
        Color color = colors.get(speciesName);
        return color == null ? UNKNOWN_COLOR : color;
    }

    private void togglePaused()
    {
        if(controlHandler != null) {
            controlHandler.togglePaused();
        }
    }

    private void requestStopAndExit()
    {
        if(controlHandler != null) {
            controlHandler.requestStopAndExit();
        }
        else {
            dispose();
        }
    }

    private JPanel createLegendPanel()
    {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            SpeciesProfile profile = factory.getProfile();
            panel.add(new JLabel(profile.getName(), new SwatchIcon(profile.getColor()),
                                 JLabel.LEFT));
        }
        panel.add(new JLabel("Weather", new SwatchIcon(WeatherType.RAIN.getColor()),
                             JLabel.LEFT));
        panel.add(new JLabel("Disease", new DiseaseIcon(), JLabel.LEFT));
        return panel;
    }

    /**
     * Custom component that displays the field.
     */
    private class FieldView extends JPanel
    {
        private static final int GRID_VIEW_SCALING_FACTOR = 8;

        private final int gridWidth;
        private final int gridHeight;
        private int xScale;
        private int yScale;
        private Dimension size;
        private Graphics graphics;
        private Image fieldImage;

        public FieldView(int height, int width)
        {
            gridHeight = height;
            gridWidth = width;
            size = new Dimension(0, 0);
        }

        public Dimension getPreferredSize()
        {
            return new Dimension(gridWidth * GRID_VIEW_SCALING_FACTOR,
                                 gridHeight * GRID_VIEW_SCALING_FACTOR);
        }

        public void preparePaint()
        {
            if(!size.equals(getSize())) {
                size = getSize();
                fieldImage = fieldView.createImage(size.width, size.height);
                graphics = fieldImage.getGraphics();

                xScale = size.width / gridWidth;
                if(xScale < 1) {
                    xScale = GRID_VIEW_SCALING_FACTOR;
                }
                yScale = size.height / gridHeight;
                if(yScale < 1) {
                    yScale = GRID_VIEW_SCALING_FACTOR;
                }
            }
        }

        public void drawMark(int x, int y, Color color)
        {
            graphics.setColor(color);
            graphics.fillRect(x * xScale, y * yScale, xScale - 1, yScale - 1);
        }

        public void drawAnimal(int x, int y, Color color, boolean infected)
        {
            int left = x * xScale;
            int top = y * yScale;
            int width = Math.max(1, xScale - 1);
            int height = Math.max(1, yScale - 1);
            graphics.setColor(EMPTY_COLOR);
            graphics.fillRect(left, top, width, height);

            graphics.setColor(color);
            graphics.fillRect(left, top, width, height);

            if(infected) {
                graphics.setColor(INFECTED_COLOR);
                int dotSize = Math.max(2, Math.min(width, height) / 3);
                graphics.fillOval(left + width - dotSize, top, dotSize, dotSize);
            }
        }

        public void paintComponent(Graphics graphics)
        {
            if(fieldImage != null) {
                Dimension currentSize = getSize();
                if(size.equals(currentSize)) {
                    graphics.drawImage(fieldImage, 0, 0, null);
                }
                else {
                    graphics.drawImage(fieldImage, 0, 0,
                        currentSize.width, currentSize.height, null);
                }
            }
        }
    }

    private static class SwatchIcon implements Icon
    {
        private final Color color;

        public SwatchIcon(Color color)
        {
            this.color = color;
        }

        public int getIconWidth()
        {
            return 14;
        }

        public int getIconHeight()
        {
            return 14;
        }

        public void paintIcon(java.awt.Component component, Graphics graphics, int x, int y)
        {
            graphics.setColor(color);
            graphics.fillRect(x, y, 13, 13);
            graphics.setColor(Color.darkGray);
            graphics.drawRect(x, y, 13, 13);
        }
    }

    private static class DiseaseIcon implements Icon
    {
        public int getIconWidth()
        {
            return 14;
        }

        public int getIconHeight()
        {
            return 14;
        }

        public void paintIcon(java.awt.Component component, Graphics graphics, int x, int y)
        {
            graphics.setColor(Color.lightGray);
            graphics.fillRect(x, y, 13, 13);
            graphics.setColor(INFECTED_COLOR);
            graphics.fillOval(x + 4, y + 4, 6, 6);
            graphics.setColor(Color.darkGray);
            graphics.drawRect(x, y, 13, 13);
        }
    }

}
