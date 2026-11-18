package savanna;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Headless stability tests for the combined ecosystem.
 */
public class SimulationIntegrationTests
{
    private static final double BALANCE_LIMIT = 12.0;
    /**
     * Plan v3.0 §2.4/M3: ecology outcomes gate only with ECO_ENFORCE=1 (from
     * M3 on); before that they are reported while structure still gates.
     */
    private static final boolean ENFORCE_ECOLOGY =
        "1".equals(System.getenv("ECO_ENFORCE"));

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

        TestSupport.assertTrue(name + " ends with a consistent field",
                               simulator.getField().hasConsistentState());
        boolean completed = simulator.getStep() == steps;
        boolean alive = simulator.getField().isViable();
        boolean balanced = ratio <= BALANCE_LIMIT;
        if(ENFORCE_ECOLOGY) {
            TestSupport.assertEquals(name + " completed requested steps",
                                     steps, simulator.getStep());
            TestSupport.assertTrue(name + " keeps all species alive", alive);
            TestSupport.assertTrue(name + " final balance stays within limit",
                                   balanced);
        }
        else {
            System.out.println("ECO_INFO " + name + " rules=" +
                EcologyRules.VERSION + " completedSteps=" + simulator.getStep() +
                " allAlive=" + alive + " balanceOk=" + balanced +
                " verdict=" + (completed && alive && balanced ? "PASS" : "FAIL") +
                " (informational until M3)");
        }
    }

    /**
     * v3.2 U6: balance is measured against the target pyramid (largest over
     * smallest of count / target); the default 240 x 360 map needs no scaling.
     */
    private static double finalBalanceRatio(Map<String, Integer> counts)
    {
        Map<String, Double> values = new java.util.LinkedHashMap<>();
        java.util.Set<String> participants = new java.util.LinkedHashSet<>();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String species = factory.getProfile().getName();
            participants.add(species);
            values.put(species, (double)counts.getOrDefault(species, 0));
        }
        return EcologyTargets.normalizedBalance(values, participants, 1.0);
    }
}
