import java.util.Random;

/**
 * African buffalo herbivore.
 */
public class Buffalo extends SavannahAnimal
{
    public Buffalo(boolean randomAge, Location location)
    {
        this(randomAge, location, Randomizer.getRandom());
    }

    public Buffalo(boolean randomAge, Location location, Random rand)
    {
        super(SpeciesRegistry.getProfile(SpeciesRegistry.BUFFALO), randomAge,
              location, rand);
    }

    public SavannahAnimal createChild(Location location)
    {
        return new Buffalo(false, location, getRandom());
    }
}
