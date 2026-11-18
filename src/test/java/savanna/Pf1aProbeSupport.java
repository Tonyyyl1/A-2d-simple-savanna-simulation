package savanna;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared fixed-budget PF1-aF probe and reconciliation support. */
final class Pf1aProbeSupport
{
    static final long TERRAIN_SEED = 20260629L;
    static final String EXPECTED_SCHEMA_HASH =
        "21391ee94500d3e3cd871ee5fd10e7c3c1017383a513dd8d698d34efddcc7adf";
    static final String FROZEN_2111_100_SEQUENCE_HASH =
        "c2ebddc5ae31c90cece5d9a06208302ebefc0c2d222e489938f59ac95ec3b126";
    static final String FROZEN_1111_1000_SEQUENCE_HASH =
        "67f08dea050fae0eb47ee7e2014100b36d272037e0de83478fe8300659845b1b";
    static final String FROZEN_2111_1000_SEQUENCE_HASH =
        "73d7d2c694174877bd9edc8c40c5fba7023a8a13c05c199cf502a9b5df10be2c";
    static final String FROZEN_1111_REFERENCE_PATH =
        "src/test/fixtures/pf1a-frozen-d2/" +
        "seed-1111-steps-1-1000.sha256";
    static final String FROZEN_2111_REFERENCE_PATH =
        "src/test/fixtures/pf1a-frozen-d2/" +
        "seed-2111-steps-1-1000.sha256";

    private static final Map<Long, Map<Integer, String>>
        FROZEN_D2_CHECKPOINTS = frozenD2Checkpoints();

    private Pf1aProbeSupport()
    {
    }

    static RunTrace run(long ecologySeed, int steps, boolean observePf,
                        boolean emitFacts)
    {
        if((ecologySeed != 1111L && ecologySeed != 2111L) ||
           (steps != 100 && steps != 1000)) {
            throw new IllegalArgumentException(
                "PF1-aF supports only the pre-registered seed/step budgets");
        }
        return runAllowed(ecologySeed, steps, observePf, emitFacts);
    }

    /**
     * FRAME-1 transparency entry point. The frame listener is deliberately
     * read-only; all legacy trace components are still captured by the same
     * frozen PF1-aF support below.
     */
    static RunTrace runFrameTransparency(
        long ecologySeed, int steps, StepFrameLedgerListener frameListener)
    {
        if((ecologySeed != 1111L && ecologySeed != 2111L) || steps != 1000 ||
           frameListener == null) {
            throw new IllegalArgumentException(
                "FRAME transparency requires frozen seed 1111/2111, " +
                "1000 steps, and one listener");
        }
        return runAllowed(ecologySeed, steps, false, false, frameListener);
    }

    /**
     * PRA-0's separately authorized fixed calibration/validation partition.
     * This entry point intentionally cannot address the R3 holdout bank.
     */
    static RunTrace runPra0(long ecologySeed, boolean observePf,
                            boolean emitFacts)
    {
        if(ecologySeed != 1111L && ecologySeed != 2111L &&
           ecologySeed != 3111L && ecologySeed != 4111L &&
           ecologySeed != 5111L) {
            throw new IllegalArgumentException(
                "PRA-0 supports only its frozen calibration/validation seeds");
        }
        return runAllowed(ecologySeed, 1000, observePf, emitFacts);
    }

    private static RunTrace runAllowed(long ecologySeed, int steps,
                                       boolean observePf,
                                       boolean emitFacts)
    {
        return runAllowed(ecologySeed, steps, observePf, emitFacts, null);
    }

    private static RunTrace runAllowed(
        long ecologySeed, int steps, boolean observePf, boolean emitFacts,
        StepFrameLedgerListener frameListener)
    {
        Randomizer.reset();
        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(ecologySeed).terrainSeed(TERRAIN_SEED).build();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        SimulationContext context = simulator.getContext();
        Collector collector = new Collector(context, ecologySeed, observePf,
            emitFacts);
        simulator.addEventListener(collector);
        context.addTransitionObservationListener(collector);
        context.addHuntObservationListener(collector);
        context.addBreedingRandomObservationListener(collector);
        if(frameListener != null) {
            context.addStepFrameLedgerListener(frameListener);
        }
        if(observePf) {
            context.addPf1aFactListener(collector);
        }

        List<String> fingerprints = new ArrayList<>();
        List<String> planDigests = new ArrayList<>();
        List<String> eventDigests = new ArrayList<>();
        List<String> transitionDigests = new ArrayList<>();
        List<String> huntRngDigests = new ArrayList<>();
        List<String> breedingRngDigests = new ArrayList<>();
        List<String> populationDigests = new ArrayList<>();
        List<String> completedViable = new ArrayList<>();

        for(int expectedStep = 1;
            expectedStep <= steps && simulator.getField().isViable();
            expectedStep++) {
            simulator.simulateOneStep();
            if(simulator.getStep() != expectedStep) {
                throw new IllegalStateException(
                    "PF1-aF simulation step stamp mismatch");
            }
            String fingerprint = SimulationFingerprint.capture(simulator);
            String plan = planDigest(context.getLastPositionPlan());
            String events = eventsDigest(context.getCurrentEvents());
            String transitions = collector.drainTransitions(expectedStep);
            String huntRng = collector.drainHuntRng(expectedStep);
            String breedingRng = collector.drainBreedingRng(expectedStep);
            String populations = orderedCounts(simulator.getField());
            boolean viable = simulator.getField().isViable();

            fingerprints.add(fingerprint);
            planDigests.add(plan);
            eventDigests.add(events);
            transitionDigests.add(transitions);
            huntRngDigests.add(huntRng);
            breedingRngDigests.add(breedingRng);
            populationDigests.add(digest(populations));
            completedViable.add(expectedStep + ":" + viable);
            collector.finishStep(expectedStep, context.getLastPositionPlan());
            if(emitFacts) {
                System.out.println("PF1AF_STEP seed=" + ecologySeed +
                    " step=" + expectedStep +
                    " legacyEcologyProjection=" + fingerprint +
                    " positionPlanDigest=" + plan +
                    " eventLedgerDigest=" + events +
                    " transitionLedgerDigest=" + transitions +
                    " huntRandomLedger=" + huntRng +
                    " breedingRandomLedger=" + breedingRng +
                    " populationBySpecies=" + compact(populations) +
                    " completed=" + expectedStep + " viable=" + viable);
            }
        }
        collector.finishRun(simulator.getStep());
        return new RunTrace(ecologySeed, steps, simulator.getStep(),
            simulator.getField().isViable(), fingerprints, planDigests,
            eventDigests, transitionDigests, huntRngDigests,
            breedingRngDigests, populationDigests, completedViable,
            new LinkedHashMap<>(simulator.getField().countLivingBySpecies()),
            collector);
    }

    static void requireExact(String label, List<String> baseline,
                             List<String> observed, int expectedSteps)
    {
        if(baseline.size() != expectedSteps ||
           observed.size() != expectedSteps) {
            throw new IllegalStateException(label +
                " trace did not complete its fixed budget");
        }
        for(int index = 0; index < expectedSteps; index++) {
            if(!baseline.get(index).equals(observed.get(index))) {
                throw new IllegalStateException(label +
                    " first differs at step " + (index + 1));
            }
        }
    }

    static void requireFrozenCheckpoints(RunTrace trace)
    {
        Map<Integer, String> expected =
            FROZEN_D2_CHECKPOINTS.get(Long.valueOf(trace.ecologySeed));
        if(expected == null) {
            throw new IllegalStateException(
                "no frozen D2 checkpoint authority for seed");
        }
        int comparisons = 0;
        for(Map.Entry<Integer, String> entry : expected.entrySet()) {
            int step = entry.getKey().intValue();
            if(step > trace.requestedSteps) continue;
            if(step > trace.fingerprints.size() ||
               !entry.getValue().equals(trace.fingerprints.get(step - 1))) {
                throw new IllegalStateException(
                    "frozen D2 fingerprint differs at step " + step +
                    " for seed " + trace.ecologySeed);
            }
            comparisons++;
        }
        if(comparisons != trace.requestedSteps / 100) {
            throw new IllegalStateException(
                "frozen D2 checkpoint coverage is incomplete");
        }
    }

    static String sequenceHash(List<String> fingerprints)
    {
        StringBuilder canonical = new StringBuilder();
        for(int index = 0; index < fingerprints.size(); index++) {
            canonical.append(index + 1).append(' ')
                .append(fingerprints.get(index)).append('\n');
        }
        return digest(canonical.toString());
    }

    /**
     * Loads an already-frozen 1..1000 D2 reference before a fact run starts.
     * A missing or partial reference fails before simulator construction and
     * therefore cannot consume the one-shot ecology budget.
     */
    static List<String> loadFrozenReference(long ecologySeed)
    {
        String rawPath;
        if(ecologySeed == 1111L) {
            rawPath = FROZEN_1111_REFERENCE_PATH;
        }
        else if(ecologySeed == 2111L) {
            rawPath = FROZEN_2111_REFERENCE_PATH;
        }
        else {
            throw new IllegalArgumentException(
                "no pre-registered PF1-aF frozen reference for seed");
        }
        Path path = Path.of(rawPath);
        if(!Files.isRegularFile(path)) {
            throw new IllegalStateException(
                "missing complete frozen D2 per-step reference; refusing " +
                "to consume PF1-aF fact budget: " + path);
        }
        List<String> result = new ArrayList<>(
            Collections.nCopies(1000, null));
        try {
            for(String raw : Files.readAllLines(path,
                                                StandardCharsets.UTF_8)) {
                String line = raw.trim();
                int step = -1;
                String fingerprint = null;
                if(line.startsWith("A1A_R2_FINGERPRINT ")) {
                    String[] tokens = line.split("\\s+");
                    for(String token : tokens) {
                        if(token.startsWith("step=")) {
                            step = Integer.parseInt(token.substring(5));
                        }
                        else if(token.startsWith("value=")) {
                            fingerprint = token.substring(6);
                        }
                    }
                }
                else {
                    String[] tokens = line.split("\\s+");
                    if(tokens.length == 2 && tokens[0].matches("[0-9]+")) {
                        step = Integer.parseInt(tokens[0]);
                        fingerprint = tokens[1];
                    }
                }
                if(step < 0 && fingerprint == null) continue;
                if(step < 1 || step > 1000 || fingerprint == null ||
                   !fingerprint.matches("[0-9a-f]{64}") ||
                   result.get(step - 1) != null) {
                    throw new IllegalStateException(
                        "invalid or duplicate frozen D2 reference row in " +
                        path + ": " + line);
                }
                result.set(step - 1, fingerprint);
            }
        }
        catch(IOException exception) {
            throw new IllegalStateException(
                "unable to read frozen D2 reference: " + path, exception);
        }
        if(result.contains(null)) {
            throw new IllegalStateException(
                "frozen D2 reference is not the exact 1..1000 sequence: " +
                path);
        }
        String expectedFinal = FROZEN_D2_CHECKPOINTS
            .get(Long.valueOf(ecologySeed)).get(Integer.valueOf(1000));
        if(!expectedFinal.equals(result.get(999))) {
            throw new IllegalStateException(
                "frozen D2 reference has the wrong final identity: " + path);
        }
        String expectedSequenceHash = ecologySeed == 1111L ?
            FROZEN_1111_1000_SEQUENCE_HASH :
            FROZEN_2111_1000_SEQUENCE_HASH;
        if(!expectedSequenceHash.equals(sequenceHash(result))) {
            throw new IllegalStateException(
                "frozen D2 reference has the wrong sequence hash for seed " +
                ecologySeed);
        }
        return Collections.unmodifiableList(result);
    }

    static String digest(String value)
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

    static String planDigest(PositionPlan plan)
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

    static String eventsDigest(List<SimulationEvent> events)
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

    private static String orderedCounts(Field field)
    {
        Map<String, Integer> counts = field.countLivingBySpecies();
        StringBuilder result = new StringBuilder("{");
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            if(result.length() > 1) result.append(',');
            String species = factory.getProfile().getName();
            result.append(species).append('=')
                .append(counts.getOrDefault(species, Integer.valueOf(0)));
        }
        return result.append('}').toString();
    }

    private static Map<Long, Map<Integer, String>> frozenD2Checkpoints()
    {
        Map<Long, Map<Integer, String>> result = new LinkedHashMap<>();
        Map<Integer, String> seed1111 = new LinkedHashMap<>();
        seed1111.put(100,
            "3137a81d1127b76b817eb1a4e5aa16a820cbff48f101a4a04cc3f27a27d3ef3c");
        seed1111.put(200,
            "04b03bb740ef9aa946aec3652131963a3c828356ff8f3e2ae693b9411e211828");
        seed1111.put(300,
            "a6370a8adc469000f6a3553c75d22314dc03675f2a7de12e4c26279d3ce83122");
        seed1111.put(400,
            "f4c5c6582b9af53a4afb01362c4e7b6f5cb54a73eb0807cc85a090ddd6e0d6d0");
        seed1111.put(500,
            "a4e2c851904baaf7132592b731f893e71079370fa2253f3e8ad9766227a3e661");
        seed1111.put(600,
            "ad84e267fa7c85c925ea7998d65b06ff462d4d83131741fa281247c2d3eb5590");
        seed1111.put(700,
            "c64d2a12584b2b8b1ef0e854562200a90948b2ca37c275313becbd51559f01d8");
        seed1111.put(800,
            "99cb41f9919dc9eca9d9c95a500838347873b1969cd262c2383b2094da4640c3");
        seed1111.put(900,
            "fbc225b4cfc55481cd3cb5aaf335e62d3241a408c04affa9dc5b04732da39bbf");
        seed1111.put(1000,
            "7c02be75fda85ced6b698502276d7543aac89f066fc9161a0d7a5032ee3b98a4");
        result.put(Long.valueOf(1111L), seed1111);

        Map<Integer, String> seed2111 = new LinkedHashMap<>();
        seed2111.put(100,
            "eead9985e34c2c78063c20e5ff725fae7c9793d1652a53dc7df51c086a61d09a");
        seed2111.put(200,
            "6951091e44a2a45fffe13f62543f5b10f1f27bc25c237be1b15659121be8880f");
        seed2111.put(300,
            "4c6883f4fb64cd53716f73e0886b132c579a3df1c5b1d0b6cc9d9d66ce70d7f4");
        seed2111.put(400,
            "62d4d30083b82714731b7600c6faad3f024c96219d008d8a1d11cbee3d24fe57");
        seed2111.put(500,
            "f5f6efdc7a805cfd472053d56c34eb4ae4947b62e0047df573af12d3f08fb562");
        seed2111.put(600,
            "a0e56a8b08ecf79325c53faccf00c4604c03b2d760dc2b9f8588fcbf698287e9");
        seed2111.put(700,
            "b93e9222b94b8e7f645f79c5b4d81d25074d8bbbd63b44d3bb66eb8887b7e9c1");
        seed2111.put(800,
            "419e8f6988f95815b46858e929d1d9f0eb4892b3da5b9673e71986af6831c773");
        seed2111.put(900,
            "36c68b4d239bb3432bf15321d9806634f4deb876059dbd35f6060b4f89a12552");
        seed2111.put(1000,
            "530715beca898c4f3b391a614fcfdb712e5ab77483d9800a78320237ee574921");
        result.put(Long.valueOf(2111L), seed2111);
        return result;
    }

    private static String location(Location value)
    {
        return value == null ? "-" : value.row() + "," + value.col();
    }

    private static String compact(String value)
    {
        return value.replace(" ", "");
    }

    private static boolean isPredator(String species)
    {
        SpeciesProfile profile = SpeciesRegistry.getProfile(species);
        return profile != null && profile.isPredator();
    }

    static final class RunTrace
    {
        final long ecologySeed;
        final int requestedSteps;
        final int completedSteps;
        final boolean viable;
        final List<String> fingerprints;
        final List<String> planDigests;
        final List<String> eventDigests;
        final List<String> transitionDigests;
        final List<String> huntRngDigests;
        final List<String> breedingRngDigests;
        final List<String> populationDigests;
        final List<String> completedViable;
        final Map<String, Integer> finalCounts;
        final Collector collector;

        RunTrace(long ecologySeed, int requestedSteps, int completedSteps,
                 boolean viable, List<String> fingerprints,
                 List<String> planDigests, List<String> eventDigests,
                 List<String> transitionDigests,
                 List<String> huntRngDigests,
                 List<String> breedingRngDigests,
                 List<String> populationDigests,
                 List<String> completedViable,
                 Map<String, Integer> finalCounts, Collector collector)
        {
            this.ecologySeed = ecologySeed;
            this.requestedSteps = requestedSteps;
            this.completedSteps = completedSteps;
            this.viable = viable;
            this.fingerprints = fingerprints;
            this.planDigests = planDigests;
            this.eventDigests = eventDigests;
            this.transitionDigests = transitionDigests;
            this.huntRngDigests = huntRngDigests;
            this.breedingRngDigests = breedingRngDigests;
            this.populationDigests = populationDigests;
            this.completedViable = completedViable;
            this.finalCounts = finalCounts;
            this.collector = collector;
        }

        void requireComplete()
        {
            if(completedSteps != requestedSteps || !viable ||
               fingerprints.size() != requestedSteps) {
                throw new IllegalStateException(
                    "PF1-aF fixed run did not complete viable");
            }
        }
    }

    static final class Collector
        implements Pf1aFactListener, SimulationEventListener,
                   TransitionObservationListener, HuntObservationListener,
                   BreedingRandomObservationListener
    {
        private final SimulationContext context;
        private final long seed;
        private final boolean expectPf;
        private final boolean emit;
        private final StringBuilder transitions = new StringBuilder();
        private final StringBuilder huntRng = new StringBuilder();
        private final StringBuilder breedingRng = new StringBuilder();
        private final Map<String, BreedingRandomObservation> pendingRng =
            new LinkedHashMap<>();
        private final Map<Long, SimulationEvent> predatorBirthEvents =
            new LinkedHashMap<>();
        private final Map<Long, ChildState> predatorChildren =
            new LinkedHashMap<>();
        private final Set<String> killKeys = new HashSet<>();
        private final Set<String> assessmentKeys = new HashSet<>();
        private final Set<String> commitKeys = new HashSet<>();
        private final Set<String> spawnOrdinalKeys = new HashSet<>();
        private final Set<String> registeredPredators = new LinkedHashSet<>();
        private final Set<String> observedPredators = new LinkedHashSet<>();
        private final Map<String, Integer> provisionalCounts =
            new LinkedHashMap<>();
        private final Map<String, Integer> committedCounts =
            new LinkedHashMap<>();
        private final Map<String, String> assessmentSpecies =
            new LinkedHashMap<>();
        private final Map<Integer, List<String>> assessmentKeysByStep =
            new LinkedHashMap<>();
        private final List<RealizedKillRecord> expectedRetained =
            new ArrayList<>();
        private int latestTransitionStep = -1;
        private int latestHuntStep = -1;
        private int latestBreedingStep = -1;
        private long huntEvents;
        private long predationDeaths;
        private long successfulHuntResults;
        private long killRecords;
        private long breedingRngObservations;
        private long legacyAssessments;
        private long commitFacts;
        private long committedChildren;
        private long birthEvents;
        private long predatorLegacyAssessments;
        private long predatorRows;
        private long spawnOrdinalFacts;
        private long committedSpawnOrdinals;
        private long probabilityDifferences;
        private long breedingRngDifferences;
        private long duplicateFacts;
        private long ordinalDifferences;
        private long retentionDifferences;
        private int maximumRetainedRecords;

        Collector(SimulationContext context, long seed, boolean expectPf,
                  boolean emit)
        {
            this.context = context;
            this.seed = seed;
            this.expectPf = expectPf;
            this.emit = emit;
            for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
                if(factory.getProfile().isPredator()) {
                    registeredPredators.add(
                        factory.getProfile().getName());
                }
            }
        }

        @Override
        public void onEvent(SimulationEvent event)
        {
            if(event.type == SimulationEvent.EventType.HUNT) {
                huntEvents++;
            }
            if((event.type == SimulationEvent.EventType.DEATH ||
                event.type == SimulationEvent.EventType.DISEASE_DEATH) &&
               event.deathCause == DeathCause.PREDATION) {
                predationDeaths++;
            }
            if(event.type == SimulationEvent.EventType.BIRTH) {
                birthEvents++;
                if(isPredator(event.targetSpecies)) {
                    predatorBirthEvents.put(Long.valueOf(event.targetId),
                        event);
                    if(emit) {
                        System.out.println("PF1AF_PREDATOR_BIRTH_EVENT seed=" +
                            seed + " step=" + event.step + " parentId=" +
                            event.actorId + " childId=" + event.targetId +
                            " species=" + event.targetSpecies + " location=" +
                            event.row + "," + event.col);
                    }
                }
            }
            if(event.type == SimulationEvent.EventType.DEATH ||
               event.type == SimulationEvent.EventType.DISEASE_DEATH) {
                ChildState child = predatorChildren.get(
                    Long.valueOf(event.actorId));
                if(child != null && child.deathStep < 0) {
                    child.deathStep = event.step;
                    child.deathCause = event.deathCause;
                }
            }
        }

        @Override
        public void onTransition(TransitionObservation observation)
        {
            latestTransitionStep = observation.getStep();
            transitions.append(observation.getStep()).append(':')
                .append(observation.getAnimalId()).append(':')
                .append(location(observation.getOrigin())).append(':')
                .append(location(observation.getRequested())).append(':')
                .append(location(observation.getResolved())).append(':')
                .append(observation.getStatus()).append(':')
                .append(observation.usedSearchFallback()).append('|');
        }

        @Override
        public void onHuntDecision(HuntDecisionObservation observation)
        {
            ChildState child = predatorChildren.get(
                Long.valueOf(observation.getActorId()));
            if(child != null && child.firstActiveStep < 0 &&
               observation.isActivePhase() &&
               observation.getLifeStage() == LifeStage.ADULT) {
                child.firstActiveStep = observation.getStep();
                child.firstActiveAge = observation.getAge();
                child.firstActiveLocalPrey =
                    observation.getLocalReachableEdibleCount();
                child.firstActivePrimaryAction =
                    observation.getPrimaryAction();
                child.firstActivePrimaryReason =
                    observation.getPrimaryReason();
            }
        }

        @Override
        public void onHuntResult(HuntObservation observation)
        {
            latestHuntStep = observation.getStep();
            HuntResult result = observation.getResult();
            HuntRandomLedger random = result.getRandomLedger();
            huntRng.append(observation.getActorId()).append(':')
                .append(result.getOutcome()).append(':')
                .append(result.getHuntingRange()).append(':')
                .append(result.getLocalReachableEdibleCount()).append(':')
                .append(Double.doubleToLongBits(result.getStaminaCost()))
                .append(':').append(random.hasDiscretionaryDraw())
                .append(':').append(random.isLocationShuffleRequested())
                .append(':').append(random.getLocationShuffleInputSize())
                .append(':').append(random.hasSuccessDraw()).append('|');
            if(result.getOutcome() == HuntOutcome.SUCCESS) {
                successfulHuntResults++;
            }
            ChildState child = predatorChildren.get(
                Long.valueOf(observation.getActorId()));
            if(child != null) {
                if(child.firstActiveStep == observation.getStep() &&
                   child.firstActiveHuntOutcome == null) {
                    child.firstActiveHuntOutcome = result.getOutcome();
                }
                if(result.getOutcome() == HuntOutcome.SUCCESS &&
                   child.firstSuccessStep < 0) {
                    child.firstSuccessStep = observation.getStep();
                }
            }
        }

        @Override
        public void onBreedingRandomObservation(
            BreedingRandomObservation observation)
        {
            latestBreedingStep = observation.getStep();
            BreedingRandomLedger ledger = observation.getRandomLedger();
            breedingRng.append(observation.getParentId()).append(':')
                .append(observation.getSpecies()).append(':')
                .append(ledger.isMateQueryShuffleRequested()).append(':')
                .append(ledger.getMateQueryShuffleInputSize()).append(':')
                .append(ledger.hasLegacyBreedingRoll()).append(':')
                .append(ledger.hasLitterDraw()).append(':')
                .append(ledger.getLitterDrawBound()).append('|');
            breedingRngObservations++;
            if(expectPf) {
                String key = key(observation.getStep(),
                    observation.getParentId());
                if(pendingRng.put(key, observation) != null) {
                    duplicateFacts++;
                }
            }
        }

        @Override
        public void onRealizedKill(RealizedKillRecord record)
        {
            killRecords++;
            expectedRetained.add(record);
            if(!killKeys.add(record.uniqueKey())) duplicateFacts++;
            ChildState child = predatorChildren.get(
                Long.valueOf(record.getPredatorId()));
            if(child != null && child.firstSuccessStep < 0) {
                child.firstSuccessStep = record.getStep();
            }
            if(emit) {
                System.out.println("PF1AF_KILL seed=" + seed +
                    " step=" + record.getStep() + " predatorId=" +
                    record.getPredatorId() + " preyId=" +
                    record.getPreyId() + " predatorSpecies=" +
                    record.getPredatorSpecies() + " preySpecies=" +
                    record.getPreySpecies() + " offeredFood=" +
                    record.getOfferedFood() + " acceptedFood=" +
                    record.getAcceptedFood());
            }
        }

        @Override
        public void onLegacyBreeding(
            LegacyBreedingObservation observation)
        {
            legacyAssessments++;
            String factKey = key(observation.getStep(),
                observation.getParentId());
            if(!assessmentKeys.add(factKey)) duplicateFacts++;
            LegacyBreedingAssessment assessment =
                observation.getAssessment();
            if(provisionalCounts.put(factKey,
                   Integer.valueOf(assessment.getProvisionalClaimCount())) !=
               null ||
               assessmentSpecies.put(factKey, observation.getSpecies()) !=
               null) {
                duplicateFacts++;
            }
            assessmentKeysByStep.computeIfAbsent(
                Integer.valueOf(observation.getStep()),
                ignored -> new ArrayList<>()).add(factKey);
            double product = assessment.getBaseProbability();
            product *= assessment.getInfectionModifier();
            product *= assessment.getSurvivalModifier();
            product *= assessment.getStaminaModifier();
            product *= assessment.getWeatherModifier();
            product *= assessment.getFoodModifier();
            product *= assessment.getLowPopulationModifier();
            product *= assessment.getPopulationBalanceModifier();
            product *= assessment.getCrowdingModifier();
            if(Double.doubleToLongBits(product) != Double.doubleToLongBits(
                assessment.getFinalProbability())) {
                probabilityDifferences++;
            }
            BreedingRandomObservation random = pendingRng.remove(factKey);
            if(random == null ||
               !sameLedger(random.getRandomLedger(),
                   assessment.getRandomLedger())) {
                breedingRngDifferences++;
            }
            if(isPredator(observation.getSpecies())) {
                predatorLegacyAssessments++;
                observedPredators.add(observation.getSpecies());
            }
        }

        @Override
        public void onBreedingCommit(BreedingCommitFact commit)
        {
            commitFacts++;
            committedChildren += commit.getCommittedBirthCount();
            String factKey = key(commit.getStep(), commit.getParentId());
            if(!commitKeys.add(factKey) ||
               !assessmentKeys.contains(factKey)) {
                duplicateFacts++;
            }
            if(committedCounts.put(factKey,
                   Integer.valueOf(commit.getCommittedBirthCount())) != null) {
                duplicateFacts++;
            }
            if(!isPredator(commit.getSpecies())) return;
            if(emit) {
                StringBuilder line = new StringBuilder(
                    "PF1AF_PREDATOR_COMMIT seed=").append(seed)
                    .append(" step=").append(commit.getStep())
                    .append(" parentId=").append(commit.getParentId())
                    .append(" species=").append(commit.getSpecies())
                    .append(" committedBirthCount=")
                    .append(commit.getCommittedBirthCount());
                physiology(line, "parentPre", commit.getParentPreBirth());
                physiology(line, "parentPost", commit.getParentPostBirth());
                System.out.println(line);
            }
            for(NewbornReserveFact newborn : commit.getNewborns()) {
                SimulationEvent birth = predatorBirthEvents.get(
                    Long.valueOf(newborn.getChildId()));
                if(birth == null || birth.step != commit.getStep() ||
                   birth.actorId != commit.getParentId()) {
                    duplicateFacts++;
                }
                ChildState state = new ChildState(commit, newborn);
                if(predatorChildren.put(Long.valueOf(newborn.getChildId()),
                    state) != null) {
                    duplicateFacts++;
                }
                if(emit) {
                    StringBuilder line = new StringBuilder(
                        "PF1AF_NEWBORN seed=").append(seed)
                        .append(" step=").append(commit.getStep())
                        .append(" parentId=").append(commit.getParentId())
                        .append(" childId=").append(newborn.getChildId())
                        .append(" litterOrdinal=")
                        .append(newborn.getLitterOrdinal())
                        .append(" species=").append(newborn.getSpecies())
                        .append(" location=")
                        .append(location(newborn.getLocation()));
                    physiology(line, "reserve", newborn.getReserve());
                    System.out.println(line);
                }
            }
        }

        @Override
        public void onPredatorFemaleAssessment(
            PredatorFemaleAssessmentFact row)
        {
            predatorRows++;
            if(!registeredPredators.contains(row.getSpecies())) {
                duplicateFacts++;
            }
            if(emit) emitPredatorFemale(row);
        }

        String drainTransitions(int step)
        {
            if(latestTransitionStep > step) {
                throw new IllegalStateException(
                    "transition ledger advanced beyond simulation");
            }
            String result = digest(transitions.toString());
            transitions.setLength(0);
            return result;
        }

        String drainHuntRng(int step)
        {
            if(latestHuntStep > step) {
                throw new IllegalStateException(
                    "HUNT RNG ledger advanced beyond simulation");
            }
            String result = digest(huntRng.toString());
            huntRng.setLength(0);
            return result;
        }

        String drainBreedingRng(int step)
        {
            if(latestBreedingStep > step) {
                throw new IllegalStateException(
                    "breeding RNG ledger advanced beyond simulation");
            }
            String result = digest(breedingRng.toString());
            breedingRng.setLength(0);
            return result;
        }

        void finishStep(int step, PositionPlan plan)
        {
            if(!expectPf) return;
            reconcileSpawnOrdinals(step, plan);
            expectedRetained.removeIf(
                record -> record.getStep() < step -
                    RealizedKillLedger.MAX_RETENTION_STEPS);
            int actual = context.getRealizedKillLedger()
                .getRetainedRecordCount();
            maximumRetainedRecords = Math.max(maximumRetainedRecords, actual);
            if(actual != expectedRetained.size()) {
                retentionDifferences++;
            }
        }

        void finishRun(int completedStep)
        {
            if(expectPf) validateFacts();
            if(emit) {
                for(ChildState child : predatorChildren.values()) {
                    emitChild(child, completedStep);
                }
                System.out.println("PF1AF_HUNT_ATOMICITY seed=" + seed +
                    " huntEvents=" + huntEvents +
                    " predationDeaths=" + predationDeaths +
                    " successfulResults=" + successfulHuntResults +
                    " realizedKillRecords=" + killRecords +
                    " differences=" + atomicityDifferences());
                System.out.println(
                    "PF1AF_KILL_LEDGER_RECONCILIATION seed=" + seed +
                    " records=" + killRecords + " uniqueKeys=" +
                    killKeys.size() + " retentionDifferences=" +
                    retentionDifferences + " maximumRetainedRecords=" +
                    maximumRetainedRecords + " retentionSteps=" +
                    RealizedKillLedger.MAX_RETENTION_STEPS);
                System.out.println(
                    "PF1AF_BREEDING_RNG_LEDGER_DIFF seed=" + seed +
                    " observations=" + breedingRngObservations +
                    " assessments=" + legacyAssessments +
                    " differences=" + breedingRngDifferences);
                System.out.println(
                    "PF1AF_LEGACY_PROBABILITY_DIFF seed=" + seed +
                    " assessments=" + legacyAssessments +
                    " differences=" + probabilityDifferences);
                System.out.println(
                    "PF1AF_BIRTH_JOIN_RECONCILIATION seed=" + seed +
                    " birthEvents=" + birthEvents +
                    " committedChildren=" + committedChildren +
                    " assessments=" + legacyAssessments +
                    " commits=" + commitFacts +
                    " predatorChildren=" + predatorChildren.size() +
                    " provisionalOrdinals=" + spawnOrdinalFacts +
                    " committedOrdinals=" + committedSpawnOrdinals +
                    " ordinalDifferences=" + ordinalDifferences +
                    " differences=" + birthJoinDifferences());
                System.out.println(
                    "PF1AF_SCHEMA_AND_RETENTION seed=" + seed +
                    " schemaVersion=" + Pf1aFactSchema.VERSION +
                    " schemaHash=" + Pf1aFactSchema.sha256() +
                    " uniqueFields=" + Pf1aFactSchema.hasUniqueFields() +
                    " registeredPredators=" +
                    compact(registeredPredators.toString()) +
                    " observedPredators=" +
                    compact(observedPredators.toString()) +
                    " duplicateFacts=" + duplicateFacts +
                    " pendingRng=" + pendingRng.size());
            }
        }

        private void validateFacts()
        {
            if(!EXPECTED_SCHEMA_HASH.equals(Pf1aFactSchema.sha256()) ||
               !Pf1aFactSchema.hasUniqueFields() ||
               atomicityDifferences() != 0L ||
               birthJoinDifferences() != 0L ||
               probabilityDifferences != 0L ||
               breedingRngDifferences != 0L ||
               ordinalDifferences != 0L ||
               retentionDifferences != 0L ||
               duplicateFacts != 0L || !pendingRng.isEmpty() ||
               !observedPredators.equals(registeredPredators) ||
               predatorRows != predatorLegacyAssessments) {
                throw new IllegalStateException(
                    "PF1-aF raw fact reconciliation failed");
            }
        }

        private void reconcileSpawnOrdinals(int step, PositionPlan plan)
        {
            if(plan == null) {
                ordinalDifferences++;
                return;
            }
            Map<String, Integer> observed = new LinkedHashMap<>();
            Map<String, Integer> committed = new LinkedHashMap<>();
            for(PositionResolution resolution :
                plan.getResolutions().values()) {
                PositionActorKey actor = resolution.getActor();
                if(!actor.isSpawn()) continue;
                String factKey = key(step, actor.getParentId());
                Integer expected = provisionalCounts.get(factKey);
                String species = assessmentSpecies.get(factKey);
                String ordinalKey = factKey + ":" +
                    actor.getLitterOrdinal();
                if(expected == null || species == null ||
                   actor.getLitterOrdinal() >= expected.intValue() ||
                   !spawnOrdinalKeys.add(ordinalKey)) {
                    ordinalDifferences++;
                }
                observed.put(factKey, Integer.valueOf(
                    observed.getOrDefault(factKey, Integer.valueOf(0))
                        .intValue() + 1));
                spawnOrdinalFacts++;
                if(resolution.isCommitted()) {
                    committed.put(factKey, Integer.valueOf(
                        committed.getOrDefault(factKey, Integer.valueOf(0))
                            .intValue() + 1));
                    committedSpawnOrdinals++;
                }
                if(emit && species != null && isPredator(species)) {
                    System.out.println(
                        "PF1AF_SPAWN_ORDINAL seed=" + seed +
                        " step=" + step + " parentId=" +
                        actor.getParentId() + " species=" + species +
                        " litterOrdinal=" + actor.getLitterOrdinal() +
                        " requested=" + location(resolution.getRequested()) +
                        " resolved=" + location(resolution.getDestination()) +
                        " candidateSource=" +
                        resolution.getCandidateSource() +
                        " candidateIndex=" +
                        resolution.getCandidateIndex() +
                        " disposition=" + resolution.getDisposition() +
                        " reason=" + resolution.getReason());
                }
            }
            List<String> stepKeys = assessmentKeysByStep.get(
                Integer.valueOf(step));
            if(stepKeys == null) return;
            for(String factKey : stepKeys) {
                int expected = provisionalCounts.get(factKey).intValue();
                int actual = observed.getOrDefault(factKey,
                    Integer.valueOf(0)).intValue();
                Integer expectedCommitted = committedCounts.get(factKey);
                int actualCommitted = committed.getOrDefault(factKey,
                    Integer.valueOf(0)).intValue();
                if(expected != actual || expectedCommitted == null ||
                   expectedCommitted.intValue() != actualCommitted) {
                    ordinalDifferences++;
                }
            }
        }

        private long atomicityDifferences()
        {
            long minimum = Math.min(Math.min(huntEvents, predationDeaths),
                Math.min(successfulHuntResults, killRecords));
            long maximum = Math.max(Math.max(huntEvents, predationDeaths),
                Math.max(successfulHuntResults, killRecords));
            return maximum - minimum;
        }

        private long birthJoinDifferences()
        {
            long difference = Math.abs(birthEvents - committedChildren);
            difference += Math.abs(legacyAssessments - commitFacts);
            difference += Math.abs(predatorBirthEvents.size() -
                predatorChildren.size());
            return difference;
        }

        private void emitPredatorFemale(PredatorFemaleAssessmentFact row)
        {
            LegacyBreedingObservation breeding = row.getBreeding();
            LegacyBreedingAssessment assessment = breeding.getAssessment();
            BreedingRandomLedger random = assessment.getRandomLedger();
            ReachableResourceFact resource = row.getLocalResource();
            StepStartLocalCapacityFact capacity = row.getLocalCapacity();
            RealizedKillWindowFact kill30 = row.getKill30();
            RealizedKillWindowFact kill60 = row.getKill60();
            StringBuilder line = new StringBuilder(
                "PF1AF_FEMALE seed=").append(seed)
                .append(" step=").append(row.getStep())
                .append(" parentId=").append(row.getParentId())
                .append(" species=").append(row.getSpecies())
                .append(" location=").append(location(row.getLocation()));
            physiology(line, "decision", row.getDecisionPhysiology());
            line.append(" effectiveHuntingRange=")
                .append(row.getEffectiveHuntingRange())
                .append(" reachablePreyCount=")
                .append(resource.getReachableEdibleCount())
                .append(" reachableOfferedFood=")
                .append(resource.getReachableOfferedFood())
                .append(" nearestPreyDistance=")
                .append(resource.getNearestEdibleDistance())
                .append(" resourcePassableVisited=")
                .append(resource.getPassableVisitedCount())
                .append(" capacityRange=")
                .append(capacity.getBreedingRange())
                .append(" capacityPassable=")
                .append(capacity.getPassableCellCount())
                .append(" capacityLiving=")
                .append(capacity.getLivingOccupantCount())
                .append(" capacityConspecific=")
                .append(capacity.getConspecificCount())
                .append(" capacityPredator=")
                .append(capacity.getPredatorCount())
                .append(" capacityEdiblePrey=")
                .append(capacity.getEdiblePreyCount())
                .append(" decisionCandidateCount=")
                .append(breeding.getCandidates()
                    .getActualDecisionCandidateCount())
                .append(" decisionCandidateLocations=")
                .append(orderedLocations(
                    breeding.getCandidates().getOrderedLocations()))
                .append(" decisionCandidateOrderDigest=")
                .append(breeding.getCandidates().getOrderDigest());
            killWindow(line, "kill30", kill30);
            killWindow(line, "kill60", kill60);
            line.append(" canBreed=").append(assessment.canBreed())
                .append(" waterSuppressed=")
                .append(assessment.isWaterSuppressed())
                .append(" mateQueryPerformed=")
                .append(assessment.isMateQueryPerformed())
                .append(" mateEligible=")
                .append(assessment.isMateEligible())
                .append(" baseProbability=")
                .append(assessment.getBaseProbability())
                .append(" infectionModifier=")
                .append(assessment.getInfectionModifier())
                .append(" survivalModifier=")
                .append(assessment.getSurvivalModifier())
                .append(" staminaModifier=")
                .append(assessment.getStaminaModifier())
                .append(" weatherModifier=")
                .append(assessment.getWeatherModifier())
                .append(" foodModifier=")
                .append(assessment.getFoodModifier())
                .append(" lowPopulationModifier=")
                .append(assessment.getLowPopulationModifier())
                .append(" populationBalanceModifier=")
                .append(assessment.getPopulationBalanceModifier())
                .append(" crowdingModifier=")
                .append(assessment.getCrowdingModifier())
                .append(" finalProbability=")
                .append(assessment.getFinalProbability())
                .append(" probabilityRollPerformed=")
                .append(assessment.isProbabilityRollPerformed())
                .append(" probabilityRollAccepted=")
                .append(assessment.isProbabilityRollAccepted())
                .append(" litterDrawPerformed=")
                .append(assessment.isLitterDrawPerformed())
                .append(" litterDrawBound=")
                .append(assessment.getLitterDrawBound())
                .append(" provisionalClaimCount=")
                .append(assessment.getProvisionalClaimCount())
                .append(" rngMateQueryShuffle=")
                .append(random.isMateQueryShuffleRequested())
                .append(" rngMateQueryInputSize=")
                .append(random.getMateQueryShuffleInputSize())
                .append(" rngLegacyBreedingRoll=")
                .append(random.hasLegacyBreedingRoll())
                .append(" rngLitterDraw=")
                .append(random.hasLitterDraw())
                .append(" rngLitterDrawBound=")
                .append(random.getLitterDrawBound())
                .append(" committedBirthCount=")
                .append(row.getCommit().getCommittedBirthCount());
            physiology(line, "parentPre",
                row.getCommit().getParentPreBirth());
            physiology(line, "parentPost",
                row.getCommit().getParentPostBirth());
            System.out.println(line);
        }

        private void emitChild(ChildState child, int completedStep)
        {
            String outcome = child.firstActiveHuntOutcome == null
                ? "NOT_ATTEMPTED" : child.firstActiveHuntOutcome.name();
            String status;
            if(child.firstSuccessStep >= 0) {
                status = "SUCCESS";
            }
            else if(child.deathStep >= 0) {
                status = "DIED_NO_SUCCESS";
            }
            else {
                status = "RIGHT_CENSORED";
            }
            StringBuilder line = new StringBuilder(
                "PF1AF_CHILD seed=").append(seed)
                .append(" parentId=").append(child.parentId)
                .append(" birthStep=").append(child.birthStep)
                .append(" childId=").append(child.childId)
                .append(" litterOrdinal=").append(child.litterOrdinal)
                .append(" species=").append(child.species)
                .append(" birthLocation=").append(child.birthLocation);
            physiology(line, "reserve", child.reserve);
            line.append(" firstActiveStep=").append(child.firstActiveStep)
                .append(" firstActiveAge=").append(child.firstActiveAge)
                .append(" firstActiveLocalPrey=")
                .append(child.firstActiveLocalPrey)
                .append(" firstActivePrimaryAction=")
                .append(child.firstActivePrimaryAction == null ? "NONE" :
                    child.firstActivePrimaryAction)
                .append(" firstActivePrimaryReason=")
                .append(child.firstActivePrimaryReason == null ? "NONE" :
                    child.firstActivePrimaryReason)
                .append(" firstActiveHuntOutcome=").append(outcome)
                .append(" firstSuccessStep=")
                .append(child.firstSuccessStep)
                .append(" deathStep=").append(child.deathStep)
                .append(" deathCause=").append(child.deathCause)
                .append(" completedStep=").append(completedStep)
                .append(" outcome=").append(status);
            System.out.println(line);
        }

        private static boolean sameLedger(BreedingRandomLedger first,
                                          BreedingRandomLedger second)
        {
            return first.isMateQueryShuffleRequested() ==
                       second.isMateQueryShuffleRequested() &&
                   first.getMateQueryShuffleInputSize() ==
                       second.getMateQueryShuffleInputSize() &&
                   first.hasLegacyBreedingRoll() ==
                       second.hasLegacyBreedingRoll() &&
                   first.hasLitterDraw() == second.hasLitterDraw() &&
                   first.getLitterDrawBound() ==
                       second.getLitterDrawBound();
        }

        private static void physiology(StringBuilder line, String prefix,
                                       AnimalPhysiologyFact fact)
        {
            line.append(' ').append(prefix).append("Age=")
                .append(fact.getAge())
                .append(' ').append(prefix).append("Food=")
                .append(fact.getFood())
                .append(' ').append(prefix).append("Hydration=")
                .append(fact.getHydration())
                .append(' ').append(prefix).append("Stamina=")
                .append(fact.getStamina())
                .append(' ').append(prefix).append("SurvivalPressure=")
                .append(fact.getSurvivalPressure());
        }

        private static void killWindow(StringBuilder line, String prefix,
                                       RealizedKillWindowFact fact)
        {
            line.append(' ').append(prefix).append("Count=")
                .append(fact.getCount())
                .append(' ').append(prefix).append("LastStep=")
                .append(fact.getLastKillStep())
                .append(' ').append(prefix).append("OfferedFood=")
                .append(fact.getOfferedFood())
                .append(' ').append(prefix).append("AcceptedFood=")
                .append(fact.getAcceptedFood())
                .append(' ').append(prefix).append("LeftCensored=")
                .append(fact.isLeftCensored());
        }

        private static String key(int step, long parentId)
        {
            return step + ":" + parentId;
        }

        private static String orderedLocations(List<Location> locations)
        {
            StringBuilder result = new StringBuilder();
            for(int index = 0; index < locations.size(); index++) {
                result.append(index).append('@')
                    .append(location(locations.get(index))).append('|');
            }
            return result.toString();
        }
    }

    private static final class ChildState
    {
        private final int birthStep;
        private final long parentId;
        private final long childId;
        private final int litterOrdinal;
        private final String species;
        private final String birthLocation;
        private final AnimalPhysiologyFact reserve;
        private int firstActiveStep = -1;
        private int firstActiveAge = -1;
        private int firstActiveLocalPrey = -1;
        private ActionType firstActivePrimaryAction;
        private BehaviorReason firstActivePrimaryReason;
        private HuntOutcome firstActiveHuntOutcome;
        private int firstSuccessStep = -1;
        private int deathStep = -1;
        private DeathCause deathCause = DeathCause.NONE;

        ChildState(BreedingCommitFact commit, NewbornReserveFact newborn)
        {
            birthStep = commit.getStep();
            parentId = commit.getParentId();
            childId = newborn.getChildId();
            litterOrdinal = newborn.getLitterOrdinal();
            species = newborn.getSpecies();
            birthLocation = location(newborn.getLocation());
            reserve = newborn.getReserve();
        }
    }
}
