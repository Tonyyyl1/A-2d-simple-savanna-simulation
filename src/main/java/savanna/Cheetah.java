package savanna;

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
        this(randomAge, location, rand, new AnimalIdentityAllocator());
    }

    public Cheetah(boolean randomAge, Location location, Random rand,
                   AnimalIdentityAllocator identityAllocator)
    {
        super(SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH), randomAge,
              location, rand, identityAllocator);
    }

    public SavannahAnimal createChild(Location location)
    {
        return new Cheetah(false, location, getRandom(),
            getIdentityAllocator());
    }
}
