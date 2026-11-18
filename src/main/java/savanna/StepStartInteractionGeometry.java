package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Immutable location mapping captured from one consistent step-start field. */
public final class StepStartInteractionGeometry implements InteractionGeometry
{
    private final int depth;
    private final int width;
    /** One slot per field cell; only get is ever used (never iterated). */
    private final Animal[] occupantByCell;
    /** Occupants located outside depth x width (never produced by a field); get only. */
    private final Map<Location, Animal> occupantsOutside;
    private final int livingPopulation;

    private StepStartInteractionGeometry(Occupants occupants)
    {
        depth = occupants.depth;
        width = occupants.width;
        occupantByCell = occupants.byCell;
        occupantsOutside = occupants.outside;
        // capture() admits only living animals, so this immutable count is the
        // same step-start fact that the former repeated scan returned.
        livingPopulation = occupants.count;
    }

    /** Collects the occupant of each distinct location while a geometry is built. */
    private static final class Occupants
    {
        final int depth;
        final int width;
        final Animal[] byCell;
        final Map<Location, Animal> outside = new HashMap<>();
        int count;

        Occupants(int depth, int width)
        {
            this.depth = depth;
            this.width = width;
            byCell = new Animal[depth * width];
        }

        /** Same contract as Map.put: returns the animal previously at the location. */
        Animal put(Location location, Animal animal)
        {
            Animal previous;
            if(location.row() >= 0 && location.row() < depth &&
               location.col() >= 0 && location.col() < width) {
                int cell = location.row() * width + location.col();
                previous = byCell[cell];
                byCell[cell] = animal;
            }
            else {
                previous = outside.put(location, animal);
            }
            if(previous == null) count++;
            return previous;
        }
    }

    public static StepStartInteractionGeometry capture(Field field)
    {
        if(field == null || (RuntimeAudit.enabled() && !field.hasConsistentState())) {
            throw new IllegalArgumentException(
                "interaction geometry requires a consistent field");
        }
        Occupants occupants = new Occupants(field.getDepth(), field.getWidth());
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
        return new StepStartInteractionGeometry(occupants);
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
        Occupants occupants = new Occupants(view.getDepth(), view.getWidth());
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
        if(occupants.count != liveAnimalsById.size()) {
            throw new IllegalStateException(
                "spatial view omits a live interaction identity");
        }
        return new StepStartInteractionGeometry(occupants);
    }

    @Override
    public Animal getAnimalAt(Location location)
    {
        if(location == null) {
            return null;
        }
        if(location.row() >= 0 && location.row() < depth &&
           location.col() >= 0 && location.col() < width) {
            return occupantByCell[location.row() * width + location.col()];
        }
        return occupantsOutside.isEmpty() ? null : occupantsOutside.get(location);
    }

    @Override
    public List<Location> getLocationsWithin(Location origin, int range,
                                             Random random)
    {
        // Room for every cell of the clamped (2 * range + 1)-square except the origin.
        long rowSpan = Math.min(depth, 2L * range + 1);
        long colSpan = Math.min(width, 2L * range + 1);
        List<Location> locations = new ArrayList<>(
            (int)Math.max(0L, Math.min(rowSpan * colSpan - 1, 1 << 16)));
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

    /**
     * Number of locations {@link #getLocationsWithin} returns for these arguments: the cells of
     * the (2 * range + 1)-square around {@code origin} that lie inside the field, without the
     * origin cell itself; 0 for a null origin or a negative range.
     */
    int countLocationsWithin(Location origin, int range)
    {
        if(origin == null || range < 0) {
            return 0;
        }
        long rows = Math.min(depth - 1L, (long)origin.row() + range) -
            Math.max(0L, (long)origin.row() - range) + 1;
        long cols = Math.min(width - 1L, (long)origin.col() + range) -
            Math.max(0L, (long)origin.col() - range) + 1;
        if(rows <= 0 || cols <= 0) {
            return 0;
        }
        long cells = rows * cols;
        if(origin.row() >= 0 && origin.row() < depth && origin.col() >= 0 &&
           origin.col() < width) {
            cells--;
        }
        return (int)cells;
    }

    /**
     * Draws from {@code random} exactly as {@code Collections.shuffle} does on a random-access
     * list of {@code size} elements (one {@code nextInt(i)} for i = size down to 2) and
     * discards the draws. It only keeps the random stream identical to the former shuffled
     * location list in the mate search; it can be removed together with the next
     * EcologyRules.VERSION increment.
     */
    static void drawShuffleStream(int size, Random random)
    {
        for(int i = size; i > 1; i--) {
            random.nextInt(i);
        }
    }

    /** One occupied cell of a window scan: the animal and the cell it stands on. */
    interface OccupantTest
    {
        boolean test(Animal animal, int row, int col);
    }

    /**
     * Whether any animal standing in the clipped (2 * range + 1)-square around {@code origin},
     * origin cell excluded, satisfies {@code test}. The answer does not depend on the scan
     * order; the scan is row-major and stops at the first match.
     */
    boolean anyOccupantWithin(Location origin, int range, OccupantTest test)
    {
        if(origin == null || range < 0) {
            return false;
        }
        long firstRow = Math.max(0L, (long)origin.row() - range);
        long lastRow = Math.min(depth - 1L, (long)origin.row() + range);
        long firstCol = Math.max(0L, (long)origin.col() - range);
        long lastCol = Math.min(width - 1L, (long)origin.col() + range);
        for(long row = firstRow; row <= lastRow; row++) {
            for(long col = firstCol; col <= lastCol; col++) {
                if(row == origin.row() && col == origin.col()) {
                    continue;
                }
                Animal animal = occupantByCell[(int)row * width + (int)col];
                if(animal != null && test.test(animal, (int)row, (int)col)) {
                    return true;
                }
            }
        }
        return false;
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
        return java.util.Objects.requireNonNull(location);
    }
}
