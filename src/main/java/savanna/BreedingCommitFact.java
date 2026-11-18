package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Exact join from one legacy assessment to committed R2 children and debit. */
public final class BreedingCommitFact
{
    private final int step;
    private final long parentId;
    private final String species;
    private final int committedBirthCount;
    private final AnimalPhysiologyFact parentPreBirth;
    private final AnimalPhysiologyFact parentPostBirth;
    private final List<NewbornReserveFact> newborns;

    BreedingCommitFact(int step, long parentId, String species,
                       int committedBirthCount,
                       AnimalPhysiologyFact parentPreBirth,
                       AnimalPhysiologyFact parentPostBirth,
                       List<NewbornReserveFact> newborns)
    {
        if(step < 0 || parentId <= 0L || species == null ||
           species.isBlank() || committedBirthCount < 0 ||
           parentPreBirth == null || parentPostBirth == null ||
           newborns == null || committedBirthCount != newborns.size()) {
            throw new IllegalArgumentException(
                "coherent breeding commit facts are required");
        }
        this.step = step;
        this.parentId = parentId;
        this.species = species;
        this.committedBirthCount = committedBirthCount;
        this.parentPreBirth = parentPreBirth;
        this.parentPostBirth = parentPostBirth;
        List<NewbornReserveFact> copied = new ArrayList<>(newborns);
        copied.sort(Comparator.comparingInt(
            NewbornReserveFact::getLitterOrdinal));
        this.newborns = Collections.unmodifiableList(copied);
    }

    public int getStep() { return step; }

    public long getParentId() { return parentId; }

    public String getSpecies() { return species; }

    public int getCommittedBirthCount() { return committedBirthCount; }

    public AnimalPhysiologyFact getParentPreBirth()
    {
        return parentPreBirth;
    }

    public AnimalPhysiologyFact getParentPostBirth()
    {
        return parentPostBirth;
    }

    public List<NewbornReserveFact> getNewborns() { return newborns; }
}
