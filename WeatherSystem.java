/**
 * Interface for weather behaviour.
 */
public interface WeatherSystem
{
    void update(SimulationContext context);

    WeatherType getCurrentWeather();

    int getHuntingRange(SavannahAnimal predator);

    double getInfectionModifier();

    double getVisibilityModifier();

    double getPlantGrowthModifier();

    String getDisplayText();
}
