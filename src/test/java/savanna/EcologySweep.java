package savanna;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Resumable M3 parameter sweep (plan v3.0 §7 M3-0/M3-1).
 *
 * <p>Usage: {@code EcologySweep <grid-file> <out-dir> [--steps=1000]
 * [--seeds=1111,2111] [--thirst=off,on] [--jobs=N] [--rank-only]}. Run it
 * through {@code scripts/eco-sweep.sh}, which freezes the classes into the
 * output directory so a resumed sweep uses the same code.</p>
 *
 * <p>Grid file, one directive per line ({@code #} starts a comment):</p>
 * <pre>
 * base key=value,key=value      applied under every candidate (at most once)
 * axis key=v1,v2,v3             cartesian product over all axes
 * candidate key=value,...       one explicit candidate
 * </pre>
 * <p>The base alone is always a candidate. Candidates are deduplicated by
 * {@link EcologyParameters#hash()}. Each run is one {@link EcologyMatrixRunner}
 * JVM with {@code --diag}; its log lands in {@code runs/} under a name built
 * from hash, seed, steps and thirst, and an existing complete log is reused,
 * so an interrupted sweep continues where it stopped. {@code ECO_JOBS} (or
 * {@code --jobs}, default 3) sets the parallelism. Every run JVM is capped
 * with {@code -Xmx} from {@code ECO_XMX} (default 1g) and the parallel
 * collector on one GC thread, about 1.2 GB and one core each (the serial
 * collector spent 40-47% of a heavy run in full GCs; one-thread parallel
 * was the fastest of four settings tried): an uncapped JVM defaults to a
 * quarter of physical memory, and eight of them exhausted a 16 GB machine.</p>
 *
 * <p>Ranking, best first: fewer extinct runs; larger smallest final species
 * count over all runs; later mean extinction step (ties among extinct
 * candidates); smaller worst normalized balance (C-0) excess over 12 among the surviving runs
 * (extinct runs are already counted); smaller worst hotspot span excess over
 * 300. Outputs {@code results.csv} (one row per run) and
 * {@code ranking.txt}.</p>
 */
public final class EcologySweep
{
    private static final double BALANCE_LIMIT = 12.0;
    private static final int SPAN_LIMIT = 300;

    private EcologySweep()
    {
    }

    public static void main(String[] args) throws Exception
    {
        if(args.length < 2) {
            System.err.println("usage: EcologySweep <grid-file> <out-dir> [--steps=1000] " +
                "[--seeds=1111,2111] [--thirst=off,on] [--jobs=N] [--rank-only]");
            System.exit(2);
        }
        Path grid = Path.of(args[0]);
        Path out = Path.of(args[1]);
        int steps = 1000;
        String[] seeds = {"1111", "2111"};
        String[] thirsts = {"off", "on"};
        int jobs = parsePositive(System.getenv().getOrDefault("ECO_JOBS", "3"), "ECO_JOBS");
        boolean rankOnly = false;
        for(int index = 2; index < args.length; index++) {
            String option = args[index];
            if(option.startsWith("--steps=")) steps = parsePositive(option.substring(8), "steps");
            else if(option.startsWith("--seeds=")) seeds = option.substring(8).split(",");
            else if(option.startsWith("--thirst=")) thirsts = option.substring(9).split(",");
            else if(option.startsWith("--jobs=")) jobs = parsePositive(option.substring(7), "jobs");
            else if("--rank-only".equals(option)) rankOnly = true;
            else throw new IllegalArgumentException("unknown option: " + option);
        }

        List<EcologyParameters> candidates = readGrid(grid);
        Path runs = out.resolve("runs");
        Files.createDirectories(runs);
        StringBuilder index = new StringBuilder("hash\toverrides\n");
        for(EcologyParameters candidate : candidates) {
            index.append(candidate.hash()).append('\t').append(candidate.overrides()).append('\n');
        }
        Files.writeString(out.resolve("candidates.tsv"), index.toString(), StandardCharsets.UTF_8);

        List<RunSpec> specs = new ArrayList<>();
        for(EcologyParameters candidate : candidates) {
            for(String seed : seeds) {
                for(String thirst : thirsts) {
                    specs.add(new RunSpec(candidate, Long.parseLong(seed.trim()), steps,
                        thirst.trim(), runs));
                }
            }
        }
        System.out.println("SWEEP candidates=" + candidates.size() + " runs=" + specs.size() +
            " rules=" + EcologyRules.VERSION);
        if(!rankOnly) execute(specs, jobs);

        List<RunResult> results = new ArrayList<>();
        for(RunSpec spec : specs) {
            RunResult result = RunResult.read(spec);
            if(result != null) results.add(result);
        }
        writeCsv(out.resolve("results.csv"), results);
        String ranking = rank(candidates, results, specs.size() / Math.max(1, candidates.size()));
        Files.writeString(out.resolve("ranking.txt"), ranking, StandardCharsets.UTF_8);
        System.out.print(ranking);
        System.out.println("SWEEP_DONE results=" + results.size() + "/" + specs.size());
    }

    static List<EcologyParameters> readGrid(Path grid) throws IOException
    {
        EcologyParameters base = null;
        Map<EcologyParameters.Key, List<String>> axes = new LinkedHashMap<>();
        List<String> explicit = new ArrayList<>();
        for(String raw : Files.readAllLines(grid, StandardCharsets.UTF_8)) {
            int comment = raw.indexOf('#');
            String line = (comment >= 0 ? raw.substring(0, comment) : raw).trim();
            if(line.isEmpty()) continue;
            int space = line.indexOf(' ');
            String directive = space < 0 ? line : line.substring(0, space);
            String body = space < 0 ? "" : line.substring(space + 1).trim();
            if("base".equals(directive)) {
                if(base != null) throw new IllegalArgumentException("more than one base line");
                base = EcologyParameters.defaults().with(body);
            }
            else if("axis".equals(directive)) {
                int equals = body.indexOf('=');
                if(equals <= 0) throw new IllegalArgumentException("axis needs key=v1,v2: " + line);
                String id = body.substring(0, equals).trim();
                EcologyParameters.Key key = EcologyParameters.Key.byId(id);
                List<String> values = new ArrayList<>();
                for(String value : body.substring(equals + 1).split(",")) {
                    if(!value.isBlank()) values.add(id + "=" + value.trim());
                }
                if(values.isEmpty() || axes.put(key, values) != null) {
                    throw new IllegalArgumentException("empty or repeated axis: " + line);
                }
            }
            else if("candidate".equals(directive)) {
                explicit.add(body);
            }
            else {
                throw new IllegalArgumentException("unknown grid directive: " + line);
            }
        }
        if(base == null) base = EcologyParameters.defaults();

        Map<String, EcologyParameters> unique = new LinkedHashMap<>();
        unique.put(base.hash(), base);
        List<String> combinations = new ArrayList<>();
        if(!axes.isEmpty()) {
            combinations.add("");
            for(List<String> values : axes.values()) {
                List<String> next = new ArrayList<>();
                for(String prefix : combinations) {
                    for(String value : values) {
                        next.add(prefix.isEmpty() ? value : prefix + "," + value);
                    }
                }
                combinations = next;
            }
        }
        combinations.addAll(explicit);
        for(String spec : combinations) {
            EcologyParameters candidate = withOver(base, spec);
            unique.putIfAbsent(candidate.hash(), candidate);
        }
        return new ArrayList<>(unique.values());
    }

    /** Applies {@code spec} over {@code base}; keys in {@code spec} win. */
    private static EcologyParameters withOver(EcologyParameters base, String spec)
    {
        Map<String, String> merged = new LinkedHashMap<>();
        for(String part : new String[] {base.overrides(), spec}) {
            if(part == null || part.isBlank() || "default".equals(part)) continue;
            for(String entry : part.split(",")) {
                if(entry.isBlank()) continue;
                int equals = entry.indexOf('=');
                if(equals <= 0) throw new IllegalArgumentException("expected key=value: " + entry);
                merged.put(entry.substring(0, equals).trim(), entry.substring(equals + 1).trim());
            }
        }
        StringBuilder text = new StringBuilder();
        for(Map.Entry<String, String> entry : merged.entrySet()) {
            if(text.length() > 0) text.append(',');
            text.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return EcologyParameters.defaults().with(text.toString());
    }

    private static void execute(List<RunSpec> specs, int jobs) throws Exception
    {
        List<RunSpec> pending = new ArrayList<>();
        for(RunSpec spec : specs) {
            if(RunResult.read(spec) == null) pending.add(spec);
        }
        System.out.println("SWEEP pending=" + pending.size() + " reused=" +
            (specs.size() - pending.size()) + " jobs=" + jobs);
        ExecutorService pool = Executors.newFixedThreadPool(jobs);
        AtomicInteger done = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for(RunSpec spec : pending) {
            futures.add(pool.submit(() -> {
                String outcome = runOne(spec);
                System.out.println("SWEEP [" + done.incrementAndGet() + "/" + pending.size() +
                    "] " + spec.name() + " " + outcome);
                return null;
            }));
        }
        pool.shutdown();
        for(Future<?> future : futures) future.get();
    }

    private static String runOne(RunSpec spec) throws IOException, InterruptedException
    {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Djava.awt.headless=true");
        command.add("-Xmx" + System.getenv().getOrDefault("ECO_XMX", "1g"));
        command.add("-XX:+UseParallelGC");
        command.add("-XX:ParallelGCThreads=1");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(EcologyMatrixRunner.class.getName());
        command.add(Long.toString(spec.seed));
        command.add(Integer.toString(spec.steps));
        command.add(spec.thirst);
        command.add("--params=" + spec.parameters.overrides());
        command.add("--diag");
        Path partial = spec.log().resolveSibling(spec.name() + ".partial");
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
            .redirectOutput(partial.toFile()).start();
        int exit = process.waitFor();
        String text = Files.readString(partial, StandardCharsets.UTF_8);
        if(exit != 0 || !text.contains("ECO_RESULT ")) {
            return "ERROR exit=" + exit + " (kept " + partial.getFileName() + ")";
        }
        Files.move(partial, spec.log(), StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
        RunResult result = RunResult.read(spec);
        return result == null ? "UNPARSED" : result.summary();
    }

    private static void writeCsv(Path file, List<RunResult> results) throws IOException
    {
        StringBuilder csv = new StringBuilder("hash,seed,steps,thirst,completed,extinctionStep," +
            "extinctSpecies,minFinal,balance,maxOverloadSpan,regionLedger,gate," +
            "lion,cheetah,zebra,buffalo,gazelle,overrides,balanceMean,means,lnSd,balanceNorm\n");
        for(RunResult result : results) {
            csv.append(result.spec.parameters.hash()).append(',').append(result.spec.seed)
                .append(',').append(result.spec.steps).append(',').append(result.spec.thirst)
                .append(',').append(result.completed).append(',').append(result.extinctionStep)
                .append(',').append(result.extinctSpecies).append(',').append(result.minFinal())
                .append(',').append(format(result.balance)).append(',').append(result.span)
                .append(',').append(result.ledger).append(',').append(result.gate);
            for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
                csv.append(',').append(result.finalCounts.getOrDefault(
                    factory.getProfile().getName(), 0));
            }
            csv.append(",\"").append(result.spec.parameters.overrides()).append("\",")
                .append(format(result.balanceMean))
                .append(',').append(result.means.replace(',', ';'))
                .append(',').append(result.lnSd.replace(',', ';'))
                .append(',').append(format(result.balanceNorm)).append('\n');
        }
        Files.writeString(file, csv.toString(), StandardCharsets.UTF_8);
    }

    private static String rank(List<EcologyParameters> candidates, List<RunResult> results,
                               int runsPerCandidate)
    {
        List<Score> scores = new ArrayList<>();
        for(EcologyParameters candidate : candidates) {
            Score score = new Score(candidate);
            for(RunResult result : results) {
                if(result.spec.parameters.equals(candidate)) score.add(result);
            }
            if(score.runs > 0) scores.add(score);
        }
        scores.sort(Score.ORDER);
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT, "%-4s %-12s %4s %5s %6s %8s %9s %7s %4s  %s%n",
            "rank", "hash", "runs", "extinct", "minFin", "meanExt", "survBal", "maxSpan",
            "pass", "overrides"));
        int rank = 1;
        for(Score score : scores) {
            text.append(String.format(Locale.ROOT, "%-4d %-12s %2d/%-2d %5d %6d %8s %9s %7d %4d  %s%n",
                rank++, score.parameters.hash(), score.runs, runsPerCandidate, score.extinct,
                score.minFinal, score.extinct == 0 ? "-" :
                    String.format(Locale.ROOT, "%.0f", score.meanExtinctionStep()),
                format(score.survivingBalance()), score.worstSpan, score.passes,
                score.parameters.overrides()));
        }
        return text.toString();
    }

    private static String format(double value)
    {
        return Double.isInfinite(value) ? "inf" : String.format(Locale.ROOT, "%.2f", value);
    }

    private static int parsePositive(String raw, String name)
    {
        int value = Integer.parseInt(raw.trim());
        if(value < 1) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static final class RunSpec
    {
        final EcologyParameters parameters;
        final long seed;
        final int steps;
        final String thirst;
        final Path directory;

        RunSpec(EcologyParameters parameters, long seed, int steps, String thirst,
                Path directory)
        {
            if(!"on".equals(thirst) && !"off".equals(thirst)) {
                throw new IllegalArgumentException("thirst must be on or off: " + thirst);
            }
            this.parameters = parameters;
            this.seed = seed;
            this.steps = steps;
            this.thirst = thirst;
            this.directory = directory;
        }

        String name()
        {
            return parameters.hash() + "-" + seed + "-" + steps + "-" + thirst;
        }

        Path log()
        {
            return directory.resolve(name() + ".log");
        }
    }

    private static final class RunResult
    {
        final RunSpec spec;
        final int completed;
        final int extinctionStep;
        final String extinctSpecies;
        final Map<String, Integer> finalCounts;
        final double balance;
        /** B1 gate statistic; older logs without it fall back to the end point. */
        final double balanceMean;
        final int span;
        final String ledger;
        final String gate;
        /** A-5 windowed means and ln-sd, species:value pairs; "-" in older logs. */
        final String means;
        final String lnSd;
        /** C-0 normalized balance (older logs fall back to the windowed-mean balance). */
        final double balanceNorm;
        /** Species that took part in the run (C-0); empty in older logs, meaning all. */
        final java.util.Set<String> participants;

        private RunResult(RunSpec spec, Map<String, String> fields)
        {
            this.spec = spec;
            completed = Integer.parseInt(fields.get("completed"));
            extinctionStep = Integer.parseInt(fields.get("extinctionStep"));
            extinctSpecies = fields.getOrDefault("extinctSpecies", "-");
            finalCounts = new LinkedHashMap<>();
            for(String entry : fields.get("final").split(",")) {
                int colon = entry.indexOf(':');
                finalCounts.put(entry.substring(0, colon),
                    Integer.parseInt(entry.substring(colon + 1)));
            }
            String rawBalance = fields.get("balance");
            balance = "inf".equals(rawBalance) ? Double.POSITIVE_INFINITY
                : Double.parseDouble(rawBalance);
            String rawMean = fields.getOrDefault("balanceMean", rawBalance);
            balanceMean = "inf".equals(rawMean) ? Double.POSITIVE_INFINITY
                : Double.parseDouble(rawMean);
            span = Integer.parseInt(fields.get("maxOverloadSpan"));
            ledger = fields.get("regionLedger");
            gate = fields.get("gate");
            means = fields.getOrDefault("means", "-");
            lnSd = fields.getOrDefault("lnSd", "-");
            String rawNorm = fields.getOrDefault("balanceNorm", rawMean);
            balanceNorm = "inf".equals(rawNorm) ? Double.POSITIVE_INFINITY
                : Double.parseDouble(rawNorm);
            participants = new java.util.LinkedHashSet<>();
            String listed = fields.getOrDefault("participants", "");
            if(!listed.isEmpty()) participants.addAll(java.util.Arrays.asList(listed.split(",")));
        }

        /** The parsed result of a complete log for {@code spec}, or null. */
        static RunResult read(RunSpec spec)
        {
            Path log = spec.log();
            if(!Files.isRegularFile(log)) return null;
            try {
                for(String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
                    if(!line.startsWith("ECO_RESULT ")) continue;
                    Map<String, String> fields = new LinkedHashMap<>();
                    for(String token : line.substring(11).split(" ")) {
                        int equals = token.indexOf('=');
                        if(equals > 0) fields.put(token.substring(0, equals),
                            token.substring(equals + 1));
                    }
                    if(!spec.parameters.hash().equals(fields.get("paramsHash")) ||
                       "ERROR".equals(fields.get("gate"))) return null;
                    return new RunResult(spec, fields);
                }
            }
            catch(IOException | RuntimeException e) {
                return null;
            }
            return null;
        }

        boolean extinct()
        {
            return extinctionStep >= 0 || completed < spec.steps;
        }

        int minFinal()
        {
            int min = Integer.MAX_VALUE;
            for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
                String species = factory.getProfile().getName();
                if(!participants.isEmpty() && !participants.contains(species)) continue;
                min = Math.min(min, finalCounts.getOrDefault(species, 0));
            }
            return min;
        }

        String summary()
        {
            return "gate=" + gate + " extinct=" + (extinct() ? extinctSpecies + "@" +
                extinctionStep : "no") + " balance=" + format(balance) +
                " balanceMean=" + format(balanceMean) + " balanceNorm=" + format(balanceNorm) +
                " span=" + span;
        }
    }

    private static final class Score
    {
        static final Comparator<Score> ORDER = Comparator
            .comparingInt((Score score) -> score.extinct)
            .thenComparing(Comparator.comparingInt((Score score) -> score.minFinal).reversed())
            .thenComparing(Comparator.comparingDouble(Score::meanExtinctionStep).reversed())
            .thenComparingDouble(score -> Math.max(0.0, score.survivingBalance() - BALANCE_LIMIT))
            .thenComparingInt(score -> Math.max(0, score.worstSpan - SPAN_LIMIT))
            .thenComparing(score -> score.parameters.hash());

        final EcologyParameters parameters;
        int runs;
        int extinct;
        int passes;
        int minFinal = Integer.MAX_VALUE;
        long extinctionStepSum;
        /** Worst balance among the surviving runs; infinite when none survived. */
        double worstBalance;
        int worstSpan;

        Score(EcologyParameters parameters)
        {
            this.parameters = parameters;
        }

        /** Worst surviving-run balance, or infinity when every run went extinct. */
        double survivingBalance()
        {
            return extinct == runs ? Double.POSITIVE_INFINITY : worstBalance;
        }

        void add(RunResult result)
        {
            runs++;
            if(result.extinct()) {
                extinct++;
                extinctionStepSum += result.extinctionStep >= 0
                    ? result.extinctionStep : result.completed;
            }
            else {
                worstBalance = Math.max(worstBalance, result.balanceNorm);
            }
            if("PASS".equals(result.gate)) passes++;
            minFinal = Math.min(minFinal, result.minFinal());
            worstSpan = Math.max(worstSpan, result.span);
        }

        /** Mean extinction step of the extinct runs; candidates without any rank first. */
        double meanExtinctionStep()
        {
            return extinct == 0 ? Double.MAX_VALUE : (double)extinctionStepSum / extinct;
        }
    }
}
