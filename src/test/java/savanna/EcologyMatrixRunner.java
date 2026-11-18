package savanna;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * One headless ecology run for the T3 seed matrix and M3 sweeps (plan v3.0
 * §2.4, §7).
 *
 * <p>Usage: {@code EcologyMatrixRunner <ecologySeed> <steps> <off|on>
 * [--enforce] [--params=key=value,...] [--diag] [--terrain-profile=NAME]
 * [--terrain-seed=N]}. Prints a single {@code ECO_RESULT} line (and, with
 * {@code --diag}, one {@code ECO_DIAG} line). The run is judged against the
 * frozen ecology gates (all species alive, windowed-mean balance <= 12.0, no
 * hotspot overloaded for more than 300 steps, closed regional ledger, and
 * G-ECO-5: every birth traced to the birth account and predator births
 * funded by kills); without {@code --enforce} a failing
 * judgement is reported but the exit code stays 0, so pre-M3 baselines
 * remain informational. {@code --params} replaces {@link EcologyParameters}
 * defaults for this JVM only.</p>
 *
 * <p>Balance (M3 gate measurement, user decision 2026-10-28, B1): the gate
 * uses {@code balanceMean}, the largest over the smallest per-species
 * <em>mean count</em> over the last {@link #MEAN_WINDOW} steps (half the run
 * when it is shorter than twice that). The end-point {@code balance} is still
 * printed, and {@code gatePoint} is the verdict the end-point rule gave
 * before B1, so older results stay comparable. The 12.0 limit is unchanged.</p>
 */
public final class EcologyMatrixRunner
{
    private static final double BALANCE_LIMIT = 12.0;
    /** G-ECO-3: no hotspot may stay overloaded for longer than this. */
    private static final int MAX_OVERLOAD_SPAN = 300;
    /** B1: the balance gate averages each species over this many final steps. */
    private static final int MEAN_WINDOW = 500;

    private EcologyMatrixRunner()
    {
    }

    public static void main(String[] args)
    {
        boolean enforce = false;
        boolean diag = false;
        boolean countFrames = false;
        String params = null;
        TerrainGenerationProfile terrainProfile = null;
        Long terrainSeed = null;
        for(int index = 3; index < args.length; index++) {
            if("--enforce".equals(args[index])) enforce = true;
            else if("--diag".equals(args[index])) diag = true;
            else if("--frame-sink=count".equals(args[index])) countFrames = true;
            else if(args[index].startsWith("--params=")) params = args[index].substring(9);
            else if(args[index].startsWith("--terrain-profile=")) {
                terrainProfile = TerrainGenerationProfile.valueOf(args[index].substring(18));
            }
            else if(args[index].startsWith("--terrain-seed=")) {
                terrainSeed = Long.valueOf(args[index].substring(15));
            }
            else {
                System.err.println("unknown option: " + args[index]);
                System.exit(2);
            }
        }
        if(args.length < 3 || !("on".equals(args[2]) || "off".equals(args[2]))) {
            System.err.println("usage: EcologyMatrixRunner <ecologySeed> <steps> <off|on> " +
                "[--enforce] [--params=key=value,...] [--diag] " +
                "[--terrain-profile=LEGACY_V1|PROCEDURAL_V2] [--terrain-seed=N]");
            System.exit(2);
        }
        if(params != null) {
            try {
                EcologyParameters.override(EcologyParameters.defaults().with(params));
            }
            catch(IllegalArgumentException e) {
                System.err.println("invalid --params: " + e.getMessage());
                System.exit(2);
            }
        }
        long seed = Long.parseLong(args[0]);
        int steps = Integer.parseInt(args[1]);
        boolean thirst = "on".equals(args[2]);
        if(steps <= 0) {
            System.err.println("steps must be positive");
            System.exit(2);
        }

        SimulationConfig.Builder builder = SimulationConfig.builder()
            .randomSeed(seed).thirstEnabled(thirst);
        if(terrainProfile != null) builder.terrainGenerationProfile(terrainProfile);
        if(terrainSeed != null) builder.terrainSeed(terrainSeed.longValue());
        SimulationConfig config = builder.build();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        if(countFrames) {
            // V1: prove that frame export does not change the result.
            simulator.addFrameSink(new FrameSink() {
                @Override public void onWorld(WorldDescriptor world) { }
                @Override public void onFrame(SimFrame frame) { }
                @Override public void onRunEnd(String reason) { }
            });
        }
        BirthTrace birthTrace = new BirthTrace(simulator.getField());
        simulator.getContext().addEventListener(birthTrace);
        Diagnostics diagnostics = null;
        if(diag) {
            diagnostics = new Diagnostics();
            simulator.getContext().addEventListener(diagnostics);
            simulator.getContext().addStepFrameLedgerListener(diagnostics);
        }
        RegionalHotspotMonitor hotspots = new RegionalHotspotMonitor(
            simulator.getField().getDepth(), simulator.getField().getWidth());
        hotspots.observe(simulator.getField());
        Map<String, Integer> minimum =
            new LinkedHashMap<>(simulator.getField().countLivingBySpecies());
        // C-0: only species with a founding population take part in the verdict.
        java.util.Set<String> participants = new java.util.LinkedHashSet<>();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String species = factory.getProfile().getName();
            if(EcologyParameters.current().initialCount(species) > 0) participants.add(species);
        }
        int passableCells = 0;
        for(int row = 0; row < simulator.getField().getDepth(); row++) {
            for(int col = 0; col < simulator.getField().getWidth(); col++) {
                if(simulator.getContext().getTerrainMap().isPassable(new Location(row, col))) {
                    passableCells++;
                }
            }
        }
        double targetScale = EcologyTargets.scale(passableCells);
        long[] stepNanos = new long[steps];
        int meanWindow = Math.min(MEAN_WINDOW, steps / 2);
        Map<String, Long> windowSum = new LinkedHashMap<>();
        Map<String, double[]> windowLn = new LinkedHashMap<>();
        int windowSamples = 0;
        int firstExtinctionStep = -1;
        String firstExtinctSpecies = "";
        long start = System.nanoTime();
        int completed = 0;
        for(int index = 0; index < steps; index++) {
            long stepStart = System.nanoTime();
            simulator.simulateOneStep();
            stepNanos[index] = System.nanoTime() - stepStart;
            completed++;
            hotspots.observe(simulator.getField());
            Map<String, Integer> counts = simulator.getField().countLivingBySpecies();
            if(index >= steps - meanWindow) {
                windowSamples++;
                for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
                    String species = factory.getProfile().getName();
                    int windowCount = counts.getOrDefault(species, 0);
                    windowSum.merge(species, (long)windowCount, Long::sum);
                    // A-5: running sums of ln(count + 1) and its square.
                    double[] ln = windowLn.computeIfAbsent(species, key -> new double[2]);
                    double logCount = Math.log(windowCount + 1.0);
                    ln[0] += logCount;
                    ln[1] += logCount * logCount;
                }
            }
            for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
                String species = factory.getProfile().getName();
                if(!participants.contains(species)) continue;
                int count = counts.getOrDefault(species, 0);
                minimum.merge(species, count, Math::min);
                if(count == 0 && firstExtinctionStep < 0) {
                    firstExtinctionStep = simulator.getStep();
                    firstExtinctSpecies = species;
                }
            }
            boolean participantsAlive = true;
            for(String species : participants) {
                if(counts.getOrDefault(species, 0) <= 0) participantsAlive = false;
            }
            if(!participantsAlive) break;
        }
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        Map<String, Integer> finalCounts = simulator.getField().countLivingBySpecies();
        double balance = balance(finalCounts);
        double balanceMean = windowSamples == 0 ? balance : meanBalance(windowSum, windowSamples);
        boolean allAlive = firstExtinctionStep < 0 && completed == steps;
        boolean hotspotOk = hotspots.getMaxOverloadSpan() <= MAX_OVERLOAD_SPAN;
        boolean birthsTraced = birthTrace.traced(simulator.getField(),
            simulator.getContext().getBirthAccount());
        String unfunded = birthTrace.unfundedPredators();
        boolean structural = allAlive && hotspotOk &&
            hotspots.ledgerCloses() && birthsTraced && unfunded.isEmpty();
        Map<String, Double> windowMeans = new LinkedHashMap<>();
        for(String species : participants) {
            windowMeans.put(species, windowSamples == 0
                ? (double)finalCounts.getOrDefault(species, 0)
                : (double)windowSum.getOrDefault(species, 0L) / windowSamples);
        }
        double balanceNorm = EcologyTargets.normalizedBalance(windowMeans, participants,
            targetScale);
        boolean pass = structural && balanceNorm <= BALANCE_LIMIT;
        boolean passPoint = structural && balance <= BALANCE_LIMIT;
        long[] measured = Arrays.copyOf(stepNanos, completed);
        Arrays.sort(measured);
        EcologyParameters parameters = EcologyParameters.current();

        StringBuilder line = new StringBuilder("ECO_RESULT");
        line.append(" rules=").append(EcologyRules.VERSION)
            .append(" paramsHash=").append(parameters.hash())
            .append(" params=").append(parameters.overrides())
            .append(" seed=").append(seed)
            .append(" thirst=").append(thirst ? "on" : "off")
            .append(" requested=").append(steps)
            .append(" completed=").append(completed)
            .append(" final=").append(compact(finalCounts))
            .append(" min=").append(compact(minimum))
            .append(" extinctionStep=").append(firstExtinctionStep)
            .append(" extinctSpecies=").append(firstExtinctSpecies.isEmpty()
                ? "-" : firstExtinctSpecies)
            .append(" balance=").append(Double.isInfinite(balance) ? "inf"
                : String.format(java.util.Locale.ROOT, "%.2f", balance))
            .append(" balanceMean=").append(Double.isInfinite(balanceMean) ? "inf"
                : String.format(java.util.Locale.ROOT, "%.2f", balanceMean))
            .append(" maxOverloadSpan=").append(hotspots.getMaxOverloadSpan())
            .append(" regionLedger=").append(hotspots.ledgerCloses() ? "closed" : "OPEN")
            .append(" birthTrace=").append(birthsTraced ? "closed" : "OPEN")
            .append(" predatorFunding=").append(unfunded.isEmpty() ? "ok" : "EXCESS:" + unfunded)
            .append(" predatorBirthsKills=").append(birthTrace.predatorSummary())
            .append(" balanceNorm=").append(Double.isInfinite(balanceNorm) ? "inf"
                : String.format(java.util.Locale.ROOT, "%.2f", balanceNorm))
            .append(" gateRule=norm-v1")
            .append(" gatePoint=").append(passPoint ? "PASS" : "FAIL")
            .append(" gate=").append(pass ? "PASS" : "FAIL")
            .append(" elapsedMs=").append(elapsedMillis)
            .append(" stepMedianMs=").append(millis(percentile(measured, 0.50)))
            .append(" stepP95Ms=").append(millis(percentile(measured, 0.95)))
            .append(" paramsFull=").append(parameters.canonical())
            .append(" config=").append(config.describe().replace(' ', '_'))
            .append(" means=").append(means(windowSum, windowSamples))
            .append(" lnSd=").append(lnSd(windowLn, windowSamples))
            .append(" participants=").append(String.join(",", participants));
        System.out.println(line);
        if(diagnostics != null) {
            System.out.println(diagnostics.describe(seed, thirst, simulator.getContext()));
        }
        if(enforce && !pass) System.exit(1);
    }

    private static double balance(Map<String, Integer> counts)
    {
        int min = Integer.MAX_VALUE;
        int max = 0;
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            int count = counts.getOrDefault(factory.getProfile().getName(), 0);
            min = Math.min(min, count);
            max = Math.max(max, count);
        }
        return min <= 0 ? Double.POSITIVE_INFINITY : (double)max / min;
    }

    /** Largest over smallest per-species mean count in the window. */
    private static double meanBalance(Map<String, Long> sums, int samples)
    {
        double min = Double.POSITIVE_INFINITY;
        double max = 0.0;
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            double mean = (double)sums.getOrDefault(factory.getProfile().getName(), 0L) / samples;
            min = Math.min(min, mean);
            max = Math.max(max, mean);
        }
        return min <= 0.0 ? Double.POSITIVE_INFINITY : max / min;
    }

    /** A-5: per-species mean count over the gate window ({@code -} when empty). */
    private static String means(Map<String, Long> sums, int samples)
    {
        if(samples == 0) return "-";
        StringBuilder value = new StringBuilder();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String species = factory.getProfile().getName();
            if(value.length() > 0) value.append(',');
            value.append(species).append(':').append(String.format(java.util.Locale.ROOT,
                "%.1f", (double)sums.getOrDefault(species, 0L) / samples));
        }
        return value.toString();
    }

    /** A-5: per-species standard deviation of ln(count + 1) over the gate window. */
    private static String lnSd(Map<String, double[]> logSums, int samples)
    {
        if(samples == 0) return "-";
        StringBuilder value = new StringBuilder();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String species = factory.getProfile().getName();
            double[] ln = logSums.getOrDefault(species, new double[2]);
            double mean = ln[0] / samples;
            double variance = Math.max(0.0, ln[1] / samples - mean * mean);
            if(value.length() > 0) value.append(',');
            value.append(species).append(':').append(String.format(java.util.Locale.ROOT,
                "%.3f", Math.sqrt(variance)));
        }
        return value.toString();
    }

    private static String compact(Map<String, Integer> counts)
    {
        StringBuilder value = new StringBuilder();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String species = factory.getProfile().getName();
            if(value.length() > 0) value.append(',');
            value.append(species).append(':')
                .append(counts.getOrDefault(species, 0));
        }
        return value.toString();
    }

    private static long percentile(long[] sorted, double fraction)
    {
        if(sorted.length == 0) return 0L;
        int index = (int)Math.ceil(fraction * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static String millis(long nanos)
    {
        return String.format(java.util.Locale.ROOT, "%.1f", nanos / 1_000_000.0);
    }

    /**
     * G-ECO-5 (plan v3.0 §7 M3-2), always on. Birth traceability: the BIRTH
     * events equal the birth account's committed births, and the account's
     * pending food equals the escrow the living pregnant mothers still hold.
     * Predator funding: a predator's food comes only from kills (and D6
     * transfers inside its own species), so per predator species the food
     * reserved for its births cannot exceed the food value of its kills plus
     * the food its animals held at the start. The kill food is the prey's
     * food value, an upper bound on what was eaten (feeding caps at max).
     */
    static final class BirthTrace implements SimulationEventListener
    {
        private final Map<String, Integer> births = new TreeMap<>();
        private final Map<String, Integer> kills = new TreeMap<>();
        private final Map<String, Long> killFood = new TreeMap<>();
        private final Map<String, Long> initialFood = new TreeMap<>();

        BirthTrace(Field field)
        {
            for(Animal animal : field.getAnimals()) {
                if(animal instanceof SavannahAnimal) {
                    SavannahAnimal savannah = (SavannahAnimal)animal;
                    if(savannah.getProfile().isPredator()) {
                        initialFood.merge(savannah.getProfile().getName(),
                            (long)Math.max(0, savannah.getFoodLevel()), Long::sum);
                    }
                }
            }
        }

        @Override
        public void onEvent(SimulationEvent event)
        {
            if(event.type == SimulationEvent.EventType.BIRTH) {
                births.merge(event.targetSpecies, 1, Integer::sum);
            }
            else if(event.type == SimulationEvent.EventType.HUNT) {
                kills.merge(event.actorSpecies, 1, Integer::sum);
                SpeciesProfile prey = SpeciesRegistry.getProfile(event.targetSpecies);
                killFood.merge(event.actorSpecies,
                    (long)(prey == null ? 0 : prey.getFoodValue()), Long::sum);
            }
        }

        boolean traced(Field field, BirthResourceAccount account)
        {
            int birthEvents = 0;
            for(int count : births.values()) birthEvents += count;
            long escrow = 0L;
            for(Animal animal : field.getAnimals()) {
                if(!(animal instanceof SavannahAnimal) || !animal.isAlive()) continue;
                ReproductionState state = ((SavannahAnimal)animal).getReproductionState();
                for(Integer ordinal : state.getPendingOrdinals()) {
                    escrow += state.escrowOf(ordinal.intValue()).getFoodUnits();
                }
            }
            return birthEvents == account.getCommittedBirths() &&
                escrow == account.getPendingFood() && account.getPendingFood() >= 0;
        }

        /** Predator species whose birth reserves exceed kill food plus starting food. */
        String unfundedPredators()
        {
            StringBuilder text = new StringBuilder();
            for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
                SpeciesProfile profile = factory.getProfile();
                if(!profile.isPredator()) continue;
                long reserved = (long)births.getOrDefault(profile.getName(), 0) *
                    ReproductionPolicy.childReserveFood(profile);
                long available = killFood.getOrDefault(profile.getName(), 0L) +
                    initialFood.getOrDefault(profile.getName(), 0L);
                if(reserved > available) {
                    if(text.length() > 0) text.append(',');
                    text.append(profile.getName());
                }
            }
            return text.toString();
        }

        String predatorSummary()
        {
            StringBuilder text = new StringBuilder();
            for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
                SpeciesProfile profile = factory.getProfile();
                if(!profile.isPredator()) continue;
                if(text.length() > 0) text.append(',');
                text.append(profile.getName()).append(':')
                    .append(births.getOrDefault(profile.getName(), 0)).append('/')
                    .append(kills.getOrDefault(profile.getName(), 0));
            }
            return text.toString();
        }
    }

    /**
     * {@code --diag} run totals (M3-0): births per species, deaths by cause
     * split into run-born young (died before adult age) and the rest, kills
     * per predator and prey (young separately), reproduction proposal reasons
     * per species, D6 provisioning, M2 avoidance/egress moves and water queue
     * events. Observation only: it reads events and published frame ledgers.
     */
    static final class Diagnostics implements SimulationEventListener,
        StepFrameLedgerListener
    {
        private final Map<Long, Integer> birthStep = new HashMap<>();
        private final Map<Long, String> bornSpecies = new HashMap<>();
        private final Map<String, Integer> births = new TreeMap<>();
        private final Map<String, Map<DeathCause, Integer>> deaths = new TreeMap<>();
        private final Map<String, Map<DeathCause, Integer>> youngDeaths = new TreeMap<>();
        private final Map<String, Integer> kills = new TreeMap<>();
        private final Map<String, Integer> youngKills = new TreeMap<>();
        private final Map<String, Map<StepFrameLedger.ProposalReason, Integer>> reasons =
            new TreeMap<>();
        private int waterBlocked;
        private int waterRetarget;

        @Override
        public void onEvent(SimulationEvent event)
        {
            switch(event.type) {
                case BIRTH:
                    births.merge(event.targetSpecies, 1, Integer::sum);
                    birthStep.put(event.targetId, event.step);
                    bornSpecies.put(event.targetId, event.targetSpecies);
                    break;
                case HUNT:
                    String pair = event.actorSpecies + ">" + event.targetSpecies;
                    kills.merge(pair, 1, Integer::sum);
                    if(isYoung(event.targetId, event.step)) {
                        youngKills.merge(pair, 1, Integer::sum);
                    }
                    break;
                case DEATH:
                    Map<String, Map<DeathCause, Integer>> table =
                        isYoung(event.actorId, event.step) ? youngDeaths : deaths;
                    table.computeIfAbsent(event.actorSpecies,
                        key -> new EnumMap<>(DeathCause.class))
                        .merge(event.deathCause, 1, Integer::sum);
                    break;
                case WATER_BLOCKED:
                    waterBlocked++;
                    break;
                case WATER_RETARGET:
                    waterRetarget++;
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onStepFrameLedger(StepFrameLedger ledger)
        {
            StepFrameLedger.ReproductionProposal proposal = ledger.getReproductionProposal();
            if(proposal == null) return;
            reasons.computeIfAbsent(ledger.getActorSpecies(),
                key -> new EnumMap<>(StepFrameLedger.ProposalReason.class))
                .merge(proposal.getReason(), 1, Integer::sum);
        }

        /** Born during this run and younger than its species' adult age at {@code step}. */
        private boolean isYoung(long id, int step)
        {
            Integer born = birthStep.get(id);
            if(born == null) return false;
            SpeciesProfile profile = SpeciesRegistry.getProfile(bornSpecies.get(id));
            return profile != null && step - born < profile.getAdultAge();
        }

        String describe(long seed, boolean thirst, SimulationContext context)
        {
            StringBuilder line = new StringBuilder("ECO_DIAG");
            line.append(" seed=").append(seed)
                .append(" thirst=").append(thirst ? "on" : "off")
                .append(" births=").append(flat(births))
                .append(" deaths=").append(nested(deaths))
                .append(" youngDeaths=").append(nested(youngDeaths))
                .append(" kills=").append(flat(kills))
                .append(" youngKills=").append(flat(youngKills))
                .append(" reasons=").append(nested(reasons))
                .append(" provisions=").append(context.getBirthAccount().getProvisions())
                .append(" provisionedFood=").append(context.getBirthAccount().getProvisionedFood())
                .append(" social=").append(flat(new TreeMap<>(context.getSocialMoveCounts())))
                .append(" waterBlocked=").append(waterBlocked)
                .append(" waterRetarget=").append(waterRetarget);
            return line.toString();
        }

        private static String flat(Map<?, Integer> counts)
        {
            if(counts.isEmpty()) return "-";
            StringBuilder text = new StringBuilder();
            for(Map.Entry<?, Integer> entry : counts.entrySet()) {
                if(text.length() > 0) text.append(',');
                text.append(entry.getKey()).append(':').append(entry.getValue());
            }
            return text.toString();
        }

        private static String nested(Map<String, ? extends Map<?, Integer>> table)
        {
            if(table.isEmpty()) return "-";
            StringBuilder text = new StringBuilder();
            for(Map.Entry<String, ? extends Map<?, Integer>> entry : table.entrySet()) {
                if(text.length() > 0) text.append('|');
                text.append(entry.getKey()).append('[').append(flat(entry.getValue()))
                    .append(']');
            }
            return text.toString();
        }
    }
}
