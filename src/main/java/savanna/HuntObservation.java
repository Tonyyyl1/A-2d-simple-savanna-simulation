package savanna;

/** Immutable result facts for one HUNT action already executed by R2. */
public final class HuntObservation
{
    private final int step;
    private final long actorId;
    private final String actorSpecies;
    private final boolean activePhase;
    private final BehaviorReason foodReason;
    private final HuntResult result;
    private final boolean huntEventRecorded;
    private final boolean predationDeathRecorded;

    public HuntObservation(int step, SavannahAnimal actor, boolean activePhase,
                           BehaviorReason foodReason, HuntResult result,
                           boolean huntEventRecorded,
                           boolean predationDeathRecorded)
    {
        this.step = step;
        actorId = actor == null ? -1L : actor.getId();
        actorSpecies = actor == null ? "" : actor.getProfile().getName();
        this.activePhase = activePhase;
        this.foodReason = foodReason;
        this.result = result == null ? new HuntResult(HuntOutcome.INELIGIBLE,
            null, "", 0, 0, 0.0, HuntRandomLedger.none()) : result;
        this.huntEventRecorded = huntEventRecorded;
        this.predationDeathRecorded = predationDeathRecorded;
    }

    public int getStep() { return step; }
    public long getActorId() { return actorId; }
    public String getActorSpecies() { return actorSpecies; }
    public boolean isActivePhase() { return activePhase; }
    public BehaviorReason getFoodReason() { return foodReason; }
    public HuntResult getResult() { return result; }
    public HuntOpportunityFact getOpportunityFact()
    {
        return result.getOpportunityFact();
    }
    public boolean isHuntEventRecorded() { return huntEventRecorded; }
    public boolean isPredationDeathRecorded() { return predationDeathRecorded; }
}
