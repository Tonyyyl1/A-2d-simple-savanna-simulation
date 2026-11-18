package savanna;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * The single pre-registered L1-F fact run: seed 2111 x100 observer OFF/ON.
 *
 * <p>Both arms retain the frozen D2 audit hook. The ON arm additionally reads
 * the immutable spatial view; it cannot create claims or consume RNG.</p>
 */
public final class L1FSpatialEcologyProbe
{
    private static final int STEPS = 100;
    private static final long ECOLOGY_SEED = 2111L;
    private static final long TERRAIN_SEED = 20260629L;
    private static final String FROZEN_D2_FINAL =
        "eead9985e34c2c78063c20e5ff725fae7c9793d1652a53dc7df51c086a61d09a";

    private L1FSpatialEcologyProbe()
    {
    }

    public static void main(String[] args)
    {
        if(args.length != 0) {
            throw new IllegalArgumentException(
                "L1-F fact budget is fixed; this probe accepts no arguments");
        }
        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(ECOLOGY_SEED).terrainSeed(TERRAIN_SEED).build();
        RunTrace baseline = run(config, false);
        RunTrace observed = run(config, true);

        requireExact("fingerprint", baseline.fingerprints,
            observed.fingerprints);
        requireExact("position plan", baseline.planDigests,
            observed.planDigests);
        requireExact("event ledger", baseline.eventDigests,
            observed.eventDigests);
        requireExact("transition ledger", baseline.transitionDigests,
            observed.transitionDigests);
        requireExact("named HUNT RNG ledger", baseline.rngDigests,
            observed.rngDigests);
        if(baseline.completedSteps != STEPS ||
           observed.completedSteps != STEPS) {
            throw new IllegalStateException(
                "L1-F did not complete both fixed 100-step arms");
        }
        if(baseline.captureDelta != STEPS ||
           observed.captureDelta != STEPS) {
            throw new IllegalStateException(
                "L1-F capture count differs from completed steps");
        }
        if(!FROZEN_D2_FINAL.equals(baseline.finalFingerprint) ||
           !FROZEN_D2_FINAL.equals(observed.finalFingerprint)) {
            throw new IllegalStateException(
                "L1-F final fingerprint differs from frozen D2");
        }
        if(observed.audit.opportunityDifferences != 0L ||
           observed.audit.opportunityComparisons == 0L) {
            throw new IllegalStateException(
                "L1-F/D2 comparable opportunity facts differ");
        }
        if(baseline.queryTotals.resourceQueries != 0L ||
           observed.queryTotals.resourceQueries !=
               observed.audit.decisionCount ||
           observed.queryTotals.maxVisited >
               observed.queryTotals.terrainPassableCells) {
            throw new IllegalStateException(
                "L1-F query ledger violates the pre-registered budget");
        }

        for(int index = 0; index < STEPS; index++) {
            System.out.println("L1F_STEP step=" + (index + 1) +
                " fingerprint=" + baseline.fingerprints.get(index) +
                " plan=" + baseline.planDigests.get(index) +
                " events=" + baseline.eventDigests.get(index) +
                " transitions=" + baseline.transitionDigests.get(index) +
                " huntRng=" + baseline.rngDigests.get(index));
        }
        System.out.println("L1F_CAPTURE_COUNT completed=" + STEPS +
            " baselineDelta=" + baseline.captureDelta +
            " observedDelta=" + observed.captureDelta + " exact=true");
        System.out.println("L1F_D2_OPPORTUNITY_DIFF comparable=" +
            observed.audit.opportunityComparisons +
            " skippedAfterStepMutation=" +
            observed.audit.opportunitySkippedAfterMutation +
            " differences=" + observed.audit.opportunityDifferences);
        System.out.println("L1F_QUERY_BUDGET resourceQueries=" +
            observed.queryTotals.resourceQueries +
            " topologyComputations=" +
            observed.queryTotals.topologyComputations +
            " topologyCacheHits=" +
            observed.queryTotals.topologyCacheHits +
            " resultCacheHits=" + observed.queryTotals.resultCacheHits +
            " passableVisits=" + observed.queryTotals.passableVisits +
            " maxVisitedOneQuery=" + observed.queryTotals.maxVisited +
            " terrainPassableCells=" +
            observed.queryTotals.terrainPassableCells);
        System.out.println("L1F_RNG_LEDGER_DIFF exactSteps=" + STEPS +
            " differences=0");
        System.out.println("L1F_CLAIM_LEDGER_DIFF exactSteps=" + STEPS +
            " differences=0");
        System.out.println("L1F_TRANSPARENCY_2111_100 PASS observerOffOnExact=" +
            STEPS + "/" + STEPS + " frozenD2FinalExact=true final=" +
            observed.finalFingerprint);
    }

    private static RunTrace run(SimulationConfig config, boolean querySpatial)
    {
        Randomizer.reset();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        SimulationContext context = simulator.getContext();
        int initialCaptureCount = context.getSpatialEcologyCaptureCount();
        TransitionFacts transitions = new TransitionFacts();
        context.addTransitionObservationListener(transitions);
        AuditListener audit = new AuditListener(context, querySpatial);
        simulator.addEventListener(audit);
        context.addHuntObservationListener(audit);

        List<String> fingerprints = new ArrayList<>();
        List<String> plans = new ArrayList<>();
        List<String> events = new ArrayList<>();
        List<String> transitionDigests = new ArrayList<>();
        List<String> rngDigests = new ArrayList<>();
        QueryTotals queryTotals = new QueryTotals();
        for(int expectedStep = 1;
            expectedStep <= STEPS && simulator.getField().isViable();
            expectedStep++) {
            simulator.simulateOneStep();
            if(simulator.getStep() != expectedStep ||
               context.getSpatialEcologyView().getStep() != expectedStep) {
                throw new IllegalStateException(
                    "L1-F step/capture stamp mismatch");
            }
            fingerprints.add(SimulationFingerprint.capture(simulator));
            plans.add(planDigest(context.getLastPositionPlan()));
            events.add(eventsDigest(context.getCurrentEvents()));
            transitionDigests.add(transitions.drain());
            rngDigests.add(audit.drainStepRng(expectedStep));
            queryTotals.add(context.getSpatialEcologyView().getQueryLedger());
        }
        return new RunTrace(fingerprints, plans, events, transitionDigests,
            rngDigests, simulator.getStep(),
            context.getSpatialEcologyCaptureCount() - initialCaptureCount,
            SimulationFingerprint.capture(simulator), audit, queryTotals);
    }

    private static void requireExact(String name, List<String> baseline,
                                     List<String> observed)
    {
        if(baseline.size() != STEPS || observed.size() != STEPS) {
            throw new IllegalStateException(name + " trace is incomplete");
        }
        for(int index = 0; index < STEPS; index++) {
            if(!baseline.get(index).equals(observed.get(index))) {
                throw new IllegalStateException(name +
                    " first differs at step " + (index + 1));
            }
        }
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
                .append(':').append(event.fromRow).append(':')
                .append(event.fromCol).append(':').append(event.actorId)
                .append(':').append(event.targetId).append(':')
                .append(event.actorSpecies).append(':')
                .append(event.targetSpecies).append(':')
                .append(event.deathCause).append('|');
        }
        return digest(value.toString());
    }

    private static String location(Location value)
    {
        return value == null ? "-" : value.row() + "," + value.col();
    }

    private static String digest(String value)
    {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for(byte item : digest.digest()) {
                result.append(String.format("%02x", item & 0xff));
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
        private final StringBuilder value = new StringBuilder();

        @Override
        public void onTransition(TransitionObservation observation)
        {
            value.append(observation.getStep()).append(':')
                .append(observation.getAnimalId()).append(':')
                .append(location(observation.getOrigin())).append(':')
                .append(location(observation.getRequested())).append(':')
                .append(location(observation.getResolved())).append(':')
                .append(observation.getStatus()).append(':')
                .append(observation.usedSearchFallback()).append('|');
        }

        String drain()
        {
            String result = digest(value.toString());
            value.setLength(0);
            return result;
        }
    }

    private static final class AuditListener
        implements HuntObservationListener, SimulationEventListener
    {
        private final SimulationContext context;
        private final boolean querySpatial;
        private final StringBuilder stepRng = new StringBuilder();
        private int currentStep = -1;
        private boolean deathSeenThisStep;
        private long decisionCount;
        private long opportunityComparisons;
        private long opportunitySkippedAfterMutation;
        private long opportunityDifferences;

        AuditListener(SimulationContext context, boolean querySpatial)
        {
            this.context = context;
            this.querySpatial = querySpatial;
        }

        @Override
        public void onEvent(SimulationEvent event)
        {
            advance(event.step);
            if(event.type == SimulationEvent.EventType.DEATH) {
                deathSeenThisStep = true;
            }
        }

        @Override
        public void onHuntDecision(HuntDecisionObservation observation)
        {
            advance(observation.getStep());
            decisionCount++;
            if(!querySpatial) return;

            SpatialEcologyView view = context.getSpatialEcologyView();
            SpatialAnimalFact actor = view.getAnimalFact(
                observation.getActorId());
            SpeciesProfile profile = SpeciesRegistry.getProfile(
                observation.getActorSpecies());
            if(actor == null || profile == null) {
                opportunityDifferences++;
                return;
            }
            ReachableResourceFact resource = view.getReachableResources(
                actor.getLocation(), observation.getHuntingRange(), profile);
            if(deathSeenThisStep) {
                /*
                 * D2 filters live Animal references after earlier same-step
                 * deaths. L1-F is intentionally immutable step-start state, so
                 * only the pre-mutation prefix has identical comparison time.
                 */
                opportunitySkippedAfterMutation++;
            }
            else {
                opportunityComparisons++;
                if(resource.getReachableEdibleCount() !=
                   observation.getLocalReachableEdibleCount()) {
                    opportunityDifferences++;
                }
            }
        }

        @Override
        public void onHuntResult(HuntObservation observation)
        {
            advance(observation.getStep());
            HuntResult result = observation.getResult();
            HuntRandomLedger random = result.getRandomLedger();
            stepRng.append(observation.getActorId()).append(':')
                .append(result.getOutcome()).append(':')
                .append(result.getHuntingRange()).append(':')
                .append(result.getLocalReachableEdibleCount()).append(':')
                .append(Double.doubleToLongBits(result.getStaminaCost()))
                .append(':').append(random.hasDiscretionaryDraw())
                .append(':').append(random.isLocationShuffleRequested())
                .append(':').append(random.getLocationShuffleInputSize())
                .append(':').append(random.hasSuccessDraw()).append('|');
        }

        String drainStepRng(int step)
        {
            if(currentStep != step) {
                throw new IllegalStateException(
                    "HUNT audit step does not match simulation step");
            }
            String result = digest(stepRng.toString());
            stepRng.setLength(0);
            return result;
        }

        private void advance(int step)
        {
            if(currentStep != step) {
                currentStep = step;
                deathSeenThisStep = false;
            }
        }
    }

    private static final class QueryTotals
    {
        private long resourceQueries;
        private long topologyComputations;
        private long topologyCacheHits;
        private long resultCacheHits;
        private long passableVisits;
        private int maxVisited;
        private int terrainPassableCells;

        void add(SpatialQueryLedger ledger)
        {
            resourceQueries += ledger.getResourceQueryCount();
            topologyComputations += ledger.getTopologyComputationCount();
            topologyCacheHits += ledger.getTopologyCacheHitCount();
            resultCacheHits += ledger.getResultCacheHitCount();
            passableVisits += ledger.getPassableCellVisitCount();
            maxVisited = Math.max(maxVisited,
                ledger.getMaxPassableCellsVisitedByOneComputation());
            terrainPassableCells = Math.max(terrainPassableCells,
                ledger.getTerrainPassableCellCount());
        }
    }

    private static final class RunTrace
    {
        private final List<String> fingerprints;
        private final List<String> planDigests;
        private final List<String> eventDigests;
        private final List<String> transitionDigests;
        private final List<String> rngDigests;
        private final int completedSteps;
        private final int captureDelta;
        private final String finalFingerprint;
        private final AuditListener audit;
        private final QueryTotals queryTotals;

        RunTrace(List<String> fingerprints, List<String> planDigests,
                 List<String> eventDigests,
                 List<String> transitionDigests, List<String> rngDigests,
                 int completedSteps, int captureDelta,
                 String finalFingerprint, AuditListener audit,
                 QueryTotals queryTotals)
        {
            this.fingerprints = fingerprints;
            this.planDigests = planDigests;
            this.eventDigests = eventDigests;
            this.transitionDigests = transitionDigests;
            this.rngDigests = rngDigests;
            this.completedSteps = completedSteps;
            this.captureDelta = captureDelta;
            this.finalFingerprint = finalFingerprint;
            this.audit = audit;
            this.queryTotals = queryTotals;
        }
    }
}
