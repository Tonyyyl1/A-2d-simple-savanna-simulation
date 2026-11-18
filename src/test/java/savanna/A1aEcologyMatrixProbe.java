package savanna;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fixed-seed ecology checkpoints used by A1a-R0 and later A1a-R3 comparisons. */
public final class A1aEcologyMatrixProbe
{
    private static final long[] DEFAULT_ECOLOGY_SEEDS =
        {1111L, 2111L, 3111L, 4111L, 5111L};

    private A1aEcologyMatrixProbe() {}

    public static void main(String[] args)
    {
        int steps = argument(args, 0, 1000);
        int interval = argument(args, 1, 100);
        long terrainSeed = longArgument(args, 2,
            SimulationConfig.DEFAULT_TERRAIN_SEED);
        long[] seeds = args.length > 3 ? parseSeeds(args[3]) : DEFAULT_ECOLOGY_SEEDS;
        if(steps <= 0 || interval <= 0 || seeds.length == 0) {
            throw new IllegalArgumentException("steps, interval and seeds are required");
        }
        System.out.println("A1A_ECOLOGY_MATRIX schema=R0" +
            " terrainSeed=" + terrainSeed + " seeds=" + join(seeds) +
            " steps=" + steps + " interval=" + interval);
        for(long seed : seeds) {
            run(seed, terrainSeed, steps, interval);
        }
    }

    private static void run(long ecologySeed, long terrainSeed, int steps,
                            int interval)
    {
        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(ecologySeed).terrainSeed(terrainSeed).build();
        Randomizer.reset();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        Map<String, Integer> minimums = new LinkedHashMap<>(
            simulator.getField().countLivingBySpecies());
        for(int index = 1; index <= steps && simulator.getField().isViable();
            index++) {
            simulator.simulateOneStep();
            updateMinimums(minimums, simulator.getField().countLivingBySpecies());
            if(index % interval == 0 || index == steps ||
               !simulator.getField().isViable()) {
                System.out.println("MATRIX seed=" + ecologySeed +
                    " step=" + simulator.getStep() +
                    " counts=" + simulator.getField().countLivingBySpecies() +
                    " minimums=" + minimums +
                    " deaths=" + simulator.getContext().getDeathSummary() +
                    " balance=" + format(balance(simulator.getField())) +
                    " fingerprint=" + SimulationFingerprint.capture(simulator));
            }
        }
        System.out.println("MATRIX_FINAL seed=" + ecologySeed +
            " completed=" + simulator.getStep() +
            " viable=" + simulator.getField().isViable() +
            " balance=" + format(balance(simulator.getField())) +
            " counts=" + simulator.getField().countLivingBySpecies());
    }

    private static void updateMinimums(Map<String, Integer> minimums,
                                       Map<String, Integer> counts)
    {
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String name = factory.getProfile().getName();
            int value = counts.getOrDefault(name, 0);
            minimums.put(name, Math.min(minimums.getOrDefault(name, value), value));
        }
    }

    private static double balance(Field field)
    {
        int minimum = Integer.MAX_VALUE;
        int maximum = 0;
        Map<String, Integer> counts = field.countLivingBySpecies();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            int value = counts.getOrDefault(factory.getProfile().getName(), 0);
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        return minimum <= 0 ? Double.POSITIVE_INFINITY :
            (double)maximum / minimum;
    }

    private static int argument(String[] args, int index, int fallback)
    {
        return args.length > index ? Integer.parseInt(args[index]) : fallback;
    }

    private static long longArgument(String[] args, int index, long fallback)
    {
        return args.length > index ? Long.parseLong(args[index]) : fallback;
    }

    private static long[] parseSeeds(String value)
    {
        String[] parts = value.split(",");
        long[] seeds = new long[parts.length];
        for(int index = 0; index < parts.length; index++) {
            seeds[index] = Long.parseLong(parts[index].trim());
        }
        return seeds;
    }

    private static String join(long[] values)
    {
        StringBuilder result = new StringBuilder();
        for(long value : values) {
            if(result.length() > 0) result.append(',');
            result.append(value);
        }
        return result.toString();
    }

    private static String format(double value)
    {
        return String.format("%.2f", value);
    }
}
