package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Represent a rectangular grid of field positions.
 * Each position stores at most one animal.
 */
public class Field
{
    private final int depth;
    private final int width;
    private final Map<Location, Animal> field;
    private final List<Animal> animals;
    /**
     * Identity-to-cell ledger used to update an already placed animal without
     * rescanning the whole field. The public field/list views remain the
     * source of truth; this is their private index.
     */
    private final Map<Animal, Location> locationsByAnimal;

    public Field(int depth, int width)
    {
        this.depth = depth;
        this.width = width;
        field = new HashMap<>();
        animals = new ArrayList<>();
        locationsByAnimal = new HashMap<>();
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
        if(!tryPlaceAnimal(anAnimal, location)) {
            throw new IllegalStateException("Location already occupied: " + location);
        }
    }

    /**
     * Atomically place or move a living animal. A successful return means the
     * map, list, private index, and {@link Animal#getLocation()} all agree.
     * A rejected placement changes none of them.
     * @return true only when the complete public placement commits.
     */
    public boolean tryPlaceAnimal(Animal anAnimal, Location location)
    {
        if(anAnimal == null || !anAnimal.isAlive() || location == null ||
           location.row() < 0 || location.row() >= depth ||
           location.col() < 0 || location.col() >= width) {
            return false;
        }
        Animal other = field.get(location);
        if(other != null && other != anAnimal && other.isAlive()) {
            return false;
        }
        if(other != null && other != anAnimal) {
            animals.remove(other);
            locationsByAnimal.remove(other);
        }
        Location previous = locationsByAnimal.get(anAnimal);
        if(previous != null && !previous.equals(location)) {
            field.remove(previous, anAnimal);
        }
        field.put(location, anAnimal);
        if(previous == null) {
            animals.add(anAnimal);
        }
        locationsByAnimal.put(anAnimal, location);
        anAnimal.setLocation(location);
        return true;
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
        return getFreeAdjacentLocations(location, Randomizer.getRandom());
    }

    public List<Location> getFreeAdjacentLocations(Location location, Random rand)
    {
        return getFreeLocationsWithin(location, 1, rand);
    }

    /**
     * Get a shuffled list of free locations within a distance.
     */
    public List<Location> getFreeLocationsWithin(Location location, int range)
    {
        return getFreeLocationsWithin(location, range, Randomizer.getRandom());
    }

    public List<Location> getFreeLocationsWithin(Location location, int range,
                                                 Random rand)
    {
        List<Location> free = new LinkedList<>();
        List<Location> nearby = getLocationsWithin(location, range, rand);
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
        return getAdjacentLocations(location, Randomizer.getRandom());
    }

    public List<Location> getAdjacentLocations(Location location, Random rand)
    {
        return getLocationsWithin(location, 1, rand);
    }

    /**
     * Return a shuffled list of locations within the given range.
     */
    public List<Location> getLocationsWithin(Location location, int range)
    {
        return getLocationsWithin(location, range, Randomizer.getRandom());
    }

    public List<Location> getLocationsWithin(Location location, int range,
                                             Random rand)
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
        locationsByAnimal.clear();
    }

    /**
     * Remove an animal from this field.
     */
    public void removeAnimal(Animal animal)
    {
        if(animal != null) {
            animals.remove(animal);
            Location location = locationsByAnimal.remove(animal);
            if(location != null) {
                field.remove(location, animal);
            }
            else {
                field.values().remove(animal);
            }
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
                locationsByAnimal.remove(entry.getValue());
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

    /** Verify the map, list, alive state, and animal locations agree. */
    public boolean hasConsistentState()
    {
        Set<Animal> mappedAnimals = new HashSet<>();
        for(Map.Entry<Location, Animal> entry : field.entrySet()) {
            Animal animal = entry.getValue();
            if(animal == null || !animal.isAlive() ||
               !entry.getKey().equals(animal.getLocation()) ||
               !mappedAnimals.add(animal)) {
                return false;
            }
        }
        Set<Animal> listedAnimals = new HashSet<>(animals);
        if(listedAnimals.size() != animals.size() ||
           !listedAnimals.equals(mappedAnimals) ||
           locationsByAnimal.size() != mappedAnimals.size() ||
           !locationsByAnimal.keySet().equals(mappedAnimals)) {
            return false;
        }
        for(Animal animal : animals) {
            if(animal == null || !animal.isAlive() ||
               !animal.getLocation().equals(locationsByAnimal.get(animal))) {
                return false;
            }
        }
        return true;
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
