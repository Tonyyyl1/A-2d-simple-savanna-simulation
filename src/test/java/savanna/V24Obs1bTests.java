package savanna;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/** Default-suite fixtures for the V24-OBS-1B streaming trophic ledger. */
public final class V24Obs1bTests
{
    private static final int STEPS = 100;
    private static final int SPECIES = SpeciesRegistry.getFactories().size();

    private V24Obs1bTests()
    {
    }

    public static void main(String[] args)
    {
        run();
        System.out.println("V24_OBS_1B_FIXTURES PASS steps=" + STEPS +
            " intervals=1 boundedSpecies=" + SPECIES +
            " transparent=true expectedRed=true");
    }

    public static void run()
    {
        Run off = run(false);
        Run on = run(true);
        require(off.fingerprints.equals(on.fingerprints),
            "OBS-1B observer changed the 100-step ecological trajectory");
        require(off.nextRandomLong == on.nextRandomLong,
            "OBS-1B observer changed random consumption");
        require(on.analysis.getSteps() == STEPS &&
                on.analysis.getStepRows() == STEPS * SPECIES &&
                on.analysis.getIntervalRows() == SPECIES &&
                on.analysis.getCompletedIntervals() == 1,
            "OBS-1B analyzer did not independently close the 100-step ledger");
        require(on.ledger.getCompletedIntervals() == 1 &&
                on.maxRetainedCurrentKeys == 0 &&
                on.ledger.getRetainedIntervalSpeciesCount() == SPECIES &&
                on.analysis.getMaxRetainedIntervalSpecies() == SPECIES,
            "OBS-1B retained unbounded completed-step state");
        expectAnalyzerRed(on.output.replaceFirst(
            "\\tclosed=true", "\\tclosed=false"));
    }

    private static Run run(boolean observe)
    {
        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(2111L).terrainSeed(20260629L).build();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        StringWriter writer = new StringWriter();
        LongHorizonTrophicLedger ledger = new LongHorizonTrophicLedger(writer);
        if(observe) simulator.addLongHorizonTrophicLedger(ledger);
        List<String> fingerprints = new ArrayList<>();
        int maxRetained = 0;
        for(int index = 0; index < STEPS; index++) {
            simulator.simulateOneStep();
            fingerprints.add(SimulationFingerprint.capture(simulator));
            maxRetained = Math.max(maxRetained,
                ledger.getRetainedCurrentStepKeyCount());
        }
        ledger.flush();
        String output = writer.toString();
        LongHorizonTrophicLedgerAnalyzer.Result analysis = observe
            ? LongHorizonTrophicLedgerAnalyzer.analyze(output) : null;
        return new Run(fingerprints,
            simulator.getContext().getRandom().nextLong(), output, ledger,
            analysis, maxRetained);
    }

    private static void expectAnalyzerRed(String stream)
    {
        try {
            LongHorizonTrophicLedgerAnalyzer.analyze(stream);
            throw new IllegalStateException(
                "OBS-1B fault seed unexpectedly passed the analyzer");
        }
        catch(IllegalStateException expected) {
            require(!expected.getMessage().contains("unexpectedly passed"),
                expected.getMessage());
        }
    }

    private static void require(boolean value, String message)
    {
        if(!value) throw new IllegalStateException(message);
    }

    private static final class Run
    {
        private final List<String> fingerprints;
        private final long nextRandomLong;
        private final String output;
        private final LongHorizonTrophicLedger ledger;
        private final LongHorizonTrophicLedgerAnalyzer.Result analysis;
        private final int maxRetainedCurrentKeys;

        private Run(List<String> fingerprints, long nextRandomLong,
                    String output, LongHorizonTrophicLedger ledger,
                    LongHorizonTrophicLedgerAnalyzer.Result analysis,
                    int maxRetainedCurrentKeys)
        {
            this.fingerprints = fingerprints;
            this.nextRandomLong = nextRandomLong;
            this.output = output;
            this.ledger = ledger;
            this.analysis = analysis;
            this.maxRetainedCurrentKeys = maxRetainedCurrentKeys;
        }
    }
}
