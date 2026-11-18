package savanna;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Streaming V24-OBS-1A observer. It retains only species summaries and the
 * current step's reconciliation keys; complete assessment rows are written as
 * they arrive and never retained as an object graph.
 */
public final class PredatorRecruitmentFunnel
    implements PredatorRecruitmentAssessmentListener, SimulationEventListener
{
    private final Writer writer;
    private final Map<String, SpeciesSummary> summaries = new LinkedHashMap<>();
    private final Map<Long, Integer> birthsByParent = new LinkedHashMap<>();
    private final Set<Long> seenPredators = new LinkedHashSet<>();
    private final Map<String, Boolean> mateSeenBySpecies = new LinkedHashMap<>();
    private final Map<String, Boolean> conceptionSeenBySpecies =
        new LinkedHashMap<>();
    private int activeStep = -1;

    public PredatorRecruitmentFunnel(Writer writer)
    {
        this.writer = writer;
    }

    @Override
    public void onEvent(SimulationEvent event)
    {
        if(event == null || !isPredator(event.actorSpecies)) return;
        ensureStep(event.step);
        SpeciesSummary summary = summary(event.actorSpecies);
        if(event.type == SimulationEvent.EventType.BIRTH) {
            Integer prior = birthsByParent.get(Long.valueOf(event.actorId));
            birthsByParent.put(Long.valueOf(event.actorId), Integer.valueOf(
                (prior == null ? 0 : prior.intValue()) + 1));
            summary.birthEvents++;
        }
        else if(event.type == SimulationEvent.EventType.DEATH) {
            Integer prior = summary.deaths.get(event.deathCause);
            summary.deaths.put(event.deathCause, Integer.valueOf(
                (prior == null ? 0 : prior.intValue()) + 1));
        }
        write("EVENT\t" + event.step + '\t' + event.type + '\t' +
            event.actorId + '\t' + event.actorSpecies + '\t' +
            event.deathCause + '\n');
    }

    @Override
    public void onPredatorRecruitmentAssessment(
        PredatorRecruitmentAssessment assessment)
    {
        if(assessment == null) {
            throw new IllegalArgumentException("assessment is required");
        }
        ensureStep(assessment.getStep());
        if(!seenPredators.add(Long.valueOf(assessment.getActorId()))) {
            throw new IllegalStateException(
                "duplicate V24-OBS-1A predator assessment: " +
                assessment.getActorId());
        }
        Integer recordedBirths = birthsByParent.get(Long.valueOf(
            assessment.getActorId()));
        if((recordedBirths == null ? 0 : recordedBirths.intValue()) !=
           assessment.getCommittedBirthCount()) {
            throw new IllegalStateException(
                "birth event and committed ordinal counts disagree for " +
                assessment.getActorId());
        }
        SpeciesSummary summary = summary(assessment.getSpecies());
        summary.assessments++;
        increment(summary.reasons, assessment.getProposalReason());
        increment(summary.terminals, assessment.getTerminalReason());
        summary.acceptedConceptions += assessment.getAcceptedConceptionCount();
        summary.pregnancyOpened += assessment.getPregnancyOpenedCount();
        summary.dueBirths += assessment.getDueBirthCount();
        summary.pendingBirths += assessment.getPendingBirthCount();
        summary.submittedOrdinals += assessment.getSubmittedOrdinalCount();
        summary.committedBirths += assessment.getCommittedBirthCount();
        summary.lostPosition += assessment.getLostPositionCount();
        summary.cancelledParent += assessment.getCancelledParentCount();
        summary.explicitPregnancyLoss +=
            assessment.getExplicitPregnancyLossCount();
        summary.minimumPopulation = Math.min(summary.minimumPopulation,
            assessment.getPopulationAtStepStart());
        if(assessment.isLegacyMateEligible()) {
            mateSeenBySpecies.put(assessment.getSpecies(), Boolean.TRUE);
        }
        if(assessment.isConceptionRollAccepted()) {
            conceptionSeenBySpecies.put(assessment.getSpecies(), Boolean.TRUE);
        }
        write(row(assessment));
    }

    @Override
    public void onPredatorRecruitmentStepComplete(int step,
                                                  Set<Long> predatorIds)
    {
        ensureStep(step);
        Set<Long> expected = predatorIds == null ? Collections.<Long>emptySet()
            : new LinkedHashSet<>(predatorIds);
        if(!seenPredators.equals(expected)) {
            throw new IllegalStateException(
                "V24-OBS-1A assessment coverage differs from step-start predators");
        }
        for(Map.Entry<String, SpeciesSummary> entry : summaries.entrySet()) {
            String species = entry.getKey();
            SpeciesSummary summary = entry.getValue();
            boolean mate = Boolean.TRUE.equals(mateSeenBySpecies.get(species));
            boolean conception = Boolean.TRUE.equals(
                conceptionSeenBySpecies.get(species));
            summary.zeroMateRun = mate ? 0 : summary.zeroMateRun + 1;
            summary.longestZeroMateRun = Math.max(summary.longestZeroMateRun,
                summary.zeroMateRun);
            summary.zeroConceptionRun = conception ? 0
                : summary.zeroConceptionRun + 1;
            summary.longestZeroConceptionRun = Math.max(
                summary.longestZeroConceptionRun, summary.zeroConceptionRun);
            if(summary.acceptedConceptions != summary.pregnancyOpened ||
               summary.pregnancyOpened != summary.committedBirths +
               summary.pendingBirths + summary.lostPosition +
               summary.cancelledParent + summary.explicitPregnancyLoss ||
               summary.dueBirths != summary.pregnancyOpened ||
               summary.submittedOrdinals != summary.pregnancyOpened) {
                throw new IllegalStateException(
                    "V24-OBS-1A lifecycle does not close for " + species);
            }
        }
        write("STEP\tstep=" + step + "\tassessments=" +
            seenPredators.size() + "\texpected=" + expected.size() +
            "\tclosed=true\n");
        birthsByParent.clear();
        seenPredators.clear();
        mateSeenBySpecies.clear();
        conceptionSeenBySpecies.clear();
        activeStep = -1;
    }

    /** A small immutable summary; full rows remain only in the output stream. */
    public Map<String, Summary> snapshotSummaries()
    {
        Map<String, Summary> result = new LinkedHashMap<>();
        for(Map.Entry<String, SpeciesSummary> entry : summaries.entrySet()) {
            result.put(entry.getKey(), new Summary(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    public int getRetainedCurrentStepKeyCount()
    {
        return birthsByParent.size() + seenPredators.size() +
            mateSeenBySpecies.size() + conceptionSeenBySpecies.size();
    }

    public void flush()
    {
        if(writer == null) return;
        try {
            writer.flush();
        }
        catch(IOException exception) {
            throw new IllegalStateException("cannot flush OBS-1A stream", exception);
        }
    }

    private void ensureStep(int step)
    {
        if(step < 0) throw new IllegalArgumentException("step must be non-negative");
        if(activeStep < 0) {
            activeStep = step;
        }
        else if(activeStep != step) {
            throw new IllegalStateException(
                "V24-OBS-1A step was not closed before next step");
        }
    }

    private SpeciesSummary summary(String species)
    {
        SpeciesSummary result = summaries.get(species);
        if(result == null) {
            result = new SpeciesSummary();
            summaries.put(species, result);
        }
        return result;
    }

    private static boolean isPredator(String species)
    {
        SpeciesProfile profile = SpeciesRegistry.getProfile(species);
        return profile != null && profile.isPredator();
    }

    private static <T> void increment(Map<T, Integer> values, T key)
    {
        Integer prior = values.get(key);
        values.put(key, Integer.valueOf((prior == null ? 0 : prior.intValue()) + 1));
    }

    private static String row(PredatorRecruitmentAssessment value)
    {
        StepStartLocalCapacityFact capacity = value.getLocalCapacity();
        return "ASSESSMENT\trun=" + value.getRunId() + "\tstep=" +
            value.getStep() + "\tactor=" + value.getActorId() + "\tspecies=" +
            value.getSpecies() + "\tsex=" + value.getSex() + "\tage=" +
            value.getAge() + "\tlifecycle=" + value.getLifeStage() +
            "\tphase=" + value.getPhase() + "\topportunity=" +
            value.getOpportunityId() + "\tledger=" + value.getLedgerId() +
            "\tproposal=" + value.getProposalReason() + "\tterminal=" +
            value.getTerminalReason() + "\tpopulation=" +
            value.getPopulationAtStepStart() + "\tadultFemale=" +
            value.getAdultFemaleCount() + "\tadultMale=" +
            value.getAdultMaleCount() + "\tlocalAdultMates=" +
            value.getLocalAdultMateCount() + "\tnearestReachableMate=" +
            value.getNearestMateDistance() + "\tnoMateRoute=" +
            value.hasNoMateRoute() + "\teffectiveMateRange=" +
            value.getEffectiveMateRange() + "\tlegacyMateEligible=" +
            value.isLegacyMateEligible() + "\tdecisionCandidates=" +
            value.getDecisionCandidateCount() + "\tfood=" + value.getFood() +
            "\tstamina=" + value.getStamina() + "\thydration=" +
            value.getHydration() + "\tsurvivalPressure=" +
            value.getSurvivalPressure() + "\tmaternalFoodFloor=" +
            value.getMaternalFoodFloor() + "\tfoodEligible=" +
            value.isFoodEligible() + "\tstaminaEligible=" +
            value.isStaminaEligible() + "\tsurvivalEligible=" +
            value.isSurvivalEligible() + "\tlifecycleEligible=" +
            value.isLifecycleEligible() + "\tjointEligible=" +
            value.isJointEligibilitySatisfied() + "\tcapacityEligibility=" +
            value.getCapacityEligibility() + "\tcapacityPassable=" +
            capacity.getPassableCellCount() + "\tcapacityOccupants=" +
            capacity.getLivingOccupantCount() + "\tcapacityConspecific=" +
            capacity.getConspecificCount() + "\tcreditEligibility=" +
            value.getCreditEligibility() + "\trealizedKillCredit=" +
            value.getRealizedKillCreditThroughStep() + "\tmateQueryRequested=" +
            value.isMateQueryRequested() + "\tmateQueryConsumed=" +
            value.isMateQueryConsumed() + "\tmateQueryDraws=" +
            value.getMateQueryRandomDrawCount() + "\tconceptionRollRequested=" +
            value.isConceptionRollRequested() + "\tconceptionRollDraws=" +
            value.getConceptionRollRandomDrawCount() + "\tconceptionRollAccepted=" +
            value.isConceptionRollAccepted() + "\tlitterDrawRequested=" +
            value.isLitterDrawRequested() + "\tlitterDraws=" +
            value.getLitterDrawRandomDrawCount() + "\tconceptionKey=" +
            value.getConceptionCorrelationKey() + "\tpregnancyKey=" +
            value.getPregnancyCorrelationKey() + "\tdueKey=" +
            value.getDueCorrelationKey() + "\tacceptedConceptions=" +
            value.getAcceptedConceptionCount() + "\tpregnancyOpened=" +
            value.getPregnancyOpenedCount() + "\tdueBirths=" +
            value.getDueBirthCount() + "\tpendingBirths=" +
            value.getPendingBirthCount() + "\tsubmittedOrdinals=" +
            value.getSubmittedOrdinalCount() + "\tcommittedBirths=" +
            value.getCommittedBirthCount() + "\tlostPosition=" +
            value.getLostPositionCount() + "\tcancelledParent=" +
            value.getCancelledParentCount() + "\tpregnancyLoss=" +
            value.getExplicitPregnancyLossCount() + '\n';
    }

    private void write(String value)
    {
        if(writer == null) return;
        try {
            writer.write(value);
        }
        catch(IOException exception) {
            throw new IllegalStateException("cannot write OBS-1A stream", exception);
        }
    }

    private static final class SpeciesSummary
    {
        private int assessments;
        private final Map<StepFrameLedger.ProposalReason, Integer> reasons =
            new EnumMap<>(StepFrameLedger.ProposalReason.class);
        private final Map<StepFrameLedger.TerminalReason, Integer> terminals =
            new EnumMap<>(StepFrameLedger.TerminalReason.class);
        private final Map<DeathCause, Integer> deaths =
            new EnumMap<>(DeathCause.class);
        private int birthEvents;
        private int acceptedConceptions;
        private int pregnancyOpened;
        private int dueBirths;
        private int pendingBirths;
        private int submittedOrdinals;
        private int committedBirths;
        private int lostPosition;
        private int cancelledParent;
        private int explicitPregnancyLoss;
        private int minimumPopulation = Integer.MAX_VALUE;
        private int zeroMateRun;
        private int longestZeroMateRun;
        private int zeroConceptionRun;
        private int longestZeroConceptionRun;
    }

    public static final class Summary
    {
        private final int assessments;
        private final Map<StepFrameLedger.ProposalReason, Integer> reasons;
        private final Map<StepFrameLedger.TerminalReason, Integer> terminals;
        private final Map<DeathCause, Integer> deaths;
        private final int birthEvents;
        private final int acceptedConceptions;
        private final int pregnancyOpened;
        private final int dueBirths;
        private final int pendingBirths;
        private final int submittedOrdinals;
        private final int committedBirths;
        private final int lostPosition;
        private final int cancelledParent;
        private final int explicitPregnancyLoss;
        private final int minimumPopulation;
        private final int longestZeroMateRun;
        private final int longestZeroConceptionRun;

        private Summary(SpeciesSummary value)
        {
            assessments = value.assessments;
            reasons = Collections.unmodifiableMap(new EnumMap<>(value.reasons));
            terminals = Collections.unmodifiableMap(new EnumMap<>(value.terminals));
            deaths = Collections.unmodifiableMap(new EnumMap<>(value.deaths));
            birthEvents = value.birthEvents;
            acceptedConceptions = value.acceptedConceptions;
            pregnancyOpened = value.pregnancyOpened;
            dueBirths = value.dueBirths;
            pendingBirths = value.pendingBirths;
            submittedOrdinals = value.submittedOrdinals;
            committedBirths = value.committedBirths;
            lostPosition = value.lostPosition;
            cancelledParent = value.cancelledParent;
            explicitPregnancyLoss = value.explicitPregnancyLoss;
            minimumPopulation = value.minimumPopulation == Integer.MAX_VALUE
                ? 0 : value.minimumPopulation;
            longestZeroMateRun = value.longestZeroMateRun;
            longestZeroConceptionRun = value.longestZeroConceptionRun;
        }

        public int getAssessments() { return assessments; }
        public Map<StepFrameLedger.ProposalReason, Integer> getReasons()
        {
            return reasons;
        }
        public Map<DeathCause, Integer> getDeaths() { return deaths; }
        public Map<StepFrameLedger.TerminalReason, Integer> getTerminals()
        {
            return terminals;
        }
        public int getBirthEvents() { return birthEvents; }
        public int getAcceptedConceptions() { return acceptedConceptions; }
        public int getPregnancyOpened() { return pregnancyOpened; }
        public int getDueBirths() { return dueBirths; }
        public int getPendingBirths() { return pendingBirths; }
        public int getSubmittedOrdinals() { return submittedOrdinals; }
        public int getCommittedBirths() { return committedBirths; }
        public int getLostPosition() { return lostPosition; }
        public int getCancelledParent() { return cancelledParent; }
        public int getExplicitPregnancyLoss() { return explicitPregnancyLoss; }
        public int getMinimumPopulation() { return minimumPopulation; }
        public int getLongestZeroMateRun() { return longestZeroMateRun; }
        public int getLongestZeroConceptionRun()
        {
            return longestZeroConceptionRun;
        }
    }
}
