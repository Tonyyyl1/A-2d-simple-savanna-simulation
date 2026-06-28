import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects statistics about the animals in the field.
 */
public class FieldStats
{
    private final Map<String, Counter> counters;
    private boolean countsValid;

    public FieldStats()
    {
        counters = new LinkedHashMap<>();
        countsValid = true;
    }

    /**
     * Get details of what is in the field.
     */
    public String getPopulationDetails(Field field)
    {
        StringBuilder details = new StringBuilder();
        if(!countsValid) {
            generateCounts(field);
        }
        for(String key : counters.keySet()) {
            Counter info = counters.get(key);
            details.append(info.getName())
                   .append(": ")
                   .append(info.getCount())
                   .append(' ');
        }
        return details.toString();
    }

    /**
     * Reset all counts.
     */
    public void reset()
    {
        countsValid = false;
        for(String key : counters.keySet()) {
            Counter count = counters.get(key);
            count.reset();
        }
    }

    /**
     * Increment the count for one animal.
     */
    public void incrementCount(Animal animal)
    {
        String name;
        if(animal instanceof SavannahAnimal) {
            name = ((SavannahAnimal)animal).getProfile().getName();
        }
        else {
            name = animal.getClass().getName();
        }

        Counter count = counters.get(name);
        if(count == null) {
            count = new Counter(name);
            counters.put(name, count);
        }
        count.increment();
    }

    /**
     * Indicate that a count has been completed.
     */
    public void countFinished()
    {
        countsValid = true;
    }

    /**
     * Determine whether the simulation is still viable.
     */
    public boolean isViable(Field field)
    {
        return field.isViable();
    }

    private void generateCounts(Field field)
    {
        reset();
        for(int row = 0; row < field.getDepth(); row++) {
            for(int col = 0; col < field.getWidth(); col++) {
                Animal animal = field.getAnimalAt(new Location(row, col));
                if(animal != null && animal.isAlive()) {
                    incrementCount(animal);
                }
            }
        }
        countsValid = true;
    }
}
