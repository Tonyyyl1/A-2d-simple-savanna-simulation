import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One row in the map/disease pressure experiment table.
 */
public class ExperimentResult
{
    final SimulationConfig config;
    final int requestedSteps;
    final int completedSteps;
    final int initialPopulation;
    final int finalPopulation;
    final int peakInfected;
    final int diseaseDeaths;
    final double finalBalanceRatio;
    final double finalDensity;
    final boolean extinct;
    final boolean overDense;
    final Map<String, Integer> minimumCounts;
    final Map<String, Integer> finalCounts;

    public ExperimentResult(SimulationConfig config, int requestedSteps,
                            int completedSteps, int initialPopulation,
                            int finalPopulation, int peakInfected,
                            int diseaseDeaths, double finalBalanceRatio,
                            double finalDensity, boolean extinct,
                            boolean overDense, Map<String, Integer> minimumCounts,
                            Map<String, Integer> finalCounts)
    {
        this.config = config;
        this.requestedSteps = requestedSteps;
        this.completedSteps = completedSteps;
        this.initialPopulation = initialPopulation;
        this.finalPopulation = finalPopulation;
        this.peakInfected = peakInfected;
        this.diseaseDeaths = diseaseDeaths;
        this.finalBalanceRatio = finalBalanceRatio;
        this.finalDensity = finalDensity;
        this.extinct = extinct;
        this.overDense = overDense;
        this.minimumCounts = new LinkedHashMap<>(minimumCounts);
        this.finalCounts = new LinkedHashMap<>(finalCounts);
    }

    public boolean isCandidate()
    {
        return !extinct && finalBalanceRatio <= 12.0 && !overDense &&
               completedSteps >= requestedSteps;
    }

    public String toTableRow()
    {
        return config.describe() +
               " | steps=" + completedSteps + "/" + requestedSteps +
               " | initial=" + initialPopulation +
               " | final=" + finalPopulation +
               " | final counts=" + finalCounts +
               " | min=" + minimumCounts +
               " | balance=" + String.format("%.2f", finalBalanceRatio) +
               " | density=" + String.format("%.3f", finalDensity) +
               " | peak infected=" + peakInfected +
               " | disease deaths=" + diseaseDeaths +
               " | extinct=" + extinct +
               " | overDense=" + overDense +
               " | candidate=" + isCandidate();
    }
}
