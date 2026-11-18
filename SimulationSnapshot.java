import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable world-level snapshot for replay and timeline tools.
 */
public class SimulationSnapshot
{
    public final int step;
    public final String weather;
    public final String time;
    public final int grassPercent;
    public final int livingPopulation;
    public final Map<String, Integer> livingBySpecies;
    private final List<AnimalSnapshot> animals;

    public SimulationSnapshot(int step, Field field, SimulationContext context)
    {
        this.step = step;
        weather = context.getWeatherSystem().getDisplayText();
        time = context.getClock().getDisplayText();
        grassPercent = (int)Math.round(
            context.getFoodSystem().getAverageFoodLevel() * 100);
        livingPopulation = field.getLivingPopulation();
        livingBySpecies = Collections.unmodifiableMap(
            new LinkedHashMap<>(field.countLivingBySpecies()));
        animals = collectAnimals(field);
    }

    public List<AnimalSnapshot> getAnimals()
    {
        return animals;
    }

    private List<AnimalSnapshot> collectAnimals(Field field)
    {
        List<AnimalSnapshot> result = new ArrayList<>();
        for(Animal animal : field.getAnimals()) {
            if(animal instanceof SavannahAnimal) {
                result.add(new AnimalSnapshot((SavannahAnimal)animal));
            }
        }
        return Collections.unmodifiableList(result);
    }
}
