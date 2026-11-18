package savanna;

import com.sun.management.ThreadMXBean;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Fixed-contract V2.7 diagnostics only.  This is deliberately not a general
 * experiment runner: it accepts the R1 compatibility workload and writes a
 * self-describing evidence directory for benchmark, equivalence, or regional
 * observation runs.
 */
public final class V27DiagnosticsTool
{
    private static final String WORKER_MARKER = "V27_WORKER_RESULT ";
    private static final int FIXED_REPEATS = 3;

    private V27DiagnosticsTool()
    {
    }

    public static void main(String[] arguments)
    {
        try {
            execute(arguments);
        }
        catch(IllegalArgumentException e) {
            System.err.println("V27 diagnostics rejected: " + e.getMessage());
            System.exit(2);
        }
        catch(Exception e) {
            System.err.println("V27 diagnostics failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static void execute(String[] arguments) throws Exception
    {
        if(arguments != null && arguments.length > 0 && "__worker".equals(arguments[0])) {
            runWorker(arguments);
            return;
        }
        Options options = Options.parse(arguments);
        options.validate();
        Files.createDirectory(options.output);
        runAndWriteEvidence(options);
    }

    private static void runAndWriteEvidence(Options options) throws Exception
    {
        Properties currentIdentity = buildIdentity();
        Properties referenceIdentity = options.referenceJar == null
            ? new Properties() : jarIdentity(options.referenceJar);
        String canonical = options.canonicalConfiguration();
        String configurationHash = sha256(canonical);
        write(options.output.resolve("COMMAND.txt"), options.displayCommand());
        write(options.output.resolve("RESOLVED_CONFIG.txt"), canonical + '\n');
        write(options.output.resolve("INPUT_IDENTITY.txt"), inputIdentity(
            options, currentIdentity, referenceIdentity));

        Result result;
        if(options.mode == Mode.BENCHMARK) {
            result = benchmark(options);
        }
        else if(options.mode == Mode.EQUIVALENCE) {
            result = equivalence(options);
        }
        else {
            result = regional(options);
        }
        result.sourceHash = property(currentIdentity, "source.hash");
        result.runtimeHash = property(currentIdentity, "runtime.tree.hash");
        result.configurationHash = configurationHash;
        result.referenceRuntimeHash = property(referenceIdentity,
            "runtime.tree.hash");
        write(options.output.resolve("RESULT.json"), result.toJson(options));
        write(options.output.resolve("SUMMARY.md"), result.summary(options));
        if(!result.passed) {
            throw new IllegalStateException(result.failure);
        }
    }

    private static Result benchmark(Options options) throws Exception
    {
        List<WorkerResult> current = new ArrayList<>();
        List<WorkerResult> reference = new ArrayList<>();
        for(int repeat = 1; repeat <= FIXED_REPEATS; repeat++) {
            current.add(runWorkerProcess(options, false, false, repeat));
            reference.add(runWorkerProcess(options, true, false, repeat));
        }
        Result result = Result.success("benchmark");
        result.current = current;
        result.reference = reference;
        for(int index = 0; index < current.size(); index++) {
            WorkerResult actual = current.get(index);
            WorkerResult baseline = reference.get(index);
            if(!actual.comparableTo(baseline)) {
                return Result.failure("benchmark", "Compatibility state differs in repeat " +
                    (index + 1) + ".", current, reference);
            }
        }
        Metrics currentMetrics = Metrics.of(current);
        Metrics referenceMetrics = Metrics.of(reference);
        result.currentMetrics = currentMetrics;
        result.referenceMetrics = referenceMetrics;
        result.performanceTargetMet = currentMetrics.p95Nanos * 3L <=
            referenceMetrics.p95Nanos && currentMetrics.allocatedBytes * 5L <=
            referenceMetrics.allocatedBytes;
        return result;
    }

    private static Result equivalence(Options options) throws Exception
    {
        WorkerResult current = runWorkerProcess(options, false, true, 1);
        WorkerResult reference = runWorkerProcess(options, true, true, 1);
        if(!current.comparableTo(reference) || !current.trace.equals(reference.trace)) {
            return Result.failure("equivalence",
                "State, terrain, event, or random trace differs.",
                Collections.singletonList(current), Collections.singletonList(reference));
        }
        Result result = Result.success("equivalence");
        result.current = Collections.singletonList(current);
        result.reference = Collections.singletonList(reference);
        result.currentMetrics = Metrics.of(result.current);
        result.referenceMetrics = Metrics.of(result.reference);
        result.performanceTargetMet = null;
        return result;
    }

    private static Result regional(Options options) throws Exception
    {
        WorkerResult current = runWorkerProcess(options, false, true, 1);
        Result result = Result.success("regional");
        result.current = Collections.singletonList(current);
        result.currentMetrics = Metrics.of(result.current);
        result.regionalCounts = current.regionalCounts;
        result.performanceTargetMet = null;
        return result;
    }

    private static WorkerResult runWorkerProcess(Options options,
                                                 boolean referenceFirst,
                                                 boolean trace, int repeat)
        throws Exception
    {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Djava.awt.headless=true");
        command.add("-cp");
        String classpath = System.getProperty("java.class.path");
        if(referenceFirst) classpath = options.referenceJar +
            System.getProperty("path.separator") + classpath;
        command.add(classpath);
        command.add(V27DiagnosticsTool.class.getName());
        command.add("__worker");
        command.add("--ecology-seed");
        command.add(Long.toString(options.ecologySeed));
        command.add("--terrain-seed");
        command.add(Long.toString(options.terrainSeed));
        command.add("--thirst");
        command.add(options.thirst);
        command.add("--warmup-steps");
        command.add(Integer.toString(options.warmupSteps));
        command.add("--measure-steps");
        command.add(Integer.toString(options.measureSteps));
        command.add("--trace");
        command.add(trace ? "on" : "off");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String console = new String(process.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        int exit = process.waitFor();
        Path log = options.output.resolve((referenceFirst ? "reference" : "current") +
            "-repeat-" + repeat + ".log");
        write(log, console);
        if(exit != 0) {
            throw new IllegalStateException("Worker process failed for " +
                (referenceFirst ? "reference" : "current") + " repeat " +
                repeat + ": exit=" + exit);
        }
        int marker = console.lastIndexOf(WORKER_MARKER);
        if(marker < 0) {
            throw new IllegalStateException("Worker process omitted its result marker.");
        }
        int end = console.indexOf('\n', marker);
        String encoded = (end < 0 ? console.substring(marker + WORKER_MARKER.length()) :
            console.substring(marker + WORKER_MARKER.length(), end)).trim();
        return WorkerResult.parse(new String(Base64.getUrlDecoder().decode(encoded),
            StandardCharsets.UTF_8));
    }

    private static void runWorker(String[] arguments) throws Exception
    {
        Map<String, String> values = workerArguments(arguments);
        long ecologySeed = positiveSeed(values.get("--ecology-seed"));
        long terrainSeed = positiveSeed(values.get("--terrain-seed"));
        boolean thirst = "on".equals(values.get("--thirst"));
        int warmup = positiveSteps(values.get("--warmup-steps"));
        int measure = positiveSteps(values.get("--measure-steps"));
        boolean trace = "on".equals(values.get("--trace"));
        // Use the public legacy constructor because it is deliberately shared
        // by the locked reference JAR and the current R1 compatibility path.
        SimulationConfig config = new SimulationConfig(3, 1.0 / 9.0, 1.0, 0.58,
            1.75, 1.75, 2.60, 0.75, 2.00, 0.85, 2.00, 0.55,
            ecologySeed, terrainSeed, thirst);
        Randomizer.reset(ecologySeed);
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        for(int index = 0; index < warmup; index++) {
            requireViable(simulator, index, "warmup");
            simulator.simulateOneStep();
        }
        ThreadMXBean allocationBean = allocationBean();
        long allocationStart = allocatedBytes(allocationBean);
        long gcCountStart = gcCount();
        long gcMillisStart = gcMillis();
        List<Long> samples = new ArrayList<>();
        List<String> stateTrace = new ArrayList<>();
        long elapsedStart = System.nanoTime();
        for(int index = 0; index < measure; index++) {
            requireViable(simulator, index, "measurement");
            long before = System.nanoTime();
            simulator.simulateOneStep();
            samples.add(System.nanoTime() - before);
            if(trace) stateTrace.add(stepDigest(simulator));
        }
        long elapsed = System.nanoTime() - elapsedStart;
        long allocationEnd = allocatedBytes(allocationBean);
        WorkerResult result = new WorkerResult();
        // RESULT.json reports the requested measurement interval, not the
        // preceding warmup.  Reaching here proves every requested measured
        // step completed without an extinction short-cut.
        result.completedSteps = measure;
        result.measuredSteps = measure;
        result.elapsedNanos = elapsed;
        result.p95Nanos = percentile(samples, 0.95d);
        result.maxNanos = Collections.max(samples);
        result.allocatedBytes = allocationStart < 0L || allocationEnd < 0L
            ? -1L : Math.max(0L, allocationEnd - allocationStart);
        result.gcCount = gcCount() - gcCountStart;
        result.gcMillis = gcMillis() - gcMillisStart;
        result.stateDigest = stateDigest(simulator);
        result.terrainFingerprint = simulator.getContext().getTerrainMap().getFingerprint();
        result.randomBoundary = Long.toUnsignedString(Randomizer.getRandom().nextLong());
        result.eventDigest = eventDigest(simulator.getContext().getCurrentEvents());
        result.trace = stateTrace;
        result.regionalCounts = regionCounts(simulator);
        System.out.println(WORKER_MARKER + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(result.serialize().getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<String, String> workerArguments(String[] arguments)
    {
        if(arguments.length != 13) throw new IllegalArgumentException(
            "worker requires exactly six key/value arguments");
        Map<String, String> values = new LinkedHashMap<>();
        for(int index = 1; index < arguments.length; index += 2) {
            if(values.put(arguments[index], arguments[index + 1]) != null) {
                throw new IllegalArgumentException("duplicate worker option: " + arguments[index]);
            }
        }
        String[] required = {"--ecology-seed", "--terrain-seed", "--thirst",
            "--warmup-steps", "--measure-steps", "--trace"};
        for(String key : required) if(!values.containsKey(key)) {
            throw new IllegalArgumentException("missing worker option: " + key);
        }
        if(!"on".equals(values.get("--thirst")) && !"off".equals(values.get("--thirst"))) {
            throw new IllegalArgumentException("worker thirst must be on or off");
        }
        if(!"on".equals(values.get("--trace")) && !"off".equals(values.get("--trace"))) {
            throw new IllegalArgumentException("worker trace must be on or off");
        }
        return values;
    }

    private static void requireViable(Simulator simulator, int index, String phase)
    {
        if(!simulator.getField().isViable()) {
            throw new IllegalStateException("Simulation became non-viable during " +
                phase + " at local step " + index + ".");
        }
    }

    private static String stepDigest(Simulator simulator)
    {
        return sha256(stateDigest(simulator) + "|events=" + eventDigest(
            simulator.getContext().getCurrentEvents()) + "|random=" +
            randomDigest(simulator.getContext().getRandom()));
    }

    private static String stateDigest(Simulator simulator)
    {
        List<Animal> animals = new ArrayList<>(simulator.getField().getAnimals());
        animals.sort(Comparator.comparingLong(Animal::getId));
        StringBuilder value = new StringBuilder();
        value.append("step=").append(simulator.getStep()).append('|');
        value.append("terrain=").append(simulator.getContext().getTerrainMap()
            .getFingerprint()).append('|');
        value.append("deaths=").append(simulator.getContext().getDeathSummary()).append('|');
        for(Animal animal : animals) {
            value.append(animal.getId()).append('|').append(animal.isAlive()).append('|')
                .append(animal.getDeathCause()).append('|').append(animal.getLocation()).append('|');
            if(animal instanceof SavannahAnimal) {
                SavannahAnimal savannah = (SavannahAnimal)animal;
                value.append(savannah.getProfile().getName()).append('|')
                    .append(savannah.getSex()).append('|').append(savannah.getAge()).append('|')
                    .append(savannah.getFoodLevel()).append('|')
                    .append(Double.doubleToLongBits(savannah.getStaminaPercent())).append('|')
                    .append(Double.doubleToLongBits(savannah.getHydrationPercent())).append('|')
                    .append(savannah.getStarvingSteps()).append('|')
                    .append(savannah.getDehydrationSteps()).append('|')
                    .append(savannah.isInfected()).append('|')
                    .append(Double.doubleToLongBits(savannah.getInfectionLevel())).append('|')
                    .append(savannah.getLastBehaviorAction()).append('|')
                    .append(savannah.getLastBehaviorReason()).append('|');
            }
        }
        return sha256(value.toString());
    }

    private static String eventDigest(List<SimulationEvent> events)
    {
        StringBuilder value = new StringBuilder();
        for(SimulationEvent event : events) {
            value.append(event.type).append('|').append(event.step).append('|')
                .append(event.row).append('|').append(event.col).append('|')
                .append(event.fromRow).append('|').append(event.fromCol).append('|')
                .append(event.actorId).append('|').append(event.targetId).append('|')
                .append(event.actorSpecies).append('|').append(event.targetSpecies).append('|')
                .append(event.deathCause).append('\n');
        }
        return sha256(value.toString());
    }

    private static String randomDigest(java.util.Random random)
    {
        // Random has no non-mutating public state accessor.  The final nextLong
        // boundary is compared separately; this identity deliberately avoids
        // consuming a value while tracing each measured step.
        return random == null ? "ABSENT" : "PRESENT";
    }

    private static Map<String, Integer> regionCounts(Simulator simulator)
    {
        int depth = simulator.getField().getDepth();
        int width = simulator.getField().getWidth();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for(int row = 0; row < 3; row++) for(int col = 0; col < 3; col++) {
            counts.put("r" + row + "c" + col, 0);
        }
        for(Animal animal : simulator.getField().getAnimals()) {
            if(!animal.isAlive() || animal.getLocation() == null) continue;
            int row = Math.min(2, animal.getLocation().row() * 3 / depth);
            int col = Math.min(2, animal.getLocation().col() * 3 / width);
            String key = "r" + row + "c" + col;
            counts.put(key, counts.get(key) + 1);
        }
        return counts;
    }

    private static ThreadMXBean allocationBean()
    {
        java.lang.management.ThreadMXBean base = ManagementFactory.getThreadMXBean();
        if(!(base instanceof ThreadMXBean)) return null;
        ThreadMXBean bean = (ThreadMXBean)base;
        if(!bean.isThreadAllocatedMemorySupported()) return null;
        if(!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        return bean;
    }

    private static long allocatedBytes(ThreadMXBean bean)
    {
        return bean == null ? -1L : bean.getThreadAllocatedBytes(
            Thread.currentThread().getId());
    }

    private static long gcCount()
    {
        long total = 0L;
        for(GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if(bean.getCollectionCount() > 0L) total += bean.getCollectionCount();
        }
        return total;
    }

    private static long gcMillis()
    {
        long total = 0L;
        for(GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if(bean.getCollectionTime() > 0L) total += bean.getCollectionTime();
        }
        return total;
    }

    private static long percentile(List<Long> values, double percentile)
    {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = Math.min(sorted.size() - 1, (int)Math.ceil(percentile *
            sorted.size()) - 1);
        return sorted.get(index);
    }

    private static Properties buildIdentity() throws IOException
    {
        try(InputStream stream = V27DiagnosticsTool.class.getClassLoader()
            .getResourceAsStream("META-INF/savanna-build.properties")) {
            if(stream == null) return new Properties();
            Properties value = new Properties();
            value.load(stream);
            return value;
        }
    }

    private static Properties jarIdentity(Path jar) throws IOException
    {
        try(java.util.jar.JarFile archive = new java.util.jar.JarFile(jar.toFile())) {
            java.util.jar.JarEntry entry = archive.getJarEntry(
                "META-INF/savanna-build.properties");
            if(entry == null) return new Properties();
            try(InputStream stream = archive.getInputStream(entry)) {
                Properties value = new Properties();
                value.load(stream);
                return value;
            }
        }
    }

    private static String inputIdentity(Options options, Properties current,
                                        Properties reference) throws IOException
    {
        return "current.source.hash=" + property(current, "source.hash") + '\n' +
            "current.runtime.tree.hash=" + property(current, "runtime.tree.hash") + '\n' +
            "reference.jar=" + (options.referenceJar == null ? "NONE" : options.referenceJar) + '\n' +
            "reference.jar.sha256=" + (options.referenceJar == null ? "NOT_APPLICABLE" :
                sha256(options.referenceJar)) + '\n' +
            "reference.source.hash=" + property(reference, "source.hash") + '\n' +
            "reference.runtime.tree.hash=" + property(reference, "runtime.tree.hash") + '\n';
    }

    private static String property(Properties values, String key)
    {
        return values.getProperty(key, "UNAVAILABLE");
    }

    private static String sha256(Path file) throws IOException
    {
        return sha256(Files.readAllBytes(file));
    }

    private static String sha256(String value)
    {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes)
    {
        try {
            return HexEncoding.lowercase(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch(NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private static void write(Path output, String text) throws IOException
    {
        Files.writeString(output, text, StandardCharsets.UTF_8);
    }

    private static long positiveSeed(String raw)
    {
        try {
            long result = Long.parseLong(raw);
            if(result < 0L) throw new IllegalArgumentException("seed must be >= 0");
            return result;
        }
        catch(NumberFormatException e) {
            throw new IllegalArgumentException("seed must be a decimal long: " + raw);
        }
    }

    private static int positiveSteps(String raw)
    {
        try {
            int result = Integer.parseInt(raw);
            if(result < 1) throw new IllegalArgumentException("steps must be >= 1");
            return result;
        }
        catch(NumberFormatException e) {
            throw new IllegalArgumentException("steps must be an integer: " + raw);
        }
    }

    enum Mode { BENCHMARK, EQUIVALENCE, REGIONAL }

    static final class Options
    {
        final Mode mode;
        final Path referenceJar;
        final String preset;
        final long ecologySeed;
        final String terrainProfile;
        final long terrainSeed;
        final String thirst;
        final int warmupSteps;
        final int measureSteps;
        final int repeats;
        final Path output;

        private Options(Mode mode, Path referenceJar, String preset,
                        long ecologySeed, String terrainProfile, long terrainSeed,
                        String thirst, int warmupSteps, int measureSteps,
                        int repeats, Path output)
        {
            this.mode = mode;
            this.referenceJar = referenceJar;
            this.preset = preset;
            this.ecologySeed = ecologySeed;
            this.terrainProfile = terrainProfile;
            this.terrainSeed = terrainSeed;
            this.thirst = thirst;
            this.warmupSteps = warmupSteps;
            this.measureSteps = measureSteps;
            this.repeats = repeats;
            this.output = output;
        }

        static Options parse(String[] arguments)
        {
            if(arguments == null || arguments.length < 1) throw new IllegalArgumentException(
                "usage: V27DiagnosticsTool <benchmark|equivalence|regional> options");
            Mode mode;
            try {
                mode = Mode.valueOf(arguments[0].toUpperCase());
            }
            catch(IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown mode: " + arguments[0]);
            }
            Map<String, String> values = new LinkedHashMap<>();
            for(int index = 1; index < arguments.length; index += 2) {
                if(index + 1 >= arguments.length || !arguments[index].startsWith("--")) {
                    throw new IllegalArgumentException("options must be --name value pairs");
                }
                if(values.put(arguments[index], arguments[index + 1]) != null) {
                    throw new IllegalArgumentException("duplicate option: " + arguments[index]);
                }
            }
            for(String key : values.keySet()) {
                if(!"--reference-jar".equals(key) && !"--preset".equals(key) &&
                   !"--ecology-seed".equals(key) && !"--terrain-profile".equals(key) &&
                   !"--terrain-seed".equals(key) && !"--thirst".equals(key) &&
                   !"--warmup-steps".equals(key) && !"--measure-steps".equals(key) &&
                   !"--repeats".equals(key) && !"--out".equals(key)) {
                    throw new IllegalArgumentException("unknown option: " + key);
                }
            }
            String[] required = {"--preset", "--ecology-seed", "--terrain-profile",
                "--terrain-seed", "--thirst", "--warmup-steps", "--measure-steps", "--out"};
            for(String key : required) if(!values.containsKey(key)) {
                throw new IllegalArgumentException("missing required option: " + key);
            }
            if(mode != Mode.REGIONAL && !values.containsKey("--reference-jar")) {
                throw new IllegalArgumentException("missing required option: --reference-jar");
            }
            int repeats = values.containsKey("--repeats")
                ? positiveSteps(values.get("--repeats")) : 1;
            return new Options(mode, values.containsKey("--reference-jar")
                ? Path.of(values.get("--reference-jar")).toAbsolutePath().normalize() : null,
                values.get("--preset"), positiveSeed(values.get("--ecology-seed")),
                values.get("--terrain-profile"), positiveSeed(values.get("--terrain-seed")),
                values.get("--thirst"), positiveSteps(values.get("--warmup-steps")),
                positiveSteps(values.get("--measure-steps")), repeats,
                Path.of(values.get("--out")).toAbsolutePath().normalize());
        }

        void validate()
        {
            if(!"default3x".equals(preset)) throw new IllegalArgumentException(
                "R1 diagnostics only accepts preset default3x");
            if(!"LEGACY_V1".equals(terrainProfile)) throw new IllegalArgumentException(
                "R1 compatibility diagnostics only accepts terrain profile LEGACY_V1");
            if(!"on".equals(thirst) && !"off".equals(thirst)) {
                throw new IllegalArgumentException("thirst must be on or off");
            }
            if(mode == Mode.BENCHMARK && repeats != FIXED_REPEATS) {
                throw new IllegalArgumentException("benchmark repeats must be exactly " +
                    FIXED_REPEATS);
            }
            if(mode != Mode.BENCHMARK && repeats != 1) {
                throw new IllegalArgumentException("only benchmark accepts --repeats");
            }
            if(mode != Mode.REGIONAL && (referenceJar == null || !Files.isRegularFile(referenceJar))) {
                throw new IllegalArgumentException("reference JAR is required and must be a file");
            }
            if(Files.exists(output)) throw new IllegalArgumentException(
                "output directory already exists: " + output);
        }

        String canonicalConfiguration()
        {
            return "diagnostic.mode=" + mode + '\n' +
                "ecology.preset=" + preset + '\n' +
                "ecology.seed=" + ecologySeed + '\n' +
                "ecology.terrainProfile=" + terrainProfile + '\n' +
                "ecology.terrainSeed=" + terrainSeed + '\n' +
                "ecology.thirst=" + thirst + '\n' +
                "benchmark.warmupSteps=" + warmupSteps + '\n' +
                "benchmark.measureSteps=" + measureSteps + '\n' +
                "benchmark.repeats=" + repeats + '\n';
        }

        String displayCommand()
        {
            String value = "V27DiagnosticsTool " + mode.name().toLowerCase() +
                " --reference-jar " + (referenceJar == null ? "NONE" : referenceJar) +
                " --preset " + preset + " --ecology-seed " + ecologySeed +
                " --terrain-profile " + terrainProfile + " --terrain-seed " + terrainSeed +
                " --thirst " + thirst + " --warmup-steps " + warmupSteps +
                " --measure-steps " + measureSteps;
            if(mode == Mode.BENCHMARK) value += " --repeats " + repeats;
            return value + " --out " + output + '\n';
        }
    }

    private static final class WorkerResult
    {
        int completedSteps;
        int measuredSteps;
        long elapsedNanos;
        long p95Nanos;
        long maxNanos;
        long allocatedBytes;
        long gcCount;
        long gcMillis;
        String stateDigest;
        String terrainFingerprint;
        String randomBoundary;
        String eventDigest;
        List<String> trace;
        Map<String, Integer> regionalCounts;

        boolean comparableTo(WorkerResult other)
        {
            return other != null && completedSteps == other.completedSteps &&
                measuredSteps == other.measuredSteps && stateDigest.equals(other.stateDigest) &&
                terrainFingerprint.equals(other.terrainFingerprint) &&
                randomBoundary.equals(other.randomBoundary) && eventDigest.equals(other.eventDigest);
        }

        String serialize()
        {
            StringBuilder value = new StringBuilder();
            put(value, "completed", completedSteps);
            put(value, "measured", measuredSteps);
            put(value, "elapsed", elapsedNanos);
            put(value, "p95", p95Nanos);
            put(value, "max", maxNanos);
            put(value, "allocated", allocatedBytes);
            put(value, "gcCount", gcCount);
            put(value, "gcMillis", gcMillis);
            put(value, "state", stateDigest);
            put(value, "terrain", terrainFingerprint);
            put(value, "random", randomBoundary);
            put(value, "events", eventDigest);
            put(value, "trace", String.join(",", trace));
            List<String> regions = new ArrayList<>();
            for(Map.Entry<String, Integer> entry : regionalCounts.entrySet()) {
                regions.add(entry.getKey() + ':' + entry.getValue());
            }
            put(value, "regions", String.join(",", regions));
            return value.toString();
        }

        static WorkerResult parse(String text)
        {
            Map<String, String> values = new LinkedHashMap<>();
            for(String line : text.split("\\n")) {
                int separator = line.indexOf('=');
                if(separator <= 0 || values.put(line.substring(0, separator),
                    line.substring(separator + 1)) != null) {
                    throw new IllegalArgumentException("malformed worker result");
                }
            }
            WorkerResult result = new WorkerResult();
            result.completedSteps = Integer.parseInt(require(values, "completed"));
            result.measuredSteps = Integer.parseInt(require(values, "measured"));
            result.elapsedNanos = Long.parseLong(require(values, "elapsed"));
            result.p95Nanos = Long.parseLong(require(values, "p95"));
            result.maxNanos = Long.parseLong(require(values, "max"));
            result.allocatedBytes = Long.parseLong(require(values, "allocated"));
            result.gcCount = Long.parseLong(require(values, "gcCount"));
            result.gcMillis = Long.parseLong(require(values, "gcMillis"));
            result.stateDigest = require(values, "state");
            result.terrainFingerprint = require(values, "terrain");
            result.randomBoundary = require(values, "random");
            result.eventDigest = require(values, "events");
            String trace = require(values, "trace");
            result.trace = trace.isEmpty() ? Collections.emptyList() : List.of(trace.split(","));
            result.regionalCounts = new LinkedHashMap<>();
            for(String entry : require(values, "regions").split(",")) {
                int separator = entry.indexOf(':');
                if(separator <= 0) throw new IllegalArgumentException("malformed region result");
                result.regionalCounts.put(entry.substring(0, separator),
                    Integer.parseInt(entry.substring(separator + 1)));
            }
            return result;
        }

        private static String require(Map<String, String> values, String key)
        {
            String value = values.get(key);
            if(value == null) throw new IllegalArgumentException("worker result omitted " + key);
            return value;
        }

        private static void put(StringBuilder value, String key, Object entry)
        {
            value.append(key).append('=').append(entry).append('\n');
        }
    }

    private static final class Metrics
    {
        final long p95Nanos;
        final long maxNanos;
        final long allocatedBytes;
        final long gcCount;
        final long gcMillis;

        private Metrics(long p95Nanos, long maxNanos, long allocatedBytes,
                        long gcCount, long gcMillis)
        {
            this.p95Nanos = p95Nanos;
            this.maxNanos = maxNanos;
            this.allocatedBytes = allocatedBytes;
            this.gcCount = gcCount;
            this.gcMillis = gcMillis;
        }

        static Metrics of(List<WorkerResult> runs)
        {
            List<Long> p95 = new ArrayList<>();
            long max = 0L;
            long allocated = 0L;
            long collections = 0L;
            long collectionMillis = 0L;
            for(WorkerResult run : runs) {
                p95.add(run.p95Nanos);
                max = Math.max(max, run.maxNanos);
                allocated = Math.max(allocated, run.allocatedBytes);
                collections = Math.max(collections, run.gcCount);
                collectionMillis = Math.max(collectionMillis, run.gcMillis);
            }
            Collections.sort(p95);
            return new Metrics(p95.get(p95.size() / 2), max, allocated,
                collections, collectionMillis);
        }
    }

    private static final class Result
    {
        final String kind;
        boolean passed;
        String failure;
        String sourceHash;
        String runtimeHash;
        String referenceRuntimeHash;
        String configurationHash;
        Boolean performanceTargetMet;
        List<WorkerResult> current = Collections.emptyList();
        List<WorkerResult> reference = Collections.emptyList();
        Metrics currentMetrics;
        Metrics referenceMetrics;
        Map<String, Integer> regionalCounts = Collections.emptyMap();

        private Result(String kind, boolean passed, String failure)
        {
            this.kind = kind;
            this.passed = passed;
            this.failure = failure;
        }

        static Result success(String kind)
        {
            return new Result(kind, true, "");
        }

        static Result failure(String kind, String failure, List<WorkerResult> current,
                              List<WorkerResult> reference)
        {
            Result result = new Result(kind, false, failure);
            result.current = current;
            result.reference = reference;
            return result;
        }

        String toJson(Options options)
        {
            WorkerResult primary = current.isEmpty() ? null : current.get(0);
            StringBuilder value = new StringBuilder();
            value.append("{\n");
            field(value, "goalId", "G28", true);
            field(value, "workPackage", "R1", true);
            field(value, "mode", kind, true);
            field(value, "sourceHash", sourceHash, true);
            field(value, "runtimeHash", runtimeHash, true);
            field(value, "referenceRuntimeHash", referenceRuntimeHash == null ?
                "NOT_APPLICABLE" : referenceRuntimeHash, true);
            field(value, "configurationHash", configurationHash, true);
            field(value, "ecologyProfile", options.preset, true);
            field(value, "terrainProfile", options.terrainProfile, true);
            number(value, "ecologySeed", options.ecologySeed, true);
            number(value, "terrainSeed", options.terrainSeed, true);
            field(value, "thirstMode", options.thirst, true);
            number(value, "requestedSteps", options.measureSteps, true);
            number(value, "completedSteps", primary == null ? 0 : primary.completedSteps,
                true);
            number(value, "repeats", options.repeats, true);
            field(value, "performanceTarget", performanceTargetMet == null ? "NOT_APPLICABLE" :
                (performanceTargetMet ? "MET" : "NOT_MET"), true);
            field(value, "command", options.displayCommand().trim(), true);
            number(value, "exitCode", passed ? 0 : 1, true);
            field(value, "result", passed ? "PASS" : "FAIL", true);
            value.append("  \"evidenceFiles\": [\"INPUT_IDENTITY.txt\", \"RESOLVED_CONFIG.txt\", \"COMMAND.txt\", \"RESULT.json\", \"SUMMARY.md\"]\n");
            value.append("}\n");
            return value.toString();
        }

        String summary(Options options)
        {
            StringBuilder value = new StringBuilder();
            value.append("# V2.7 R1 diagnostics\n\n")
                .append("- Mode: `").append(kind).append("`\n")
                .append("- Result: `").append(passed ? "PASS" : "FAIL").append("`\n")
                .append("- Config: `").append(options.preset).append("`, ecology seed `")
                .append(options.ecologySeed).append("`, terrain `")
                .append(options.terrainProfile).append("/").append(options.terrainSeed)
                .append("`, thirst `").append(options.thirst).append("`\n")
                .append("- Requested measured steps: ").append(options.measureSteps).append("\n");
            if(currentMetrics != null) value.append("- Current median p95 / max / allocation: `")
                .append(currentMetrics.p95Nanos).append(" ns` / `")
                .append(currentMetrics.maxNanos).append(" ns` / `")
                .append(currentMetrics.allocatedBytes).append(" bytes`, GC `")
                .append(currentMetrics.gcCount).append(" collections / ")
                .append(currentMetrics.gcMillis).append(" ms`\n");
            if(referenceMetrics != null) value.append("- Reference median p95 / max / allocation: `")
                .append(referenceMetrics.p95Nanos).append(" ns` / `")
                .append(referenceMetrics.maxNanos).append(" ns` / `")
                .append(referenceMetrics.allocatedBytes).append(" bytes`, GC `")
                .append(referenceMetrics.gcCount).append(" collections / ")
                .append(referenceMetrics.gcMillis).append(" ms`\n");
            if(performanceTargetMet != null) value.append("- R1 performance target: `")
                .append(performanceTargetMet ? "MET" : "NOT_MET")
                .append("` (measurement validity remains separate from target attainment)\n");
            if(!regionalCounts.isEmpty()) value.append("- Final 3x3 regional live counts: `")
                .append(regionalCounts).append("`\n");
            if(!passed) value.append("- Failure: ").append(failure).append("\n");
            value.append("\nThis is development-only R1 evidence; it does not create a candidate, change CURRENT_DEV, or qualify ecology.\n");
            return value.toString();
        }

        private static void field(StringBuilder value, String name, String entry,
                                  boolean comma)
        {
            value.append("  \"").append(name).append("\": \"")
                .append(json(entry)).append("\"");
            if(comma) value.append(',');
            value.append('\n');
        }

        private static void number(StringBuilder value, String name, long entry,
                                   boolean comma)
        {
            value.append("  \"").append(name).append("\": ").append(entry);
            if(comma) value.append(',');
            value.append('\n');
        }

        private static String json(String value)
        {
            return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
        }
    }
}
