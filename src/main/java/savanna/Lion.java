package savanna;

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
        this(randomAge, location, rand, new AnimalIdentityAllocator());
    }

    public Lion(boolean randomAge, Location location, Random rand,
                AnimalIdentityAllocator identityAllocator)
    {
        super(SpeciesRegistry.getProfile(SpeciesRegistry.LION), randomAge,
              location, rand, identityAllocator);
    }

    public SavannahAnimal createChild(Location location)
    {
        return new Lion(false, location, getRandom(), getIdentityAllocator());
    }
}
