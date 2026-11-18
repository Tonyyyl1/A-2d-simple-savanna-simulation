package savanna;

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
import java.awt.event.MouseAdapter;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * A graphical view of the savanna simulation grid.
 */
public class SimulatorView extends JFrame
{
    private static final Color UNKNOWN_COLOR = Color.gray;
    private static final Color INFECTED_COLOR = new Color(210, 35, 42);
    private static final String STEP_PREFIX = "Step: ";
    private static final String POPULATION_PREFIX = "Population: ";

    private final JLabel stepLabel;
    private final JLabel environment;
    private final JLabel population;
    private final JLabel heatmapSummary;
    private final JButton pauseButton;
    private final JButton stopButton;
    private final JCheckBox heatmapToggle;
    private final JComboBox<String> heatmapTypeSelector;
    private final JComboBox<String> heatmapWindowSelector;
    private final JComboBox<String> heatmapSpeciesSelector;
    private final FieldView fieldView;
    private final Map<String, Color> colors;
    private final FieldStats stats;
    private final ViewInteractionController interactionController;
    private final AnimalIconSet iconSet;
    private final FieldRenderer fieldRenderer;
    private final OverviewRenderer overviewRenderer;
    private final InspectRenderer inspectRenderer;
    private final DisplayConfig displayConfig;
    private SimulationControlHandler controlHandler;
    private SimulationDiagnostics.DiagnosticSnapshot previousSnapshot;
    private Field lastField;
    private SimulationContext lastContext;
    private List<SimulationEvent> lastEvents;
    private List<SceneDirector.Scene> storyboard;
    private List<SceneDirector.SceneActor> ambientActors;
    private List<HistoricalEventOverlay.Entry> historicalEntries;
    private RenderFrame lastRenderFrame;
    private String markerSummary = "";
    private DisplayFactAudit latestFactAudit;
    private int sceneIndex;
    private long sceneStartNanos;
    private long ambientStartNanos;
    private Timer sceneTimer;
    private boolean inspectMode;
    private boolean pauseBeforeInspect;

    /**
     * Creates the Swing view on the EDT. The simulator is normally created on
     * its own thread, so construction itself must not leak Swing mutations to
     * that thread.
     */
    public static SimulatorView createOnEventDispatchThread(final int height,
                                                            final int width,
                                                            final String configSummary)
    {
        return createOnEventDispatchThread(height, width, configSummary,
            DisplayConfig.defaults());
    }

    public static SimulatorView createOnEventDispatchThread(final int height,
                                                            final int width,
                                                            final String configSummary,
                                                            final DisplayConfig displayConfig)
    {
        if(SwingUtilities.isEventDispatchThread()) {
            return new SimulatorView(height, width, configSummary, displayConfig);
        }
        final SimulatorView[] result = new SimulatorView[1];
        runOnEdtAndWait(new Runnable() {
            public void run()
            {
                result[0] = new SimulatorView(height, width, configSummary,
                    displayConfig);
            }
        });
        return result[0];
    }

    public SimulatorView(int height, int width)
    {
        this(height, width, "");
    }

    public SimulatorView(int height, int width, String configSummary)
    {
        this(height, width, configSummary, DisplayConfig.defaults());
    }

    public SimulatorView(int height, int width, String configSummary,
                         DisplayConfig displayConfig)
    {
        requireEdt("construct SimulatorView");
        this.displayConfig = displayConfig == null ? DisplayConfig.defaults()
                                                   : displayConfig;
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
        fieldRenderer = new FieldRenderer(colors);
        overviewRenderer = new OverviewRenderer();
        inspectRenderer = new InspectRenderer();
        historicalEntries = Collections.emptyList();

        setTitle("African Savanna Simulation" +
                 (configSummary == null || configSummary.isEmpty()
                    ? "" : " - " + configSummary));
        stepLabel = new JLabel(STEP_PREFIX, JLabel.CENTER);
        environment = new JLabel("", JLabel.CENTER);
        population = new JLabel(POPULATION_PREFIX, JLabel.CENTER);
        heatmapSummary = new JLabel("Heatmap: LAST_1000 | All / 全部 | All species / 全部物种", JLabel.CENTER);
        pauseButton = new JButton("Pause");
        stopButton = new JButton("Stop & Exit");
        heatmapToggle = new JCheckBox("Heatmap / 热力图");
        heatmapTypeSelector = new JComboBox<>(
            EventMetadataRegistry.heatmapLabels());
        HeatmapWindow[] windows = HeatmapWindow.values();
        String[] windowLabels = new String[windows.length];
        for(int index = 0; index < windows.length; index++) {
            windowLabels[index] = windows[index].getLabel();
        }
        heatmapWindowSelector = new JComboBox<>(windowLabels);
        heatmapWindowSelector.setSelectedItem(HeatmapWindow.LAST_1000.getLabel());
        heatmapSpeciesSelector = new JComboBox<>(
            HeatmapSelection.actorSpeciesChoices().toArray(new String[0]));

        setLocation(100, 50);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent event)
            {
                requestStopAndExit();
            }
        });

        fieldView = new FieldView(height, width);
        fieldView.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent event)
            {
                fieldView.updateTargetSize();
                if(inspectMode) {
                    rebuildStoryboard();
                }
                fieldView.repaint();
            }
        });
        interactionController.addViewportChangeListener(
            new ViewInteractionController.ViewportChangeListener() {
                public void viewportChanged()
                {
                    updateInspectMode();
                }
            });
        interactionController.register(fieldView, fieldView);
        fieldView.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent event)
            {
                if(event.getButton() == MouseEvent.BUTTON1 && inspectMode) {
                    SavannahAnimal animal =
                        fieldView.findInspectAnimalAt(event.getX(), event.getY());
                    if(animal != null) {
                        showAnimalStatusLog(animal);
                    }
                }
            }
        });

        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 1));
        controlsPanel.add(heatmapToggle);
        controlsPanel.add(heatmapTypeSelector);
        controlsPanel.add(heatmapWindowSelector);
        controlsPanel.add(heatmapSpeciesSelector);
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
        bottomPanel.add(heatmapSummary, BorderLayout.CENTER);
        bottomPanel.add(createLegendPanel(), BorderLayout.SOUTH);

        Container contents = getContentPane();
        contents.add(topPanel, BorderLayout.NORTH);
        contents.add(fieldView, BorderLayout.CENTER);
        contents.add(bottomPanel, BorderLayout.SOUTH);
        pauseButton.addActionListener(event -> togglePaused());
        stopButton.addActionListener(event -> requestStopAndExit());
        heatmapToggle.addActionListener(event -> updateHeatmapControls());
        heatmapTypeSelector.addActionListener(event -> updateHeatmapControls());
        heatmapWindowSelector.addActionListener(event -> updateHeatmapControls());
        heatmapSpeciesSelector.addActionListener(event -> updateHeatmapControls());
        updateHeatmapControls();
        pack();
    }

    /**
     * Connect the window buttons to the running simulator.
     */
    public void setControlHandler(SimulationControlHandler controlHandler)
    {
        final SimulationControlHandler handler = controlHandler;
        runOnEdtAndWait(new Runnable() {
            public void run()
            {
                SimulatorView.this.controlHandler = handler;
            }
        });
    }

    /**
     * Update the pause button to match the simulator state.
     */
    public void setPaused(boolean paused)
    {
        final boolean requested = paused;
        runOnEdtAndWait(new Runnable() {
            public void run()
            {
                pauseButton.setText(requested ? "Resume" : "Pause");
            }
        });
    }

    /**
     * Disable buttons after the simulation is stopping.
     */
    public void setStopping()
    {
        runOnEdtAndWait(new Runnable() {
            public void run()
            {
                pauseButton.setEnabled(false);
                stopButton.setEnabled(false);
                stopButton.setText("Stopping...");
                stopSceneTimer();
            }
        });
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
        final int requestedStep = step;
        final Field requestedField = field;
        final SimulationContext requestedContext = context;
        final RenderFrame requestedFrame = RenderFrame.capture(
            requestedStep, requestedField, requestedContext,
            requestedField == null ? 1 : requestedField.getWidth(),
            requestedField == null ? 1 : requestedField.getDepth());
        runOnEdtAndWait(new Runnable() {
            public void run()
            {
                showStatusOnEdt(requestedFrame, requestedField, requestedContext);
            }
        });
    }

    /** Commits a frame already published by the simulator without recapturing Field. */
    public void showStatus(RenderFrame frame, Field field,
                           SimulationContext context)
    {
        final RenderFrame requestedFrame = frame;
        final Field requestedField = field;
        final SimulationContext requestedContext = context;
        runOnEdtAndWait(new Runnable() {
            public void run()
            {
                showStatusOnEdt(requestedFrame, requestedField, requestedContext);
            }
        });
    }

    /**
     * Builds the whole ordinary layer on the EDT while the caller is waiting,
     * then swaps one completed frame reference. A build failure deliberately
     * leaves the previous front frame and labels intact.
     */
    private void showStatusOnEdt(RenderFrame renderFrame, Field field,
                                 SimulationContext context)
    {
        requireEdt("build an overview frame");
        if(renderFrame == null) {
            return;
        }
        FieldView.OverviewBuildResult build = fieldView.buildAndCommit(renderFrame);
        if(build == null) {
            return;
        }

        lastField = field;
        lastContext = context;
        lastRenderFrame = renderFrame;
        lastEvents = renderFrame.frameEventSummary.getRepresentativeEvents();
        latestFactAudit = build.audit;
        stepLabel.setText(STEP_PREFIX + renderFrame.step);
        environment.setIcon(new SwatchIcon(renderFrame.metrics.weatherType.getColor()));
        environment.setText(" " + renderFrame.metrics.environmentText);
        population.setText(POPULATION_PREFIX + build.populationText);
        markerSummary = formatMarkerSummary(renderFrame, build);
        updateHeatmapSummary(renderFrame);
        fieldView.repaint();
        if(!isVisible()) {
            setVisible(true);
        }
    }

    private static void runOnEdtAndWait(Runnable action)
    {
        if(SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the EDT.", e);
        }
        catch(InvocationTargetException e) {
            Throwable cause = e.getCause();
            if(cause instanceof RuntimeException) {
                throw (RuntimeException)cause;
            }
            if(cause instanceof Error) {
                throw (Error)cause;
            }
            throw new IllegalStateException("EDT action failed.", cause);
        }
    }

    private static void requireEdt(String action)
    {
        if(!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("The EDT must " + action + ".");
        }
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

    private void updateHeatmapControls()
    {
        fieldView.setHeatmapVisible(heatmapToggle.isSelected());
        fieldView.setHeatmapSelection(selectedHeatmapSelection());
        updateHeatmapSummary(lastRenderFrame);
        fieldView.repaint();
    }

    private HeatmapSelection selectedHeatmapSelection()
    {
        HeatmapWindow[] windows = HeatmapWindow.values();
        int windowIndex = Math.max(0, Math.min(windows.length - 1,
                                                 heatmapWindowSelector.getSelectedIndex()));
        int speciesIndex = heatmapSpeciesSelector.getSelectedIndex();
        String species = speciesIndex <= 0 ? null
            : (String)heatmapSpeciesSelector.getSelectedItem();
        return new HeatmapSelection(windows[windowIndex], selectedHeatmapType(), species);
    }

    private void updateHeatmapSummary(RenderFrame frame)
    {
        HeatmapSelection selection = selectedHeatmapSelection();
        if(frame == null || frame.heatmapSnapshot == null) {
            heatmapSummary.setText("Heatmap: " + selection.getEventLabel() + " | " +
                selection.getWindow().getLabel() + " | " + selection.getSpeciesLabel() +
                " | awaiting visible frame" + markerSummary);
            return;
        }
        HeatmapSelectionFacts facts = frame.heatmapSnapshot.select(selection);
        heatmapSummary.setText("Heatmap: " + selection.getEventLabel() + " | " +
            selection.getWindow().getLabel() + " | " + selection.getSpeciesLabel() +
            " | total " + facts.getTotalEventCount() + " max " + facts.getMaximum() +
            " | steps [" + facts.getStartStep() + ',' + facts.getEndStep() + "]" +
            markerSummary);
    }

    private String formatMarkerSummary(RenderFrame frame, FieldView.OverviewBuildResult build)
    {
        if(frame == null || build == null) return "";
        StringBuilder summary = new StringBuilder(" | markers ");
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String species = factory.getProfile().getName();
            summary.append(species).append(' ')
                .append(build.drawnBySpecies.getOrDefault(species, 0)).append('/')
                .append(frame.metrics.speciesCounts.getOrDefault(species, 0)).append(' ');
        }
        return summary.append("| priority ").append(build.priorityOverlayCount)
            .append(" | density-aware sampling").toString();
    }

    private SimulationEvent.EventType selectedHeatmapType()
    {
        return EventMetadataRegistry.heatmapTypeAt(
            heatmapTypeSelector.getSelectedIndex());
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
        historicalEntries = Collections.emptyList();
        if(controlHandler != null && !pauseBeforeInspect) {
            controlHandler.setPaused(false);
        }
        fieldView.repaint();
    }

    private void rebuildStoryboard()
    {
        if(lastRenderFrame == null) {
            storyboard = Collections.emptyList();
            ambientActors = Collections.emptyList();
            historicalEntries = Collections.emptyList();
            stopSceneTimer();
            fieldView.repaint();
            return;
        }
        Rectangle viewport = fieldView.visibleCells();
        InspectRenderResult model = inspectRenderer.render(
            lastRenderFrame, new InspectLayout(viewport));
        storyboard = model.getScenes();
        ambientActors = model.getCurrentActors();
        historicalEntries = model.getHistoricalEntries();
        refreshInspectFactAudit(viewport);
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

    /**
     * Updates an audit-only immutable sidecar. It neither feeds a renderer nor
     * alters the field, random stream, timers, or current display frame.
     */
    private void refreshInspectFactAudit(Rectangle viewport)
    {
        if(latestFactAudit == null) {
            return;
        }
        List<SceneDirector.SceneActor> visibleActors = new ArrayList<>();
        Set<Long> activeIds = new HashSet<>();
        for(SceneDirector.Scene scene : storyboard) {
            for(SceneDirector.SceneActor actor : scene.actors) {
                visibleActors.add(actor);
                activeIds.add(actor.animalId);
            }
        }
        for(SceneDirector.SceneActor actor : ambientActors) {
            if(!activeIds.contains(actor.animalId)) {
                visibleActors.add(actor);
            }
        }
        List<DisplayFactAudit.AnimalFact> facts =
            DisplayFactAudit.captureFrameFacts(lastRenderFrame, viewport);
        latestFactAudit = new DisplayFactAudit(latestFactAudit.getStep(),
            latestFactAudit.getGeneration(), viewport, facts,
            new ArrayList<>(facts),
            DisplayFactAudit.captureInspectActorInputs(lastRenderFrame,
                                                       visibleActors));
    }

    /** Returns the latest immutable B3 sidecar without changing the display. */
    public DisplayFactAudit getLatestFactAudit()
    {
        final DisplayFactAudit[] result = new DisplayFactAudit[1];
        runOnEdtAndWait(new Runnable() {
            public void run()
            {
                result[0] = latestFactAudit;
            }
        });
        return result[0];
    }

    private Timer sceneTimer()
    {
        if(sceneTimer == null) {
            sceneTimer = new Timer(displayConfig.getInspectTickMillis(),
                event -> advanceStoryboard());
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
        if(elapsedMs > current.durationMs +
           displayConfig.getInspectHoldMillis()) {
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

    private void showAnimalStatusLog(SavannahAnimal animal)
    {
        AnimalStatusLog.showDialog(this, animal, lastContext, lastField);
    }

    private SavannahAnimal findAnimalById(long animalId)
    {
        if(lastField == null || animalId < 0) {
            return null;
        }
        for(Animal animal : lastField.getAnimals()) {
            if(animal instanceof SavannahAnimal && animal.isAlive() &&
               animal.getId() == animalId) {
                return (SavannahAnimal)animal;
            }
        }
        return null;
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
        panel.add(new JLabel("Thirst", new ThirstIcon(), JLabel.LEFT));
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
        private Dimension size;
        private TerrainMap terrainMap;
        private VisualGridGeometry geometry;
        private VisualWaterMask visualWaterMask;
        private final OverviewFrameBuffer frameBuffer;
        private BufferedImage cachedTerrainImage;
        private TerrainMap cachedTerrainMap;
        private Dimension cachedTerrainSize;
        private long resizeGeneration;
        private HeatmapSnapshot heatmapSnapshot;
        private HeatmapSelection heatmapSelection;
        private HeatmapSelectionFacts heatmapFacts;
        private boolean heatmapVisible;
        private int aggregateColumns;
        private int aggregateRows;
        private int[][] aggregatePopulation;
        public FieldView(int height, int width)
        {
            gridHeight = height;
            gridWidth = width;
            size = new Dimension(0, 0);
            frameBuffer = new OverviewFrameBuffer();
            cachedTerrainImage = null;
            cachedTerrainMap = null;
            cachedTerrainSize = new Dimension(0, 0);
            resizeGeneration = 0L;
            heatmapVisible = false;
            heatmapSelection = HeatmapSelection.defaultSelection();
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

        public void setHeatmapVisible(boolean visible)
        {
            heatmapVisible = visible;
            repaint();
        }

        public void setHeatmapSelection(HeatmapSelection selection)
        {
            heatmapSelection = selection == null ? HeatmapSelection.defaultSelection()
                                                  : selection;
            heatmapFacts = heatmapSnapshot == null ? null
                : heatmapSnapshot.select(heatmapSelection);
            repaint();
        }

        /**
         * Records a new target size but never rebuilds from Field here. Until
         * the next simulation refresh commits a completed frame, paint scales
         * the previous completed frame.
         */
        public void updateTargetSize()
        {
            requireEdt("update overview size");
            Dimension target = targetSize();
            if(!size.equals(target)) {
                size = new Dimension(target);
                geometry = new VisualGridGeometry(gridHeight, gridWidth,
                                                  size.width, size.height);
                resizeGeneration++;
            }
        }

        /**
         * Constructs the normal layer privately and replaces the front frame
         * only after terrain, markers and the system overlay are all present.
         * Returning null means the old complete frame remains authoritative.
         */
        public OverviewBuildResult buildAndCommit(RenderFrame renderFrame)
        {
            requireEdt("build and commit an overview frame");
            updateTargetSize();
            Dimension target = new Dimension(size);
            long generation = resizeGeneration;
            TerrainMap currentTerrainMap = renderFrame.terrainMap;
            VisualGridGeometry frameGeometry = new VisualGridGeometry(
                gridHeight, gridWidth, target.width, target.height);
            VisualWaterMask frameWaterMask = currentTerrainMap == null ? null :
                new VisualWaterMask(currentTerrainMap, target.width, target.height);
            Rectangle overviewViewport = new Rectangle(0, 0, gridWidth,
                                                        gridHeight);
            try {
                OverviewRenderResult rendered = overviewRenderer.render(renderFrame,
                    new RenderOptions(target.width, target.height, true));
                OverviewFrameBuffer.CompletedFrame completed =
                    frameBuffer.completedFrame(generation, renderFrame.step,
                    target.width, target.height, rendered.getTerrainImage(),
                    currentTerrainMap, frameGeometry, frameWaterMask,
                    rendered.getOverlayImage(), rendered.getDrawnAnimalCount(),
                    true);
                if(generation != resizeGeneration) {
                    return null;
                }
                frameBuffer.commit(completed);
                terrainMap = currentTerrainMap;
                geometry = frameGeometry;
                visualWaterMask = frameWaterMask;
                heatmapSnapshot = renderFrame.heatmapSnapshot;
                heatmapFacts = heatmapSnapshot == null ? null
                    : heatmapSnapshot.select(heatmapSelection);
                List<DisplayFactAudit.AnimalFact> facts =
                    DisplayFactAudit.captureFrameFacts(renderFrame,
                                                       overviewViewport);
                DisplayFactAudit audit = new DisplayFactAudit(renderFrame.step,
                    completed.getGeneration(), overviewViewport, facts,
                    new ArrayList<>(facts), Collections.emptyList());
                return new OverviewBuildResult(renderFrame.metrics.populationText,
                                               audit, rendered.getDrawnBySpecies(),
                                               rendered.getPriorityOverlayCount());
            }
            catch(RuntimeException e) {
                System.err.println("Overview frame build failed; retaining the " +
                                   "previous completed frame: " + e.getMessage());
                return null;
            }
        }

        private final class OverviewBuildResult
        {
            private final String populationText;
            private final DisplayFactAudit audit;
            private final Map<String, Integer> drawnBySpecies;
            private final int priorityOverlayCount;

            private OverviewBuildResult(String populationText,
                                        DisplayFactAudit audit,
                                        Map<String, Integer> drawnBySpecies,
                                        int priorityOverlayCount)
            {
                this.populationText = populationText;
                this.audit = audit;
                this.drawnBySpecies = Collections.unmodifiableMap(
                    new LinkedHashMap<>(drawnBySpecies));
                this.priorityOverlayCount = priorityOverlayCount;
            }
        }

        private Dimension targetSize()
        {
            Dimension current = getSize();
            if(current.width <= 0 || current.height <= 0) {
                current = getPreferredSize();
            }
            return new Dimension(Math.max(1, current.width),
                                 Math.max(1, current.height));
        }

        private BufferedImage terrainImageFor(TerrainMap currentTerrainMap,
                                              Dimension target)
        {
            if(cachedTerrainImage != null && cachedTerrainMap == currentTerrainMap &&
               cachedTerrainSize.equals(target)) {
                return cachedTerrainImage;
            }
            BufferedImage terrain = new BufferedImage(target.width, target.height,
                                                       BufferedImage.TYPE_INT_ARGB);
            Graphics2D terrainGraphics = terrain.createGraphics();
            try {
                if(currentTerrainMap != null) {
                    currentTerrainMap.drawBackground(terrainGraphics, target.width,
                                                     target.height);
                }
                else {
                    terrainGraphics.setColor(TerrainType.OPEN_PLAIN.getColor());
                    terrainGraphics.fillRect(0, 0, target.width, target.height);
                }
            }
            finally {
                terrainGraphics.dispose();
            }
            cachedTerrainImage = terrain;
            cachedTerrainMap = currentTerrainMap;
            cachedTerrainSize = new Dimension(target);
            return terrain;
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
               animal.isThirsty() ||
               animal.getStaminaStage() == StaminaStage.LOW) {
                return true;
            }
            int spacing = Math.min(7, Math.max(2, population / 4));
            return Math.abs(x * 31 + y * 17) % spacing == 0;
        }

        public boolean canDrawOrdinaryAnimal(Location location,
                                             SavannahAnimal animal,
                                             VisualWaterMask frameWaterMask)
        {
            return frameWaterMask == null ||
                   SafeMarkerPlacement.hasVisibleLandPixel(location, animal,
                       frameWaterMask.getGeometry(), frameWaterMask.getTerrain());
        }

        public boolean canDrawOrdinaryAnimal(Location location)
        {
            return canDrawOrdinaryAnimal(location, null, visualWaterMask);
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

        public void drawSystemOverlay(Graphics2D graphics, Dimension target,
                                      SimulationContext context,
                                      SimulationDiagnostics.DiagnosticSnapshot snapshot,
                                      SimulationDiagnostics.DiagnosticSnapshot previous,
                                      int[] behaviorCounts)
        {
            drawWeatherAndTimeOverlay(graphics, target, context);
            drawMetricPanel(graphics, target, context, snapshot, previous,
                            behaviorCounts);
        }

        private void drawWeatherAndTimeOverlay(Graphics2D graphics, Dimension target,
                                               SimulationContext context)
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
            graphics.setColor(tint);
            graphics.fillRect(0, 0, target.width, target.height);

            DayPhase phase = context.getClock().getPhase();
            if(phase == DayPhase.NIGHT) {
                graphics.setColor(new Color(19, 32, 62, 82));
                graphics.fillRect(0, 0, target.width, target.height);
            }
            else if(phase == DayPhase.DAWN || phase == DayPhase.DUSK) {
                graphics.setColor(new Color(232, 122, 54, 42));
                graphics.fillRect(0, 0, target.width, target.height);
            }
        }

        private void drawMetricPanel(Graphics2D graphics, Dimension target,
                                     SimulationContext context,
                                     SimulationDiagnostics.DiagnosticSnapshot snapshot,
                                     SimulationDiagnostics.DiagnosticSnapshot previous,
                                     int[] behaviorCounts)
        {
            int panelWidth = Math.min(Math.max(230, target.width / 3), 330);
            int panelHeight = 230;
            int left = 8;
            int top = 8;
            graphics.setColor(new Color(255, 249, 225, 205));
            graphics.fillRoundRect(left, top, panelWidth, panelHeight, 8, 8);
            graphics.setColor(new Color(70, 57, 37, 150));
            graphics.drawRoundRect(left, top, panelWidth, panelHeight, 8, 8);

            Font oldFont = graphics.getFont();
            graphics.setFont(oldFont.deriveFont(Font.BOLD, 12.0f));
            graphics.setColor(new Color(45, 37, 28));
            graphics.drawString("Step " + context.getStep() + "  " +
                                      context.getClock().getDisplayText(),
                                      left + 10, top + 18);

            graphics.setFont(oldFont.deriveFont(11.0f));
            drawMetricBar(graphics, left + 10, top + 32, panelWidth - 20, "Grass",
                          snapshot.grassPercent,
                          new Color(94, 151, 62));
            drawMetricBar(graphics, left + 10, top + 48, panelWidth - 20, "Disease",
                          snapshot.diseasePercent,
                          INFECTED_COLOR);
            drawMetricBar(graphics, left + 10, top + 64, panelWidth - 20, "Survival",
                          snapshot.averageSurvival,
                          new Color(72, 137, 190));
            drawMetricBar(graphics, left + 10, top + 80, panelWidth - 20, "Stamina",
                          snapshot.averageStamina,
                          new Color(222, 178, 53));

            graphics.setColor(new Color(45, 37, 28));
            drawFittedString(graphics, "Actions: " +
                             BehaviorVisuals.compactSummary(behaviorCounts),
                             left + 10, top + 104, panelWidth - 20);
            drawFittedString(graphics, "Signals: " +
                             SimulationDiagnostics.formatSignals(snapshot),
                             left + 10, top + 120, panelWidth - 20);
            drawFittedString(graphics,
                             SimulationDiagnostics.formatShortTrend(snapshot, previous),
                             left + 10, top + 136, panelWidth - 20);
            RenderProvenance provenance = context.getRenderProvenance();
            drawFittedString(graphics, "Build / class: " +
                             provenance.getBuildId() + " / " +
                             provenance.getCandidateClass(),
                             left + 10, top + 152, panelWidth - 20);
            drawFittedString(graphics, "Main / test manifest: " +
                             provenance.getShortSourceHash() + " / " +
                             provenance.getShortTestSourceHash(),
                             left + 10, top + 168, panelWidth - 20);
            drawFittedString(graphics, "Config: " +
                             provenance.getShortConfigurationHash(),
                             left + 10, top + 184, panelWidth - 20);
            drawFittedString(graphics, "Ecology / terrain seed: " +
                             provenance.getEcologyRandomSeed() + " / " +
                             provenance.getTerrainSeed(),
                             left + 10, top + 200, panelWidth - 20);
            drawFittedString(graphics, "Terrain: " +
                             provenance.getTerrainGeneratorVersion() + " fp " +
                             provenance.getShortTerrainFingerprint(),
                             left + 10, top + 216, panelWidth - 20);

            graphics.setFont(oldFont);
        }

        private void drawMetricBar(Graphics2D graphics, int left, int top,
                                   int width, String label,
                                   int percent, Color color)
        {
            int labelWidth = 54;
            int barWidth = width - labelWidth - 34;
            FontMetrics metrics = graphics.getFontMetrics();
            graphics.setColor(new Color(45, 37, 28));
            graphics.drawString(label, left, top + 9);
            graphics.setColor(new Color(64, 58, 45, 62));
            graphics.fillRoundRect(left + labelWidth, top, barWidth, 10, 5, 5);
            graphics.setColor(color);
            graphics.fillRoundRect(left + labelWidth, top,
                Math.max(1, (barWidth * Math.min(100, percent)) / 100), 10, 5, 5);
            String value = percent + "%";
            graphics.setColor(new Color(45, 37, 28));
            graphics.drawString(value, left + width - metrics.stringWidth(value),
                                      top + 9);
        }

        private void drawFittedString(Graphics2D graphics, String text,
                                      int x, int baseline,
                                      int maxWidth)
        {
            FontMetrics metrics = graphics.getFontMetrics();
            String fitted = text;
            while(fitted.length() > 3 && metrics.stringWidth(fitted) > maxWidth) {
                fitted = fitted.substring(0, fitted.length() - 4) + "...";
            }
            graphics.drawString(fitted, x, baseline);
        }

        public void paintComponent(Graphics graphics)
        {
            super.paintComponent(graphics);
            OverviewFrameBuffer.CompletedFrame frame = frameBuffer.currentFrame();
            if(frame == null) {
                return;
            }
            Dimension currentSize = getSize();
            int drawWidth = currentSize.width > 0 ? currentSize.width :
                frame.getWidth();
            int drawHeight = currentSize.height > 0 ? currentSize.height :
                frame.getHeight();
            Graphics2D g2 = (Graphics2D)graphics.create();
            interactionController.getTransform().apply(g2);
            g2.drawImage(frame.getTerrainImage(), 0, 0, drawWidth, drawHeight,
                         null);
            drawHeatmapOverlay(g2, frame.getTerrainMap());
            if(!inspectMode) {
                g2.drawImage(frame.getOverlayImage(), 0, 0, drawWidth, drawHeight,
                             null);
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

        private void drawHeatmapOverlay(Graphics2D g2, TerrainMap frameTerrainMap)
        {
            if(!heatmapVisible || heatmapFacts == null ||
               frameTerrainMap == null || geometry == null) {
                return;
            }
            HeatmapRenderer.render(g2, heatmapFacts, frameTerrainMap, geometry,
                visibleCells());
        }

        Rectangle visibleCells()
        {
            ViewportTransform transform = interactionController.getTransform();
            double zoom = Math.max(0.0001, transform.getZoom());
            int viewW = getWidth() > 0 ? getWidth() : size.width;
            int viewH = getHeight() > 0 ? getHeight() : size.height;
            double worldLeft = -transform.getOffsetX() / zoom;
            double worldRight = (viewW - transform.getOffsetX()) / zoom;
            double worldTop = -transform.getOffsetY() / zoom;
            double worldBottom = (viewH - transform.getOffsetY()) / zoom;
            int colMin = (int)Math.floor(geometry.gridColForPixel(worldLeft));
            int colMax = (int)Math.ceil(geometry.gridColForPixel(worldRight));
            int rowMin = (int)Math.floor(geometry.gridRowForPixel(worldTop));
            int rowMax = (int)Math.ceil(geometry.gridRowForPixel(worldBottom));
            colMin = Math.max(0, Math.min(gridWidth - 1, colMin));
            colMax = Math.max(0, Math.min(gridWidth - 1, colMax));
            rowMin = Math.max(0, Math.min(gridHeight - 1, rowMin));
            rowMax = Math.max(0, Math.min(gridHeight - 1, rowMax));
            return new Rectangle(colMin, rowMin,
                Math.max(1, colMax - colMin + 1),
                Math.max(1, rowMax - rowMin + 1));
        }

        SavannahAnimal findInspectAnimalAt(int mouseX, int mouseY)
        {
            if(!inspectMode) {
                return null;
            }
            SceneDirector.Scene activeScene =
                (!storyboard.isEmpty() && sceneIndex < storyboard.size())
                    ? storyboard.get(sceneIndex) : null;
            double elapsedMs = activeScene == null ? 0.0 :
                (System.nanoTime() - sceneStartNanos) / 1_000_000.0;
            double ambientElapsedMs =
                ((System.nanoTime() - ambientStartNanos) / 1_000_000.0) %
                SceneDirector.AMBIENT_DURATION_MS;

            long activeHit = activeScene == null ? -1L :
                findHitActorId(activeScene.actors, elapsedMs, mouseX, mouseY);
            if(activeHit >= 0) {
                return SimulatorView.this.findAnimalById(activeHit);
            }

            Set<Long> activeActorIds = activeActorIds(activeScene);
            List<SceneDirector.SceneActor> candidates = new ArrayList<>();
            for(SceneDirector.SceneActor actor : ambientActors) {
                if(!activeActorIds.contains(actor.animalId)) {
                    candidates.add(actor);
                }
            }
            long ambientHit =
                findHitActorId(candidates, ambientElapsedMs, mouseX, mouseY);
            if(ambientHit >= 0) {
                return SimulatorView.this.findAnimalById(ambientHit);
            }
            return findAnimalAtScreenCell(mouseX, mouseY);
        }

        private long findHitActorId(List<SceneDirector.SceneActor> actors,
                                    double elapsedMs, int mouseX, int mouseY)
        {
            long bestId = -1L;
            double bestDistance = Double.MAX_VALUE;
            for(SceneDirector.SceneActor actor : actors) {
                double distance = actorHitDistance(actor, elapsedMs,
                                                   mouseX, mouseY);
                if(distance < bestDistance) {
                    bestDistance = distance;
                    bestId = actor.animalId;
                }
            }
            return bestId;
        }

        private double actorHitDistance(SceneDirector.SceneActor actor,
                                        double elapsedMs, int mouseX, int mouseY)
        {
            SceneDirector.Keyframe frame = actor.at(elapsedMs);
            if(frame.alpha <= 0.01) {
                return Double.MAX_VALUE;
            }

            double row = frame.row + 0.5;
            double col = frame.col + 0.5;
            if(actor.tremble) {
                double phase = System.nanoTime() / 1.0e8;
                row += Math.sin(phase) * 0.05;
                col += Math.cos(phase * 1.3) * 0.05;
            }
            double[] adjusted = avoidWaterFootprint(row, col);
            row = adjusted[0];
            col = adjusted[1];

            ViewportTransform transform = interactionController.getTransform();
            int sx = screenX(col);
            int sy = screenY(row);
            int iconPixels = (int)Math.round(
                VisualFootprint.inspectIconPixels(frame.scale, geometry) *
                transform.getZoom());
            int padding = Math.max(4, iconPixels / 5);
            int half = iconPixels / 2 + padding;
            if(mouseX < sx - half || mouseX > sx + half ||
               mouseY < sy - half || mouseY > sy + half) {
                return Double.MAX_VALUE;
            }
            return Math.hypot(mouseX - sx, mouseY - sy);
        }

        private SavannahAnimal findAnimalAtScreenCell(int mouseX, int mouseY)
        {
            if(lastField == null) {
                return null;
            }
            ViewportTransform transform = interactionController.getTransform();
            double zoom = Math.max(0.0001, transform.getZoom());
            double worldX = (mouseX - transform.getOffsetX()) / zoom;
            double worldY = (mouseY - transform.getOffsetY()) / zoom;
            if(worldX < 0 || worldX >= geometry.pixelWidth() ||
               worldY < 0 || worldY >= geometry.pixelHeight()) {
                return null;
            }
            int col = geometry.cellColAtPixel(worldX);
            int row = geometry.cellRowAtPixel(worldY);
            if(row < 0 || row >= gridHeight || col < 0 || col >= gridWidth) {
                return null;
            }
            Animal animal = lastField.getAnimalAt(new Location(row, col));
            if(animal instanceof SavannahAnimal && animal.isAlive()) {
                return (SavannahAnimal)animal;
            }
            return null;
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
            Rectangle cell = geometry.cellBounds(vfx.cellRow, vfx.cellCol);
            int sx = screenX(geometry.gridColForPixel(cell.x));
            int sy = screenY(geometry.gridRowForPixel(cell.y));
            int w = (int)Math.ceil(cell.width *
                interactionController.getTransform().getZoom());
            int h = (int)Math.ceil(cell.height *
                interactionController.getTransform().getZoom());
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
                   !viewport.contains(event.col, event.row) ||
                   lastRenderFrame == null ||
                   lastRenderFrame.animalById(event.actorId) == null) {
                    continue;
                }
                drawEventMarker(g2, event,
                    EventMetadataRegistry.forType(event.type));
            }
            for(HistoricalEventOverlay.Entry entry : historicalEntries) {
                drawEventMarker(g2, entry.getEvent(),
                    EventMetadataRegistry.forType(entry.getEvent().type));
            }
            g2.setFont(old);
        }

        private void drawEventMarker(Graphics2D graphics,
                                     SimulationEvent event,
                                     EventMetadata metadata)
        {
            if(event == null || metadata == null ||
               metadata.getIconText().isEmpty()) {
                return;
            }
            int sx = screenX(event.col + 0.5);
            int sy = screenY(event.row + 0.5);
            Color base = metadata.getColor();
            graphics.setColor(new Color(base.getRed(), base.getGreen(),
                                        base.getBlue(), 210));
            graphics.drawString(metadata.getIconText(), sx - 4, sy + 5);
        }

        private void drawSceneActor(Graphics2D g2,
                                    SceneDirector.SceneActor actor,
                                    double elapsedMs)
        {
            SceneDirector.Keyframe frame = actor.at(elapsedMs);
            if(frame.alpha <= 0.01) {
                return;
            }

            double row = frame.row + 0.5;
            double col = frame.col + 0.5;
            if(actor.tremble) {
                double phase = System.nanoTime() / 1.0e8;
                row += Math.sin(phase) * 0.05;
                col += Math.cos(phase * 1.3) * 0.05;
            }
            double[] adjusted = avoidWaterFootprint(row, col);
            row = adjusted[0];
            col = adjusted[1];

            ViewportTransform transform = interactionController.getTransform();
            int sx = screenX(col);
            int sy = screenY(row);
            int iconPixels = (int)Math.round(
                VisualFootprint.inspectIconPixels(frame.scale, geometry) *
                transform.getZoom());
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

        private double[] avoidWaterFootprint(double row, double col)
        {
            if(terrainMap == null) {
                return new double[] { row, col };
            }
            int cellRow = clampCell((int)Math.floor(row), gridHeight);
            int cellCol = clampCell((int)Math.floor(col), gridWidth);
            Location center = new Location(cellRow, cellCol);
            if(!terrainMap.isPassable(center) || footprintTouchesWater(row, col)) {
                Location nearest = nearestSafeVisualCell(cellRow, cellCol, 8);
                if(nearest != null) {
                    return new double[] { nearest.row() + 0.5,
                                          nearest.col() + 0.5 };
                }
            }
            if(!footprintTouchesWater(row, col)) {
                return new double[] { row, col };
            }

            double rowPush = 0.0;
            double colPush = 0.0;
            for(int dr = -1; dr <= 1; dr++) {
                for(int dc = -1; dc <= 1; dc++) {
                    if(dr == 0 && dc == 0) {
                        continue;
                    }
                    int sampleRow = clampCell(cellRow + dr, gridHeight);
                    int sampleCol = clampCell(cellCol + dc, gridWidth);
                    if(!terrainMap.isPassable(new Location(sampleRow, sampleCol))) {
                        rowPush -= dr;
                        colPush -= dc;
                    }
                }
            }
            double length = Math.sqrt(rowPush * rowPush + colPush * colPush);
            if(length <= 0.0) {
                return new double[] { row, col };
            }
            double adjustedRow = row + rowPush / length * 0.22;
            double adjustedCol = col + colPush / length * 0.22;
            if(footprintTouchesWater(adjustedRow, adjustedCol)) {
                adjustedRow = cellRow + 0.5;
                adjustedCol = cellCol + 0.5;
            }
            return new double[] { adjustedRow, adjustedCol };
        }

        private boolean footprintTouchesWater(double row, double col)
        {
            return VisualFootprint.inspectCenterTouchesWater(row, col, 1.0,
                                                             terrainMap,
                                                             geometry);
        }

        private Location nearestSafeVisualCell(int row, int col, int maxRange)
        {
            for(int range = 1; range <= maxRange; range++) {
                for(int dr = -range; dr <= range; dr++) {
                    for(int dc = -range; dc <= range; dc++) {
                        if(Math.max(Math.abs(dr), Math.abs(dc)) != range) {
                            continue;
                        }
                        int nextRow = clampCell(row + dr, gridHeight);
                        int nextCol = clampCell(col + dc, gridWidth);
                        Location location = new Location(nextRow, nextCol);
                        if(terrainMap.isPassable(location) &&
                           !footprintTouchesWater(nextRow + 0.5,
                                                  nextCol + 0.5)) {
                            return location;
                        }
                    }
                }
            }
            return null;
        }

        private int clampCell(int value, int max)
        {
            return Math.max(0, Math.min(max - 1, value));
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
            return (int)Math.round(geometry.pixelXForGridCol(col) *
                                   transform.getZoom() +
                                   transform.getOffsetX());
        }

        private int screenY(double row)
        {
            ViewportTransform transform = interactionController.getTransform();
            return (int)Math.round(geometry.pixelYForGridRow(row) *
                                   transform.getZoom() +
                                   transform.getOffsetY());
        }

        private double quadBezier(double p0, double p1, double p2, double t)
        {
            double u = 1.0 - t;
            return u * u * p0 + 2.0 * u * t * p1 + t * t * p2;
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

    private static class ThirstIcon implements Icon
    {
        public int getIconWidth()
        {
            return 14;
        }

        public int getIconHeight()
        {
            return 14;
        }

        public void paintIcon(java.awt.Component component, Graphics graphics,
                              int x, int y)
        {
            graphics.setColor(new Color(43, 136, 210));
            graphics.fillOval(x + 4, y + 2, 6, 9);
            graphics.setColor(Color.darkGray);
            graphics.drawOval(x + 4, y + 2, 6, 9);
        }
    }

}
