package savanna;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Frozen 1111/2111 x1000 legacy-component transparency gate for FRAME-1.
 *
 * <p>The expected component sequence hashes were derived from the immutable
 * accepted PF1-aF traces. The new frame listener is enabled during each run;
 * only the profile-allowed frame ledger and HUNT opportunity facts are new.</p>
 */
public final class V24FrameTransparencyProbe
{
    private static final String PROFILE_SHA256 =
        "5935d04308f69920ce8473df4dec97f962535fe67531a23f57899904cb55a22c";

    private V24FrameTransparencyProbe()
    {
    }

    public static void main(String[] args) throws Exception
    {
        if(args.length != 0) {
            throw new IllegalArgumentException(
                "FRAME transparency budget is frozen; no arguments allowed");
        }
        V24GateProfile profile = V24GateProfile.load(Path.of("gate-profiles",
            V24GateProfile.FRAME_FILE), PROFILE_SHA256);
        if(profile.getKind() != V24GateProfile.Kind.FRAME_TRANSPARENCY) {
            throw new IllegalStateException(
                "FRAME transparency profile kind differs");
        }
        System.out.println("FRAME_TRANSPARENCY_START profile=" +
            profile.getId() + " profileSha256=" + profile.getSha256() +
            " seeds=1111,2111 terrainSeed=" +
            Pf1aProbeSupport.TERRAIN_SEED + " steps=1000 retries=0");

        runSeed(1111L, expected1111(), profile);
        runSeed(2111L, expected2111(), profile);
        System.out.println("FRAME_TRANSPARENCY_PASS profile=" +
            profile.getId() + " exactLegacyComponents=" +
            profile.getExactComponents().size() + "/" +
            profile.getExactComponents().size() +
            " allowedDifferences=" + profile.getAllowedDifferences());
    }

    private static void runSeed(long seed, Expected expected,
                                V24GateProfile profile)
    {
        FrameCollector collector = new FrameCollector();
        Pf1aProbeSupport.RunTrace trace =
            Pf1aProbeSupport.runFrameTransparency(seed, 1000, collector);
        trace.requireComplete();
        requireSequence(seed, "ecology", expected.ecology,
            trace.fingerprints);
        requireSequence(seed, "position", expected.position,
            trace.planDigests);
        requireSequence(seed, "event", expected.events,
            trace.eventDigests);
        requireSequence(seed, "transition", expected.transitions,
            trace.transitionDigests);
        requireSequence(seed, "hunt-rng", expected.huntRng,
            trace.huntRngDigests);
        requireSequence(seed, "breeding-rng", expected.breedingRng,
            trace.breedingRngDigests);
        requireSequence(seed, "population", expected.population,
            trace.populationDigests);
        requireSequence(seed, "completed-viable", expected.completedViable,
            trace.completedViable);
        collector.requireComplete(1000);
        if(profile.getExactComponents().size() != 11 ||
           profile.getAllowedDifferences().size() != 2) {
            throw new IllegalStateException(
                "FRAME profile component ledger is not exact");
        }
        System.out.println("FRAME_TRANSPARENCY_RESULT seed=" + seed +
            " completed=1000 viable=true legacySequenceExact=8/8" +
            " profileComponentsExact=11/11 frameSteps=" +
            collector.getCompletedSteps() + " frameLedgers=" +
            collector.getLedgerCount() + " duplicateLedgers=0" +
            " final=" + trace.fingerprints.get(999) + " result=PASS");
    }

    private static void requireSequence(long seed, String component,
                                        String expected, List<String> values)
    {
        if(values.size() != 1000) {
            throw new IllegalStateException("FRAME " + component +
                " trace incomplete for seed " + seed);
        }
        String actual = Pf1aProbeSupport.sequenceHash(values);
        if(!expected.equals(actual)) {
            throw new IllegalStateException("FRAME " + component +
                " differs from accepted legacy trace for seed " + seed +
                ": expected=" + expected + " actual=" + actual);
        }
    }

    private static Expected expected1111()
    {
        return new Expected(
            "67f08dea050fae0eb47ee7e2014100b36d272037e0de83478fe8300659845b1b",
            "bb4d0e8bd77a9eb7ba18510f393009c130451fb0c6fc2a98d7f02582f330ede4",
            "d3b399c7dcf9969bf3ebea02c08b41a6cec0bfc2d6f24fb8b6405ccd5b00dd18",
            "b72dae0bbc4f6a7e44a9e0216ce1c1db39673d20dc19d2bcc68525797be292f5",
            "15b4c403c8177a2d0ca3dc09d9ba4ee1c2d019a0693e8f66c6858fc46f5c04d5",
            "51ffa40eefe63af21c34b6ed8a63d3e220a250c7af7c5de6e47204b35a169d26",
            "266962959b05ca9246822fa2db4a60345547aa06c145f96ad88b632a2f1f6fb0",
            "f2258747aa6e8eca92db211271b6028332f5a0eb8c79e95a27798060104a956f");
    }

    private static Expected expected2111()
    {
        return new Expected(
            "73d7d2c694174877bd9edc8c40c5fba7023a8a13c05c199cf502a9b5df10be2c",
            "6cc469050c090bd7af2b4734653dd54cb66fc85aa47e28170f84053daf22bbd2",
            "b30f28b4983e782c7766ce332924fa0d30570a4bc658369451f676b37c5ac6bc",
            "770122827a421af4fd8c2d7a34109e14ca943ee8d51116523bdcdfc4cb555210",
            "f976575be95e7a2cd96790c1ed00febafd8007592f3605c4fa6396996163c1f3",
            "07a761bc81ecd82dc4a02311873289aace8448b3bac41f59460b597fee3fbc9c",
            "ad64adc3670af91550a324daf806dae1bcbb1dd89bddff4573cf9d4c470e0946",
            "f2258747aa6e8eca92db211271b6028332f5a0eb8c79e95a27798060104a956f");
    }

    private static final class Expected
    {
        private final String ecology;
        private final String position;
        private final String events;
        private final String transitions;
        private final String huntRng;
        private final String breedingRng;
        private final String population;
        private final String completedViable;

        Expected(String ecology, String position, String events,
                 String transitions, String huntRng, String breedingRng,
                 String population, String completedViable)
        {
            this.ecology = ecology;
            this.position = position;
            this.events = events;
            this.transitions = transitions;
            this.huntRng = huntRng;
            this.breedingRng = breedingRng;
            this.population = population;
            this.completedViable = completedViable;
        }
    }

    /** Bounded one-step identity set; no whole-run ledger graph is retained. */
    private static final class FrameCollector
        implements StepFrameLedgerListener
    {
        private final Set<Long> currentActors = new HashSet<>();
        private int currentStep;
        private int completedSteps;
        private long ledgerCount;
        private boolean duplicate;

        @Override
        public void onStepFrameLedger(StepFrameLedger ledger)
        {
            if(ledger == null || ledger.getStep() <= 0) {
                throw new IllegalStateException("invalid FRAME ledger");
            }
            if(currentStep != ledger.getStep()) {
                if(currentStep != 0) completedSteps++;
                currentStep = ledger.getStep();
                currentActors.clear();
            }
            duplicate = duplicate ||
                !currentActors.add(Long.valueOf(ledger.getActorId()));
            ledgerCount++;
        }

        void requireComplete(int expectedSteps)
        {
            int actualSteps = completedSteps + (currentStep == 0 ? 0 : 1);
            if(duplicate || actualSteps != expectedSteps || ledgerCount <= 0) {
                throw new IllegalStateException(
                    "FRAME listener ledger coverage is incomplete");
            }
            completedSteps = actualSteps;
        }

        int getCompletedSteps() { return completedSteps; }
        long getLedgerCount() { return ledgerCount; }
    }
}
