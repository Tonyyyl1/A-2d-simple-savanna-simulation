/**
 * Command-line runner used for stability testing without opening the GUI.
 */
public class SimulationRunner
{
    private static final double BALANCE_RATIO_LIMIT = 12.0;
    private static final int CHECK_INTERVAL = 100;

    public static void main(String[] args)
    {
        int steps = 200000;
        if(args.length > 0) {
            steps = Integer.parseInt(args[0]);
        }

        long startTime = System.currentTimeMillis();
        Simulator simulator = new Simulator(false);
        simulator.setVerbose(false);
        SimulationDiagnostics.DiagnosticSnapshot previousSnapshot = null;
        SimulationDiagnostics.DiagnosticSnapshot currentSnapshot =
            SimulationDiagnostics.capture(simulator.getField(), simulator.getContext());
        System.out.println(SimulationDiagnostics.toConsoleLine(
            simulator.getStep(), currentSnapshot, previousSnapshot));
        previousSnapshot = currentSnapshot;
        java.util.Map<String, Integer> minimumCounts =
            new java.util.LinkedHashMap<>(simulator.getField().countLivingBySpecies());
        boolean viable = simulator.getField().isViable();
        for(int index = 1; index <= steps && viable; index++) {
            simulator.simulateOneStep();
            if(index % CHECK_INTERVAL == 0 || index == steps) {
                java.util.Map<String, Integer> counts = simulator.getField().countLivingBySpecies();
                for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
                    String name = factory.getProfile().getName();
                    int count = counts.getOrDefault(name, 0);
                    int previousMinimum = minimumCounts.getOrDefault(name, count);
                    minimumCounts.put(name, Math.min(previousMinimum, count));
                }
                viable = simulator.getField().isViable();
                currentSnapshot = SimulationDiagnostics.capture(simulator.getField(),
                                                                simulator.getContext());
                System.out.println(SimulationDiagnostics.toConsoleLine(
                    simulator.getStep(), currentSnapshot, previousSnapshot));
                previousSnapshot = currentSnapshot;
            }
        }
        long elapsedMillis = System.currentTimeMillis() - startTime;
        java.util.Map<String, Integer> finalCounts = simulator.getField().countLivingBySpecies();
        double finalBalanceRatio = finalBalanceRatio(finalCounts);
        System.out.println("Final: " + simulator.getSummary());
        System.out.println("Minimum populations: " + minimumCounts);
        System.out.println("Extinction: " + (!simulator.getField().isViable()));
        System.out.println("Final balance ratio: " + String.format("%.2f", finalBalanceRatio));
        System.out.println("Balance limit: " + BALANCE_RATIO_LIMIT);
        System.out.println("Elapsed ms: " + elapsedMillis);

        if(simulator.getStep() < steps || !simulator.getField().isViable() ||
           finalBalanceRatio > BALANCE_RATIO_LIMIT) {
            System.exit(1);
        }
    }

    private static double finalBalanceRatio(java.util.Map<String, Integer> counts)
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
