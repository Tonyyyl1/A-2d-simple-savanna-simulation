package savanna;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Trophic probe (plan v3.2 A-2): movement, hunt funnel, death ages, terrain
 * use and population statistics for one headless run. Observation only; it
 * reads events, hunt observations and the published field, never the
 * ecological random stream.
 *
 * <p>Usage: {@code TrophicProbe <seed> <steps> <off|on>
 * [--params=key=value,...]}. Prints five line kinds per species:
 * {@code PROBE_MOVE}, {@code PROBE_HUNT}, {@code PROBE_DEATH},
 * {@code PROBE_TERRAIN}, {@code PROBE_POP}.</p>
 */
public final class TrophicProbe
{
    /** Population statistics start here (or at half the run when shorter). */
    private static final int POP_FROM_STEP = 500;
    private static final int TERRAIN_EVERY = 10;
    private static final int AGE_BUCKET = 6;

    private TrophicProbe()
    {
    }

    public static void main(String[] args)
    {
        if(args.length < 3 || !("on".equals(args[2]) || "off".equals(args[2]))) {
            System.err.println("usage: TrophicProbe <seed> <steps> <off|on> " +
                "[--params=key=value,...]");
            System.exit(2);
        }
        for(int index = 3; index < args.length; index++) {
            if(args[index].startsWith("--params=")) {
                EcologyParameters.override(
                    EcologyParameters.defaults().with(args[index].substring(9)));
            }
            else {
                System.err.println("unknown option: " + args[index]);
                System.exit(2);
            }
        }
        long seed = Long.parseLong(args[0]);
        int steps = Integer.parseInt(args[1]);
        boolean thirst = "on".equals(args[2]);

        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(seed).thirstEnabled(thirst).build();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        Probe probe = new Probe();
        simulator.addEventListener(probe);
        simulator.getContext().addHuntObservationListener(probe);

        int popFrom = Math.min(POP_FROM_STEP, steps / 2);
        Map<String, List<Integer>> populations = new TreeMap<>();
        Map<String, Integer> minimum = new TreeMap<>();
        Map<String, Integer> maximum = new TreeMap<>();
        TerrainMap terrain = simulator.getContext().getTerrainMap();
        for(int index = 0; index < steps; index++) {
            simulator.simulateOneStep();
            Map<String, Integer> counts = simulator.getField().countLivingBySpecies();
            probe.scan(simulator.getField(), terrain, simulator.getStep(),
                simulator.getStep() % TERRAIN_EVERY == 0);
            if(index >= popFrom) {
                for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
                    String name = factory.getProfile().getName();
                    int count = counts.getOrDefault(name, 0);
                    populations.computeIfAbsent(name, key -> new ArrayList<>()).add(count);
                    minimum.merge(name, count, Math::min);
                    maximum.merge(name, count, Math::max);
                }
            }
            boolean alive = true;
            for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
                String name = factory.getProfile().getName();
                if(EcologyParameters.current().initialCount(name) > 0 && counts.getOrDefault(name, 0) <= 0) {
                    alive = false;
                }
            }
            if(!alive) break;
        }

        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            SpeciesProfile profile = factory.getProfile();
            String name = profile.getName();
            System.out.println(probe.moveLine(name, limitFor(profile)));
            System.out.println(probe.huntLine(name));
            if(profile.isPredator()) System.out.println(probe.chaseLine(name));
            System.out.println(probe.deathLine(name, profile.getAdultAge()));
            System.out.println(probe.terrainLine(name));
            System.out.println(popLine(name, populations.get(name),
                minimum.getOrDefault(name, 0), maximum.getOrDefault(name, 0)));
        }
    }

    /** Per-step movement limit before B-1: Lion 2, Cheetah 3, herbivores 2. */
    static int limitFor(SpeciesProfile profile)
    {
        if("Cheetah".equals(profile.getName())) return 3;
        return 2;
    }

    private static String popLine(String name, List<Integer> samples, int min, int max)
    {
        if(samples == null || samples.isEmpty()) {
            return "PROBE_POP species=" + name + " n=0";
        }
        double sum = 0.0;
        for(int value : samples) sum += value;
        double mean = sum / samples.size();
        double logMean = 0.0;
        for(int value : samples) logMean += Math.log(value + 1.0);
        logMean /= samples.size();
        double variance = 0.0;
        for(int value : samples) {
            double delta = Math.log(value + 1.0) - logMean;
            variance += delta * delta;
        }
        double lnSd = Math.sqrt(variance / samples.size());
        return String.format(Locale.ROOT,
            "PROBE_POP species=%s n=%d mean=%.1f lnSd=%.3f min=%d max=%d",
            name, samples.size(), mean, lnSd, min, max);
    }

    static final class Probe implements SimulationEventListener, HuntObservationListener
    {
        private final Map<String, Map<Integer, Integer>> moveHistogram = new TreeMap<>();
        private final Map<String, Integer> moveMax = new TreeMap<>();
        private final Map<String, Integer> moveOver = new TreeMap<>();
        private final Map<String, Map<HuntOutcome, Integer>> hunt = new TreeMap<>();
        private final Map<String, Integer> activeSteps = new TreeMap<>();
        private final Map<String, Integer> kills = new TreeMap<>();
        private final Map<String, Map<DeathCause, Integer>> deaths = new TreeMap<>();
        private final Map<String, Map<Integer, Integer>> starveAges = new TreeMap<>();
        private final Map<String, Integer> adultCliff = new TreeMap<>();
        private final Map<String, Integer> starveTotal = new TreeMap<>();
        private final Map<String, Map<TerrainType, Long>> terrainUse = new TreeMap<>();
        /** Age of each animal at the previous scan, for death-age lookup. */
        private final Map<Long, Integer> lastAge = new HashMap<>();
        private final Map<Long, String> speciesOf = new HashMap<>();
        /** Consecutive OUT_OF_CONTACT steps per predator, and how each chase ended. */
        private final Map<Long, Integer> chaseStreak = new HashMap<>();
        private final Map<String, Map<String, Integer>> chaseEnds = new TreeMap<>();

        void scan(Field field, TerrainMap terrain, int step, boolean sampleTerrain)
        {
            for(Animal animal : field.getAnimals()) {
                if(!(animal instanceof SavannahAnimal) || !animal.isAlive()) continue;
                SavannahAnimal savannah = (SavannahAnimal)animal;
                lastAge.put(savannah.getId(), savannah.getAge());
                String name = savannah.getProfile().getName();
                speciesOf.put(savannah.getId(), name);
                if(sampleTerrain && savannah.getLocation() != null) {
                    terrainUse.computeIfAbsent(name, key -> new TreeMap<>())
                        .merge(terrain.getTerrainAt(savannah.getLocation()), 1L, Long::sum);
                }
            }
        }

        @Override
        public void onEvent(SimulationEvent event)
        {
            switch(event.type) {
                case MOVE: {
                    int distance = Math.max(Math.abs(event.row - event.fromRow),
                        Math.abs(event.col - event.fromCol));
                    moveHistogram.computeIfAbsent(event.actorSpecies, key -> new TreeMap<>())
                        .merge(distance, 1, Integer::sum);
                    moveMax.merge(event.actorSpecies, distance, Math::max);
                    SpeciesProfile profile = SpeciesRegistry.getProfile(event.actorSpecies);
                    if(profile != null && distance > limitFor(profile)) {
                        moveOver.merge(event.actorSpecies, 1, Integer::sum);
                    }
                    break;
                }
                case HUNT:
                    kills.merge(event.actorSpecies, 1, Integer::sum);
                    break;
                case DEATH:
                case DISEASE_DEATH: {
                    String name = event.actorSpecies;
                    DeathCause cause = event.type == SimulationEvent.EventType.DISEASE_DEATH
                        ? DeathCause.DISEASE : event.deathCause;
                    deaths.computeIfAbsent(name, key -> new TreeMap<>()).merge(cause, 1, Integer::sum);
                    if(cause == DeathCause.STARVATION) {
                        Integer age = lastAge.get(event.actorId);
                        SpeciesProfile profile = SpeciesRegistry.getProfile(name);
                        if(age != null && profile != null) {
                            starveAges.computeIfAbsent(name, key -> new TreeMap<>())
                                .merge(age / AGE_BUCKET, 1, Integer::sum);
                            starveTotal.merge(name, 1, Integer::sum);
                            int adult = profile.getAdultAge();
                            if(age >= adult && age <= adult + AGE_BUCKET) {
                                adultCliff.merge(name, 1, Integer::sum);
                            }
                        }
                    }
                    break;
                }
                default:
                    break;
            }
        }

        @Override
        public void onHuntResult(HuntObservation observation)
        {
            String name = observation.getActorSpecies();
            if(observation.isActivePhase()) activeSteps.merge(name, 1, Integer::sum);
            HuntOutcome outcome = observation.getResult().getOutcome();
            hunt.computeIfAbsent(name, key -> new TreeMap<>()).merge(outcome, 1, Integer::sum);
            long id = observation.getActorId();
            if(outcome == HuntOutcome.OUT_OF_CONTACT) {
                chaseStreak.merge(id, 1, Integer::sum);
            }
            else if(chaseStreak.containsKey(id)) {
                int length = chaseStreak.remove(id);
                String key = (outcome == HuntOutcome.FAILED_ATTEMPT ||
                    outcome == HuntOutcome.SUCCESS ? "attempt" : "other") + ":" +
                    (length >= 10 ? "10+" : Integer.toString(length));
                chaseEnds.computeIfAbsent(name, k -> new TreeMap<>()).merge(key, 1, Integer::sum);
            }
        }

        String chaseLine(String name)
        {
            StringBuilder text = new StringBuilder();
            for(Map.Entry<String, Integer> entry :
                chaseEnds.getOrDefault(name, new TreeMap<>()).entrySet()) {
                if(text.length() > 0) text.append(',');
                text.append(entry.getKey()).append('=').append(entry.getValue());
            }
            return "PROBE_CHASE species=" + name + " ends=" + (text.length() == 0 ? "-" : text);
        }

        String moveLine(String name, int limit)
        {
            Map<Integer, Integer> histogram = moveHistogram.getOrDefault(name, new TreeMap<>());
            StringBuilder text = new StringBuilder();
            for(Map.Entry<Integer, Integer> entry : histogram.entrySet()) {
                if(text.length() > 0) text.append(',');
                text.append(entry.getKey()).append(':').append(entry.getValue());
            }
            return "PROBE_MOVE species=" + name + " limit=" + limit +
                " max=" + moveMax.getOrDefault(name, 0) +
                " overLimit=" + moveOver.getOrDefault(name, 0) +
                " hist=" + (text.length() == 0 ? "-" : text);
        }

        String huntLine(String name)
        {
            Map<HuntOutcome, Integer> outcomes = hunt.getOrDefault(name, new TreeMap<>());
            return "PROBE_HUNT species=" + name +
                " activeSteps=" + activeSteps.getOrDefault(name, 0) +
                " noLocalReachablePrey=" + outcomes.getOrDefault(HuntOutcome.NO_LOCAL_REACHABLE_PREY, 0) +
                " outOfContact=" + outcomes.getOrDefault(HuntOutcome.OUT_OF_CONTACT, 0) +
                " attempts=" + (outcomes.getOrDefault(HuntOutcome.FAILED_ATTEMPT, 0) +
                    outcomes.getOrDefault(HuntOutcome.SUCCESS, 0)) +
                " kills=" + kills.getOrDefault(name, 0) +
                " declined=" + outcomes.getOrDefault(HuntOutcome.DECLINED, 0);
        }

        String deathLine(String name, int adultAge)
        {
            StringBuilder causes = new StringBuilder();
            for(Map.Entry<DeathCause, Integer> entry :
                deaths.getOrDefault(name, new TreeMap<>()).entrySet()) {
                if(causes.length() > 0) causes.append(',');
                causes.append(entry.getKey()).append(':').append(entry.getValue());
            }
            StringBuilder ages = new StringBuilder();
            for(Map.Entry<Integer, Integer> entry :
                starveAges.getOrDefault(name, new TreeMap<>()).entrySet()) {
                if(ages.length() > 0) ages.append(',');
                ages.append(entry.getKey() * AGE_BUCKET).append(':').append(entry.getValue());
            }
            int total = starveTotal.getOrDefault(name, 0);
            double cliff = total == 0 ? 0.0 : (double)adultCliff.getOrDefault(name, 0) / total;
            return String.format(Locale.ROOT,
                "PROBE_DEATH species=%s adultAge=%d causes=%s starveAges=%s adultCliff=%.3f",
                name, adultAge, causes.length() == 0 ? "-" : causes,
                ages.length() == 0 ? "-" : ages, cliff);
        }

        String terrainLine(String name)
        {
            Map<TerrainType, Long> use = terrainUse.getOrDefault(name, new TreeMap<>());
            long total = 0L;
            for(long value : use.values()) total += value;
            StringBuilder text = new StringBuilder();
            for(Map.Entry<TerrainType, Long> entry : use.entrySet()) {
                if(text.length() > 0) text.append(',');
                text.append(entry.getKey()).append(':').append(String.format(Locale.ROOT,
                    "%.3f", total == 0 ? 0.0 : (double)entry.getValue() / total));
            }
            return "PROBE_TERRAIN species=" + name + " samples=" + total +
                " share=" + (text.length() == 0 ? "-" : text);
        }
    }
}
