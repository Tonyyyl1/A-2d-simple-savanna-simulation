package savanna;

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
        this(randomAge, location, rand, new AnimalIdentityAllocator());
    }

    public Gazelle(boolean randomAge, Location location, Random rand,
                   AnimalIdentityAllocator identityAllocator)
    {
        super(SpeciesRegistry.getProfile(SpeciesRegistry.GAZELLE), randomAge,
              location, rand, identityAllocator);
    }

    public SavannahAnimal createChild(Location location)
    {
        return new Gazelle(false, location, getRandom(),
            getIdentityAllocator());
    }
}
