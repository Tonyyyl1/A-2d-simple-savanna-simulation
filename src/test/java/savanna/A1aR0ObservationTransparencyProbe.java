package savanna;

/**
 * Confirms that attaching an observation listener leaves the legacy simulation
 * state unchanged.  This protects the R0 diagnostic boundary before A1a-R1
 * introduces any decision data into the transition path.
 */
public final class A1aR0ObservationTransparencyProbe
{
    private A1aR0ObservationTransparencyProbe() {}

    public static void main(String[] args)
    {
        int steps = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        long ecologySeed = args.length > 1 ? Long.parseLong(args[1]) :
            SimulationConfig.DEFAULT_RANDOM_SEED;
        long terrainSeed = args.length > 2 ? Long.parseLong(args[2]) :
            SimulationConfig.DEFAULT_TERRAIN_SEED;
        if(steps <= 0) {
            throw new IllegalArgumentException("steps must be positive");
        }

        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(ecologySeed).terrainSeed(terrainSeed).build();
        String[] withoutObserver = run(config, steps, false);
        String[] withObserver = run(config, steps, true);
        for(int index = 0; index < steps; index++) {
            if(!withoutObserver[index].equals(withObserver[index])) {
                throw new AssertionError("observer changed state at step " +
                    (index + 1) + " without=" + withoutObserver[index] +
                    " with=" + withObserver[index]);
            }
        }
        System.out.println("A1A_R0_OBSERVATION_TRANSPARENCY PASS steps=" +
            steps + " observations=" + observationCount(config, steps) +
            " fingerprint=" + withObserver[steps - 1]);
    }

    private static String[] run(SimulationConfig config, int steps,
                                boolean attachObserver)
    {
        Randomizer.reset();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        long[] observations = {0L};
        if(attachObserver) {
            simulator.getContext().addTransitionObservationListener(
                observation -> observations[0]++);
        }
        String[] fingerprints = new String[steps];
        for(int index = 1; index <= steps; index++) {
            simulator.simulateOneStep();
            fingerprints[index - 1] = SimulationFingerprint.capture(simulator);
        }
        return fingerprints;
    }

    private static long observationCount(SimulationConfig config, int steps)
    {
        Randomizer.reset();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        long[] observations = {0L};
        simulator.getContext().addTransitionObservationListener(
            observation -> observations[0]++);
        for(int index = 0; index < steps; index++) {
            simulator.simulateOneStep();
        }
        return observations[0];
    }
}
