package savanna;

/** One exact legacy breeding result before R2 spawn resolution. */
public final class LegacyBreedingObservation
{
    private final int step;
    private final long parentId;
    private final String species;
    private final Location location;
    private final DecisionBirthCandidateFact candidates;
    private final LegacyBreedingAssessment assessment;

    LegacyBreedingObservation(int step, SavannahAnimal parent,
                              DecisionBirthCandidateFact candidates,
                              LegacyBreedingAssessment assessment)
    {
        if(step < 0 || parent == null || parent.getLocation() == null ||
           candidates == null || assessment == null ||
           candidates.getStep() != step ||
           candidates.getActorId() != parent.getId() ||
           assessment.getActualDecisionCandidateCount() !=
               candidates.getActualDecisionCandidateCount()) {
            throw new IllegalArgumentException(
                "coherent legacy breeding facts are required");
        }
        this.step = step;
        parentId = parent.getId();
        species = parent.getProfile().getName();
        location = copy(parent.getLocation());
        this.candidates = candidates;
        this.assessment = assessment;
    }

    public int getStep() { return step; }

    public long getParentId() { return parentId; }

    public String getSpecies() { return species; }

    public Location getLocation() { return copy(location); }

    public DecisionBirthCandidateFact getCandidates() { return candidates; }

    public LegacyBreedingAssessment getAssessment() { return assessment; }

    private static Location copy(Location value)
    {
        return new Location(value.row(), value.col());
    }
}
