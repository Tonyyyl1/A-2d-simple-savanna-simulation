package savanna;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
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
    /** One slot per cell, row * width + col; a slot holds the animal that owns the cell. */
    private final Animal[] cells;
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
        cells = new Animal[depth * width];
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
        int cell = location.row() * width + location.col();
        Animal other = cells[cell];
        if(other != null && other != anAnimal && other.isAlive()) {
            return false;
        }
        if(other != null && other != anAnimal) {
            animals.remove(other);
            locationsByAnimal.remove(other);
        }
        Location previous = locationsByAnimal.get(anAnimal);
        if(previous != null && !previous.equals(location)) {
            clearCell(previous, anAnimal);
        }
        cells[cell] = anAnimal;
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
        if(location == null || location.row() < 0 || location.row() >= depth ||
           location.col() < 0 || location.col() >= width) {
            return null;
        }
        return cells[location.row() * width + location.col()];
    }

    /** Empties the cell of {@code location} when it is still owned by {@code animal}. */
    private void clearCell(Location location, Animal animal)
    {
        if(location.row() < 0 || location.row() >= depth ||
           location.col() < 0 || location.col() >= width) {
            return;
        }
        int cell = location.row() * width + location.col();
        if(cells[cell] == animal) {
            cells[cell] = null;
        }
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
            Animal anAnimal = getAnimalAt(next);
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
        // Counted from the animal list (never by scanning the cells). The result lists the
        // registered species in registry order, then any other species in the order it
        // was first met; a species with no living animal is absent.
        Map<String, Integer> found = new LinkedHashMap<>();
        for(Animal anAnimal : animals) {
            if(anAnimal instanceof SavannahAnimal) {
                SavannahAnimal animal = (SavannahAnimal)anAnimal;
                if(animal.isAlive()) {
                    String name = animal.getProfile().getName();
                    found.put(name, found.getOrDefault(name, 0) + 1);
                }
            }
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String name = factory.getProfile().getName();
            Integer count = found.remove(name);
            if(count != null) counts.put(name, count);
        }
        counts.putAll(found);
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
        Arrays.fill(cells, null);
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
                clearCell(location, animal);
            }
            else {
                for(int cell = 0; cell < cells.length; cell++) {
                    if(cells[cell] == animal) {
                        cells[cell] = null;
                        break;
                    }
                }
            }
        }
    }

    /**
     * Remove all dead animals from this field.
     */
    public void removeDeadAnimals()
    {
        // A dead animal has no location of its own, so its cell comes from the ledger.
        for(Animal animal : animals) {
            if(!animal.isAlive()) {
                Location location = locationsByAnimal.remove(animal);
                if(location != null) {
                    clearCell(location, animal);
                }
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

    /**
     * Verify the cells, list, alive state, and animal locations agree. The cells and
     * the list are each walked once; the list must name every occupied cell exactly once.
     */
    public boolean hasConsistentState()
    {
        int occupied = 0;
        for(int cell = 0; cell < cells.length; cell++) {
            Animal animal = cells[cell];
            if(animal == null) continue;
            Location location = animal.getLocation();
            if(!animal.isAlive() || location == null ||
               !isInside(location) || location.row() * width + location.col() != cell) {
                return false;
            }
            occupied++;
        }
        if(animals.size() != occupied || locationsByAnimal.size() != occupied) {
            return false;
        }
        BitSet listedCells = new BitSet(cells.length);
        for(Animal animal : animals) {
            if(animal == null || !animal.isAlive()) return false;
            Location location = animal.getLocation();
            if(location == null || !isInside(location)) return false;
            int cell = location.row() * width + location.col();
            if(cells[cell] != animal || listedCells.get(cell) ||
               !location.equals(locationsByAnimal.get(animal))) {
                return false;
            }
            listedCells.set(cell);
        }
        return true;
    }

    private boolean isInside(Location location)
    {
        return location.row() >= 0 && location.row() < depth &&
               location.col() >= 0 && location.col() < width;
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
