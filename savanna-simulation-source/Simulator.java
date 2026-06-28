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

    private Field field;
    private int step;
    private final SimulatorView view;
    private final SimulationRecorder recorder;
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
        this(DEFAULT_DEPTH, DEFAULT_WIDTH, true);
    }

    /**
     * Construct a simulation that can optionally suppress the GUI.
     */
    public Simulator(boolean showView)
    {
        this(DEFAULT_DEPTH, DEFAULT_WIDTH, showView);
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
        if(width <= 0 || depth <= 0) {
            System.out.println("The dimensions must be >= zero.");
            System.out.println("Using default values.");
            depth = DEFAULT_DEPTH;
            width = DEFAULT_WIDTH;
        }

        field = new Field(depth, width);
        context = new SimulationContext(depth, width);
        view = showView ? new SimulatorView(depth, width) : null;
        pauseLock = new Object();
        recorder = showView
            ? new WindowedSimulationRecorder("savanna-simulation-step-report.html")
            : new NullSimulationRecorder();
        verbose = showView;
        stepDelay = showView ? 8 : 0;
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

                public void requestStopAndExit()
                {
                    Simulator.this.requestStopAndExit();
                }
            });
        }

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
        Collections.shuffle(animals, Randomizer.getRandom());
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
        Randomizer.reset();
        context = new SimulationContext(field.getDepth(), field.getWidth());
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
        synchronized(pauseLock) {
            paused = !paused;
            if(view != null) {
                view.setPaused(paused);
            }
            if(!paused) {
                pauseLock.notifyAll();
            }
        }
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
        Random rand = Randomizer.getRandom();
        field.clear();
        for(int row = 0; row < field.getDepth(); row++) {
            for(int col = 0; col < field.getWidth(); col++) {
                for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
                    if(rand.nextDouble() <= factory.getProfile().getCreationProbability()) {
                        Location location = new Location(row, col);
                        field.placeAnimal(factory.create(true, location), location);
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
        return "Step " + step + " | " + field.countLivingBySpecies() +
               " | " + context.getEnvironmentSummary(field);
    }

    /**
     * Give every species a founder group at the start only.
     */
    private void placeFoundingGroups()
    {
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            int placed = 0;
            int target = factory.getProfile().getFoundingPopulation();
            while(placed < target) {
                Location location = randomFreeLocation();
                if(location == null) {
                    return;
                }
                field.placeAnimal(factory.create(true, location), location);
                placed++;
            }
        }
    }

    private Location randomFreeLocation()
    {
        Random rand = Randomizer.getRandom();
        for(int tries = 0; tries < 500; tries++) {
            Location location = new Location(rand.nextInt(field.getDepth()),
                                             rand.nextInt(field.getWidth()));
            if(field.getAnimalAt(location) == null) {
                return location;
            }
        }
        for(int row = 0; row < field.getDepth(); row++) {
            for(int col = 0; col < field.getWidth(); col++) {
                Location location = new Location(row, col);
                if(field.getAnimalAt(location) == null) {
                    return location;
                }
            }
        }
        return null;
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
