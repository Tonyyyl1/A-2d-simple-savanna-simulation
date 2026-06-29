import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds compact human-readable summaries for terminal simulation runs.
 */
public class SimulationDiagnostics
{
    private SimulationDiagnostics()
    {
    }

    public static String toConsoleLine(int step, Field field, SimulationContext context)
    {
        return toConsoleLine(step, capture(field, context), null);
    }

    public static String toConsoleLine(int step, DiagnosticSnapshot snapshot,
                                       DiagnosticSnapshot previous)
    {

        return "Step " + step +
               " | " + snapshot.weather +
               " | " + snapshot.time +
               " | Pop=" + snapshot.total + " " + snapshot.speciesCounts +
               " | Pred/Prey=" + snapshot.predatorPreyRatio + "%" +
               " | Grass=" + snapshot.grassPercent + "%" +
               " | Disease=" + snapshot.infected + "(" +
               snapshot.diseasePercent + "%)" +
               " | Avg stamina=" + snapshot.averageStamina + "%" +
               " | Avg survival=" + snapshot.averageSurvival + "%" +
               " | Low survival=" + snapshot.lowSurvival +
               " | Low stamina=" + snapshot.lowStamina +
               " | Starving=" + snapshot.starving +
               " | Trend: " + formatTrend(snapshot, previous) +
               " | Event: " + formatEvent(snapshot, previous) +
               " | Signals: " + formatSignals(snapshot);
    }

    public static String toFinalSummary(Field field, SimulationContext context)
    {
        DiagnosticSnapshot snapshot = capture(field, context);
        return "Step " + context.getStep() +
               " | " + snapshot.weather +
               " | " + snapshot.time +
               " | Pop=" + snapshot.total + " " + snapshot.speciesCounts +
               " | Pred/Prey=" + snapshot.predatorPreyRatio + "%" +
               " | Grass=" + snapshot.grassPercent + "%" +
               " | Disease=" + snapshot.infected + "(" +
               snapshot.diseasePercent + "%)" +
               " | Avg stamina=" + snapshot.averageStamina + "%" +
               " | Avg survival=" + snapshot.averageSurvival + "%" +
               " | Low survival=" + snapshot.lowSurvival +
               " | Low stamina=" + snapshot.lowStamina +
               " | Starving=" + snapshot.starving +
               " | Signals: " + formatSignals(snapshot);
    }

    public static DiagnosticSnapshot capture(Field field, SimulationContext context)
    {
        DiagnosticStats stats = collect(field);
        int grassPercent = percent(context.getFoodSystem().getAverageFoodLevel() * 100.0);
        int diseasePercent = stats.total == 0 ? 0 :
            percent((stats.infected * 100.0) / stats.total);
        int predatorPreyRatio = stats.prey == 0 ? 999 :
            percent((stats.predators * 100.0) / stats.prey);
        return new DiagnosticSnapshot(
            context.getWeatherSystem().getDisplayText(),
            context.getClock().getDisplayText(),
            stats.total,
            stats.predators,
            stats.prey,
            stats.infected,
            stats.lowSurvival,
            stats.lowStamina,
            stats.starving,
            stats.averageStamina(),
            stats.averageSurvival(),
            grassPercent,
            diseasePercent,
            predatorPreyRatio,
            speciesCounts(field));
    }

    private static DiagnosticStats collect(Field field)
    {
        DiagnosticStats stats = new DiagnosticStats();
        for(Animal animal : field.getAnimals()) {
            if(animal instanceof SavannahAnimal && animal.isAlive()) {
                SavannahAnimal savannahAnimal = (SavannahAnimal)animal;
                stats.total++;
                if(savannahAnimal.getProfile().isPredator()) {
                    stats.predators++;
                }
                else {
                    stats.prey++;
                }
                if(savannahAnimal.isInfected()) {
                    stats.infected++;
                }
                if(savannahAnimal.getSurvivalPercent() < 30.0) {
                    stats.lowSurvival++;
                }
                if(savannahAnimal.getStaminaStage() == StaminaStage.LOW) {
                    stats.lowStamina++;
                }
                if(savannahAnimal.getStarvingSteps() > 0) {
                    stats.starving++;
                }
                stats.totalStamina += savannahAnimal.getStaminaPercent();
                stats.totalSurvival += savannahAnimal.getSurvivalPercent();
            }
        }
        return stats;
    }

    private static Map<String, Integer> speciesCounts(Field field)
    {
        Map<String, Integer> ordered = new LinkedHashMap<>();
        Map<String, Integer> counts = field.countLivingBySpecies();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String name = factory.getProfile().getName();
            ordered.put(name, counts.getOrDefault(name, 0));
        }
        return ordered;
    }

    public static String formatTrend(DiagnosticSnapshot snapshot,
                                     DiagnosticSnapshot previous)
    {
        if(previous == null) {
            return "baseline";
        }
        return "pop " + signed(snapshot.total - previous.total) +
               ", grass " + signed(snapshot.grassPercent - previous.grassPercent) +
               "pp, disease " +
               signed(snapshot.diseasePercent - previous.diseasePercent) + "pp" +
               ", survival " +
               signed(snapshot.averageSurvival - previous.averageSurvival) + "pp";
    }

    public static String formatShortTrend(DiagnosticSnapshot snapshot,
                                          DiagnosticSnapshot previous)
    {
        if(previous == null) {
            return "Trend baseline";
        }
        return "Pop " + signed(snapshot.total - previous.total) +
               " | Grass " +
               signed(snapshot.grassPercent - previous.grassPercent) + "pp" +
               " | Survival " +
               signed(snapshot.averageSurvival - previous.averageSurvival) + "pp";
    }

    public static String formatSignals(DiagnosticSnapshot snapshot)
    {
        StringBuilder signals = new StringBuilder();
        appendSignal(signals, snapshot.grassPercent < 45, "grazing stress");
        appendSignal(signals, snapshot.diseasePercent >= 10, "disease pressure");
        appendSignal(signals, snapshot.lowSurvival > Math.max(8, snapshot.total / 18),
                     "survival pressure");
        appendSignal(signals, snapshot.lowStamina > Math.max(8, snapshot.total / 18),
                     "stamina fatigue");
        appendSignal(signals, snapshot.predatorPreyRatio > 35, "predator pressure");
        if(signals.length() == 0) {
            signals.append("stable");
        }
        return signals.toString();
    }

    public static String formatEvent(DiagnosticSnapshot snapshot,
                                     DiagnosticSnapshot previous)
    {
        if(previous == null) {
            return "baseline readout";
        }
        if(snapshot.weather.indexOf("Fog") >= 0) {
            return "fog reduced hunting visibility";
        }
        int survivalChange = snapshot.averageSurvival - previous.averageSurvival;
        int lowSurvivalChange = snapshot.lowSurvival - previous.lowSurvival;
        if(lowSurvivalChange > Math.max(5, snapshot.total / 40) ||
           survivalChange <= -8) {
            return "survival pressure rising";
        }
        int populationChange = snapshot.total - previous.total;
        if(populationChange >= Math.max(20, previous.total / 20)) {
            return "population recovering";
        }
        if(populationChange <= -Math.max(20, previous.total / 25)) {
            return "population falling";
        }
        int diseaseChange = snapshot.diseasePercent - previous.diseasePercent;
        if(diseaseChange <= -2 || snapshot.infected < previous.infected - 5) {
            return "disease pressure falling";
        }
        if(diseaseChange >= 2 || snapshot.infected > previous.infected + 5) {
            return "disease pressure rising";
        }
        int grassChange = snapshot.grassPercent - previous.grassPercent;
        if(grassChange <= -8) {
            return "grass reserves falling";
        }
        if(grassChange >= 8) {
            return "grass reserves recovering";
        }
        return "ecosystem holding steady";
    }

    private static void appendSignal(StringBuilder signals, boolean active,
                                     String signal)
    {
        if(active) {
            if(signals.length() > 0) {
                signals.append(", ");
            }
            signals.append(signal);
        }
    }

    private static int percent(double value)
    {
        return Math.max(0, Math.min(999, (int)Math.round(value)));
    }

    private static String signed(int value)
    {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    public static class DiagnosticSnapshot
    {
        final String weather;
        final String time;
        final int total;
        final int predators;
        final int prey;
        final int infected;
        final int lowSurvival;
        final int lowStamina;
        final int starving;
        final int averageStamina;
        final int averageSurvival;
        final int grassPercent;
        final int diseasePercent;
        final int predatorPreyRatio;
        final Map<String, Integer> speciesCounts;

        DiagnosticSnapshot(String weather, String time, int total, int predators,
                           int prey, int infected, int lowSurvival,
                           int lowStamina, int starving, int averageStamina,
                           int averageSurvival, int grassPercent,
                           int diseasePercent, int predatorPreyRatio,
                           Map<String, Integer> speciesCounts)
        {
            this.weather = weather;
            this.time = time;
            this.total = total;
            this.predators = predators;
            this.prey = prey;
            this.infected = infected;
            this.lowSurvival = lowSurvival;
            this.lowStamina = lowStamina;
            this.starving = starving;
            this.averageStamina = averageStamina;
            this.averageSurvival = averageSurvival;
            this.grassPercent = grassPercent;
            this.diseasePercent = diseasePercent;
            this.predatorPreyRatio = predatorPreyRatio;
            this.speciesCounts = speciesCounts;
        }
    }

    private static class DiagnosticStats
    {
        int total;
        int predators;
        int prey;
        int infected;
        int lowSurvival;
        int lowStamina;
        int starving;
        double totalStamina;
        double totalSurvival;

        int averageStamina()
        {
            return total == 0 ? 0 : (int)Math.round(totalStamina / total);
        }

        int averageSurvival()
        {
            return total == 0 ? 0 : (int)Math.round(totalSurvival / total);
        }
    }
}
