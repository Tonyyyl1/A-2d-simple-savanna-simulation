import java.util.Map;

/**
 * Shared systems and step-level state.
 */
public class SimulationContext
{
    private final SimulationClock clock;
    private final WeatherSystem weatherSystem;
    private final DiseaseSystem diseaseSystem;
    private final PredationSystem predationSystem;
    private final BreedingSystem breedingSystem;
    private final FoodSystem foodSystem;
    private Map<String, Integer> populationSnapshot;
    private int step;

    public SimulationContext(int depth, int width)
    {
        clock = new SimulationClock();
        weatherSystem = new SeasonalWeatherSystem();
        diseaseSystem = new SavannahDiseaseSystem();
        predationSystem = new FoodChainPredationSystem();
        breedingSystem = new MateFindingBreedingSystem();
        foodSystem = new GrasslandFoodSystem(depth, width);
    }

    public void startStep(int step, Field field)
    {
        this.step = step;
        clock.setStep(step);
        weatherSystem.update(this);
        foodSystem.grow(this, field);
        populationSnapshot = field.countLivingBySpecies();
    }

    public int getStep()
    {
        return step;
    }

    public SimulationClock getClock()
    {
        return clock;
    }

    public WeatherSystem getWeatherSystem()
    {
        return weatherSystem;
    }

    public DiseaseSystem getDiseaseSystem()
    {
        return diseaseSystem;
    }

    public PredationSystem getPredationSystem()
    {
        return predationSystem;
    }

    public BreedingSystem getBreedingSystem()
    {
        return breedingSystem;
    }

    public FoodSystem getFoodSystem()
    {
        return foodSystem;
    }

    public int getPopulation(String speciesName)
    {
        Integer count = populationSnapshot.get(speciesName);
        return count == null ? 0 : count;
    }

    public String getEnvironmentSummary(Field field)
    {
        int infected = diseaseSystem.countInfected(field);
        int grassPercent = (int)Math.round(foodSystem.getAverageFoodLevel() * 100);
        return "Weather: " + weatherSystem.getDisplayText() +
               " | " + clock.getDisplayText() +
               " | Grass: " + grassPercent + "%" +
               " | Diseased: " + infected;
    }
}
