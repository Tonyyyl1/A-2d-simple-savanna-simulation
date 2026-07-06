/**
 * Zebra herbivore.
 */
public class Zebra extends SavannahAnimal
{
    public Zebra(boolean randomAge, Location location)
    {
        super(SpeciesRegistry.getProfile(SpeciesRegistry.ZEBRA), randomAge, location);
    }

    public SavannahAnimal createChild(Location location)
    {
        return new Zebra(false, location);
    }
}
