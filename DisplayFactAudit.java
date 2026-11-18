import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable, read-only evidence for one display fact boundary. It is an
 * audit sidecar only: neither renderer reads it to decide what to draw.
 */
public final class DisplayFactAudit
{
    public enum Source
    {
        ORDINARY_MARKER,
        INSPECT_ACTOR
    }

    public enum DifferenceType
    {
        MISSING_ID,
        SPECIES_MISMATCH,
        DEAD_ACTOR,
        VIEWPORT_OUTSIDE,
        POSITION_MISMATCH
    }

    /** One immutable current-fact record; it never retains an Animal. */
    public static final class AnimalFact
    {
        public final long animalId;
        public final String species;
        public final int row;
        public final int col;
        public final boolean alive;

        public AnimalFact(long animalId, String species, int row, int col,
                          boolean alive)
        {
            this.animalId = animalId;
            this.species = species == null ? "" : species;
            this.row = row;
            this.col = col;
            this.alive = alive;
        }
    }

    public static final class Difference
    {
        public final Source source;
        public final DifferenceType type;
        public final long animalId;
        public final String detail;

        private Difference(Source source, DifferenceType type, long animalId,
                           String detail)
        {
            this.source = source;
            this.type = type;
            this.animalId = animalId;
            this.detail = detail;
        }
    }

    public static final class Comparison
    {
        private final Source source;
        private final List<Difference> differences;

        private Comparison(Source source, List<Difference> differences)
        {
            this.source = source;
            this.differences = Collections.unmodifiableList(
                new ArrayList<>(differences));
        }

        public Source getSource()
        {
            return source;
        }

        public List<Difference> getDifferences()
        {
            return differences;
        }

        public boolean isConsistent()
        {
            return differences.isEmpty();
        }
    }

    private final int step;
    private final long generation;
    private final Rectangle viewport;
    private final List<AnimalFact> fieldFacts;
    private final List<AnimalFact> ordinaryMarkerInputs;
    private final List<AnimalFact> inspectActorInputs;

    public DisplayFactAudit(int step, long generation, Rectangle viewport,
                            List<AnimalFact> fieldFacts,
                            List<AnimalFact> ordinaryMarkerInputs,
                            List<AnimalFact> inspectActorInputs)
    {
        if(viewport == null) {
            throw new IllegalArgumentException("A display audit needs a viewport.");
        }
        this.step = step;
        this.generation = generation;
        this.viewport = new Rectangle(viewport);
        this.fieldFacts = immutableCopy(fieldFacts);
        this.ordinaryMarkerInputs = immutableCopy(ordinaryMarkerInputs);
        this.inspectActorInputs = immutableCopy(inspectActorInputs);
    }

    public int getStep()
    {
        return step;
    }

    public long getGeneration()
    {
        return generation;
    }

    public Rectangle getViewport()
    {
        return new Rectangle(viewport);
    }

    public List<AnimalFact> getFieldFacts()
    {
        return fieldFacts;
    }

    public List<AnimalFact> getOrdinaryMarkerInputs()
    {
        return ordinaryMarkerInputs;
    }

    public List<AnimalFact> getInspectActorInputs()
    {
        return inspectActorInputs;
    }

    public Comparison compareOrdinaryMarkerInputs()
    {
        return compare(Source.ORDINARY_MARKER, ordinaryMarkerInputs);
    }

    public Comparison compareInspectActorInputs()
    {
        return compare(Source.INSPECT_ACTOR, inspectActorInputs);
    }

    private Comparison compare(Source source, List<AnimalFact> inputs)
    {
        Map<Long, AnimalFact> current = byId(fieldFacts);
        Map<Long, AnimalFact> candidates = byId(inputs);
        List<Difference> differences = new ArrayList<>();

        for(AnimalFact candidate : candidates.values()) {
            if(!candidate.alive) {
                differences.add(new Difference(source, DifferenceType.DEAD_ACTOR,
                    candidate.animalId, "input is not living"));
                continue;
            }
            if(!contains(viewport, candidate)) {
                differences.add(new Difference(source, DifferenceType.VIEWPORT_OUTSIDE,
                    candidate.animalId, "input position is outside the viewport"));
                continue;
            }
            AnimalFact expected = current.get(candidate.animalId);
            if(expected == null) {
                differences.add(new Difference(source, DifferenceType.MISSING_ID,
                    candidate.animalId, "input ID is absent from Field facts"));
            }
            else if(!expected.species.equals(candidate.species)) {
                differences.add(new Difference(source, DifferenceType.SPECIES_MISMATCH,
                    candidate.animalId, "input species differs from Field facts"));
            }
            else if(expected.row != candidate.row || expected.col != candidate.col) {
                differences.add(new Difference(source, DifferenceType.POSITION_MISMATCH,
                    candidate.animalId, "input location differs from Field facts"));
            }
        }

        for(AnimalFact expected : current.values()) {
            if(!candidates.containsKey(expected.animalId)) {
                differences.add(new Difference(source, DifferenceType.MISSING_ID,
                    expected.animalId, "current Field ID is absent from input"));
            }
        }
        return new Comparison(source, differences);
    }

    private static boolean contains(Rectangle viewport, AnimalFact fact)
    {
        return viewport.contains(fact.col, fact.row);
    }

    private static Map<Long, AnimalFact> byId(List<AnimalFact> facts)
    {
        Map<Long, AnimalFact> result = new LinkedHashMap<>();
        for(AnimalFact fact : facts) {
            if(fact != null) {
                result.put(fact.animalId, fact);
            }
        }
        return result;
    }

    private static List<AnimalFact> immutableCopy(List<AnimalFact> facts)
    {
        return Collections.unmodifiableList(new ArrayList<>(
            facts == null ? Collections.<AnimalFact>emptyList() : facts));
    }

    /** Captures only current, living Savannah facts in the requested viewport. */
    public static List<AnimalFact> captureFieldFacts(Field field,
                                                      Rectangle viewport)
    {
        List<AnimalFact> facts = new ArrayList<>();
        if(field == null || viewport == null) {
            return facts;
        }
        for(Animal animal : field.getAnimals()) {
            if(animal instanceof SavannahAnimal && animal.isAlive()) {
                SavannahAnimal savannah = (SavannahAnimal)animal;
                Location location = savannah.getLocation();
                if(location != null && viewport.contains(location.col(),
                                                          location.row())) {
                    facts.add(factFor(savannah));
                }
            }
        }
        return facts;
    }

    /**
     * Records the current fact represented by each inspect actor. Actor
     * keyframes may animate from a historic position, so their audit location
     * deliberately remains the matching living Field location.
     */
    public static List<AnimalFact> captureInspectActorInputs(
        Field field, List<SceneDirector.SceneActor> actors)
    {
        Map<Long, SavannahAnimal> living = livingById(field);
        List<AnimalFact> facts = new ArrayList<>();
        if(actors == null) {
            return facts;
        }
        for(SceneDirector.SceneActor actor : actors) {
            if(actor == null) {
                continue;
            }
            SavannahAnimal animal = living.get(actor.animalId);
            if(animal == null || animal.getLocation() == null) {
                facts.add(new AnimalFact(actor.animalId, actor.species,
                    -1, -1, false));
            }
            else {
                Location location = animal.getLocation();
                facts.add(new AnimalFact(actor.animalId, actor.species,
                    location.row(), location.col(), true));
            }
        }
        return facts;
    }

    public static AnimalFact factFor(SavannahAnimal animal)
    {
        Location location = animal == null ? null : animal.getLocation();
        return new AnimalFact(animal == null ? -1L : animal.getId(),
            animal == null ? "" : animal.getProfile().getName(),
            location == null ? -1 : location.row(),
            location == null ? -1 : location.col(),
            animal != null && animal.isAlive());
    }

    private static Map<Long, SavannahAnimal> livingById(Field field)
    {
        Map<Long, SavannahAnimal> living = new LinkedHashMap<>();
        if(field == null) {
            return living;
        }
        for(Animal animal : field.getAnimals()) {
            if(animal instanceof SavannahAnimal && animal.isAlive()) {
                living.put(animal.getId(), (SavannahAnimal)animal);
            }
        }
        return living;
    }
}
