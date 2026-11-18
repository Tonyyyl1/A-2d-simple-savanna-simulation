package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-context owner that joins PF1-aF facts without driving behavior. */
final class Pf1aFactLedger
{
    private final RealizedKillLedger realizedKills =
        new RealizedKillLedger();
    private final Map<Long, Draft> drafts = new LinkedHashMap<>();
    private RealizedKillWindowView killWindow;
    private SpatialEcologyView spatialView;
    private Soc1aFactView capacityView;
    private int step;

    void startStep(int step, SpatialEcologyView spatialView,
                   Soc1aFactView capacityView)
    {
        if(!drafts.isEmpty()) {
            throw new IllegalStateException(
                "previous PF1-aF assessment step was not finalized");
        }
        if(spatialView == null || capacityView == null ||
           spatialView.getStep() != step || capacityView.getStep() != step) {
            throw new IllegalArgumentException(
                "coherent L1-F/SOC1-aF step views are required");
        }
        this.step = step;
        this.spatialView = spatialView;
        this.capacityView = capacityView;
        killWindow = realizedKills.startStep(step);
    }

    void recordKill(RealizedKillRecord record)
    {
        realizedKills.append(record);
    }

    void onAnimalDeath(long animalId)
    {
        realizedKills.onAnimalDeath(animalId);
    }

    LegacyBreedingObservation recordLegacy(
        SavannahAnimal parent, DecisionBirthCandidateFact candidates,
        LegacyBreedingAssessment assessment, SimulationContext context)
    {
        if(parent == null || candidates == null || assessment == null ||
           context == null || step != context.getStep() ||
           killWindow == null) {
            throw new IllegalArgumentException(
                "PF1-aF capture has not started for this decision");
        }
        LegacyBreedingObservation observation =
            new LegacyBreedingObservation(step, parent, candidates,
                assessment);
        AnimalPhysiologyFact physiology =
            AnimalPhysiologyFact.capture(parent);
        Draft draft = new Draft(observation, physiology);
        if(parent.getProfile().isPredator()) {
            int range = HuntLocalOpportunity.effectiveRange(parent, context);
            draft.effectiveHuntingRange = range;
            draft.localResource = spatialView.getReachableResources(
                parent.getLocation(), range, parent.getProfile());
            draft.localCapacity =
                capacityView.getLocalCapacity(parent.getId());
            draft.kill30 = killWindow.getWindow(parent.getId(),
                RealizedKillLedger.WINDOW_30);
            draft.kill60 = killWindow.getWindow(parent.getId(),
                RealizedKillLedger.WINDOW_60);
        }
        if(drafts.put(Long.valueOf(parent.getId()), draft) != null) {
            throw new IllegalStateException(
                "duplicate same-step legacy breeding assessment: " +
                parent.getId());
        }
        return observation;
    }

    void recordCommittedBirth(SavannahAnimal parent, SavannahAnimal child,
                              int litterOrdinal, Location location)
    {
        Draft draft = requireDraft(parent);
        for(NewbornReserveFact existing : draft.newborns) {
            if(existing.getChildId() == child.getId() ||
               existing.getLitterOrdinal() == litterOrdinal) {
                throw new IllegalStateException(
                    "duplicate PF1-aF committed child or ordinal");
            }
        }
        draft.newborns.add(new NewbornReserveFact(child, litterOrdinal,
            location));
    }

    void recordBirthDebit(SavannahAnimal parent, int committedBirthCount,
                          AnimalPhysiologyFact before,
                          AnimalPhysiologyFact after)
    {
        Draft draft = requireDraft(parent);
        if(draft.debitRecorded || committedBirthCount <= 0 ||
           committedBirthCount != draft.newborns.size() ||
           before == null || after == null) {
            throw new IllegalStateException(
                "invalid or duplicate PF1-aF parent debit");
        }
        draft.debitRecorded = true;
        draft.preBirth = before;
        draft.postBirth = after;
    }

    Completion finishStep(Map<Long, SavannahAnimal> adultsById)
    {
        if(adultsById == null) {
            throw new IllegalArgumentException("adult index is required");
        }
        List<BreedingCommitFact> commits = new ArrayList<>();
        List<PredatorFemaleAssessmentFact> predatorRows =
            new ArrayList<>();
        for(Draft draft : drafts.values()) {
            if(!draft.debitRecorded) {
                SavannahAnimal parent = adultsById.get(
                    Long.valueOf(draft.observation.getParentId()));
                AnimalPhysiologyFact current = parent != null &&
                    parent.isAlive()
                    ? AnimalPhysiologyFact.capture(parent)
                    : draft.decisionPhysiology;
                draft.preBirth = current;
                draft.postBirth = current;
            }
            BreedingCommitFact commit = new BreedingCommitFact(step,
                draft.observation.getParentId(),
                draft.observation.getSpecies(), draft.newborns.size(),
                draft.preBirth, draft.postBirth, draft.newborns);
            commits.add(commit);
            if(draft.localResource != null) {
                predatorRows.add(new PredatorFemaleAssessmentFact(
                    draft.observation, draft.decisionPhysiology,
                    draft.effectiveHuntingRange, draft.localResource,
                    draft.localCapacity, draft.kill30, draft.kill60, commit));
            }
        }
        drafts.clear();
        return new Completion(commits, predatorRows);
    }

    RealizedKillWindowView getKillWindow()
    {
        if(killWindow == null) {
            throw new IllegalStateException(
                "PF1-aF kill window unavailable before capture");
        }
        return killWindow;
    }

    RealizedKillLedger getRealizedKills()
    {
        return realizedKills;
    }

    private Draft requireDraft(SavannahAnimal parent)
    {
        if(parent == null) {
            throw new IllegalArgumentException("parent is required");
        }
        Draft draft = drafts.get(Long.valueOf(parent.getId()));
        if(draft == null) {
            throw new IllegalStateException(
                "orphan BIRTH/debit without same-step legacy assessment: " +
                parent.getId());
        }
        return draft;
    }

    static final class Completion
    {
        private final List<BreedingCommitFact> commits;
        private final List<PredatorFemaleAssessmentFact> predatorRows;

        Completion(List<BreedingCommitFact> commits,
                   List<PredatorFemaleAssessmentFact> predatorRows)
        {
            this.commits = Collections.unmodifiableList(
                new ArrayList<>(commits));
            this.predatorRows = Collections.unmodifiableList(
                new ArrayList<>(predatorRows));
        }

        List<BreedingCommitFact> getCommits() { return commits; }

        List<PredatorFemaleAssessmentFact> getPredatorRows()
        {
            return predatorRows;
        }
    }

    private static final class Draft
    {
        private final LegacyBreedingObservation observation;
        private final AnimalPhysiologyFact decisionPhysiology;
        private final List<NewbornReserveFact> newborns =
            new ArrayList<>();
        private int effectiveHuntingRange;
        private ReachableResourceFact localResource;
        private StepStartLocalCapacityFact localCapacity;
        private RealizedKillWindowFact kill30;
        private RealizedKillWindowFact kill60;
        private AnimalPhysiologyFact preBirth;
        private AnimalPhysiologyFact postBirth;
        private boolean debitRecorded;

        Draft(LegacyBreedingObservation observation,
              AnimalPhysiologyFact decisionPhysiology)
        {
            this.observation = observation;
            this.decisionPhysiology = decisionPhysiology;
        }
    }
}
