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
    /** V1 frame export: receivers, per-generation numbering and the last frame sent. */
    private final java.util.List<FrameSink> frameSinks = new java.util.ArrayList<>();
    private final SimFrameBuilder frameBuilder = new SimFrameBuilder();
    private int runGeneration;
    private int frameSequence;
    private SimFrame lastFrame;
    private WorldDescriptor lastWorld;
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

        publishFrame(false);
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
        runGeneration++;
        frameSequence = 0;
        lastFrame = null;
        frameBuilder.reset();
        eventAccumulator.clear();
        identityAllocator.reset();
        context = new SimulationContext(field.getDepth(), field.getWidth(), runtimeConfig);
        attachEventListeners(context);
        populate();
        context.startStep(step, field);
        publishWorldAndInitialFrame();
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
        TerrainMap terrain = context.getTerrainMap();
        int passable = 0;
        for(int row = 0; row < field.getDepth(); row++) {
            for(int col = 0; col < field.getWidth(); col++) {
                if(terrain.isPassable(new Location(row, col))) passable++;
            }
        }
        double scale = EcologyTargets.scale(passable);
        EcologyParameters parameters = EcologyParameters.current();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            SpeciesProfile profile = factory.getProfile();
            int target = (int)Math.round(parameters.initialCount(profile.getName()) * scale);
            if(target <= 0) continue;
            placeFounders(factory, target, rand);
        }
    }

    /**
     * C-0: founders go to empty land cells drawn with probability proportional
     * to the species' habitat spawn modifier (cells with modifier 0 are never
     * used); each pick consumes the context random stream.
     */
    private void placeFounders(SpeciesFactory factory, int target, Random rand)
    {
        TerrainMap terrain = context.getTerrainMap();
        int depth = field.getDepth();
        int width = field.getWidth();
        double[] cumulative = new double[depth * width];
        double total = 0.0;
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                Location location = new Location(row, col);
                if(terrain.isPassable(location)) {
                    total += Math.max(0.0, HabitatPreferences.spawnModifier(
                        factory.getProfile(), terrain.getTerrainAt(location)));
                }
                cumulative[row * width + col] = total;
            }
        }
        if(total <= 0.0) return;
        int placed = 0;
        int attempts = 0;
        int attemptLimit = target * 200 + 10_000;
        while(placed < target && attempts++ < attemptLimit) {
            double draw = rand.nextDouble() * total;
            int low = 0;
            int high = cumulative.length - 1;
            while(low < high) {
                int middle = (low + high) >>> 1;
                if(cumulative[middle] > draw) high = middle;
                else low = middle + 1;
            }
            Location location = new Location(low / width, low % width);
            double weight = low == 0 ? cumulative[0] : cumulative[low] - cumulative[low - 1];
            if(weight <= 0.0 || field.getAnimalAt(location) != null) continue;
            field.placeAnimal(factory.create(true, location, context.getRandom(),
                identityAllocator), location);
            placed++;
        }
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

    private void attachEventListeners(SimulationContext context)
    {
        for(SimulationEventListener listener : eventListeners) {
            context.addEventListener(listener);
        }
        for(PredatorRecruitmentAssessmentListener listener :
            predatorRecruitmentListeners) {
            context.addPredatorRecruitmentAssessmentListener(listener);
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

    /**
     * Registers a frame receiver. It immediately receives the world description
     * and the latest frame (frame 0 when none has been sent in this generation).
     */
    public void addFrameSink(FrameSink sink)
    {
        if(sink == null || frameSinks.contains(sink)) return;
        frameSinks.add(sink);
        if(lastWorld == null || lastWorld.runGeneration != runGeneration) {
            lastWorld = WorldDescriptor.capture(runGeneration, context);
        }
        sink.onWorld(lastWorld);
        if(lastFrame == null) {
            lastFrame = frameBuilder.build(runGeneration, frameSequence++, step, context,
                field, false);
        }
        sink.onFrame(lastFrame);
    }

    public void removeFrameSink(FrameSink sink)
    {
        frameSinks.remove(sink);
    }

    /** Sets the generation number that the next {@link #reset()} increments from. */
    public void setInitialRunGeneration(int generation)
    {
        runGeneration = generation;
        lastWorld = null;
    }

    public int getRunGeneration()
    {
        return runGeneration;
    }

    private void publishWorldAndInitialFrame()
    {
        lastWorld = null;
        if(frameSinks.isEmpty()) return;
        lastWorld = WorldDescriptor.capture(runGeneration, context);
        for(FrameSink sink : frameSinks) sink.onWorld(lastWorld);
        publishFrame(false);
    }

    private void publishFrame(boolean last)
    {
        if(frameSinks.isEmpty()) return;
        lastFrame = frameBuilder.build(runGeneration, frameSequence++, step, context, field, last);
        for(FrameSink sink : frameSinks) sink.onFrame(lastFrame);
    }

    private void finishRecording(String reason)
    {
        for(FrameSink sink : frameSinks) sink.onRunEnd(reason);
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
