package savanna;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The audit switch: the three modes parse as documented, sampled steps are the multiples
 * of the period, and a child JVM per mode shows which steps really run their audits.
 */
public final class RuntimeAuditTests
{
    private RuntimeAuditTests()
    {
    }

    public static void main(String[] args)
    {
        if(args.length == 1 && "--probe".equals(args[0])) {
            probe();
            return;
        }
        TestSupport.startSuite("Runtime audit switch");
        run();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    public static void run()
    {
        TestSupport.startGroup("Runtime audit switch");
        testParse();
        testSampledSteps();
        testChildJvmModes();
        TestSupport.finishGroup("RUNTIME_AUDIT");
    }

    private static void testParse()
    {
        assertMode("on", true, 0);
        assertMode("true", true, 0);
        assertMode("off", false, 0);
        assertMode("OFF", false, 0);
        assertMode("false", false, 0);
        assertMode("every:50", false, 50);
        assertMode("EVERY:7", false, 7);
        assertMode(" every:50 ", false, 50);
        // every step is the same as on
        assertMode("every:1", true, 0);
        // anything unusable falls back to the safe default, full audit
        assertMode("every:0", true, 0);
        assertMode("every:-3", true, 0);
        assertMode("every:x", true, 0);
        assertMode("every:", true, 0);
        assertMode("sometimes", true, 0);
    }

    private static void assertMode(String text, boolean full, int period)
    {
        RuntimeAudit.Mode mode = RuntimeAudit.parse(text);
        TestSupport.assertTrue("audit mode '" + text + "' full=" + full + " period=" + period,
            mode.full == full && mode.period == period);
    }

    private static void testSampledSteps()
    {
        TestSupport.assertTrue("step 50 audited by every:50", RuntimeAudit.isSampledStep(50, 50));
        TestSupport.assertTrue("step 100 audited by every:50", RuntimeAudit.isSampledStep(100, 50));
        TestSupport.assertTrue("step 1 is always audited by every:50", RuntimeAudit.isSampledStep(1, 50));
        TestSupport.assertFalse("step 2 not audited by every:50", RuntimeAudit.isSampledStep(2, 50));
        TestSupport.assertFalse("step 49 not audited by every:50", RuntimeAudit.isSampledStep(49, 50));
        TestSupport.assertFalse("step 51 not audited by every:50", RuntimeAudit.isSampledStep(51, 50));
        TestSupport.assertTrue("step 0 audited by every:50", RuntimeAudit.isSampledStep(0, 50));
        TestSupport.assertFalse("period 0 never samples", RuntimeAudit.isSampledStep(50, 0));
        TestSupport.assertFalse("period 0 never samples step 1", RuntimeAudit.isSampledStep(1, 0));
        int audited = 0;
        for(int step = 1; step <= 1000; step++) {
            if(RuntimeAudit.isSampledStep(step, 50)) audited++;
        }
        TestSupport.assertEquals("every:50 audits step 1 and 20 multiples in 1000 steps", 21, audited);
    }

    private static void testChildJvmModes()
    {
        // each child starts fresh, so it resolves the mode from its own property
        TestSupport.assertEquals("child -Dsavanna.audit=on audits every step",
            "1,2,3,4,5,6,7,8,9,10,11,12", child("on"));
        TestSupport.assertEquals("child -Dsavanna.audit=off audits no step", "", child("off"));
        TestSupport.assertEquals("child -Dsavanna.audit=every:5 audits steps 1, 5 and 10",
            "1,5,10", child("every:5"));
        TestSupport.assertEquals("child -Dsavanna.audit=every:1 audits every step",
            "1,2,3,4,5,6,7,8,9,10,11,12", child("every:1"));
    }

    /** Runs the probe in a child JVM and returns the comma-separated audited steps. */
    private static String child(String mode)
    {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Djava.awt.headless=true");
        command.add("-Xmx128m");
        command.add("-Dsavanna.audit=" + mode);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(RuntimeAuditTests.class.getName());
        command.add("--probe");
        ProcessBuilder builder = new ProcessBuilder(command);
        // JAVA_TOOL_OPTIONS makes the JVM print a notice on stderr; only stdout carries the answer
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().remove("SAVANNA_AUDIT_FORCE");
        try {
            Process process = builder.start();
            String last = "";
            try(BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while((line = reader.readLine()) != null) {
                    last = line;
                }
            }
            process.waitFor();
            return last.trim();
        }
        catch(IOException | InterruptedException e) {
            throw new IllegalStateException("audit probe JVM failed: " + e, e);
        }
    }

    /** Child side: asks the switch after each of steps 1..12 and prints the audited ones. */
    private static void probe()
    {
        StringBuilder audited = new StringBuilder();
        for(int step = 1; step <= 12; step++) {
            RuntimeAudit.beginStep(step);
            if(RuntimeAudit.enabled()) {
                if(audited.length() > 0) audited.append(',');
                audited.append(step);
            }
        }
        System.out.println(audited);
    }
}
