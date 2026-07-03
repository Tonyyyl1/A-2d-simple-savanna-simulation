import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Represent a rectangular grid of field positions.
 * Each position stores at most one animal.
 */
public class Field
{
    private static final Random rand = Randomizer.getRandom();

    private final int depth;
    private final int width;
    private final Map<Location, Animal> field;
    private final List<Animal> animals;

    public Field(int depth, int width)
    {
        this.depth = depth;
        this.width = width;
        field = new HashMap<>();
        animals = new ArrayList<>();
    }

    /**
     * Place an animal at the given location.
     */
    public void placeAnimal(Animal anAnimal, Location location)
    {
        assert location != null;
        if(anAnimal == null || !anAnimal.isAlive()) {
            return;
        }
        Animal other = field.get(location);
        if(other != null) {
            animals.remove(other);
        }
        field.put(location, anAnimal);
        animals.add(anAnimal);
    }

    /**
     * Return the animal at the given location, if any.
     */
    public Animal getAnimalAt(Location location)
    {
        return field.get(location);
    }

    /**
     * Get a shuffled list of the free adjacent locations.
     */
    public List<Location> getFreeAdjacentLocations(Location location)
    {
        return getFreeLocationsWithin(location, 1);
    }

    /**
     * Get a shuffled list of free locations within a distance.
     */
    public List<Location> getFreeLocationsWithin(Location location, int range)
    {
        List<Location> free = new LinkedList<>();
        List<Location> nearby = getLocationsWithin(location, range);
        for(Location next : nearby) {
            Animal anAnimal = field.get(next);
            if(anAnimal == null || !anAnimal.isAlive()) {
                free.add(next);
            }
        }
        return free;
    }

    /**
     * Return a shuffled list of locations adjacent to the given one.
     */
    public List<Location> getAdjacentLocations(Location location)
    {
        return getLocationsWithin(location, 1);
    }

    /**
     * Return a shuffled list of locations within the given range.
     */
    public List<Location> getLocationsWithin(Location location, int range)
    {
        List<Location> locations = new ArrayList<>();
        if(location != null) {
            int row = location.row();
            int col = location.col();
            for(int roffset = -range; roffset <= range; roffset++) {
                int nextRow = row + roffset;
                if(nextRow >= 0 && nextRow < depth) {
                    for(int coffset = -range; coffset <= range; coffset++) {
                        int nextCol = col + coffset;
                        if(nextCol >= 0 && nextCol < width &&
                           (roffset != 0 || coffset != 0)) {
                            locations.add(new Location(nextRow, nextCol));
                        }
                    }
                }
            }
            Collections.shuffle(locations, rand);
        }
        return locations;
    }

    /**
     * Print the number of each savanna species in the field.
     */
    public void fieldStats()
    {
        Map<String, Integer> counts = countLivingBySpecies();
        StringBuilder details = new StringBuilder();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String name = factory.getProfile().getName();
            details.append(name)
                   .append(": ")
                   .append(counts.getOrDefault(name, 0))
                   .append(' ');
        }
        System.out.println(details);
    }

    /**
     * Count living animals by species name.
     */
    public Map<String, Integer> countLivingBySpecies()
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for(Animal anAnimal : field.values()) {
            if(anAnimal instanceof SavannahAnimal) {
                SavannahAnimal animal = (SavannahAnimal)anAnimal;
                if(animal.isAlive()) {
                    String name = animal.getProfile().getName();
                    counts.put(name, counts.getOrDefault(name, 0) + 1);
                }
            }
        }
        return counts;
    }

    /**
     * @return The total number of living animals.
     */
    public int getLivingPopulation()
    {
        int count = 0;
        for(Animal animal : animals) {
            if(animal.isAlive()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Empty the field.
     */
    public void clear()
    {
        field.clear();
        animals.clear();
    }

    /**
     * Remove an animal from this field.
     */
    public void removeAnimal(Animal animal)
    {
        if(animal != null) {
            animals.remove(animal);
            field.values().remove(animal);
        }
    }

    /**
     * Remove all dead animals from this field.
     */
    public void removeDeadAnimals()
    {
        Iterator<Map.Entry<Location, Animal>> entries = field.entrySet().iterator();
        while(entries.hasNext()) {
            Map.Entry<Location, Animal> entry = entries.next();
            if(!entry.getValue().isAlive()) {
                entries.remove();
            }
        }
        animals.removeIf(animal -> !animal.isAlive());
    }

    /**
     * Return whether every registered species is still alive.
     */
    public boolean isViable()
    {
        Map<String, Integer> counts = countLivingBySpecies();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String name = factory.getProfile().getName();
            if(counts.getOrDefault(name, 0) <= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get a copy of the animal list.
     */
    public List<Animal> getAnimals()
    {
        return new ArrayList<>(animals);
    }

    public int getDepth()
    {
        return depth;
    }

    public int getWidth()
    {
        return width;
    }
}
