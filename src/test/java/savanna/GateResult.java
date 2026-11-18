package savanna;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One immutable authority for gate counts, failure order, console output,
 * machine-readable JSON, Markdown, and process exit status.
 */
public final class GateResult
{
    public static final String SCHEMA = "v24-gate-result-v1";

    /** One assertion in execution order. */
    public static final class Assertion
    {
        private final String id;
        private final boolean passed;
        private final String detail;

        private Assertion(String id, boolean passed, String detail)
        {
            this.id = requireText(id, "assertion id");
            this.passed = passed;
            this.detail = detail == null ? "" : detail;
        }

        public String getId() { return id; }
        public boolean isPassed() { return passed; }
        public String getDetail() { return detail; }
    }

    /** One completed assertion group in execution order. */
    public static final class Group
    {
        private final String name;
        private final String fixtureId;
        private final List<Assertion> assertions;
        private final long elapsedMillis;

        private Group(String name, String fixtureId,
                      List<Assertion> assertions, long elapsedMillis)
        {
            this.name = requireText(name, "group name");
            this.fixtureId = fixtureId == null || fixtureId.isBlank()
                ? null : fixtureId;
            this.assertions = Collections.unmodifiableList(
                new ArrayList<>(assertions));
            this.elapsedMillis = Math.max(0L, elapsedMillis);
        }

        public String getName() { return name; }
        public String getFixtureId() { return fixtureId; }
        public List<Assertion> getAssertions() { return assertions; }
        public long getElapsedMillis() { return elapsedMillis; }

        public int getTotal()
        {
            return assertions.size();
        }

        public int getFailed()
        {
            int failed = 0;
            for(Assertion assertion : assertions) {
                if(!assertion.isPassed()) failed++;
            }
            return failed;
        }

        public int getPassed()
        {
            return getTotal() - getFailed();
        }
    }

    /** Mutable only while a suite is executing. */
    public static final class Builder
    {
        private final String suiteName;
        private final long startedAtMillis;
        private final List<Group> groups = new ArrayList<>();
        private String openGroupName;
        private long openGroupStartedAtMillis;
        private List<Assertion> openAssertions;
        private boolean finished;

        private Builder(String suiteName)
        {
            this.suiteName = requireText(suiteName, "suite name");
            startedAtMillis = System.currentTimeMillis();
        }

        public void startGroup(String name)
        {
            requireOpen();
            if(openAssertions != null) {
                throw new IllegalStateException(
                    "cannot start a group before finishing " + openGroupName);
            }
            openGroupName = requireText(name, "group name");
            openGroupStartedAtMillis = System.currentTimeMillis();
            openAssertions = new ArrayList<>();
        }

        public void record(String id, boolean passed, String detail)
        {
            requireOpen();
            if(openAssertions == null) {
                throw new IllegalStateException(
                    "assertion recorded without an open group: " + id);
            }
            openAssertions.add(new Assertion(id, passed, detail));
        }

        public Group finishGroup(String fixtureId)
        {
            requireOpen();
            if(openAssertions == null) {
                throw new IllegalStateException("no group is open");
            }
            Group group = new Group(openGroupName, fixtureId, openAssertions,
                System.currentTimeMillis() - openGroupStartedAtMillis);
            groups.add(group);
            openGroupName = null;
            openAssertions = null;
            return group;
        }

        public int getTotal()
        {
            int total = 0;
            for(Group group : groups) total += group.getTotal();
            if(openAssertions != null) total += openAssertions.size();
            return total;
        }

        public int getFailed()
        {
            int failed = 0;
            for(Group group : groups) failed += group.getFailed();
            if(openAssertions != null) {
                for(Assertion assertion : openAssertions) {
                    if(!assertion.isPassed()) failed++;
                }
            }
            return failed;
        }

        public GateResult finish()
        {
            requireOpen();
            if(openAssertions != null) {
                throw new IllegalStateException(
                    "cannot finish suite with open group " + openGroupName);
            }
            finished = true;
            return new GateResult(suiteName, groups,
                System.currentTimeMillis() - startedAtMillis, -1, -1,
                Collections.<Assertion>emptyList());
        }

        private void requireOpen()
        {
            if(finished) {
                throw new IllegalStateException("gate result is already final");
            }
        }
    }

    private final String suiteName;
    private final List<Group> groups;
    private final long elapsedMillis;
    private final int archivedTotal;
    private final int archivedPassed;
    private final List<Assertion> archivedFailures;

    private GateResult(String suiteName, List<Group> groups,
                       long elapsedMillis, int archivedTotal,
                       int archivedPassed, List<Assertion> archivedFailures)
    {
        this.suiteName = requireText(suiteName, "suite name");
        this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
        this.elapsedMillis = Math.max(0L, elapsedMillis);
        this.archivedTotal = archivedTotal;
        this.archivedPassed = archivedPassed;
        this.archivedFailures = Collections.unmodifiableList(
            new ArrayList<>(archivedFailures));
    }

    public static Builder builder(String suiteName)
    {
        return new Builder(suiteName);
    }

    /** Builds a canonical result from an immutable historical console log. */
    public static GateResult archived(String suiteName, int total, int passed,
                                      List<Assertion> failures)
    {
        if(total < 0 || passed < 0 || passed > total || failures == null ||
           total - passed != failures.size()) {
            throw new IllegalArgumentException(
                "archived totals and ordered failures must reconcile");
        }
        for(Assertion failure : failures) {
            if(failure == null || failure.isPassed()) {
                throw new IllegalArgumentException(
                    "archived failure list contains a passing assertion");
            }
        }
        return new GateResult(suiteName, Collections.<Group>emptyList(), 0L,
            total, passed, failures);
    }

    public static Assertion archivedFailure(String id, String detail)
    {
        return new Assertion(id, false, detail);
    }

    public String getSuiteName() { return suiteName; }
    public List<Group> getGroups() { return groups; }
    public long getElapsedMillis() { return elapsedMillis; }

    public int getTotal()
    {
        if(archivedTotal >= 0) return archivedTotal;
        int total = 0;
        for(Group group : groups) total += group.getTotal();
        return total;
    }

    public int getPassed()
    {
        if(archivedPassed >= 0) return archivedPassed;
        return getTotal() - getFailed();
    }

    public int getFailed()
    {
        if(archivedTotal >= 0) return archivedFailures.size();
        int failed = 0;
        for(Group group : groups) failed += group.getFailed();
        return failed;
    }

    public boolean isPassed() { return getFailed() == 0; }
    public int getExitCode() { return isPassed() ? 0 : 1; }

    public List<String> getFailureIds()
    {
        List<String> result = new ArrayList<>();
        if(archivedTotal >= 0) {
            for(Assertion failure : archivedFailures) {
                result.add(failure.getId());
            }
        }
        else {
            for(Group group : groups) {
                for(Assertion assertion : group.getAssertions()) {
                    if(!assertion.isPassed()) result.add(assertion.getId());
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    public String getFirstFailureId()
    {
        List<String> failures = getFailureIds();
        return failures.isEmpty() ? "NONE" : failures.get(0);
    }

    public String renderConsole()
    {
        StringBuilder output = new StringBuilder();
        output.append("Running ").append(suiteName).append('\n');
        if(archivedTotal >= 0) {
            for(Assertion failure : archivedFailures) {
                appendAssertion(output, failure);
            }
        }
        else {
            for(Group group : groups) {
                output.append('\n').append("== ").append(group.getName())
                    .append(" ==\n");
                for(Assertion assertion : group.getAssertions()) {
                    appendAssertion(output, assertion);
                }
                output.append("Group result: ").append(group.getPassed())
                    .append('/').append(group.getTotal())
                    .append(" passed in ").append(group.getElapsedMillis())
                    .append(" ms\n");
                if(group.getFixtureId() != null) {
                    output.append(group.getFixtureId()).append(' ')
                        .append(group.getFailed() == 0 ? "PASS" : "FAIL")
                        .append('\n');
                }
            }
        }
        output.append('\n').append("Passed ").append(getPassed()).append('/')
            .append(getTotal()).append(" tests in ").append(elapsedMillis)
            .append(" ms\n");
        output.append("GATE_RESULT schema=").append(SCHEMA)
            .append(" status=").append(isPassed() ? "PASS" : "FAIL")
            .append('\n');
        output.append("GATE_TOTAL ").append(getTotal()).append('\n');
        output.append("GATE_PASS ").append(getPassed()).append('\n');
        output.append("GATE_FAIL ").append(getFailed()).append('\n');
        output.append("GATE_FIRST_FAILURE ").append(getFirstFailureId())
            .append('\n');
        for(String failureId : getFailureIds()) {
            output.append("GATE_FAILURE_ID ").append(failureId).append('\n');
        }
        return output.toString();
    }

    public String renderJson()
    {
        StringBuilder output = new StringBuilder();
        output.append("{\n");
        appendJsonString(output, "schema", SCHEMA, true);
        appendJsonString(output, "suite", suiteName, true);
        appendJsonString(output, "status", isPassed() ? "PASS" : "FAIL", true);
        output.append("  \"total\": ").append(getTotal()).append(",\n");
        output.append("  \"pass\": ").append(getPassed()).append(",\n");
        output.append("  \"fail\": ").append(getFailed()).append(",\n");
        appendJsonString(output, "firstFailureId", getFirstFailureId(), true);
        output.append("  \"failureIds\": [");
        List<String> failures = getFailureIds();
        for(int index = 0; index < failures.size(); index++) {
            if(index > 0) output.append(',');
            output.append('\n').append("    \"")
                .append(jsonEscape(failures.get(index))).append('"');
        }
        if(!failures.isEmpty()) output.append('\n').append("  ");
        output.append("]\n");
        output.append("}\n");
        return output.toString();
    }

    public String renderMarkdown()
    {
        StringBuilder output = new StringBuilder();
        output.append("# Gate result\n\n");
        output.append("- Schema: `").append(SCHEMA).append("`\n");
        output.append("- Suite: `").append(markdownEscape(suiteName))
            .append("`\n");
        output.append("- Status: `").append(isPassed() ? "PASS" : "FAIL")
            .append("`\n");
        output.append("- Total: `").append(getTotal()).append("`\n");
        output.append("- Passed: `").append(getPassed()).append("`\n");
        output.append("- Failed: `").append(getFailed()).append("`\n");
        output.append("- First failure: `")
            .append(markdownEscape(getFirstFailureId())).append("`\n\n");
        output.append("## Failure IDs\n\n");
        if(getFailureIds().isEmpty()) {
            output.append("None.\n");
        }
        else {
            for(String failureId : getFailureIds()) {
                output.append("- `").append(markdownEscape(failureId))
                    .append("`\n");
            }
        }
        return output.toString();
    }

    public void printConsole(PrintStream stream)
    {
        if(stream == null) throw new IllegalArgumentException("stream is required");
        stream.print(renderConsole());
    }

    public void writeArtifacts(Path directory) throws IOException
    {
        if(directory == null) {
            throw new IllegalArgumentException("artifact directory is required");
        }
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("gate-result.console.txt"),
            renderConsole(), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("gate-result.json"), renderJson(),
            StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("gate-result.md"), renderMarkdown(),
            StandardCharsets.UTF_8);
    }

    private static void appendAssertion(StringBuilder output,
                                        Assertion assertion)
    {
        output.append(assertion.isPassed() ? "PASS " : "FAIL ")
            .append(assertion.getId());
        if(!assertion.isPassed() && !assertion.getDetail().isBlank()) {
            output.append(" - ").append(assertion.getDetail());
        }
        output.append('\n');
    }

    private static void appendJsonString(StringBuilder output, String key,
                                         String value, boolean comma)
    {
        output.append("  \"").append(jsonEscape(key)).append("\": \"")
            .append(jsonEscape(value)).append('"');
        if(comma) output.append(',');
        output.append('\n');
    }

    private static String jsonEscape(String value)
    {
        StringBuilder escaped = new StringBuilder();
        for(int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            switch(ch) {
                case '\\': escaped.append("\\\\"); break;
                case '"': escaped.append("\\\""); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default:
                    if(ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int)ch));
                    }
                    else escaped.append(ch);
            }
        }
        return escaped.toString();
    }

    private static String markdownEscape(String value)
    {
        return value.replace("`", "\\`");
    }

    private static String requireText(String value, String label)
    {
        if(value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }
}
