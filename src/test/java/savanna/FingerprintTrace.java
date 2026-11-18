package savanna;

/**
 * Deterministic fingerprint trace tool for performance refactoring verification.
 * Prints {@code FP <step> <sha256>} every {@code interval} steps.
 */
public final class FingerprintTrace
{
    private FingerprintTrace()
    {
    }

    public static void main(String[] args)
    {
        if(args.length < 4) {
            System.err.println("usage: FingerprintTrace <seed> <steps> <off|on> <interval> " +
                "[--params=key=value,...] " +
                "[--terrain-profile=LEGACY_V1|PROCEDURAL_V2] [--terrain-seed=N]");
            System.exit(2);
        }
        long seed = Long.parseLong(args[0]);
        int steps = Integer.parseInt(args[1]);
        boolean thirst = "on".equals(args[2]);
        int interval = Integer.parseInt(args[3]);
        if(steps <= 0 || interval <= 0) {
            System.err.println("steps and interval must be positive");
            System.exit(2);
        }
        TerrainGenerationProfile terrainProfile = null;
        Long terrainSeed = null;
        for(int index = 4; index < args.length; index++) {
            if(args[index].startsWith("--params=")) {
                EcologyParameters.override(
                    EcologyParameters.defaults().with(args[index].substring(9)));
            }
            else if(args[index].startsWith("--terrain-profile=")) {
                terrainProfile = TerrainGenerationProfile.valueOf(args[index].substring(18));
            }
            else if(args[index].startsWith("--terrain-seed=")) {
                terrainSeed = Long.valueOf(args[index].substring(15));
            }
            else {
                System.err.println("unknown option: " + args[index]);
                System.exit(2);
            }
        }
        SimulationConfig.Builder builder = SimulationConfig.builder()
            .randomSeed(seed).thirstEnabled(thirst);
        if(terrainProfile != null) builder.terrainGenerationProfile(terrainProfile);
        if(terrainSeed != null) builder.terrainSeed(terrainSeed.longValue());
        SimulationConfig config = builder.build();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        for(int step = 1; step <= steps; step++) {
            simulator.simulateOneStep();
            if(step % interval == 0) {
                System.out.println("FP " + step + " " + SimulationFingerprint.capture(simulator));
            }
        }
    }
}
