package savanna;

/** Emits a per-step M1R behavior fingerprint for the R1 no-integration gate. */
public final class A1aR1EcologyFingerprintProbe
{
    private A1aR1EcologyFingerprintProbe()
    {
    }

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
        Randomizer.reset();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        for(int step = 1; step <= steps; step++) {
            simulator.simulateOneStep();
            System.out.println("A1A_R1_FINGERPRINT step=" + step +
                " value=" + SimulationFingerprint.capture(simulator));
        }
        System.out.println("A1A_R1_FINGERPRINT PASS steps=" + steps +
            " final=" + SimulationFingerprint.capture(simulator));
    }
}
