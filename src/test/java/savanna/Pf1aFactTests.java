package savanna;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Controlled PF1-aF fact, retention, RNG, and join contracts. */
public final class Pf1aFactTests
{
    private static final String EXPECTED_SCHEMA_HASH =
        "21391ee94500d3e3cd871ee5fd10e7c3c1017383a513dd8d698d34efddcc7adf";

    private Pf1aFactTests()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("PF1-aF fact fixtures");
        run();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    public static void run()
    {
        TestSupport.startGroup("PF1-aF resource, kill, and breeding facts");
        HuntSetup success = testSuccessfulHuntAtomicJoin();
        testOfferedVersusAcceptedAtCap();
        testOneKillOneRecord(success);
        testNonSuccessHasNoRecord();
        testStepStartWindowExcludesSameStepKill();
        testWindowThirtySixtyAndLeftCensor();
        testLedgerBoundedAndDeathCleanup();
        IntegrationTrace integration = runIntegrationTrace(40, true);
        testLocalResourceL1Equivalence(integration);
        BreedingSetup breeding = controlledBreedingSetup();
        testLegacyProbabilityFactorExact(breeding);
        testNoSecondMateQueryOrRng(breeding);
        testLitterBoundAndClaimCountExact(breeding);
        testBirthParentChildReserveJoin(integration);
        testAllRegisteredPredators(integration);
        testObserverOffOnExact();
        testSchemaUniquenessAndOrphanZero(integration);
        TestSupport.finishGroup("PF1AF_FIXTURES");
    }

    private static HuntSetup testSuccessfulHuntAtomicJoin()
    {
        HuntSetup setup = huntSetup(true, true,
            SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH)
                .getMaxFoodLevel() / 2,
            new FixedDecisionRandom(0.0, 0));
        setup.result = setup.system.huntWithOutcome(setup.predator,
            StepStartInteractionGeometry.capture(setup.field), setup.context);
        TestSupport.assertEquals(
            "PF1AF F01 successful HUNT has typed success",
            HuntOutcome.SUCCESS, setup.result.getOutcome());
        TestSupport.assertEquals(
            "PF1AF F01 one successful chain emits one kill fact", 1,
            setup.collector.kills.size());
        TestSupport.assertEquals(
            "PF1AF F01 HUNT event joins predator and prey", 1,
            countEvents(setup.context, SimulationEvent.EventType.HUNT));
        TestSupport.assertEquals(
            "PF1AF F01 PREDATION death joins the same prey",
            DeathCause.PREDATION, setup.prey.getDeathCause());
        RealizedKillRecord record = setup.collector.kills.get(0);
        TestSupport.assertEquals(
            "PF1AF F01 record predator ID exact",
            Long.valueOf(setup.predator.getId()),
            Long.valueOf(record.getPredatorId()));
        TestSupport.assertEquals(
            "PF1AF F01 record prey ID exact",
            Long.valueOf(setup.prey.getId()),
            Long.valueOf(record.getPreyId()));
        return setup;
    }

    private static void testOfferedVersusAcceptedAtCap()
    {
        SpeciesProfile profile =
            SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH);
        HuntSetup setup = huntSetup(true, true,
            profile.getMaxFoodLevel() - 1,
            new FixedDecisionRandom(0.0, 0));
        setup.result = setup.system.huntWithOutcome(setup.predator,
            StepStartInteractionGeometry.capture(setup.field), setup.context);
        RealizedKillRecord record = setup.collector.kills.get(0);
        TestSupport.assertEquals(
            "PF1AF F02 offered food is prey profile value",
            setup.prey.getProfile().getFoodValue(), record.getOfferedFood());
        TestSupport.assertEquals(
            "PF1AF F02 accepted food is actual clamped delta", 1,
            record.getAcceptedFood());
        TestSupport.assertEquals(
            "PF1AF F02 predator reaches exact food cap",
            profile.getMaxFoodLevel(), setup.predator.getFoodLevel());
    }

    private static void testOneKillOneRecord(HuntSetup setup)
    {
        RealizedKillRecord record = setup.collector.kills.get(0);
        RealizedKillLedger ledger = new RealizedKillLedger();
        ledger.startStep(record.getStep());
        ledger.append(record);
        boolean duplicateRejected = false;
        try {
            ledger.append(record);
        }
        catch(IllegalStateException expected) {
            duplicateRejected = true;
        }
        TestSupport.assertEquals("PF1AF F03 one kill leaves one record", 1,
            ledger.getRetainedRecordCount());
        TestSupport.assertTrue(
            "PF1AF F03 duplicate step/predator/prey fails closed",
            duplicateRejected);
    }

    private static void testNonSuccessHasNoRecord()
    {
        HuntSetup ineligible = huntSetup(true, false, 0,
            new FixedDecisionRandom(0.0, 0));
        HuntResult ineligibleResult = ineligible.system.huntWithOutcome(
            ineligible.predator,
            StepStartInteractionGeometry.capture(ineligible.field),
            ineligible.context);

        HuntSetup declined = huntSetup(true, true,
            SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH)
                .getMaxFoodLevel(),
            new FixedDecisionRandom(1.0, 0));
        HuntResult declinedResult = declined.system.huntWithOutcome(
            declined.predator,
            StepStartInteractionGeometry.capture(declined.field),
            declined.context);

        HuntSetup failed = huntSetup(true, true, 1,
            new FixedDecisionRandom(1.0, 0));
        HuntResult failedResult = failed.system.huntWithOutcome(
            failed.predator,
            StepStartInteractionGeometry.capture(failed.field),
            failed.context);

        HuntSetup noLocal = huntSetup(false, true, 1,
            new FixedDecisionRandom(0.0, 0));
        HuntResult noLocalResult = noLocal.system.huntWithOutcome(
            noLocal.predator,
            StepStartInteractionGeometry.capture(noLocal.field),
            noLocal.context);

        TestSupport.assertEquals(
            "PF1AF F04 juvenile remains INELIGIBLE",
            HuntOutcome.INELIGIBLE, ineligibleResult.getOutcome());
        TestSupport.assertEquals(
            "PF1AF F04 satiated decision remains DECLINED",
            HuntOutcome.DECLINED, declinedResult.getOutcome());
        TestSupport.assertEquals(
            "PF1AF F04 failed attempt remains named",
            HuntOutcome.FAILED_ATTEMPT, failedResult.getOutcome());
        TestSupport.assertEquals(
            "PF1AF F04 no local prey remains named",
            HuntOutcome.NO_LOCAL_REACHABLE_PREY,
            noLocalResult.getOutcome());
        int records = ineligible.collector.kills.size() +
            declined.collector.kills.size() +
            failed.collector.kills.size() +
            noLocal.collector.kills.size();
        TestSupport.assertEquals(
            "PF1AF F04 non-success outcomes emit no kill record", 0,
            records);
    }

    private static void testStepStartWindowExcludesSameStepKill()
    {
        HuntSetup setup = huntSetup(true, true, 1,
            new FixedDecisionRandom(0.0, 0));
        RealizedKillWindowView before =
            setup.context.getRealizedKillWindowView();
        setup.system.huntWithOutcome(setup.predator,
            StepStartInteractionGeometry.capture(setup.field), setup.context);
        RealizedKillWindowView stillFrozen =
            setup.context.getRealizedKillWindowView();
        Field nextField = new Field(setup.field.getDepth(),
            setup.field.getWidth());
        nextField.placeAnimal(setup.predator,
            setup.predator.getLocation());
        setup.context.startStep(2, nextField);
        RealizedKillWindowView next =
            setup.context.getRealizedKillWindowView();
        TestSupport.assertEquals(
            "PF1AF F05 current step starts with zero same-step kills", 0,
            before.getWindow(setup.predator.getId(), 30).getCount());
        TestSupport.assertEquals(
            "PF1AF F05 current frozen view stays unchanged after HUNT", 0,
            stillFrozen.getWindow(setup.predator.getId(), 30).getCount());
        TestSupport.assertEquals(
            "PF1AF F05 next step admits the completed kill", 1,
            next.getWindow(setup.predator.getId(), 30).getCount());
    }

    private static void testWindowThirtySixtyAndLeftCensor()
    {
        RealizedKillLedger ledger = new RealizedKillLedger();
        ledger.startStep(1);
        ledger.append(new RealizedKillRecord(1, 10L, 20L, "Lion",
            "Gazelle", 58, 40));
        RealizedKillWindowView step31 = null;
        for(int step = 2; step <= 31; step++) {
            step31 = ledger.startStep(step);
        }
        RealizedKillWindowFact thirty = step31.getWindow(10L, 30);
        RealizedKillWindowFact sixty = step31.getWindow(10L, 60);
        RealizedKillWindowView step32 = ledger.startStep(32);
        TestSupport.assertEquals(
            "PF1AF F06 30-step inclusive boundary retains step 1", 1,
            thirty.getCount());
        TestSupport.assertFalse(
            "PF1AF F06 30-step window is complete at step 31",
            thirty.isLeftCensored());
        TestSupport.assertTrue(
            "PF1AF F06 60-step window remains left censored at step 31",
            sixty.isLeftCensored());
        TestSupport.assertEquals(
            "PF1AF F06 step 32 excludes step 1 from 30-window", 0,
            step32.getWindow(10L, 30).getCount());
        TestSupport.assertEquals(
            "PF1AF F06 step 32 retains step 1 in 60-window", 1,
            step32.getWindow(10L, 60).getCount());
    }

    private static void testLedgerBoundedAndDeathCleanup()
    {
        RealizedKillLedger ledger = new RealizedKillLedger();
        ledger.startStep(1);
        ledger.append(new RealizedKillRecord(1, 11L, 21L, "Cheetah",
            "Gazelle", 58, 20));
        ledger.onAnimalDeath(11L);
        RealizedKillWindowView afterDeath = ledger.startStep(2);
        for(int step = 3; step <= 62; step++) {
            ledger.startStep(step);
        }
        TestSupport.assertEquals(
            "PF1AF F07 predator death removes active lookup", 0,
            ledger.getActivePredatorCount());
        TestSupport.assertEquals(
            "PF1AF F07 dead predator absent from next snapshot", 0,
            afterDeath.getWindow(11L, 60).getCount());
        TestSupport.assertEquals(
            "PF1AF F07 history prunes beyond 60 completed steps", 0,
            ledger.getRetainedRecordCount());
    }

    private static void testLocalResourceL1Equivalence(
        IntegrationTrace trace)
    {
        TestSupport.assertTrue(
            "PF1AF F08 observed predator rows are non-empty",
            !trace.collector.predatorRows.isEmpty());
        TestSupport.assertEquals(
            "PF1AF F08 each local resource row equals L1-F", 0,
            trace.collector.localResourceMismatches);
    }

    private static void testLegacyProbabilityFactorExact(
        BreedingSetup setup)
    {
        LegacyBreedingAssessment assessment =
            setup.observation.getAssessment();
        double product = assessment.getBaseProbability();
        product *= assessment.getInfectionModifier();
        product *= assessment.getSurvivalModifier();
        product *= assessment.getStaminaModifier();
        product *= assessment.getWeatherModifier();
        product *= assessment.getFoodModifier();
        product *= assessment.getLowPopulationModifier();
        product *= assessment.getPopulationBalanceModifier();
        product *= assessment.getCrowdingModifier();
        TestSupport.assertTrue(
            "PF1AF F09 final probability is exact factor product",
            Double.doubleToLongBits(product) ==
                Double.doubleToLongBits(assessment.getFinalProbability()));
        TestSupport.assertEquals(
            "PF1AF F09 assessment candidate count is exact",
            setup.initialCandidates.size(),
            assessment.getActualDecisionCandidateCount());
    }

    private static void testNoSecondMateQueryOrRng(BreedingSetup setup)
    {
        BreedingRandomLedger ledger =
            setup.observation.getAssessment().getRandomLedger();
        TestSupport.assertEquals(
            "PF1AF F10 mate query executes exactly once", 1,
            setup.geometry.locationQueries);
        TestSupport.assertTrue(
            "PF1AF F10 mate query shuffle is named",
            ledger.isMateQueryShuffleRequested());
        TestSupport.assertEquals(
            "PF1AF F10 one legacy breeding roll is named", 1,
            setup.decisionRandom.doubleCalls);
        TestSupport.assertEquals(
            "PF1AF F10 one litter draw is named", 1,
            setup.decisionRandom.intCalls);
    }

    private static void testLitterBoundAndClaimCountExact(
        BreedingSetup setup)
    {
        LegacyBreedingAssessment assessment =
            setup.observation.getAssessment();
        TestSupport.assertEquals(
            "PF1AF F11 litter bound is profile max",
            setup.parent.getProfile().getMaxLitterSize(),
            assessment.getLitterDrawBound());
        TestSupport.assertEquals(
            "PF1AF F11 provisional claim count equals returned claims",
            setup.claims.size(), assessment.getProvisionalClaimCount());
        boolean ordinalsExact = true;
        for(int index = 0; index < setup.claims.size(); index++) {
            ordinalsExact = ordinalsExact &&
                setup.claims.get(index).getActor().getLitterOrdinal() == index;
        }
        TestSupport.assertTrue(
            "PF1AF F11 provisional ordinals are exact", ordinalsExact);
    }

    private static void testBirthParentChildReserveJoin(
        IntegrationTrace trace)
    {
        int joinedBirths = 0;
        boolean reservesExact = true;
        boolean parentDebitObserved = false;
        for(BreedingCommitFact commit : trace.collector.commits) {
            joinedBirths += commit.getCommittedBirthCount();
            if(commit.getCommittedBirthCount() > 0) {
                parentDebitObserved = parentDebitObserved ||
                    commit.getParentPostBirth().getStamina() <
                    commit.getParentPreBirth().getStamina();
            }
            for(NewbornReserveFact newborn : commit.getNewborns()) {
                SpeciesProfile profile =
                    SpeciesRegistry.getProfile(newborn.getSpecies());
                reservesExact = reservesExact &&
                    newborn.getReserve().getAge() == 0 &&
                    newborn.getReserve().getFood() ==
                        profile.getMaxFoodLevel() &&
                    newborn.getReserve().getHydration() ==
                        SavannahAnimal.MAX_HYDRATION &&
                    newborn.getReserve().getStamina() ==
                        SavannahAnimal.MAX_STAMINA;
            }
        }
        TestSupport.assertTrue(
            "PF1AF F12 integration run contains committed births",
            trace.birthEvents > 0);
        TestSupport.assertEquals(
            "PF1AF F12 every BIRTH joins one newborn reserve",
            trace.birthEvents, joinedBirths);
        TestSupport.assertTrue(
            "PF1AF F12 newborn initial reserves are exact", reservesExact);
        TestSupport.assertTrue(
            "PF1AF F12 successful parent debit has pre/post facts",
            parentDebitObserved);
    }

    private static void testAllRegisteredPredators(IntegrationTrace trace)
    {
        Set<String> expected = new HashSet<>();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            if(factory.getProfile().isPredator()) {
                expected.add(factory.getProfile().getName());
            }
        }
        Set<String> observed = new HashSet<>();
        for(PredatorFemaleAssessmentFact row :
            trace.collector.predatorRows) {
            observed.add(row.getSpecies());
        }
        TestSupport.assertEquals(
            "PF1AF F13 every registered predator has assessment rows",
            expected, observed);
    }

    private static void testObserverOffOnExact()
    {
        IntegrationTrace baseline = runIntegrationTrace(20, false);
        IntegrationTrace observed = runIntegrationTrace(20, true);
        TestSupport.assertEquals(
            "PF1AF F14 observer OFF/ON ecology fingerprints exact",
            baseline.fingerprints, observed.fingerprints);
        TestSupport.assertEquals(
            "PF1AF F14 observer OFF/ON plan/event traces exact",
            baseline.stepDigests, observed.stepDigests);
        TestSupport.assertEquals(
            "PF1AF F14 observer OFF/ON breeding RNG ledgers exact",
            baseline.breedingRngDigests, observed.breedingRngDigests);
        TestSupport.assertTrue(
            "PF1AF F14 observed arm emits PF1-aF facts",
            !observed.collector.kills.isEmpty() &&
            !observed.collector.legacy.isEmpty());
    }

    private static void testSchemaUniquenessAndOrphanZero(
        IntegrationTrace trace)
    {
        int committedChildren = 0;
        Set<String> assessmentKeys = new HashSet<>();
        Set<String> expectedOrdinals = new HashSet<>();
        Set<String> resolvedOrdinals = new HashSet<>();
        int committedOrdinals = 0;
        boolean unique = true;
        for(LegacyBreedingObservation observation :
            trace.collector.legacy) {
            String assessmentKey = observation.getStep() + ":" +
                observation.getParentId();
            unique = unique && assessmentKeys.add(assessmentKey);
            for(int ordinal = 0; ordinal <
                observation.getAssessment().getProvisionalClaimCount();
                ordinal++) {
                expectedOrdinals.add(assessmentKey + ":" + ordinal);
            }
        }
        for(BreedingCommitFact commit : trace.collector.commits) {
            committedChildren += commit.getCommittedBirthCount();
            unique = unique && assessmentKeys.contains(commit.getStep() +
                ":" + commit.getParentId());
        }
        for(SpawnResolutionTrace spawn : trace.spawnResolutions) {
            resolvedOrdinals.add(spawn.step + ":" +
                spawn.resolution.getActor().getParentId() + ":" +
                spawn.resolution.getActor().getLitterOrdinal());
            if(spawn.resolution.isCommitted()) committedOrdinals++;
        }
        TestSupport.assertTrue(
            "PF1AF F15 canonical schema fields are unique",
            Pf1aFactSchema.hasUniqueFields());
        TestSupport.assertEquals(
            "PF1AF F15 canonical schema covers every raw contract field",
            94, Pf1aFactSchema.getFields().size());
        TestSupport.assertEquals(
            "PF1AF F15 canonical schema hash is frozen",
            EXPECTED_SCHEMA_HASH, Pf1aFactSchema.sha256());
        TestSupport.assertEquals(
            "PF1AF F15 every successful HUNT has one kill record",
            trace.successfulHunts, trace.collector.kills.size());
        TestSupport.assertEquals(
            "PF1AF F15 every BIRTH has one commit join",
            trace.birthEvents, committedChildren);
        TestSupport.assertTrue(
            "PF1AF F15 assessment and commit keys have no orphan/duplicate",
            unique);
        TestSupport.assertEquals(
            "PF1AF F15 every provisional ordinal has one total resolution",
            expectedOrdinals, resolvedOrdinals);
        TestSupport.assertEquals(
            "PF1AF F15 committed ordinal count equals committed children",
            committedChildren, committedOrdinals);
        List<String> frozen1111 =
            Pf1aProbeSupport.loadFrozenReference(1111L);
        TestSupport.assertEquals(
            "PF1AF F15 archived seed-1111 reference covers 1..1000",
            1000, frozen1111.size());
        TestSupport.assertEquals(
            "PF1AF F15 archived seed-1111 reference sequence is exact",
            Pf1aProbeSupport.FROZEN_1111_1000_SEQUENCE_HASH,
            Pf1aProbeSupport.sequenceHash(frozen1111));
        List<String> frozen2111 =
            Pf1aProbeSupport.loadFrozenReference(2111L);
        TestSupport.assertEquals(
            "PF1AF F15 archived seed-2111 reference covers 1..1000",
            1000, frozen2111.size());
        TestSupport.assertEquals(
            "PF1AF F15 archived seed-2111 reference sequence is exact",
            Pf1aProbeSupport.FROZEN_2111_1000_SEQUENCE_HASH,
            Pf1aProbeSupport.sequenceHash(frozen2111));
        System.out.println("PF1AF_SCHEMA_HASH value=" +
            Pf1aFactSchema.sha256());
    }

    private static HuntSetup huntSetup(boolean includePrey, boolean adult,
                                       int foodLevel, Random huntRandom)
    {
        SimulationContext context = new SimulationContext(24, 24,
            SimulationConfig.builder().randomSeed(2111L)
                .terrainSeed(20260629L).build());
        Location[] pair = adjacentPassablePair(context.getTerrainMap());
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Cheetah predator = new Cheetah(false, pair[0],
            new FixedSexRandom(Sex.FEMALE), identities);
        if(adult) makeAdult(predator);
        TestSupport.setField(predator, "foodLevel", foodLevel);
        TestSupport.setField(predator, "stamina",
            SavannahAnimal.MAX_STAMINA);
        Field field = new Field(24, 24);
        field.placeAnimal(predator, pair[0]);
        Gazelle prey = null;
        if(includePrey) {
            prey = new Gazelle(false, pair[1],
                new FixedSexRandom(Sex.MALE), identities);
            field.placeAnimal(prey, pair[1]);
        }
        FactCollector collector = new FactCollector(context);
        context.addPf1aFactListener(collector);
        context.startStep(1, field);
        return new HuntSetup(context, field, predator, prey,
            new FoodChainPredationSystem(huntRandom), collector);
    }

    private static BreedingSetup controlledBreedingSetup()
    {
        SimulationContext context = new SimulationContext(24, 24,
            SimulationConfig.builder().randomSeed(2111L)
                .terrainSeed(20260629L).build());
        Location[] pair = adjacentPassablePair(context.getTerrainMap());
        SpeciesFactory factory = factoryFor(SpeciesRegistry.CHEETAH);
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        SavannahAnimal parent = factory.create(false, pair[0],
            new FixedSexRandom(Sex.FEMALE), identities);
        SavannahAnimal mate = factory.create(false, pair[1],
            new FixedSexRandom(Sex.MALE), identities);
        TestSupport.setField(parent, "age",
            parent.getProfile().getBreedingAge());
        makeAdult(mate);
        TestSupport.setField(parent, "foodLevel",
            parent.getProfile().getMaxFoodLevel());
        TestSupport.setField(parent, "stamina",
            SavannahAnimal.MAX_STAMINA);
        Field field = new Field(24, 24);
        field.placeAnimal(parent, pair[0]);
        field.placeAnimal(mate, pair[1]);
        FactCollector collector = new FactCollector(context);
        context.addPf1aFactListener(collector);
        context.startStep(1, field);

        CountingGeometry geometry = new CountingGeometry(
            StepStartInteractionGeometry.capture(field));
        List<Location> candidates = passableLocations(
            context.getTerrainMap(), field, 3);
        List<Location> initial = new ArrayList<>(candidates);
        DecisionBirthCandidateFact candidateFact =
            DecisionBirthCandidateFact.capture(1, parent, candidates);
        FixedDecisionRandom decisionRandom =
            new FixedDecisionRandom(0.0, Integer.MAX_VALUE);
        MateFindingBreedingSystem breeding =
            new MateFindingBreedingSystem(decisionRandom);
        List<PositionClaim> claims = breeding.propose(parent, geometry,
            context, candidates, candidateFact);
        if(collector.legacy.size() != 1) {
            throw new IllegalStateException(
                "controlled PF1-aF breeding fixture emitted no assessment");
        }
        return new BreedingSetup(parent, initial, claims,
            collector.legacy.get(0), geometry, decisionRandom);
    }

    private static IntegrationTrace runIntegrationTrace(int steps,
                                                        boolean observe)
    {
        Randomizer.reset();
        Simulator simulator = new Simulator(SimulationConfig.builder()
            .randomSeed(1111L).terrainSeed(20260629L).build(), false);
        simulator.setVerbose(false);
        SimulationContext context = simulator.getContext();
        FactCollector collector = new FactCollector(context);
        EventCounter events = new EventCounter();
        HuntCounter hunts = new HuntCounter();
        BreedingRngDigest breedingRng = new BreedingRngDigest();
        context.addEventListener(events);
        context.addHuntObservationListener(hunts);
        context.addBreedingRandomObservationListener(breedingRng);
        if(observe) context.addPf1aFactListener(collector);
        List<String> fingerprints = new ArrayList<>();
        List<String> stepDigests = new ArrayList<>();
        List<String> breedingRngDigests = new ArrayList<>();
        List<SpawnResolutionTrace> spawnResolutions = new ArrayList<>();
        for(int step = 1; step <= steps &&
            simulator.getField().isViable(); step++) {
            simulator.simulateOneStep();
            fingerprints.add(SimulationFingerprint.capture(simulator));
            stepDigests.add(stepDigest(context));
            breedingRngDigests.add(breedingRng.drain(step));
            PositionPlan plan = context.getLastPositionPlan();
            if(plan != null) {
                for(PositionResolution resolution :
                    plan.getResolutions().values()) {
                    if(resolution.getActor().isSpawn()) {
                        spawnResolutions.add(
                            new SpawnResolutionTrace(step, resolution));
                    }
                }
            }
        }
        return new IntegrationTrace(fingerprints, stepDigests,
            breedingRngDigests, spawnResolutions, collector, events.births,
            hunts.successes);
    }

    private static String stepDigest(SimulationContext context)
    {
        StringBuilder value = new StringBuilder();
        for(SimulationEvent event : context.getCurrentEvents()) {
            value.append(event.type).append(':').append(event.actorId)
                .append(':').append(event.targetId).append(':')
                .append(event.row).append(',').append(event.col)
                .append('|');
        }
        PositionPlan plan = context.getLastPositionPlan();
        if(plan != null) {
            for(Map.Entry<PositionActorKey, PositionResolution> entry :
                plan.getResolutions().entrySet()) {
                PositionResolution resolution = entry.getValue();
                value.append(entry.getKey()).append(':')
                    .append(resolution.getDisposition()).append(':')
                    .append(resolution.getDestination()).append('|');
            }
        }
        return value.toString();
    }

    private static int countEvents(SimulationContext context,
                                   SimulationEvent.EventType type)
    {
        int count = 0;
        for(SimulationEvent event : context.getCurrentEvents()) {
            if(event.type == type) count++;
        }
        return count;
    }

    private static void makeAdult(SavannahAnimal animal)
    {
        TestSupport.setField(animal, "age",
            animal.getProfile().getAdultAge());
    }

    private static SpeciesFactory factoryFor(String species)
    {
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            if(factory.getProfile().getName().equals(species)) return factory;
        }
        throw new IllegalArgumentException("unknown fixture species");
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
                        if((dr != 0 || dc != 0) &&
                           second.row() >= 0 &&
                           second.row() < terrain.getDepth() &&
                           second.col() >= 0 &&
                           second.col() < terrain.getWidth() &&
                           terrain.isPassable(second)) {
                            return new Location[] { first, second };
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("no passable fixture pair");
    }

    private static List<Location> passableLocations(TerrainMap terrain,
                                                     Field field, int count)
    {
        List<Location> result = new ArrayList<>();
        for(int row = 0; row < terrain.getDepth() &&
            result.size() < count; row++) {
            for(int col = 0; col < terrain.getWidth() &&
                result.size() < count; col++) {
                Location location = new Location(row, col);
                if(terrain.isPassable(location) &&
                   field.getAnimalAt(location) == null) {
                    result.add(location);
                }
            }
        }
        if(result.size() != count) {
            throw new IllegalStateException(
                "not enough passable fixture locations");
        }
        return result;
    }

    private static final class HuntSetup
    {
        private final SimulationContext context;
        private final Field field;
        private final Cheetah predator;
        private final Gazelle prey;
        private final FoodChainPredationSystem system;
        private final FactCollector collector;
        private HuntResult result;

        HuntSetup(SimulationContext context, Field field, Cheetah predator,
                  Gazelle prey, FoodChainPredationSystem system,
                  FactCollector collector)
        {
            this.context = context;
            this.field = field;
            this.predator = predator;
            this.prey = prey;
            this.system = system;
            this.collector = collector;
        }
    }

    private static final class BreedingSetup
    {
        private final SavannahAnimal parent;
        private final List<Location> initialCandidates;
        private final List<PositionClaim> claims;
        private final LegacyBreedingObservation observation;
        private final CountingGeometry geometry;
        private final FixedDecisionRandom decisionRandom;

        BreedingSetup(SavannahAnimal parent,
                      List<Location> initialCandidates,
                      List<PositionClaim> claims,
                      LegacyBreedingObservation observation,
                      CountingGeometry geometry,
                      FixedDecisionRandom decisionRandom)
        {
            this.parent = parent;
            this.initialCandidates = initialCandidates;
            this.claims = claims;
            this.observation = observation;
            this.geometry = geometry;
            this.decisionRandom = decisionRandom;
        }
    }

    private static final class IntegrationTrace
    {
        private final List<String> fingerprints;
        private final List<String> stepDigests;
        private final List<String> breedingRngDigests;
        private final List<SpawnResolutionTrace> spawnResolutions;
        private final FactCollector collector;
        private final int birthEvents;
        private final int successfulHunts;

        IntegrationTrace(List<String> fingerprints, List<String> stepDigests,
                         List<String> breedingRngDigests,
                         List<SpawnResolutionTrace> spawnResolutions,
                         FactCollector collector, int birthEvents,
                         int successfulHunts)
        {
            this.fingerprints = fingerprints;
            this.stepDigests = stepDigests;
            this.breedingRngDigests = breedingRngDigests;
            this.spawnResolutions = spawnResolutions;
            this.collector = collector;
            this.birthEvents = birthEvents;
            this.successfulHunts = successfulHunts;
        }
    }

    private static final class SpawnResolutionTrace
    {
        private final int step;
        private final PositionResolution resolution;

        SpawnResolutionTrace(int step, PositionResolution resolution)
        {
            this.step = step;
            this.resolution = resolution;
        }
    }

    private static final class FactCollector implements Pf1aFactListener
    {
        private final SimulationContext context;
        private final List<RealizedKillRecord> kills = new ArrayList<>();
        private final List<LegacyBreedingObservation> legacy =
            new ArrayList<>();
        private final List<BreedingCommitFact> commits =
            new ArrayList<>();
        private final List<PredatorFemaleAssessmentFact> predatorRows =
            new ArrayList<>();
        private int localResourceMismatches;

        FactCollector(SimulationContext context)
        {
            this.context = context;
        }

        @Override
        public void onRealizedKill(RealizedKillRecord record)
        {
            kills.add(record);
        }

        @Override
        public void onLegacyBreeding(
            LegacyBreedingObservation observation)
        {
            legacy.add(observation);
        }

        @Override
        public void onBreedingCommit(BreedingCommitFact commit)
        {
            commits.add(commit);
        }

        @Override
        public void onPredatorFemaleAssessment(
            PredatorFemaleAssessmentFact assessment)
        {
            predatorRows.add(assessment);
            ReachableResourceFact direct = context.getSpatialEcologyView()
                .getReachableResources(assessment.getLocation(),
                    assessment.getEffectiveHuntingRange(),
                    assessment.getSpecies());
            if(!direct.equals(assessment.getLocalResource())) {
                localResourceMismatches++;
            }
        }
    }

    private static final class EventCounter
        implements SimulationEventListener
    {
        private int births;

        @Override
        public void onEvent(SimulationEvent event)
        {
            if(event.type == SimulationEvent.EventType.BIRTH) births++;
        }
    }

    private static final class HuntCounter
        implements HuntObservationListener
    {
        private int successes;

        @Override
        public void onHuntResult(HuntObservation observation)
        {
            if(observation.getResult().getOutcome() ==
               HuntOutcome.SUCCESS) successes++;
        }
    }

    private static final class BreedingRngDigest
        implements BreedingRandomObservationListener
    {
        private final StringBuilder current = new StringBuilder();
        private int latestStep = -1;

        @Override
        public void onBreedingRandomObservation(
            BreedingRandomObservation observation)
        {
            latestStep = observation.getStep();
            BreedingRandomLedger ledger = observation.getRandomLedger();
            current.append(observation.getParentId()).append(':')
                .append(observation.getSpecies()).append(':')
                .append(ledger.isMateQueryShuffleRequested()).append(':')
                .append(ledger.getMateQueryShuffleInputSize()).append(':')
                .append(ledger.hasLegacyBreedingRoll()).append(':')
                .append(ledger.hasLitterDraw()).append(':')
                .append(ledger.getLitterDrawBound()).append('|');
        }

        String drain(int step)
        {
            if(latestStep > step) {
                throw new IllegalStateException(
                    "breeding RNG ledger advanced beyond simulation");
            }
            String result = current.toString();
            current.setLength(0);
            return result;
        }
    }

    private static final class CountingGeometry
        implements InteractionGeometry
    {
        private final InteractionGeometry delegate;
        private int locationQueries;

        CountingGeometry(InteractionGeometry delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public Animal getAnimalAt(Location location)
        {
            return delegate.getAnimalAt(location);
        }

        @Override
        public List<Location> getLocationsWithin(Location origin, int range,
                                                 Random random)
        {
            locationQueries++;
            return delegate.getLocationsWithin(origin, range, random);
        }

        @Override
        public int getLivingPopulation()
        {
            return delegate.getLivingPopulation();
        }

        @Override
        public int getDepth() { return delegate.getDepth(); }

        @Override
        public int getWidth() { return delegate.getWidth(); }
    }

    private static final class FixedSexRandom extends Random
    {
        private final boolean male;

        FixedSexRandom(Sex sex)
        {
            male = sex == Sex.MALE;
        }

        @Override
        public boolean nextBoolean() { return male; }
    }

    private static final class FixedDecisionRandom extends Random
    {
        private final double fixedDouble;
        private final int fixedInt;
        private int doubleCalls;
        private int intCalls;

        FixedDecisionRandom(double fixedDouble, int fixedInt)
        {
            this.fixedDouble = fixedDouble;
            this.fixedInt = fixedInt;
        }

        @Override
        public double nextDouble()
        {
            doubleCalls++;
            return fixedDouble;
        }

        @Override
        public int nextInt(int bound)
        {
            intCalls++;
            return fixedInt == Integer.MAX_VALUE
                ? bound - 1 : Math.floorMod(fixedInt, bound);
        }
    }
}
