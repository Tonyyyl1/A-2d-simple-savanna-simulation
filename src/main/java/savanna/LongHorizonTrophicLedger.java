package savanna;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Streaming V24-OBS-1B trophic ledger. It consumes only immutable observation
 * facts and retains one step plus one 100-step aggregate for registry species.
 */
public final class LongHorizonTrophicLedger implements SimulationEventListener,
    StepFrameLedgerListener, PredatorRecruitmentAssessmentListener,
    Pf1aFactListener, LongHorizonTrophicStepListener
{
    public static final String SCHEMA = "v24-obs-1b-v1";

    private final Writer writer;
    private final Map<String, StepValues> current = newValues();
    private Map<String, IntervalValues> interval = newIntervals();
    private final Map<String, Integer> lastPopulation = new LinkedHashMap<>();
    private final Map<String, Integer> zeroMateRuns = new LinkedHashMap<>();
    private final Map<String, Integer> zeroConceptionRuns =
        new LinkedHashMap<>();
    private final Map<String, Integer> longestZeroMateRuns =
        new LinkedHashMap<>();
    private final Map<String, Integer> longestZeroConceptionRuns =
        new LinkedHashMap<>();
    private int activeStep = -1;
    private int lastCompletedStep;
    private int intervalStart = 1;
    private int completedIntervals;
    private boolean recruitmentStepClosed;
    private int recruitmentExpectedTotal;

    public LongHorizonTrophicLedger(Writer writer)
    {
        this.writer = writer;
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String species = factory.getProfile().getName();
            zeroMateRuns.put(species, Integer.valueOf(0));
            zeroConceptionRuns.put(species, Integer.valueOf(0));
            longestZeroMateRuns.put(species, Integer.valueOf(0));
            longestZeroConceptionRuns.put(species, Integer.valueOf(0));
        }
    }

    @Override
    public void onEvent(SimulationEvent event)
    {
        if(event == null) return;
        ensureStep(event.step);
        if(event.type == SimulationEvent.EventType.BIRTH) {
            String species = event.targetSpecies.isBlank()
                ? event.actorSpecies : event.targetSpecies;
            value(species).births++;
        }
        else if(event.type == SimulationEvent.EventType.DEATH) {
            StepValues value = value(event.actorSpecies);
            value.deaths++;
            increment(value.deathCauses, event.deathCause);
        }
        else if(event.type == SimulationEvent.EventType.GRAZE) {
            value(event.actorSpecies).grazes++;
        }
    }

    @Override
    public void onStepFrameLedger(StepFrameLedger ledger)
    {
        if(ledger == null) {
            throw new IllegalArgumentException("FRAME ledger is required");
        }
        ensureStep(ledger.getStep());
        StepValues value = value(ledger.getActorSpecies());
        value.frameLedgers++;
        SpeciesProfile profile = SpeciesRegistry.getProfile(
            ledger.getActorSpecies());
        if(profile == null || !profile.isPredator()) return;
        value.huntAssessments++;
        HuntOpportunityFact opportunity = ledger.getHuntOpportunity();
        int local = opportunity.getCandidateCount();
        value.localPreyCountSum += local;
        if(local > 0) {
            value.huntLocalReachable++;
            int nearest = Integer.MAX_VALUE;
            for(HuntOpportunityFact.CandidateFact candidate :
                opportunity.getCandidates()) {
                nearest = Math.min(nearest, candidate.getTopologyDistance());
                value.localPreyDistanceSum += candidate.getTopologyDistance();
            }
            value.nearestPreyDistance = Math.min(value.nearestPreyDistance,
                nearest);
        }
        HuntResult result = ledger.getPrimaryAction().getHuntResult();
        if(result == null) return;
        if(result.getOpportunityFact() == null ||
           !opportunity.hasSameIdentity(result.getOpportunityFact())) {
            throw new IllegalStateException(
                "OBS-1B HUNT result does not consume its exact S0 opportunity");
        }
        if(result.getOutcome() != HuntOutcome.INELIGIBLE &&
           result.getOutcome() != HuntOutcome.DECLINED &&
           result.getLocalReachableEdibleCount() != local) {
            throw new IllegalStateException(
                "OBS-1B active HUNT result changed its S0 local opportunity");
        }
        if(result.getOutcome() == HuntOutcome.FAILED_ATTEMPT ||
           result.getOutcome() == HuntOutcome.SUCCESS) {
            value.huntAttempts++;
        }
        if(result.getOutcome() == HuntOutcome.SUCCESS) value.huntSuccess++;
    }

    @Override
    public void onRealizedKill(RealizedKillRecord record)
    {
        if(record == null) return;
        ensureStep(record.getStep());
        StepValues value = value(record.getPredatorSpecies());
        value.realizedKills++;
        value.acceptedEnergy += record.getAcceptedFood();
    }

    @Override
    public void onPredatorRecruitmentAssessment(
        PredatorRecruitmentAssessment assessment)
    {
        if(assessment == null) {
            throw new IllegalArgumentException(
                "predator recruitment assessment is required");
        }
        ensureStep(assessment.getStep());
        StepValues value = value(assessment.getSpecies());
        value.recruitmentAssessments++;
        if(assessment.isMateQueryRequested()) value.mateQueries++;
        if(assessment.isLegacyMateEligible()) {
            value.mateEligible++;
            value.mateSeen = true;
        }
        value.acceptedConceptions += assessment.getAcceptedConceptionCount();
        value.pregnancyOpened += assessment.getPregnancyOpenedCount();
        value.dueBirths += assessment.getDueBirthCount();
        value.pendingBirths += assessment.getPendingBirthCount();
        value.submittedOrdinals += assessment.getSubmittedOrdinalCount();
        value.committedBirths += assessment.getCommittedBirthCount();
        value.lostPosition += assessment.getLostPositionCount();
        value.cancelledParent += assessment.getCancelledParentCount();
        value.pregnancyLoss += assessment.getExplicitPregnancyLossCount();
        if(assessment.getAcceptedConceptionCount() > 0) {
            value.conceptionSeen = true;
        }
    }

    @Override
    public void onPredatorRecruitmentStepComplete(int step,
                                                  Set<Long> predatorIds)
    {
        ensureStep(step);
        if(recruitmentStepClosed) {
            throw new IllegalStateException(
                "OBS-1B recruitment step closed twice");
        }
        recruitmentStepClosed = true;
        recruitmentExpectedTotal = predatorIds == null ? 0 : predatorIds.size();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            SpeciesProfile profile = factory.getProfile();
            if(!profile.isPredator()) continue;
            String species = profile.getName();
            StepValues value = value(species);
            int zeroMate = value.mateSeen ? 0 : get(zeroMateRuns, species) + 1;
            int zeroConception = value.conceptionSeen ? 0
                : get(zeroConceptionRuns, species) + 1;
            zeroMateRuns.put(species, Integer.valueOf(zeroMate));
            zeroConceptionRuns.put(species, Integer.valueOf(zeroConception));
            int longestMate = Math.max(get(longestZeroMateRuns, species),
                zeroMate);
            int longestConception = Math.max(
                get(longestZeroConceptionRuns, species), zeroConception);
            longestZeroMateRuns.put(species, Integer.valueOf(longestMate));
            longestZeroConceptionRuns.put(species,
                Integer.valueOf(longestConception));
            value.zeroMateRun = zeroMate;
            value.zeroConceptionRun = zeroConception;
            value.longestZeroMateRun = longestMate;
            value.longestZeroConceptionRun = longestConception;
        }
    }

    @Override
    public void onLongHorizonTrophicStep(LongHorizonTrophicStepFact fact)
    {
        if(fact == null) {
            throw new IllegalArgumentException("completed trophic fact is required");
        }
        ensureStep(fact.getStep());
        if(fact.getStep() != lastCompletedStep + 1) {
            throw new IllegalStateException(
                "OBS-1B completed steps must be contiguous from one");
        }
        int expectedPredators = 0;
        int assessedPredators = 0;
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String species = factory.getProfile().getName();
            StepValues value = value(species);
            LongHorizonTrophicStepFact.PopulationFact population =
                fact.getPopulation(species);
            if(population == null) {
                throw new IllegalStateException(
                    "OBS-1B missing registry population: " + species);
            }
            if(lastCompletedStep > 0 &&
               get(lastPopulation, species) != population.getStartPopulation()) {
                throw new IllegalStateException(
                    "OBS-1B population continuity failed for " + species);
            }
            if(value.frameLedgers != population.getStartPopulation() ||
               population.getEndPopulation() !=
                   population.getStartPopulation() + value.births - value.deaths ||
               value.deaths != sumDeaths(value.deathCauses)) {
                throw new IllegalStateException(
                    "OBS-1B step conservation failed for " + species);
            }
            if(factory.getProfile().isPredator()) {
                expectedPredators += population.getStartPopulation();
                assessedPredators += value.recruitmentAssessments;
                if(value.recruitmentAssessments !=
                       population.getStartPopulation() ||
                   value.huntAssessments != population.getStartPopulation() ||
                   value.huntSuccess != value.realizedKills ||
                   value.acceptedConceptions != value.pregnancyOpened ||
                   value.pregnancyOpened != value.dueBirths ||
                   value.pregnancyOpened != value.pendingBirths +
                       value.committedBirths + value.lostPosition +
                       value.cancelledParent + value.pregnancyLoss ||
                   value.pregnancyOpened != value.submittedOrdinals ||
                   value.committedBirths != value.births) {
                    throw new IllegalStateException(
                        "OBS-1B predator funnel failed for " + species);
                }
            }
            lastPopulation.put(species,
                Integer.valueOf(population.getEndPopulation()));
            writeStep(fact, species, population, value);
            interval.get(species).add(population, value);
        }
        if(!recruitmentStepClosed ||
           recruitmentExpectedTotal != expectedPredators ||
           assessedPredators != expectedPredators) {
            throw new IllegalStateException(
                "OBS-1B predator assessment coverage is incomplete");
        }
        if(fact.getStep() % LongHorizonTrophicStepFact.INTERVAL_STEPS == 0) {
            if(!fact.isResourceBoundary()) {
                throw new IllegalStateException(
                    "OBS-1B interval lacks resource/capacity boundary facts");
            }
            for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
                String species = factory.getProfile().getName();
                LongHorizonTrophicStepFact.CapacityFact capacity =
                    fact.getCapacity(species);
                LongHorizonTrophicStepFact.PopulationFact population =
                    fact.getPopulation(species);
                if(capacity == null ||
                   capacity.getActorCount() != population.getStartPopulation()) {
                    throw new IllegalStateException(
                        "OBS-1B capacity coverage failed for " + species);
                }
                writeInterval(fact, species, interval.get(species), capacity);
            }
            completedIntervals++;
            intervalStart = fact.getStep() + 1;
            interval = newIntervals();
        }
        lastCompletedStep = fact.getStep();
        clearCurrent();
    }

    public int getCompletedIntervals() { return completedIntervals; }

    public int getRetainedCurrentStepKeyCount()
    {
        return activeStep < 0 ? 0 : current.size();
    }

    public int getRetainedIntervalSpeciesCount()
    {
        return interval.size();
    }

    public void flush()
    {
        if(writer == null) return;
        try {
            writer.flush();
        }
        catch(IOException exception) {
            throw new IllegalStateException("cannot flush OBS-1B stream", exception);
        }
    }

    private void writeStep(LongHorizonTrophicStepFact fact, String species,
                           LongHorizonTrophicStepFact.PopulationFact population,
                           StepValues value)
    {
        StringBuilder row = prefix("STEP", fact.getRunId())
            .append("\tstep=").append(fact.getStep())
            .append("\tspecies=").append(species)
            .append("\tpopulationStart=").append(population.getStartPopulation())
            .append("\tpopulationEnd=").append(population.getEndPopulation())
            .append("\tjuvenile=").append(population.getJuvenile())
            .append("\tcub=").append(population.getCub())
            .append("\tadultFemale=").append(population.getAdultFemale())
            .append("\tadultMale=").append(population.getAdultMale());
        appendCounts(row, value);
        LongHorizonTrophicStepFact.CapacityFact capacity =
            fact.getCapacity(species);
        appendBoundary(row, fact, capacity);
        row.append("\tclosed=true\n");
        write(row.toString());
    }

    private void writeInterval(LongHorizonTrophicStepFact fact, String species,
                               IntervalValues value,
                               LongHorizonTrophicStepFact.CapacityFact capacity)
    {
        double perCapita = (value.populationEnd - value.populationStart) /
            (double)Math.max(1, value.populationStart);
        StringBuilder row = prefix("INTERVAL", fact.getRunId())
            .append("\tstart=").append(intervalStart)
            .append("\tend=").append(fact.getStep())
            .append("\tspecies=").append(species)
            .append("\tpopulationStart=").append(value.populationStart)
            .append("\tpopulationEnd=").append(value.populationEnd)
            .append("\tjuvenile=").append(value.juvenile)
            .append("\tcub=").append(value.cub)
            .append("\tadultFemale=").append(value.adultFemale)
            .append("\tadultMale=").append(value.adultMale);
        appendCounts(row, value);
        row.append("\tperCapitaGrowthHex=")
            .append(Double.toHexString(perCapita));
        appendBoundary(row, fact, capacity);
        row.append("\tclosed=true\n");
        write(row.toString());
    }

    private static void appendCounts(StringBuilder row, Counts value)
    {
        row.append("\tbirths=").append(value.births)
            .append("\tdeaths=").append(value.deaths);
        for(DeathCause cause : observedDeathCauses()) {
            row.append("\tdeath").append(cause.name()).append('=')
                .append(count(value.deathCauses, cause));
        }
        row.append("\tnetGrowth=").append(value.births - value.deaths)
            .append("\tpredationDeaths=")
            .append(count(value.deathCauses, DeathCause.PREDATION))
            .append("\totherDeaths=")
            .append(value.deaths - count(value.deathCauses,
                DeathCause.PREDATION))
            .append("\tframeLedgers=").append(value.frameLedgers)
            .append("\thuntAssessments=").append(value.huntAssessments)
            .append("\thuntLocalReachable=").append(value.huntLocalReachable)
            .append("\thuntAttempts=").append(value.huntAttempts)
            .append("\thuntSuccess=").append(value.huntSuccess)
            .append("\trealizedKills=").append(value.realizedKills)
            .append("\tacceptedEnergy=").append(value.acceptedEnergy)
            .append("\tlocalPreyCountSum=").append(value.localPreyCountSum)
            .append("\tlocalPreyDistanceSum=")
            .append(value.localPreyDistanceSum)
            .append("\tnearestPreyDistance=")
            .append(value.nearestPreyDistance == Integer.MAX_VALUE ? -1
                : value.nearestPreyDistance)
            .append("\trecruitmentAssessments=")
            .append(value.recruitmentAssessments)
            .append("\tmateQueries=").append(value.mateQueries)
            .append("\tmateEligible=").append(value.mateEligible)
            .append("\tacceptedConceptions=")
            .append(value.acceptedConceptions)
            .append("\tpregnancyOpened=").append(value.pregnancyOpened)
            .append("\tdueBirths=").append(value.dueBirths)
            .append("\tpendingBirths=").append(value.pendingBirths)
            .append("\tsubmittedOrdinals=").append(value.submittedOrdinals)
            .append("\tcommittedBirths=").append(value.committedBirths)
            .append("\tlostPosition=").append(value.lostPosition)
            .append("\tcancelledParent=").append(value.cancelledParent)
            .append("\tpregnancyLoss=").append(value.pregnancyLoss)
            .append("\tzeroMateRunEnd=").append(value.zeroMateRun)
            .append("\tlongestZeroMateRun=")
            .append(value.longestZeroMateRun)
            .append("\tzeroConceptionRunEnd=")
            .append(value.zeroConceptionRun)
            .append("\tlongestZeroConceptionRun=")
            .append(value.longestZeroConceptionRun)
            .append("\tgrazes=").append(value.grazes);
    }

    private static void appendBoundary(StringBuilder row,
                                       LongHorizonTrophicStepFact fact,
                                       LongHorizonTrophicStepFact.CapacityFact capacity)
    {
        row.append("\tresourceBoundary=").append(fact.isResourceBoundary())
            .append("\tgrassAfterGrowthMeanHex=")
            .append(Double.toHexString(fact.getGrassAfterGrowthMean()))
            .append("\tgrassAtStepEndMeanHex=")
            .append(Double.toHexString(fact.getGrassAtStepEndMean()))
            .append("\tcapacityActors=").append(capacity.getActorCount())
            .append("\tcapacityPassableSum=")
            .append(capacity.getPassableCellSum())
            .append("\tcapacityOccupantSum=")
            .append(capacity.getLivingOccupantSum())
            .append("\tcapacityConspecificSum=")
            .append(capacity.getConspecificSum())
            .append("\tcapacityPredatorSum=")
            .append(capacity.getPredatorSum())
            .append("\tcapacityEdiblePreySum=")
            .append(capacity.getEdiblePreySum());
    }

    private static StringBuilder prefix(String type, String runId)
    {
        return new StringBuilder(type).append("\tschema=").append(SCHEMA)
            .append("\tintervalSteps=")
            .append(LongHorizonTrophicStepFact.INTERVAL_STEPS)
            .append("\trun=").append(runId);
    }

    private void ensureStep(int step)
    {
        if(step <= 0) {
            throw new IllegalArgumentException("OBS-1B step must be positive");
        }
        if(activeStep < 0) activeStep = step;
        else if(activeStep != step) {
            throw new IllegalStateException(
                "OBS-1B step was not closed before the next step");
        }
    }

    private StepValues value(String species)
    {
        StepValues result = current.get(species);
        if(result == null) {
            throw new IllegalStateException(
                "OBS-1B encountered unknown species: " + species);
        }
        return result;
    }

    private void clearCurrent()
    {
        for(String species : current.keySet()) {
            current.put(species, new StepValues());
        }
        activeStep = -1;
        recruitmentStepClosed = false;
        recruitmentExpectedTotal = 0;
    }

    private void write(String value)
    {
        if(writer == null) return;
        try {
            writer.write(value);
        }
        catch(IOException exception) {
            throw new IllegalStateException("cannot write OBS-1B stream", exception);
        }
    }

    private static Map<String, StepValues> newValues()
    {
        Map<String, StepValues> result = new LinkedHashMap<>();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            result.put(factory.getProfile().getName(), new StepValues());
        }
        return result;
    }

    private static Map<String, IntervalValues> newIntervals()
    {
        Map<String, IntervalValues> result = new LinkedHashMap<>();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            result.put(factory.getProfile().getName(), new IntervalValues());
        }
        return result;
    }

    private static int get(Map<String, Integer> values, String species)
    {
        Integer result = values.get(species);
        return result == null ? 0 : result.intValue();
    }

    private static int count(Map<DeathCause, Integer> values, DeathCause cause)
    {
        Integer result = values.get(cause);
        return result == null ? 0 : result.intValue();
    }

    private static void increment(Map<DeathCause, Integer> values,
                                  DeathCause cause)
    {
        values.put(cause, Integer.valueOf(count(values, cause) + 1));
    }

    private static int sumDeaths(Map<DeathCause, Integer> values)
    {
        int result = 0;
        for(DeathCause cause : observedDeathCauses()) {
            result += count(values, cause);
        }
        return result;
    }

    private static DeathCause[] observedDeathCauses()
    {
        return new DeathCause[] { DeathCause.AGE, DeathCause.STARVATION,
            DeathCause.DEHYDRATION, DeathCause.DISEASE,
            DeathCause.PREDATION, DeathCause.POSITION_CONFLICT,
            DeathCause.UNKNOWN };
    }

    private static class Counts
    {
        protected long births;
        protected long deaths;
        protected final Map<DeathCause, Integer> deathCauses =
            new EnumMap<>(DeathCause.class);
        protected long frameLedgers;
        protected long huntAssessments;
        protected long huntLocalReachable;
        protected long huntAttempts;
        protected long huntSuccess;
        protected long realizedKills;
        protected long acceptedEnergy;
        protected long localPreyCountSum;
        protected long localPreyDistanceSum;
        protected int nearestPreyDistance = Integer.MAX_VALUE;
        protected long recruitmentAssessments;
        protected long mateQueries;
        protected long mateEligible;
        protected long acceptedConceptions;
        protected long pregnancyOpened;
        protected long dueBirths;
        protected long pendingBirths;
        protected long submittedOrdinals;
        protected long committedBirths;
        protected long lostPosition;
        protected long cancelledParent;
        protected long pregnancyLoss;
        protected int zeroMateRun;
        protected int longestZeroMateRun;
        protected int zeroConceptionRun;
        protected int longestZeroConceptionRun;
        protected long grazes;
    }

    private static final class StepValues extends Counts
    {
        private boolean mateSeen;
        private boolean conceptionSeen;
    }

    private static final class IntervalValues extends Counts
    {
        private int populationStart = -1;
        private int populationEnd;
        private int juvenile;
        private int cub;
        private int adultFemale;
        private int adultMale;

        private void add(LongHorizonTrophicStepFact.PopulationFact population,
                         StepValues value)
        {
            if(populationStart < 0) {
                populationStart = population.getStartPopulation();
            }
            populationEnd = population.getEndPopulation();
            juvenile = population.getJuvenile();
            cub = population.getCub();
            adultFemale = population.getAdultFemale();
            adultMale = population.getAdultMale();
            births += value.births;
            deaths += value.deaths;
            for(DeathCause cause : observedDeathCauses()) {
                int sum = count(deathCauses, cause) +
                    count(value.deathCauses, cause);
                deathCauses.put(cause, Integer.valueOf(sum));
            }
            frameLedgers += value.frameLedgers;
            huntAssessments += value.huntAssessments;
            huntLocalReachable += value.huntLocalReachable;
            huntAttempts += value.huntAttempts;
            huntSuccess += value.huntSuccess;
            realizedKills += value.realizedKills;
            acceptedEnergy += value.acceptedEnergy;
            localPreyCountSum += value.localPreyCountSum;
            localPreyDistanceSum += value.localPreyDistanceSum;
            nearestPreyDistance = Math.min(nearestPreyDistance,
                value.nearestPreyDistance);
            recruitmentAssessments += value.recruitmentAssessments;
            mateQueries += value.mateQueries;
            mateEligible += value.mateEligible;
            acceptedConceptions += value.acceptedConceptions;
            pregnancyOpened += value.pregnancyOpened;
            dueBirths += value.dueBirths;
            pendingBirths += value.pendingBirths;
            submittedOrdinals += value.submittedOrdinals;
            committedBirths += value.committedBirths;
            lostPosition += value.lostPosition;
            cancelledParent += value.cancelledParent;
            pregnancyLoss += value.pregnancyLoss;
            zeroMateRun = value.zeroMateRun;
            longestZeroMateRun = Math.max(longestZeroMateRun,
                value.longestZeroMateRun);
            zeroConceptionRun = value.zeroConceptionRun;
            longestZeroConceptionRun = Math.max(longestZeroConceptionRun,
                value.longestZeroConceptionRun);
            grazes += value.grazes;
        }
    }
}
