package savanna;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Plan v3.0 M2 scenarios: finite water service, threat avoidance, egress, hotspots. */
public final class M2CapacityWaterTests
{
    /** 10:00, when gazelles and cheetahs are active. */
    private static final int DAY_STEP = 34;
    /** 06:00 (dawn), when lions are active. */
    private static final int DAWN_STEP = 30;

    private M2CapacityWaterTests()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("M2 local capacity and water");
        run();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    public static void run()
    {
        TestSupport.startGroup("M2 local capacity and water");
        drinkingIsAMultiStepService();
        abandonedPoolIsAvoidedWhenAnotherIsReachable();
        blockedQueueRetargetsAfterTheWait();
        preyFleesAnActivePredator();
        thirstyPreyFleesOnlyWhenWaterFleeIsOn();
        thirstyFemaleConceivesOnlyWhenAllowed();
        refugeAndVigilanceScaleHuntSuccess();
        sizePreferenceAndMetabolism();
        cheetahZebraDiet();
        denseGazelleChange();
        waterZonePredation();
        grassGateAndUpkeep();
        cheetahRangeBonus();
        territoryLimits();
        crowdedPredatorLeavesTheCrowd();
        overloadFactorFallsWithLocalPrey();
        ecologyParametersValidateAndOverride();
        hotspotHistorySurvivesDriftAndLedgerCloses();
        TestSupport.finishGroup("M2_CAPACITY_WATER");
    }

    /** W01: a shore slot restores 35% per step; departure starts at 95%. */
    private static void drinkingIsAMultiStepService()
    {
        SimulationContext context = thirstContext(40, 40, 7L);
        Location shore = firstShore(context);
        Gazelle gazelle = new Gazelle(false, shore, new Random(1L), new AnimalIdentityAllocator());
        TestSupport.setHydration(gazelle, 10.0);
        Field field = new Field(40, 40);
        field.placeAnimal(gazelle, shore);
        context.startStep(DAY_STEP, field);
        ThirstSystem thirst = context.getThirstSystem();
        thirst.tryDrink(gazelle, context);
        TestSupport.assertClose("M2 W01 one service step restores 35%", 45.0,
            gazelle.getHydrationPercent(), 1e-9);
        TestSupport.assertEquals("M2 W01 the session keeps its slot",
            WaterAccessState.DRINKING, gazelle.getWaterAccessState());
        thirst.tryDrink(gazelle, context);
        thirst.tryDrink(gazelle, context);
        TestSupport.assertTrue("M2 W01 three steps reach the completion level",
            gazelle.getHydrationPercent() >= EcologyParameters.defaults().get(
                EcologyParameters.Key.DRINK_COMPLETE_PERCENT));
        TestSupport.assertEquals("M2 W01 a completed session departs",
            WaterAccessState.DEPARTING, gazelle.getWaterAccessState());
    }

    /** W02: after abandoning a pool the animal targets a different reachable one. */
    private static void abandonedPoolIsAvoidedWhenAnotherIsReachable()
    {
        SimulationContext context = thirstContext(240, 360, 8L);
        SavannahThirstSystem thirst = (SavannahThirstSystem)context.getThirstSystem();
        Location land = firstShore(context);
        Gazelle gazelle = new Gazelle(false, land, new Random(1L), new AnimalIdentityAllocator());
        TestSupport.setHydration(gazelle, 30.0);
        Field field = new Field(240, 360);
        field.placeAnimal(gazelle, land);
        context.startStep(DAY_STEP, field);
        String first = thirst.selectWaterTarget(gazelle, context).getPoolId();
        gazelle.setWaterTarget(first, false);
        gazelle.abandonWaterTarget(DAY_STEP);
        String second = thirst.selectWaterTarget(gazelle, context).getPoolId();
        TestSupport.assertTrue("M2 W02 the map offers more than one pool",
            thirst.countDrinkableShores() > 0);
        TestSupport.assertTrue("M2 W02 the abandoned pool is excluded for a while",
            gazelle.isPoolExcluded(first, DAY_STEP + 1));
        TestSupport.assertFalse("M2 W02 retargeting picks a different pool",
            first.equals(second));
    }

    /** W03: queued without progress for more than the wait, the animal retargets. */
    private static void blockedQueueRetargetsAfterTheWait()
    {
        SimulationContext context = thirstContext(240, 360, 9L);
        ThirstSystem thirst = context.getThirstSystem();
        Location near = nearShoreLand(context);
        Gazelle gazelle = new Gazelle(false, near, new Random(1L), new AnimalIdentityAllocator());
        TestSupport.setHydration(gazelle, 20.0);
        Field field = new Field(240, 360);
        field.placeAnimal(gazelle, near);
        context.startStep(DAY_STEP, field);
        String original = thirst.selectWaterTarget(gazelle, context).getPoolId();
        ThirstBehaviorProvider provider = new ThirstBehaviorProvider();
        int maxWait = EcologyParameters.defaults().getInt(
            EcologyParameters.Key.WATER_MAX_WAIT_STEPS);
        for(int wait = 0; wait <= maxWait; wait++) {
            provider.propose(gazelle, new ArrayList<Location>(), context);
        }
        TestSupport.assertTrue("M2 W03 blocked waiting records WATER_BLOCKED",
            countEvents(context, SimulationEvent.EventType.WATER_BLOCKED) >=
                maxWait);
        TestSupport.assertTrue("M2 W03 the wait ends in a recorded retarget",
            countEvents(context, SimulationEvent.EventType.WATER_RETARGET) >= 1);
        TestSupport.assertTrue("M2 W03 the abandoned pool is excluded",
            gazelle.isPoolExcluded(original, DAY_STEP + 1));
    }

    /** S01: prey move away from an active predator that can eat them. */
    private static void preyFleesAnActivePredator()
    {
        SimulationContext context = new SimulationContext(40, 40,
            SimulationConfig.builder().mapScale(1).randomSeed(10L).build());
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Location prey = openLand(context, 3);
        Location hunter = new Location(prey.row(), prey.col() + 2);
        Gazelle gazelle = new Gazelle(false, prey, new Random(2L), identities);
        Cheetah cheetah = new Cheetah(false, hunter, new Random(3L), identities);
        TestSupport.setField(cheetah, "age", Integer.valueOf(cheetah.getProfile().getAdultAge()));
        Field field = new Field(40, 40);
        field.placeAnimal(gazelle, prey);
        field.placeAnimal(cheetah, hunter);
        context.startStep(DAY_STEP, field);
        PositionStepCoordinator board = new PositionStepCoordinator(field, context);
        board.propose(gazelle, board.createAvailabilityView());
        board.resolveAndCommit();
        TestSupport.assertTrue("M2 S01 prey gains distance from the predator",
            chebyshev(gazelle.getLocation(), hunter) > 2);
        TestSupport.assertTrue("M2 S01 the flight is counted",
            context.getSocialMoveCounts().getOrDefault(BehaviorReason.THREAT_AVOIDANCE, 0) > 0);
    }

    /** M3 family 2: a thirsty, non-critical prey keeps heading for water unless the water flee mode is on. */
    private static void thirstyPreyFleesOnlyWhenWaterFleeIsOn()
    {
        int[] keep = waterFleeStep(0);
        TestSupport.assertEquals("M3 water flee off: water priority does not flee", 0, keep[0]);
        int[] flee = waterFleeStep(1);
        TestSupport.assertTrue("M3 water flee on: the flight is counted", flee[0] > 0);
        TestSupport.assertTrue("M3 water flee on: the prey gains distance", flee[1] > flee[2]);
        TestSupport.assertEquals("M3 water flee: reset restores the defaults",
            EcologyParameters.defaults(), EcologyParameters.current());
    }

    /** One step with the given flee mode: {threat-avoidance moves, distance after, distance before}. */
    private static int[] waterFleeStep(int mode)
    {
        EcologyParameters.override(EcologyParameters.defaults()
            .with("avoidance.waterFleeMode=" + mode));
        try {
            SimulationContext context = thirstContext(240, 360, 9L);
            AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
            Location prey = nearShoreLand(context);
            Location hunter = null;
            for(int dr = -2; dr <= 2 && hunter == null; dr++) {
                for(int dc = -2; dc <= 2 && hunter == null; dc++) {
                    Location cell = new Location(prey.row() + dr, prey.col() + dc);
                    if(Math.max(Math.abs(dr), Math.abs(dc)) == 2 &&
                       context.getTerrainMap().isPassable(cell)) hunter = cell;
                }
            }
            Gazelle gazelle = new Gazelle(false, prey, new Random(2L), identities);
            // Thirsty but not critical: midway between the two thresholds.
            TestSupport.setHydration(gazelle, thirstyNotCritical());
            Cheetah cheetah = new Cheetah(false, hunter, new Random(3L), identities);
            TestSupport.setField(cheetah, "age", Integer.valueOf(cheetah.getProfile().getAdultAge()));
            Field field = new Field(240, 360);
            field.placeAnimal(gazelle, prey);
            field.placeAnimal(cheetah, hunter);
            context.startStep(DAY_STEP, field);
            PositionStepCoordinator board = new PositionStepCoordinator(field, context);
            board.propose(gazelle, board.createAvailabilityView());
            board.resolveAndCommit();
            return new int[] {
                context.getSocialMoveCounts().getOrDefault(BehaviorReason.THREAT_AVOIDANCE, 0),
                chebyshev(gazelle.getLocation(), hunter), chebyshev(prey, hunter)};
        }
        finally {
            EcologyParameters.override(null);
        }
    }

    /** M3 change A: a thirsty, non-critical female may conceive only when the switch is on. */
    private static void thirstyFemaleConceivesOnlyWhenAllowed()
    {
        TestSupport.assertEquals("M3 A off: water priority suppresses the conception",
            StepFrameLedger.ProposalReason.WATER_SUPPRESSED, thirstyBreedingReason(0));
        StepFrameLedger.ProposalReason allowed = thirstyBreedingReason(1);
        TestSupport.assertTrue("M3 A on: the thirsty female reaches the breeding decision",
            allowed != null && allowed != StepFrameLedger.ProposalReason.WATER_SUPPRESSED &&
            allowed != StepFrameLedger.ProposalReason.NOT_REQUESTED);
        TestSupport.assertEquals("M3 A: reset restores the defaults",
            EcologyParameters.defaults(), EcologyParameters.current());
    }

    /** Reproduction proposal reason of a thirsty (not critical), fed adult female with a male beside her. */
    private static StepFrameLedger.ProposalReason thirstyBreedingReason(int mode)
    {
        EcologyParameters.override(EcologyParameters.defaults()
            .with("repro.conceiveWhileThirsty=" + mode));
        try {
            SimulationContext context = thirstContext(240, 360, 9L);
            AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
            Location here = nearShoreLand(context);
            Location there = null;
            for(int dr = -1; dr <= 1 && there == null; dr++) {
                for(int dc = -1; dc <= 1 && there == null; dc++) {
                    Location cell = new Location(here.row() + dr, here.col() + dc);
                    if((dr != 0 || dc != 0) && context.getTerrainMap().isPassable(cell)) {
                        there = cell;
                    }
                }
            }
            Gazelle female = new Gazelle(false, here, new FixedSexRandom(Sex.FEMALE), identities);
            Gazelle male = new Gazelle(false, there, new FixedSexRandom(Sex.MALE), identities);
            for(Gazelle gazelle : new Gazelle[] {female, male}) {
                TestSupport.setField(gazelle, "age",
                    Integer.valueOf(gazelle.getProfile().getBreedingAge()));
                TestSupport.setField(gazelle, "foodLevel",
                    Integer.valueOf(gazelle.getProfile().getMaxFoodLevel()));
            }
            TestSupport.setHydration(female, thirstyNotCritical());
            Field field = new Field(240, 360);
            field.placeAnimal(female, here);
            field.placeAnimal(male, there);
            context.startStep(DAY_STEP, field);
            StepFrameLedger.ProposalReason[] seen = new StepFrameLedger.ProposalReason[1];
            context.addStepFrameLedgerListener(ledger -> {
                if(ledger.getActorId() == female.getId()) {
                    seen[0] = ledger.getReproductionProposal().getReason();
                }
            });
            PositionStepCoordinator board = new PositionStepCoordinator(field, context);
            board.propose(female, board.createAvailabilityView());
            board.resolveAndCommit();
            return seen[0];
        }
        finally {
            EcologyParameters.override(null);
        }
    }

    /** M3 change C: refuge and vigilance factors; 0 leaves the success chance unchanged. */
    private static void refugeAndVigilanceScaleHuntSuccess()
    {
        TestSupport.assertClose("M3 C refuge off keeps success", 1.0,
            FoodChainPredationSystem.refugeFactor(1, 0), 1e-12);
        TestSupport.assertClose("M3 C refuge: 2 of 10 local prey give a fifth", 0.2,
            FoodChainPredationSystem.refugeFactor(2, 10), 1e-12);
        TestSupport.assertClose("M3 C refuge: at the count there is no refuge", 1.0,
            FoodChainPredationSystem.refugeFactor(10, 10), 1e-12);
        TestSupport.assertClose("M3 C vigilance off keeps success", 1.0,
            FoodChainPredationSystem.vigilanceFactor(20, 0.0), 1e-12);
        TestSupport.assertClose("M3 C vigilance: a lone prey is not protected", 1.0,
            FoodChainPredationSystem.vigilanceFactor(1, 0.5), 1e-12);
        TestSupport.assertClose("M3 C vigilance: three together halve success", 0.5,
            FoodChainPredationSystem.vigilanceFactor(3, 0.5), 1e-12);
    }

    /** M3 changes E and F: prey size preference factor and fractional metabolism. */
    private static void sizePreferenceAndMetabolism()
    {
        TestSupport.assertClose("M3 E off keeps the score", 1.0,
            FoodChainPredationSystem.sizePreference(66, 47.0, 0.0), 1e-12);
        TestSupport.assertClose("M3 E: twice the mean food doubles the score at k = 1", 2.0,
            FoodChainPredationSystem.sizePreference(80, 40.0, 1.0), 1e-12);
        TestSupport.assertClose("M3 E: half the mean food quarters the score at k = 2", 0.25,
            FoodChainPredationSystem.sizePreference(20, 40.0, 2.0), 1e-12);

        SimulationContext context = new SimulationContext(24, 24,
            SimulationConfig.builder().mapScale(1).randomSeed(5L).build());
        Location cell = openLand(context, 1);
        Cheetah cheetah = new Cheetah(false, cell, new Random(4L), new AnimalIdentityAllocator());
        TestSupport.setField(cheetah, "foodLevel", Integer.valueOf(50));
        Field field = new Field(24, 24);
        field.placeAnimal(cheetah, cell);
        context.startStep(DAY_STEP, field);
        EcologyParameters.override(EcologyParameters.defaults().with("metabolism.cheetah=0.5"));
        try {
            TestSupport.invokePrivate(cheetah, "useEnergy",
                new Class<?>[] {SimulationContext.class}, new Object[] {context});
            TestSupport.assertEquals("M3 F: half a unit is carried over", 50, cheetah.getFoodLevel());
            TestSupport.invokePrivate(cheetah, "useEnergy",
                new Class<?>[] {SimulationContext.class}, new Object[] {context});
            TestSupport.assertEquals("M3 F: two half units use one food", 49, cheetah.getFoodLevel());
        }
        finally {
            EcologyParameters.override(null);
        }
        TestSupport.invokePrivate(cheetah, "useEnergy",
            new Class<?>[] {SimulationContext.class}, new Object[] {context});
        TestSupport.assertEquals("M3 F off: one food per step", 48, cheetah.getFoodLevel());
    }

    /** M3 change G: a Cheetah may also hunt young Zebra, and only when switched on. */
    private static void cheetahZebraDiet()
    {
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Location cell = new Location(1, 1);
        Zebra zebra = new Zebra(false, cell, new Random(1L), identities);
        Gazelle gazelle = new Gazelle(false, cell, new Random(2L), identities);
        SpeciesProfile cheetah = SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH);
        SpeciesProfile lion = SpeciesRegistry.getProfile(SpeciesRegistry.LION);
        SpeciesProfile zebraProfile = zebra.getProfile();
        TestSupport.assertTrue("M3 G off: a Cheetah does not hunt a Zebra cub",
            !HuntLocalOpportunity.canHunt(cheetah, zebra, 0));
        TestSupport.assertTrue("M3 G: Gazelle stay prey at every setting",
            HuntLocalOpportunity.canHunt(cheetah, gazelle, 0) &&
            HuntLocalOpportunity.canHunt(cheetah, gazelle, 2));
        TestSupport.assertTrue("M3 G: stage 1 and 2 take a Zebra cub",
            HuntLocalOpportunity.canHunt(cheetah, zebra, 1) &&
            HuntLocalOpportunity.canHunt(cheetah, zebra, 2));
        TestSupport.setField(zebra, "age",
            Integer.valueOf(zebraProfile.getCubAgeLimit() + 1));
        TestSupport.assertEquals("M3 G test setup: a juvenile Zebra", LifeStage.JUVENILE,
            zebra.getLifeStage());
        TestSupport.assertTrue("M3 G: stage 1 takes a juvenile Zebra, stage 2 does not",
            HuntLocalOpportunity.canHunt(cheetah, zebra, 1) &&
            !HuntLocalOpportunity.canHunt(cheetah, zebra, 2));
        TestSupport.setField(zebra, "age", Integer.valueOf(zebraProfile.getAdultAge()));
        TestSupport.assertTrue("M3 G: an adult Zebra is never Cheetah prey",
            !HuntLocalOpportunity.canHunt(cheetah, zebra, 1));
        TestSupport.assertTrue("M3 G: only Cheetah gains the new prey; Lion is unchanged",
            HuntLocalOpportunity.canHunt(lion, zebra, 0) &&
            !HuntLocalOpportunity.canHunt(zebraProfile, gazelle, 1));
        TestSupport.assertEquals("M3 G is off by default", 0,
            EcologyParameters.defaults().getInt(EcologyParameters.Key.CHEETAH_ZEBRA_STAGE));
    }

    /** M3 change H: Gazelle-specific birth limit and the predator density response. */
    private static void denseGazelleChange()
    {
        TestSupport.assertClose("M3 H2 off keeps success", 1.0,
            FoodChainPredationSystem.densityResponse(50, 10, 0.0), 1e-12);
        TestSupport.assertClose("M3 H2: at or below the refuge count nothing changes", 1.0,
            FoodChainPredationSystem.densityResponse(10, 10, 1.0), 1e-12);
        TestSupport.assertClose("M3 H2: 30 prey at refuge 10 and beta 0.5 gives 2.0", 2.0,
            FoodChainPredationSystem.densityResponse(30, 10, 0.5), 1e-12);
        TestSupport.assertClose("M3 H1: below the explicit limit births are unchanged", 1.0,
            MateFindingBreedingSystem.overloadFactor(5, 8), 1e-12);
        TestSupport.assertClose("M3 H1: twice the limit quarters births at exponent 2", 0.25,
            MateFindingBreedingSystem.overloadFactor(16, 8), 1e-12);
        TestSupport.assertEquals("M3 H1 is off by default", 0,
            EcologyParameters.defaults().getInt(EcologyParameters.Key.OVERLOAD_GAZELLE));
        TestSupport.assertClose("M3 H2 is off by default", 0.0,
            EcologyParameters.defaults().get(EcologyParameters.Key.DENSITY_RESPONSE), 1e-12);
    }

    /** M3 change W: hunt success inside the waterhole zone. */
    private static void waterZonePredation()
    {
        TestSupport.assertClose("M3 W off (radius 0) keeps success", 1.0,
            FoodChainPredationSystem.waterZoneFactor(0, 0, 0.5), 1e-12);
        TestSupport.assertClose("M3 W off (scale 1) keeps success", 1.0,
            FoodChainPredationSystem.waterZoneFactor(2, 8, 1.0), 1e-12);
        TestSupport.assertClose("M3 W: inside the zone success is scaled", 0.4,
            FoodChainPredationSystem.waterZoneFactor(8, 8, 0.4), 1e-12);
        TestSupport.assertClose("M3 W: outside the zone nothing changes", 1.0,
            FoodChainPredationSystem.waterZoneFactor(9, 8, 0.4), 1e-12);
        TerrainMap terrain = new TerrainMap(60, 90, 1L);
        int water = 0;
        int nearOk = 0;
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location cell = new Location(row, col);
                int distance = terrain.chebyshevDistanceToWater(cell);
                if(terrain.getTerrainAt(cell) == TerrainType.WATERHOLE) {
                    if(distance == 0) water++;
                    else nearOk = -1000000;
                }
                else if(distance <= 0) {
                    nearOk = -1000000;
                }
            }
        }
        TestSupport.assertTrue("M3 W: distance is 0 exactly on water cells", water > 0 &&
            nearOk >= 0);
        int beside = Integer.MAX_VALUE;
        for(int row = 0; row < terrain.getDepth() && beside != 1; row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                beside = Math.min(beside, terrain.chebyshevDistanceToWater(
                    new Location(row, col)) == 1 ? 1 : Integer.MAX_VALUE);
                if(beside == 1) break;
            }
        }
        TestSupport.assertEquals("M3 W: a cell next to water has distance 1", 1, beside);
        TestSupport.assertEquals("M3 W is off by default", 0,
            EcologyParameters.defaults().getInt(EcologyParameters.Key.WATER_ZONE_RADIUS));
        TestSupport.assertClose("M3 W success scale is 1 by default", 1.0,
            EcologyParameters.defaults().get(EcologyParameters.Key.WATER_ZONE_SUCCESS), 1e-12);
    }

    /** M3 change B: herbivore grass gate and pregnancy upkeep. */
    private static void grassGateAndUpkeep()
    {
        TestSupport.assertClose("M3 B1: at the minimum the gate is closed", 0.0,
            MateFindingBreedingSystem.grassGate(0.15, 0.15, 0.60), 1e-12);
        TestSupport.assertClose("M3 B1: below the minimum the gate is closed", 0.0,
            MateFindingBreedingSystem.grassGate(0.05, 0.15, 0.60), 1e-12);
        TestSupport.assertClose("M3 B1: halfway the gate is half open", 0.5,
            MateFindingBreedingSystem.grassGate(0.375, 0.15, 0.60), 1e-12);
        TestSupport.assertClose("M3 B1: at the maximum the gate is fully open", 1.0,
            MateFindingBreedingSystem.grassGate(0.60, 0.15, 0.60), 1e-12);
        TestSupport.assertClose("M3 B1: the gate is capped at 1.12", 1.12,
            MateFindingBreedingSystem.grassGate(5.0, 0.15, 0.60), 1e-12);
        TestSupport.assertEquals("M3 B1 is off by default", 0,
            EcologyParameters.defaults().getInt(EcologyParameters.Key.GRASS_GATE));
        TestSupport.assertTrue("M3 B': mode 0 gates every herbivore",
            MateFindingBreedingSystem.grassGateApplies(0, SpeciesRegistry.ZEBRA) &&
            MateFindingBreedingSystem.grassGateApplies(0, SpeciesRegistry.GAZELLE));
        TestSupport.assertTrue("M3 B': mode 1 gates only Gazelle",
            MateFindingBreedingSystem.grassGateApplies(1, SpeciesRegistry.GAZELLE) &&
            !MateFindingBreedingSystem.grassGateApplies(1, SpeciesRegistry.ZEBRA) &&
            !MateFindingBreedingSystem.grassGateApplies(1, SpeciesRegistry.BUFFALO));
        TestSupport.assertEquals("M3 B' is off by default", 0,
            EcologyParameters.defaults().getInt(EcologyParameters.Key.GRASS_GATE_SPECIES));
        TestSupport.assertClose("M3 B2 is off by default", 0.0,
            EcologyParameters.defaults().get(EcologyParameters.Key.GESTATION_UPKEEP), 1e-12);

        SimulationContext context = new SimulationContext(24, 24,
            SimulationConfig.builder().mapScale(1).randomSeed(5L).build());
        Location cell = openLand(context, 1);
        Gazelle gazelle = new Gazelle(false, cell, new Random(4L), new AnimalIdentityAllocator());
        TestSupport.setField(gazelle, "foodLevel", Integer.valueOf(40));
        Field field = new Field(24, 24);
        field.placeAnimal(gazelle, cell);
        context.startStep(DAY_STEP, field);
        EcologyParameters.override(EcologyParameters.defaults().with("repro.gestationUpkeep=0.5"));
        try {
            TestSupport.invokePrivate(gazelle, "useEnergy",
                new Class<?>[] {SimulationContext.class}, new Object[] {context});
            TestSupport.assertEquals("M3 B2: a mother who is not pregnant pays no upkeep", 39,
                gazelle.getFoodLevel());
        }
        finally {
            EcologyParameters.override(null);
        }
    }

    /** M3 change C1: Cheetah hunting range bonus. */
    private static void cheetahRangeBonus()
    {
        SpeciesProfile cheetah = SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH);
        SpeciesProfile lion = SpeciesRegistry.getProfile(SpeciesRegistry.LION);
        TestSupport.assertEquals("M3 C1 off: no extra range", 0,
            HuntLocalOpportunity.rangeBonus(cheetah, 0));
        TestSupport.assertEquals("M3 C1: Cheetah gets the bonus", 3,
            HuntLocalOpportunity.rangeBonus(cheetah, 3));
        TestSupport.assertEquals("M3 C1: Lion is unchanged", 0,
            HuntLocalOpportunity.rangeBonus(lion, 3));
        TestSupport.assertEquals("M3 C1 is off by default", 0,
            EcologyParameters.defaults().getInt(EcologyParameters.Key.CHEETAH_RANGE_BONUS));
    }

    /** M3 change A1: predator territory limits are per species and off by default. */
    private static void territoryLimits()
    {
        SpeciesProfile lion = SpeciesRegistry.getProfile(SpeciesRegistry.LION);
        SpeciesProfile cheetah = SpeciesRegistry.getProfile(SpeciesRegistry.CHEETAH);
        SpeciesProfile zebra = SpeciesRegistry.getProfile(SpeciesRegistry.ZEBRA);
        EcologyParameters off = EcologyParameters.defaults().with("territory.lion=0,territory.cheetah=0");
        TestSupport.assertEquals("M3 A1 off: Lion has no limit", 0,
            MateFindingBreedingSystem.territoryLimit(lion, off));
        TestSupport.assertEquals("C-2: Lion territory limit is on by default", 3,
            MateFindingBreedingSystem.territoryLimit(lion, EcologyParameters.defaults()));
        TestSupport.assertEquals("M3 A1 off: Cheetah has no limit", 0,
            MateFindingBreedingSystem.territoryLimit(cheetah, off));
        TestSupport.assertEquals("C-3: Cheetah territory limit is on by default", 2,
            MateFindingBreedingSystem.territoryLimit(cheetah, EcologyParameters.defaults()));
        EcologyParameters on = off.with("territory.lion=3,territory.cheetah=5");
        TestSupport.assertEquals("M3 A1: Lion limit", 3,
            MateFindingBreedingSystem.territoryLimit(lion, on));
        TestSupport.assertEquals("M3 A1: Cheetah limit", 5,
            MateFindingBreedingSystem.territoryLimit(cheetah, on));
        TestSupport.assertEquals("M3 A1: prey never get a territory limit", 0,
            MateFindingBreedingSystem.territoryLimit(zebra, on));
        TestSupport.assertEquals("M3 A1: only Lion set leaves Cheetah alone", 0,
            MateFindingBreedingSystem.territoryLimit(cheetah,
                off.with("territory.lion=3")));
        TestSupport.assertEquals("M3 A1: radius default", 10,
            off.getInt(EcologyParameters.Key.TERRITORY_RADIUS));
    }

    /** A hydration between the configured critical and thirsty thresholds. */
    private static double thirstyNotCritical()
    {
        EcologyParameters parameters = EcologyParameters.current();
        return (parameters.get(EcologyParameters.Key.HYDRATION_CRITICAL_THRESHOLD) +
            parameters.get(EcologyParameters.Key.HYDRATION_THIRSTY_THRESHOLD)) / 2.0;
    }

    /** Random whose nextBoolean fixes the newborn sex (true = male). */
    private static final class FixedSexRandom extends Random
    {
        private final boolean male;

        FixedSexRandom(Sex sex)
        {
            super(1L);
            male = sex == Sex.MALE;
        }

        @Override
        public boolean nextBoolean()
        {
            return male;
        }
    }

    /** S02: a fed predator among more than three others moves away from them. */
    private static void crowdedPredatorLeavesTheCrowd()
    {
        SimulationContext context = new SimulationContext(40, 40,
            SimulationConfig.builder().mapScale(1).randomSeed(11L).build());
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Location centre = openLand(context, 4);
        Lion focal = new Lion(false, centre, new Random(4L), identities);
        TestSupport.setField(focal, "age", Integer.valueOf(focal.getProfile().getAdultAge()));
        Field field = new Field(40, 40);
        field.placeAnimal(focal, centre);
        List<Location> crowd = new ArrayList<>();
        for(int offset = 2; offset <= 3; offset++) {
            crowd.add(new Location(centre.row() + offset, centre.col()));
            crowd.add(new Location(centre.row() + offset, centre.col() + 1));
        }
        long rows = 0;
        for(Location cell : crowd) {
            Lion other = new Lion(false, cell, new Random(5L), identities);
            TestSupport.setField(other, "age", Integer.valueOf(other.getProfile().getAdultAge()));
            field.placeAnimal(other, cell);
            rows += cell.row();
        }
        int crowdRow = (int)(rows / crowd.size());
        context.startStep(DAWN_STEP, field);
        PositionStepCoordinator board = new PositionStepCoordinator(field, context);
        board.propose(focal, board.createAvailabilityView());
        board.resolveAndCommit();
        TestSupport.assertTrue("M2 S02 the crowded predator moves away from the crowd",
            Math.abs(focal.getLocation().row() - crowdRow) > Math.abs(centre.row() - crowdRow));
        TestSupport.assertTrue("M2 S02 the egress is counted",
            context.getSocialMoveCounts().getOrDefault(BehaviorReason.EGRESS, 0) > 0);
    }

    private static void overloadFactorFallsWithLocalPrey()
    {
        // The limit is calibrated in M3 (capacity.preyOverload); the shape is
        // max(5%, (limit / prey)^2) with the default exponent 2.
        int limit = EcologyParameters.current().getInt(
            EcologyParameters.Key.PREY_OVERLOAD_COUNT);
        TestSupport.assertClose("M2 overload: up to the limit keeps births", 1.0,
            MateFindingBreedingSystem.overloadFactor(limit), 1e-12);
        TestSupport.assertClose("M2 overload: twice the limit quarters the births", 0.25,
            MateFindingBreedingSystem.overloadFactor(2 * limit), 1e-12);
        TestSupport.assertClose("M2 overload: never below 5%", 0.05,
            MateFindingBreedingSystem.overloadFactor(10_000), 1e-12);
    }

    /** M3-0: one parameter set; overrides are validated and reach every rule that shares a key. */
    private static void ecologyParametersValidateAndOverride()
    {
        EcologyParameters defaults = EcologyParameters.defaults();
        TestSupport.assertEquals("M3-0 defaults have no overrides", "default",
            defaults.overrides());
        TestSupport.assertEquals("M3-0 the 'default' spec keeps the hash", defaults.hash(),
            defaults.with("default").hash());
        EcologyParameters wider = defaults.with(
            "repro.predatorAdultMargin=off, capacity.preyOverload=60");
        TestSupport.assertEquals("M3-0 overrides print in canonical order",
            "capacity.preyOverload=60,repro.predatorAdultMargin=off", wider.overrides());
        TestSupport.assertTrue("M3-0 a different set has a different hash",
            !defaults.hash().equals(wider.hash()));
        TestSupport.assertEquals("M3-0 a round trip through the canonical string",
            wider, defaults.with(wider.canonical()));
        String[] rejected = {"capacity.unknown=1", "capacity.preyOverload=0",
            "capacity.preyOverload=30.5", "predation.successCap=0.5,predation.successCap=0.6",
            "predation.successCap"};
        for(String spec : rejected) {
            boolean threw = false;
            try {
                defaults.with(spec);
            }
            catch(IllegalArgumentException expected) {
                threw = true;
            }
            TestSupport.assertTrue("M3-0 rejects " + spec, threw);
        }
        SpeciesProfile lion = SpeciesRegistry.getProfile(SpeciesRegistry.LION);
        int defaultReserve = ReproductionPolicy.childReserveFood(lion);
        EcologyParameters.override(wider);
        try {
            TestSupport.assertClose("M3-0 the merged prey overload limit moves births", 1.0,
                MateFindingBreedingSystem.overloadFactor(60), 1e-12);
            TestSupport.assertEquals("M3-0 margin off leaves the one-unit reserve", 1,
                ReproductionPolicy.childReserveFood(lion));
        }
        finally {
            EcologyParameters.override(null);
        }
        TestSupport.assertEquals("M3-0 reset restores the defaults", defaults,
            EcologyParameters.current());
        TestSupport.assertEquals("M3-0 reset restores the reserve", defaultReserve,
            ReproductionPolicy.childReserveFood(lion));
    }

    /** S03 + ledger: a drifting hotspot keeps its span; the regional ledger closes. */
    private static void hotspotHistorySurvivesDriftAndLedgerCloses()
    {
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        List<Gazelle> herd = new ArrayList<>();
        Field field = new Field(48, 48);
        for(int index = 0; index < 40; index++) {
            Location cell = new Location(index / 8, index % 8);
            Gazelle gazelle = new Gazelle(false, cell, new Random(index), identities);
            herd.add(gazelle);
            field.placeAnimal(gazelle, cell);
        }
        RegionalHotspotMonitor monitor = new RegionalHotspotMonitor(48, 48);
        monitor.observe(field);
        for(int step = 1; step <= 3; step++) {
            Field next = new Field(48, 48);
            for(int index = 0; index < herd.size(); index++) {
                Gazelle gazelle = herd.get(index);
                if(step == 2 && index == 0) {
                    gazelle.setDead(DeathCause.AGE);
                    continue;
                }
                Location cell = new Location(index / 8, index % 8 + step * 4);
                TestSupport.setField(gazelle, "location", cell);
                next.placeAnimal(gazelle, cell);
            }
            if(step == 3) {
                Gazelle calf = new Gazelle(false, new Location(10, 30), new Random(99L), identities);
                next.placeAnimal(calf, calf.getLocation());
            }
            monitor.observe(next);
        }
        TestSupport.assertEquals("M2 S03 a drifting hotspot keeps its history", 4,
            monitor.getMaxOverloadSpan());
        TestSupport.assertTrue("M2 S03 the drift is counted as migration",
            monitor.getTotalImmigration() > 0);
        TestSupport.assertTrue("M2 regional ledger closes with birth, death and migration",
            monitor.ledgerCloses());
    }

    private static SimulationContext thirstContext(int depth, int width, long seed)
    {
        return new SimulationContext(depth, width, SimulationConfig.builder()
            .mapScale(depth >= 240 ? 3 : 1).thirstEnabled(true).randomSeed(seed).build());
    }

    private static Location firstShore(SimulationContext context)
    {
        TerrainMap terrain = context.getTerrainMap();
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location cell = new Location(row, col);
                if(context.getThirstSystem().isDrinkableShore(cell)) return cell;
            }
        }
        throw new IllegalStateException("no shore");
    }

    private static Location nearShoreLand(SimulationContext context)
    {
        TerrainMap terrain = context.getTerrainMap();
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location cell = new Location(row, col);
                int distance = context.getThirstSystem().distanceToShore(cell);
                if(terrain.isPassable(cell) && distance == 2) return cell;
            }
        }
        throw new IllegalStateException("no near-shore land");
    }

    private static Location openLand(SimulationContext context, int radius)
    {
        TerrainMap terrain = context.getTerrainMap();
        for(int row = radius + 1; row < terrain.getDepth() - radius - 1; row++) {
            for(int col = radius + 1; col < terrain.getWidth() - radius - 1; col++) {
                boolean open = true;
                for(int dr = -radius; dr <= radius && open; dr++) {
                    for(int dc = -radius; dc <= radius && open; dc++) {
                        open = terrain.isPassable(new Location(row + dr, col + dc));
                    }
                }
                if(open) return new Location(row, col);
            }
        }
        throw new IllegalStateException("no open land");
    }

    private static int countEvents(SimulationContext context, SimulationEvent.EventType type)
    {
        int count = 0;
        for(SimulationEvent event : context.getCurrentEvents()) {
            if(event.type == type) count++;
        }
        return count;
    }

    private static int chebyshev(Location first, Location second)
    {
        return Math.max(Math.abs(first.row() - second.row()),
                        Math.abs(first.col() - second.col()));
    }
}
