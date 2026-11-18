package savanna;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Executes immutable TRUST V4 transparency or semantic ecology profiles. */
public final class VersionedEcologyGateRunner
{
    private VersionedEcologyGateRunner() {}

    public static void main(String[] args) throws Exception
    {
        RuntimeClasspathGuard.verifyOrThrow();
        if(args.length != 2) {
            throw new IllegalArgumentException(
                "usage: VersionedEcologyGateRunner <profile> <expected-sha256>");
        }
        Path path = Path.of(args[0]);
        byte[] bytes = Files.readAllBytes(path);
        String actualProfileHash = sha256(bytes);
        if(!actualProfileHash.equals(args[1])) {
            throw new IllegalStateException(
                "gate profile hash mismatch: expected=" + args[1] +
                " actual=" + actualProfileHash);
        }
        Map<String, String> profile = parse(bytes);
        require("trust-gate-profile-v1".equals(profile.get("schema")),
            "unsupported gate profile schema");
        BuildIdentity identity = BuildIdentity.current();
        require(identity.getSourceHash().equals(
            profile.get("required.source.hash")), "source hash mismatch");
        require(identity.getTestSourceHash().equals(
            profile.get("required.test.source.hash")),
            "test source hash mismatch");
        require(identity.getRuntimeTreeHash().equals(
            profile.get("required.runtime.tree.hash")),
            "runtime-tree hash mismatch");
        String configHash = ResolvedRuntimeConfig.fromLegacy(
            SimulationConfig.default3x()).getConfigurationHash();
        require(configHash.equals(profile.get("required.config.hash")),
            "configuration hash mismatch");

        String kind = required(profile, "profile.kind");
        int steps = integer(profile, "steps");
        int interval = integer(profile, "checkpoint.interval");
        long terrainSeed = Long.parseLong(required(profile, "terrain.seed"));
        String[] seeds = required(profile, "ecology.seeds").split(",");
        require(steps > 0 && interval > 0 && seeds.length > 0,
            "invalid gate run budget");
        if("TRANSPARENCY".equals(kind)) {
            require(steps == 1000 && interval == 100 &&
                terrainSeed == Pf1aProbeSupport.TERRAIN_SEED,
                "transparency profile must use the frozen D2 budget");
        }
        else if("SEMANTIC_ECOLOGY".equals(kind)) {
            require("true".equals(required(profile,
                "gate.all.species.alive")),
                "semantic profile must require all species alive");
            require("true".equals(required(profile,
                "gate.stop.on.first.failure")),
                "semantic profile must stop on first failure");
            require(integer(profile, "gate.retry.count") == 0,
                "semantic profile must forbid retries");
        }
        else {
            throw new IllegalStateException(
                "unsupported gate profile kind: " + kind);
        }
        System.out.println("ECOLOGY_GATE_START profile=" +
            required(profile, "profile.id") + " profileSha256=" +
            actualProfileHash + " kind=" + kind + " seeds=" +
            profile.get("ecology.seeds") + " terrainSeed=" + terrainSeed +
            " steps=" + steps + " retries=0");
        List<GateResult> results = new ArrayList<>();
        for(String seedText : seeds) {
            long seed = Long.parseLong(seedText);
            GateResult result = "TRANSPARENCY".equals(kind) ?
                runTransparency(seed, steps, interval) :
                runSemantic(seed, terrainSeed, steps, interval);
            results.add(result);
            boolean passed;
            if("TRANSPARENCY".equals(kind)) {
                passed = result.completed == steps && result.viable &&
                    result.ledgerClosed && result.frozenStepwiseExact &&
                    result.fingerprint.equals(required(profile,
                        "expected." + seed + ".fingerprint")) &&
                    counts(result.counts).equals(required(profile,
                        "expected." + seed + ".counts"));
            }
            else {
                Map<String, Integer> reference = parseCounts(required(profile,
                    "reference." + seed + ".counts"));
                double maximum = Math.max(decimal(profile,
                    "gate.per.seed.balance.base.max"), balance(reference) *
                    decimal(profile, "gate.per.seed.balance.r0.factor"));
                int floor = integer(profile, "gate.species.final.floor");
                passed = result.completed == steps && result.viable &&
                    result.ledgerClosed &&
                    result.balance <= maximum &&
                    allSpeciesAtLeast(result.counts, floor) &&
                    withinPerSpeciesRatio(result.counts, reference,
                        decimal(profile, "gate.single.seed.count.ratio.min"),
                        decimal(profile, "gate.single.seed.count.ratio.max"));
            }
            System.out.println("ECOLOGY_GATE_RESULT seed=" + seed +
                " completed=" + result.completed + " viable=" +
                result.viable + " balance=" +
                String.format(Locale.ROOT, "%.2f", result.balance) +
                " counts=" + counts(result.counts) + " fingerprint=" +
                result.fingerprint + " ledgerClosed=" +
                result.ledgerClosed + " result=" +
                (passed ? "PASS" : "FAIL"));
            if(!passed) {
                System.out.println("ECOLOGY_GATE_STOP firstFailureSeed=" + seed +
                    " retryCount=0");
                System.exit(1);
            }
        }
        if("SEMANTIC_ECOLOGY".equals(kind) &&
           !withinMedianRatios(results, profile, seeds)) {
            System.out.println("ECOLOGY_GATE_STOP matrixMedianFailure=true " +
                "retryCount=0");
            System.exit(1);
        }
        System.out.println("ECOLOGY_GATE_PASS profile=" +
            required(profile, "profile.id"));
    }

    private static GateResult runTransparency(long seed, int steps,
                                              int interval)
    {
        require(seed == 1111L || seed == 2111L,
            "transparency seed is not frozen");
        List<String> frozen = Pf1aProbeSupport.loadFrozenReference(seed);
        Pf1aProbeSupport.RunTrace trace =
            Pf1aProbeSupport.run(seed, steps, false, false);
        trace.requireComplete();
        boolean exact = frozen.equals(trace.fingerprints);
        for(int step = interval; step <= steps; step += interval) {
            System.out.println("ECOLOGY_GATE_CHECKPOINT seed=" + seed +
                " step=" + step + " fingerprint=" +
                trace.fingerprints.get(step - 1));
        }
        Map<String, Integer> finalCounts = orderedCounts(trace.finalCounts);
        return new GateResult(trace.completedSteps, trace.viable, finalCounts,
            balance(finalCounts), trace.fingerprints.get(steps - 1), true,
            exact);
    }

    private static GateResult runSemantic(long seed, long terrainSeed,
                                          int steps, int interval)
    {
        Randomizer.reset();
        Simulator simulator = new Simulator(SimulationConfig.builder()
            .randomSeed(seed).terrainSeed(terrainSeed).build(), false);
        simulator.setVerbose(false);
        Map<String, Integer> initialCounts = orderedCounts(
            simulator.getField().countLivingBySpecies());
        ReconciliationLedger ledger = new ReconciliationLedger(initialCounts);
        simulator.addEventListener(ledger);
        for(int index = 1; index <= steps && simulator.getField().isViable();
            index++) {
            simulator.simulateOneStep();
            if(index % interval == 0) {
                System.out.println("ECOLOGY_GATE_CHECKPOINT seed=" + seed +
                    " step=" + index + " fingerprint=" +
                    SimulationFingerprint.capture(simulator));
            }
        }
        Map<String, Integer> finalCounts =
            orderedCounts(simulator.getField().countLivingBySpecies());
        return new GateResult(simulator.getStep(),
            simulator.getField().isViable(), finalCounts,
            balance(finalCounts), SimulationFingerprint.capture(simulator),
            ledger.reconciles(finalCounts), false);
    }

    private static boolean withinMedianRatios(List<GateResult> results,
                                               Map<String, String> profile,
                                               String[] seeds)
    {
        double minimum = decimal(profile,
            "gate.five.seed.median.count.ratio.min");
        double maximum = decimal(profile,
            "gate.five.seed.median.count.ratio.max");
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String species = factory.getProfile().getName();
            List<Integer> observed = new ArrayList<>();
            List<Integer> reference = new ArrayList<>();
            for(int index = 0; index < seeds.length; index++) {
                observed.add(results.get(index).counts.get(species));
                reference.add(parseCounts(required(profile,
                    "reference." + seeds[index] + ".counts")).get(species));
            }
            Collections.sort(observed);
            Collections.sort(reference);
            double ratio = median(observed) / median(reference);
            if(ratio < minimum || ratio > maximum) return false;
        }
        return true;
    }

    private static double median(List<Integer> values)
    {
        int middle = values.size() / 2;
        return values.size() % 2 == 1 ? values.get(middle) :
            (values.get(middle - 1) + values.get(middle)) / 2.0;
    }

    private static boolean withinPerSpeciesRatio(
        Map<String, Integer> observed, Map<String, Integer> reference,
        double minimum, double maximum)
    {
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String species = factory.getProfile().getName();
            double ratio = (double)observed.getOrDefault(species, 0) /
                reference.getOrDefault(species, 0);
            if(ratio < minimum || ratio > maximum) return false;
        }
        return true;
    }

    private static boolean allSpeciesAtLeast(Map<String, Integer> counts,
                                             int floor)
    {
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            if(counts.getOrDefault(factory.getProfile().getName(), 0) < floor) {
                return false;
            }
        }
        return true;
    }

    private static double balance(Map<String, Integer> counts)
    {
        int minimum = Integer.MAX_VALUE;
        int maximum = 0;
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            int count = counts.getOrDefault(factory.getProfile().getName(), 0);
            minimum = Math.min(minimum, count);
            maximum = Math.max(maximum, count);
        }
        return minimum <= 0 ? Double.POSITIVE_INFINITY :
            (double)maximum / minimum;
    }

    private static Map<String, String> parse(byte[] bytes)
    {
        Map<String, String> result = new LinkedHashMap<>();
        List<String> lines = new String(bytes, StandardCharsets.UTF_8).lines()
            .toList();
        for(String line : lines) {
            if(line.isBlank() || line.startsWith("#")) continue;
            int separator = line.indexOf('=');
            require(separator > 0 && separator < line.length() - 1,
                "malformed profile row: " + line);
            String key = line.substring(0, separator);
            require(result.put(key, line.substring(separator + 1)) == null,
                "duplicate profile key: " + key);
        }
        return result;
    }

    private static String required(Map<String, String> values, String key)
    {
        String value = values.get(key);
        require(value != null && !value.isBlank(),
            "missing profile key: " + key);
        return value;
    }

    private static int integer(Map<String, String> values, String key)
    {
        return Integer.parseInt(required(values, key));
    }

    private static double decimal(Map<String, String> values, String key)
    {
        double value = Double.parseDouble(required(values, key));
        require(Double.isFinite(value) && value > 0.0,
            "profile decimal must be positive and finite: " + key);
        return value;
    }

    private static Map<String, Integer> parseCounts(String value)
    {
        Map<String, Integer> result = new LinkedHashMap<>();
        for(String token : value.split(",")) {
            int separator = token.indexOf(':');
            require(separator > 0 && separator < token.length() - 1,
                "malformed species count: " + token);
            String species = token.substring(0, separator);
            int count = Integer.parseInt(token.substring(separator + 1));
            require(SpeciesRegistry.getProfile(species) != null && count > 0 &&
                result.put(species, count) == null,
                "invalid or duplicate species count: " + token);
        }
        require(result.size() == SpeciesRegistry.getFactories().size(),
            "species count set is incomplete");
        return result;
    }

    private static Map<String, Integer> orderedCounts(
        Map<String, Integer> source)
    {
        Map<String, Integer> result = new LinkedHashMap<>();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String species = factory.getProfile().getName();
            result.put(species, source.getOrDefault(species, 0));
        }
        return result;
    }

    private static String counts(Map<String, Integer> values)
    {
        StringBuilder result = new StringBuilder();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            if(result.length() > 0) result.append(',');
            String species = factory.getProfile().getName();
            result.append(species).append(':')
                .append(values.getOrDefault(species, 0));
        }
        return result.toString();
    }

    private static String sha256(byte[] bytes)
    {
        try {
            StringBuilder result = new StringBuilder();
            for(byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return result.toString();
        }
        catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void require(boolean condition, String message)
    {
        if(!condition) throw new IllegalStateException(message);
    }

    private static final class GateResult
    {
        private final int completed;
        private final boolean viable;
        private final Map<String, Integer> counts;
        private final double balance;
        private final String fingerprint;
        private final boolean ledgerClosed;
        private final boolean frozenStepwiseExact;

        private GateResult(int completed, boolean viable,
                           Map<String, Integer> counts, double balance,
                           String fingerprint, boolean ledgerClosed,
                           boolean frozenStepwiseExact)
        {
            this.completed = completed;
            this.viable = viable;
            this.counts = counts;
            this.balance = balance;
            this.fingerprint = fingerprint;
            this.ledgerClosed = ledgerClosed;
            this.frozenStepwiseExact = frozenStepwiseExact;
        }
    }

    private static final class ReconciliationLedger
        implements SimulationEventListener
    {
        private final Map<String, Integer> expected;

        private ReconciliationLedger(Map<String, Integer> initial)
        {
            expected = new LinkedHashMap<>(initial);
        }

        @Override
        public void onEvent(SimulationEvent event)
        {
            if(event.type == SimulationEvent.EventType.BIRTH) {
                adjust(event.targetSpecies, 1);
            }
            else if(event.type == SimulationEvent.EventType.DEATH ||
                    event.type == SimulationEvent.EventType.DISEASE_DEATH) {
                adjust(event.actorSpecies, -1);
            }
        }

        private void adjust(String species, int delta)
        {
            require(SpeciesRegistry.getProfile(species) != null,
                "ledger event has unknown species: " + species);
            expected.put(species, expected.getOrDefault(species, 0) + delta);
        }

        private boolean reconciles(Map<String, Integer> finalCounts)
        {
            return expected.equals(finalCounts);
        }
    }
}
