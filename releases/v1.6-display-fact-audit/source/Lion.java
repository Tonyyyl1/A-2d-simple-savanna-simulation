import java.util.Random;

/**
 * Lion predator.
 */
public class Lion extends SavannahAnimal
{
    public Lion(boolean randomAge, Location location)
    {
        this(randomAge, location, Randomizer.getRandom());
    }

    public Lion(boolean randomAge, Location location, Random rand)
    {
        super(SpeciesRegistry.getProfile(SpeciesRegistry.LION), randomAge,
              location, rand);
    }

    public SavannahAnimal createChild(Location location)
    {
        return new Lion(false, location, getRandom());
    }
}
