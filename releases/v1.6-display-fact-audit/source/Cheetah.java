import java.util.Random;

/**
 * Cheetah predator.
 */
public class Cheetah extends SavannahAnimal
{
    public Cheetah(boolean randomAge, Location location)
    {
        this(randomAge, location, Randomizer.getRandom());
    }

    public Cheetah(boolean randomAge, Location location, Random rand)
    {
        super(SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH), randomAge,
              location, rand);
    }

    public SavannahAnimal createChild(Location location)
    {
        return new Cheetah(false, location, getRandom());
    }
}
