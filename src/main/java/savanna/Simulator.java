package savanna;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A predator-prey simulator for an African savanna.
 */
public class Simulator
{
    private static final int DEFAULT_WIDTH = 120;
    private static final int DEFAULT_DEPTH = 80;

    private final SimulationConfig config;
    private final ResolvedRuntimeConfig runtimeConfig;
    private final ObservationSchedule observationSchedule;
    private final AnimalIdentityAllocator identityAllocator;
    private Field field;
    private int step;
    private final SimulatorView view;
    private final SimulationRecorder recorder;
    private final List<SimulationEventListener> eventListeners;
    private final List<PredatorRecruitmentAssessmentListener>
        predatorRecruitmentListeners;
    private final List<LongHorizonTrophicLedger> longHorizonTrophicLedgers;
    private final EventAccumulator eventAccumulator;
    private final RenderFramePublisher renderFramePublisher;
    private SimulationContext context;
    private boolean verbose;
    private int stepDelay;
    private final Object pauseLock;
    private volatile boolean paused;
    private volatile boolean stopRequested;
    private volatile boolean exitWhenStopped;
    private boolean recorderFinished;
    private int lastVisibleFrameStep;
    private boolean renderFrameCaptureEnabled;

    /**
     * Construct a simulation field with default size and a GUI.
     */
    public Simulator()
    {
        this(SimulationConfig.default3x(), true);
    }

    /**
     * Construct a simulation that can optionally suppress the GUI.
     */
    public Simulator(boolean showView)
    {
        this(SimulationConfig.default3x(), showView);
    }

    public Simulator(SimulationConfig config, boolean showView)
    {
        this(ResolvedRuntimeConfig.fromLegacy(config), showView);
    }

    public Simulator(ResolvedRuntimeConfig runtimeConfig, boolean showView)
    {
        this(runtimeConfig == null ? DEFAULT_DEPTH
                                   : runtimeConfig.getEcologyConfig().getScaledDepth(),
             runtimeConfig == null ? DEFAULT_WIDTH
                                   : runtimeConfig.getEcologyConfig().getScaledWidth(),
             showView, runtimeConfig);
    }

    /**
     * Create a simulation field with the given size and a GUI.
     */
    public Simulator(int depth, int width)
    {
        this(depth, width, true);
    }

    /**
     * Create a simulation field with the given size.
     */
    public Simulator(int depth, int width, boolean showView)
    {
        this(depth, width, showView, SimulationConfig.baseline());
    }

    public Simulator(int depth, int width, boolean showView,
                     SimulationConfig config)
    {
        this(depth, width, showView, ResolvedRuntimeConfig.fromLegacy(config));
    }

    public Simulator(int depth, int width, boolean showView,
                     ResolvedRuntimeConfig runtimeConfig)
    {
        if(width <= 0 || depth <= 0) {
            System.out.println("The dimensions must be >= zero.");
            System.out.println("Using default values.");
            depth = DEFAULT_DEPTH;
            width = DEFAULT_WIDTH;
        }

        this.runtimeConfig = runtimeConfig == null
            ? ResolvedRuntimeConfig.fromLegacy(SimulationConfig.baseline())
            : runtimeConfig;
        this.config = this.runtimeConfig.getEcologyConfig();
        identityAllocator = new AnimalIdentityAllocator();
        observationSchedule = new ObservationSchedule(
            this.runtimeConfig.getObservationConfig());
        field = new Field(depth, width);
        context = new SimulationContext(depth, width, this.runtimeConfig);
        view = showView ? SimulatorView.createOnEventDispatchThread(
            depth, width, this.config.describe(),
            this.runtimeConfig.getDisplayConfig()) : null;
        eventListeners = new java.util.ArrayList<>();
        predatorRecruitmentListeners = new java.util.ArrayList<>();
        longHorizonTrophicLedgers = new java.util.ArrayList<>();
        eventAccumulator = new EventAccumulator(
            this.runtimeConfig.getObservationConfig());
        renderFramePublisher = new RenderFramePublisher();
        renderFrameCaptureEnabled = showView;
        pauseLock = new Object();
        recorder = showView
            ? new WindowedSimulationRecorder("savanna-simulation-step-report.html",
                this.runtimeConfig.getObservationConfig())
            : new NullSimulationRecorder();
        verbose = showView;
        stepDelay = showView
            ? this.runtimeConfig.getDisplayConfig().getBoundaryDelayMillis() : 0;
        paused = false;
        stopRequested = false;
        exitWhenStopped = false;
        recorderFinished = false;
        lastVisibleFrameStep = -1;
        if(view != null) {
            view.setControlHandler(new SimulationControlHandler() {
                public void togglePaused()
                {
                    Simulator.this.togglePaused();
                }

                public void setPaused(boolean paused)
                {
                    Simulator.this.setPaused(paused);
                }

                public boolean isPaused()
                {
                    return Simulator.this.isPaused();
                }

                public void requestStopAndExit()
                {
                    Simulator.this.requestStopAndExit();
                }
            });
        }
        addEventListener(eventAccumulator);

        reset();
    }

    /**
     * Run a long simulation of 200000 steps.
     */
    public void runLongSimulation()
    {
        simulate(200000);
    }

    /**
     * Run the simulation for the given number of steps.
     */
    public void simulate(int numSteps)
    {
        reportStats();
        int targetStep = step + numSteps;
        for(int n = 1; n <= numSteps && field.isViable() && !stopRequested; n++) {
            waitWhilePaused();
            if(stopRequested) {
                break;
            }
            simulateOneStep();
            if(view != null && stepDelay > 0 && shouldRefreshVisualStep()) {
                delay(stepDelay);
            }
        }
        String reason;
        if(step >= targetStep && field.isViable()) {
            reason = "Completed " + numSteps + " requested steps.";
        }
        else if(!field.isViable()) {
            reason = "Stopped early because at least one species became extinct.";
        }
        else if(stopRequested) {
            reason = "Stopped by user at step " + step + ".";
        }
        else {
            reason = "Stopped at step " + step + ".";
        }
        if(renderFrameCaptureEnabled) publishVisibleRenderFrameIfNeeded();
        refreshVisualStatus();
        finishRecording(reason);
        if(exitWhenStopped) {
            System.exit(0);
        }
    }

    /**
     * Run the simulation from its current state for a single step.
     */
    public void simulateOneStep()
    {
        step++;
        context.startStep(step, field);

        PositionStepCoordinator positions = new PositionStepCoordinator(field,
            context);
        List<Animal> animals = field.getAnimals();
        Collections.shuffle(animals, context.getRandom());
        prepareCriticalWaterReservations(animals,
            positions.createAvailabilityView());
        PositionAvailabilityView availability = positions
            .createAvailabilityView();
        for(Animal anAnimal : animals) {
            positions.propose(anAnimal, availability);
        }
        Field nextFieldState = positions.resolveAndCommitCompleteStep();
        nextFieldState.removeDeadAnimals();
        if(!nextFieldState.hasConsistentState()) {
            throw new IllegalStateException(
                "Field transition produced inconsistent animal state at step " + step);
        }

        field = nextFieldState;
        if(renderFrameCaptureEnabled) {
            if(isVisibleFrameBoundary()) {
                publishVisibleRenderFrameIfNeeded();
            }
            else {
                publishRenderFrame();
            }
        }

        reportStats();
        if(shouldRefreshVisualStep()) {
            refreshVisualStatus();
        }
        recorder.recordStep(step, field, context);
    }

    private void prepareCriticalWaterReservations(List<Animal> animals,
                                                  PositionAvailabilityView availability)
    {
        if(!context.isThirstEnabled() || availability == null) return;
        List<SavannahAnimal> critical = new java.util.ArrayList<>();
        for(Animal animal : animals) {
            if(animal instanceof SavannahAnimal && animal.isAlive() &&
               context.getThirstSystem().assess((SavannahAnimal)animal, context)
                   .getStage() == HydrationStage.CRITICAL) {
                critical.add((SavannahAnimal)animal);
            }
        }
        critical.sort((first, second) -> Long.compare(first.getId(), second.getId()));
        java.util.Set<Location> claimed = new java.util.HashSet<>();
        for(SavannahAnimal animal : critical) {
            WaterTargetDecision target = context.getThirstSystem()
                .decideWaterTarget(animal, context);
            Location origin = animal.getLocation();
            Location selected = null;
            int currentDistance = context.getThirstSystem().distanceToTargetPool(origin,
                target.getPoolId());
            for(Location candidate : availability.adjacentCandidates(
                PositionCandidateObservationPhase.CRITICAL_WATER_SCAN,
                animal.getId(), origin, null)) {
                if(claimed.contains(candidate) ||
                   context.getThirstSystem().distanceToTargetPool(candidate,
                       target.getPoolId()) >= currentDistance) continue;
                if(selected == null || candidate.row() < selected.row() ||
                   (candidate.row() == selected.row() &&
                    candidate.col() < selected.col())) selected = candidate;
            }
            if(selected != null && context.reserveCriticalWaterMove(animal, selected)) {
                claimed.add(selected);
            }
        }
    }

    /**
     * Reset the simulation to a starting position.
     */
    public void reset()
    {
        step = 0;
        paused = false;
        stopRequested = false;
        exitWhenStopped = false;
        recorderFinished = false;
        eventAccumulator.clear();
        identityAllocator.reset();
        context = new SimulationContext(field.getDepth(), field.getWidth(), runtimeConfig);
        attachEventListeners(context);
        populate();
        context.startStep(step, field);
        if(renderFrameCaptureEnabled) publishVisibleRenderFrameIfNeeded();
        if(view != null) {
            refreshVisualStatus();
        }
        recorder.start(field, context);
    }

    private void publishRenderFrame()
    {
        if(!renderFrameCaptureEnabled) return;
        renderFramePublisher.publish(RenderFrame.capture(
            step, field, context, field.getWidth(), field.getDepth()));
    }

    /** Publish a forced-final visible frame once, even when it is a cadence boundary. */
    private void publishVisibleRenderFrameIfNeeded()
    {
        if(lastVisibleFrameStep == step) return;
        publishVisibleRenderFrame();
        lastVisibleFrameStep = step;
    }

    /** Freeze a same-step heatmap snapshot for a GUI, final, or PNG boundary. */
    public RenderFrame captureVisibleRenderFrame()
    {
        return VisibleRenderFrameFactory.capture(step, field, context,
            eventAccumulator, field.getWidth(), field.getDepth());
    }

    private void publishVisibleRenderFrame()
    {
        if(!renderFrameCaptureEnabled) return;
        renderFramePublisher.publish(captureVisibleRenderFrame());
    }

    /** Enable retained latest-frame capture for a headless observer. */
    public void enableRenderFrameCapture()
    {
        if(renderFrameCaptureEnabled) return;
        renderFrameCaptureEnabled = true;
        publishVisibleRenderFrameIfNeeded();
    }

    public boolean isRenderFrameCaptureEnabled()
    {
        return renderFrameCaptureEnabled;
    }

    /** True only when this simulator has a durable report recorder. */
    boolean hasPersistentReportRecorder()
    {
        return !(recorder instanceof NullSimulationRecorder);
    }

    public RenderFramePublisher getRenderFramePublisher()
    {
        enableRenderFrameCapture();
        return renderFramePublisher;
    }

    /**
     * Pause or resume the visual simulation.
     */
    public void togglePaused()
    {
        setPaused(!paused);
    }

    /**
     * Explicitly pause or resume the visual simulation.
     */
    public void setPaused(boolean paused)
    {
        synchronized(pauseLock) {
            this.paused = paused;
            if(!this.paused) {
                pauseLock.notifyAll();
            }
        }
        if(view != null) {
            view.setPaused(this.paused);
        }
    }

    public boolean isPaused()
    {
        return paused;
    }

    /**
     * Stop the visual simulation, write the report, and close the application.
     */
    public void requestStopAndExit()
    {
        stopRequested = true;
        exitWhenStopped = true;
        paused = false;
        synchronized(pauseLock) {
            pauseLock.notifyAll();
        }
        if(view != null) {
            view.setStopping();
        }
    }

    /**
     * Randomly populate the field with savanna species.
     */
    private void populate()
    {
        Random rand = context.getRandom();
        field.clear();
        for(int row = 0; row < field.getDepth(); row++) {
            for(int col = 0; col < field.getWidth(); col++) {
                Location location = new Location(row, col);
                if(!context.getTerrainMap().isPassable(location)) {
                    continue;
                }
                for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
                    double probability = config.creationProbability(factory.getProfile()) *
                        HabitatPreferences.spawnModifier(factory.getProfile(),
                            context.getTerrainMap().getTerrainAt(location));
                    if(rand.nextDouble() <= probability) {
                        field.placeAnimal(factory.create(true, location,
                                                         context.getRandom(),
                                                         identityAllocator),
                                          location);
                        break;
                    }
                }
            }
        }
        placeFoundingGroups();
    }

    /**
     * Report on the number of each type of animal in the field.
     */
    public void reportStats()
    {
        if(verbose) {
            System.out.print("Step: " + step + " ");
            field.fieldStats();
        }
    }

    /**
     * @return The current simulation step.
     */
    public int getStep()
    {
        return step;
    }

    /**
     * @return The current field.
     */
    public Field getField()
    {
        return field;
    }

    AnimalIdentityAllocator getIdentityAllocator()
    {
        return identityAllocator;
    }

    /**
     * @return The current shared simulation context.
     */
    public SimulationContext getContext()
    {
        return context;
    }

    public void addEventListener(SimulationEventListener listener)
    {
        if(listener != null && !eventListeners.contains(listener)) {
            eventListeners.add(listener);
            context.addEventListener(listener);
        }
    }

    public void removeEventListener(SimulationEventListener listener)
    {
        eventListeners.remove(listener);
        context.removeEventListener(listener);
    }

    /**
     * Registers a V24-OBS-1A observer and preserves it across reset(). The
     * listener remains observational and is not a behavior-policy extension.
     */
    public void addPredatorRecruitmentAssessmentListener(
        PredatorRecruitmentAssessmentListener listener)
    {
        if(listener != null && !predatorRecruitmentListeners.contains(listener)) {
            predatorRecruitmentListeners.add(listener);
            context.addPredatorRecruitmentAssessmentListener(listener);
        }
    }

    public void removePredatorRecruitmentAssessmentListener(
        PredatorRecruitmentAssessmentListener listener)
    {
        predatorRecruitmentListeners.remove(listener);
        context.removePredatorRecruitmentAssessmentListener(listener);
    }

    /**
     * Registers the V24-OBS-1B streaming ledger on every immutable fact
     * channel it consumes and preserves that registration across reset().
     */
    public void addLongHorizonTrophicLedger(LongHorizonTrophicLedger ledger)
    {
        if(ledger == null || longHorizonTrophicLedgers.contains(ledger)) return;
        longHorizonTrophicLedgers.add(ledger);
        addEventListener(ledger);
        addPredatorRecruitmentAssessmentListener(ledger);
        context.addStepFrameLedgerListener(ledger);
        context.addPf1aFactListener(ledger);
        context.addLongHorizonTrophicStepListener(ledger);
    }

    public void removeLongHorizonTrophicLedger(LongHorizonTrophicLedger ledger)
    {
        if(ledger == null || !longHorizonTrophicLedgers.remove(ledger)) return;
        removeEventListener(ledger);
        removePredatorRecruitmentAssessmentListener(ledger);
        context.removeStepFrameLedgerListener(ledger);
        context.removePf1aFactListener(ledger);
        context.removeLongHorizonTrophicStepListener(ledger);
    }

    /**
     * Turn step-by-step console output on or off.
     */
    public void setVerbose(boolean verbose)
    {
        this.verbose = verbose;
    }

    /**
     * Set the delay between visual steps.
     */
    public void setStepDelay(int milliseconds)
    {
        stepDelay = Math.max(0, milliseconds);
    }

    /**
     * Run a visual simulation with a readable animation speed.
     */
    public void runVisualSimulation(int numSteps)
    {
        setStepDelay(runtimeConfig.getDisplayConfig().getBoundaryDelayMillis());
        simulate(numSteps);
    }

    /**
     * @return A compact summary of the simulation.
     */
    public String getSummary()
    {
        return SimulationDiagnostics.toFinalSummary(field, context);
    }

    /**
     * Give every species a founder group at the start only.
     */
    private void placeFoundingGroups()
    {
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            int placed = 0;
            int target = config.foundingPopulation(factory.getProfile());
            while(placed < target) {
                Location location = randomFreeLocation();
                if(location == null) {
                    return;
                }
                field.placeAnimal(factory.create(true, location,
                                                 context.getRandom(),
                                                 identityAllocator),
                                  location);
                placed++;
            }
        }
    }

    private Location randomFreeLocation()
    {
        Random rand = context.getRandom();
        for(int tries = 0; tries < 500; tries++) {
            Location location = new Location(rand.nextInt(field.getDepth()),
                                             rand.nextInt(field.getWidth()));
            if(field.getAnimalAt(location) == null &&
               context.getTerrainMap().isPassable(location)) {
                return location;
            }
        }
        for(int row = 0; row < field.getDepth(); row++) {
            for(int col = 0; col < field.getWidth(); col++) {
                Location location = new Location(row, col);
                if(field.getAnimalAt(location) == null &&
                   context.getTerrainMap().isPassable(location)) {
                    return location;
                }
            }
        }
        return null;
    }

    private void attachEventListeners(SimulationContext context)
    {
        for(SimulationEventListener listener : eventListeners) {
            context.addEventListener(listener);
        }
        for(PredatorRecruitmentAssessmentListener listener :
            predatorRecruitmentListeners) {
            context.addPredatorRecruitmentAssessmentListener(listener);
        }
        for(LongHorizonTrophicLedger ledger : longHorizonTrophicLedgers) {
            context.addStepFrameLedgerListener(ledger);
            context.addPf1aFactListener(ledger);
            context.addLongHorizonTrophicStepListener(ledger);
        }
    }

    private void waitWhilePaused()
    {
        synchronized(pauseLock) {
            while(paused && !stopRequested) {
                try {
                    pauseLock.wait();
                }
                catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    stopRequested = true;
                    break;
                }
            }
        }
    }

    private void finishRecording(String reason)
    {
        if(!recorderFinished) {
            recorder.finish(field, context, reason);
            recorderFinished = true;
        }
    }

    private boolean shouldRefreshVisualStep()
    {
        return view != null && isVisibleFrameBoundary();
    }

    private boolean isVisibleFrameBoundary()
    {
        return observationSchedule.isVisibleBoundary(step);
    }

    private void refreshVisualStatus()
    {
        if(view != null) {
            view.showStatus(renderFramePublisher.latest(), field, context);
        }
    }

    /**
     * Pause for a given time.
     */
    private void delay(int milliseconds)
    {
        try {
            Thread.sleep(milliseconds);
        }
        catch(InterruptedException e) {
            // ignore
        }
    }
}
