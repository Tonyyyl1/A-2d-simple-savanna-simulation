/**
 * Lion predator.
 */
public class Lion extends SavannahAnimal
{
    public Lion(boolean randomAge, Location location)
    {
        super(SpeciesRegistry.getProfile(SpeciesRegistry.LION), randomAge, location);
    }

    public SavannahAnimal createChild(Location location)
    {
        return new Lion(false, location);
    }
}
