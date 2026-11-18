package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Immutable location mapping captured from one consistent step-start field. */
public final class StepStartInteractionGeometry implements InteractionGeometry
{
    private final int depth;
    private final int width;
    private final Map<Location, Animal> occupants;
    private final int livingPopulation;

    private StepStartInteractionGeometry(int depth, int width,
                                         Map<Location, Animal> occupants)
    {
        this.depth = depth;
        this.width = width;
        this.occupants = Collections.unmodifiableMap(
            new LinkedHashMap<>(occupants));
        // capture() admits only living animals, so this immutable count is the
        // same step-start fact that the former repeated scan returned.
        livingPopulation = this.occupants.size();
    }

    public static StepStartInteractionGeometry capture(Field field)
    {
        if(field == null || !field.hasConsistentState()) {
            throw new IllegalArgumentException(
                "interaction geometry requires a consistent field");
        }
        Map<Location, Animal> occupants = new LinkedHashMap<>();
        for(Animal animal : field.getAnimals()) {
            if(!animal.isAlive()) {
                continue;
            }
            Location location = animal.getLocation();
            if(location == null || field.getAnimalAt(location) != animal) {
                throw new IllegalStateException(
                    "field location disagrees while capturing interaction geometry");
            }
            Animal previous = occupants.put(copy(location), animal);
            if(previous != null && previous != animal) {
                throw new IllegalStateException(
                    "duplicate interaction location: " + location);
            }
        }
        return new StepStartInteractionGeometry(field.getDepth(), field.getWidth(),
            occupants);
    }

    /**
     * Builds the legacy behavior adapter from the authoritative immutable
     * spatial view without scanning or recapturing the Field.
     */
    public static StepStartInteractionGeometry fromSpatialView(
        SpatialEcologyView view, Map<Long, ? extends Animal> liveAnimalsById)
    {
        if(view == null || liveAnimalsById == null) {
            throw new IllegalArgumentException(
                "spatial view and live identity resolver are required");
        }
        Map<Location, Animal> occupants = new LinkedHashMap<>();
        for(SpatialAnimalFact fact : view.getAnimalFacts()) {
            Animal animal = liveAnimalsById.get(fact.getId());
            if(animal == null || !animal.isAlive() ||
               animal.getId() != fact.getId() ||
               !fact.getLocation().equals(animal.getLocation())) {
                throw new IllegalStateException(
                    "spatial view and live identity resolver disagree for " +
                    fact.getId());
            }
            Animal previous = occupants.put(fact.getLocation(), animal);
            if(previous != null) {
                throw new IllegalStateException(
                    "duplicate interaction location: " + fact.getLocation());
            }
        }
        if(occupants.size() != liveAnimalsById.size()) {
            throw new IllegalStateException(
                "spatial view omits a live interaction identity");
        }
        return new StepStartInteractionGeometry(view.getDepth(), view.getWidth(),
            occupants);
    }

    @Override
    public Animal getAnimalAt(Location location)
    {
        return occupants.get(location);
    }

    @Override
    public List<Location> getLocationsWithin(Location origin, int range,
                                             Random random)
    {
        List<Location> locations = new ArrayList<>();
        if(origin != null) {
            int row = origin.row();
            int col = origin.col();
            for(int rowOffset = -range; rowOffset <= range; rowOffset++) {
                int nextRow = row + rowOffset;
                if(nextRow < 0 || nextRow >= depth) {
                    continue;
                }
                for(int colOffset = -range; colOffset <= range; colOffset++) {
                    int nextCol = col + colOffset;
                    if(nextCol >= 0 && nextCol < width &&
                       (rowOffset != 0 || colOffset != 0)) {
                        locations.add(new Location(nextRow, nextCol));
                    }
                }
            }
            Collections.shuffle(locations, random);
        }
        return locations;
    }

    @Override
    public int getLivingPopulation()
    {
        return livingPopulation;
    }

    @Override
    public int getDepth() { return depth; }

    @Override
    public int getWidth() { return width; }

    private static Location copy(Location location)
    {
        return new Location(location.row(), location.col());
    }
}
