import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs map-size and disease-pressure experiments without changing the normal
 * simulator defaults.
 */
public class SimulationExperimentRunner
{
    private static final int FAST_STEPS = 1000;
    private static final int FULL_STEPS = 5000;
    private static final double BALANCE_RATIO_LIMIT = 12.0;
    private static final double OVER_DENSE_LIMIT = 0.30;

    public static void main(String[] args)
    {
        String mode = args.length > 0 ? args[0].toLowerCase() : "full";
        boolean smoke = "smoke".equals(mode);
        boolean target3x = "target3x".equals(mode);
        boolean best3x = "best3x".equals(mode);
        List<SimulationConfig> configs = best3x ? best3xConfigs() :
            (target3x ? target3xConfigs() :
            (smoke ? smokeConfigs() : experimentConfigs()));

        System.out.println("Savanna map/disease pressure experiments");
        System.out.println("Mode: " + (smoke ? "smoke" :
                           (target3x ? "target3x" :
                           (best3x ? "best3x" : "full matrix"))));
        System.out.println("Columns: config | steps | initial | final | final counts | " +
                           "min | balance | density | peak infected | disease deaths | " +
                           "extinct | overDense | candidate");

        List<ExperimentResult> candidates = new ArrayList<>();
        for(SimulationConfig config : configs) {
            ExperimentResult fast = run(config, FAST_STEPS);
            System.out.println("FAST | " + fast.toTableRow());
            if(!smoke && fast.isCandidate()) {
                ExperimentResult full = run(config, FULL_STEPS);
                System.out.println("FULL | " + full.toTableRow());
                if(full.isCandidate()) {
                    candidates.add(full);
                }
            }
            else if(smoke && fast.isCandidate()) {
                candidates.add(fast);
            }
        }

        System.out.println();
        System.out.println("Candidate configs: " + candidates.size());
        for(ExperimentResult candidate : candidates) {
            System.out.println(candidate.toTableRow());
        }
    }

    public static ExperimentResult run(SimulationConfig config, int steps)
    {
        Randomizer.reset();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        Field field = simulator.getField();
        int initialPopulation = field.getLivingPopulation();
        int peakInfected = simulator.getContext().getDiseaseSystem().countInfected(field);
        Map<String, Integer> minimumCounts =
            new LinkedHashMap<>(field.countLivingBySpecies());

        int completed = 0;
        while(completed < steps && simulator.getField().isViable()) {
            simulator.simulateOneStep();
            completed++;
            Field current = simulator.getField();
            peakInfected = Math.max(peakInfected,
                simulator.getContext().getDiseaseSystem().countInfected(current));
            updateMinimums(minimumCounts, current.countLivingBySpecies());
        }

        Field finalField = simulator.getField();
        Map<String, Integer> finalCounts = finalField.countLivingBySpecies();
        double finalBalanceRatio = finalBalanceRatio(finalCounts);
        double finalDensity = density(finalField);
        boolean extinct = !finalField.isViable();
        boolean overDense = finalDensity > OVER_DENSE_LIMIT;
        int diseaseDeaths = 0;
        if(simulator.getContext().getDiseaseSystem() instanceof SavannahDiseaseSystem) {
            diseaseDeaths = ((SavannahDiseaseSystem)
                simulator.getContext().getDiseaseSystem()).getDiseaseDeaths();
        }
        return new ExperimentResult(config, steps, completed, initialPopulation,
            finalField.getLivingPopulation(), peakInfected, diseaseDeaths,
            finalBalanceRatio, finalDensity, extinct, overDense, minimumCounts,
            finalCounts);
    }

    private static List<SimulationConfig> experimentConfigs()
    {
        List<SimulationConfig> configs = new ArrayList<>();
        double[] diseaseTransmission = {1.0, 1.25, 1.5, 2.0};
        double[] diseaseFatality = {1.0, 1.5, 2.0};
        for(int scale = 2; scale <= 4; scale++) {
            double creation = 1.0 / (scale * scale);
            double founding = 1.0;
            double breeding = 0.75;
            for(double transmission : diseaseTransmission) {
                for(double fatality : diseaseFatality) {
                    configs.add(new SimulationConfig(scale, creation, founding,
                        breeding, transmission, fatality));
                }
            }
        }
        return configs;
    }

    private static List<SimulationConfig> smokeConfigs()
    {
        List<SimulationConfig> configs = new ArrayList<>();
        configs.add(new SimulationConfig(2, 0.25, 1.0, 0.75, 1.0, 1.0));
        configs.add(new SimulationConfig(3, 1.0 / 9.0, 1.0, 0.75, 1.5, 1.5));
        configs.add(new SimulationConfig(4, 1.0 / 16.0, 1.0, 0.75, 2.0, 2.0));
        return configs;
    }

    private static List<SimulationConfig> target3xConfigs()
    {
        List<SimulationConfig> configs = new ArrayList<>();
        configs.add(new SimulationConfig(3, 1.0 / 9.0, 1.0, 0.65,
            1.5, 1.5, 2.2, 0.85, 1.7, 0.90, 1.7, 0.70));
        configs.add(new SimulationConfig(3, 1.0 / 9.0, 1.0, 0.58,
            1.75, 1.75, 2.6, 0.75, 2.0, 0.85, 2.0, 0.55));
        configs.add(new SimulationConfig(3, 1.0 / 9.0, 1.0, 0.52,
            2.0, 2.0, 3.0, 0.65, 2.3, 0.80, 2.2, 0.45));
        configs.add(new SimulationConfig(3, 0.085, 1.0, 0.52,
            2.0, 2.0, 3.2, 0.58, 2.4, 0.75, 2.4, 0.40));
        configs.add(new SimulationConfig(3, 0.075, 1.0, 0.48,
            2.25, 2.25, 3.5, 0.50, 2.6, 0.70, 2.5, 0.35));
        return configs;
    }

    private static List<SimulationConfig> best3xConfigs()
    {
        List<SimulationConfig> configs = new ArrayList<>();
        configs.add(SimulationConfig.target3xBalanced());
        return configs;
    }

    private static void updateMinimums(Map<String, Integer> minimumCounts,
                                       Map<String, Integer> counts)
    {
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String name = factory.getProfile().getName();
            int count = counts.getOrDefault(name, 0);
            int previousMinimum = minimumCounts.getOrDefault(name, count);
            minimumCounts.put(name, Math.min(previousMinimum, count));
        }
    }

    private static double density(Field field)
    {
        return (double)field.getLivingPopulation() /
               Math.max(1, field.getDepth() * field.getWidth());
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
