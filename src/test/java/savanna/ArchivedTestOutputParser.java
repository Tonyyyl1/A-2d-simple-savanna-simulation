package savanna;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts an immutable historical AllTests console log into one GateResult. */
public final class ArchivedTestOutputParser
{
    private static final Pattern FAILURE = Pattern.compile(
        "^FAIL (.+?) - (.+)$");
    private static final Pattern TOTAL = Pattern.compile(
        "^Passed ([0-9]+)/([0-9]+) tests(?: in [0-9]+ ms)?$");

    private ArchivedTestOutputParser() {}

    public static GateResult parse(Path path) throws IOException
    {
        if(path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException(
                "archived test output must be a regular file");
        }
        List<GateResult.Assertion> failures = new ArrayList<>();
        int passed = -1;
        int total = -1;
        for(String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            Matcher failure = FAILURE.matcher(line);
            if(failure.matches()) {
                failures.add(GateResult.archivedFailure(failure.group(1),
                    failure.group(2)));
                continue;
            }
            Matcher summary = TOTAL.matcher(line);
            if(summary.matches()) {
                passed = Integer.parseInt(summary.group(1));
                total = Integer.parseInt(summary.group(2));
            }
        }
        if(total < 0 || passed < 0) {
            throw new IllegalStateException(
                "archived output has no canonical final test total");
        }
        return GateResult.archived(path.getFileName().toString(), total,
            passed, failures);
    }
}
