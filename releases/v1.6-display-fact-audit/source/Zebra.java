import java.util.Random;

/**
 * Zebra herbivore.
 */
public class Zebra extends SavannahAnimal
{
    public Zebra(boolean randomAge, Location location)
    {
        this(randomAge, location, Randomizer.getRandom());
    }

    public Zebra(boolean randomAge, Location location, Random rand)
    {
        super(SpeciesRegistry.getProfile(SpeciesRegistry.ZEBRA), randomAge,
              location, rand);
    }

    public SavannahAnimal createChild(Location location)
    {
        return new Zebra(false, location, getRandom());
    }
}
