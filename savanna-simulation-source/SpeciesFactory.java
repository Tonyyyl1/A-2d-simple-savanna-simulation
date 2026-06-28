/**
 * Factory abstraction used when the simulator populates the field.
 */
public interface SpeciesFactory
{
    SpeciesProfile getProfile();

    SavannahAnimal create(boolean randomAge, Location location);
}
