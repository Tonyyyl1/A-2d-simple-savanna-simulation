package savanna;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/** Fixed 100/1000-step OFF/ON transparency probe for V24-OBS-1B. */
public final class V24Obs1bTransparencyProbe
{
    private static final long TERRAIN_SEED = 20260629L;
    private static final int SPECIES = SpeciesRegistry.getFactories().size();

    private V24Obs1bTransparencyProbe()
    {
    }

    public static void main(String[] args)
    {
        if(args.length != 0) {
            throw new IllegalArgumentException(
                "OBS-1B transparency matrix is fixed");
        }
        verify(2111L, 100);
        verify(1111L, 1000);
        verify(2111L, 1000);
    }

    private static void verify(long seed, int steps)
    {
        Trace off = run(seed, steps, false);
        Trace on = run(seed, steps, true);
        require(off.fingerprints.equals(on.fingerprints),
            "observer changed ecology trace for seed " + seed +
            " steps " + steps);
        require(off.nextRandomLong == on.nextRandomLong,
            "observer changed RNG state for seed " + seed +
            " steps " + steps);
        int intervals = steps / LongHorizonTrophicStepFact.INTERVAL_STEPS;
        require(on.analysis.getSteps() == steps &&
                on.analysis.getStepRows() == steps * SPECIES &&
                on.analysis.getIntervalRows() == intervals * SPECIES &&
                on.analysis.getCompletedIntervals() == intervals &&
                on.completedIntervals == intervals,
            "observer analyzer/interval closure failed for seed " + seed);
        require(on.maxRetainedCurrentKeys == 0 &&
                on.retainedIntervalSpecies == SPECIES &&
                on.analysis.getMaxRetainedIntervalSpecies() == SPECIES,
            "observer retained unbounded step/interval state for seed " + seed);
        System.out.println("V24_OBS_1B_TRANSPARENCY seed=" + seed +
            " steps=" + steps + " ecologyExact=true rngExact=true" +
            " stepRows=" + on.analysis.getStepRows() +
            " intervalRows=" + on.analysis.getIntervalRows() +
            " boundedCurrentKeys=0 boundedIntervalSpecies=" + SPECIES +
            " analyzerRecomputed=true result=PASS");
    }

    private static Trace run(long seed, int steps, boolean observe)
    {
        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(seed).terrainSeed(TERRAIN_SEED).build();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        StringWriter writer = new StringWriter();
        LongHorizonTrophicLedger ledger = new LongHorizonTrophicLedger(writer);
        if(observe) simulator.addLongHorizonTrophicLedger(ledger);
        List<String> fingerprints = new ArrayList<>();
        int maxRetained = 0;
        for(int index = 0; index < steps; index++) {
            simulator.simulateOneStep();
            fingerprints.add(SimulationFingerprint.capture(simulator));
            maxRetained = Math.max(maxRetained,
                ledger.getRetainedCurrentStepKeyCount());
        }
        ledger.flush();
        LongHorizonTrophicLedgerAnalyzer.Result analysis = observe
            ? LongHorizonTrophicLedgerAnalyzer.analyze(writer.toString()) : null;
        return new Trace(fingerprints,
            simulator.getContext().getRandom().nextLong(), analysis,
            maxRetained, ledger.getRetainedIntervalSpeciesCount(),
            ledger.getCompletedIntervals());
    }

    private static void require(boolean value, String message)
    {
        if(!value) throw new IllegalStateException(message);
    }

    private static final class Trace
    {
        private final List<String> fingerprints;
        private final long nextRandomLong;
        private final LongHorizonTrophicLedgerAnalyzer.Result analysis;
        private final int maxRetainedCurrentKeys;
        private final int retainedIntervalSpecies;
        private final int completedIntervals;

        private Trace(List<String> fingerprints, long nextRandomLong,
                      LongHorizonTrophicLedgerAnalyzer.Result analysis,
                      int maxRetainedCurrentKeys,
                      int retainedIntervalSpecies, int completedIntervals)
        {
            this.fingerprints = fingerprints;
            this.nextRandomLong = nextRandomLong;
            this.analysis = analysis;
            this.maxRetainedCurrentKeys = maxRetainedCurrentKeys;
            this.retainedIntervalSpecies = retainedIntervalSpecies;
            this.completedIntervals = completedIntervals;
        }
    }
}
