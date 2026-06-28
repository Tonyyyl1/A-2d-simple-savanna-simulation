/**
 * Cheetah predator.
 */
public class Cheetah extends SavannahAnimal
{
    public Cheetah(boolean randomAge, Location location)
    {
        super(SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH), randomAge, location);
    }

    public SavannahAnimal createChild(Location location)
    {
        return new Cheetah(false, location);
    }
}
