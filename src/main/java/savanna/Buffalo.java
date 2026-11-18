package savanna;

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
        this(randomAge, location, rand, new AnimalIdentityAllocator());
    }

    public Buffalo(boolean randomAge, Location location, Random rand,
                   AnimalIdentityAllocator identityAllocator)
    {
        super(SpeciesRegistry.getProfile(SpeciesRegistry.BUFFALO), randomAge,
              location, rand, identityAllocator);
    }

    public SavannahAnimal createChild(Location location)
    {
        return new Buffalo(false, location, getRandom(),
            getIdentityAllocator());
    }
}
