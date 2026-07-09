import java.util.Random;

/**
 * Weather model with a light Markov-style transition between states.
 */
public class SeasonalWeatherSystem implements WeatherSystem
{
    private final Random rand;
    private WeatherType currentWeather = WeatherType.CLEAR;
    private int stepsUntilChange = 18;

    public SeasonalWeatherSystem()
    {
        this(Randomizer.getRandom());
    }

    public SeasonalWeatherSystem(Random rand)
    {
        this.rand = rand;
    }

    public void update(SimulationContext context)
    {
        stepsUntilChange--;
        if(stepsUntilChange <= 0) {
            currentWeather = chooseNextWeather();
            stepsUntilChange = 12 + rand.nextInt(25);
        }
    }

    public WeatherType getCurrentWeather()
    {
        return currentWeather;
    }

    public int getHuntingRange(SavannahAnimal predator)
    {
        double adjusted = predator.getProfile().getSearchRange() * currentWeather.getVisibilityModifier();
        if(predator.isInfected()) {
            adjusted *= 0.80;
        }
        return Math.max(1, (int)Math.round(adjusted));
    }

    public double getInfectionModifier()
    {
        return currentWeather.getInfectionModifier();
    }

    public double getVisibilityModifier()
    {
        return currentWeather.getVisibilityModifier();
    }

    public double getPlantGrowthModifier()
    {
        return currentWeather.getPlantGrowthModifier();
    }

    public String getDisplayText()
    {
        return currentWeather.getDisplayName();
    }

    private WeatherType chooseNextWeather()
    {
        double roll = rand.nextDouble();
        if(currentWeather == WeatherType.DROUGHT) {
            if(roll < 0.18) {
                return WeatherType.RAIN;
            }
            else if(roll < 0.30) {
                return WeatherType.FOG;
            }
            else if(roll < 0.62) {
                return WeatherType.CLEAR;
            }
            else {
                return WeatherType.DROUGHT;
            }
        }
        else if(currentWeather == WeatherType.RAIN) {
            if(roll < 0.32) {
                return WeatherType.FOG;
            }
            else if(roll < 0.72) {
                return WeatherType.CLEAR;
            }
            else if(roll < 0.85) {
                return WeatherType.RAIN;
            }
            else {
                return WeatherType.DROUGHT;
            }
        }
        else if(currentWeather == WeatherType.FOG) {
            if(roll < 0.20) {
                return WeatherType.RAIN;
            }
            else if(roll < 0.76) {
                return WeatherType.CLEAR;
            }
            else if(roll < 0.90) {
                return WeatherType.FOG;
            }
            else {
                return WeatherType.DROUGHT;
            }
        }
        else {
            if(roll < 0.18) {
                return WeatherType.RAIN;
            }
            else if(roll < 0.32) {
                return WeatherType.FOG;
            }
            else if(roll < 0.48) {
                return WeatherType.DROUGHT;
            }
            else {
                return WeatherType.CLEAR;
            }
        }
    }
}
