package savanna;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Standalone V24-OBS-1A fixtures; intentionally compilable in isolation. */
public final class V24Obs1aTests
{
    private static final int STEPS = 4;

    private V24Obs1aTests()
    {
    }

    public static void main(String[] args)
    {
        run();
        System.out.println("V24_OBS_1A_FIXTURES PASS steps=" + STEPS +
            " boundedKeys=0 transparent=true");
    }

    public static void run()
    {
        Run baseline = run(false);
        Run observed = run(true);
        require(baseline.trace.equals(observed.trace),
            "OBS-1A observer changed ecology/event trajectory");
        require(baseline.nextRandomLong == observed.nextRandomLong,
            "OBS-1A observer changed ecology random consumption");
        require(observed.funnel.getRetainedCurrentStepKeyCount() == 0,
            "OBS-1A retained completed-step objects");
        require(observed.output.indexOf("ASSESSMENT\t") >= 0 &&
            observed.output.indexOf("STEP\t") >= 0,
            "OBS-1A stream omitted assessment or closure rows");
        require(observed.output.indexOf(
                    "capacityEligibility=NOT_GATED_BY_LEGACY_SEMANTICS") >= 0 &&
                observed.output.indexOf(
                    "creditEligibility=NOT_GATED_BY_LEGACY_SEMANTICS") >= 0,
            "OBS-1A did not explicitly preserve absent legacy gates");
        require(observed.output.indexOf("nearestReachableMate=") >= 0 &&
                observed.output.indexOf("conceptionKey=") >= 0 &&
                observed.output.indexOf("mateQueryDraws=") >= 0,
            "OBS-1A stream omitted required reachability, correlation, or RNG fields");
        require(observed.analysis.getAssessmentCount() ==
                observed.observationCount,
            "OBS-1A analyzer did not recompute streamed assessment count");
        for(String species : new String[] { SpeciesRegistry.LION,
                                             SpeciesRegistry.CHEETAH }) {
            PredatorRecruitmentFunnel.Summary summary =
                observed.summaries.get(species);
            require(summary != null && summary.getAssessments() > 0,
                "OBS-1A omitted predator assessments for " + species);
            require(summary.getSubmittedOrdinals() ==
                summary.getCommittedBirths() + summary.getLostPosition() +
                summary.getCancelledParent() +
                summary.getExplicitPregnancyLoss(),
                "OBS-1A ordinal closure failed for " + species);
            require(summary.getAcceptedConceptions() ==
                summary.getPregnancyOpened() &&
                summary.getPregnancyOpened() == summary.getDueBirths() &&
                summary.getPregnancyOpened() == summary.getCommittedBirths() +
                    summary.getPendingBirths() + summary.getLostPosition() +
                    summary.getCancelledParent() +
                    summary.getExplicitPregnancyLoss(),
                "OBS-1A lifecycle closure failed for " + species);
            require(summary.getBirthEvents() == summary.getCommittedBirths(),
                "OBS-1A birth-event closure failed for " + species);
            int classified = 0;
            for(Integer count : summary.getReasons().values()) {
                classified += count.intValue();
            }
            require(classified == summary.getAssessments(),
                "OBS-1A terminal buckets are incomplete for " + species);
            require(summary.getMinimumPopulation() > 0,
                "OBS-1A population floor missing for " + species);
        }
        require(observed.observationCount > 0,
            "OBS-1A observer produced no streamed assessments");
    }

    private static Run run(boolean observer)
    {
        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(2111L).terrainSeed(20260629L).build();
        SimulationContext context = new SimulationContext(32, 32, config);
        Field field = new Field(32, 32);
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        populateFixture(field, context.getTerrainMap(), identities);
        StringWriter writer = new StringWriter();
        PredatorRecruitmentFunnel funnel = new PredatorRecruitmentFunnel(writer);
        if(observer) {
            context.addEventListener(funnel);
            context.addPredatorRecruitmentAssessmentListener(funnel);
        }
        List<String> trace = new ArrayList<>();
        for(int step = 1; step <= STEPS; step++) {
            context.startStep(step, field);
            PositionStepCoordinator coordinator = new PositionStepCoordinator(
                field, context);
            PositionAvailabilityView availability = coordinator
                .createAvailabilityView();
            for(Animal animal : field.getAnimals()) {
                coordinator.propose(animal, availability);
            }
            field = coordinator.resolveAndCommitCompleteStep();
            trace.add(digest(field, context));
        }
        funnel.flush();
        String output = writer.toString();
        return new Run(trace, output, funnel, funnel.snapshotSummaries(),
            count(output, "ASSESSMENT\t"),
            PredatorRecruitmentFunnelAnalyzer.analyze(output),
            context.getRandom().nextLong());
    }

    private static void populateFixture(Field field, TerrainMap terrain,
                                        AnimalIdentityAllocator identities)
    {
        Location[] locations = passableLocations(terrain, 4);
        Cheetah femaleCheetah = new Cheetah(false, locations[0],
            new SexRandom(false), identities);
        Cheetah maleCheetah = new Cheetah(false, locations[1],
            new SexRandom(true), identities);
        Lion femaleLion = new Lion(false, locations[2], new SexRandom(false),
            identities);
        Lion maleLion = new Lion(false, locations[3], new SexRandom(true),
            identities);
        makeBreedingAdult(femaleCheetah);
        makeBreedingAdult(maleCheetah);
        makeBreedingAdult(femaleLion);
        makeBreedingAdult(maleLion);
        field.placeAnimal(femaleCheetah, locations[0]);
        field.placeAnimal(maleCheetah, locations[1]);
        field.placeAnimal(femaleLion, locations[2]);
        field.placeAnimal(maleLion, locations[3]);
    }

    private static Location[] passableLocations(TerrainMap terrain, int count)
    {
        List<Location> values = new ArrayList<>();
        for(int row = 0; row < terrain.getDepth() && values.size() < count; row++) {
            for(int col = 0; col < terrain.getWidth() && values.size() < count;
                col++) {
                Location value = new Location(row, col);
                if(terrain.isPassable(value)) values.add(value);
            }
        }
        if(values.size() != count) throw new IllegalStateException("fixture land missing");
        return values.toArray(new Location[count]);
    }

    private static void makeBreedingAdult(SavannahAnimal animal)
    {
        try {
            java.lang.reflect.Field age = SavannahAnimal.class.getDeclaredField("age");
            age.setAccessible(true);
            age.setInt(animal, Math.max(animal.getProfile().getAdultAge(),
                animal.getProfile().getBreedingAge()));
        }
        catch(ReflectiveOperationException exception) {
            throw new IllegalStateException("cannot prepare OBS-1A fixture", exception);
        }
    }

    private static String digest(Field field, SimulationContext context)
    {
        StringBuilder result = new StringBuilder();
        List<SavannahAnimal> animals = new ArrayList<>();
        for(Animal animal : field.getAnimals()) {
            if(animal instanceof SavannahAnimal && animal.isAlive()) {
                animals.add((SavannahAnimal)animal);
            }
        }
        animals.sort(Comparator.comparingLong(Animal::getId));
        result.append(context.getStep()).append('|')
            .append(context.getDeathSummary()).append('|');
        for(SavannahAnimal animal : animals) {
            Location location = animal.getLocation();
            result.append(animal.getId()).append(':')
                .append(animal.getProfile().getName()).append(':')
                .append(animal.getSex()).append(':').append(animal.getAge())
                .append(':').append(animal.getFoodLevel()).append(':')
                .append(Double.doubleToLongBits(animal.getStaminaPercent()))
                .append(':').append(Double.doubleToLongBits(
                    animal.getHydrationPercent())).append(':')
                .append(animal.getStarvingSteps()).append(':')
                .append(animal.isInfected()).append(':')
                .append(location.row()).append(',').append(location.col())
                .append(';');
        }
        for(SimulationEvent event : context.getCurrentEvents()) {
            result.append("event=").append(event.type).append(':')
                .append(event.actorId).append(':').append(event.targetId)
                .append(':').append(event.deathCause).append(';');
        }
        return result.toString();
    }

    private static int count(String value, String token)
    {
        int result = 0;
        int offset = 0;
        while((offset = value.indexOf(token, offset)) >= 0) {
            result++;
            offset += token.length();
        }
        return result;
    }

    private static void require(boolean condition, String message)
    {
        if(!condition) throw new IllegalStateException(message);
    }

    private static final class Run
    {
        private final List<String> trace;
        private final String output;
        private final PredatorRecruitmentFunnel funnel;
        private final Map<String, PredatorRecruitmentFunnel.Summary> summaries;
        private final int observationCount;
        private final PredatorRecruitmentFunnelAnalyzer.Result analysis;
        private final long nextRandomLong;

        private Run(List<String> trace, String output,
                    PredatorRecruitmentFunnel funnel,
                    Map<String, PredatorRecruitmentFunnel.Summary> summaries,
                    int observationCount,
                    PredatorRecruitmentFunnelAnalyzer.Result analysis,
                    long nextRandomLong)
        {
            this.trace = trace;
            this.output = output;
            this.funnel = funnel;
            this.summaries = summaries;
            this.observationCount = observationCount;
            this.analysis = analysis;
            this.nextRandomLong = nextRandomLong;
        }
    }

    private static final class SexRandom extends Random
    {
        private final boolean male;

        private SexRandom(boolean male)
        {
            this.male = male;
        }

        @Override
        public boolean nextBoolean()
        {
            return male;
        }

        @Override
        protected int next(int bits)
        {
            return 0;
        }
    }
}
