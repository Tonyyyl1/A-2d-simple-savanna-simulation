package savanna;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Independent checker for the tab-delimited V24-OBS-1A stream.  It keeps only
 * counters and per-step/parent keys; callers may discard the source stream as
 * it is checked by a file-backed reader in later batches.
 */
public final class PredatorRecruitmentFunnelAnalyzer
{
    private PredatorRecruitmentFunnelAnalyzer()
    {
    }

    public static Result analyze(String stream)
    {
        if(stream == null) throw new IllegalArgumentException("stream is required");
        Map<String, Integer> birthsByStepParent = new HashMap<>();
        Map<Integer, Integer> assessmentsByStep = new HashMap<>();
        Map<Integer, Integer> expectedByStep = new HashMap<>();
        Set<String> assessmentKeys = new HashSet<>();
        Map<String, Integer> assessmentsBySpecies = new HashMap<>();
        Map<String, Integer> birthsBySpecies = new HashMap<>();
        int assessments = 0;
        int birthEvents = 0;
        String[] lines = stream.split("\\R");
        for(String line : lines) {
            if(line.isEmpty()) continue;
            String[] cells = line.split("\\t");
            if(cells.length == 0) continue;
            if("EVENT".equals(cells[0])) {
                if(cells.length != 6) fail("malformed event row");
                if("BIRTH".equals(cells[2])) {
                    String key = cells[1] + ':' + cells[3];
                    increment(birthsByStepParent, key);
                    increment(birthsBySpecies, cells[4]);
                    birthEvents++;
                }
                continue;
            }
            if("ASSESSMENT".equals(cells[0])) {
                Map<String, String> values = keyValues(cells, 1);
                require(values, "run", "step", "actor", "species", "sex",
                    "age", "lifecycle", "phase", "opportunity", "ledger",
                    "proposal", "terminal", "nearestReachableMate",
                    "noMateRoute", "capacityEligibility", "creditEligibility",
                    "mateQueryRequested", "mateQueryConsumed", "mateQueryDraws",
                    "conceptionRollRequested", "conceptionRollDraws",
                    "conceptionRollAccepted", "litterDrawRequested", "litterDraws",
                    "conceptionKey", "pregnancyKey", "dueKey",
                    "acceptedConceptions", "pregnancyOpened", "dueBirths",
                    "pendingBirths", "submittedOrdinals", "committedBirths",
                    "lostPosition", "cancelledParent", "pregnancyLoss");
                int step = integer(values, "step");
                int accepted = integer(values, "acceptedConceptions");
                int opened = integer(values, "pregnancyOpened");
                int due = integer(values, "dueBirths");
                int pending = integer(values, "pendingBirths");
                int committed = integer(values, "committedBirths");
                int lost = integer(values, "lostPosition");
                int cancelled = integer(values, "cancelledParent");
                int pregnancyLoss = integer(values, "pregnancyLoss");
                if(accepted != opened || opened != due || opened != pending +
                   committed + lost + cancelled + pregnancyLoss ||
                   opened != integer(values, "submittedOrdinals")) {
                    fail("assessment lifecycle does not close: " +
                        values.get("step") + ':' + values.get("actor"));
                }
                String key = step + ":" + values.get("actor");
                if(!assessmentKeys.add(key)) fail("duplicate assessment: " + key);
                if(committed != count(birthsByStepParent, key)) {
                    fail("birth-event mismatch: " + key);
                }
                increment(assessmentsByStep, Integer.valueOf(step));
                increment(assessmentsBySpecies, values.get("species"));
                assessments++;
                continue;
            }
            if("STEP".equals(cells[0])) {
                Map<String, String> values = keyValues(cells, 1);
                require(values, "step", "assessments", "expected", "closed");
                int step = integer(values, "step");
                if(!"true".equals(values.get("closed")) ||
                   integer(values, "assessments") != integer(values, "expected") ||
                   integer(values, "assessments") != count(assessmentsByStep, step)) {
                    fail("step coverage does not close: " + step);
                }
                expectedByStep.put(Integer.valueOf(step), Integer.valueOf(
                    integer(values, "expected")));
                continue;
            }
            fail("unknown OBS-1A row type: " + cells[0]);
        }
        for(Integer step : assessmentsByStep.keySet()) {
            if(!expectedByStep.containsKey(step)) fail("missing step closure: " + step);
        }
        return new Result(assessments, birthEvents, assessmentsBySpecies,
            birthsBySpecies);
    }

    private static Map<String, String> keyValues(String[] cells, int start)
    {
        Map<String, String> values = new HashMap<>();
        for(int index = start; index < cells.length; index++) {
            int split = cells[index].indexOf('=');
            if(split <= 0 || values.put(cells[index].substring(0, split),
                cells[index].substring(split + 1)) != null) {
                fail("malformed or duplicate assessment field");
            }
        }
        return values;
    }

    private static void require(Map<String, String> values, String... names)
    {
        for(String name : names) if(!values.containsKey(name)) fail("missing " + name);
    }

    private static int integer(Map<String, String> values, String name)
    {
        try {
            return Integer.parseInt(values.get(name));
        }
        catch(NumberFormatException exception) {
            throw new IllegalStateException("invalid integer " + name, exception);
        }
    }

    private static int count(Map<?, Integer> values, Object key)
    {
        Integer value = values.get(key);
        return value == null ? 0 : value.intValue();
    }

    private static <T> void increment(Map<T, Integer> values, T key)
    {
        values.put(key, Integer.valueOf(count(values, key) + 1));
    }

    private static void fail(String message)
    {
        throw new IllegalStateException("V24-OBS-1A stream invalid: " + message);
    }

    public static final class Result
    {
        private final int assessmentCount;
        private final int birthEventCount;
        private final Map<String, Integer> assessmentsBySpecies;
        private final Map<String, Integer> birthsBySpecies;

        private Result(int assessmentCount, int birthEventCount,
                       Map<String, Integer> assessmentsBySpecies,
                       Map<String, Integer> birthsBySpecies)
        {
            this.assessmentCount = assessmentCount;
            this.birthEventCount = birthEventCount;
            this.assessmentsBySpecies = Map.copyOf(assessmentsBySpecies);
            this.birthsBySpecies = Map.copyOf(birthsBySpecies);
        }

        public int getAssessmentCount() { return assessmentCount; }
        public int getBirthEventCount() { return birthEventCount; }
        public Map<String, Integer> getAssessmentsBySpecies()
        {
            return assessmentsBySpecies;
        }
        public Map<String, Integer> getBirthsBySpecies() { return birthsBySpecies; }
    }
}
