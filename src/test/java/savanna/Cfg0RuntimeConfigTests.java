package savanna;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Focused CFG0 contracts; these do not alter ecological decision inputs. */
public final class Cfg0RuntimeConfigTests
{
    private Cfg0RuntimeConfigTests() {}

    public static void run()
    {
        ObservationConfig defaults = ObservationConfig.defaults();
        TestSupport.assertEquals("CFG0 visible default remains 100", 100,
            defaults.getVisibleCadence());
        TestSupport.assertEquals("CFG0 report default remains 100", 100,
            defaults.getReportCadence());
        TestSupport.assertEquals("CFG0 chart default remains 100", 100,
            defaults.getChartCadence());
        TestSupport.assertEquals("CFG0 inspect tick default remains 33 ms", 33,
            DisplayConfig.defaults().getInspectTickMillis());
        TestSupport.assertTrue("CFG0 descriptors expose visible cadence",
            ConfigDescriptorRegistry.find("observation.visibleCadence") != null);
        TestSupport.assertTrue("CFG0 defaults have stable provenance hash",
            ResolvedRuntimeConfig.fromLegacy(SimulationConfig.default3x())
                .getConfigurationHash().length() == 64);
        SimulationConfig collisionA = SimulationConfig.builder()
            .creationMultiplier(1.001).build();
        SimulationConfig collisionB = SimulationConfig.builder()
            .creationMultiplier(1.002).build();
        TestSupport.assertEquals("CFG contract reproduces human description collision",
            collisionA.describe(), collisionB.describe());
        ResolvedRuntimeConfig resolvedA = ResolvedRuntimeConfig.fromLegacy(collisionA);
        ResolvedRuntimeConfig resolvedB = ResolvedRuntimeConfig.fromLegacy(collisionB);
        TestSupport.assertTrue("CFG contract separates lossless configuration hashes",
            !resolvedA.getConfigurationHash().equals(
                resolvedB.getConfigurationHash()));
        TestSupport.assertTrue("CFG canonical doubles use lossless hex form",
            resolvedA.getCanonicalForm().contains(
                "ecology.creationMultiplier=0x"));
        ResolvedRuntimeConfig roundTrip = ResolvedRuntimeConfig.fromCanonicalForm(
            resolvedA.getCanonicalForm());
        TestSupport.assertEquals("CFG canonical export/import is exact",
            resolvedA.getCanonicalForm(), roundTrip.getCanonicalForm());
        TestSupport.assertEquals("CFG canonical hash survives export/import",
            resolvedA.getConfigurationHash(), roundTrip.getConfigurationHash());

        Locale originalLocale = Locale.getDefault();
        String frenchHash;
        String japaneseHash;
        try {
            Locale.setDefault(Locale.FRANCE);
            frenchHash = ResolvedRuntimeConfig.fromLegacy(collisionA)
                .getConfigurationHash();
            Locale.setDefault(Locale.JAPAN);
            japaneseHash = ResolvedRuntimeConfig.fromLegacy(collisionA)
                .getConfigurationHash();
        }
        finally {
            Locale.setDefault(originalLocale);
        }
        TestSupport.assertEquals("CFG canonical hash is locale independent",
            frenchHash, japaneseHash);
        TestSupport.assertTrue("CFG rejects zero ecology multiplier",
            rejects(() -> SimulationConfig.builder()
                .creationMultiplier(0.0).build()));
        TestSupport.assertTrue("CFG rejects NaN ecology multiplier",
            rejects(() -> SimulationConfig.builder()
                .breedingMultiplier(Double.NaN).build()));
        TestSupport.assertTrue("CFG rejects infinite ecology multiplier",
            rejects(() -> SimulationConfig.builder()
                .diseaseFatalityMultiplier(Double.POSITIVE_INFINITY).build()));
        TestSupport.assertTrue("CFG rejects out-of-range map scale",
            rejects(() -> SimulationConfig.builder().mapScale(0).build()));
        TestSupport.assertTrue("CFG rejects missing canonical field",
            rejects(() -> ResolvedRuntimeConfig.fromCanonicalForm(
                resolvedA.getCanonicalForm().replaceFirst("schema=3\\n", ""))));
        TestSupport.assertTrue("CFG rejects duplicate canonical field",
            rejects(() -> ResolvedRuntimeConfig.fromCanonicalForm(
                resolvedA.getCanonicalForm() + "schema=3\\n")));
        TestSupport.assertTrue("CFG rejects missing policy values",
            rejects(() -> SimulationConfig.builder().hydrationPolicy(null)));

        TestSupport.assertTrue("CFG0 rejects zero cadence",
            rejects(() -> new ObservationConfig(0, 11, 13, 17, 3, 7, 96, 600, 5)));
        TestSupport.assertTrue("CFG0 rejects unordered heatmap windows",
            rejects(() -> new ObservationConfig(7, 11, 13, 17, 7, 3, 96, 600, 5)));
        TestSupport.assertTrue("CFG0 rejects invalid display tick",
            rejects(() -> new DisplayConfig(24, 0, 450, 2400, 2.5)));

        ObservationConfig primes = new ObservationConfig(7, 11, 13, 17,
            3, 7, 96, 600, 5);
        ObservationSchedule schedule = new ObservationSchedule(primes);
        TestSupport.assertEquals("CFG0 visible prime cadence checkpoints",
            Arrays.asList(0, 7, 14, 21, 28, 29),
            schedule.checkpoints(29, primes.getVisibleCadence()));
        TestSupport.assertEquals("CFG0 report prime cadence checkpoints",
            Arrays.asList(0, 11, 22, 29),
            schedule.checkpoints(29, primes.getReportCadence()));
        TestSupport.assertEquals("CFG0 chart prime cadence checkpoints",
            Arrays.asList(0, 13, 26, 29),
            schedule.checkpoints(29, primes.getChartCadence()));
        TestSupport.assertEquals("CFG0 visible interval before boundary", 22,
            schedule.visibleIntervalStart(28));
        TestSupport.assertEquals("CFG0 visible interval starts on boundary", 29,
            schedule.visibleIntervalStart(29));
        TestSupport.assertEquals("CFG0 final boundary is not duplicated",
            Arrays.asList(0, 7, 14, 21, 28),
            schedule.checkpoints(28, primes.getVisibleCadence()));

        ResolvedRuntimeConfig runtime = new ResolvedRuntimeConfig(
            SimulationConfig.baseline(), primes, DisplayConfig.defaults());
        Field field = new Field(2, 2);
        SimulationContext context = new SimulationContext(2, 2, runtime);
        context.startStep(7, field);
        TestSupport.assertEquals("CFG0 first prime frame starts at one", 1,
            context.getFrameEventSummary().getStartStep());
        context.startStep(8, field);
        TestSupport.assertEquals("CFG0 next prime frame starts at eight", 8,
            context.getFrameEventSummary().getStartStep());

        Randomizer.reset();
        Simulator legacy = new Simulator(SimulationConfig.default3x(), false);
        legacy.setVerbose(false);
        for(int step = 0; step < 10; step++) legacy.simulateOneStep();
        String legacyFingerprint = SimulationFingerprint.capture(legacy);
        Randomizer.reset();
        Simulator configured = new Simulator(new ResolvedRuntimeConfig(
            SimulationConfig.default3x(), primes, DisplayConfig.defaults()), false);
        configured.setVerbose(false);
        for(int step = 0; step < 10; step++) configured.simulateOneStep();
        TestSupport.assertEquals("CFG0 observation config leaves ecology fingerprint unchanged",
            legacyFingerprint, SimulationFingerprint.capture(configured));
        ObservationConfig alternateObservation = new ObservationConfig(5, 9, 11, 17,
            3, 7, 64, 300, 3);
        Randomizer.reset();
        Simulator alternateConfigured = new Simulator(new ResolvedRuntimeConfig(
            SimulationConfig.default3x(), alternateObservation,
            new DisplayConfig(0, 17, 0, 1200, 1.75)), false);
        alternateConfigured.setVerbose(false);
        for(int step = 0; step < 10; step++) alternateConfigured.simulateOneStep();
        TestSupport.assertEquals("CFG0 third observation/display config preserves ecology",
            legacyFingerprint, SimulationFingerprint.capture(alternateConfigured));

        RenderFramePublisher publisher = new RenderFramePublisher();
        Field transportField = new Field(2, 2);
        SimulationContext transportContext = new SimulationContext(2, 2);
        transportContext.startStep(0, transportField);
        RenderFrame firstFrame = RenderFrame.capture(0, transportField,
            transportContext, 2, 2);
        transportContext.startStep(1, transportField);
        RenderFrame secondFrame = RenderFrame.capture(1, transportField,
            transportContext, 2, 2);
        publisher.publish(firstFrame);
        publisher.publish(secondFrame);
        TestSupport.assertEquals("CFG0 display publisher retains newest frame",
            secondFrame, publisher.latest());
        TestSupport.assertEquals("CFG0 display publisher counts publishes", 2,
            (int)publisher.getPublishedFrameCount());
        TestSupport.assertEquals("CFG0 display publisher counts overwritten frame", 1,
            (int)publisher.getDroppedFrameCount());

        RecordingProbe recorder = new RecordingProbe(primes);
        Field recorderField = new Field(2, 2);
        SimulationContext recorderContext = new SimulationContext(2, 2, runtime);
        recorder.start(recorderField, recorderContext);
        for(int step = 1; step <= 26; step++) {
            recorderContext.startStep(step, recorderField);
            recorder.recordStep(step, recorderField, recorderContext);
        }
        TestSupport.assertEquals("CFG0 report sink keeps report cadence only",
            Arrays.asList(0, 11, 22), recorder.reportSteps);
        TestSupport.assertEquals("CFG0 chart sink uses independent cadence",
            Arrays.asList(0, 13, 26), recorder.chartSteps);

        Simulator boundaryEnd = new Simulator(runtime, false);
        boundaryEnd.setVerbose(false);
        boundaryEnd.enableRenderFrameCapture();
        boundaryEnd.simulate(28);
        TestSupport.assertEquals("CFG0 final boundary publishes once", 29,
            (int)boundaryEnd.getRenderFramePublisher().getPublishedFrameCount());
        Simulator partialEnd = new Simulator(runtime, false);
        partialEnd.setVerbose(false);
        partialEnd.enableRenderFrameCapture();
        partialEnd.simulate(29);
        TestSupport.assertEquals("CFG0 off-boundary final publishes one forced frame", 31,
            (int)partialEnd.getRenderFramePublisher().getPublishedFrameCount());
    }

    private static boolean rejects(Runnable action)
    {
        try {
            action.run();
            return false;
        }
        catch(IllegalArgumentException expected) {
            return true;
        }
    }

    private static final class RecordingProbe extends StepReportRecorder
    {
        private final List<Integer> reportSteps = new ArrayList<>();
        private final List<Integer> chartSteps = new ArrayList<>();

        private RecordingProbe(ObservationConfig config)
        {
            super("cfg0-recorder-probe-unused.html", config);
        }

        protected void onStepRecorded(StepSnapshot snapshot)
        {
            reportSteps.add(snapshot.getStep());
        }

        protected void onChartRecorded(StepSnapshot snapshot)
        {
            chartSteps.add(snapshot.getStep());
        }
    }
}
