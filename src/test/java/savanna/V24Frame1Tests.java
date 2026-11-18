package savanna;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Policy-neutral acceptance fixtures for the V24 FRAME-1 S0-S4 boundary. */
public final class V24Frame1Tests
{
    private static final long ECOLOGY_SEED = 2111L;
    private static final long TERRAIN_SEED = 20260629L;
    private static final int FRAME_STEP = 6;

    private V24Frame1Tests()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("V24-FRAME-1 policy-neutral acceptance fixtures");
        run();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    public static void run()
    {
        TestSupport.startGroup("V24-FRAME-1 S0-S4 causal frame tests");
        testFrozenHuntOpportunitySurvivesPostCaptureDebit();
        testConceptionProposalAndHuntShareOneFrame();
        testPureProposalNoMateAndResourceFloor();
        testPrimaryFailureAndProposalPurityGuard();
        testOrdinalLossVocabulary();
        testEveryStepStartActorPublishesExactlyOneLedger();
        testFrameObserverTransparency();
        TestSupport.finishGroup("V24_FRAME_1_FIXTURES");
    }

    private static void testFrozenHuntOpportunitySurvivesPostCaptureDebit()
    {
        ControlledSetup setup = controlledSetup(true, true,
            SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH)
                .getMaxFoodLevel());
        String frameId = StepFrameLedger.frameId(FRAME_STEP,
            setup.parent.getId());
        HuntOpportunityFact frozen = HuntOpportunityFact.capture(FRAME_STEP,
            setup.parent, frameId, setup.geometry, setup.context);

        TestSupport.assertEquals("FRAME F01 S0 freezes the boundary prey", 1,
            frozen.getCandidateCount());
        TestSupport.assertEquals("FRAME F01 S0 captures full-stamina range", 7,
            frozen.getEffectiveRange());
        TestSupport.assertClose("FRAME F01 S0 captures pre-debit stamina",
            SavannahAnimal.MAX_STAMINA,
            frozen.getCapturePhysiology().getStamina(), 0.000001);

        setup.parent.spendStamina(40.0);
        HuntOpportunityFact driftFault = HuntOpportunityFact.capture(
            FRAME_STEP, setup.parent, frameId, setup.geometry, setup.context);
        TestSupport.assertEquals("FRAME F01 a recompute would shrink range", 6,
            driftFault.getEffectiveRange());
        TestSupport.assertEquals("FRAME F01 a recompute would lose boundary prey",
            0, driftFault.getCandidateCount());

        HuntResult result = new FoodChainPredationSystem(
            new FixedDoubleRandom(0.0)).huntWithOutcome(setup.parent,
                setup.geometry, setup.context, frozen);
        TestSupport.assertTrue("FRAME F01 S2 consumes exact S0 identity",
            frozen.hasSameIdentity(result.getOpportunityFact()));
        TestSupport.assertEquals("FRAME F01 S2 keeps frozen range", 7,
            result.getHuntingRange());
        TestSupport.assertEquals("FRAME F01 S2 keeps frozen candidate set", 1,
            result.getLocalReachableEdibleCount());
        TestSupport.assertEquals("FRAME F01 frozen boundary prey remains actionable",
            HuntOutcome.SUCCESS, result.getOutcome());

        Cheetah other = new Cheetah(false, setup.origin,
            new FixedSexRandom(Sex.FEMALE), new AnimalIdentityAllocator());
        makeBreedingAdult(other);
        TestSupport.assertTrue("FRAME F01 mismatched actor cannot consume S0 fact",
            rejects(() -> new FoodChainPredationSystem(
                new FixedDoubleRandom(0.0)).huntWithOutcome(other,
                    setup.geometry, setup.context, frozen)));
    }

    private static void testConceptionProposalAndHuntShareOneFrame()
    {
        ControlledSetup setup = controlledSetup(true, true,
            SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH)
                .getMaxFoodLevel());
        StepFrameLedger.FrameDraft draft = StepFrameLedger.FrameDraft.begin(
            FRAME_STEP, setup.parent);
        draft.captureS0(setup.parent, setup.origin, setup.geometry,
            setup.context);

        List<Location> birthCandidates = TerrainNavigator.freeAdjacent(
            setup.field, setup.context.getTerrainMap(), setup.origin,
            new Random(17L));
        int populationBefore = setup.field.getLivingPopulation();
        AnimalPhysiologyFact before = AnimalPhysiologyFact.capture(setup.parent);
        StepFrameLedger.ReproductionProposal proposal =
            new MateFindingBreedingSystem(new FixedDecisionRandom(0.0, 0))
                .proposeFrame(setup.parent, setup.geometry, setup.context,
                    birthCandidates, draft.getResourceClock(), null);
        AnimalPhysiologyFact after = AnimalPhysiologyFact.capture(setup.parent);

        TestSupport.assertEquals("FRAME F02 S1 creates provisional ordinals",
            StepFrameLedger.ProposalReason.PROVISIONAL_CLAIMS,
            proposal.getReason());
        TestSupport.assertTrue("FRAME F02 accepted proposal owns tokens",
            proposal.getClaimCount() > 0);
        TestSupport.assertTrue("FRAME F02 S1 is physiology-pure",
            samePhysiology(before, after) &&
            samePhysiology(proposal.getBeforePhysiology(),
                proposal.getAfterPhysiology()));
        TestSupport.assertEquals("FRAME F02 S1 creates no child", populationBefore,
            setup.field.getLivingPopulation());
        draft.recordS1(proposal);

        HuntResult hunt = new FoodChainPredationSystem(
            new FixedDoubleRandom(0.0)).huntWithOutcome(setup.parent,
                setup.geometry, setup.context, draft.getHuntOpportunity());
        StepFrameLedger.PrimaryAction primary = StepFrameLedger.PrimaryAction
            .hunt(draft.getFrameId(), FRAME_STEP, setup.parent, hunt);
        draft.recordS2(primary);
        StepFrameLedger.ReproductionCommit commit =
            StepFrameLedger.ReproductionCommit.submit(proposal,
                setup.parent.isAlive());
        draft.recordS3(commit);

        List<StepFrameLedger.OrdinalOutcome> outcomes = new ArrayList<>();
        for(PositionClaim claim : proposal.getPositionClaims()) {
            PositionResolution loss = new PositionResolution(claim.getActor(),
                PositionClaimKind.SPAWN, claim.getRequested(), null, null, -1,
                PositionPlanDisposition.REJECTED,
                PositionResolutionReason.HIGHER_PRIORITY_LOSS);
            outcomes.add(StepFrameLedger.OrdinalOutcome.fromResolution(claim,
                loss));
        }
        PositionResolution adult = adultStay(setup.parent, setup.origin);
        StepFrameLedger ledger = draft.publishS4(setup.parent, adult, outcomes,
            StepFrameLedger.TerminalReason.COMPLETED);

        TestSupport.assertEquals("FRAME F02 exact S0-S4 order",
            List.of(StepFrameLedger.Phase.S0_CAPTURE,
                StepFrameLedger.Phase.S1_REPRODUCTION_PROPOSAL,
                StepFrameLedger.Phase.S2_PRIMARY_ACTION,
                StepFrameLedger.Phase.S3_REPRODUCTION_COMMIT,
                StepFrameLedger.Phase.S4_PUBLISH), ledger.getPhases());
        TestSupport.assertEquals("FRAME F02 HUNT and proposal join one frame",
            ledger.getFrameId(), ledger.getPrimaryAction().getFrameId());
        TestSupport.assertTrue("FRAME F02 S2 retains the exact S0 opportunity",
            ledger.getHuntOpportunity().hasSameIdentity(
                ledger.getPrimaryAction().getHuntOpportunityFact()));
        TestSupport.assertEquals("FRAME F02 S3 submits accepted tokens",
            StepFrameLedger.CommitDisposition.SUBMITTED_TO_POSITION_BOARD,
            ledger.getReproductionCommit().getDisposition());
        TestSupport.assertEquals("FRAME F02 S4 resolves every ordinal",
            proposal.getClaimCount(), ledger.getOrdinalOutcomes().size());
        TestSupport.assertTrue("FRAME F02 losing ordinals are explicit",
            allLost(ledger.getOrdinalOutcomes()));
        TestSupport.assertTrue("FRAME F02 published facts are immutable",
            rejects(() -> ledger.getPhases().add(
                StepFrameLedger.Phase.S0_CAPTURE)) &&
            rejects(() -> ledger.getHuntOpportunity().getCandidates().add(null)) &&
            rejects(() -> ledger.getOrdinalOutcomes().add(null)));
        TestSupport.assertTrue("FRAME F02 draft can publish only once",
            rejects(() -> draft.publishS4(setup.parent, adult, outcomes,
                StepFrameLedger.TerminalReason.COMPLETED)));
    }

    private static void testPureProposalNoMateAndResourceFloor()
    {
        int floor = SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH)
            .getMaxFoodLevel() / 3;
        ControlledSetup atFloor = controlledSetup(true, true, floor);
        FixedDecisionRandom floorRandom = new FixedDecisionRandom(0.0, 0);
        StepFrameLedger.ResourceClock floorClock =
            StepFrameLedger.ResourceClock.capture(FRAME_STEP, atFloor.parent,
                StepFrameLedger.frameId(FRAME_STEP, atFloor.parent.getId()));
        StepFrameLedger.ReproductionProposal floorProposal =
            new MateFindingBreedingSystem(floorRandom).proposeFrame(
                atFloor.parent, atFloor.geometry, atFloor.context,
                freeAdjacent(atFloor), floorClock, null);
        TestSupport.assertEquals("FRAME F03 resource floor is exclusive",
            StepFrameLedger.ProposalReason.NOT_ELIGIBLE,
            floorProposal.getReason());
        TestSupport.assertTrue("FRAME F03 floor rejection requests no conception RNG",
            floorRandom.getDoubleCalls() == 0 && floorRandom.getIntCalls() == 0);

        ControlledSetup aboveFloor = controlledSetup(true, true, floor + 1);
        FixedDecisionRandom acceptedRandom = new FixedDecisionRandom(0.0, 0);
        StepFrameLedger.ResourceClock aboveClock =
            StepFrameLedger.ResourceClock.capture(FRAME_STEP,
                aboveFloor.parent, StepFrameLedger.frameId(FRAME_STEP,
                    aboveFloor.parent.getId()));
        StepFrameLedger.ReproductionProposal accepted =
            new MateFindingBreedingSystem(acceptedRandom).proposeFrame(
                aboveFloor.parent, aboveFloor.geometry, aboveFloor.context,
                freeAdjacent(aboveFloor), aboveClock, null);
        TestSupport.assertEquals("FRAME F03 one unit above floor is eligible",
            StepFrameLedger.ProposalReason.PROVISIONAL_CLAIMS,
            accepted.getReason());
        TestSupport.assertEquals("FRAME F03 S0 clock freezes floor resource",
            floor + 1, aboveClock.getPhysiology().getFood());
        TestSupport.assertTrue("FRAME F03 prior-step kill credit is eligible",
            aboveClock.mayConsumeRealizedKillFromStep(FRAME_STEP - 1));
        TestSupport.assertFalse("FRAME F03 same-step kill credit is ineligible",
            aboveClock.mayConsumeRealizedKillFromStep(FRAME_STEP));

        ControlledSetup noMate = controlledSetup(false, true,
            SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH)
                .getMaxFoodLevel());
        FixedDecisionRandom noMateRandom = new FixedDecisionRandom(0.0, 0);
        StepFrameLedger.ResourceClock noMateClock =
            StepFrameLedger.ResourceClock.capture(FRAME_STEP, noMate.parent,
                StepFrameLedger.frameId(FRAME_STEP, noMate.parent.getId()));
        AnimalPhysiologyFact noMateBefore = AnimalPhysiologyFact.capture(
            noMate.parent);
        StepFrameLedger.ReproductionProposal noMateProposal =
            new MateFindingBreedingSystem(noMateRandom).proposeFrame(
                noMate.parent, noMate.geometry, noMate.context,
                freeAdjacent(noMate), noMateClock, null);
        TestSupport.assertEquals("FRAME F03 no mate has explicit terminal reason",
            StepFrameLedger.ProposalReason.NO_MATE, noMateProposal.getReason());
        TestSupport.assertEquals("FRAME F03 no mate creates zero ordinals", 0,
            noMateProposal.getClaimCount());
        TestSupport.assertTrue("FRAME F03 no mate consumes no roll/litter RNG",
            noMateRandom.getDoubleCalls() == 0 &&
            noMateRandom.getIntCalls() == 0);
        TestSupport.assertTrue("FRAME F03 no mate leaves physiology exact",
            samePhysiology(noMateBefore,
                AnimalPhysiologyFact.capture(noMate.parent)));
    }

    private static void testPrimaryFailureAndProposalPurityGuard()
    {
        ControlledSetup setup = controlledSetup(false, true, 0);
        StepFrameLedger.FrameDraft draft = StepFrameLedger.FrameDraft.begin(
            FRAME_STEP, setup.parent);
        draft.captureS0(setup.parent, setup.origin, setup.geometry,
            setup.context);
        StepFrameLedger.ReproductionProposal proposal =
            new MateFindingBreedingSystem(new FixedDecisionRandom(0.0, 0))
                .proposeFrame(setup.parent, setup.geometry, setup.context,
                    freeAdjacent(setup), draft.getResourceClock(), null);
        draft.recordS1(proposal);
        HuntResult failed = new FoodChainPredationSystem(
            new FixedDoubleRandom(0.99)).huntWithOutcome(setup.parent,
                setup.geometry, setup.context, draft.getHuntOpportunity());
        draft.recordS2(StepFrameLedger.PrimaryAction.hunt(draft.getFrameId(),
            FRAME_STEP, setup.parent, failed));
        StepFrameLedger.ReproductionCommit commit =
            StepFrameLedger.ReproductionCommit.submit(proposal,
                setup.parent.isAlive());
        draft.recordS3(commit);
        StepFrameLedger ledger = draft.publishS4(setup.parent,
            adultStay(setup.parent, setup.origin), List.of(),
            StepFrameLedger.TerminalReason.COMPLETED);

        TestSupport.assertEquals("FRAME F04 primary failure is explicit",
            HuntOutcome.FAILED_ATTEMPT,
            ledger.getPrimaryAction().getHuntResult().getOutcome());
        TestSupport.assertTrue("FRAME F04 failed primary retains S0 identity",
            ledger.getHuntOpportunity().hasSameIdentity(
                failed.getOpportunityFact()));
        TestSupport.assertTrue("FRAME F04 failed HUNT creates no kill",
            setup.prey.isAlive() &&
            setup.context.getDeathCount(DeathCause.PREDATION) == 0);
        TestSupport.assertEquals("FRAME F04 rejected reproduction has no half commit",
            StepFrameLedger.CommitDisposition.NO_PROVISIONAL_CLAIMS,
            commit.getDisposition());

        ControlledSetup purity = controlledSetup(false, false,
            SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH)
                .getMaxFoodLevel());
        String frameId = StepFrameLedger.frameId(FRAME_STEP,
            purity.parent.getId());
        StepFrameLedger.ResourceClock clock =
            StepFrameLedger.ResourceClock.capture(FRAME_STEP, purity.parent,
                frameId);
        AnimalPhysiologyFact before = AnimalPhysiologyFact.capture(purity.parent);
        purity.parent.spendStamina(1.0);
        AnimalPhysiologyFact after = AnimalPhysiologyFact.capture(purity.parent);
        TestSupport.assertTrue("FRAME F04 mutation fault cannot form pure S1 proposal",
            rejects(() -> StepFrameLedger.ReproductionProposal.create(frameId,
                FRAME_STEP, purity.parent, clock,
                StepFrameLedger.ProposalReason.NOT_REQUESTED, List.of(), null,
                before, after)));
    }

    private static void testOrdinalLossVocabulary()
    {
        ControlledSetup setup = controlledSetup(false, false,
            SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH)
                .getMaxFoodLevel());
        Location destination = freeAdjacent(setup).get(0);
        PositionClaim claim = PositionClaim.spawn(setup.parent.getId(), 3,
            destination, List.of(), destination, 0, 0);
        PositionResolution loss = new PositionResolution(claim.getActor(),
            PositionClaimKind.SPAWN, destination, null, null, -1,
            PositionPlanDisposition.REJECTED,
            PositionResolutionReason.HIGHER_PRIORITY_LOSS);
        StepFrameLedger.OrdinalOutcome lost =
            StepFrameLedger.OrdinalOutcome.fromResolution(claim, loss);
        StepFrameLedger.OrdinalOutcome pregnancyLoss =
            StepFrameLedger.OrdinalOutcome.pregnancyLoss(
                setup.parent.getId(), 4);
        TestSupport.assertEquals("FRAME F05 losing ordinal is named",
            StepFrameLedger.OrdinalDisposition.LOST_POSITION_RESOLUTION,
            lost.getDisposition());
        TestSupport.assertEquals("FRAME F05 position loss reason is retained",
            PositionResolutionReason.HIGHER_PRIORITY_LOSS,
            lost.getPositionReason());
        TestSupport.assertEquals("FRAME F05 pregnancy loss is explicit",
            StepFrameLedger.OrdinalDisposition.PREGNANCY_LOSS,
            pregnancyLoss.getDisposition());
    }

    private static void testEveryStepStartActorPublishesExactlyOneLedger()
    {
        Randomizer.reset();
        Simulator simulator = new Simulator(SimulationConfig.builder()
            .randomSeed(ECOLOGY_SEED).terrainSeed(TERRAIN_SEED).build(), false);
        simulator.setVerbose(false);
        Set<Long> stepStartIds = livingIds(simulator.getField());
        FrameCollector collector = new FrameCollector();
        simulator.getContext().addStepFrameLedgerListener(collector);
        simulator.simulateOneStep();
        List<StepFrameLedger> ledgers = simulator.getContext()
            .getCurrentStepFrameLedgers();

        TestSupport.assertEquals("FRAME F06 one ledger per step-start actor",
            stepStartIds.size(), ledgers.size());
        TestSupport.assertEquals("FRAME F06 observer receives the same ledger set",
            stepStartIds.size(), collector.getCount());
        TestSupport.assertEquals("FRAME F06 ledger actor identity set is exact",
            stepStartIds, ledgerActorIds(ledgers));
        TestSupport.assertFalse("FRAME F06 no actor publishes twice",
            collector.hasDuplicate());
        TestSupport.assertTrue("FRAME F06 every ledger has complete joined phases",
            allCompleteAndJoined(ledgers));
        TestSupport.assertTrue("FRAME F06 context exposes immutable ledger list",
            rejects(() -> ledgers.add(null)));
    }

    private static void testFrameObserverTransparency()
    {
        FrameTrace baseline = runTrace(80, false);
        FrameTrace observed = runTrace(80, true);
        TestSupport.assertEquals("FRAME F07 observer leaves ecology/plan/event/frame trace exact",
            baseline.stepDigests, observed.stepDigests);
        TestSupport.assertEquals("FRAME F07 both traces complete 80 steps", 80,
            observed.completedSteps);
        TestSupport.assertTrue("FRAME F07 observer receives immutable ledgers",
            observed.observedLedgerCount > 0 && !observed.observerDuplicate);
    }

    private static ControlledSetup controlledSetup(boolean includeMate,
                                                   boolean includePrey,
                                                   int parentFood)
    {
        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(ECOLOGY_SEED).terrainSeed(TERRAIN_SEED).build();
        SimulationContext context = new SimulationContext(24, 24, config);
        Location[] locations = boundaryFixtureLocations(context.getTerrainMap(),
            7);
        Location origin = locations[0];
        Location preyLocation = locations[1];
        Location mateLocation = locations[2];
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Cheetah parent = new Cheetah(false, origin,
            new FixedSexRandom(Sex.FEMALE), identities);
        makeBreedingAdult(parent);
        TestSupport.setField(parent, "foodLevel", parentFood);
        TestSupport.setField(parent, "stamina", SavannahAnimal.MAX_STAMINA);
        Field field = new Field(24, 24);
        field.placeAnimal(parent, origin);
        Cheetah mate = null;
        if(includeMate) {
            mate = new Cheetah(false, mateLocation,
                new FixedSexRandom(Sex.MALE), identities);
            makeBreedingAdult(mate);
            field.placeAnimal(mate, mateLocation);
        }
        Gazelle prey = null;
        if(includePrey) {
            prey = new Gazelle(false, preyLocation,
                new FixedSexRandom(Sex.MALE), identities);
            field.placeAnimal(prey, preyLocation);
        }
        context.startStep(FRAME_STEP, field);
        return new ControlledSetup(context, field,
            StepStartInteractionGeometry.capture(field), parent, mate, prey,
            origin);
    }

    private static Location[] boundaryFixtureLocations(TerrainMap terrain,
                                                       int distance)
    {
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location origin = new Location(row, col);
                if(!terrain.isPassable(origin)) continue;
                List<Location> adjacent = passableAdjacent(terrain, origin);
                if(adjacent.size() < 2) continue;
                TerrainNavigator.Reachability reachable =
                    TerrainNavigator.reachableDistances(terrain, origin,
                        distance);
                for(int preyRow = 0; preyRow < terrain.getDepth(); preyRow++) {
                    for(int preyCol = 0; preyCol < terrain.getWidth(); preyCol++) {
                        Location prey = new Location(preyRow, preyCol);
                        if(reachable.distanceTo(prey) == distance) {
                            return new Location[] { origin, prey,
                                adjacent.get(0) };
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("no FRAME boundary fixture locations");
    }

    private static List<Location> passableAdjacent(TerrainMap terrain,
                                                   Location origin)
    {
        List<Location> result = new ArrayList<>();
        for(int dr = -1; dr <= 1; dr++) {
            for(int dc = -1; dc <= 1; dc++) {
                Location next = new Location(origin.row() + dr,
                    origin.col() + dc);
                if((dr != 0 || dc != 0) && next.row() >= 0 &&
                   next.row() < terrain.getDepth() && next.col() >= 0 &&
                   next.col() < terrain.getWidth() &&
                   terrain.isPassable(next)) result.add(next);
            }
        }
        return result;
    }

    private static List<Location> freeAdjacent(ControlledSetup setup)
    {
        return TerrainNavigator.freeAdjacent(setup.field,
            setup.context.getTerrainMap(), setup.origin, new Random(23L));
    }

    private static void makeBreedingAdult(SavannahAnimal animal)
    {
        TestSupport.setField(animal, "age",
            Math.max(animal.getProfile().getAdultAge(),
                animal.getProfile().getBreedingAge()));
    }

    private static PositionResolution adultStay(SavannahAnimal actor,
                                                Location origin)
    {
        return new PositionResolution(PositionActorKey.adult(actor.getId()),
            PositionClaimKind.STAY, null, origin,
            PositionCandidateSource.ORIGIN_STAY, 0,
            PositionPlanDisposition.COMMITTED,
            PositionResolutionReason.OWNER_STAY_PROTECTION);
    }

    private static boolean samePhysiology(AnimalPhysiologyFact first,
                                          AnimalPhysiologyFact second)
    {
        return first != null && second != null &&
            first.getAge() == second.getAge() &&
            first.getFood() == second.getFood() &&
            Double.doubleToLongBits(first.getHydration()) ==
                Double.doubleToLongBits(second.getHydration()) &&
            Double.doubleToLongBits(first.getStamina()) ==
                Double.doubleToLongBits(second.getStamina()) &&
            Double.doubleToLongBits(first.getSurvivalPressure()) ==
                Double.doubleToLongBits(second.getSurvivalPressure());
    }

    private static boolean allLost(List<StepFrameLedger.OrdinalOutcome> values)
    {
        if(values.isEmpty()) return false;
        for(StepFrameLedger.OrdinalOutcome value : values) {
            if(value.getDisposition() !=
               StepFrameLedger.OrdinalDisposition.LOST_POSITION_RESOLUTION) {
                return false;
            }
        }
        return true;
    }

    private static boolean allCompleteAndJoined(List<StepFrameLedger> ledgers)
    {
        for(StepFrameLedger ledger : ledgers) {
            if(ledger.getPhases().size() != 5 ||
               ledger.getFrameEntryPhysiology() == null ||
               ledger.getResourceClock() == null ||
               ledger.getHuntOpportunity() == null ||
               ledger.getReproductionProposal() == null ||
               ledger.getPrimaryAction() == null ||
               ledger.getReproductionCommit() == null ||
               ledger.getFinalPhysiology() == null ||
               !ledger.getFrameId().equals(
                   ledger.getHuntOpportunity().getFrameId()) ||
               ledger.getActorId() !=
                   ledger.getReproductionProposal().getActorId() ||
               ledger.getOrdinalOutcomes().size() !=
                   ledger.getReproductionProposal().getClaimCount()) {
                return false;
            }
        }
        return true;
    }

    private static Set<Long> livingIds(Field field)
    {
        Set<Long> result = new LinkedHashSet<>();
        for(Animal animal : field.getAnimals()) {
            if(animal.isAlive()) result.add(Long.valueOf(animal.getId()));
        }
        return result;
    }

    private static Set<Long> ledgerActorIds(List<StepFrameLedger> ledgers)
    {
        Set<Long> result = new LinkedHashSet<>();
        for(StepFrameLedger ledger : ledgers) {
            result.add(Long.valueOf(ledger.getActorId()));
        }
        return result;
    }

    private static FrameTrace runTrace(int steps, boolean observe)
    {
        Randomizer.reset();
        Simulator simulator = new Simulator(SimulationConfig.builder()
            .randomSeed(ECOLOGY_SEED).terrainSeed(TERRAIN_SEED).build(), false);
        simulator.setVerbose(false);
        FrameCollector collector = new FrameCollector();
        if(observe) {
            simulator.getContext().addStepFrameLedgerListener(collector);
        }
        List<String> stepDigests = new ArrayList<>();
        for(int index = 0; index < steps && simulator.getField().isViable();
            index++) {
            simulator.simulateOneStep();
            stepDigests.add(digest(
                SimulationFingerprint.capture(simulator) + "|plan=" +
                planDigest(simulator.getContext().getLastPositionPlan()) +
                "|events=" + eventsDigest(
                    simulator.getContext().getCurrentEvents()) +
                "|frames=" + frameDigest(simulator.getContext()
                    .getCurrentStepFrameLedgers())));
        }
        return new FrameTrace(stepDigests, simulator.getStep(),
            collector.getCount(), collector.hasDuplicate());
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

    private static String frameDigest(List<StepFrameLedger> ledgers)
    {
        StringBuilder value = new StringBuilder();
        for(StepFrameLedger ledger : ledgers) {
            value.append(ledger.getStep()).append(':')
                .append(ledger.getActorId()).append(':')
                .append(ledger.getLedgerId()).append(':')
                .append(ledger.getHuntOpportunity().getOpportunityId())
                .append(':').append(ledger.getReproductionProposal()
                    .getProposalId())
                .append(':').append(ledger.getReproductionCommit()
                    .getDisposition())
                .append(':').append(ledger.getTerminalReason()).append('|');
        }
        return digest(value.toString());
    }

    private static String digest(String value)
    {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for(byte item : digest.digest()) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        }
        catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static boolean rejects(ThrowingRunnable action)
    {
        try {
            action.run();
            return false;
        }
        catch(RuntimeException expected) {
            return true;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable
    {
        void run();
    }

    private static final class ControlledSetup
    {
        private final SimulationContext context;
        private final Field field;
        private final InteractionGeometry geometry;
        private final Cheetah parent;
        private final Cheetah mate;
        private final Gazelle prey;
        private final Location origin;

        ControlledSetup(SimulationContext context, Field field,
                        InteractionGeometry geometry, Cheetah parent,
                        Cheetah mate, Gazelle prey, Location origin)
        {
            this.context = context;
            this.field = field;
            this.geometry = geometry;
            this.parent = parent;
            this.mate = mate;
            this.prey = prey;
            this.origin = origin;
        }
    }

    private static final class FixedSexRandom extends Random
    {
        private final boolean male;

        FixedSexRandom(Sex sex)
        {
            super(31L);
            male = sex == Sex.MALE;
        }

        @Override
        public boolean nextBoolean()
        {
            return male;
        }
    }

    private static final class FixedDoubleRandom extends Random
    {
        private final double value;

        FixedDoubleRandom(double value)
        {
            super(37L);
            this.value = value;
        }

        @Override
        public double nextDouble()
        {
            return value;
        }
    }

    private static final class FixedDecisionRandom extends Random
    {
        private final double fixedDouble;
        private final int fixedInt;
        private int doubleCalls;
        private int intCalls;

        FixedDecisionRandom(double fixedDouble, int fixedInt)
        {
            super(41L);
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
            return Math.floorMod(fixedInt, bound);
        }

        int getDoubleCalls() { return doubleCalls; }
        int getIntCalls() { return intCalls; }
    }

    private static final class FrameCollector
        implements StepFrameLedgerListener
    {
        private final Set<String> identities = new HashSet<>();
        private int count;
        private boolean duplicate;

        @Override
        public void onStepFrameLedger(StepFrameLedger ledger)
        {
            count++;
            if(!identities.add(ledger.getStep() + ":" + ledger.getActorId())) {
                duplicate = true;
            }
        }

        int getCount() { return count; }
        boolean hasDuplicate() { return duplicate; }
    }

    private static final class FrameTrace
    {
        private final List<String> stepDigests;
        private final int completedSteps;
        private final int observedLedgerCount;
        private final boolean observerDuplicate;

        FrameTrace(List<String> stepDigests, int completedSteps,
                   int observedLedgerCount, boolean observerDuplicate)
        {
            this.stepDigests = stepDigests;
            this.completedSteps = completedSteps;
            this.observedLedgerCount = observedLedgerCount;
            this.observerDuplicate = observerDuplicate;
        }
    }
}
