package savanna;

/** Exact initial reserve values for one committed legacy child. */
public final class NewbornReserveFact
{
    private final long childId;
    private final int litterOrdinal;
    private final String species;
    private final Location location;
    private final AnimalPhysiologyFact reserve;

    NewbornReserveFact(SavannahAnimal child, int litterOrdinal,
                       Location location)
    {
        if(child == null || litterOrdinal < 0 || location == null) {
            throw new IllegalArgumentException(
                "child, ordinal, and committed location are required");
        }
        childId = child.getId();
        this.litterOrdinal = litterOrdinal;
        species = child.getProfile().getName();
        this.location = copy(location);
        reserve = AnimalPhysiologyFact.capture(child);
    }

    public long getChildId() { return childId; }

    public int getLitterOrdinal() { return litterOrdinal; }

    public String getSpecies() { return species; }

    public Location getLocation() { return copy(location); }

    public AnimalPhysiologyFact getReserve() { return reserve; }

    private static Location copy(Location value)
    {
        return new Location(value.row(), value.col());
    }
}
