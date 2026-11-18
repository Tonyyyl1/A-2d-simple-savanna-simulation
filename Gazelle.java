/**
 * Gazelle herbivore.
 */
public class Gazelle extends SavannahAnimal
{
    public Gazelle(boolean randomAge, Location location)
    {
        super(SpeciesRegistry.getProfile(SpeciesRegistry.GAZELLE), randomAge, location);
    }

    public SavannahAnimal createChild(Location location)
    {
        return new Gazelle(false, location);
    }
}
