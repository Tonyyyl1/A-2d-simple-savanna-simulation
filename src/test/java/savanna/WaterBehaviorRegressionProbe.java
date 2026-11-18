package savanna;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * O0a read-only water/movement regression probe. It observes completed
 * transitions and events; it never participates in behaviour selection.
 */
public final class WaterBehaviorRegressionProbe
    implements SimulationEventListener, TransitionObservationListener
{
    private final Simulator simulator;
    private final WaterSourceIndex waterIndex;
    private final int[][] passablePrefix;
    private final Map<Integer, StepEvents> eventsByStep = new HashMap<>();
    private final Map<Integer, List<TransitionObservation>> transitionsByStep =
        new HashMap<>();
    private final Map<String, BucketStats> buckets = new LinkedHashMap<>();
    private final Map<String, EventStats> eventStats = new LinkedHashMap<>();
    private final Map<String, Long> actionTransitionMatrix = new LinkedHashMap<>();
    private final Map<String, ShoreOccupancy> shoreOccupancy = new LinkedHashMap<>();
    private final Map<Long, Integer> shoreStreak = new HashMap<>();
    private final Map<Long, Integer> lastDrinkStep = new HashMap<>();
    private final Map<String, Long> seekOutcomes = new LinkedHashMap<>();
    private final List<String> fingerprints = new ArrayList<>();
    private long identityViolations;
    private long birthViolations;
    private long nonRestStayedWithRestCredit;
    private long transitionUnknown;
    private long drinkExitCount;
    private long drinkExitStepTotal;

    private WaterBehaviorRegressionProbe(Simulator simulator)
    {
        this.simulator = simulator;
        waterIndex = new WaterSourceIndex(simulator.getContext().getTerrainMap());
        passablePrefix = SpatialBuckets.passablePrefix(simulator.getContext()
            .getTerrainMap());
        simulator.addEventListener(this);
        simulator.getContext().addTransitionObservationListener(this);
    }

    public static void main(String[] args) throws Exception
    {
        if(args.length > 0 && "--compare".equals(args[0])) {
            int steps = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
            long ecologySeed = args.length > 2 ? Long.parseLong(args[2]) : 1111L;
            long terrainSeed = args.length > 3 ? Long.parseLong(args[3]) : 20260629L;
            verifyProbeTransparency(steps, ecologySeed, terrainSeed);
            return;
        }
        if(args.length > 0 && "--repeat".equals(args[0])) {
            int steps = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
            long ecologySeed = args.length > 2 ? Long.parseLong(args[2]) : 1111L;
            long terrainSeed = args.length > 3 ? Long.parseLong(args[3]) : 20260629L;
            verifyRepeatability(steps, ecologySeed, terrainSeed);
            return;
        }
        int steps = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        long ecologySeed = args.length > 1 ? Long.parseLong(args[1]) : 1111L;
        long terrainSeed = args.length > 2 ? Long.parseLong(args[2]) : 20260629L;
        File output = args.length > 3 ? new File(args[3]) : null;
        WaterBehaviorRegressionProbe probe = run(steps, ecologySeed, terrainSeed);
        String report = probe.report();
        if(output != null) {
            probe.writeReport(output, report);
        }
        System.out.print(report);
    }

    public static WaterBehaviorRegressionProbe run(int steps, long ecologySeed,
                                                   long terrainSeed)
    {
        SimulationConfig config = SimulationConfig.builder().mapScale(3)
            .thirstEnabled(true).randomSeed(ecologySeed).terrainSeed(terrainSeed)
            .build();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        WaterBehaviorRegressionProbe probe = new WaterBehaviorRegressionProbe(simulator);
        for(int index = 0; index < steps && simulator.getField().isViable(); index++) {
            probe.observeOneStep();
        }
        probe.assertConservation();
        return probe;
    }

    /** O0a gate: attaching this observer cannot change any observed state. */
    public static void verifyProbeTransparency(int steps, long ecologySeed,
                                               long terrainSeed)
    {
        List<String> withoutProbe = runFingerprints(steps, ecologySeed, terrainSeed,
                                                    false);
        List<String> withProbe = runFingerprints(steps, ecologySeed, terrainSeed,
                                                 true);
        if(!withoutProbe.equals(withProbe)) {
            throw new IllegalStateException("O0a probe changed simulation fingerprint.");
        }
        System.out.println("O0A_PROBE_TRANSPARENCY PASS steps=" + steps +
            " ecologySeed=" + ecologySeed + " terrainSeed=" + terrainSeed +
            " final=" + withoutProbe.get(withoutProbe.size() - 1));
    }

    /** O0a gate: two separately constructed runs must produce the same audit. */
    public static void verifyRepeatability(int steps, long ecologySeed,
                                           long terrainSeed)
    {
        WaterBehaviorRegressionProbe first = run(steps, ecologySeed, terrainSeed);
        WaterBehaviorRegressionProbe second = run(steps, ecologySeed, terrainSeed);
        if(!first.getFingerprints().equals(second.getFingerprints()) ||
           !first.report().equals(second.report())) {
            throw new IllegalStateException("O0a same-seed repeatability failed.");
        }
        System.out.println("O0A_REPEATABILITY PASS steps=" + steps +
            " ecologySeed=" + ecologySeed + " terrainSeed=" + terrainSeed +
            " final=" + first.getFingerprints().get(first.getFingerprints().size() - 1));
    }

    private static List<String> runFingerprints(int steps, long ecologySeed,
                                                long terrainSeed,
                                                boolean attachProbe)
    {
        SimulationConfig config = SimulationConfig.builder().mapScale(3)
            .thirstEnabled(true).randomSeed(ecologySeed).terrainSeed(terrainSeed)
            .build();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        WaterBehaviorRegressionProbe probe = attachProbe
            ? new WaterBehaviorRegressionProbe(simulator) : null;
        List<String> values = new ArrayList<>();
        values.add(SimulationFingerprint.capture(simulator));
        for(int index = 0; index < steps && simulator.getField().isViable(); index++) {
            if(probe == null) {
                simulator.simulateOneStep();
            }
            else {
                probe.observeOneStep();
            }
            values.add(SimulationFingerprint.capture(simulator));
        }
        return values;
    }

    private void observeOneStep()
    {
        int nextStep = simulator.getStep() + 1;
        Map<Long, StartFact> start = startFacts();
        simulator.simulateOneStep();
        Map<Long, SavannahAnimal> end = livingById();
        List<TransitionObservation> transitions = transitionsByStep.getOrDefault(
            simulator.getStep(), Collections.emptyList());
        StepEvents events = eventsByStep.remove(simulator.getStep());
        if(events == null) events = new StepEvents();
        auditIdentity(start, end, transitions, events);
        auditTransitions(start, end, transitions);
        observePopulation(end);
        transitionsByStep.remove(simulator.getStep());
        fingerprints.add(SimulationFingerprint.capture(simulator));
        if(simulator.getStep() != nextStep) {
            throw new IllegalStateException("Probe step alignment failed.");
        }
    }

    private Map<Long, StartFact> startFacts()
    {
        Map<Long, StartFact> facts = new HashMap<>();
        Field field = simulator.getField();
        ThirstSystem thirst = simulator.getContext().getThirstSystem();
        for(Animal actor : field.getAnimals()) {
            if(!(actor instanceof SavannahAnimal) || !actor.isAlive()) {
                continue;
            }
            SavannahAnimal animal = (SavannahAnimal)actor;
            Location location = animal.getLocation();
            boolean closer = hasStrictlyCloserFreeNeighbor(field, thirst, location);
            facts.put(animal.getId(), new StartFact(animal, location, closer));
        }
        return facts;
    }

    private boolean hasStrictlyCloserFreeNeighbor(Field field, ThirstSystem thirst,
                                                   Location origin)
    {
        if(origin == null || thirst == null) {
            return false;
        }
        int distance = thirst.distanceToShore(origin);
        for(int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            for(int colOffset = -1; colOffset <= 1; colOffset++) {
                if(rowOffset == 0 && colOffset == 0) {
                    continue;
                }
                Location candidate = new Location(origin.row() + rowOffset,
                    origin.col() + colOffset);
                TerrainMap terrain = simulator.getContext().getTerrainMap();
                if(candidate.row() < 0 || candidate.row() >= terrain.getDepth() ||
                   candidate.col() < 0 || candidate.col() >= terrain.getWidth() ||
                   !terrain.isPassable(candidate)) {
                    continue;
                }
                Animal occupant = field.getAnimalAt(candidate);
                if((occupant == null || !occupant.isAlive()) &&
                   thirst.distanceToShore(candidate) < distance) {
                    return true;
                }
            }
        }
        return false;
    }

    private Map<Long, SavannahAnimal> livingById()
    {
        Map<Long, SavannahAnimal> result = new HashMap<>();
        for(Animal actor : simulator.getField().getAnimals()) {
            if(actor instanceof SavannahAnimal && actor.isAlive()) {
                SavannahAnimal animal = (SavannahAnimal)actor;
                result.put(animal.getId(), animal);
            }
        }
        return result;
    }

    private void auditIdentity(Map<Long, StartFact> start,
                               Map<Long, SavannahAnimal> end,
                               List<TransitionObservation> transitions,
                               StepEvents events)
    {
        Set<Long> transitioned = new HashSet<>();
        Set<Long> spawnCandidates = new HashSet<>();
        for(TransitionObservation observation : transitions) {
            if(observation.getStatus() == TransitionStatus.SPAWNED ||
               !start.containsKey(observation.getAnimalId())) {
                spawnCandidates.add(observation.getAnimalId());
            }
            else {
                transitioned.add(observation.getAnimalId());
            }
            if(observation.getStatus() == null) {
                transitionUnknown++;
            }
        }
        Set<Long> deaths = new HashSet<>();
        Set<Long> births = new HashSet<>();
        deaths.addAll(events.deaths);
        births.addAll(events.births);
        for(long id : start.keySet()) {
            if(!transitioned.contains(id) && !deaths.contains(id)) {
                identityViolations++;
            }
        }
        for(long id : births) {
            if(!spawnCandidates.contains(id)) {
                birthViolations++;
            }
        }
        for(long id : end.keySet()) {
            if(!start.containsKey(id) && !spawnCandidates.contains(id)) {
                birthViolations++;
            }
        }
    }

    private void auditTransitions(Map<Long, StartFact> start,
                                  Map<Long, SavannahAnimal> end,
                                  List<TransitionObservation> transitions)
    {
        ThirstSystem thirst = simulator.getContext().getThirstSystem();
        for(TransitionObservation observation : transitions) {
            if(observation.getStatus() == TransitionStatus.SPAWNED) {
                continue;
            }
            SavannahAnimal animal = end.get(observation.getAnimalId());
            String action = animal == null || animal.getLastBehaviorAction() == null
                ? "UNKNOWN" : animal.getLastBehaviorAction().name();
            String physical = physicalOutcome(observation);
            increment(actionTransitionMatrix, action + " x " + physical);
            BehaviorOutcome outcome = animal == null ? null :
                animal.getLastBehaviorOutcome();
            if("STAYED".equals(physical) && outcome != null &&
               outcome.receivesRestCredit() && action != null &&
               !"REST".equals(action)) {
                nonRestStayedWithRestCredit++;
            }
            if("SEEK_WATER".equals(action)) {
                classifySeek(start.get(observation.getAnimalId()), observation, thirst);
            }
        }
    }

    private String physicalOutcome(TransitionObservation observation)
    {
        TransitionStatus status = observation.getStatus();
        if(status == TransitionStatus.MOVED || status == TransitionStatus.REROUTED) {
            return "MOVED";
        }
        if(status == TransitionStatus.STAYED) {
            return "STAYED";
        }
        if(status == TransitionStatus.BLOCKED || status == TransitionStatus.CONFLICT_LOST) {
            return "UNRESOLVED";
        }
        return "UNKNOWN";
    }

    private void classifySeek(StartFact start, TransitionObservation observation,
                              ThirstSystem thirst)
    {
        if(start == null || thirst == null || observation.getResolved() == null) {
            increment(seekOutcomes, "EXISTING_FACTS_INSUFFICIENT");
            return;
        }
        int before = thirst.distanceToShore(start.location);
        int after = thirst.distanceToShore(observation.getResolved());
        if(after < before) {
            increment(seekOutcomes, "STRICTLY_CLOSER");
        }
        else if(!start.strictlyCloserFreeNeighbor) {
            increment(seekOutcomes, "NO_STRICTLY_CLOSER_FREE_CELL");
        }
        else if(observation.getStatus() == TransitionStatus.BLOCKED ||
                observation.getStatus() == TransitionStatus.CONFLICT_LOST ||
                observation.usedSearchFallback()) {
            increment(seekOutcomes, "RESERVATION_FAILURE_OR_FALLBACK");
        }
        else {
            increment(seekOutcomes, "ALGORITHM_SELECTION_ERROR");
        }
    }

    private void observePopulation(Map<Long, SavannahAnimal> end)
    {
        SpatialBuckets density = new SpatialBuckets(simulator.getContext().getTerrainMap(),
            passablePrefix, end.values());
        Map<String, Integer> occupiedShore = new HashMap<>();
        for(SavannahAnimal animal : end.values()) {
            Location location = animal.getLocation();
            String pool = waterIndex.inferredNearestPool(location);
            String key = animal.getProfile().getName() + "|" +
                simulator.getContext().getClock().getPhase() + "|" + pool;
            BucketStats stats = buckets.computeIfAbsent(key, ignored -> new BucketStats());
            int shoreDistance = simulator.getContext().getThirstSystem()
                .distanceToShore(location);
            stats.observe(animal, shoreDistance,
                simulator.getContext().getFoodSystem().getFoodLevelAt(location));
            if(shoreDistance == 0) {
                String shorePool = waterIndex.shorePool(location);
                if(!WaterSourceIndex.NONE.equals(shorePool)) {
                    occupiedShore.put(shorePool,
                        occupiedShore.getOrDefault(shorePool, 0) + 1);
                }
                int streak = shoreStreak.getOrDefault(animal.getId(), 0) + 1;
                shoreStreak.put(animal.getId(), streak);
                stats.maxShoreStreak = Math.max(stats.maxShoreStreak, streak);
            }
            else {
                shoreStreak.remove(animal.getId());
                Integer drankAt = lastDrinkStep.remove(animal.getId());
                if(drankAt != null) {
                    drinkExitCount++;
                    drinkExitStepTotal += simulator.getStep() - drankAt;
                }
            }
            if(shoreDistance <= 12 || animal.getLastBehaviorAction() == ActionType.SEEK_WATER) {
                stats.observeDensity(density, animal);
            }
        }
        for(String pool : waterIndex.getPoolIds()) {
            int occupied = occupiedShore.getOrDefault(pool, 0);
            shoreOccupancy.computeIfAbsent(pool, ignored -> new ShoreOccupancy(
                waterIndex.getShoreCellCount(pool))).record(occupied);
        }
    }

    public void onEvent(SimulationEvent event)
    {
        if(event != null) {
            StepEvents stepEvents = eventsByStep.computeIfAbsent(event.step,
                ignored -> new StepEvents());
            if(event.type == SimulationEvent.EventType.DEATH ||
               event.type == SimulationEvent.EventType.DISEASE_DEATH) {
                stepEvents.deaths.add(event.actorId);
            }
            if(event.type == SimulationEvent.EventType.BIRTH) {
                stepEvents.births.add(event.targetId);
            }
            DayPhase phase = simulator.getContext().getClock().getPhase();
            String key = event.type + "|" + event.actorSpecies + "|" + phase;
            eventStats.computeIfAbsent(key, ignored -> new EventStats()).record(event);
            if(event.type == SimulationEvent.EventType.DRINK) {
                lastDrinkStep.put(event.actorId, event.step);
            }
        }
    }

    public void onTransition(TransitionObservation observation)
    {
        if(observation != null) {
            transitionsByStep.computeIfAbsent(observation.getStep(),
                ignored -> new ArrayList<>()).add(observation);
        }
    }

    public List<String> getFingerprints()
    {
        return Collections.unmodifiableList(fingerprints);
    }

    public String report()
    {
        StringBuilder report = new StringBuilder();
        SimulationConfig config = simulator.getContext().getConfig();
        report.append("O0A WATER_BEHAVIOR_REGRESSION\n");
        report.append("CONFIG ").append(config.describe()).append('\n');
        report.append("PROVENANCE ").append(simulator.getContext()
            .getRenderProvenance().detailedSummary()).append('\n');
        report.append("POOLS ").append(waterIndex.getPoolIds()).append('\n');
        report.append("COMPLETED step=").append(simulator.getStep())
            .append(" finalFingerprint=")
            .append(fingerprints.isEmpty() ? "" : fingerprints.get(fingerprints.size() - 1))
            .append('\n');
        report.append("IDENTITY startMissing=").append(identityViolations)
            .append(" birthMismatch=").append(birthViolations)
            .append(" transitionUnknown=").append(transitionUnknown).append('\n');
        report.append("REST_CREDIT nonRestStayed=")
            .append(nonRestStayedWithRestCredit).append('\n');
        report.append("SEEK_OUTCOMES ").append(seekOutcomes).append('\n');
        report.append("DRINK_EXIT count=").append(drinkExitCount)
            .append(" averageSteps=").append(format(drinkExitCount == 0 ? 0.0 :
                (double)drinkExitStepTotal / drinkExitCount)).append('\n');
        for(Map.Entry<String, Long> entry : actionTransitionMatrix.entrySet()) {
            report.append("ACTION_PHYSICAL ").append(entry.getKey()).append(" count=")
                .append(entry.getValue()).append('\n');
        }
        for(Map.Entry<String, EventStats> entry : eventStats.entrySet()) {
            report.append("EVENT ").append(entry.getKey()).append(' ')
                .append(entry.getValue()).append('\n');
        }
        for(Map.Entry<String, ShoreOccupancy> entry : shoreOccupancy.entrySet()) {
            report.append("SHORE_OCCUPANCY ").append(entry.getKey()).append(' ')
                .append(entry.getValue()).append('\n');
        }
        for(Map.Entry<String, BucketStats> entry : buckets.entrySet()) {
            report.append("BUCKET ").append(entry.getKey()).append(' ')
                .append(entry.getValue()).append('\n');
        }
        return report.toString();
    }

    private void writeReport(File directory, String report) throws IOException
    {
        if(!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create output directory: " + directory);
        }
        File output = new File(directory, "water-behavior-regression.txt");
        Files.writeString(output.toPath(), report, StandardCharsets.UTF_8);
        System.out.println("Wrote O0a water behavior report: " + output.getAbsolutePath());
    }

    private void assertConservation()
    {
        if(identityViolations != 0 || birthViolations != 0 || transitionUnknown != 0) {
            throw new IllegalStateException("O0a identity conservation failed: startMissing=" +
                identityViolations + " birthMismatch=" + birthViolations +
                " transitionUnknown=" + transitionUnknown);
        }
    }

    private static void increment(Map<String, Long> map, String key)
    {
        map.put(key, map.getOrDefault(key, 0L) + 1L);
    }

    private static String format(double value)
    {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static final class StartFact
    {
        private final Location location;
        private final boolean strictlyCloserFreeNeighbor;

        StartFact(SavannahAnimal animal, Location location,
                  boolean strictlyCloserFreeNeighbor)
        {
            this.location = location;
            this.strictlyCloserFreeNeighbor = strictlyCloserFreeNeighbor;
        }
    }

    private static final class EventStats
    {
        private long count;
        private long births;
        private long hunts;
        private long deaths;

        void record(SimulationEvent event)
        {
            count++;
            births += event.type == SimulationEvent.EventType.BIRTH ? 1 : 0;
            hunts += event.type == SimulationEvent.EventType.HUNT ? 1 : 0;
            deaths += event.type == SimulationEvent.EventType.DEATH ||
                      event.type == SimulationEvent.EventType.DISEASE_DEATH ? 1 : 0;
        }

        public String toString()
        {
            return "count=" + count + " births=" + births + " hunts=" + hunts +
                   " deaths=" + deaths;
        }
    }

    private static final class StepEvents
    {
        private final Set<Long> deaths = new HashSet<>();
        private final Set<Long> births = new HashSet<>();
    }

    private static final class ShoreOccupancy
    {
        private final int shoreCells;
        private long totalOccupied;
        private int maxOccupied;
        private long samples;

        ShoreOccupancy(int shoreCells) { this.shoreCells = shoreCells; }

        void record(int occupied)
        {
            totalOccupied += occupied;
            maxOccupied = Math.max(maxOccupied, occupied);
            samples++;
        }

        public String toString()
        {
            return "shoreCells=" + shoreCells + " meanOccupied=" +
                format(samples == 0 ? 0.0 : (double)totalOccupied / samples) +
                " maxOccupied=" + maxOccupied;
        }
    }

    private static final class BucketStats
    {
        private long animalTicks;
        private long shoreTicks;
        private long nearTicks;
        private long ring6Ticks;
        private long ring12Ticks;
        private double hydrationTotal;
        private double grassTotal;
        private int maxShoreStreak;
        private final DensityStats density = new DensityStats();

        void observe(SavannahAnimal animal, int shoreDistance, double grass)
        {
            animalTicks++;
            shoreTicks += shoreDistance == 0 ? 1 : 0;
            nearTicks += shoreDistance <= 2 ? 1 : 0;
            ring6Ticks += shoreDistance <= 6 ? 1 : 0;
            ring12Ticks += shoreDistance <= 12 ? 1 : 0;
            hydrationTotal += animal.getHydrationPercent();
            grassTotal += grass;
        }

        void observeDensity(SpatialBuckets index, SavannahAnimal animal)
        {
            for(int radius : new int[] {3, 6, 12}) {
                DensityCounts values = index.counts(animal, radius);
                density.record(radius, values);
            }
        }

        public String toString()
        {
            return "ticks=" + animalTicks + " shore=" + percent(shoreTicks, animalTicks) +
                " near2=" + percent(nearTicks, animalTicks) +
                " ring6=" + percent(ring6Ticks, animalTicks) +
                " ring12=" + percent(ring12Ticks, animalTicks) +
                " avgHydration=" + format(animalTicks == 0 ? 0.0 : hydrationTotal / animalTicks) +
                " avgGrass=" + format(animalTicks == 0 ? 0.0 : grassTotal / animalTicks) +
                " maxShoreStreak=" + maxShoreStreak + " " + density;
        }

        private static String percent(long part, long total)
        {
            return format(total == 0 ? 0.0 : part * 100.0 / total) + "%";
        }
    }

    private static final class DensityStats
    {
        private final Map<Integer, double[]> sums = new LinkedHashMap<>();

        void record(int radius, DensityCounts counts)
        {
            double[] values = sums.computeIfAbsent(radius, ignored -> new double[4]);
            values[0] += counts.all;
            values[1] += counts.conspecific;
            values[2] += counts.trophic;
            values[3] += 1.0;
        }

        public String toString()
        {
            StringBuilder result = new StringBuilder("density[");
            for(Map.Entry<Integer, double[]> entry : sums.entrySet()) {
                if(result.length() > 8) result.append(';');
                double[] values = entry.getValue();
                result.append('r').append(entry.getKey()).append(" all=")
                    .append(format(values[0] / values[3])).append(" conspecific=")
                    .append(format(values[1] / values[3])).append(" trophic=")
                    .append(format(values[2] / values[3]));
            }
            return result.append(']').toString();
        }
    }

    private static final class DensityCounts
    {
        private final double all;
        private final double conspecific;
        private final double trophic;

        DensityCounts(double all, double conspecific, double trophic)
        {
            this.all = all;
            this.conspecific = conspecific;
            this.trophic = trophic;
        }
    }

    /** Sparse 13x13 spatial bins make O0a density observation read-only and bounded. */
    private static final class SpatialBuckets
    {
        private static final int BIN = 13;
        private final TerrainMap terrain;
        private final Map<Integer, List<SavannahAnimal>> buckets = new HashMap<>();
        private final int binColumns;
        private final int[][] passablePrefix;

        SpatialBuckets(TerrainMap terrain, int[][] passablePrefix,
                       Iterable<SavannahAnimal> animals)
        {
            this.terrain = terrain;
            binColumns = (terrain.getWidth() + BIN - 1) / BIN;
            this.passablePrefix = passablePrefix;
            for(SavannahAnimal animal : animals) {
                Location location = animal.getLocation();
                int key = key(location.row() / BIN, location.col() / BIN);
                buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(animal);
            }
        }

        DensityCounts counts(SavannahAnimal center, int radius)
        {
            Location location = center.getLocation();
            int all = 0;
            int conspecific = 0;
            int trophic = 0;
            int minBinRow = Math.max(0, (location.row() - radius) / BIN);
            int maxBinRow = Math.min((terrain.getDepth() - 1) / BIN,
                (location.row() + radius) / BIN);
            int minBinCol = Math.max(0, (location.col() - radius) / BIN);
            int maxBinCol = Math.min((terrain.getWidth() - 1) / BIN,
                (location.col() + radius) / BIN);
            for(int binRow = minBinRow; binRow <= maxBinRow; binRow++) {
                for(int binCol = minBinCol; binCol <= maxBinCol; binCol++) {
                    for(SavannahAnimal candidate : buckets.getOrDefault(key(binRow, binCol),
                        Collections.emptyList())) {
                        Location other = candidate.getLocation();
                        if(Math.abs(other.row() - location.row()) > radius ||
                           Math.abs(other.col() - location.col()) > radius) {
                            continue;
                        }
                        all++;
                        if(candidate.getProfile().getName().equals(
                            center.getProfile().getName())) conspecific++;
                        if(candidate.getProfile().isPredator() ==
                           center.getProfile().isPredator()) trophic++;
                    }
                }
            }
            int capacity = capacity(location, radius);
            return new DensityCounts(all / (double)capacity,
                conspecific / (double)capacity, trophic / (double)capacity);
        }

        private int capacity(Location location, int radius)
        {
            int top = Math.max(0, location.row() - radius);
            int left = Math.max(0, location.col() - radius);
            int bottom = Math.min(terrain.getDepth() - 1, location.row() + radius);
            int right = Math.min(terrain.getWidth() - 1, location.col() + radius);
            return Math.max(1, passablePrefix[bottom + 1][right + 1] -
                passablePrefix[top][right + 1] - passablePrefix[bottom + 1][left] +
                passablePrefix[top][left]);
        }

        private int key(int row, int col) { return row * binColumns + col; }

        static int[][] passablePrefix(TerrainMap terrain)
        {
            int[][] prefix = new int[terrain.getDepth() + 1][terrain.getWidth() + 1];
            for(int row = 0; row < terrain.getDepth(); row++) {
                for(int col = 0; col < terrain.getWidth(); col++) {
                    int passable = terrain.isPassable(new Location(row, col)) ? 1 : 0;
                    prefix[row + 1][col + 1] = passable + prefix[row][col + 1] +
                        prefix[row + 1][col] - prefix[row][col];
                }
            }
            return prefix;
        }
    }
}
