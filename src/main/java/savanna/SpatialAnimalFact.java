package savanna;

import java.util.Objects;

/** Immutable identity and location facts captured at one simulation step start. */
public final class SpatialAnimalFact
{
    private final long id;
    private final String species;
    private final Location location;
    private final Sex sex;
    private final LifeStage lifeStage;
    private final boolean predator;
    private final boolean aliveAtCapture;

    public SpatialAnimalFact(long id, String species, Location location, Sex sex,
                             LifeStage lifeStage, boolean predator,
                             boolean aliveAtCapture)
    {
        if(id <= 0L || species == null || species.isBlank() ||
           location == null || sex == null || lifeStage == null) {
            throw new IllegalArgumentException(
                "complete spatial animal facts are required");
        }
        this.id = id;
        this.species = species;
        this.location = copy(location);
        this.sex = sex;
        this.lifeStage = lifeStage;
        this.predator = predator;
        this.aliveAtCapture = aliveAtCapture;
    }

    public long getId() { return id; }

    public String getSpecies() { return species; }

    public Location getLocation() { return copy(location); }

    public Sex getSex() { return sex; }

    public LifeStage getLifeStage() { return lifeStage; }

    public boolean isPredator() { return predator; }

    public boolean wasAliveAtCapture() { return aliveAtCapture; }

    @Override
    public boolean equals(Object other)
    {
        if(this == other) return true;
        if(!(other instanceof SpatialAnimalFact)) return false;
        SpatialAnimalFact fact = (SpatialAnimalFact)other;
        return id == fact.id && predator == fact.predator &&
               aliveAtCapture == fact.aliveAtCapture &&
               species.equals(fact.species) && location.equals(fact.location) &&
               sex == fact.sex && lifeStage == fact.lifeStage;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, species, location, sex, lifeStage, predator,
            aliveAtCapture);
    }

    @Override
    public String toString()
    {
        return id + ":" + species + "@" + location + ":" + sex + ":" +
            lifeStage + ":predator=" + predator + ":alive=" + aliveAtCapture;
    }

    private static Location copy(Location value)
    {
        return new Location(value.row(), value.col());
    }
}
