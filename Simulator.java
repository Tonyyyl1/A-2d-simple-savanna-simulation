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
    private Field field;
    private int step;
    private final SimulatorView view;
    private final SimulationRecorder recorder;
    private final List<SimulationEventListener> eventListeners;
    private final EventAccumulator eventAccumulator;
    private SimulationContext context;
    private boolean verbose;
    private int stepDelay;
    private final Object pauseLock;
    private volatile boolean paused;
    private volatile boolean stopRequested;
    private volatile boolean exitWhenStopped;
    private boolean recorderFinished;

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
        this(config == null ? DEFAULT_DEPTH : config.getScaledDepth(),
             config == null ? DEFAULT_WIDTH : config.getScaledWidth(),
             showView, config);
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
        if(width <= 0 || depth <= 0) {
            System.out.println("The dimensions must be >= zero.");
            System.out.println("Using default values.");
            depth = DEFAULT_DEPTH;
            width = DEFAULT_WIDTH;
        }

        this.config = config == null ? SimulationConfig.baseline() : config;
        field = new Field(depth, width);
        context = new SimulationContext(depth, width, this.config);
        view = showView ? new SimulatorView(depth, width, this.config.describe()) : null;
        eventListeners = new java.util.ArrayList<>();
        eventAccumulator = new EventAccumulator();
        pauseLock = new Object();
        recorder = showView
            ? new WindowedSimulationRecorder("savanna-simulation-step-report.html")
            : new NullSimulationRecorder();
        verbose = showView;
        stepDelay = showView ? 24 : 0;
        paused = false;
        stopRequested = false;
        exitWhenStopped = false;
        recorderFinished = false;
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
            view.setEventAccumulator(eventAccumulator);
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

        Field nextFieldState = new Field(field.getDepth(), field.getWidth());
        List<Animal> animals = field.getAnimals();
        Collections.shuffle(animals, context.getRandom());
        for(Animal anAnimal : animals) {
            anAnimal.act(field, nextFieldState, context);
        }
        nextFieldState.removeDeadAnimals();

        field = nextFieldState;

        reportStats();
        if(shouldRefreshVisualStep()) {
            refreshVisualStatus();
        }
        recorder.recordStep(step, field, context);
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
        Animal.resetIds();
        context = new SimulationContext(field.getDepth(), field.getWidth(), config);
        attachEventListeners(context);
        populate();
        context.startStep(step, field);
        if(view != null) {
            refreshVisualStatus();
        }
        recorder.start(field, context);
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
            if(view != null) {
                view.setPaused(this.paused);
            }
            if(!this.paused) {
                pauseLock.notifyAll();
            }
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
        if(view != null) {
            view.setStopping();
        }
        synchronized(pauseLock) {
            pauseLock.notifyAll();
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
                                                         context.getRandom()),
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
        setStepDelay(24);
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
                                                 context.getRandom()),
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
        return view != null &&
               (step == 0 || step % SimulationSettings.VISUAL_REFRESH_INTERVAL == 0);
    }

    private void refreshVisualStatus()
    {
        if(view != null) {
            view.showStatus(step, field, context);
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
