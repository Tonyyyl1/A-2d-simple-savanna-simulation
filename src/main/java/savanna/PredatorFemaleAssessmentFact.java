package savanna;

/** Full immutable PF1-aF row for one predator female breeding assessment. */
public final class PredatorFemaleAssessmentFact
{
    private final LegacyBreedingObservation breeding;
    private final AnimalPhysiologyFact decisionPhysiology;
    private final int effectiveHuntingRange;
    private final ReachableResourceFact localResource;
    private final StepStartLocalCapacityFact localCapacity;
    private final RealizedKillWindowFact kill30;
    private final RealizedKillWindowFact kill60;
    private final BreedingCommitFact commit;

    PredatorFemaleAssessmentFact(LegacyBreedingObservation breeding,
                                 AnimalPhysiologyFact decisionPhysiology,
                                 int effectiveHuntingRange,
                                 ReachableResourceFact localResource,
                                 StepStartLocalCapacityFact localCapacity,
                                 RealizedKillWindowFact kill30,
                                 RealizedKillWindowFact kill60,
                                 BreedingCommitFact commit)
    {
        if(breeding == null || decisionPhysiology == null ||
           effectiveHuntingRange < 0 || localResource == null ||
           localCapacity == null || kill30 == null || kill60 == null ||
           commit == null ||
           breeding.getParentId() != commit.getParentId() ||
           breeding.getStep() != commit.getStep() ||
           localCapacity.getActorId() != breeding.getParentId() ||
           kill30.getPredatorId() != breeding.getParentId() ||
           kill60.getPredatorId() != breeding.getParentId()) {
            throw new IllegalArgumentException(
                "coherent predator-female assessment facts are required");
        }
        this.breeding = breeding;
        this.decisionPhysiology = decisionPhysiology;
        this.effectiveHuntingRange = effectiveHuntingRange;
        this.localResource = localResource;
        this.localCapacity = localCapacity;
        this.kill30 = kill30;
        this.kill60 = kill60;
        this.commit = commit;
    }

    public int getStep() { return breeding.getStep(); }

    public long getParentId() { return breeding.getParentId(); }

    public String getSpecies() { return breeding.getSpecies(); }

    public Location getLocation() { return breeding.getLocation(); }

    public LegacyBreedingObservation getBreeding() { return breeding; }

    public AnimalPhysiologyFact getDecisionPhysiology()
    {
        return decisionPhysiology;
    }

    public int getEffectiveHuntingRange()
    {
        return effectiveHuntingRange;
    }

    public ReachableResourceFact getLocalResource() { return localResource; }

    public StepStartLocalCapacityFact getLocalCapacity()
    {
        return localCapacity;
    }

    public RealizedKillWindowFact getKill30() { return kill30; }

    public RealizedKillWindowFact getKill60() { return kill60; }

    public BreedingCommitFact getCommit() { return commit; }
}
