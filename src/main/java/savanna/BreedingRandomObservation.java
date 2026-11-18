package savanna;

/**
 * Read-only identity of the named RNG operations already performed by one
 * legacy breeding assessment.
 */
public final class BreedingRandomObservation
{
    private final int step;
    private final long parentId;
    private final String species;
    private final BreedingRandomLedger randomLedger;

    BreedingRandomObservation(int step, SavannahAnimal parent,
                              BreedingRandomLedger randomLedger)
    {
        if(step < 0 || parent == null || parent.getId() <= 0L ||
           parent.getProfile() == null || randomLedger == null) {
            throw new IllegalArgumentException(
                "complete breeding RNG observation facts are required");
        }
        this.step = step;
        parentId = parent.getId();
        species = parent.getProfile().getName();
        this.randomLedger = randomLedger;
    }

    public int getStep() { return step; }

    public long getParentId() { return parentId; }

    public String getSpecies() { return species; }

    public BreedingRandomLedger getRandomLedger() { return randomLedger; }
}
