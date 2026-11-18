package savanna;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure GATE-1 contracts for same-frame HUNT facts and named differences. */
public final class V24D2Contract
{
    public static final long BOUNDARY_ACTOR_ID = 7001L;
    public static final int BOUNDARY_STEP = 48;
    public static final String BOUNDARY_FRAME_ID = "frame-48";
    public static final double PRE_CONCEPTION_STAMINA = 100.0;
    public static final double POST_CONCEPTION_STAMINA = 60.0;
    public static final int FROZEN_RANGE = 7;
    public static final int RECOMPUTED_RANGE = 6;
    public static final String UNIQUE_CANDIDATE =
        "Gazelle#9001@topology-distance=7";

    private V24D2Contract() {}

    public static final class OpportunityFact
    {
        private final long actorId;
        private final int step;
        private final String frameId;
        private final String opportunityId;
        private final String species;
        private final String sex;
        private final boolean adult;
        private final double captureStamina;
        private final int range;
        private final String candidateDigest;
        private final int candidateCount;

        private OpportunityFact(long actorId, int step, String frameId,
                                String opportunityId, String species,
                                String sex, boolean adult,
                                double captureStamina, int range,
                                String candidateDigest, int candidateCount)
        {
            this.actorId = actorId;
            this.step = step;
            this.frameId = requireText(frameId, "frame id");
            this.opportunityId = requireText(opportunityId, "opportunity id");
            this.species = requireText(species, "species");
            this.sex = requireText(sex, "sex");
            this.adult = adult;
            this.captureStamina = captureStamina;
            this.range = range;
            this.candidateDigest = requireText(candidateDigest,
                "candidate digest");
            this.candidateCount = candidateCount;
        }

        public long getActorId() { return actorId; }
        public int getStep() { return step; }
        public String getFrameId() { return frameId; }
        public String getOpportunityId() { return opportunityId; }
        public String getSpecies() { return species; }
        public String getSex() { return sex; }
        public boolean isAdult() { return adult; }
        public double getCaptureStamina() { return captureStamina; }
        public int getRange() { return range; }
        public String getCandidateDigest() { return candidateDigest; }
        public int getCandidateCount() { return candidateCount; }

        public OpportunityFact withOpportunityId(String value)
        {
            return copy(value, range, candidateDigest, candidateCount,
                actorId, step, frameId);
        }

        public OpportunityFact withRange(int value)
        {
            return copy(opportunityId, value, candidateDigest, candidateCount,
                actorId, step, frameId);
        }

        public OpportunityFact withCandidateDigest(String value)
        {
            return copy(opportunityId, range, value, candidateCount,
                actorId, step, frameId);
        }

        public OpportunityFact withCandidateCount(int value)
        {
            return copy(opportunityId, range, candidateDigest, value,
                actorId, step, frameId);
        }

        private OpportunityFact copy(String id, int newRange, String digest,
                                     int count, long newActor, int newStep,
                                     String newFrame)
        {
            return new OpportunityFact(newActor, newStep, newFrame, id,
                species, sex, adult, captureStamina, newRange, digest, count);
        }
    }

    public static final class Reconciliation
    {
        private final List<String> violations;

        private Reconciliation(List<String> violations)
        {
            this.violations = Collections.unmodifiableList(
                new ArrayList<>(violations));
        }

        public boolean isExact() { return violations.isEmpty(); }
        public List<String> getViolations() { return violations; }
    }

    public static final class BoundaryScenario
    {
        private final OpportunityFact decision;
        private final OpportunityFact execution;
        private final boolean conceptionAccepted;
        private final boolean executionRecomputedLiveStamina;

        private BoundaryScenario(OpportunityFact decision,
                                 OpportunityFact execution,
                                 boolean conceptionAccepted,
                                 boolean executionRecomputedLiveStamina)
        {
            this.decision = decision;
            this.execution = execution;
            this.conceptionAccepted = conceptionAccepted;
            this.executionRecomputedLiveStamina =
                executionRecomputedLiveStamina;
        }

        public OpportunityFact getDecision() { return decision; }
        public OpportunityFact getExecution() { return execution; }
        public boolean isConceptionAccepted() { return conceptionAccepted; }
        public boolean isExecutionRecomputedLiveStamina()
        {
            return executionRecomputedLiveStamina;
        }
    }

    /** Correct boundary: S2 consumes the exact fact captured for S0/S1. */
    public static BoundaryScenario frozenAcceptedConception()
    {
        OpportunityFact fact = boundaryDecision();
        return new BoundaryScenario(fact, fact, true, false);
    }

    /** Control: a rejected conception leaves the same immutable HUNT fact. */
    public static BoundaryScenario rejectedConceptionControl()
    {
        OpportunityFact fact = boundaryDecision();
        return new BoundaryScenario(fact, fact, false, false);
    }

    /** Frozen fault: execution re-derives range from post-debit live stamina. */
    public static BoundaryScenario liveStaminaRecomputeFault()
    {
        OpportunityFact decision = boundaryDecision();
        String emptyDigest = digest("NO_REACHABLE_CANDIDATES");
        String recomputedId = opportunityId(BOUNDARY_ACTOR_ID, BOUNDARY_STEP,
            BOUNDARY_FRAME_ID, RECOMPUTED_RANGE, emptyDigest, 0);
        OpportunityFact execution = new OpportunityFact(BOUNDARY_ACTOR_ID,
            BOUNDARY_STEP, BOUNDARY_FRAME_ID, recomputedId, "Cheetah",
            "FEMALE", true, POST_CONCEPTION_STAMINA, RECOMPUTED_RANGE,
            emptyDigest, 0);
        return new BoundaryScenario(decision, execution, true, true);
    }

    public static OpportunityFact boundaryDecision()
    {
        String candidateDigest = digest(UNIQUE_CANDIDATE);
        String opportunityId = opportunityId(BOUNDARY_ACTOR_ID, BOUNDARY_STEP,
            BOUNDARY_FRAME_ID, FROZEN_RANGE, candidateDigest, 1);
        return new OpportunityFact(BOUNDARY_ACTOR_ID, BOUNDARY_STEP,
            BOUNDARY_FRAME_ID, opportunityId, "Cheetah", "FEMALE", true,
            PRE_CONCEPTION_STAMINA, FROZEN_RANGE, candidateDigest, 1);
    }

    public static Reconciliation reconcile(OpportunityFact decision,
                                           OpportunityFact execution)
    {
        if(decision == null || execution == null) {
            return new Reconciliation(List.of("missing-opportunity-fact"));
        }
        List<String> violations = new ArrayList<>();
        if(decision.getActorId() != execution.getActorId()) {
            violations.add("actor-id");
        }
        if(decision.getStep() != execution.getStep()) violations.add("step");
        if(!decision.getFrameId().equals(execution.getFrameId())) {
            violations.add("frame-id");
        }
        if(!decision.getOpportunityId().equals(execution.getOpportunityId())) {
            violations.add("opportunity-id");
        }
        if(decision.getRange() != execution.getRange()) violations.add("range");
        if(!decision.getCandidateDigest().equals(
               execution.getCandidateDigest())) {
            violations.add("candidate-digest");
        }
        if(decision.getCandidateCount() != execution.getCandidateCount()) {
            violations.add("candidate-count");
        }
        return new Reconciliation(violations);
    }

    /** Requires an exact named difference set; a monolithic hash is forbidden. */
    public static Reconciliation verifyDifferenceLedger(
        V24GateProfile profile, Set<String> observedDifferences)
    {
        if(profile == null || observedDifferences == null) {
            return new Reconciliation(List.of("missing-difference-ledger"));
        }
        Set<String> observed = new LinkedHashSet<>(observedDifferences);
        List<String> violations = new ArrayList<>();
        if(observed.contains("whole-simulation-fingerprint")) {
            violations.add("monolithic-fingerprint-forbidden");
        }
        Set<String> missing = new LinkedHashSet<>(
            profile.getAllowedDifferences());
        missing.removeAll(observed);
        Set<String> extra = new LinkedHashSet<>(observed);
        extra.removeAll(profile.getAllowedDifferences());
        for(String value : missing) violations.add("missing:" + value);
        for(String value : extra) violations.add("extra:" + value);
        return new Reconciliation(violations);
    }

    public static String digest(String value)
    {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            StringBuilder result = new StringBuilder();
            for(byte item : sha.digest(value.getBytes(StandardCharsets.UTF_8))) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        }
        catch(NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static String opportunityId(long actorId, int step,
                                        String frameId, int range,
                                        String candidateDigest, int count)
    {
        return digest(actorId + "|" + step + "|" + frameId + "|" + range +
            "|" + candidateDigest + "|" + count);
    }

    private static String requireText(String value, String label)
    {
        if(value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }
}
