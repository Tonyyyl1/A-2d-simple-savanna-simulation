import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Shared systems and step-level state.
 */
public class SimulationContext
{
    private static final int MAX_RECENT_EVENTS = 600;

    private final Random random;
    private final SimulationClock clock;
    private final WeatherSystem weatherSystem;
    private final DiseaseSystem diseaseSystem;
    private final PredationSystem predationSystem;
    private final BreedingSystem breedingSystem;
    private final FoodSystem foodSystem;
    private final TerrainMap terrainMap;
    private final ThirstSystem thirstSystem;
    private final SimulationConfig config;
    private Map<String, Integer> populationSnapshot;
    private int step;
    private List<SimulationEvent> currentEvents = new ArrayList<>();
    private List<SimulationEvent> previousEvents = new ArrayList<>();
    private final List<SimulationEvent> recentEvents = new ArrayList<>();
    private final List<SimulationEventListener> eventListeners =
        new ArrayList<>();

    public SimulationContext(int depth, int width)
    {
        this(depth, width, SimulationConfig.baseline());
    }

    public SimulationContext(int depth, int width, SimulationConfig config)
    {
        this.config = config == null ? SimulationConfig.baseline() : config;
        random = new Random(this.config.getRandomSeed());
        clock = new SimulationClock();
        weatherSystem = new SeasonalWeatherSystem(random);
        diseaseSystem = new SavannahDiseaseSystem(random);
        predationSystem = new FoodChainPredationSystem(random);
        breedingSystem = new MateFindingBreedingSystem(random);
        foodSystem = new GrasslandFoodSystem(depth, width, random);
        terrainMap = new TerrainMap(depth, width, this.config.getTerrainSeed());
        thirstSystem = this.config.isThirstEnabled()
            ? new SavannahThirstSystem(terrainMap) : null;
    }

    public void startStep(int step, Field field)
    {
        this.step = step;
        previousEvents = currentEvents;
        currentEvents = new ArrayList<>();
        clock.setStep(step);
        weatherSystem.update(this);
        foodSystem.grow(this, field);
        populationSnapshot = field.countLivingBySpecies();
    }

    public void recordEvent(SimulationEvent event)
    {
        if(event == null) {
            return;
        }
        currentEvents.add(event);
        recentEvents.add(event);
        while(recentEvents.size() > MAX_RECENT_EVENTS) {
            recentEvents.remove(0);
        }
        for(SimulationEventListener listener : eventListeners) {
            listener.onEvent(event);
        }
    }

    public void addEventListener(SimulationEventListener listener)
    {
        if(listener != null && !eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }

    public void removeEventListener(SimulationEventListener listener)
    {
        eventListeners.remove(listener);
    }

    public List<SimulationEvent> getCurrentEvents()
    {
        return Collections.unmodifiableList(currentEvents);
    }

    public List<SimulationEvent> getPreviousEvents()
    {
        return Collections.unmodifiableList(previousEvents);
    }

    public List<SimulationEvent> getRecentEvents()
    {
        return Collections.unmodifiableList(recentEvents);
    }

    public int getStep()
    {
        return step;
    }

    public Random getRandom()
    {
        return random;
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

    public ThirstSystem getThirstSystem()
    {
        return thirstSystem;
    }

    public boolean isThirstEnabled()
    {
        return thirstSystem != null;
    }

    public TerrainMap getTerrainMap()
    {
        return terrainMap;
    }

    public SimulationConfig getConfig()
    {
        return config;
    }

    public double getBreedingProbability(SpeciesProfile profile)
    {
        return config.breedingProbability(profile);
    }

    public int getFoundingPopulation(SpeciesProfile profile)
    {
        return config.foundingPopulation(profile);
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
               " | Diseased: " + infected +
               " | Thirst: " + (isThirstEnabled() ? "on" : "off");
    }
}
