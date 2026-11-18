package savanna;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable environment and diagnostic facts belonging to one RenderFrame. */
public final class RenderMetrics
{
    public final WeatherType weatherType;
    public final DayPhase dayPhase;
    public final String timeText;
    public final String environmentText;
    public final String populationText;
    public final int grassPercent;
    public final int diseasePercent;
    public final int averageSurvival;
    public final int averageStamina;
    public final String signals;
    public final Map<String, Integer> speciesCounts;

    private RenderMetrics(WeatherType weatherType, DayPhase dayPhase,
                          String timeText, String environmentText,
                          String populationText, int grassPercent,
                          int diseasePercent, int averageSurvival,
                          int averageStamina, String signals,
                          Map<String, Integer> speciesCounts)
    {
        this.weatherType = weatherType == null ? WeatherType.CLEAR : weatherType;
        this.dayPhase = dayPhase == null ? DayPhase.DAY : dayPhase;
        this.timeText = timeText == null ? "" : timeText;
        this.environmentText = environmentText == null ? "" : environmentText;
        this.populationText = populationText == null ? "" : populationText;
        this.grassPercent = grassPercent;
        this.diseasePercent = diseasePercent;
        this.averageSurvival = averageSurvival;
        this.averageStamina = averageStamina;
        this.signals = signals == null ? "stable" : signals;
        this.speciesCounts = Collections.unmodifiableMap(
            new LinkedHashMap<>(speciesCounts));
    }

    public static RenderMetrics capture(Field field, SimulationContext context)
    {
        if(field == null || context == null) {
            return empty();
        }
        SimulationDiagnostics.DiagnosticSnapshot snapshot =
            SimulationDiagnostics.capture(field, context);
        Map<String, Integer> counts = new LinkedHashMap<>();
        StringBuilder population = new StringBuilder();
        Map<String, Integer> actual = field.countLivingBySpecies();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String species = factory.getProfile().getName();
            int count = actual.getOrDefault(species, 0);
            counts.put(species, count);
            population.append(species).append(": ").append(count).append(' ');
        }
        return new RenderMetrics(
            context.getWeatherSystem().getCurrentWeather(),
            context.getClock().getPhase(), context.getClock().getDisplayText(),
            context.getEnvironmentSummary(field), population.toString(),
            snapshot.grassPercent, snapshot.diseasePercent,
            snapshot.averageSurvival, snapshot.averageStamina,
            SimulationDiagnostics.formatSignals(snapshot), counts);
    }

    public static RenderMetrics empty()
    {
        return new RenderMetrics(WeatherType.CLEAR, DayPhase.DAY, "", "", "",
                                 0, 0, 0, 0, "stable",
                                 Collections.emptyMap());
    }
}
