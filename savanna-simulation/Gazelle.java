import java.util.Random;

/**
 * Gazelle herbivore.
 */
public class Gazelle extends SavannahAnimal
{
    public Gazelle(boolean randomAge, Location location)
    {
        this(randomAge, location, Randomizer.getRandom());
    }

    public Gazelle(boolean randomAge, Location location, Random rand)
    {
        super(SpeciesRegistry.getProfile(SpeciesRegistry.GAZELLE), randomAge,
              location, rand);
    }

    public SavannahAnimal createChild(Location location)
    {
        return new Gazelle(false, location, getRandom());
    }
}
