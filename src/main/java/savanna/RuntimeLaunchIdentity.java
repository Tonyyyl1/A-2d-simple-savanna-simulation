package savanna;

import java.util.Set;

/** Launch classification supplied by a fail-closed canonical launcher. */
public final class RuntimeLaunchIdentity
{
    public static final String CANDIDATE_CLASS_PROPERTY =
        "savanna.candidate.class";
    private static final Set<String> KNOWN_CLASSES = Set.of(
        "UNDECLARED", "WORKTREE", "ACCEPTED_BASELINE",
        "TRANSITIONAL_CANDIDATE", "INFRASTRUCTURE_CANDIDATE",
        "ECOLOGY_CANDIDATE", "RELEASE_CANDIDATE");

    private RuntimeLaunchIdentity()
    {
    }

    public static String currentCandidateClass()
    {
        String value = System.getProperty(CANDIDATE_CLASS_PROPERTY,
            "UNDECLARED");
        return requireKnownClass(value);
    }

    public static String requireKnownClass(String value)
    {
        String normalized = value == null ? "UNDECLARED" : value.trim();
        if(!KNOWN_CLASSES.contains(normalized)) {
            throw new IllegalStateException("Unknown runtime candidate class: " +
                                            normalized);
        }
        return normalized;
    }
}
