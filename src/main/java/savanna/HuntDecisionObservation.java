package savanna;

/** Read-only pre-action facts used by the D2 would-seek diagnostic. */
public final class HuntDecisionObservation
{
    private final int step;
    private final long actorId;
    private final String actorSpecies;
    private final int age;
    private final LifeStage lifeStage;
    private final boolean activePhase;
    private final BehaviorReason foodReason;
    private final ActionType primaryAction;
    private final BehaviorReason primaryReason;
    private final boolean waterPriority;
    private final String opportunityId;
    private final int huntingRange;
    private final String candidateDigest;
    private final int localReachableEdibleCount;
    private final boolean wouldSeekOnLocalMiss;

    public HuntDecisionObservation(int step, SavannahAnimal actor,
                                   boolean activePhase,
                                   BehaviorReason foodReason,
                                   BehaviorIntent primaryIntent,
                                   boolean waterPriority,
                                   HuntLocalOpportunity opportunity,
                                   boolean wouldSeekOnLocalMiss)
    {
        this(step, actor, activePhase, foodReason, primaryIntent,
            waterPriority, opportunity == null ? "LEGACY_UNVERSIONED" :
                "LEGACY_UNVERSIONED", opportunity == null ? 0 :
                opportunity.getEffectiveRange(), "LEGACY_UNVERSIONED",
            opportunity == null ? 0 :
                opportunity.getLocalReachableEdibleCount(),
            wouldSeekOnLocalMiss);
    }

    /** FRAME-1 constructor bound to the exact immutable S0 opportunity. */
    public HuntDecisionObservation(int step, SavannahAnimal actor,
                                   boolean activePhase,
                                   BehaviorReason foodReason,
                                   BehaviorIntent primaryIntent,
                                   boolean waterPriority,
                                   HuntOpportunityFact opportunity,
                                   boolean wouldSeekOnLocalMiss)
    {
        this(step, actor, activePhase, foodReason, primaryIntent,
            waterPriority, opportunity == null ? "MISSING" :
                opportunity.getOpportunityId(), opportunity == null ? 0 :
                opportunity.getEffectiveRange(), opportunity == null ?
                "MISSING" : opportunity.getCandidateDigest(),
            opportunity == null ? 0 : opportunity.getCandidateCount(),
            wouldSeekOnLocalMiss);
    }

    private HuntDecisionObservation(int step, SavannahAnimal actor,
                                    boolean activePhase,
                                    BehaviorReason foodReason,
                                    BehaviorIntent primaryIntent,
                                    boolean waterPriority,
                                    String opportunityId, int huntingRange,
                                    String candidateDigest,
                                    int localReachableEdibleCount,
                                    boolean wouldSeekOnLocalMiss)
    {
        this.step = step;
        actorId = actor == null ? -1L : actor.getId();
        actorSpecies = actor == null ? "" : actor.getProfile().getName();
        age = actor == null ? -1 : actor.getAge();
        lifeStage = actor == null ? null : actor.getLifeStage();
        this.activePhase = activePhase;
        this.foodReason = foodReason;
        primaryAction = primaryIntent == null ? null :
            primaryIntent.getActionType();
        primaryReason = primaryIntent == null ? null :
            primaryIntent.getReason();
        this.waterPriority = waterPriority;
        this.opportunityId = opportunityId;
        this.huntingRange = huntingRange;
        this.candidateDigest = candidateDigest;
        this.localReachableEdibleCount = localReachableEdibleCount;
        this.wouldSeekOnLocalMiss = wouldSeekOnLocalMiss;
    }

    public int getStep() { return step; }
    public long getActorId() { return actorId; }
    public String getActorSpecies() { return actorSpecies; }
    public int getAge() { return age; }
    public LifeStage getLifeStage() { return lifeStage; }
    public boolean isActivePhase() { return activePhase; }
    public BehaviorReason getFoodReason() { return foodReason; }
    public ActionType getPrimaryAction() { return primaryAction; }
    public BehaviorReason getPrimaryReason() { return primaryReason; }
    public boolean isWaterPriority() { return waterPriority; }
    public String getOpportunityId() { return opportunityId; }
    public int getHuntingRange() { return huntingRange; }
    public String getCandidateDigest() { return candidateDigest; }
    public int getLocalReachableEdibleCount()
    {
        return localReachableEdibleCount;
    }
    public boolean hasLocalReachableEdible()
    {
        return localReachableEdibleCount > 0;
    }
    public boolean wouldSeekOnLocalMiss() { return wouldSeekOnLocalMiss; }
}
