package savanna;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Fixed non-ecology OFF/ON transparency probe for V24-OBS-1A. */
public final class V24Obs1aTransparencyProbe
{
    private static final int STEPS = 1000;
    private static final long TERRAIN_SEED = 20260629L;

    private V24Obs1aTransparencyProbe()
    {
    }

    public static void main(String[] args)
    {
        if(args.length != 0) {
            throw new IllegalArgumentException("OBS-1A transparency budget is fixed");
        }
        for(long seed : new long[] { 1111L, 2111L }) {
            Trace off = run(seed, false);
            Trace on = run(seed, true);
            require(off.fingerprints.equals(on.fingerprints),
                "observer changed ecology trace for seed " + seed);
            require(off.nextRandomLong == on.nextRandomLong,
                "observer changed RNG state for seed " + seed);
            require(on.analysis.getAssessmentCount() > 0 &&
                on.maxRetainedKeys == 0,
                "observer stream or bounded state invalid for seed " + seed);
            System.out.println("V24_OBS_1A_TRANSPARENCY seed=" + seed +
                " steps=" + STEPS + " ecologyExact=true rngExact=true" +
                " assessments=" + on.analysis.getAssessmentCount() +
                " boundedKeys=0 result=PASS");
        }
    }

    private static Trace run(long seed, boolean observe)
    {
        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(seed).terrainSeed(TERRAIN_SEED).build();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        StringWriter writer = new StringWriter();
        PredatorRecruitmentFunnel funnel = new PredatorRecruitmentFunnel(writer);
        if(observe) {
            simulator.addEventListener(funnel);
            simulator.addPredatorRecruitmentAssessmentListener(funnel);
        }
        List<String> fingerprints = new ArrayList<>();
        int maxRetainedKeys = 0;
        for(int step = 0; step < STEPS; step++) {
            simulator.simulateOneStep();
            fingerprints.add(digest(simulator));
            maxRetainedKeys = Math.max(maxRetainedKeys,
                funnel.getRetainedCurrentStepKeyCount());
        }
        funnel.flush();
        String output = writer.toString();
        PredatorRecruitmentFunnelAnalyzer.Result analysis = observe
            ? PredatorRecruitmentFunnelAnalyzer.analyze(output) : null;
        return new Trace(fingerprints, simulator.getContext().getRandom().nextLong(),
            analysis, maxRetainedKeys);
    }

    private static void require(boolean value, String message)
    {
        if(!value) throw new IllegalStateException(message);
    }

    private static String digest(Simulator simulator)
    {
        StringBuilder value = new StringBuilder();
        List<SavannahAnimal> animals = new ArrayList<>();
        for(Animal animal : simulator.getField().getAnimals()) {
            if(animal instanceof SavannahAnimal && animal.isAlive()) {
                animals.add((SavannahAnimal)animal);
            }
        }
        animals.sort(Comparator.comparingLong(Animal::getId));
        SimulationContext context = simulator.getContext();
        value.append(context.getStep()).append('|')
            .append(context.getClock().getPhase()).append('|')
            .append(context.getDeathSummary()).append('|');
        for(SavannahAnimal animal : animals) {
            Location location = animal.getLocation();
            value.append(animal.getId()).append(':')
                .append(animal.getProfile().getName()).append(':')
                .append(animal.getSex()).append(':').append(animal.getAge())
                .append(':').append(animal.getFoodLevel()).append(':')
                .append(Double.doubleToLongBits(animal.getStaminaPercent()))
                .append(':').append(Double.doubleToLongBits(
                    animal.getHydrationPercent())).append(':')
                .append(animal.getStarvingSteps()).append(':')
                .append(animal.getDehydrationSteps()).append(':')
                .append(animal.isInfected()).append(':')
                .append(location.row()).append(',').append(location.col())
                .append(';');
        }
        for(SimulationEvent event : context.getCurrentEvents()) {
            value.append("event=").append(event.type).append(':')
                .append(event.actorId).append(':').append(event.targetId)
                .append(':').append(event.deathCause).append(';');
        }
        return value.toString();
    }

    private static final class Trace
    {
        private final List<String> fingerprints;
        private final long nextRandomLong;
        private final PredatorRecruitmentFunnelAnalyzer.Result analysis;
        private final int maxRetainedKeys;

        private Trace(List<String> fingerprints, long nextRandomLong,
                      PredatorRecruitmentFunnelAnalyzer.Result analysis,
                      int maxRetainedKeys)
        {
            this.fingerprints = fingerprints;
            this.nextRandomLong = nextRandomLong;
            this.analysis = analysis;
            this.maxRetainedKeys = maxRetainedKeys;
        }
    }
}
