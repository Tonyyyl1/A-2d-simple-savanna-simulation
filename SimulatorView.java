import java.awt.BorderLayout;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * A graphical view of the savanna simulation grid.
 */
public class SimulatorView extends JFrame
{
    private static final Color UNKNOWN_COLOR = Color.gray;
    private static final Color INFECTED_COLOR = new Color(210, 35, 42);
    private static final double HOLD_MS = 450.0;
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
    private final ViewInteractionController interactionController;
    private final AnimalIconSet iconSet;
    private SimulationControlHandler controlHandler;
    private SimulationDiagnostics.DiagnosticSnapshot previousSnapshot;
    private Field lastField;
    private SimulationContext lastContext;
    private List<SimulationEvent> lastEvents;
    private List<SceneDirector.Scene> storyboard;
    private List<SceneDirector.SceneActor> ambientActors;
    private int sceneIndex;
    private long sceneStartNanos;
    private long ambientStartNanos;
    private Timer sceneTimer;
    private boolean inspectMode;
    private boolean pauseBeforeInspect;

    public SimulatorView(int height, int width)
    {
        stats = new FieldStats();
        interactionController = new ViewInteractionController();
        iconSet = new AnimalIconSet();
        lastEvents = Collections.emptyList();
        storyboard = Collections.emptyList();
        ambientActors = Collections.emptyList();
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
        interactionController.addViewportChangeListener(
            new ViewInteractionController.ViewportChangeListener() {
                public void viewportChanged()
                {
                    updateInspectMode();
                }
            });
        interactionController.register(fieldView, fieldView);

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
        stopSceneTimer();
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

        lastField = field;
        lastContext = context;
        lastEvents = context == null ? Collections.emptyList() :
            new ArrayList<>(context.getRecentEvents());

        stepLabel.setText(STEP_PREFIX + step);
        if(context != null) {
            environment.setIcon(new SwatchIcon(context.getWeatherSystem()
                                                     .getCurrentWeather().getColor()));
            environment.setText(" " + context.getEnvironmentSummary(field));
        }

        stats.reset();
        TerrainMap terrainMap = context == null ? null : context.getTerrainMap();
        fieldView.preparePaint(terrainMap);
        fieldView.drawPopulationPressure(field);

        for(int row = 0; row < field.getDepth(); row++) {
            for(int col = 0; col < field.getWidth(); col++) {
                Animal animal = field.getAnimalAt(new Location(row, col));
                if(animal != null && animal.isAlive()) {
                    stats.incrementCount(animal);
                    if(animal instanceof SavannahAnimal) {
                        SavannahAnimal savannahAnimal = (SavannahAnimal)animal;
                        if(fieldView.shouldDrawAnimal(col, row, savannahAnimal)) {
                            fieldView.drawAnimal(col, row, savannahAnimal);
                        }
                    }
                    else {
                        fieldView.drawMark(col, row, UNKNOWN_COLOR);
                    }
                }
            }
        }
        stats.countFinished();
        if(context != null) {
            SimulationDiagnostics.DiagnosticSnapshot currentSnapshot =
                SimulationDiagnostics.capture(field, context);
            SimulationDiagnostics.DiagnosticSnapshot comparisonSnapshot =
                step == 0 ? null : previousSnapshot;
            fieldView.drawSystemOverlay(context, currentSnapshot,
                                        comparisonSnapshot);
            previousSnapshot = currentSnapshot;
        }

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

    public boolean isInspectMode()
    {
        return interactionController.isInspectMode();
    }

    private void updateInspectMode()
    {
        boolean shouldInspect = interactionController.isInspectMode();
        if(shouldInspect == inspectMode) {
            if(inspectMode) {
                rebuildStoryboard();
            }
            return;
        }
        if(shouldInspect) {
            enterInspectMode();
        }
        else {
            exitInspectMode();
        }
    }

    private void enterInspectMode()
    {
        inspectMode = true;
        pauseBeforeInspect = controlHandler != null && controlHandler.isPaused();
        if(controlHandler != null && !pauseBeforeInspect) {
            controlHandler.setPaused(true);
        }
        rebuildStoryboard();
    }

    private void exitInspectMode()
    {
        inspectMode = false;
        stopSceneTimer();
        storyboard = Collections.emptyList();
        ambientActors = Collections.emptyList();
        if(controlHandler != null && !pauseBeforeInspect) {
            controlHandler.setPaused(false);
        }
        fieldView.repaint();
    }

    private void rebuildStoryboard()
    {
        if(lastField == null || lastContext == null) {
            storyboard = Collections.emptyList();
            ambientActors = Collections.emptyList();
            stopSceneTimer();
            fieldView.repaint();
            return;
        }
        Rectangle viewport = fieldView.visibleCells();
        storyboard = SceneDirector.buildScene(lastEvents, lastField,
                                             lastContext.getTerrainMap(),
                                             viewport);
        ambientActors = SceneDirector.buildBehaviorActors(lastEvents, lastField,
                                                          viewport);
        sceneIndex = 0;
        sceneStartNanos = System.nanoTime();
        ambientStartNanos = sceneStartNanos;
        if(storyboard.isEmpty() && ambientActors.isEmpty()) {
            stopSceneTimer();
        }
        else {
            sceneTimer().restart();
        }
        fieldView.repaint();
    }

    private Timer sceneTimer()
    {
        if(sceneTimer == null) {
            sceneTimer = new Timer(33, event -> advanceStoryboard());
        }
        return sceneTimer;
    }

    private void stopSceneTimer()
    {
        if(sceneTimer != null) {
            sceneTimer.stop();
        }
    }

    private void advanceStoryboard()
    {
        if(storyboard.isEmpty()) {
            if(ambientActors.isEmpty()) {
                stopSceneTimer();
            }
            fieldView.repaint();
            return;
        }
        if(sceneIndex >= storyboard.size()) {
            if(ambientActors.isEmpty()) {
                stopSceneTimer();
            }
            fieldView.repaint();
            return;
        }
        double elapsedMs = (System.nanoTime() - sceneStartNanos) / 1_000_000.0;
        SceneDirector.Scene current = storyboard.get(sceneIndex);
        if(elapsedMs > current.durationMs + HOLD_MS) {
            sceneIndex++;
            sceneStartNanos = System.nanoTime();
            if(sceneIndex >= storyboard.size()) {
                stopSceneTimer();
            }
        }
        fieldView.repaint();
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
        panel.add(new JLabel("Predator", new PredatorIcon(), JLabel.LEFT));
        panel.add(new JLabel("Herbivore", new HerbivoreIcon(), JLabel.LEFT));
        panel.add(new JLabel("Survival pressure", new SurvivalIcon(), JLabel.LEFT));
        panel.add(new JLabel("Low stamina", new StaminaIcon(), JLabel.LEFT));
        return panel;
    }

    /**
     * Custom component that displays the field.
     */
    private class FieldView extends JPanel
        implements ViewInteractionController.SizeProvider
    {
        private static final int GRID_VIEW_SCALING_FACTOR = 10;
        private static final int AGGREGATE_CELL_SIZE = 8;

        private final int gridWidth;
        private final int gridHeight;
        private int xScale;
        private int yScale;
        private Dimension size;
        private TerrainMap terrainMap;
        private BufferedImage terrainImage;
        private BufferedImage animalImage;
        private Graphics2D animalGraphics;
        private int aggregateColumns;
        private int aggregateRows;
        private int[][] aggregatePopulation;

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

        public int viewWidth()
        {
            return getWidth();
        }

        public int viewHeight()
        {
            return getHeight();
        }

        public int contentWidth()
        {
            return Math.max(1, size.width);
        }

        public int contentHeight()
        {
            return Math.max(1, size.height);
        }

        public void preparePaint(TerrainMap currentTerrainMap)
        {
            Dimension currentSize = getSize();
            if(currentSize.width <= 0 || currentSize.height <= 0) {
                currentSize = getPreferredSize();
            }

            boolean sizeChanged = !size.equals(currentSize);
            boolean terrainChanged = terrainMap != currentTerrainMap;

            if(sizeChanged) {
                size = new Dimension(currentSize);
                xScale = Math.max(1, size.width / gridWidth);
                yScale = Math.max(1, size.height / gridHeight);
                animalImage = new BufferedImage(size.width, size.height,
                                                BufferedImage.TYPE_INT_ARGB);
                if(animalGraphics != null) {
                    animalGraphics.dispose();
                }
                animalGraphics = animalImage.createGraphics();
                animalGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                                RenderingHints.VALUE_ANTIALIAS_ON);
            }

            if(sizeChanged || terrainChanged || terrainImage == null) {
                terrainMap = currentTerrainMap;
                terrainImage = new BufferedImage(size.width, size.height,
                                                 BufferedImage.TYPE_INT_ARGB);
                Graphics2D terrainGraphics = terrainImage.createGraphics();
                if(terrainMap != null) {
                    terrainMap.drawBackground(terrainGraphics, size.width, size.height);
                }
                else {
                    terrainGraphics.setColor(TerrainType.OPEN_PLAIN.getColor());
                    terrainGraphics.fillRect(0, 0, size.width, size.height);
                }
                terrainGraphics.dispose();
            }

            clearAnimalLayer();
        }

        public void drawPopulationPressure(Field field)
        {
            aggregateColumns = (gridWidth + AGGREGATE_CELL_SIZE - 1) /
                               AGGREGATE_CELL_SIZE;
            aggregateRows = (gridHeight + AGGREGATE_CELL_SIZE - 1) /
                            AGGREGATE_CELL_SIZE;
            aggregatePopulation = new int[aggregateRows][aggregateColumns];

            for(Animal animal : field.getAnimals()) {
                if(animal instanceof SavannahAnimal && animal.isAlive()) {
                    SavannahAnimal savannahAnimal = (SavannahAnimal)animal;
                    Location location = savannahAnimal.getLocation();
                    int aggregateRow = aggregateRow(location.row());
                    int aggregateColumn = aggregateColumn(location.col());
                    aggregatePopulation[aggregateRow][aggregateColumn]++;
                }
            }
        }

        public boolean shouldDrawAnimal(int x, int y, SavannahAnimal animal)
        {
            int population = aggregatePopulation == null ? 0 :
                aggregatePopulation[aggregateRow(y)][aggregateColumn(x)];
            if(population <= 5 || animal.getProfile().isPredator() ||
               animal.isInfected() || animal.isSurvivalCritical() ||
               animal.getStaminaStage() == StaminaStage.LOW) {
                return true;
            }
            int spacing = Math.min(7, Math.max(2, population / 4));
            return Math.abs(x * 31 + y * 17) % spacing == 0;
        }

        public void drawMark(int x, int y, Color color)
        {
            animalGraphics.setColor(color);
            animalGraphics.fillRect(x * xScale, y * yScale,
                                    Math.max(1, xScale - 1),
                                    Math.max(1, yScale - 1));
        }

        public void drawAnimal(int x, int y, SavannahAnimal animal)
        {
            int left = x * xScale;
            int top = y * yScale;
            int cellWidth = Math.max(1, xScale - 1);
            int cellHeight = Math.max(1, yScale - 1);
            int symbolSize = Math.max(3, Math.min(cellWidth, cellHeight) - 2);
            int symbolLeft = left + Math.max(0, (cellWidth - symbolSize) / 2);
            int symbolTop = top + Math.max(0, (cellHeight - symbolSize) / 2);
            Color color = getColor(animal.getProfile().getName());

            animalGraphics.setColor(new Color(20, 18, 12, 60));
            animalGraphics.fillOval(symbolLeft + 1, symbolTop + 2,
                                    symbolSize, Math.max(2, symbolSize / 2));
            animalGraphics.setColor(color);
            if(animal.getProfile().isPredator()) {
                Polygon triangle = new Polygon();
                triangle.addPoint(symbolLeft + symbolSize / 2, symbolTop);
                triangle.addPoint(symbolLeft, symbolTop + symbolSize);
                triangle.addPoint(symbolLeft + symbolSize, symbolTop + symbolSize);
                animalGraphics.fillPolygon(triangle);
                animalGraphics.setColor(new Color(25, 25, 25, 120));
                animalGraphics.drawPolygon(triangle);
            }
            else {
                animalGraphics.fillOval(symbolLeft, symbolTop, symbolSize, symbolSize);
                animalGraphics.setColor(new Color(25, 25, 25, 115));
                animalGraphics.drawOval(symbolLeft, symbolTop, symbolSize, symbolSize);
            }

            if(animal.isSurvivalCritical()) {
                animalGraphics.setColor(new Color(255, 145, 42, 210));
                animalGraphics.drawOval(symbolLeft - 1, symbolTop - 1,
                                        symbolSize + 2, symbolSize + 2);
            }

            if(animal.getStaminaStage() == StaminaStage.LOW && cellHeight >= 5) {
                int barWidth = Math.max(2, (int)Math.round(cellWidth *
                    (animal.getStaminaPercent() / 100.0)));
                animalGraphics.setColor(new Color(35, 35, 35, 120));
                animalGraphics.fillRect(left, top + cellHeight - 2, cellWidth, 2);
                animalGraphics.setColor(new Color(245, 211, 76, 230));
                animalGraphics.fillRect(left, top + cellHeight - 2, barWidth, 2);
            }

            if(animal.isInfected()) {
                animalGraphics.setColor(INFECTED_COLOR);
                int dotSize = Math.max(2, symbolSize / 3);
                animalGraphics.fillOval(symbolLeft + symbolSize - dotSize,
                                        symbolTop, dotSize, dotSize);
            }
        }

        private int aggregateRow(int row)
        {
            if(aggregateRows <= 0) {
                return 0;
            }
            return Math.max(0, Math.min(aggregateRows - 1,
                                        row / AGGREGATE_CELL_SIZE));
        }

        private int aggregateColumn(int col)
        {
            if(aggregateColumns <= 0) {
                return 0;
            }
            return Math.max(0, Math.min(aggregateColumns - 1,
                                        col / AGGREGATE_CELL_SIZE));
        }

        public void drawSystemOverlay(SimulationContext context,
                                      SimulationDiagnostics.DiagnosticSnapshot snapshot,
                                      SimulationDiagnostics.DiagnosticSnapshot previous)
        {
            drawWeatherAndTimeOverlay(context);
            drawMetricPanel(context, snapshot, previous);
        }

        private void drawWeatherAndTimeOverlay(SimulationContext context)
        {
            WeatherType weather = context.getWeatherSystem().getCurrentWeather();
            Color tint;
            if(weather == WeatherType.RAIN) {
                tint = new Color(66, 110, 155, 44);
            }
            else if(weather == WeatherType.FOG) {
                tint = new Color(226, 229, 220, 76);
            }
            else if(weather == WeatherType.DROUGHT) {
                tint = new Color(221, 145, 68, 50);
            }
            else {
                tint = new Color(255, 241, 174, 20);
            }
            animalGraphics.setColor(tint);
            animalGraphics.fillRect(0, 0, size.width, size.height);

            DayPhase phase = context.getClock().getPhase();
            if(phase == DayPhase.NIGHT) {
                animalGraphics.setColor(new Color(19, 32, 62, 82));
                animalGraphics.fillRect(0, 0, size.width, size.height);
            }
            else if(phase == DayPhase.DAWN || phase == DayPhase.DUSK) {
                animalGraphics.setColor(new Color(232, 122, 54, 42));
                animalGraphics.fillRect(0, 0, size.width, size.height);
            }
        }

        private void drawMetricPanel(SimulationContext context,
                                     SimulationDiagnostics.DiagnosticSnapshot snapshot,
                                     SimulationDiagnostics.DiagnosticSnapshot previous)
        {
            int panelWidth = Math.min(Math.max(230, size.width / 3), 330);
            int panelHeight = 134;
            int left = 8;
            int top = 8;
            animalGraphics.setColor(new Color(255, 249, 225, 205));
            animalGraphics.fillRoundRect(left, top, panelWidth, panelHeight, 8, 8);
            animalGraphics.setColor(new Color(70, 57, 37, 150));
            animalGraphics.drawRoundRect(left, top, panelWidth, panelHeight, 8, 8);

            Font oldFont = animalGraphics.getFont();
            animalGraphics.setFont(oldFont.deriveFont(Font.BOLD, 12.0f));
            animalGraphics.setColor(new Color(45, 37, 28));
            animalGraphics.drawString("Step " + context.getStep() + "  " +
                                      context.getClock().getDisplayText(),
                                      left + 10, top + 18);

            animalGraphics.setFont(oldFont.deriveFont(11.0f));
            drawMetricBar(left + 10, top + 32, panelWidth - 20, "Grass",
                          snapshot.grassPercent,
                          new Color(94, 151, 62));
            drawMetricBar(left + 10, top + 48, panelWidth - 20, "Disease",
                          snapshot.diseasePercent,
                          INFECTED_COLOR);
            drawMetricBar(left + 10, top + 64, panelWidth - 20, "Survival",
                          snapshot.averageSurvival,
                          new Color(72, 137, 190));
            drawMetricBar(left + 10, top + 80, panelWidth - 20, "Stamina",
                          snapshot.averageStamina,
                          new Color(222, 178, 53));

            animalGraphics.setColor(new Color(45, 37, 28));
            drawFittedString("Signals: " +
                             SimulationDiagnostics.formatSignals(snapshot),
                             left + 10, top + 104, panelWidth - 20);
            drawFittedString(SimulationDiagnostics.formatShortTrend(snapshot, previous),
                             left + 10, top + 120, panelWidth - 20);

            animalGraphics.setFont(oldFont);
        }

        private void drawMetricBar(int left, int top, int width, String label,
                                   int percent, Color color)
        {
            int labelWidth = 54;
            int barWidth = width - labelWidth - 34;
            FontMetrics metrics = animalGraphics.getFontMetrics();
            animalGraphics.setColor(new Color(45, 37, 28));
            animalGraphics.drawString(label, left, top + 9);
            animalGraphics.setColor(new Color(64, 58, 45, 62));
            animalGraphics.fillRoundRect(left + labelWidth, top, barWidth, 10, 5, 5);
            animalGraphics.setColor(color);
            animalGraphics.fillRoundRect(left + labelWidth, top,
                Math.max(1, (barWidth * Math.min(100, percent)) / 100), 10, 5, 5);
            String value = percent + "%";
            animalGraphics.setColor(new Color(45, 37, 28));
            animalGraphics.drawString(value, left + width - metrics.stringWidth(value),
                                      top + 9);
        }

        private void drawFittedString(String text, int x, int baseline,
                                      int maxWidth)
        {
            FontMetrics metrics = animalGraphics.getFontMetrics();
            String fitted = text;
            while(fitted.length() > 3 && metrics.stringWidth(fitted) > maxWidth) {
                fitted = fitted.substring(0, fitted.length() - 4) + "...";
            }
            animalGraphics.drawString(fitted, x, baseline);
        }

        public void paintComponent(Graphics graphics)
        {
            super.paintComponent(graphics);
            if(terrainImage != null) {
                Dimension currentSize = getSize();
                Graphics2D g2 = (Graphics2D)graphics.create();
                interactionController.getTransform().apply(g2);
                int drawWidth = size.equals(currentSize) ? size.width :
                    currentSize.width;
                int drawHeight = size.equals(currentSize) ? size.height :
                    currentSize.height;
                g2.drawImage(terrainImage, 0, 0, drawWidth, drawHeight, null);
                if(!inspectMode) {
                    g2.drawImage(animalImage, 0, 0, drawWidth, drawHeight, null);
                }
                g2.dispose();
                if(inspectMode) {
                    Graphics2D screen = (Graphics2D)graphics.create();
                    screen.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                            RenderingHints.VALUE_ANTIALIAS_ON);
                    drawInspectLayer(screen);
                    screen.dispose();
                }
            }
        }

        Rectangle visibleCells()
        {
            ViewportTransform transform = interactionController.getTransform();
            double zoom = Math.max(0.0001, transform.getZoom());
            double cellWidth = Math.max(1, xScale);
            double cellHeight = Math.max(1, yScale);
            int viewW = getWidth() > 0 ? getWidth() : size.width;
            int viewH = getHeight() > 0 ? getHeight() : size.height;
            int colMin = (int)Math.floor(-transform.getOffsetX() / (zoom * cellWidth));
            int colMax = (int)Math.ceil((viewW - transform.getOffsetX()) /
                                        (zoom * cellWidth));
            int rowMin = (int)Math.floor(-transform.getOffsetY() / (zoom * cellHeight));
            int rowMax = (int)Math.ceil((viewH - transform.getOffsetY()) /
                                        (zoom * cellHeight));
            colMin = Math.max(0, Math.min(gridWidth - 1, colMin));
            colMax = Math.max(0, Math.min(gridWidth - 1, colMax));
            rowMin = Math.max(0, Math.min(gridHeight - 1, rowMin));
            rowMax = Math.max(0, Math.min(gridHeight - 1, rowMax));
            return new Rectangle(colMin, rowMin,
                Math.max(1, colMax - colMin + 1),
                Math.max(1, rowMax - rowMin + 1));
        }

        private void drawInspectLayer(Graphics2D g2)
        {
            SceneDirector.Scene activeScene =
                (!storyboard.isEmpty() && sceneIndex < storyboard.size())
                    ? storyboard.get(sceneIndex) : null;
            double elapsedMs = activeScene == null ? 0.0 :
                (System.nanoTime() - sceneStartNanos) / 1_000_000.0;
            double ambientElapsedMs =
                ((System.nanoTime() - ambientStartNanos) / 1_000_000.0) %
                SceneDirector.AMBIENT_DURATION_MS;
            Set<Long> activeActorIds = activeActorIds(activeScene);

            if(activeScene != null) {
                for(SceneDirector.Vfx vfx : activeScene.vfx) {
                    if("GRASS_DIM".equals(vfx.kind)) {
                        drawGrassDim(g2, vfx, elapsedMs);
                    }
                }
            }

            for(SceneDirector.SceneActor actor : ambientActors) {
                if(!activeActorIds.contains(actor.animalId)) {
                    drawSceneActor(g2, actor, ambientElapsedMs);
                }
            }

            drawEventMarkersScreen(g2);

            if(activeScene != null) {
                for(SceneDirector.Vfx vfx : activeScene.vfx) {
                    if("INFECTION_ARC".equals(vfx.kind)) {
                        drawInfectionArc(g2, vfx, elapsedMs);
                    }
                }
                for(SceneDirector.SceneActor actor : activeScene.actors) {
                    drawSceneActor(g2, actor, elapsedMs);
                }
            }
        }

        private Set<Long> activeActorIds(SceneDirector.Scene activeScene)
        {
            Set<Long> ids = new HashSet<>();
            if(activeScene != null) {
                for(SceneDirector.SceneActor actor : activeScene.actors) {
                    ids.add(actor.animalId);
                }
            }
            return ids;
        }

        private void drawGrassDim(Graphics2D g2, SceneDirector.Vfx vfx,
                                  double elapsedMs)
        {
            double t = Math.max(0.0, Math.min(1.0,
                (elapsedMs - vfx.startMs) / Math.max(1.0, vfx.endMs - vfx.startMs)));
            int sx = screenX(vfx.cellCol);
            int sy = screenY(vfx.cellRow);
            int w = (int)Math.ceil(xScale * interactionController.getTransform().getZoom());
            int h = (int)Math.ceil(yScale * interactionController.getTransform().getZoom());
            g2.setColor(new Color(30, 26, 12, (int)(90 * t)));
            g2.fillRect(sx, sy, w, h);
        }

        private void drawEventMarkersScreen(Graphics2D g2)
        {
            Font old = g2.getFont();
            g2.setFont(old.deriveFont(Font.BOLD, 12.0f));
            Rectangle viewport = visibleCells();
            for(SimulationEvent event : lastEvents) {
                if(event.type == SimulationEvent.EventType.MOVE ||
                   !viewport.contains(event.col, event.row)) {
                    continue;
                }
                int sx = screenX(event.col) +
                    (int)Math.round(xScale * interactionController.getTransform().getZoom() / 2.0);
                int sy = screenY(event.row) +
                    (int)Math.round(yScale * interactionController.getTransform().getZoom() / 2.0);
                switch(event.type) {
                    case HUNT:
                        g2.setColor(new Color(220, 60, 40, 200));
                        g2.drawString("X", sx - 4, sy + 5);
                        break;
                    case BIRTH:
                        g2.setColor(new Color(80, 200, 100, 200));
                        g2.drawString("+", sx - 4, sy + 5);
                        break;
                    case DISEASE_DEATH:
                        g2.setColor(new Color(160, 40, 160, 200));
                        g2.drawString("!", sx - 3, sy + 5);
                        break;
                    case INFECTION:
                        g2.setColor(new Color(90, 200, 90, 190));
                        g2.fillOval(sx - 3, sy - 3, 6, 6);
                        break;
                    default:
                        break;
                }
            }
            g2.setFont(old);
        }

        private void drawSceneActor(Graphics2D g2,
                                    SceneDirector.SceneActor actor,
                                    double elapsedMs)
        {
            SceneDirector.Keyframe frame = actor.at(elapsedMs);
            if(frame.alpha <= 0.01) {
                return;
            }

            double row = frame.row;
            double col = frame.col;
            if(actor.tremble) {
                double phase = System.nanoTime() / 1.0e8;
                row += Math.sin(phase) * 0.05;
                col += Math.cos(phase * 1.3) * 0.05;
            }

            ViewportTransform transform = interactionController.getTransform();
            int sx = (int)Math.round(col * xScale * transform.getZoom() +
                                     transform.getOffsetX());
            int sy = (int)Math.round(row * yScale * transform.getZoom() +
                                     transform.getOffsetY());
            int iconPixels = (int)Math.max(8, Math.min(xScale, yScale) *
                transform.getZoom() * 1.6 * frame.scale);
            int drawX = sx - iconPixels / 2;
            int drawY = sy - iconPixels / 2;

            Composite oldComposite = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                (float)Math.max(0.0, Math.min(1.0, frame.alpha))));

            BufferedImage icon = iconSet.iconFor(actor.species,
                                                 getColor(actor.species));
            g2.setColor(new Color(20, 18, 12, 70));
            g2.fillOval(drawX + 2, drawY + iconPixels - iconPixels / 4,
                        Math.max(2, iconPixels - 4),
                        Math.max(2, iconPixels / 4));
            g2.drawImage(icon, drawX, drawY, iconPixels, iconPixels, null);

            if(frame.filterAlpha > 0.01) {
                g2.setColor(new Color(90, 140, 60,
                                      (int)(140 * frame.filterAlpha)));
                g2.fillOval(drawX, drawY, iconPixels, iconPixels);
            }
            if(frame.marker != null) {
                Font old = g2.getFont();
                g2.setFont(old.deriveFont(Font.BOLD, 14.0f));
                g2.setColor(frame.markerColor == null ? Color.white :
                            frame.markerColor);
                g2.drawString(frame.marker, sx - 6, sy - iconPixels / 2 - 4);
                g2.setFont(old);
            }

            g2.setComposite(oldComposite);
        }

        private void drawInfectionArc(Graphics2D g2, SceneDirector.Vfx vfx,
                                      double elapsedMs)
        {
            if(elapsedMs < vfx.startMs || elapsedMs > vfx.endMs) {
                return;
            }
            double t = (elapsedMs - vfx.startMs) /
                       Math.max(1.0, vfx.endMs - vfx.startMs);
            double fromX = screenX(vfx.fromCol);
            double fromY = screenY(vfx.fromRow);
            double toX = screenX(vfx.toCol);
            double toY = screenY(vfx.toRow);
            double midX = (fromX + toX) / 2.0;
            double midY = Math.min(fromY, toY) - 20.0;

            g2.setColor(new Color(90, 200, 90, 210));
            for(int i = 0; i < 4; i++) {
                double pt = (t + (double)i / 4.0) % 1.0;
                double x = quadBezier(fromX, midX, toX, pt);
                double y = quadBezier(fromY, midY, toY, pt);
                g2.fillOval((int)Math.round(x) - 3,
                            (int)Math.round(y) - 3, 6, 6);
            }
        }

        private int screenX(double col)
        {
            ViewportTransform transform = interactionController.getTransform();
            return (int)Math.round(col * xScale * transform.getZoom() +
                                   transform.getOffsetX());
        }

        private int screenY(double row)
        {
            ViewportTransform transform = interactionController.getTransform();
            return (int)Math.round(row * yScale * transform.getZoom() +
                                   transform.getOffsetY());
        }

        private double quadBezier(double p0, double p1, double p2, double t)
        {
            double u = 1.0 - t;
            return u * u * p0 + 2.0 * u * t * p1 + t * t * p2;
        }

        private void clearAnimalLayer()
        {
            animalGraphics.setComposite(AlphaComposite.Clear);
            animalGraphics.fillRect(0, 0, size.width, size.height);
            animalGraphics.setComposite(AlphaComposite.SrcOver);
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

    private static class PredatorIcon implements Icon
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
            Graphics2D g2 = (Graphics2D)graphics.create();
            Polygon triangle = new Polygon();
            triangle.addPoint(x + 7, y + 2);
            triangle.addPoint(x + 2, y + 12);
            triangle.addPoint(x + 12, y + 12);
            g2.setColor(new Color(189, 91, 45));
            g2.fillPolygon(triangle);
            g2.setColor(Color.darkGray);
            g2.drawPolygon(triangle);
            g2.dispose();
        }
    }

    private static class HerbivoreIcon implements Icon
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
            graphics.setColor(new Color(92, 151, 72));
            graphics.fillOval(x + 2, y + 2, 10, 10);
            graphics.setColor(Color.darkGray);
            graphics.drawOval(x + 2, y + 2, 10, 10);
        }
    }

    private static class SurvivalIcon implements Icon
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
            Graphics2D g2 = (Graphics2D)graphics.create();
            g2.setColor(new Color(255, 145, 42));
            g2.setStroke(new BasicStroke(2.0f));
            g2.drawOval(x + 2, y + 2, 10, 10);
            g2.dispose();
        }
    }

    private static class StaminaIcon implements Icon
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
            graphics.setColor(new Color(40, 40, 40, 130));
            graphics.fillRect(x + 1, y + 9, 12, 3);
            graphics.setColor(new Color(245, 211, 76));
            graphics.fillRect(x + 1, y + 9, 7, 3);
            graphics.setColor(Color.darkGray);
            graphics.drawRect(x + 1, y + 9, 12, 3);
        }
    }

}
