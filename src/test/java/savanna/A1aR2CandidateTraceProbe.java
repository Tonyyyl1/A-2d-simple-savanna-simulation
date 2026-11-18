package savanna;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * R2R diagnostic: proves candidate observation is transparent, then records
 * adjacent-candidate shape and the matching resolved position plan.
 */
public final class A1aR2CandidateTraceProbe
{
    private A1aR2CandidateTraceProbe() {}

    public static void main(String[] args)
    {
        int steps = argument(args, 0, 1000);
        int interval = argument(args, 1, 100);
        long ecologySeed = longArgument(args, 2, 2111L);
        long terrainSeed = longArgument(args, 3, 20260629L);
        int transparencySteps = argument(args, 4, steps);
        if(steps <= 0 || interval <= 0) {
            throw new IllegalArgumentException("steps and interval must be positive");
        }
        if(transparencySteps <= 0 || transparencySteps > steps) {
            throw new IllegalArgumentException(
                "transparencySteps must be in 1..steps");
        }
        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(ecologySeed).terrainSeed(terrainSeed).build();
        List<String> baseline = run(config, transparencySteps, interval, false);
        List<String> observed = run(config, transparencySteps, interval, true);
        if(!baseline.equals(observed)) {
            throw new IllegalStateException(
                "R2 candidate observation changed ecological, plan, event, or transition facts");
        }
        if(transparencySteps < steps) {
            run(config, steps, interval, true);
        }
        System.out.println("A1A_R2R_CANDIDATE_TRACE PASS transparent=true" +
            " ecologySeed=" + ecologySeed + " terrainSeed=" + terrainSeed +
            " steps=" + steps + " transparencySteps=" + transparencySteps +
            " checkpoints=" + observed.size());
    }

    private static List<String> run(SimulationConfig config, int steps,
                                    int interval, boolean observeCandidates)
    {
        Randomizer.reset();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        StepFacts facts = new StepFacts();
        simulator.getContext().addTransitionObservationListener(facts);
        CandidateFacts candidates = observeCandidates ? new CandidateFacts() : null;
        if(candidates != null) {
            simulator.getContext().addPositionCandidateObservationListener(candidates);
        }
        List<String> result = new ArrayList<>();
        for(int index = 1; index <= steps && simulator.getField().isViable(); index++) {
            facts.captureSpecies(simulator.getField());
            if(candidates != null) {
                candidates.captureSpecies(simulator.getField());
            }
            simulator.simulateOneStep();
            String stepDigest = digest(SimulationFingerprint.capture(simulator) +
                "|plan=" + planDigest(simulator.getContext().getLastPositionPlan()) +
                "|events=" + eventsDigest(simulator.getContext().getCurrentEvents()) +
                "|transitions=" + facts.drainTransitionDigest());
            result.add(stepDigest);
            if(candidates != null && (index % interval == 0 || index == steps ||
                !simulator.getField().isViable())) {
                System.out.println(candidates.drainWindow(simulator.getStep(),
                    simulator.getContext().getLastPositionPlan()));
            }
        }
        return result;
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

    private static String digest(String value)
    {
        MessageDigest sha = newDigest();
        sha.update(value.getBytes(StandardCharsets.UTF_8));
        return hex(sha.digest());
    }

    private static MessageDigest newDigest()
    {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String hex(byte[] bytes)
    {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for(byte valueByte : bytes) {
            result.append(String.format("%02x", valueByte & 0xff));
        }
        return result.toString();
    }

    private static final class StepFacts implements TransitionObservationListener
    {
        private final StringBuilder transitions = new StringBuilder();

        private void captureSpecies(Field field) { }

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

        private String drainTransitionDigest()
        {
            String result = digest(transitions.toString());
            transitions.setLength(0);
            return result;
        }
    }

    private static final class CandidateFacts
        implements PositionCandidateObservationListener
    {
        private final Map<Long, String> speciesById = new LinkedHashMap<>();
        private final Map<String, Long> factsByPhase = new LinkedHashMap<>();
        private final Map<String, Long> proposalsBySpecies = new LinkedHashMap<>();
        private MessageDigest candidateDigest = newDigest();
        private long passable;
        private long snapshotOccupied;
        private long reservedForOther;
        private long shuffleRequests;
        private long shuffleInputs;

        private void captureSpecies(Field field)
        {
            speciesById.clear();
            for(Animal animal : field.getAnimals()) {
                if(animal instanceof SavannahAnimal && animal.isAlive()) {
                    speciesById.put(Long.valueOf(animal.getId()),
                        ((SavannahAnimal)animal).getProfile().getName());
                }
            }
        }

        public void onPositionCandidates(PositionCandidateObservation observation)
        {
            String phase = observation.getPhase().name();
            increment(factsByPhase, phase);
            if(observation.getPhase() ==
                PositionCandidateObservationPhase.PROPOSAL_ADJACENT) {
                String species = speciesById.get(Long.valueOf(observation.getActorId()));
                increment(proposalsBySpecies, species == null ? "UNKNOWN" : species);
            }
            passable += observation.getPassableCount();
            snapshotOccupied += observation.getSnapshotOccupiedCount();
            reservedForOther += observation.getReservedForOtherCount();
            if(observation.isShuffleRequested()) shuffleRequests++;
            shuffleInputs += observation.getShuffleInputSize();
            updateCandidate(new StringBuilder().append(observation.getStep())
                .append(':').append(phase).append(':')
                .append(observation.getActorId()).append(':')
                .append(location(observation.getOrigin())).append(':')
                .append(observation.getPassableCount()).append(':')
                .append(observation.getSnapshotOccupiedCount()).append(':')
                .append(observation.getReservedForOtherCount()).append(':')
                .append(observation.isShuffleRequested()).append(':')
                .append(observation.getShuffleInputSize()).toString());
            for(Location candidate : observation.getCandidates()) {
                updateCandidate(":" + location(candidate));
            }
            updateCandidate("|");
        }

        private String drainWindow(int step, PositionPlan plan)
        {
            String result = "A1A_R2R_CANDIDATE_WINDOW step=" + step +
                " factsByPhase=" + factsByPhase +
                " proposalsBySpecies=" + proposalsBySpecies +
                " passable=" + passable +
                " snapshotOccupied=" + snapshotOccupied +
                " reservedForOther=" + reservedForOther +
                " shuffleRequests=" + shuffleRequests +
                " shuffleInputTotal=" + shuffleInputs +
                " candidateDigest=" + hex(candidateDigest.digest()) +
                " resolvedDestinationDigest=" + planDigest(plan);
            factsByPhase.clear();
            proposalsBySpecies.clear();
            candidateDigest = newDigest();
            passable = 0L;
            snapshotOccupied = 0L;
            reservedForOther = 0L;
            shuffleRequests = 0L;
            shuffleInputs = 0L;
            return result;
        }

        private static void increment(Map<String, Long> values, String key)
        {
            Long current = values.get(key);
            values.put(key, Long.valueOf(current == null ? 1L : current.longValue() + 1L));
        }

        private void updateCandidate(String value)
        {
            candidateDigest.update(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
