package savanna;

import java.util.Random;

/**
 * Factory abstraction used when the simulator populates the field.
 */
public interface SpeciesFactory
{
    SpeciesProfile getProfile();

    SavannahAnimal create(boolean randomAge, Location location, Random rand,
                          AnimalIdentityAllocator identityAllocator);

    default SavannahAnimal create(boolean randomAge, Location location,
                                  Random rand)
    {
        return create(randomAge, location, rand,
            new AnimalIdentityAllocator());
    }
}
