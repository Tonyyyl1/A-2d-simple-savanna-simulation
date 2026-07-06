import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Headless stability tests for the combined ecosystem.
 */
public class SimulationIntegrationTests
{
    private static final double BALANCE_LIMIT = 12.0;

    public static void runDefault()
    {
        TestSupport.startGroup("Integration tests");
        runStabilityTest("1000 step stability", 1000);
        TestSupport.finishGroup();
    }

    public static void runFull()
    {
        TestSupport.startGroup("Full integration tests");
        runStabilityTest("5000 step stability", 5000);
        TestSupport.finishGroup();
    }

    private static void runStabilityTest(String name, int steps)
    {
        Randomizer.reset();
        long startTime = System.currentTimeMillis();
        Simulator simulator = new Simulator(false);
        simulator.setVerbose(false);
        Map<String, Integer> minimumCounts =
            new LinkedHashMap<>(simulator.getField().countLivingBySpecies());
        for(int step = 1; step <= steps && simulator.getField().isViable(); step++) {
            simulator.simulateOneStep();
            if(step % 100 == 0 || step == steps) {
                Map<String, Integer> counts = simulator.getField().countLivingBySpecies();
                for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
                    String species = factory.getProfile().getName();
                    int count = counts.getOrDefault(species, 0);
                    int previousMinimum = minimumCounts.getOrDefault(species, count);
                    minimumCounts.put(species, Math.min(previousMinimum, count));
                }
            }
        }
        long elapsed = System.currentTimeMillis() - startTime;
        Map<String, Integer> finalCounts = simulator.getField().countLivingBySpecies();
        double ratio = finalBalanceRatio(finalCounts);
        System.out.println(name + " final counts: " + finalCounts);
        System.out.println(name + " minimum counts: " + minimumCounts);
        System.out.println(name + " balance ratio: " +
                           String.format("%.2f", ratio));
        System.out.println(name + " elapsed ms: " + elapsed);

        TestSupport.assertEquals(name + " completed requested steps",
                                 steps, simulator.getStep());
        TestSupport.assertTrue(name + " keeps all species alive",
                               simulator.getField().isViable());
        TestSupport.assertTrue(name + " final balance stays within limit",
                               ratio <= BALANCE_LIMIT);
    }

    private static double finalBalanceRatio(Map<String, Integer> counts)
    {
        int min = Integer.MAX_VALUE;
        int max = 0;
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            int count = counts.getOrDefault(factory.getProfile().getName(), 0);
            min = Math.min(min, count);
            max = Math.max(max, count);
        }
        if(min <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return (double)max / min;
    }
}
