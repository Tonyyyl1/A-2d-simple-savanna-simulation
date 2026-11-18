package savanna;

/** The one fixed PF1-aF seed-2111 x100 observer OFF/ON transparency run. */
public final class Pf1aTransparencyProbe
{
    private static final int STEPS = 100;
    private static final long SEED = 2111L;

    private Pf1aTransparencyProbe()
    {
    }

    public static void main(String[] args)
    {
        if(args.length != 0) {
            throw new IllegalArgumentException(
                "PF1-aF transparency budget is fixed; no arguments allowed");
        }
        System.out.println("PF1AF_TRANSPARENCY_RUN schema=1 seed=" + SEED +
            " terrainSeed=" + Pf1aProbeSupport.TERRAIN_SEED +
            " steps=" + STEPS + " order=OFF_THEN_ON");
        Pf1aProbeSupport.RunTrace baseline =
            Pf1aProbeSupport.run(SEED, STEPS, false, false);
        Pf1aProbeSupport.RunTrace observed =
            Pf1aProbeSupport.run(SEED, STEPS, true, true);
        baseline.requireComplete();
        observed.requireComplete();
        Pf1aProbeSupport.requireExact("legacy ecology projection",
            baseline.fingerprints, observed.fingerprints, STEPS);
        Pf1aProbeSupport.requireExact("position plan",
            baseline.planDigests, observed.planDigests, STEPS);
        Pf1aProbeSupport.requireExact("event ledger",
            baseline.eventDigests, observed.eventDigests, STEPS);
        Pf1aProbeSupport.requireExact("transition ledger",
            baseline.transitionDigests, observed.transitionDigests, STEPS);
        Pf1aProbeSupport.requireExact("HUNT RNG ledger",
            baseline.huntRngDigests, observed.huntRngDigests, STEPS);
        Pf1aProbeSupport.requireExact("breeding RNG ledger",
            baseline.breedingRngDigests, observed.breedingRngDigests, STEPS);
        Pf1aProbeSupport.requireExact("population projection",
            baseline.populationDigests, observed.populationDigests, STEPS);
        Pf1aProbeSupport.requireExact("completed/viable projection",
            baseline.completedViable, observed.completedViable, STEPS);
        Pf1aProbeSupport.requireFrozenCheckpoints(baseline);
        Pf1aProbeSupport.requireFrozenCheckpoints(observed);
        String baselineHash = Pf1aProbeSupport.sequenceHash(
            baseline.fingerprints);
        String observedHash = Pf1aProbeSupport.sequenceHash(
            observed.fingerprints);
        if(!Pf1aProbeSupport.FROZEN_2111_100_SEQUENCE_HASH.equals(
               baselineHash) ||
           !baselineHash.equals(observedHash)) {
            throw new IllegalStateException(
                "PF1-aF transparency differs from frozen D2 sequence");
        }
        System.out.println("PF1AF_TRANSPARENCY_2111_100 PASS " +
            "observerOffOnExact=100/100 frozenD2PerStepExact=100/100 " +
            "sequenceHash=" + observedHash + " final=" +
            observed.fingerprints.get(STEPS - 1));
    }
}
