package savanna;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * D2 evidence probe: compares a frozen-R2 run with and without the read-only
 * HUNT ledger, then emits the observed phase/reason/outcome/RNG accounting.
 */
public final class A1aR2rD2Probe
{
    private A1aR2rD2Probe()
    {
    }

    public static void main(String[] args)
    {
        int steps = argument(args, 0, 300);
        int interval = argument(args, 1, 100);
        long ecologySeed = longArgument(args, 2, 2111L);
        long terrainSeed = longArgument(args, 3, 20260629L);
        if(steps <= 0 || interval <= 0) {
            throw new IllegalArgumentException("steps and interval must be positive");
        }
        SimulationConfig config = SimulationConfig.builder().randomSeed(ecologySeed)
            .terrainSeed(terrainSeed).build();
        RunTrace baseline = run(config, steps, interval, false);
        RunTrace observed = run(config, steps, interval, true);
        if(!baseline.stepDigests.equals(observed.stepDigests)) {
            throw new IllegalStateException("D2 observer changed ecological, plan, " +
                "event, or transition facts");
        }
        if(observed.ledger.getReconciliationFailures() != 0) {
            throw new IllegalStateException("D2 local-opportunity reconciliation failed: " +
                observed.ledger.getReconciliationFailures());
        }
        System.out.println("A1A_R2R_D2_PROBE PASS transparent=true ecologySeed=" +
            ecologySeed + " terrainSeed=" + terrainSeed + " steps=" + steps +
            " completed=" + observed.completedSteps + " final=" +
            observed.finalFingerprint + " huntResults=" +
            observed.ledger.getResultCount() + " wouldSeek=" +
            observed.ledger.getWouldSeekCount());
    }

    private static RunTrace run(SimulationConfig config, int steps, int interval,
                                boolean observe)
    {
        Randomizer.reset();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        TransitionFacts transitions = new TransitionFacts();
        simulator.getContext().addTransitionObservationListener(transitions);
        HuntLedger ledger = new HuntLedger();
        if(observe) {
            simulator.getContext().addHuntObservationListener(ledger);
        }
        List<String> stepDigests = new ArrayList<>();
        for(int index = 1; index <= steps && simulator.getField().isViable(); index++) {
            simulator.simulateOneStep();
            String fingerprint = SimulationFingerprint.capture(simulator);
            stepDigests.add(digest(fingerprint + "|plan=" + planDigest(
                simulator.getContext().getLastPositionPlan()) + "|events=" +
                eventsDigest(simulator.getContext().getCurrentEvents()) +
                "|transitions=" + transitions.drain()));
            if(observe && (index % interval == 0 || index == steps ||
               !simulator.getField().isViable())) {
                System.out.println("A1A_R2R_D2_FINGERPRINT step=" +
                    simulator.getStep() + " value=" + fingerprint);
                System.out.println(ledger.drainWindow(simulator.getStep()));
            }
        }
        return new RunTrace(stepDigests, ledger, simulator.getStep(),
            SimulationFingerprint.capture(simulator));
    }

    private static String planDigest(PositionPlan plan)
    {
        if(plan == null) return "none";
        StringBuilder value = new StringBuilder(plan.getPolicyVersion())
            .append('|').append(plan.getStepSalt());
        for(PositionResolution resolution : plan.getResolutions().values()) {
            value.append('|').append(resolution.getActor().getKind())
                .append(':').append(resolution.getActor().getParentOrAdultId())
                .append(':').append(resolution.getActor().getLitterOrdinal())
                .append(':').append(resolution.getClaimKind())
                .append(':').append(location(resolution.getRequested()))
                .append(':').append(location(resolution.getDestination()))
                .append(':').append(resolution.getCandidateSource())
                .append(':').append(resolution.getCandidateIndex())
                .append(':').append(resolution.getDisposition())
                .append(':').append(resolution.getReason());
        }
        return digest(value.toString());
    }

    private static String eventsDigest(List<SimulationEvent> events)
    {
        StringBuilder value = new StringBuilder();
        for(SimulationEvent event : events) {
            value.append(event.type).append(':').append(event.step)
                .append(':').append(event.row).append(':').append(event.col)
                .append(':').append(event.fromRow).append(':').append(event.fromCol)
                .append(':').append(event.actorId).append(':').append(event.targetId)
                .append(':').append(event.actorSpecies).append(':')
                .append(event.targetSpecies).append(':').append(event.deathCause)
                .append('|');
        }
        return digest(value.toString());
    }

    private static int argument(String[] args, int index, int fallback)
    {
        return args.length > index ? Integer.parseInt(args[index]) : fallback;
    }

    private static long longArgument(String[] args, int index, long fallback)
    {
        return args.length > index ? Long.parseLong(args[index]) : fallback;
    }

    private static String location(Location value)
    {
        return value == null ? "-" : value.row() + "," + value.col();
    }

    private static String key(int step, long actorId)
    {
        return step + ":" + actorId;
    }

    private static void increment(Map<String, Long> values, String key)
    {
        Long current = values.get(key);
        values.put(key, Long.valueOf(current == null ? 1L : current.longValue() + 1L));
    }

    private static String digest(String value)
    {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for(byte valueByte : digest.digest()) {
                result.append(String.format("%02x", valueByte & 0xff));
            }
            return result.toString();
        }
        catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static final class TransitionFacts
        implements TransitionObservationListener
    {
        private final StringBuilder transitions = new StringBuilder();

        public void onTransition(TransitionObservation observation)
        {
            transitions.append(observation.getStep()).append(':')
                .append(observation.getAnimalId()).append(':')
                .append(location(observation.getOrigin())).append(':')
                .append(location(observation.getRequested())).append(':')
                .append(location(observation.getResolved())).append(':')
                .append(observation.getStatus()).append(':')
                .append(observation.usedSearchFallback()).append('|');
        }

        private String drain()
        {
            String result = digest(transitions.toString());
            transitions.setLength(0);
            return result;
        }
    }

    private static final class HuntLedger implements HuntObservationListener
    {
        private final Map<String, HuntDecisionObservation> decisions =
            new LinkedHashMap<>();
        private final Map<String, Long> decisionFacts = new TreeMap<>();
        private final Map<String, Long> outcomeFacts = new TreeMap<>();
        private long resultCount;
        private long wouldSeekCount;
        private long huntEvents;
        private long predationDeaths;
        private long discretionaryDraws;
        private long locationShuffleRequests;
        private long locationShuffleInputs;
        private long successDraws;
        private double staminaCost;
        private long reconciliationFailures;

        public void onHuntDecision(HuntDecisionObservation observation)
        {
            decisions.put(key(observation.getStep(), observation.getActorId()),
                observation);
            increment(decisionFacts, "active=" + observation.isActivePhase() +
                ",food=" + named(observation.getFoodReason()) + ",primary=" +
                named(observation.getPrimaryReason()) + ",water=" +
                observation.isWaterPriority() + ",local=" +
                observation.hasLocalReachableEdible() + ",wouldSeek=" +
                observation.wouldSeekOnLocalMiss());
            if(observation.wouldSeekOnLocalMiss()) wouldSeekCount++;
        }

        public void onHuntResult(HuntObservation observation)
        {
            resultCount++;
            HuntResult result = observation.getResult();
            HuntRandomLedger random = result.getRandomLedger();
            HuntDecisionObservation decision = decisions.get(key(
                observation.getStep(), observation.getActorId()));
            if(decision == null || (result.getOutcome() != HuntOutcome.DECLINED &&
               result.getOutcome() != HuntOutcome.INELIGIBLE &&
               decision.getLocalReachableEdibleCount() !=
                   result.getLocalReachableEdibleCount()) ||
               (result.getOutcome() == HuntOutcome.NO_LOCAL_REACHABLE_PREY &&
                decision.hasLocalReachableEdible())) {
                reconciliationFailures++;
            }
            increment(outcomeFacts, "active=" + observation.isActivePhase() +
                ",food=" + named(observation.getFoodReason()) + ",outcome=" +
                result.getOutcome() + ",prey=" + named(result.getSelectedPreySpecies()));
            staminaCost += result.getStaminaCost();
            if(observation.isHuntEventRecorded()) huntEvents++;
            if(observation.isPredationDeathRecorded()) predationDeaths++;
            if(random.hasDiscretionaryDraw()) discretionaryDraws++;
            if(random.isLocationShuffleRequested()) locationShuffleRequests++;
            locationShuffleInputs += random.getLocationShuffleInputSize();
            if(random.hasSuccessDraw()) successDraws++;
        }

        private String drainWindow(int step)
        {
            String result = "A1A_R2R_D2_HUNT_WINDOW step=" + step +
                " decisions=" + decisionFacts + " outcomes=" + outcomeFacts +
                " staminaCost=" + String.format(java.util.Locale.ROOT, "%.4f",
                    staminaCost) + " huntEvents=" + huntEvents +
                " predationDeaths=" + predationDeaths + " rng={discretion=" +
                discretionaryDraws + ",shuffleRequests=" +
                locationShuffleRequests + ",shuffleInputTotal=" +
                locationShuffleInputs + ",success=" + successDraws +
                "} reconciliationFailures=" + reconciliationFailures;
            decisionFacts.clear();
            outcomeFacts.clear();
            staminaCost = 0.0;
            huntEvents = 0L;
            predationDeaths = 0L;
            discretionaryDraws = 0L;
            locationShuffleRequests = 0L;
            locationShuffleInputs = 0L;
            successDraws = 0L;
            return result;
        }

        private long getResultCount() { return resultCount; }
        private long getWouldSeekCount() { return wouldSeekCount; }
        private long getReconciliationFailures()
        {
            return reconciliationFailures;
        }

        private static String named(Object value)
        {
            return value == null || String.valueOf(value).isEmpty() ? "NONE" :
                String.valueOf(value);
        }
    }

    private static final class RunTrace
    {
        private final List<String> stepDigests;
        private final HuntLedger ledger;
        private final int completedSteps;
        private final String finalFingerprint;

        RunTrace(List<String> stepDigests, HuntLedger ledger,
                 int completedSteps, String finalFingerprint)
        {
            this.stepDigests = stepDigests;
            this.ledger = ledger;
            this.completedSteps = completedSteps;
            this.finalFingerprint = finalFingerprint;
        }
    }
}
