package savanna;

import java.util.List;

/** The one fixed PF1-aF observer-ON seed-1111 x1000 fact run. */
public final class Pf1aFacts1111Probe
{
    private Pf1aFacts1111Probe()
    {
    }

    public static void main(String[] args)
    {
        if(args.length != 0) {
            throw new IllegalArgumentException(
                "PF1-aF seed-1111 fact budget is fixed; no arguments allowed");
        }
        List<String> frozen =
            Pf1aProbeSupport.loadFrozenReference(1111L);
        System.out.println("PF1AF_FACT_RUN schema=1 seed=1111 terrainSeed=" +
            Pf1aProbeSupport.TERRAIN_SEED +
            " steps=1000 observer=ON runOrdinal=1");
        Pf1aProbeSupport.RunTrace trace =
            Pf1aProbeSupport.run(1111L, 1000, true, true);
        trace.requireComplete();
        Pf1aProbeSupport.requireFrozenCheckpoints(trace);
        Pf1aProbeSupport.requireExact("frozen D2 legacy ecology projection",
            frozen, trace.fingerprints, 1000);
        String sequenceHash =
            Pf1aProbeSupport.sequenceHash(trace.fingerprints);
        System.out.println("PF1AF_IDENTITY seed=1111 completed=1000 " +
            "viable=true frozenD2PerStepExact=1000/1000 sequenceHash=" +
            sequenceHash + " final=" + trace.fingerprints.get(999));
    }
}
