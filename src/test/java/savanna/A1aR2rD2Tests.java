package savanna;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Controlled D2 contracts; none may create a position claim or consume RNG. */
public final class A1aR2rD2Tests
{
    private static final String FROZEN_2111_STEP_150 =
        // Deterministic step-150 trace, updated whenever EcologyRules.VERSION
        // changes. Intentional trace migration: v3.2-c4-l4
        "3890adc137077c5e53852fec9a77a9d3c613e6ee8d3309d6c05ca6fbe5481b92";

    private A1aR2rD2Tests()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("A1a-R2R-D2 causal HUNT boundary fixtures");
        run();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    public static void run()
    {
        TestSupport.startGroup("A1a-R2R-D2 causal HUNT boundary tests");
        testTypedOutcomeLegacyDestinationEquivalence();
        testOutcomeCostEventAndRandomShape();
        testPhaseReasonOutcomeClassifierReconciliation();
        testWouldSeekObservationTransparency();
        testFrozenR2StepwiseFingerprint();
        TestSupport.finishGroup("A1A_R2R_D2_FIXTURES");
    }

    private static void testTypedOutcomeLegacyDestinationEquivalence()
    {
        HuntSetup typed = setup(true, 0, new FixedDoubleRandom(0.0));
        makeAdult(typed.predator);
        Location typedPreyLocation = typed.prey.getLocation();
        HuntResult detailed = typed.predation.huntWithOutcome(typed.predator,
            StepStartInteractionGeometry.capture(typed.field), typed.context);
        HuntSetup legacy = setup(true, 0, new FixedDoubleRandom(0.0));
        makeAdult(legacy.predator);
        Location legacyDestination = legacy.predation.hunt(legacy.predator,
            StepStartInteractionGeometry.capture(legacy.field), legacy.context);

        TestSupport.assertEquals("D2 F01 typed outcome marks successful hunt",
            HuntOutcome.SUCCESS, detailed.getOutcome());
        TestSupport.assertEquals("D2 F01 legacy destination adapter is exact",
            legacyDestination, detailed.getDestination());
        TestSupport.assertEquals("D2 F01 typed destination keeps prey cell",
            typedPreyLocation, detailed.getDestination());
    }

    private static void testOutcomeCostEventAndRandomShape()
    {
        HuntSetup ineligible = setup(false, 0, new FixedDoubleRandom(0.0));
        HuntResult ineligibleResult = ineligible.predation.huntWithOutcome(
            ineligible.predator, StepStartInteractionGeometry.capture(
            ineligible.field), ineligible.context);
        TestSupport.assertEquals("D2 F02 juvenile predator is INELIGIBLE",
            HuntOutcome.INELIGIBLE, ineligibleResult.getOutcome());
        TestSupport.assertClose("D2 F02 ineligible costs zero stamina", 0.0,
            ineligibleResult.getStaminaCost(), 0.000001);
        TestSupport.assertFalse("D2 F02 ineligible has no named RNG phase",
            ineligibleResult.getRandomLedger().hasDiscretionaryDraw() ||
            ineligibleResult.getRandomLedger().isLocationShuffleRequested() ||
            ineligibleResult.getRandomLedger().hasSuccessDraw());

        HuntSetup declined = setup(false,
            SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH).getMaxFoodLevel(),
            new FixedDoubleRandom(0.99));
        makeAdult(declined.predator);
        HuntResult declinedResult = declined.predation.huntWithOutcome(
            declined.predator, StepStartInteractionGeometry.capture(
            declined.field), declined.context);
        TestSupport.assertEquals("D2 F02 satiated predator may DECLINE",
            HuntOutcome.DECLINED, declinedResult.getOutcome());
        TestSupport.assertTrue("D2 F02 decline names discretionary RNG",
            declinedResult.getRandomLedger().hasDiscretionaryDraw());
        TestSupport.assertFalse("D2 F02 decline does not request prey shuffle",
            declinedResult.getRandomLedger().isLocationShuffleRequested());

        // M3 change B: above the satiation fraction the decline is certain
        // and draws no random number; a hungry predator still hunts.
        EcologyParameters.override(EcologyParameters.defaults()
            .with("predation.satiationFraction=0.8"));
        try {
            HuntSetup sated = setup(true,
                SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH).getMaxFoodLevel(),
                new FixedDoubleRandom(0.0));
            makeAdult(sated.predator);
            HuntResult satedResult = sated.predation.huntWithOutcome(sated.predator,
                StepStartInteractionGeometry.capture(sated.field), sated.context);
            TestSupport.assertEquals("M3 B a sated predator does not hunt",
                HuntOutcome.DECLINED, satedResult.getOutcome());
            TestSupport.assertFalse("M3 B the satiation decline draws no random number",
                satedResult.getRandomLedger().hasDiscretionaryDraw());
            HuntSetup hungry = setup(true, 0, new FixedDoubleRandom(0.0));
            makeAdult(hungry.predator);
            TestSupport.assertEquals("M3 B a hungry predator still hunts",
                HuntOutcome.SUCCESS, hungry.predation.huntWithOutcome(hungry.predator,
                    StepStartInteractionGeometry.capture(hungry.field),
                    hungry.context).getOutcome());
        }
        finally {
            EcologyParameters.override(null);
        }

        HuntSetup noLocal = setup(false, 0, new FixedDoubleRandom(0.0));
        makeAdult(noLocal.predator);
        HuntResult noLocalResult = noLocal.predation.huntWithOutcome(
            noLocal.predator, StepStartInteractionGeometry.capture(
            noLocal.field), noLocal.context);
        TestSupport.assertEquals("D2 F02 empty range is NO_LOCAL_REACHABLE_PREY",
            HuntOutcome.NO_LOCAL_REACHABLE_PREY, noLocalResult.getOutcome());
        TestSupport.assertClose("D2 F02 no-local keeps frozen hunt cost",
            SavannahAnimal.STAMINA_HUNT_COST * 0.65,
            noLocalResult.getStaminaCost(), 0.000001);
        TestSupport.assertTrue("D2 F02 no-local names location shuffle request",
            noLocalResult.getRandomLedger().isLocationShuffleRequested());
        TestSupport.assertFalse("D2 F02 no-local does not draw success RNG",
            noLocalResult.getRandomLedger().hasSuccessDraw());

        HuntSetup failed = setup(true, 0, new FixedDoubleRandom(0.99));
        makeAdult(failed.predator);
        HuntResult failedResult = failed.predation.huntWithOutcome(
            failed.predator, StepStartInteractionGeometry.capture(failed.field),
            failed.context);
        TestSupport.assertEquals("D2 F02 failed hunt is named",
            HuntOutcome.FAILED_ATTEMPT, failedResult.getOutcome());
        TestSupport.assertEquals("D2 F02 failed hunt names selected prey species",
            SpeciesRegistry.GAZELLE, failedResult.getSelectedPreySpecies());
        TestSupport.assertTrue("D2 F02 failed hunt has positive frozen cost",
            failedResult.getStaminaCost() > 0.0);
        TestSupport.assertTrue("D2 F02 failed hunt names success RNG",
            failedResult.getRandomLedger().hasSuccessDraw());
        TestSupport.assertTrue("D2 F02 failed hunt has no kill event",
            !hasEvent(failed.context, SimulationEvent.EventType.HUNT) &&
            failed.context.getDeathCount(DeathCause.PREDATION) == 0);

        HuntSetup succeeded = setup(true, 0, new FixedDoubleRandom(0.0));
        makeAdult(succeeded.predator);
        HuntResult successResult = succeeded.predation.huntWithOutcome(
            succeeded.predator, StepStartInteractionGeometry.capture(
            succeeded.field), succeeded.context);
        TestSupport.assertEquals("D2 F02 successful hunt is named",
            HuntOutcome.SUCCESS, successResult.getOutcome());
        TestSupport.assertTrue("D2 F02 success records hunt and predation death",
            hasEvent(succeeded.context, SimulationEvent.EventType.HUNT) &&
            succeeded.context.getDeathCount(DeathCause.PREDATION) == 1);
    }

    private static void testPhaseReasonOutcomeClassifierReconciliation()
    {
        RunTrace trace = runTrace(48, 1111L, 20260629L, true);
        TestSupport.assertTrue("D2 F03 records active HUNT results",
            !trace.huntFacts.results.isEmpty());
        boolean reconciled = true;
        for(HuntObservation result : trace.huntFacts.results) {
            HuntDecisionObservation decision = trace.huntFacts.decisions.get(
                key(result.getStep(), result.getActorId()));
            HuntResult typed = result.getResult();
            reconciled = reconciled && decision != null;
            if(typed.getOutcome() != HuntOutcome.DECLINED &&
               typed.getOutcome() != HuntOutcome.INELIGIBLE) {
                reconciled = reconciled &&
                    decision.getLocalReachableEdibleCount() ==
                        typed.getLocalReachableEdibleCount();
            }
            if(typed.getOutcome() == HuntOutcome.NO_LOCAL_REACHABLE_PREY) {
                reconciled = reconciled && !decision.hasLocalReachableEdible();
            }
        }
        TestSupport.assertTrue("D2 F03 shared classifier reconciles each active HUNT",
            reconciled);
        TestSupport.assertTrue("D2 F03 ledger exposes reason and outcome",
            trace.huntFacts.hasReasonAndOutcomeFacts());
    }

    private static void testWouldSeekObservationTransparency()
    {
        RunTrace baseline = runTrace(80, 2111L, 20260629L, false);
        RunTrace observed = runTrace(80, 2111L, 20260629L, true);
        TestSupport.assertEquals("D2 F04 observer leaves ecology/plan/event/transition trace exact",
            baseline.stepDigests, observed.stepDigests);
        TestSupport.assertTrue("D2 F04 observer records decision facts without claims",
            !observed.huntFacts.decisions.isEmpty());
    }

    private static void testFrozenR2StepwiseFingerprint()
    {
        // 150 steps finish under any plausible ecology parameters; survival
        // itself is judged by the seed matrix, not by this determinism check.
        RunTrace trace = runTrace(150, 2111L, 20260629L, false);
        TestSupport.assertEquals("D2 F05 completes frozen 2111 150-step run", 150,
            trace.completedSteps);
        TestSupport.assertEquals("D2 F05 keeps frozen 2111 step-150 fingerprint",
            FROZEN_2111_STEP_150, trace.finalFingerprint);
    }

    private static HuntSetup setup(boolean includePrey, int foodLevel, Random random)
    {
        SimulationConfig config = SimulationConfig.builder().randomSeed(2111L)
            .terrainSeed(20260629L).build();
        SimulationContext context = new SimulationContext(24, 24, config);
        Location[] pair = adjacentPassablePair(context.getTerrainMap());
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Cheetah predator = new Cheetah(false, pair[0], new Random(11L),
                                       identities);
        TestSupport.setField(predator, "foodLevel", foodLevel);
        Field field = new Field(24, 24);
        field.placeAnimal(predator, pair[0]);
        Gazelle prey = null;
        if(includePrey) {
            prey = new Gazelle(false, pair[1], new Random(12L), identities);
            field.placeAnimal(prey, pair[1]);
        }
        context.startStep(6, field);
        return new HuntSetup(context, field, predator, prey,
            new FoodChainPredationSystem(random));
    }

    private static void makeAdult(SavannahAnimal animal)
    {
        TestSupport.setField(animal, "age", animal.getProfile().getAdultAge());
    }

    private static Location[] adjacentPassablePair(TerrainMap terrain)
    {
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location first = new Location(row, col);
                if(!terrain.isPassable(first)) continue;
                for(int dr = -1; dr <= 1; dr++) {
                    for(int dc = -1; dc <= 1; dc++) {
                        Location second = new Location(row + dr, col + dc);
                        if((dr != 0 || dc != 0) && second.row() >= 0 &&
                           second.row() < terrain.getDepth() &&
                           second.col() >= 0 && second.col() < terrain.getWidth() &&
                           terrain.isPassable(second)) {
                            return new Location[] { first, second };
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("no adjacent passable pair");
    }

    private static boolean hasEvent(SimulationContext context,
                                    SimulationEvent.EventType type)
    {
        for(SimulationEvent event : context.getCurrentEvents()) {
            if(event.type == type) return true;
        }
        return false;
    }

    private static RunTrace runTrace(int steps, long ecologySeed,
                                     long terrainSeed, boolean observe)
    {
        SimulationConfig config = SimulationConfig.builder().randomSeed(ecologySeed)
            .terrainSeed(terrainSeed).build();
        Randomizer.reset();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        TransitionFacts transitions = new TransitionFacts();
        simulator.getContext().addTransitionObservationListener(transitions);
        HuntFacts huntFacts = new HuntFacts();
        if(observe) simulator.getContext().addHuntObservationListener(huntFacts);
        List<String> digests = new ArrayList<>();
        for(int index = 1; index <= steps && simulator.getField().isViable(); index++) {
            simulator.simulateOneStep();
            digests.add(digest(SimulationFingerprint.capture(simulator) +
                "|plan=" + planDigest(simulator.getContext().getLastPositionPlan()) +
                "|events=" + eventsDigest(simulator.getContext().getCurrentEvents()) +
                "|transitions=" + transitions.drain()));
        }
        return new RunTrace(digests, huntFacts, simulator.getStep(),
            SimulationFingerprint.capture(simulator));
    }

    private static String planDigest(PositionPlan plan)
    {
        if(plan == null) return "none";
        StringBuilder value = new StringBuilder(plan.getPolicyVersion())
            .append('|').append(plan.getStepSalt());
        for(PositionResolution resolution : plan.getResolutions().values()) {
            value.append('|').append(resolution.getActor()).append(':')
                .append(resolution.getClaimKind()).append(':')
                .append(resolution.getRequested()).append(':')
                .append(resolution.getDestination()).append(':')
                .append(resolution.getCandidateSource()).append(':')
                .append(resolution.getCandidateIndex()).append(':')
                .append(resolution.getDisposition()).append(':')
                .append(resolution.getReason());
        }
        return digest(value.toString());
    }

    private static String eventsDigest(List<SimulationEvent> events)
    {
        StringBuilder value = new StringBuilder();
        for(SimulationEvent event : events) {
            value.append(event.type).append(':').append(event.step).append(':')
                .append(event.actorId).append(':').append(event.targetId)
                .append(':').append(event.row).append(':').append(event.col)
                .append(':').append(event.deathCause).append('|');
        }
        return digest(value.toString());
    }

    private static String digest(String value)
    {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for(byte valueByte : digest.digest()) {
                result.append(String.format("%02x", valueByte & 0xff));
            }
            return result.toString();
        }
        catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String key(int step, long actorId)
    {
        return step + ":" + actorId;
    }

    private static final class HuntSetup
    {
        private final SimulationContext context;
        private final Field field;
        private final Cheetah predator;
        private final Gazelle prey;
        private final FoodChainPredationSystem predation;

        HuntSetup(SimulationContext context, Field field, Cheetah predator,
                  Gazelle prey, FoodChainPredationSystem predation)
        {
            this.context = context;
            this.field = field;
            this.predator = predator;
            this.prey = prey;
            this.predation = predation;
        }
    }

    private static final class FixedDoubleRandom extends Random
    {
        private final double value;

        FixedDoubleRandom(double value)
        {
            super(7L);
            this.value = value;
        }

        @Override
        public double nextDouble()
        {
            return value;
        }
    }

    private static final class TransitionFacts
        implements TransitionObservationListener
    {
        private final StringBuilder value = new StringBuilder();

        public void onTransition(TransitionObservation observation)
        {
            value.append(observation.getStep()).append(':')
                .append(observation.getAnimalId()).append(':')
                .append(observation.getOrigin()).append(':')
                .append(observation.getRequested()).append(':')
                .append(observation.getResolved()).append(':')
                .append(observation.getStatus()).append(':')
                .append(observation.usedSearchFallback()).append('|');
        }

        private String drain()
        {
            String result = digest(value.toString());
            value.setLength(0);
            return result;
        }
    }

    private static final class HuntFacts implements HuntObservationListener
    {
        private final Map<String, HuntDecisionObservation> decisions =
            new LinkedHashMap<>();
        private final List<HuntObservation> results = new ArrayList<>();

        public void onHuntDecision(HuntDecisionObservation observation)
        {
            decisions.put(key(observation.getStep(), observation.getActorId()),
                observation);
        }

        public void onHuntResult(HuntObservation observation)
        {
            results.add(observation);
        }

        private boolean hasReasonAndOutcomeFacts()
        {
            for(HuntObservation observation : results) {
                if(observation.getFoodReason() == null ||
                   observation.getResult().getOutcome() == null) {
                    return false;
                }
            }
            return !results.isEmpty();
        }
    }

    private static final class RunTrace
    {
        private final List<String> stepDigests;
        private final HuntFacts huntFacts;
        private final int completedSteps;
        private final String finalFingerprint;

        RunTrace(List<String> stepDigests, HuntFacts huntFacts,
                 int completedSteps, String finalFingerprint)
        {
            this.stepDigests = stepDigests;
            this.huntFacts = huntFacts;
            this.completedSteps = completedSteps;
            this.finalFingerprint = finalFingerprint;
        }
    }
}
