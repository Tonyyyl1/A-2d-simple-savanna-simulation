/**
 * African buffalo herbivore.
 */
public class Buffalo extends SavannahAnimal
{
    public Buffalo(boolean randomAge, Location location)
    {
        super(SpeciesRegistry.getProfile(SpeciesRegistry.BUFFALO), randomAge, location);
    }

    public SavannahAnimal createChild(Location location)
    {
        return new Buffalo(false, location);
    }
}
