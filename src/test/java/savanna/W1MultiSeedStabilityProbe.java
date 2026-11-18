package savanna;

import java.util.LinkedHashMap;
import java.util.Map;

/** Candidate-only W1 guard for the intended thirst-off rest-accounting change. */
public final class W1MultiSeedStabilityProbe
{
    private static final long[] ECOLOGY_SEEDS = { 1111L, 2222L, 3333L };
    private static final int STEPS = 1000;

    private W1MultiSeedStabilityProbe()
    {
    }

    public static void main(String[] args)
    {
        for(long seed : ECOLOGY_SEEDS) {
            SimulationConfig config = SimulationConfig.builder()
                .randomSeed(seed)
                .terrainSeed(SimulationConfig.DEFAULT_TERRAIN_SEED)
                .thirstEnabled(false)
                .build();
            Simulator simulator = new Simulator(config, false);
            simulator.setVerbose(false);
            Map<String, Integer> minimum = new LinkedHashMap<>(
                simulator.getField().countLivingBySpecies());
            for(int step = 0; step < STEPS && simulator.getField().isViable(); step++) {
                simulator.simulateOneStep();
                mergeMinimum(minimum, simulator.getField().countLivingBySpecies());
            }
            Map<String, Integer> finalCounts =
                simulator.getField().countLivingBySpecies();
            double balance = balanceRatio(finalCounts);
            boolean passed = simulator.getStep() == STEPS &&
                simulator.getField().isViable();
            System.out.println("W1_SEED seed=" + seed + " final=" + finalCounts +
                " minimum=" + minimum + " balance=" +
                String.format("%.2f", balance) + " status=" +
                (passed ? "PASS" : "FAIL"));
            if(!passed) {
                System.exit(1);
            }
        }
        System.out.println("W1_MULTI_SEED_STABILITY PASS seeds=" +
            ECOLOGY_SEEDS.length + " steps=" + STEPS);
    }

    private static void mergeMinimum(Map<String, Integer> minimum,
                                     Map<String, Integer> current)
    {
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String species = factory.getProfile().getName();
            int count = current.getOrDefault(species, 0);
            int prior = minimum.getOrDefault(species, count);
            minimum.put(species, Math.min(prior, count));
        }
    }

    private static double balanceRatio(Map<String, Integer> counts)
    {
        int minimum = Integer.MAX_VALUE;
        int maximum = 0;
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            int count = counts.getOrDefault(factory.getProfile().getName(), 0);
            minimum = Math.min(minimum, count);
            maximum = Math.max(maximum, count);
        }
        return minimum <= 0 ? Double.POSITIVE_INFINITY
                            : (double)maximum / minimum;
    }
}
