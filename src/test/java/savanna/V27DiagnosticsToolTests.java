package savanna;

/** Contract tests for the deliberately narrow R1 diagnostics command surface. */
public final class V27DiagnosticsToolTests
{
    private V27DiagnosticsToolTests()
    {
    }

    public static void run()
    {
        acceptsFixedCompatibilityBenchmark();
        rejectsUnknownMode();
        rejectsDuplicateOutput();
        rejectsNonLegacyProfile();
        rejectsInvalidSeed();
        rejectsWrongBenchmarkRepeatCount();
        rejectsMissingReferenceForEquivalence();
    }

    private static void acceptsFixedCompatibilityBenchmark()
    {
        V27DiagnosticsTool.Options options = V27DiagnosticsTool.Options.parse(new String[] {
            "benchmark", "--reference-jar", "build/reference.jar", "--preset", "default3x",
            "--ecology-seed", "1111", "--terrain-profile", "LEGACY_V1",
            "--terrain-seed", "20260629", "--thirst", "off", "--warmup-steps", "200",
            "--measure-steps", "800", "--repeats", "3", "--out", "build/v27/new-result"
        });
        TestSupport.assertEquals("V27 diagnostics parses benchmark mode",
            V27DiagnosticsTool.Mode.BENCHMARK, options.mode);
        TestSupport.assertEquals("V27 diagnostics preserves fixed repeat contract", 3,
            options.repeats);
    }

    private static void rejectsUnknownMode()
    {
        rejects("unknown mode", new String[] {"analyze"});
    }

    private static void rejectsDuplicateOutput()
    {
        rejects("duplicate option", new String[] {"regional", "--preset", "default3x",
            "--ecology-seed", "1111", "--terrain-profile", "LEGACY_V1",
            "--terrain-seed", "20260629", "--thirst", "off", "--warmup-steps", "1",
            "--measure-steps", "1", "--out", "one", "--out", "two"});
    }

    private static void rejectsNonLegacyProfile()
    {
        V27DiagnosticsTool.Options options = V27DiagnosticsTool.Options.parse(new String[] {
            "regional", "--preset", "default3x", "--ecology-seed", "1111",
            "--terrain-profile", "PROCEDURAL_V2", "--terrain-seed", "20260629",
            "--thirst", "off", "--warmup-steps", "1", "--measure-steps", "1",
            "--out", "build/v27/new-result"
        });
        rejectsValidation("non-legacy profile", options);
    }

    private static void rejectsInvalidSeed()
    {
        rejects("invalid seed", new String[] {"regional", "--preset", "default3x",
            "--ecology-seed", "-1", "--terrain-profile", "LEGACY_V1",
            "--terrain-seed", "20260629", "--thirst", "off", "--warmup-steps", "1",
            "--measure-steps", "1", "--out", "build/v27/new-result"});
    }

    private static void rejectsWrongBenchmarkRepeatCount()
    {
        V27DiagnosticsTool.Options options = V27DiagnosticsTool.Options.parse(new String[] {
            "benchmark", "--reference-jar", "build/reference.jar", "--preset", "default3x",
            "--ecology-seed", "1111", "--terrain-profile", "LEGACY_V1",
            "--terrain-seed", "20260629", "--thirst", "off", "--warmup-steps", "1",
            "--measure-steps", "1", "--repeats", "2", "--out", "build/v27/new-result"
        });
        rejectsValidation("wrong benchmark repeat count", options);
    }

    private static void rejectsMissingReferenceForEquivalence()
    {
        rejects("missing reference", new String[] {"equivalence", "--preset", "default3x",
            "--ecology-seed", "1111", "--terrain-profile", "LEGACY_V1",
            "--terrain-seed", "20260629", "--thirst", "off", "--warmup-steps", "1",
            "--measure-steps", "1", "--out", "build/v27/new-result"});
    }

    private static void rejects(String name, String[] arguments)
    {
        try {
            V27DiagnosticsTool.Options.parse(arguments);
            throw new AssertionError("Expected rejection: " + name);
        }
        catch(IllegalArgumentException expected) {
            TestSupport.assertTrue("V27 diagnostics rejects " + name,
                expected.getMessage() != null && !expected.getMessage().isEmpty());
        }
    }

    private static void rejectsValidation(String name, V27DiagnosticsTool.Options options)
    {
        try {
            options.validate();
            throw new AssertionError("Expected validation rejection: " + name);
        }
        catch(IllegalArgumentException expected) {
            TestSupport.assertTrue("V27 diagnostics rejects " + name,
                expected.getMessage() != null && !expected.getMessage().isEmpty());
        }
    }
}
