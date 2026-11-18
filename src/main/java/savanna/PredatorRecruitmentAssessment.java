package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable V24-OBS-1A row joined from an S0 predator snapshot and its one
 * S4 frame ledger. It describes existing legacy semantics only; it does not
 * create a pregnancy, credit, capacity gate, or mate query.
 */
public final class PredatorRecruitmentAssessment
{
    public enum EligibilityState
    {
        PASS,
        FAIL,
        NOT_GATED_BY_LEGACY_SEMANTICS
    }

    private final S0Fact s0;
    private final StepFrameLedger ledger;
    private final StepFrameLedger.ProposalReason proposalReason;
    private final boolean mateQueryRequested;
    private final boolean mateQueryConsumed;
    private final boolean conceptionRollRequested;
    private final boolean conceptionRollAccepted;
    private final boolean litterDrawRequested;
    private final boolean legacyMateEligible;
    private final int mateQueryRandomDrawCount;
    private final int conceptionRollRandomDrawCount;
    private final int litterDrawRandomDrawCount;
    private final int acceptedConceptionCount;
    private final int pregnancyOpenedCount;
    private final int dueBirthCount;
    private final int pendingBirthCount;
    private final int submittedOrdinalCount;
    private final int committedBirthCount;
    private final int lostPositionCount;
    private final int cancelledParentCount;
    private final int explicitPregnancyLossCount;

    private PredatorRecruitmentAssessment(S0Fact s0, StepFrameLedger ledger)
    {
        if(s0 == null || ledger == null ||
           s0.actorId != ledger.getActorId() || s0.step != ledger.getStep() ||
           !s0.frameId.equals(ledger.getFrameId())) {
            throw new IllegalArgumentException(
                "S0/S4 predator recruitment facts must join exactly");
        }
        this.s0 = s0;
        this.ledger = ledger;
        proposalReason = ledger.getReproductionProposal().getReason();
        LegacyBreedingAssessment legacy =
            ledger.getReproductionProposal().getLegacyAssessment();
        BreedingRandomLedger random = legacy == null ? null
            : legacy.getRandomLedger();
        mateQueryRequested = random != null &&
            random.isMateQueryShuffleRequested();
        conceptionRollRequested = random != null &&
            random.hasLegacyBreedingRoll();
        conceptionRollAccepted = legacy != null &&
            legacy.isProbabilityRollAccepted();
        litterDrawRequested = random != null && random.hasLitterDraw();
        legacyMateEligible = legacy != null && legacy.isMateEligible();
        mateQueryRandomDrawCount = random == null ? 0
            : Math.max(0, random.getMateQueryShuffleInputSize() - 1);
        mateQueryConsumed = mateQueryRandomDrawCount > 0;
        conceptionRollRandomDrawCount = conceptionRollRequested ? 1 : 0;
        litterDrawRandomDrawCount = litterDrawRequested ? 1 : 0;
        submittedOrdinalCount = ledger.getReproductionProposal().getClaimCount();
        /*
         * FRAME-1 preserves the legacy immediate-birth lifecycle: accepted
         * litters become S1 ordinal obligations and are resolved in this same
         * frame.  It has no persistent pregnancy object or deferred due queue.
         * These names make that fact explicit without inventing either state.
         */
        acceptedConceptionCount = submittedOrdinalCount;
        pregnancyOpenedCount = submittedOrdinalCount;
        dueBirthCount = submittedOrdinalCount;
        pendingBirthCount = 0;
        int committed = 0;
        int lost = 0;
        int cancelled = 0;
        int pregnancyLoss = 0;
        for(StepFrameLedger.OrdinalOutcome outcome :
            ledger.getOrdinalOutcomes()) {
            switch(outcome.getDisposition()) {
                case COMMITTED_BIRTH:
                    committed++;
                    break;
                case LOST_POSITION_RESOLUTION:
                    lost++;
                    break;
                case CANCELLED_PARENT_TERMINAL:
                    cancelled++;
                    break;
                case PREGNANCY_LOSS:
                    pregnancyLoss++;
                    break;
                default:
                    throw new IllegalStateException(
                        "unknown ordinal disposition");
            }
        }
        if(acceptedConceptionCount != pregnancyOpenedCount ||
           pregnancyOpenedCount != committed + pendingBirthCount + lost +
           cancelled + pregnancyLoss || dueBirthCount !=
           pregnancyOpenedCount) {
            throw new IllegalStateException(
                "legacy reproduction lifecycle does not close");
        }
        committedBirthCount = committed;
        lostPositionCount = lost;
        cancelledParentCount = cancelled;
        explicitPregnancyLossCount = pregnancyLoss;
    }

    static S0Fact captureS0(SavannahAnimal actor,
                            StepFrameLedger.ResourceClock resourceClock,
                            List<Location> decisionCandidates,
                            SimulationContext context)
    {
        if(actor == null || resourceClock == null || decisionCandidates == null ||
           context == null || resourceClock.getActorId() != actor.getId() ||
           resourceClock.getStep() != context.getStep()) {
            throw new IllegalArgumentException(
                "complete V24-OBS-1A S0 inputs are required");
        }
        SpatialEcologyView view = context.getSpatialEcologyView();
        SpatialAnimalFact fact = view == null ? null
            : view.getAnimalFact(actor.getId());
        if(fact == null || !fact.isPredator() ||
           !fact.getSpecies().equals(actor.getProfile().getName())) {
            throw new IllegalStateException(
                "predator assessment must use its step-start spatial fact");
        }
        int population = context.getPopulation(fact.getSpecies());
        int recoveryPopulation = Math.max(1,
            context.getFoundingPopulation(actor.getProfile()) * 2);
        int effectiveMateRange = actor.getProfile().getBreedingRange();
        if(population < recoveryPopulation) {
            effectiveMateRange *= 2;
        }
        int adultFemale = 0;
        int adultMale = 0;
        int localAdultMates = 0;
        int nearestMateDistance = Integer.MAX_VALUE;
        TerrainNavigator.Reachability reachability =
            TerrainNavigator.reachableDistances(context.getTerrainMap(),
                fact.getLocation(), effectiveMateRange);
        for(SpatialAnimalFact other : view.getAnimalFacts()) {
            if(!fact.getSpecies().equals(other.getSpecies()) ||
               other.getLifeStage() != LifeStage.ADULT) {
                continue;
            }
            if(other.getSex() == Sex.FEMALE) adultFemale++;
            if(other.getSex() != Sex.MALE) continue;
            adultMale++;
            int distance = reachability.distanceTo(other.getLocation());
            if(distance > 0 && distance <= effectiveMateRange) {
                localAdultMates++;
                nearestMateDistance = Math.min(nearestMateDistance, distance);
            }
        }
        AnimalPhysiologyFact resource = resourceClock.getPhysiology();
        int maternalFoodFloor = actor.getProfile().getMaxFoodLevel() / 3 + 1;
        boolean foodEligible = resource.getFood() >= maternalFoodFloor;
        boolean staminaEligible = resource.getStamina() >= 30.0;
        boolean survivalEligible = !actor.isSurvivalCritical();
        boolean lifecycleEligible = fact.getSex() == Sex.FEMALE &&
            actor.canBreed();
        StepStartLocalCapacityFact capacity = context.getSoc1aFactView()
            .getLocalCapacity(actor.getId());
        String runId = "seed=" + context.getConfig().getRandomSeed() +
            ";terrain=" + context.getConfig().getTerrainSeed();
        return new S0Fact(context.getStep(), runId, resourceClock.getFrameId(),
            fact, resource,
            context.getClock()
            .getPhase(), decisionCandidates.size(), adultFemale, adultMale,
            population, localAdultMates, nearestMateDistance == Integer.MAX_VALUE
                ? -1 : nearestMateDistance, effectiveMateRange,
            maternalFoodFloor, foodEligible, staminaEligible, survivalEligible,
            lifecycleEligible, capacity,
            resourceClock.getRealizedKillCreditThroughStep());
    }

    static PredatorRecruitmentAssessment complete(S0Fact s0,
                                                   StepFrameLedger ledger)
    {
        return new PredatorRecruitmentAssessment(s0, ledger);
    }

    public int getStep() { return s0.step; }
    public String getRunId() { return s0.runId; }
    public String getFrameId() { return s0.frameId; }
    public String getLedgerId() { return ledger.getLedgerId(); }
    public long getActorId() { return s0.actorId; }
    public String getSpecies() { return s0.species; }
    public Sex getSex() { return s0.sex; }
    public LifeStage getLifeStage() { return s0.lifeStage; }
    public int getAge() { return s0.age; }
    public DayPhase getPhase() { return s0.phase; }
    public String getOpportunityId()
    {
        return ledger.getHuntOpportunity().getOpportunityId();
    }
    public int getDecisionCandidateCount() { return s0.decisionCandidateCount; }
    public int getPopulationAtStepStart() { return s0.populationAtStepStart; }
    public int getAdultFemaleCount() { return s0.adultFemaleCount; }
    public int getAdultMaleCount() { return s0.adultMaleCount; }
    public int getLocalAdultMateCount() { return s0.localAdultMateCount; }
    public int getNearestMateDistance() { return s0.nearestMateDistance; }
    public boolean hasNoMateRoute() { return s0.nearestMateDistance < 0; }
    public int getEffectiveMateRange() { return s0.effectiveMateRange; }
    public int getMaternalFoodFloor() { return s0.maternalFoodFloor; }
    public int getFood() { return s0.food; }
    public double getStamina() { return s0.stamina; }
    public double getHydration() { return s0.hydration; }
    public double getSurvivalPressure() { return s0.survivalPressure; }
    public boolean isFoodEligible() { return s0.foodEligible; }
    public boolean isStaminaEligible() { return s0.staminaEligible; }
    public boolean isSurvivalEligible() { return s0.survivalEligible; }
    public boolean isLifecycleEligible() { return s0.lifecycleEligible; }
    public boolean isMateEligible() { return s0.localAdultMateCount > 0; }
    /** Exact legacy mate-query result; separate from topology-only observation. */
    public boolean isLegacyMateEligible() { return legacyMateEligible; }
    public boolean isJointEligibilitySatisfied()
    {
        return s0.lifecycleEligible && s0.decisionCandidateCount > 0 &&
            legacyMateEligible;
    }
    public EligibilityState getCreditEligibility()
    {
        return EligibilityState.NOT_GATED_BY_LEGACY_SEMANTICS;
    }
    public EligibilityState getCapacityEligibility()
    {
        return EligibilityState.NOT_GATED_BY_LEGACY_SEMANTICS;
    }
    public StepStartLocalCapacityFact getLocalCapacity() { return s0.capacity; }
    public int getRealizedKillCreditThroughStep()
    {
        return s0.realizedKillCreditThroughStep;
    }
    public StepFrameLedger.ProposalReason getProposalReason()
    {
        return proposalReason;
    }
    public boolean isMateQueryRequested() { return mateQueryRequested; }
    public boolean isMateQueryConsumed() { return mateQueryConsumed; }
    public int getMateQueryRandomDrawCount()
    {
        return mateQueryRandomDrawCount;
    }
    public boolean isConceptionRollRequested() { return conceptionRollRequested; }
    public int getConceptionRollRandomDrawCount()
    {
        return conceptionRollRandomDrawCount;
    }
    public boolean isConceptionRollAccepted() { return conceptionRollAccepted; }
    public boolean isLitterDrawRequested() { return litterDrawRequested; }
    public int getLitterDrawRandomDrawCount()
    {
        return litterDrawRandomDrawCount;
    }
    public int getAcceptedConceptionCount() { return acceptedConceptionCount; }
    public int getPregnancyOpenedCount() { return pregnancyOpenedCount; }
    public int getDueBirthCount() { return dueBirthCount; }
    public int getPendingBirthCount() { return pendingBirthCount; }
    public String getConceptionCorrelationKey()
    {
        return acceptedConceptionCount == 0 ? "" :
            ledger.getReproductionProposal().getProposalId();
    }
    public String getPregnancyCorrelationKey()
    {
        return pregnancyOpenedCount == 0 ? "" : getConceptionCorrelationKey();
    }
    public String getDueCorrelationKey()
    {
        return dueBirthCount == 0 ? "" : getConceptionCorrelationKey();
    }
    public int getSubmittedOrdinalCount() { return submittedOrdinalCount; }
    public int getCommittedBirthCount() { return committedBirthCount; }
    public int getLostPositionCount() { return lostPositionCount; }
    public int getCancelledParentCount() { return cancelledParentCount; }
    public int getExplicitPregnancyLossCount()
    {
        return explicitPregnancyLossCount;
    }
    public List<StepFrameLedger.OrdinalOutcome> getOrdinalOutcomes()
    {
        return Collections.unmodifiableList(new ArrayList<>(
            ledger.getOrdinalOutcomes()));
    }
    public StepFrameLedger.TerminalReason getTerminalReason()
    {
        return ledger.getTerminalReason();
    }

    static final class S0Fact
    {
        private final int step;
        private final String runId;
        private final String frameId;
        private final long actorId;
        private final String species;
        private final Sex sex;
        private final LifeStage lifeStage;
        private final int age;
        private final DayPhase phase;
        private final int decisionCandidateCount;
        private final int populationAtStepStart;
        private final int adultFemaleCount;
        private final int adultMaleCount;
        private final int localAdultMateCount;
        private final int nearestMateDistance;
        private final int effectiveMateRange;
        private final int maternalFoodFloor;
        private final int food;
        private final double stamina;
        private final double hydration;
        private final double survivalPressure;
        private final boolean foodEligible;
        private final boolean staminaEligible;
        private final boolean survivalEligible;
        private final boolean lifecycleEligible;
        private final StepStartLocalCapacityFact capacity;
        private final int realizedKillCreditThroughStep;

        private S0Fact(int step, String runId, String frameId,
                       SpatialAnimalFact actor, AnimalPhysiologyFact physiology,
                       DayPhase phase, int decisionCandidateCount,
                       int adultFemaleCount, int adultMaleCount,
                       int populationAtStepStart, int localAdultMateCount,
                       int nearestMateDistance,
                       int effectiveMateRange, int maternalFoodFloor,
                       boolean foodEligible, boolean staminaEligible,
                       boolean survivalEligible, boolean lifecycleEligible,
                       StepStartLocalCapacityFact capacity,
                       int realizedKillCreditThroughStep)
        {
            this.step = step;
            this.runId = runId;
            this.frameId = frameId;
            actorId = actor.getId();
            species = actor.getSpecies();
            sex = actor.getSex();
            lifeStage = actor.getLifeStage();
            age = physiology.getAge();
            this.phase = phase;
            this.decisionCandidateCount = decisionCandidateCount;
            this.populationAtStepStart = populationAtStepStart;
            this.adultFemaleCount = adultFemaleCount;
            this.adultMaleCount = adultMaleCount;
            this.localAdultMateCount = localAdultMateCount;
            this.nearestMateDistance = nearestMateDistance;
            this.effectiveMateRange = effectiveMateRange;
            this.maternalFoodFloor = maternalFoodFloor;
            food = physiology.getFood();
            stamina = physiology.getStamina();
            hydration = physiology.getHydration();
            survivalPressure = physiology.getSurvivalPressure();
            this.foodEligible = foodEligible;
            this.staminaEligible = staminaEligible;
            this.survivalEligible = survivalEligible;
            this.lifecycleEligible = lifecycleEligible;
            this.capacity = capacity;
            this.realizedKillCreditThroughStep = realizedKillCreditThroughStep;
        }

    }
}
