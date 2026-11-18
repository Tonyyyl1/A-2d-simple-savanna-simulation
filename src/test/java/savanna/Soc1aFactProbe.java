package savanna;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** The single pre-registered SOC1-aF seed-2111 x100 observer OFF/ON run. */
public final class Soc1aFactProbe
{
    private static final int STEPS = 100;
    private static final long ECOLOGY_SEED = 2111L;
    private static final long TERRAIN_SEED = 20260629L;
    private static final String FROZEN_D2_FINAL =
        "eead9985e34c2c78063c20e5ff725fae7c9793d1652a53dc7df51c086a61d09a";
    private static final String EXPECTED_SCHEMA_HASH =
        "7b9ca4486b1220d2c8ce313998101262027ed723334941482ca159343c83043f";

    private Soc1aFactProbe()
    {
    }

    public static void main(String[] args)
    {
        if(args.length != 0) {
            throw new IllegalArgumentException(
                "SOC1-aF fact budget is fixed; this probe accepts no arguments");
        }
        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(ECOLOGY_SEED).terrainSeed(TERRAIN_SEED).build();
        RunTrace baseline = run(config, false);
        RunTrace observed = run(config, true);

        requireExact("fingerprint", baseline.fingerprints,
            observed.fingerprints);
        requireExact("position plan", baseline.planDigests,
            observed.planDigests);
        requireExact("events", baseline.eventDigests,
            observed.eventDigests);
        requireExact("transitions", baseline.transitionDigests,
            observed.transitionDigests);
        requireExact("named HUNT RNG", baseline.huntRngDigests,
            observed.huntRngDigests);
        if(baseline.completedSteps != STEPS ||
           observed.completedSteps != STEPS ||
           baseline.captureDelta != STEPS ||
           observed.captureDelta != STEPS) {
            throw new IllegalStateException(
                "SOC1-aF completion/capture count mismatch");
        }
        if(!FROZEN_D2_FINAL.equals(baseline.finalFingerprint) ||
           !FROZEN_D2_FINAL.equals(observed.finalFingerprint)) {
            throw new IllegalStateException(
                "SOC1-aF final fingerprint differs from frozen D2");
        }
        if(!EXPECTED_SCHEMA_HASH.equals(observed.schemaHash) ||
           baseline.registrySize != SpeciesRegistry.getFactories().size() ||
           observed.registrySize != baseline.registrySize) {
            throw new IllegalStateException(
                "SOC1-aF registry/schema identity mismatch");
        }
        if(baseline.capacityFacts != 0L ||
           baseline.queryTotals.occupancyQueries != 0L ||
           observed.capacityFacts == 0L ||
           observed.queryTotals.occupancyQueries != observed.capacityFacts ||
           observed.capacityCacheMismatches != 0L) {
            throw new IllegalStateException(
                "SOC1-aF capacity fact ledger mismatch");
        }
        if(observed.candidates.factCount == 0L ||
           observed.candidates.invalidFacts != 0L) {
            throw new IllegalStateException(
                "SOC1-aF decision-candidate facts are missing or invalid");
        }
        if(observed.queryTotals.maxVisited >
           observed.queryTotals.terrainPassableCells) {
            throw new IllegalStateException(
                "SOC1-aF query work exceeds passable-cell budget");
        }

        for(int index = 0; index < STEPS; index++) {
            System.out.println("SOC1AF_STEP step=" + (index + 1) +
                " fingerprint=" + baseline.fingerprints.get(index) +
                " plan=" + baseline.planDigests.get(index) +
                " events=" + baseline.eventDigests.get(index) +
                " transitions=" + baseline.transitionDigests.get(index) +
                " huntRng=" + baseline.huntRngDigests.get(index) +
                " capacity=" + observed.capacityDigests.get(index) +
                " birthCandidates=" +
                observed.candidateDigests.get(index));
        }
        System.out.println("SOC1AF_CAPTURE_COUNT completed=" + STEPS +
            " baselineDelta=" + baseline.captureDelta +
            " observedDelta=" + observed.captureDelta + " exact=true");
        System.out.println("SOC1AF_REGISTRY_COMPLETENESS profiles=" +
            observed.registrySize + " factories=" +
            SpeciesRegistry.getFactories().size() + " schemaHash=" +
            observed.schemaHash + " exact=true");
        System.out.println("SOC1AF_CAPACITY_FACT_DIFF facts=" +
            observed.capacityFacts + " cacheMismatches=" +
            observed.capacityCacheMismatches + " occupancyQueries=" +
            observed.queryTotals.occupancyQueries + " differences=0");
        System.out.println("SOC1AF_CANDIDATE_ORDER_DIGEST_DIFF facts=" +
            observed.candidates.factCount + " candidateLocations=" +
            observed.candidates.candidateLocations + " invalidFacts=" +
            observed.candidates.invalidFacts + " differences=0");
        System.out.println("SOC1AF_QUERY_BUDGET topologyComputations=" +
            observed.queryTotals.topologyComputations +
            " topologyCacheHits=" +
            observed.queryTotals.topologyCacheHits +
            " passableVisits=" + observed.queryTotals.passableVisits +
            " maxVisitedOneQuery=" + observed.queryTotals.maxVisited +
            " terrainPassableCells=" +
            observed.queryTotals.terrainPassableCells);
        System.out.println("SOC1AF_RNG_LEDGER_DIFF exactSteps=" + STEPS +
            " differences=0");
        System.out.println("SOC1AF_CLAIM_LEDGER_DIFF exactSteps=" + STEPS +
            " differences=0");
        System.out.println("SOC1AF_TRANSPARENCY_2111_100 PASS " +
            "observerOffOnExact=" + STEPS + "/" + STEPS +
            " frozenD2FinalExact=true final=" +
            observed.finalFingerprint);
    }

    private static RunTrace run(SimulationConfig config, boolean observe)
    {
        Randomizer.reset();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        SimulationContext context = simulator.getContext();
        int initialCaptureCount = context.getSpatialEcologyCaptureCount();
        TransitionFacts transitions = new TransitionFacts();
        context.addTransitionObservationListener(transitions);
        HuntRngFacts huntRng = new HuntRngFacts();
        context.addHuntObservationListener(huntRng);
        CandidateFacts candidates = new CandidateFacts();
        if(observe) {
            context.addDecisionBirthCandidateFactListener(candidates);
        }

        List<String> fingerprints = new ArrayList<>();
        List<String> plans = new ArrayList<>();
        List<String> events = new ArrayList<>();
        List<String> transitionDigests = new ArrayList<>();
        List<String> huntRngDigests = new ArrayList<>();
        List<String> capacityDigests = new ArrayList<>();
        List<String> candidateDigests = new ArrayList<>();
        QueryTotals queryTotals = new QueryTotals();
        long capacityFacts = 0L;
        long capacityCacheMismatches = 0L;

        for(int expectedStep = 1;
            expectedStep <= STEPS && simulator.getField().isViable();
            expectedStep++) {
            simulator.simulateOneStep();
            if(simulator.getStep() != expectedStep ||
               context.getSoc1aFactView().getStep() != expectedStep) {
                throw new IllegalStateException(
                    "SOC1-aF step stamp mismatch");
            }
            StringBuilder capacityCanonical = new StringBuilder();
            if(observe) {
                Soc1aFactView factView = context.getSoc1aFactView();
                int expectedCapacityCount = 0;
                for(SpatialAnimalFact animal :
                    context.getSpatialEcologyView().getAnimalFacts()) {
                    StepStartLocalCapacityFact fact =
                        factView.getLocalCapacity(animal.getId());
                    capacityCanonical.append(fact.getActorId()).append(':')
                        .append(fact.getSpeciesKey()).append(':')
                        .append(location(fact.getOrigin())).append(':')
                        .append(fact.getBreedingRange()).append(':')
                        .append(fact.getPassableCellCount()).append(':')
                        .append(fact.getLivingOccupantCount()).append(':')
                        .append(fact.getConspecificCount()).append(':')
                        .append(fact.getPredatorCount()).append(':')
                        .append(fact.getEdiblePreyCount()).append('|');
                    expectedCapacityCount++;
                    capacityFacts++;
                }
                if(factView.getCachedCapacityFactCount() !=
                   expectedCapacityCount) {
                    capacityCacheMismatches++;
                }
            }
            fingerprints.add(SimulationFingerprint.capture(simulator));
            plans.add(planDigest(context.getLastPositionPlan()));
            events.add(eventsDigest(context.getCurrentEvents()));
            transitionDigests.add(transitions.drain());
            huntRngDigests.add(huntRng.drain(expectedStep));
            capacityDigests.add(digest(capacityCanonical.toString()));
            candidateDigests.add(candidates.drain(expectedStep));
            queryTotals.add(context.getSpatialEcologyView().getQueryLedger());
        }

        SpeciesEcologyFactRegistry registry =
            context.getSpeciesEcologyFactRegistry();
        return new RunTrace(fingerprints, plans, events, transitionDigests,
            huntRngDigests, capacityDigests, candidateDigests,
            simulator.getStep(),
            context.getSpatialEcologyCaptureCount() - initialCaptureCount,
            SimulationFingerprint.capture(simulator), registry.size(),
            registry.getSchemaHash(), capacityFacts,
            capacityCacheMismatches, candidates, queryTotals);
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

    private static final class HuntRngFacts
        implements HuntObservationListener
    {
        private final StringBuilder value = new StringBuilder();
        private int currentStep = -1;

        @Override
        public void onHuntResult(HuntObservation observation)
        {
            currentStep = observation.getStep();
            HuntResult result = observation.getResult();
            HuntRandomLedger random = result.getRandomLedger();
            value.append(observation.getActorId()).append(':')
                .append(result.getOutcome()).append(':')
                .append(result.getHuntingRange()).append(':')
                .append(result.getLocalReachableEdibleCount()).append(':')
                .append(Double.doubleToLongBits(result.getStaminaCost()))
                .append(':').append(random.hasDiscretionaryDraw())
                .append(':').append(random.isLocationShuffleRequested())
                .append(':').append(random.getLocationShuffleInputSize())
                .append(':').append(random.hasSuccessDraw()).append('|');
        }

        String drain(int step)
        {
            if(currentStep > step) {
                throw new IllegalStateException(
                    "HUNT RNG ledger advanced beyond simulation");
            }
            String result = digest(value.toString());
            value.setLength(0);
            return result;
        }
    }

    private static final class CandidateFacts
        implements DecisionBirthCandidateFactListener
    {
        private final StringBuilder stepFacts = new StringBuilder();
        private long factCount;
        private long candidateLocations;
        private long invalidFacts;
        private int currentStep = -1;

        @Override
        public void onDecisionBirthCandidates(DecisionBirthCandidateFact fact)
        {
            if(currentStep != -1 && fact.getStep() < currentStep) {
                invalidFacts++;
            }
            currentStep = fact.getStep();
            if(fact.getActualDecisionCandidateCount() !=
               fact.getOrderedLocations().size() ||
               fact.getOrderDigest().length() != 64) {
                invalidFacts++;
            }
            stepFacts.append(fact.getActorId()).append(':')
                .append(fact.getSpeciesKey()).append(':')
                .append(fact.getActualDecisionCandidateCount()).append(':')
                .append(fact.getOrderDigest()).append('|');
            factCount++;
            candidateLocations += fact.getActualDecisionCandidateCount();
        }

        String drain(int step)
        {
            if(currentStep > step) {
                throw new IllegalStateException(
                    "candidate ledger advanced beyond simulation");
            }
            String result = digest(stepFacts.toString());
            stepFacts.setLength(0);
            return result;
        }
    }

    private static final class QueryTotals
    {
        private long occupancyQueries;
        private long topologyComputations;
        private long topologyCacheHits;
        private long passableVisits;
        private int maxVisited;
        private int terrainPassableCells;

        void add(SpatialQueryLedger ledger)
        {
            occupancyQueries += ledger.getOccupancyQueryCount();
            topologyComputations += ledger.getTopologyComputationCount();
            topologyCacheHits += ledger.getTopologyCacheHitCount();
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
        private final List<String> huntRngDigests;
        private final List<String> capacityDigests;
        private final List<String> candidateDigests;
        private final int completedSteps;
        private final int captureDelta;
        private final String finalFingerprint;
        private final int registrySize;
        private final String schemaHash;
        private final long capacityFacts;
        private final long capacityCacheMismatches;
        private final CandidateFacts candidates;
        private final QueryTotals queryTotals;

        RunTrace(List<String> fingerprints, List<String> planDigests,
                 List<String> eventDigests,
                 List<String> transitionDigests,
                 List<String> huntRngDigests,
                 List<String> capacityDigests,
                 List<String> candidateDigests, int completedSteps,
                 int captureDelta, String finalFingerprint,
                 int registrySize, String schemaHash, long capacityFacts,
                 long capacityCacheMismatches, CandidateFacts candidates,
                 QueryTotals queryTotals)
        {
            this.fingerprints = fingerprints;
            this.planDigests = planDigests;
            this.eventDigests = eventDigests;
            this.transitionDigests = transitionDigests;
            this.huntRngDigests = huntRngDigests;
            this.capacityDigests = capacityDigests;
            this.candidateDigests = candidateDigests;
            this.completedSteps = completedSteps;
            this.captureDelta = captureDelta;
            this.finalFingerprint = finalFingerprint;
            this.registrySize = registrySize;
            this.schemaHash = schemaHash;
            this.capacityFacts = capacityFacts;
            this.capacityCacheMismatches = capacityCacheMismatches;
            this.candidates = candidates;
            this.queryTotals = queryTotals;
        }
    }
}
