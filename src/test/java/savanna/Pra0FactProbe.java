package savanna;

import java.util.List;

/** Fixed-budget test-only PRA-0 observer OFF/ON fact probe. */
public final class Pra0FactProbe
{
    private static final String CORE_JAR_SHA256 =
        "8b1f0e4cd3998aba90a45b203800b4d302798ba0b3bd6ef7d8bb74a9990be6a5";

    private Pra0FactProbe()
    {
    }

    public static void main(String[] args)
    {
        if(args.length != 2) {
            throw new IllegalArgumentException(
                "usage: Pra0FactProbe SEED OFF|ON");
        }
        long seed;
        try {
            seed = Long.parseLong(args[0]);
        }
        catch(NumberFormatException exception) {
            throw new IllegalArgumentException("PRA-0 seed must be decimal",
                exception);
        }
        if(seed != 1111L && seed != 2111L && seed != 3111L &&
           seed != 4111L && seed != 5111L) {
            throw new IllegalArgumentException(
                "PRA-0 seed is outside the frozen calibration/validation partition");
        }
        boolean observed;
        if("OFF".equals(args[1])) {
            observed = false;
        }
        else if("ON".equals(args[1])) {
            observed = true;
        }
        else {
            throw new IllegalArgumentException(
                "PRA-0 observer must be exactly OFF or ON");
        }

        System.out.println("PRA0_FACT_RUN schema=1 seed=" + seed +
            " terrainSeed=" + Pf1aProbeSupport.TERRAIN_SEED +
            " steps=1000 observer=" + args[1] +
            " coreJarSha256=" + CORE_JAR_SHA256);
        Pf1aProbeSupport.RunTrace trace =
            Pf1aProbeSupport.runPra0(seed, observed, observed);
        trace.requireComplete();
        emitTrace(trace, observed);
        System.out.println("PRA0_IDENTITY schema=1 seed=" + seed +
            " observer=" + args[1] + " completed=" + trace.completedSteps +
            " viable=" + trace.viable + " sequenceHash=" +
            Pf1aProbeSupport.sequenceHash(trace.fingerprints) +
            " final=" + trace.fingerprints.get(trace.completedSteps - 1) +
            " finalCounts=" + compact(trace.finalCounts.toString()) +
            " factSchemaHash=" + Pf1aProbeSupport.EXPECTED_SCHEMA_HASH +
            " coreJarSha256=" + CORE_JAR_SHA256);
    }

    private static void emitTrace(Pf1aProbeSupport.RunTrace trace,
                                  boolean observed)
    {
        List<String> viable = trace.completedViable;
        for(int index = 0; index < trace.completedSteps; index++) {
            String[] completion = viable.get(index).split(":", 2);
            System.out.println("PRA0_TRACE schema=1 seed=" +
                trace.ecologySeed + " observer=" +
                (observed ? "ON" : "OFF") +
                " step=" + (index + 1) +
                " legacyEcologyProjection=" + trace.fingerprints.get(index) +
                " positionPlanDigest=" + trace.planDigests.get(index) +
                " eventLedgerDigest=" + trace.eventDigests.get(index) +
                " transitionLedgerDigest=" +
                trace.transitionDigests.get(index) +
                " huntRandomLedger=" + trace.huntRngDigests.get(index) +
                " breedingRandomLedger=" +
                trace.breedingRngDigests.get(index) +
                " populationDigest=" + trace.populationDigests.get(index) +
                " completed=" + completion[0] +
                " viable=" + completion[1]);
        }
    }

    private static String compact(String value)
    {
        return value.replace(" ", "");
    }
}
