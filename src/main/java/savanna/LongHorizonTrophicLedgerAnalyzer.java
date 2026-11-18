package savanna;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Independently recomputes every V24-OBS-1B 100-step ledger closure. */
public final class LongHorizonTrophicLedgerAnalyzer
{
    private static final String[] DEATH_FIELDS = {
        "deathAGE", "deathSTARVATION", "deathDEHYDRATION", "deathDISEASE",
        "deathPREDATION", "deathPOSITION_CONFLICT", "deathUNKNOWN"
    };
    private static final String[] SUM_FIELDS = {
        "births", "deaths", "deathAGE", "deathSTARVATION",
        "deathDEHYDRATION", "deathDISEASE", "deathPREDATION",
        "deathPOSITION_CONFLICT", "deathUNKNOWN", "netGrowth",
        "predationDeaths", "otherDeaths", "frameLedgers",
        "huntAssessments", "huntLocalReachable", "huntAttempts",
        "huntSuccess", "realizedKills", "acceptedEnergy",
        "localPreyCountSum", "localPreyDistanceSum",
        "recruitmentAssessments", "mateQueries", "mateEligible",
        "acceptedConceptions", "pregnancyOpened", "dueBirths",
        "pendingBirths", "submittedOrdinals", "committedBirths",
        "lostPosition", "cancelledParent", "pregnancyLoss", "grazes"
    };
    private static final String[] END_FIELDS = {
        "populationEnd", "juvenile", "cub", "adultFemale", "adultMale",
        "zeroMateRunEnd", "zeroConceptionRunEnd"
    };
    private static final String[] BOUNDARY_FIELDS = {
        "resourceBoundary", "grassAfterGrowthMeanHex",
        "grassAtStepEndMeanHex", "capacityActors",
        "capacityPassableSum", "capacityOccupantSum",
        "capacityConspecificSum", "capacityPredatorSum",
        "capacityEdiblePreySum"
    };

    private LongHorizonTrophicLedgerAnalyzer()
    {
    }

    public static Result analyze(String stream)
    {
        if(stream == null) {
            throw new IllegalArgumentException("OBS-1B stream is required");
        }
        Set<String> species = registrySpecies();
        Map<String, Aggregate> aggregates = new LinkedHashMap<>();
        Map<String, Long> lastPopulation = new LinkedHashMap<>();
        Set<String> stepSpecies = new LinkedHashSet<>();
        Set<String> intervalSpecies = new LinkedHashSet<>();
        int currentStep = 0;
        int stepRows = 0;
        int intervalRows = 0;
        int completedIntervals = 0;
        int maxRetainedSpecies = 0;
        boolean intervalDue = false;
        String run = null;

        String[] lines = stream.split("\\n", -1);
        for(String line : lines) {
            if(line.isEmpty()) continue;
            Row row = parse(line);
            require(LongHorizonTrophicLedger.SCHEMA.equals(row.value("schema")),
                "unexpected OBS-1B schema");
            require(integer(row, "intervalSteps") ==
                LongHorizonTrophicStepFact.INTERVAL_STEPS,
                "unexpected OBS-1B interval");
            require("true".equals(row.value("closed")),
                "OBS-1B row is not closed");
            if(run == null) run = row.value("run");
            require(run.equals(row.value("run")),
                "OBS-1B stream mixed run identities");

            if("STEP".equals(row.type)) {
                require(!intervalDue,
                    "OBS-1B interval rows are missing before the next step");
                int step = integer(row, "step");
                if(step != currentStep) {
                    if(currentStep > 0) {
                        require(stepSpecies.equals(species),
                            "OBS-1B step omitted or duplicated registry species");
                    }
                    require(step == currentStep + 1,
                        "OBS-1B step rows are not contiguous from one");
                    currentStep = step;
                    stepSpecies.clear();
                }
                String name = row.value("species");
                require(species.contains(name) && stepSpecies.add(name),
                    "OBS-1B invalid or duplicate step species");
                validateStep(row, name, step, lastPopulation);
                Aggregate aggregate = aggregates.get(name);
                if(aggregate == null) {
                    aggregate = new Aggregate(step, row);
                    aggregates.put(name, aggregate);
                }
                aggregate.add(row);
                maxRetainedSpecies = Math.max(maxRetainedSpecies,
                    aggregates.size());
                stepRows++;
                if(stepSpecies.equals(species) && step %
                    LongHorizonTrophicStepFact.INTERVAL_STEPS == 0) {
                    intervalDue = true;
                    intervalSpecies.clear();
                }
            }
            else if("INTERVAL".equals(row.type)) {
                require(intervalDue && stepSpecies.equals(species),
                    "OBS-1B interval appeared without a complete boundary step");
                require(integer(row, "end") == currentStep,
                    "OBS-1B interval end differs from its boundary step");
                String name = row.value("species");
                require(species.contains(name) && intervalSpecies.add(name),
                    "OBS-1B invalid or duplicate interval species");
                Aggregate aggregate = aggregates.get(name);
                require(aggregate != null,
                    "OBS-1B interval has no independently accumulated steps");
                aggregate.requireSame(row);
                intervalRows++;
                if(intervalSpecies.equals(species)) {
                    completedIntervals++;
                    intervalDue = false;
                    aggregates.clear();
                }
            }
            else {
                throw new IllegalStateException(
                    "unknown OBS-1B row type: " + row.type);
            }
        }
        if(currentStep > 0) {
            require(stepSpecies.equals(species),
                "OBS-1B final step is incomplete");
        }
        require(!intervalDue, "OBS-1B final interval is incomplete");
        return new Result(stepRows, intervalRows, completedIntervals,
            currentStep, maxRetainedSpecies);
    }

    private static void validateStep(Row row, String species, int step,
                                     Map<String, Long> lastPopulation)
    {
        long start = nonnegative(row, "populationStart");
        long end = nonnegative(row, "populationEnd");
        long juvenile = nonnegative(row, "juvenile");
        long cub = nonnegative(row, "cub");
        long adultFemale = nonnegative(row, "adultFemale");
        long adultMale = nonnegative(row, "adultMale");
        long births = nonnegative(row, "births");
        long deaths = nonnegative(row, "deaths");
        require(end == juvenile + adultFemale + adultMale && cub <= juvenile,
            "OBS-1B lifecycle partition failed for " + species);
        require(end == start + births - deaths,
            "OBS-1B population conservation failed for " + species);
        Long previous = lastPopulation.put(species, Long.valueOf(end));
        require(previous == null || previous.longValue() == start,
            "OBS-1B population continuity failed for " + species);
        long deathSum = 0L;
        for(String field : DEATH_FIELDS) deathSum += nonnegative(row, field);
        require(deathSum == deaths && signed(row, "netGrowth") == births - deaths,
            "OBS-1B death/net-growth conservation failed for " + species);
        require(nonnegative(row, "predationDeaths") ==
                nonnegative(row, "deathPREDATION") &&
                nonnegative(row, "otherDeaths") == deaths -
                    nonnegative(row, "deathPREDATION"),
            "OBS-1B predation/other death partition failed for " + species);
        require(nonnegative(row, "frameLedgers") == start,
            "OBS-1B FRAME coverage failed for " + species);

        long hunts = nonnegative(row, "huntAssessments");
        long reachable = nonnegative(row, "huntLocalReachable");
        long attempts = nonnegative(row, "huntAttempts");
        long successes = nonnegative(row, "huntSuccess");
        long realized = nonnegative(row, "realizedKills");
        require(reachable <= hunts && attempts <= hunts && successes <= attempts &&
                successes == realized,
            "OBS-1B HUNT funnel failed for " + species);
        nonnegative(row, "acceptedEnergy");
        nonnegative(row, "localPreyCountSum");
        nonnegative(row, "localPreyDistanceSum");
        long nearest = signed(row, "nearestPreyDistance");
        require(nearest >= -1L,
            "OBS-1B nearest prey distance is invalid for " + species);

        SpeciesProfile profile = SpeciesRegistry.getProfile(species);
        require(profile != null, "OBS-1B species is not registered");
        long recruitment = nonnegative(row, "recruitmentAssessments");
        long accepted = nonnegative(row, "acceptedConceptions");
        long opened = nonnegative(row, "pregnancyOpened");
        long due = nonnegative(row, "dueBirths");
        long pending = nonnegative(row, "pendingBirths");
        long submitted = nonnegative(row, "submittedOrdinals");
        long committed = nonnegative(row, "committedBirths");
        long losses = nonnegative(row, "lostPosition") +
            nonnegative(row, "cancelledParent") +
            nonnegative(row, "pregnancyLoss");
        if(profile.isPredator()) {
            require(hunts == start && recruitment == start &&
                    nonnegative(row, "mateQueries") <= recruitment &&
                    nonnegative(row, "mateEligible") <= recruitment &&
                    accepted == opened && due == opened && submitted == opened &&
                    opened == pending + committed + losses && committed == births,
                "OBS-1B recruitment funnel failed for " + species);
        }
        else {
            require(hunts == 0L && recruitment == 0L && accepted == 0L &&
                    opened == 0L && due == 0L && pending == 0L &&
                    submitted == 0L && committed == 0L && losses == 0L,
                "OBS-1B predator-only facts leaked to " + species);
        }
        long zeroMate = nonnegative(row, "zeroMateRunEnd");
        long longestMate = nonnegative(row, "longestZeroMateRun");
        long zeroConception = nonnegative(row, "zeroConceptionRunEnd");
        long longestConception = nonnegative(row,
            "longestZeroConceptionRun");
        require(longestMate >= zeroMate && longestConception >= zeroConception,
            "OBS-1B zero-run maximum failed for " + species);
        nonnegative(row, "grazes");

        boolean boundary = Boolean.parseBoolean(row.value("resourceBoundary"));
        require(boundary == (step %
            LongHorizonTrophicStepFact.INTERVAL_STEPS == 0),
            "OBS-1B resource boundary cadence failed");
        double afterGrowth = finiteHex(row, "grassAfterGrowthMeanHex");
        double atEnd = finiteHex(row, "grassAtStepEndMeanHex");
        long capacityActors = nonnegative(row, "capacityActors");
        for(String field : Arrays.copyOfRange(BOUNDARY_FIELDS, 4,
                                               BOUNDARY_FIELDS.length)) {
            nonnegative(row, field);
        }
        if(boundary) {
            require(afterGrowth >= 0.0 && afterGrowth <= 1.0 && atEnd >= 0.0 &&
                    atEnd <= afterGrowth && capacityActors == start,
                "OBS-1B boundary resource/capacity facts failed for " + species);
        }
        else {
            require(afterGrowth == 0.0 && atEnd == 0.0 && capacityActors == 0L,
                "OBS-1B non-boundary row retained boundary facts");
            for(String field : Arrays.copyOfRange(BOUNDARY_FIELDS, 4,
                                                   BOUNDARY_FIELDS.length)) {
                require(signed(row, field) == 0L,
                    "OBS-1B non-boundary capacity is nonzero");
            }
        }
    }

    private static final class Aggregate
    {
        private final int startStep;
        private final long populationStart;
        private final Map<String, Long> sums = new LinkedHashMap<>();
        private final Map<String, String> ends = new LinkedHashMap<>();
        private final Map<String, String> boundary = new LinkedHashMap<>();
        private long nearest = -1L;
        private long longestMate;
        private long longestConception;
        private int endStep;

        private Aggregate(int startStep, Row first)
        {
            this.startStep = startStep;
            populationStart = nonnegative(first, "populationStart");
            for(String field : SUM_FIELDS) sums.put(field, Long.valueOf(0L));
        }

        private void add(Row row)
        {
            endStep = integer(row, "step");
            for(String field : SUM_FIELDS) {
                sums.put(field, Long.valueOf(sums.get(field).longValue() +
                    signed(row, field)));
            }
            for(String field : END_FIELDS) ends.put(field, row.value(field));
            long value = signed(row, "nearestPreyDistance");
            if(value >= 0L && (nearest < 0L || value < nearest)) nearest = value;
            longestMate = Math.max(longestMate,
                nonnegative(row, "longestZeroMateRun"));
            longestConception = Math.max(longestConception,
                nonnegative(row, "longestZeroConceptionRun"));
            if(Boolean.parseBoolean(row.value("resourceBoundary"))) {
                for(String field : BOUNDARY_FIELDS) {
                    boundary.put(field, row.value(field));
                }
            }
        }

        private void requireSame(Row row)
        {
            require(integer(row, "start") == startStep &&
                    integer(row, "end") == endStep,
                "OBS-1B interval span differs from accumulated steps");
            require(nonnegative(row, "populationStart") == populationStart,
                "OBS-1B interval start population differs from steps");
            for(String field : SUM_FIELDS) {
                require(signed(row, field) == sums.get(field).longValue(),
                    "OBS-1B interval sum differs for " + field);
            }
            for(String field : END_FIELDS) {
                require(row.value(field).equals(ends.get(field)),
                    "OBS-1B interval terminal value differs for " + field);
            }
            require(signed(row, "nearestPreyDistance") == nearest,
                "OBS-1B interval nearest prey distance differs from steps");
            require(nonnegative(row, "longestZeroMateRun") == longestMate &&
                    nonnegative(row, "longestZeroConceptionRun") ==
                        longestConception,
                "OBS-1B interval zero-run maximum differs from steps");
            for(String field : BOUNDARY_FIELDS) {
                require(row.value(field).equals(boundary.get(field)),
                    "OBS-1B interval boundary differs for " + field);
            }
            long populationEnd = nonnegative(row, "populationEnd");
            String expected = Double.toHexString((populationEnd -
                populationStart) / (double)Math.max(1L, populationStart));
            require(expected.equals(row.value("perCapitaGrowthHex")),
                "OBS-1B per-capita growth was not independently recomputed");
        }
    }

    private static Row parse(String line)
    {
        String[] fields = line.split("\\t", -1);
        require(fields.length > 1 && !fields[0].isBlank(),
            "malformed OBS-1B row");
        Map<String, String> values = new LinkedHashMap<>();
        for(int index = 1; index < fields.length; index++) {
            int equals = fields[index].indexOf('=');
            require(equals > 0 && equals < fields[index].length() - 1,
                "malformed OBS-1B field");
            String key = fields[index].substring(0, equals);
            String value = fields[index].substring(equals + 1);
            require(values.putIfAbsent(key, value) == null,
                "duplicate OBS-1B field: " + key);
        }
        return new Row(fields[0], values);
    }

    private static long nonnegative(Row row, String field)
    {
        long value = signed(row, field);
        require(value >= 0L, "negative OBS-1B field: " + field);
        return value;
    }

    private static int integer(Row row, String field)
    {
        long value = signed(row, field);
        require(value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE,
            "OBS-1B integer field out of range: " + field);
        return (int)value;
    }

    private static long signed(Row row, String field)
    {
        try {
            return Long.parseLong(row.value(field));
        }
        catch(NumberFormatException exception) {
            throw new IllegalStateException(
                "invalid OBS-1B integer field: " + field, exception);
        }
    }

    private static double finiteHex(Row row, String field)
    {
        try {
            double value = Double.valueOf(row.value(field)).doubleValue();
            require(Double.isFinite(value),
                "non-finite OBS-1B double field: " + field);
            return value;
        }
        catch(NumberFormatException exception) {
            throw new IllegalStateException(
                "invalid OBS-1B double field: " + field, exception);
        }
    }

    private static Set<String> registrySpecies()
    {
        Set<String> result = new LinkedHashSet<>();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            result.add(factory.getProfile().getName());
        }
        return Collections.unmodifiableSet(result);
    }

    private static void require(boolean value, String message)
    {
        if(!value) throw new IllegalStateException(message);
    }

    private static final class Row
    {
        private final String type;
        private final Map<String, String> values;

        private Row(String type, Map<String, String> values)
        {
            this.type = type;
            this.values = values;
        }

        private String value(String key)
        {
            String value = values.get(key);
            if(value == null || value.isBlank()) {
                throw new IllegalStateException(
                    "OBS-1B row lacks field: " + key);
            }
            return value;
        }
    }

    public static final class Result
    {
        private final int stepRows;
        private final int intervalRows;
        private final int completedIntervals;
        private final int steps;
        private final int maxRetainedIntervalSpecies;

        private Result(int stepRows, int intervalRows, int completedIntervals,
                       int steps, int maxRetainedIntervalSpecies)
        {
            this.stepRows = stepRows;
            this.intervalRows = intervalRows;
            this.completedIntervals = completedIntervals;
            this.steps = steps;
            this.maxRetainedIntervalSpecies = maxRetainedIntervalSpecies;
        }

        public int getStepRows() { return stepRows; }
        public int getIntervalRows() { return intervalRows; }
        public int getCompletedIntervals() { return completedIntervals; }
        public int getSteps() { return steps; }
        public int getMaxRetainedIntervalSpecies()
        {
            return maxRetainedIntervalSpecies;
        }
    }
}
