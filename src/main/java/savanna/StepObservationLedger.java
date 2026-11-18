package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects completed actor ledgers and joins optional predator S0/S4 facts.
 * Step resets clear collected facts while retaining observer registrations.
 * Ecological state and random sources remain owned by SimulationContext.
 */
final class StepObservationLedger
{
    private final List<StepFrameLedgerListener> frameListeners = new ArrayList<>();
    private final List<PredatorRecruitmentAssessmentListener>
        predatorListeners = new ArrayList<>();
    private final List<LongHorizonTrophicStepListener> trophicListeners =
        new ArrayList<>();
    private final Map<Long, StepFrameLedger> frameLedgers = new LinkedHashMap<>();
    private final Map<Long, PredatorRecruitmentAssessment.S0Fact>
        predatorS0Facts = new LinkedHashMap<>();
    private final Map<Long, PredatorRecruitmentAssessment> predatorAssessments =
        new LinkedHashMap<>();

    void startStep()
    {
        frameLedgers.clear();
        predatorS0Facts.clear();
        predatorAssessments.clear();
    }

    void addFrameListener(StepFrameLedgerListener listener)
    {
        if(listener != null && !frameListeners.contains(listener)) {
            frameListeners.add(listener);
        }
    }

    void removeFrameListener(StepFrameLedgerListener listener)
    {
        frameListeners.remove(listener);
    }

    void addPredatorListener(PredatorRecruitmentAssessmentListener listener)
    {
        if(listener != null && !predatorListeners.contains(listener)) {
            predatorListeners.add(listener);
        }
    }

    void removePredatorListener(PredatorRecruitmentAssessmentListener listener)
    {
        predatorListeners.remove(listener);
    }

    boolean hasPredatorListeners()
    {
        return !predatorListeners.isEmpty();
    }

    void addTrophicListener(LongHorizonTrophicStepListener listener)
    {
        if(listener != null && !trophicListeners.contains(listener)) {
            trophicListeners.add(listener);
        }
    }

    void removeTrophicListener(LongHorizonTrophicStepListener listener)
    {
        trophicListeners.remove(listener);
    }

    void finishTrophicStep(Field committed, SimulationContext context)
    {
        if(trophicListeners.isEmpty()) return;
        LongHorizonTrophicStepFact fact =
            LongHorizonTrophicStepFact.capture(committed, context);
        for(LongHorizonTrophicStepListener listener : trophicListeners) {
            listener.onLongHorizonTrophicStep(fact);
        }
    }

    void recordPredatorS0(SavannahAnimal actor,
                          StepFrameLedger.ResourceClock resourceClock,
                          List<Location> decisionCandidates,
                          SimulationContext context)
    {
        if(!hasPredatorListeners() || actor == null ||
           !actor.getProfile().isPredator()) {
            return;
        }
        PredatorRecruitmentAssessment.S0Fact fact =
            PredatorRecruitmentAssessment.captureS0(actor, resourceClock,
                decisionCandidates, context);
        if(predatorS0Facts.putIfAbsent(Long.valueOf(actor.getId()), fact) != null) {
            throw new IllegalStateException(
                "duplicate V24-OBS-1A S0 predator assessment: " + actor.getId());
        }
    }

    void recordFrame(StepFrameLedger ledger, int step)
    {
        if(ledger == null || ledger.getStep() != step) {
            throw new IllegalStateException(
                "published frame ledger must belong to the active step");
        }
        if(frameLedgers.putIfAbsent(ledger.getActorId(), ledger) != null) {
            throw new IllegalStateException(
                "actor frame ledger published twice: " + ledger.getActorId());
        }
        for(StepFrameLedgerListener listener : frameListeners) {
            listener.onStepFrameLedger(ledger);
        }
        if(hasPredatorListeners() && isPredatorSpecies(ledger.getActorSpecies())) {
            PredatorRecruitmentAssessment.S0Fact s0 =
                predatorS0Facts.remove(Long.valueOf(ledger.getActorId()));
            if(s0 == null) {
                throw new IllegalStateException(
                    "V24-OBS-1A S4 predator ledger lacks its S0 fact: " +
                    ledger.getActorId());
            }
            PredatorRecruitmentAssessment assessment =
                PredatorRecruitmentAssessment.complete(s0, ledger);
            if(predatorAssessments.putIfAbsent(
                Long.valueOf(ledger.getActorId()), assessment) != null) {
                throw new IllegalStateException(
                    "duplicate V24-OBS-1A S4 predator assessment: " +
                    ledger.getActorId());
            }
            for(PredatorRecruitmentAssessmentListener listener : predatorListeners) {
                listener.onPredatorRecruitmentAssessment(assessment);
            }
        }
    }

    void finishPredatorStep(Set<Long> expectedActorIds, SimulationContext context)
    {
        if(!hasPredatorListeners()) return;
        Set<Long> expectedPredators = new LinkedHashSet<>();
        for(SpatialAnimalFact fact : context.getSpatialEcologyView().getAnimalFacts()) {
            if(fact.isPredator()) expectedPredators.add(Long.valueOf(fact.getId()));
        }
        if(expectedActorIds == null || !expectedActorIds.containsAll(expectedPredators) ||
           !predatorAssessments.keySet().equals(expectedPredators) ||
           !predatorS0Facts.isEmpty()) {
            throw new IllegalStateException(
                "V24-OBS-1A predator assessment coverage is incomplete");
        }
        for(PredatorRecruitmentAssessmentListener listener : predatorListeners) {
            listener.onPredatorRecruitmentStepComplete(context.getStep(),
                Collections.unmodifiableSet(expectedPredators));
        }
    }

    void requireCompleteFrames(Set<Long> expectedActorIds)
    {
        if(expectedActorIds == null || !frameLedgers.keySet().equals(expectedActorIds)) {
            throw new IllegalStateException(
                "S4 frame ledger identities differ from step-start actors");
        }
    }

    List<StepFrameLedger> getCurrentFrames()
    {
        return Collections.unmodifiableList(new ArrayList<>(frameLedgers.values()));
    }

    private static boolean isPredatorSpecies(String species)
    {
        SpeciesProfile profile = SpeciesRegistry.getProfile(species);
        return profile != null && profile.isPredator();
    }
}
