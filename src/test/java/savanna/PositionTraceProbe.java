package savanna;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Observation-only A1a-R0 baseline trace for the legacy transition path.
 * Priority and resolution reason intentionally remain LEGACY_UNAVAILABLE here;
 * this probe records the facts a replacement must explain without changing
 * the simulator's decisions or random-number stream.
 */
public final class PositionTraceProbe
{
    private PositionTraceProbe() {}

    public static void main(String[] args)
    {
        int steps = argument(args, 0, 1000);
        int interval = argument(args, 1, 100);
        long ecologySeed = longArgument(args, 2,
            SimulationConfig.DEFAULT_RANDOM_SEED);
        long terrainSeed = longArgument(args, 3,
            SimulationConfig.DEFAULT_TERRAIN_SEED);
        if(steps <= 0 || interval <= 0) {
            throw new IllegalArgumentException("steps and interval must be positive");
        }

        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(ecologySeed).terrainSeed(terrainSeed).build();
        Randomizer.reset();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        TraceCollector collector = new TraceCollector();
        simulator.getContext().addTransitionObservationListener(collector);

        System.out.println("POSITION_TRACE_BASELINE schema=R0" +
            " priority=LEGACY_UNAVAILABLE" +
            " reason=LEGACY_UNAVAILABLE" +
            " ecologySeed=" + ecologySeed +
            " terrainSeed=" + terrainSeed +
            " steps=" + steps + " interval=" + interval);
        for(int index = 1; index <= steps && simulator.getField().isViable();
            index++) {
            collector.captureSpecies(simulator.getField());
            simulator.simulateOneStep();
            if(index % interval == 0 || index == steps ||
               !simulator.getField().isViable()) {
                System.out.println("TRACE step=" + simulator.getStep() +
                    " transitions=" + collector.getTotals() +
                    " bySpecies=" + collector.getBySpecies() +
                    " deaths=" + simulator.getContext().getDeathSummary() +
                    " counts=" + simulator.getField().countLivingBySpecies() +
                    " fingerprint=" + SimulationFingerprint.capture(simulator));
            }
        }
        System.out.println("POSITION_TRACE_BASELINE PASS completed=" +
            simulator.getStep() + " viable=" + simulator.getField().isViable() +
            " transitions=" + collector.getTotals());
    }

    private static int argument(String[] args, int index, int fallback)
    {
        return args.length > index ? Integer.parseInt(args[index]) : fallback;
    }

    private static long longArgument(String[] args, int index, long fallback)
    {
        return args.length > index ? Long.parseLong(args[index]) : fallback;
    }

    private static final class TraceCollector
        implements TransitionObservationListener
    {
        private final Map<Long, String> speciesById = new HashMap<>();
        private final Map<TransitionStatus, Long> totals =
            new EnumMap<>(TransitionStatus.class);
        private final Map<String, Map<TransitionStatus, Long>> bySpecies =
            new LinkedHashMap<>();

        private void captureSpecies(Field field)
        {
            speciesById.clear();
            for(Animal animal : field.getAnimals()) {
                if(animal instanceof SavannahAnimal && animal.isAlive()) {
                    speciesById.put(animal.getId(),
                        ((SavannahAnimal)animal).getProfile().getName());
                }
            }
        }

        public void onTransition(TransitionObservation observation)
        {
            TransitionStatus status = observation.getStatus();
            increment(totals, status);
            String species = speciesById.get(observation.getAnimalId());
            if(species == null) {
                species = status == TransitionStatus.SPAWNED ? "SPAWN" : "UNKNOWN";
            }
            Map<TransitionStatus, Long> speciesTotals = bySpecies.get(species);
            if(speciesTotals == null) {
                speciesTotals = new EnumMap<>(TransitionStatus.class);
                bySpecies.put(species, speciesTotals);
            }
            increment(speciesTotals, status);
        }

        private Map<TransitionStatus, Long> getTotals()
        {
            return new EnumMap<>(totals);
        }

        private Map<String, Map<TransitionStatus, Long>> getBySpecies()
        {
            Map<String, Map<TransitionStatus, Long>> copy = new LinkedHashMap<>();
            for(Map.Entry<String, Map<TransitionStatus, Long>> entry :
                    bySpecies.entrySet()) {
                copy.put(entry.getKey(), new EnumMap<>(entry.getValue()));
            }
            return copy;
        }

        private static void increment(Map<TransitionStatus, Long> counts,
                                      TransitionStatus status)
        {
            counts.put(status, counts.getOrDefault(status, 0L) + 1L);
        }
    }
}
