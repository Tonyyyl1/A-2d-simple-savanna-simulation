package savanna;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/** V24-GATE-1 pure fixtures. This class never constructs a Simulator. */
public final class V24Gate1Tests
{
    private static final Path FAILED_OUTPUT = Path.of("artifacts", "candidates",
        "failed", "a1a-r2r-br1p-predator-recruitment-20260802-default-regression",
        "evidence", "ALL_TESTS_DEFAULT.txt");
    private static final Path ACCEPTED_OUTPUT = Path.of("artifacts", "candidates",
        "accepted", "core-rebase-reach-and-graze-20260802", "evidence",
        "ALL_TESTS.txt");
    private static final Path PROFILE_DIRECTORY = Path.of("gate-profiles");
    private static final Path MAIN_SOURCE = Path.of("src", "main", "java",
        "savanna");
    private static final String CORE_SHA =
        "8f7e15f14d7ffad2d7842f77dd69f234994ee8ee13ab9593abf53cb257f49557";
    private static final String FRAME_SHA =
        "5935d04308f69920ce8473df4dec97f962535fe67531a23f57899904cb55a22c";
    private static final String BR1P2_SHA =
        "dc15c5ee052758d37d5aa8992ebefc2e21747246f433477f527114f019457ad2";
    private static final List<String> SIX_FAILURES = List.of(
        "D2 F03 shared classifier reconciles each active HUNT",
        "D2 F05 keeps frozen R2 step-300 fingerprint",
        "A1BB0 F12 production source caller count is exactly zero",
        "1000 step stability completed requested steps",
        "1000 step stability keeps all species alive",
        "1000 step stability final balance stays within limit");

    private V24Gate1Tests() {}

    public static void main(String[] args) throws Exception
    {
        TestSupport.startSuite("V24-GATE-1 truthful gate fixtures");
        historicalLedgerFixtures();
        truthfulReporterFixtures();
        profileAndManifestFixtures();
        sameFrameAndDifferenceLedgerFixtures();
        a1bAstProfileFixtures();
        failFastPipelineFixtures();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    private static void historicalLedgerFixtures() throws Exception
    {
        TestSupport.startGroup("GATE-1 archived six-failure ledger");
        GateResult failed = ArchivedTestOutputParser.parse(FAILED_OUTPUT);
        TestSupport.assertEquals("V24 G01 archived total is exact", 1009,
            failed.getTotal());
        TestSupport.assertEquals("V24 G01 archived pass count is exact", 1003,
            failed.getPassed());
        TestSupport.assertEquals("V24 G01 archived failure count is exact", 6,
            failed.getFailed());
        TestSupport.assertEquals("V24 G01 six failure IDs stay ordered",
            SIX_FAILURES, failed.getFailureIds());
        TestSupport.assertEquals("V24 G01 true first failure is D2 F03",
            SIX_FAILURES.get(0), failed.getFirstFailureId());
        String failedBytes = Files.readString(FAILED_OUTPUT,
            StandardCharsets.UTF_8);
        TestSupport.assertEquals("V24 G01 immutable failed output hash is exact",
            "16ac4e8c9261a0b3b721d8e36de9b3785e7085cb45786cd1ab075da2e08485e0",
            V24D2Contract.digest(failedBytes));

        GateResult accepted = ArchivedTestOutputParser.parse(ACCEPTED_OUTPUT);
        TestSupport.assertEquals("V24 G01 accepted baseline remains 875 total",
            875, accepted.getTotal());
        TestSupport.assertEquals("V24 G01 accepted baseline remains 875 pass",
            875, accepted.getPassed());
        TestSupport.assertEquals("V24 G01 accepted baseline has zero failures",
            0, accepted.getFailed());

        GateArtifactSummary authority = GateArtifactSummary.fromResult(failed);
        TestSupport.assertEquals("V24 G01 console summary round-trips exactly",
            authority, GateArtifactSummary.fromConsole(failed.renderConsole()));
        TestSupport.assertEquals("V24 G01 JSON summary round-trips exactly",
            authority, GateArtifactSummary.fromJson(failed.renderJson()));
        TestSupport.assertEquals("V24 G01 Markdown summary round-trips exactly",
            authority, GateArtifactSummary.fromMarkdown(
                failed.renderMarkdown()));
        Path malformed = Files.createTempFile("v24-archived-ledger-fault-",
            ".txt");
        Files.writeString(malformed,
            "FAIL injected historical fault - expected red\n" +
            "Passed 1/1 tests in 0 ms\n", StandardCharsets.UTF_8);
        TestSupport.assertTrue("V24 G01 unreconciled archive total is red",
            throwsException(() -> ArchivedTestOutputParser.parse(malformed)));
        TestSupport.finishGroup("V24_ARCHIVED_LEDGER_FIXTURES");
    }

    private static void truthfulReporterFixtures() throws Exception
    {
        TestSupport.startGroup("GATE-1 truthful group/process reporter");
        ProcessRun failed = runTestSupportChild("FAIL");
        TestSupport.assertTrue("V24 G02 injected failure exits nonzero",
            failed.exitCode != 0);
        TestSupport.assertFalse("V24 G02 failure prints no FIXTURES PASS",
            failed.console.contains("V24_TEST_SUPPORT_FIXTURES PASS"));
        TestSupport.assertTrue("V24 G02 failure prints one truthful banner",
            occurrences(failed.console,
                "V24_TEST_SUPPORT_FIXTURES FAIL") == 1);
        GateArtifactSummary failedAuthority = GateArtifactSummary.fromConsole(
            failed.console);
        TestSupport.assertEquals("V24 G02 failed JSON shares one authority",
            failedAuthority, GateArtifactSummary.fromJson(
                Files.readString(failed.outputDirectory.resolve(
                    "gate-result.json"), StandardCharsets.UTF_8)));
        TestSupport.assertEquals("V24 G02 failed Markdown shares one authority",
            failedAuthority, GateArtifactSummary.fromMarkdown(
                Files.readString(failed.outputDirectory.resolve(
                    "gate-result.md"), StandardCharsets.UTF_8)));
        TestSupport.assertEquals("V24 G02 failed first ID is exact",
            "V24 G02 injected assertion", failedAuthority.getFirstFailureId());

        ProcessRun passed = runTestSupportChild("PASS");
        TestSupport.assertEquals("V24 G02 zero failure exits zero", 0,
            passed.exitCode);
        TestSupport.assertEquals("V24 G02 PASS banner prints exactly once", 1,
            occurrences(passed.console, "V24_TEST_SUPPORT_FIXTURES PASS"));
        GateArtifactSummary passedAuthority = GateArtifactSummary.fromConsole(
            passed.console);
        TestSupport.assertEquals("V24 G02 passed total/pass/fail is exact",
            "1/1/0:NONE:[]", passedAuthority.toString());
        TestSupport.assertEquals("V24 G02 passed JSON shares one authority",
            passedAuthority, GateArtifactSummary.fromJson(Files.readString(
                passed.outputDirectory.resolve("gate-result.json"),
                StandardCharsets.UTF_8)));
        TestSupport.assertEquals("V24 G02 passed Markdown shares one authority",
            passedAuthority, GateArtifactSummary.fromMarkdown(Files.readString(
                passed.outputDirectory.resolve("gate-result.md"),
                StandardCharsets.UTF_8)));
        String corruptJson = failedAuthority.getTotal() == 1
            ? Files.readString(failed.outputDirectory.resolve(
                "gate-result.json"), StandardCharsets.UTF_8)
                .replace("\"total\": 1", "\"total\": 2") : "";
        TestSupport.assertTrue("V24 G02 inconsistent JSON counts are red",
            throwsException(() -> GateArtifactSummary.fromJson(corruptJson)));
        TestSupport.finishGroup("V24_TRUTHFUL_REPORTER_FIXTURES");
    }

    private static void profileAndManifestFixtures() throws Exception
    {
        TestSupport.startGroup("GATE-1 versioned profile and manifest fail-closed");
        V24GateProfile core = coreProfile();
        V24GateProfile frame = frameProfile();
        V24GateProfile br1p2 = br1p2Profile();
        TestSupport.assertEquals("V24 G03 CORE profile kind is exact",
            V24GateProfile.Kind.CORE_REBASE_TRANSPARENCY, core.getKind());
        TestSupport.assertEquals("V24 G03 FRAME profile kind is exact",
            V24GateProfile.Kind.FRAME_TRANSPARENCY, frame.getKind());
        TestSupport.assertEquals("V24 G03 BR1P2 profile kind is exact",
            V24GateProfile.Kind.BR1P2_SEMANTIC, br1p2.getKind());
        TestSupport.assertFalse("V24 G03 rejected BR1-P fingerprint is absent",
            Files.readString(PROFILE_DIRECTORY.resolve(
                V24GateProfile.BR1P2_FILE), StandardCharsets.UTF_8)
                .contains("e0676c79ef00f5fef993d7eb059acb262334e3423d34690f6d77ccf44911214b"));
        String sealer = Files.readString(Path.of("seal-candidate.sh"),
            StandardCharsets.UTF_8);
        TestSupport.assertTrue("V24 G03 future sealer emits manifest v2",
            sealer.contains("candidate-manifest-v2"));
        TestSupport.assertTrue("V24 G03 future sealer binds all gate fields",
            sealer.contains("gateProfileSha256") &&
            sealer.contains("gateProfileId") &&
            sealer.contains("gateProfileKind"));

        AtomicInteger validInvocations = new AtomicInteger();
        Path validDirectory = profileFixtureDirectory(core, false, false);
        Path validManifest = writeManifest(validDirectory, core, null, null,
            false);
        V24GateProfile verified = V24GateProfile.verifyCandidateThen(
            validManifest, validDirectory.resolve("profiles"),
            validInvocations::incrementAndGet);
        TestSupport.assertEquals("V24 G03 valid manifest selects one profile",
            core.getId(), verified.getId());
        TestSupport.assertEquals("V24 G03 valid profile reaches next stage once",
            1, validInvocations.get());

        assertManifestRejectedBeforeInvocation("missing-manifest", core,
            ManifestFault.MISSING_MANIFEST);
        assertManifestRejectedBeforeInvocation("missing-profile-field", core,
            ManifestFault.MISSING_PROFILE_FIELD);
        assertManifestRejectedBeforeInvocation("unknown-profile", core,
            ManifestFault.UNKNOWN_PROFILE);
        assertManifestRejectedBeforeInvocation("tampered-profile", core,
            ManifestFault.TAMPERED_PROFILE);
        assertManifestRejectedBeforeInvocation("unknown-kind", core,
            ManifestFault.UNKNOWN_KIND);
        TestSupport.finishGroup("V24_PROFILE_MANIFEST_FIXTURES");
    }

    private static void sameFrameAndDifferenceLedgerFixtures() throws Exception
    {
        TestSupport.startGroup("GATE-1 D2 same-frame and named difference ledger");
        V24D2Contract.BoundaryScenario accepted =
            V24D2Contract.frozenAcceptedConception();
        V24D2Contract.OpportunityFact decision = accepted.getDecision();
        TestSupport.assertTrue("V24 G04 boundary is adult female Cheetah",
            decision.isAdult() && "FEMALE".equals(decision.getSex()) &&
            "Cheetah".equals(decision.getSpecies()));
        TestSupport.assertClose("V24 G04 decision stamina is frozen at 100",
            100.0, decision.getCaptureStamina(), 0.0);
        TestSupport.assertEquals("V24 G04 unique Gazelle topology range is seven",
            7, decision.getRange());
        TestSupport.assertEquals("V24 G04 unique Gazelle count is one", 1,
            decision.getCandidateCount());
        TestSupport.assertTrue("V24 G04 accepted conception reuses exact opportunity",
            V24D2Contract.reconcile(decision, accepted.getExecution()).isExact());

        V24D2Contract.BoundaryScenario rejected =
            V24D2Contract.rejectedConceptionControl();
        TestSupport.assertTrue("V24 G04 rejected conception control stays exact",
            !rejected.isConceptionAccepted() &&
            V24D2Contract.reconcile(rejected.getDecision(),
                rejected.getExecution()).isExact());
        V24D2Contract.BoundaryScenario recomputed =
            V24D2Contract.liveStaminaRecomputeFault();
        V24D2Contract.Reconciliation recomputeResult =
            V24D2Contract.reconcile(recomputed.getDecision(),
                recomputed.getExecution());
        TestSupport.assertTrue("V24 G04 live-stamina recompute fault is red",
            !recomputeResult.isExact() &&
            recomputeResult.getViolations().contains("range") &&
            recomputeResult.getViolations().contains("candidate-digest"));
        TestSupport.assertEquals("V24 G04 recompute fault drops range to six", 6,
            recomputed.getExecution().getRange());

        TestSupport.assertFalse("V24 G04 opportunity-ID fault is red",
            V24D2Contract.reconcile(decision,
                decision.withOpportunityId(V24D2Contract.digest("fault-id")))
                .isExact());
        TestSupport.assertFalse("V24 G04 range fault is red",
            V24D2Contract.reconcile(decision, decision.withRange(6)).isExact());
        TestSupport.assertFalse("V24 G04 candidate-digest fault is red",
            V24D2Contract.reconcile(decision,
                decision.withCandidateDigest(V24D2Contract.digest("fault")))
                .isExact());
        TestSupport.assertFalse("V24 G04 candidate-count fault is red",
            V24D2Contract.reconcile(decision,
                decision.withCandidateCount(0)).isExact());

        V24GateProfile core = coreProfile();
        V24GateProfile frame = frameProfile();
        V24GateProfile br1p2 = br1p2Profile();
        TestSupport.assertTrue("V24 G05 CORE zero-difference ledger passes",
            V24D2Contract.verifyDifferenceLedger(core, Set.of()).isExact());
        Set<String> frameExact = Set.of("frame-phase-ledger",
            "hunt-opportunity-fact");
        TestSupport.assertTrue("V24 G05 FRAME exact named ledger passes",
            V24D2Contract.verifyDifferenceLedger(frame, frameExact).isExact());
        TestSupport.assertFalse("V24 G05 FRAME missing ledger item is red",
            V24D2Contract.verifyDifferenceLedger(frame,
                Set.of("frame-phase-ledger")).isExact());
        TestSupport.assertFalse("V24 G05 FRAME extra ledger item is red",
            V24D2Contract.verifyDifferenceLedger(frame, Set.of(
                "frame-phase-ledger", "hunt-opportunity-fact", "water"))
                .isExact());
        TestSupport.assertFalse("V24 G05 monolithic new fingerprint is red",
            V24D2Contract.verifyDifferenceLedger(frame, Set.of(
                "frame-phase-ledger", "hunt-opportunity-fact",
                "whole-simulation-fingerprint")).isExact());
        TestSupport.assertTrue("V24 G05 BR1P2 exact named ledger passes",
            V24D2Contract.verifyDifferenceLedger(br1p2, Set.of(
                "breeding-rng", "escrow-ledger", "reproduction-lifecycle"))
                .isExact());
        TestSupport.finishGroup("V24_D2_LEDGER_FIXTURES");
    }

    private static void a1bAstProfileFixtures() throws Exception
    {
        TestSupport.startGroup("GATE-1 A1b AST call-edge profiles");
        V24GateProfile core = coreProfile();
        V24GateProfile connected = br1p2Profile();
        A1bDependencyContract.Verification current =
            A1bDependencyContract.verify(MAIN_SOURCE, core);
        TestSupport.assertTrue("V24 G06 current CORE disconnected AST passes",
            current.isPassed());
        TestSupport.assertTrue("V24 G06 current CORE has zero transition owner",
            current.getScan().getTransitionOwners().isEmpty());
        TestSupport.assertTrue("V24 G06 current CORE has zero kernel consumer",
            current.getScan().getKernelConsumers().isEmpty());

        Path commentOnly = syntheticA1b(A1bFault.COMMENT_ONLY);
        TestSupport.assertTrue("V24 G06 comments cannot fool disconnected AST",
            A1bDependencyContract.verify(commentOnly, core).isPassed());
        Path oneCaller = syntheticA1b(A1bFault.DISCONNECTED_ONE_CALLER);
        TestSupport.assertFalse("V24 G06 disconnected profile rejects one caller",
            A1bDependencyContract.verify(oneCaller, core).isPassed());
        Path validConnected = syntheticA1b(A1bFault.CONNECTED_VALID);
        TestSupport.assertTrue("V24 G06 connected exact owner/allowlist passes",
            A1bDependencyContract.verify(validConnected, connected).isPassed());
        TestSupport.assertFalse("V24 G06 connected profile rejects zero owners",
            A1bDependencyContract.verify(syntheticA1b(
                A1bFault.CONNECTED_ZERO_OWNER), connected).isPassed());
        TestSupport.assertFalse("V24 G06 connected profile rejects two owners",
            A1bDependencyContract.verify(syntheticA1b(
                A1bFault.CONNECTED_TWO_OWNERS), connected).isPassed());
        TestSupport.assertFalse("V24 G06 connected profile rejects wrong owner",
            A1bDependencyContract.verify(syntheticA1b(
                A1bFault.CONNECTED_WRONG_OWNER), connected).isPassed());
        TestSupport.assertFalse("V24 G06 kernel forbidden dependency is red",
            A1bDependencyContract.verify(syntheticA1b(
                A1bFault.CONNECTED_FORBIDDEN), connected).isPassed());
        TestSupport.finishGroup("V24_A1B_AST_FIXTURES");
    }

    private static void failFastPipelineFixtures() throws Exception
    {
        TestSupport.startGroup("GATE-1 default-before-ecology ordering");
        GateResult failed = ArchivedTestOutputParser.parse(FAILED_OUTPUT);
        AtomicInteger failureInvocations = new AtomicInteger();
        GateResult returned = V24GatePipeline.runEcologyOnlyAfterDefault(
            failed, failureInvocations::incrementAndGet);
        TestSupport.assertEquals("V24 G07 failed default runs zero ecology", 0,
            failureInvocations.get());
        TestSupport.assertTrue("V24 G07 failed result identity is retained",
            returned == failed);

        GateResult passed = ArchivedTestOutputParser.parse(ACCEPTED_OUTPUT);
        AtomicInteger passInvocations = new AtomicInteger();
        V24GatePipeline.runEcologyOnlyAfterDefault(passed,
            passInvocations::incrementAndGet);
        TestSupport.assertEquals("V24 G07 passing control reaches next stage once",
            1, passInvocations.get());
        TestSupport.finishGroup("V24_FAIL_FAST_FIXTURES");
    }

    private static V24GateProfile coreProfile() throws IOException
    {
        return V24GateProfile.load(PROFILE_DIRECTORY.resolve(
            V24GateProfile.CORE_FILE), CORE_SHA);
    }

    private static V24GateProfile frameProfile() throws IOException
    {
        return V24GateProfile.load(PROFILE_DIRECTORY.resolve(
            V24GateProfile.FRAME_FILE), FRAME_SHA);
    }

    private static V24GateProfile br1p2Profile() throws IOException
    {
        return V24GateProfile.load(PROFILE_DIRECTORY.resolve(
            V24GateProfile.BR1P2_FILE), BR1P2_SHA);
    }

    private static ProcessRun runTestSupportChild(String mode) throws Exception
    {
        Path output = Files.createTempDirectory("v24-test-support-" +
            mode.toLowerCase() + '-');
        String java = Path.of(System.getProperty("java.home"), "bin", "java")
            .toString();
        Process process = new ProcessBuilder(java,
            "-Dsavanna.gate.output=" + output,
            "-cp", System.getProperty("java.class.path"),
            "savanna.TestSupportProcessFixture", mode)
            .redirectErrorStream(true).start();
        String console = new String(process.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        return new ProcessRun(exitCode, console, output);
    }

    private static Path profileFixtureDirectory(V24GateProfile profile,
                                                boolean tamper,
                                                boolean unknownKind)
        throws IOException
    {
        Path root = Files.createTempDirectory("v24-profile-fixture-");
        Path profiles = Files.createDirectories(root.resolve("profiles"));
        String content = Files.readString(profile.getPath(),
            StandardCharsets.UTF_8);
        if(tamper) content += "# fault-seed: tampered-profile\n";
        if(unknownKind) content = content.replace(
            "profile.kind=CORE_REBASE_TRANSPARENCY",
            "profile.kind=UNKNOWN_KIND_FAULT_SEED");
        Files.writeString(profiles.resolve(profile.getPath().getFileName()),
            content, StandardCharsets.UTF_8);
        return root;
    }

    private static Path writeManifest(Path root, V24GateProfile profile,
                                      String profileName, String profileHash,
                                      boolean omitProfileField)
        throws IOException
    {
        String name = profileName == null
            ? profile.getPath().getFileName().toString() : profileName;
        String hash = profileHash == null ? profile.getSha256() : profileHash;
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"schemaVersion\": \"candidate-manifest-v2\",\n");
        if(!omitProfileField) {
            json.append("  \"gateProfile\": \"").append(name)
                .append("\",\n");
        }
        json.append("  \"gateProfileSha256\": \"").append(hash)
            .append("\",\n")
            .append("  \"gateProfileId\": \"").append(profile.getId())
            .append("\",\n")
            .append("  \"gateProfileKind\": \"")
            .append(profile.getKind().name()).append("\"\n}\n");
        Path manifest = root.resolve("MANIFEST.json");
        Files.writeString(manifest, json.toString(), StandardCharsets.UTF_8);
        return manifest;
    }

    private static void assertManifestRejectedBeforeInvocation(
        String fixtureId, V24GateProfile profile, ManifestFault fault)
        throws Exception
    {
        boolean tamper = fault == ManifestFault.TAMPERED_PROFILE;
        boolean unknownKind = fault == ManifestFault.UNKNOWN_KIND;
        Path root = profileFixtureDirectory(profile, tamper, unknownKind);
        Path manifest;
        if(fault == ManifestFault.MISSING_MANIFEST) {
            manifest = root.resolve("missing-MANIFEST.json");
        }
        else {
            String profileName = fault == ManifestFault.UNKNOWN_PROFILE
                ? "v24-unknown-profile.properties" : null;
            String hash = unknownKind || tamper ? V24D2Contract.digest(
                Files.readString(root.resolve("profiles").resolve(
                    profile.getPath().getFileName()), StandardCharsets.UTF_8))
                : null;
            // A tamper fault keeps the frozen manifest hash, while unknown-kind
            // uses its matching hash so kind validation is the first red gate.
            if(tamper) hash = profile.getSha256();
            manifest = writeManifest(root, profile, profileName, hash,
                fault == ManifestFault.MISSING_PROFILE_FIELD);
        }
        AtomicInteger invocations = new AtomicInteger();
        boolean rejected = throwsException(() ->
            V24GateProfile.verifyCandidateThen(manifest,
                root.resolve("profiles"), invocations::incrementAndGet));
        TestSupport.assertTrue("V24 G03 " + fixtureId + " is rejected",
            rejected);
        TestSupport.assertEquals("V24 G03 " + fixtureId +
            " runs zero simulation stages", 0, invocations.get());
    }

    private static Path syntheticA1b(A1bFault fault) throws IOException
    {
        Path root = Files.createTempDirectory("v24-a1b-ast-");
        String kernel = "final class BirthResourceTransaction { " +
            "static Object transition(Object a, Object b) { return null; } }\n";
        if(fault == A1bFault.CONNECTED_FORBIDDEN) {
            kernel = "final class BirthResourceTransaction { Simulator bad; " +
                "static Object transition(Object a, Object b) { return null; } }\n";
        }
        Files.writeString(root.resolve("BirthResourceTransaction.java"), kernel,
            StandardCharsets.UTF_8);
        Files.writeString(root.resolve("BirthResourceTransactionState.java"),
            "final class BirthResourceTransactionState {}\n",
            StandardCharsets.UTF_8);

        if(fault == A1bFault.COMMENT_ONLY) {
            Files.writeString(root.resolve("CommentOnly.java"),
                "// BirthResourceTransaction.transition and Simulator are comments\n" +
                "final class CommentOnly { String text = \"BirthResourceTransaction\"; }\n",
                StandardCharsets.UTF_8);
            return root;
        }
        if(fault == A1bFault.DISCONNECTED_ONE_CALLER) {
            Files.writeString(root.resolve("OtherOwner.java"),
                ownerSource("OtherOwner", true), StandardCharsets.UTF_8);
            return root;
        }

        boolean pregnancyCalls = fault != A1bFault.CONNECTED_ZERO_OWNER &&
            fault != A1bFault.CONNECTED_WRONG_OWNER;
        Files.writeString(root.resolve("PredatorPregnancyState.java"),
            ownerSource("PredatorPregnancyState", pregnancyCalls),
            StandardCharsets.UTF_8);
        Files.writeString(root.resolve("PredatorReproductionFingerprint.java"),
            "final class PredatorReproductionFingerprint { " +
            "BirthResourceTransactionState state; }\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("SavannahAnimal.java"),
            "class SavannahAnimal { BirthResourceTransactionState state; }\n",
            StandardCharsets.UTF_8);
        if(fault == A1bFault.CONNECTED_TWO_OWNERS ||
           fault == A1bFault.CONNECTED_WRONG_OWNER) {
            Files.writeString(root.resolve("OtherOwner.java"),
                ownerSource("OtherOwner", true), StandardCharsets.UTF_8);
        }
        return root;
    }

    private static String ownerSource(String owner, boolean calls)
    {
        return "final class " + owner + " { BirthResourceTransaction state; " +
            (calls ? "void call() { BirthResourceTransaction.transition(null, null); }"
                   : "void noCall() {}") + " }\n";
    }

    private static boolean throwsException(ThrowingRunnable action)
    {
        try {
            action.run();
            return false;
        }
        catch(Exception | AssertionError expected) {
            return true;
        }
    }

    private static int occurrences(String value, String needle)
    {
        int count = 0;
        int offset = 0;
        while((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private enum ManifestFault
    {
        MISSING_MANIFEST,
        MISSING_PROFILE_FIELD,
        UNKNOWN_PROFILE,
        TAMPERED_PROFILE,
        UNKNOWN_KIND
    }

    private enum A1bFault
    {
        COMMENT_ONLY,
        DISCONNECTED_ONE_CALLER,
        CONNECTED_VALID,
        CONNECTED_ZERO_OWNER,
        CONNECTED_TWO_OWNERS,
        CONNECTED_WRONG_OWNER,
        CONNECTED_FORBIDDEN
    }

    @FunctionalInterface
    private interface ThrowingRunnable
    {
        void run() throws Exception;
    }

    private static final class ProcessRun
    {
        private final int exitCode;
        private final String console;
        private final Path outputDirectory;

        private ProcessRun(int exitCode, String console, Path outputDirectory)
        {
            this.exitCode = exitCode;
            this.console = console;
            this.outputDirectory = outputDirectory;
        }
    }
}
