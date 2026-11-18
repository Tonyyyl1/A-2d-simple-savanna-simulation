package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal round-trip view shared by console, JSON, and Markdown evidence. */
public final class GateArtifactSummary
{
    private final int total;
    private final int passed;
    private final int failed;
    private final String firstFailureId;
    private final List<String> failureIds;

    private GateArtifactSummary(int total, int passed, int failed,
                                String firstFailureId,
                                List<String> failureIds)
    {
        if(total < 0 || passed < 0 || failed < 0 || passed + failed != total ||
           firstFailureId == null || failureIds == null ||
           failed != failureIds.size() ||
           (!failureIds.isEmpty() &&
            !firstFailureId.equals(failureIds.get(0))) ||
           (failureIds.isEmpty() && !"NONE".equals(firstFailureId))) {
            throw new IllegalArgumentException(
                "gate artifact summary does not reconcile");
        }
        this.total = total;
        this.passed = passed;
        this.failed = failed;
        this.firstFailureId = firstFailureId;
        this.failureIds = Collections.unmodifiableList(
            new ArrayList<>(failureIds));
    }

    public static GateArtifactSummary fromResult(GateResult result)
    {
        return new GateArtifactSummary(result.getTotal(), result.getPassed(),
            result.getFailed(), result.getFirstFailureId(),
            result.getFailureIds());
    }

    public static GateArtifactSummary fromConsole(String console)
    {
        int total = integerLine(console, "GATE_TOTAL ");
        int passed = integerLine(console, "GATE_PASS ");
        int failed = integerLine(console, "GATE_FAIL ");
        String first = textLine(console, "GATE_FIRST_FAILURE ");
        List<String> failures = repeatedLines(console, "GATE_FAILURE_ID ");
        return new GateArtifactSummary(total, passed, failed, first, failures);
    }

    public static GateArtifactSummary fromJson(String json)
    {
        int total = jsonInteger(json, "total");
        int passed = jsonInteger(json, "pass");
        int failed = jsonInteger(json, "fail");
        String first = jsonString(json, "firstFailureId");
        Matcher array = Pattern.compile(
            "\\\"failureIds\\\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL)
            .matcher(json);
        if(!array.find()) {
            throw new IllegalArgumentException("JSON failureIds is missing");
        }
        List<String> failures = new ArrayList<>();
        Matcher value = Pattern.compile("\\\"((?:\\\\.|[^\\\"])*)\\\"")
            .matcher(array.group(1));
        while(value.find()) failures.add(jsonUnescape(value.group(1)));
        return new GateArtifactSummary(total, passed, failed, first, failures);
    }

    public static GateArtifactSummary fromMarkdown(String markdown)
    {
        int total = markdownInteger(markdown, "- Total: `");
        int passed = markdownInteger(markdown, "- Passed: `");
        int failed = markdownInteger(markdown, "- Failed: `");
        String first = markdownValue(markdown, "- First failure: `");
        List<String> failures = new ArrayList<>();
        boolean inFailures = false;
        for(String line : markdown.lines().toList()) {
            if("## Failure IDs".equals(line)) {
                inFailures = true;
                continue;
            }
            if(inFailures && line.startsWith("- `") && line.endsWith("`")) {
                failures.add(line.substring(3, line.length() - 1)
                    .replace("\\`", "`"));
            }
        }
        return new GateArtifactSummary(total, passed, failed, first, failures);
    }

    public int getTotal() { return total; }
    public int getPassed() { return passed; }
    public int getFailed() { return failed; }
    public String getFirstFailureId() { return firstFailureId; }
    public List<String> getFailureIds() { return failureIds; }

    @Override
    public boolean equals(Object other)
    {
        if(this == other) return true;
        if(!(other instanceof GateArtifactSummary)) return false;
        GateArtifactSummary that = (GateArtifactSummary)other;
        return total == that.total && passed == that.passed &&
            failed == that.failed &&
            firstFailureId.equals(that.firstFailureId) &&
            failureIds.equals(that.failureIds);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(total, passed, failed, firstFailureId, failureIds);
    }

    @Override
    public String toString()
    {
        return total + "/" + passed + "/" + failed + ':' + firstFailureId +
            ':' + failureIds;
    }

    private static int integerLine(String text, String prefix)
    {
        return Integer.parseInt(textLine(text, prefix));
    }

    private static String textLine(String text, String prefix)
    {
        List<String> values = repeatedLines(text, prefix);
        if(values.size() != 1) {
            throw new IllegalArgumentException(
                "expected exactly one line with prefix " + prefix);
        }
        return values.get(0);
    }

    private static List<String> repeatedLines(String text, String prefix)
    {
        List<String> values = new ArrayList<>();
        for(String line : text.lines().toList()) {
            if(line.startsWith(prefix)) values.add(line.substring(prefix.length()));
        }
        return values;
    }

    private static int jsonInteger(String json, String key)
    {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) +
            "\\\"\\s*:\\s*([0-9]+)").matcher(json);
        if(!matcher.find()) {
            throw new IllegalArgumentException("JSON integer is missing: " + key);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static String jsonString(String json, String key)
    {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) +
            "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
            .matcher(json);
        if(!matcher.find()) {
            throw new IllegalArgumentException("JSON string is missing: " + key);
        }
        return jsonUnescape(matcher.group(1));
    }

    private static String jsonUnescape(String value)
    {
        StringBuilder result = new StringBuilder();
        boolean escaping = false;
        for(int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if(!escaping && ch == '\\') {
                escaping = true;
            }
            else if(escaping) {
                switch(ch) {
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case '\\': result.append('\\'); break;
                    case '"': result.append('"'); break;
                    default: throw new IllegalArgumentException(
                        "unsupported JSON escape in gate artifact");
                }
                escaping = false;
            }
            else result.append(ch);
        }
        if(escaping) throw new IllegalArgumentException("trailing JSON escape");
        return result.toString();
    }

    private static int markdownInteger(String markdown, String prefix)
    {
        return Integer.parseInt(markdownValue(markdown, prefix));
    }

    private static String markdownValue(String markdown, String prefix)
    {
        for(String line : markdown.lines().toList()) {
            if(line.startsWith(prefix) && line.endsWith("`")) {
                return line.substring(prefix.length(), line.length() - 1)
                    .replace("\\`", "`");
            }
        }
        throw new IllegalArgumentException(
            "Markdown value is missing: " + prefix);
    }
}
