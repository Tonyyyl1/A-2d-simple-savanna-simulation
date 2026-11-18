package savanna;

import java.util.List;

/** Plan v3.0 M1 resource-closure scenarios, run through the production pipeline. */
public final class M1ResourceTests
{
    private static final double RELATIVE_TOLERANCE = 1e-9;

    private M1ResourceTests()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("M1 resource closure");
        run();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    public static void run()
    {
        TestSupport.startGroup("M1 resource closure");
        grassCapacityFollowsTerrain();
        grassLedgerClosesAndWaterStaysBare();
        grassGrowthConsumesNoEcologyRandom();
        conceptionLeavesMaternalFloor();
        partialLitterBirthsSettleOnceEach();
        blockedBirthBecomesLossAfterWait();
        motherDeathLosesPendingOrdinals();
        birthLedgerClosesInProductionRun();
        twinConceptionWithOneFreeCellIsValid();
        provisioningIsConservedAndTargetsDependents();
        unmatedFemaleMovesTowardSensedMale();
        TestSupport.finishGroup("M1_RESOURCES");
    }

    /** A 10:00 step, so herbivores are active. */
    private static final int DAY_STEP = 34;

    /** E05: conception pays the litter reserve and leaves exactly the floor. */
    private static void conceptionLeavesMaternalFloor()
    {
        BirthScene scene = new BirthScene(1);
        Gazelle mother = scene.mother;
        Gazelle mate = scene.addMale();
        SpeciesProfile profile = mother.getProfile();
        int floor = ReproductionPolicy.maternalFloor(profile);
        int reserve = ReproductionPolicy.childReserveFood(profile);
        // Energy use at proposal removes one unit before S1 reads the clock.
        TestSupport.setField(mother, "foodLevel", Integer.valueOf(floor + reserve + 1));
        scene.context.startStep(DAY_STEP, scene.field);
        TestSupport.setField(scene.context, "breedingSystem",
            new MateFindingBreedingSystem(new AlwaysYes()));
        PositionStepCoordinator board = new PositionStepCoordinator(scene.field,
            scene.context);
        PositionAvailabilityView view = board.createAvailabilityView();
        board.propose(mother, view);
        board.propose(mate, view);
        int foodBeforeCommit = mother.getFoodLevel();
        board.resolveAndCommitCompleteStep();
        TestSupport.assertTrue("M1 E05 affordable mother conceives",
            mother.getReproductionState().isPregnant());
        TestSupport.assertEquals("M1 E05 conception debits exactly one reserve",
            Integer.valueOf(foodBeforeCommit - reserve),
            Integer.valueOf(mother.getFoodLevel()));
        TestSupport.assertTrue("M1 E05 mother keeps at least her floor",
            mother.getFoodLevel() >= floor);
        TestSupport.assertEquals("M1 E05 account records the maternal debit",
            Long.valueOf(reserve),
            Long.valueOf(scene.context.getBirthAccount().getMaternalFood()));
    }

    /** E06 + E08: two ordinals, one free cell per step, each settles once. */
    private static void partialLitterBirthsSettleOnceEach()
    {
        BirthScene scene = new BirthScene(2);
        List<Gazelle> blockers = scene.blockAllButOneNeighbor();
        scene.context.recordBirthTransaction(scene.mother.getReproductionState()
            .conceive(scene.mother, DAY_STEP -
                ReproductionPolicy.gestationSteps(scene.mother.getProfile()), 2));
        scene.context.startStep(DAY_STEP, scene.field);
        PositionStepCoordinator first = new PositionStepCoordinator(scene.field,
            scene.context);
        first.propose(scene.mother, first.createAvailabilityView());
        Field afterFirst = first.resolveAndCommit();
        BirthResourceAccount account = scene.context.getBirthAccount();
        ReproductionState pregnancy = scene.mother.getReproductionState();
        TestSupport.assertEquals("M1 E06 one free cell commits one ordinal", 1,
            account.getCommittedBirths());
        TestSupport.assertEquals("M1 E06 the other ordinal stays pending", 1,
            pregnancy.getPendingOrdinals().size());
        Gazelle child = newbornOf(afterFirst, scene);
        SpeciesProfile profile = scene.mother.getProfile();
        TestSupport.assertEquals("M1 E08 newborn food is exactly its escrow",
            Integer.valueOf(ReproductionPolicy.childReserveFood(profile)),
            Integer.valueOf(child.getFoodLevel()));
        TestSupport.assertClose("M1 E08 newborn hydration is the declared allowance",
            ReproductionPolicy.NEWBORN_HYDRATION_PERCENT, child.getHydrationPercent(),
            1e-9);
        TestSupport.assertClose("M1 E08 newborn stamina is the declared allowance",
            ReproductionPolicy.NEWBORN_STAMINA_PERCENT, child.getStaminaPercent(),
            1e-9);

        afterFirst.removeAnimal(blockers.get(0));
        scene.context.startStep(DAY_STEP + 1, afterFirst);
        PositionStepCoordinator second = new PositionStepCoordinator(afterFirst,
            scene.context);
        PositionAvailabilityView view = second.createAvailabilityView();
        second.propose(scene.mother, view);
        second.propose(child, view);
        second.resolveAndCommit();
        TestSupport.assertEquals("M1 E06 freed cell commits the second ordinal", 2,
            account.getCommittedBirths());
        TestSupport.assertFalse("M1 E06 fully born pregnancy closes",
            pregnancy.isPregnant());
        TestSupport.assertEquals("M1 E06 closing starts the postpartum cooldown",
            Integer.valueOf(DAY_STEP + 1 + ReproductionPolicy.cooldownSteps(profile)),
            Integer.valueOf(pregnancy.getCooldownUntilStep()));
        TestSupport.assertEquals("M1 E06 maternal debit equals committed reserve",
            Long.valueOf(account.getMaternalFood()),
            Long.valueOf(account.getCommittedFood()));
        TestSupport.assertTrue("M1 E08 newborn grazes on its own",
            hasEventBy(scene.context, SimulationEvent.EventType.GRAZE, child.getId()));
    }

    /** A due ordinal that never finds a cell becomes explicit loss. */
    private static void blockedBirthBecomesLossAfterWait()
    {
        BirthScene scene = new BirthScene(3);
        scene.blockAllNeighbors();
        scene.context.recordBirthTransaction(scene.mother.getReproductionState()
            .conceive(scene.mother, DAY_STEP -
                ReproductionPolicy.gestationSteps(scene.mother.getProfile()), 1));
        Field field = scene.field;
        for(int wait = 0; wait <= ReproductionPolicy.MAX_BIRTH_WAIT_STEPS; wait++) {
            scene.context.startStep(DAY_STEP + wait, field);
            PositionStepCoordinator board = new PositionStepCoordinator(field,
                scene.context);
            board.propose(scene.mother, board.createAvailabilityView());
            field = board.resolveAndCommit();
        }
        BirthResourceAccount account = scene.context.getBirthAccount();
        TestSupport.assertEquals("M1 blocked ordinal is lost after the wait", 1,
            account.getLostOrdinals());
        TestSupport.assertEquals("M1 blocked ordinal creates no child", 0,
            account.getCommittedBirths());
        TestSupport.assertFalse("M1 expired pregnancy closes",
            scene.mother.getReproductionState().isPregnant());
        TestSupport.assertEquals("M1 lost escrow is accounted",
            Long.valueOf(account.getMaternalFood()), Long.valueOf(account.getLostFood()));
    }

    /** E07: a pregnant mother's death turns every pending ordinal into loss. */
    private static void motherDeathLosesPendingOrdinals()
    {
        BirthScene scene = new BirthScene(4);
        scene.context.startStep(DAY_STEP, scene.field);
        scene.context.recordBirthTransaction(scene.mother.getReproductionState()
            .conceive(scene.mother, DAY_STEP, 2));
        scene.context.recordDeath(scene.mother, DeathCause.PREDATION);
        ReproductionState pregnancy = scene.mother.getReproductionState();
        TestSupport.assertFalse("M1 E07 dead mother's pregnancy closes",
            pregnancy.isPregnant());
        TestSupport.assertEquals("M1 E07 every pending ordinal is lost", 2,
            scene.context.getBirthAccount().getLostOrdinals());
        TestSupport.assertEquals("M1 E07 no escrow remains pending",
            Long.valueOf(0L), Long.valueOf(
                scene.context.getBirthAccount().getPendingFood()));
    }

    /** Ledger identity and per-mother escrow agree every step of a real run. */
    private static void birthLedgerClosesInProductionRun()
    {
        Simulator simulator = new Simulator(SimulationConfig.builder()
            .mapScale(1).randomSeed(1111L).build(), false);
        simulator.setVerbose(false);
        java.util.Set<Long> newborns = new java.util.HashSet<>();
        int[] newbornGrazes = {0};
        int[] secondGenerationBirths = {0};
        int[] predatorNewbornKills = {0};
        simulator.addEventListener(event -> {
            if(event.type == SimulationEvent.EventType.BIRTH) {
                if(newborns.contains(event.actorId)) secondGenerationBirths[0]++;
                newborns.add(event.targetId);
            }
            if(event.type == SimulationEvent.EventType.GRAZE &&
               newborns.contains(event.actorId)) newbornGrazes[0]++;
            if(event.type == SimulationEvent.EventType.HUNT &&
               newborns.contains(event.actorId)) predatorNewbornKills[0]++;
        });
        BirthResourceAccount account = simulator.getContext().getBirthAccount();
        boolean closes = true;
        int steps = 0;
        int newbornAdults = 0;
        for(; steps < 300 && simulator.getField().isViable(); steps++) {
            simulator.simulateOneStep();
            long pending = 0;
            for(Animal animal : simulator.getField().getAnimals()) {
                SavannahAnimal savannah = (SavannahAnimal)animal;
                ReproductionState state = savannah.getReproductionState();
                for(Integer ordinal : state.getPendingOrdinals()) {
                    pending += state.escrowOf(ordinal.intValue()).getFoodUnits();
                }
            }
            closes &= account.getPendingFood() == pending &&
                account.getMaternalFood() == account.getCommittedFood() +
                    account.getLostFood() + account.getPendingFood();
        }
        for(Animal animal : simulator.getField().getAnimals()) {
            SavannahAnimal savannah = (SavannahAnimal)animal;
            if(newborns.contains(savannah.getId()) && savannah.isAdult()) newbornAdults++;
        }
        TestSupport.assertTrue("M1 production run conceives and gives birth",
            account.getConceptions() > 0 && account.getCommittedBirths() > 0);
        TestSupport.assertTrue("M1 birth ledger closes against live escrow every step",
            closes);
        TestSupport.assertTrue("M1 E08 newborn herbivores graze on their own",
            newbornGrazes[0] > 0);
        System.out.println("ECO_INFO M1 E08 run steps=" + steps + " " + account +
            " newbornAdultsAlive=" + newbornAdults + " newbornGrazes=" + newbornGrazes[0] +
            " predatorNewbornKills=" + predatorNewbornKills[0] +
            " secondGenerationBirths=" + secondGenerationBirths[0] +
            " (informational until M3)");
    }

    /** A litter larger than the free cells is still a valid conception (no claims yet). */
    private static void twinConceptionWithOneFreeCellIsValid()
    {
        BirthScene scene = new BirthScene(12);
        scene.addMale();
        scene.context.startStep(DAY_STEP, scene.field);
        List<Location> oneCell = new java.util.ArrayList<>(
            java.util.Collections.singletonList(scene.neighbors.get(1)));
        StepFrameLedger.ReproductionProposal proposal =
            new MateFindingBreedingSystem(new TwinRoll()).proposeFrame(scene.mother,
                StepStartInteractionGeometry.capture(scene.field), scene.context,
                oneCell, StepFrameLedger.ResourceClock.capture(DAY_STEP, scene.mother,
                    StepFrameLedger.frameId(DAY_STEP, scene.mother.getId())), null);
        TestSupport.assertEquals("M1 twin conception with one free cell is a valid conception",
            StepFrameLedger.ProposalReason.CONCEPTION, proposal.getReason());
        TestSupport.assertEquals("M1 twin conception keeps the rolled litter", 2,
            proposal.getConceptionLitter());
    }

    private static final class TwinRoll extends java.util.Random
    {
        TwinRoll()
        {
            super(1L);
        }

        @Override
        public double nextDouble()
        {
            return 0.0;
        }

        @Override
        public int nextInt(int bound)
        {
            return bound - 1;
        }
    }

    /** D6: a kill shares conserved food with dependent cubs only. */
    private static void provisioningIsConservedAndTargetsDependents()
    {
        SimulationContext context = new SimulationContext(24, 24,
            SimulationConfig.builder().mapScale(1).randomSeed(5L).build());
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Field field = new Field(24, 24);
        Location origin = landWithNeighbors(context, 3);
        List<Location> near = passableAround(context, origin, 1);
        Cheetah mother = new Cheetah(false, origin, new FixedSex(Sex.FEMALE), identities);
        TestSupport.setField(mother, "age", Integer.valueOf(mother.getProfile().getBreedingAge()));
        field.placeAnimal(mother, origin);
        Cheetah cub = new Cheetah(false, near.get(0), new FixedSex(Sex.MALE), identities);
        cub.setMotherId(mother.getId());
        TestSupport.setField(cub, "foodLevel", Integer.valueOf(5));
        field.placeAnimal(cub, near.get(0));
        Cheetah stranger = new Cheetah(false, near.get(1), new FixedSex(Sex.MALE), identities);
        TestSupport.setField(stranger, "foodLevel", Integer.valueOf(5));
        field.placeAnimal(stranger, near.get(1));
        context.startStep(DAY_STEP, field);

        int motherBefore = mother.getFoodLevel();
        context.provisionDependents(mother);
        int given = mother.getProfile().getMaxFoodLevel() == motherBefore
            ? motherBefore - mother.getFoodLevel() : -1;
        TestSupport.assertTrue("M1 D6 mother shares food with her dependent cub",
            given > 0);
        TestSupport.assertEquals("M1 D6 provisioning is conserved",
            Integer.valueOf(5 + given), Integer.valueOf(cub.getFoodLevel()));
        TestSupport.assertEquals("M1 D6 non-dependent cub receives nothing",
            Integer.valueOf(5), Integer.valueOf(stranger.getFoodLevel()));
        TestSupport.assertTrue("M1 D6 mother keeps her floor",
            mother.getFoodLevel() >= ReproductionPolicy.maternalFloor(mother.getProfile()));
        TestSupport.assertEquals("M1 D6 provisioning is recorded",
            Long.valueOf(given), Long.valueOf(context.getBirthAccount().getProvisionedFood()));
    }

    /** G04: an unmated female steps toward the nearest sensed adult male. */
    private static void unmatedFemaleMovesTowardSensedMale()
    {
        SimulationContext context = new SimulationContext(40, 40,
            SimulationConfig.builder().mapScale(1).randomSeed(6L).build());
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Field field = new Field(40, 40);
        SpeciesProfile profile = SpeciesRegistry.getProfile(SpeciesRegistry.ZEBRA);
        int gap = profile.getBreedingRange() * 2;
        Location femaleAt = null;
        Location maleAt = null;
        for(int row = 1; row < 39 && femaleAt == null; row++) {
            for(int col = 1; col + gap < 39 && femaleAt == null; col++) {
                Location a = new Location(row, col);
                Location b = new Location(row, col + gap);
                if(passableAround(context, a, 1).size() == 8 &&
                   context.getTerrainMap().isPassable(b)) {
                    femaleAt = a;
                    maleAt = b;
                }
            }
        }
        Zebra female = new Zebra(false, femaleAt, new FixedSex(Sex.FEMALE), identities);
        Zebra male = new Zebra(false, maleAt, new FixedSex(Sex.MALE), identities);
        for(Zebra zebra : java.util.Arrays.asList(female, male)) {
            TestSupport.setField(zebra, "age", Integer.valueOf(profile.getBreedingAge()));
            field.placeAnimal(zebra, zebra.getLocation());
        }
        context.startStep(DAY_STEP, field);
        PositionStepCoordinator board = new PositionStepCoordinator(field, context);
        board.propose(female, board.createAvailabilityView());
        board.resolveAndCommit();
        int before = gap;
        int after = Math.max(Math.abs(female.getLocation().row() - maleAt.row()),
            Math.abs(female.getLocation().col() - maleAt.col()));
        TestSupport.assertTrue("M1 G04 unmated female steps toward the sensed male",
            after < before);
    }

    private static Location landWithNeighbors(SimulationContext context, int minimum)
    {
        for(int row = 1; row < context.getTerrainMap().getDepth() - 1; row++) {
            for(int col = 1; col < context.getTerrainMap().getWidth() - 1; col++) {
                Location cell = new Location(row, col);
                if(context.getTerrainMap().isPassable(cell) &&
                   passableAround(context, cell, 1).size() >= minimum) return cell;
            }
        }
        throw new IllegalStateException("no land cell with neighbours");
    }

    private static List<Location> passableAround(SimulationContext context,
                                                 Location cell, int radius)
    {
        List<Location> result = new java.util.ArrayList<>();
        for(int dr = -radius; dr <= radius; dr++) {
            for(int dc = -radius; dc <= radius; dc++) {
                if(dr == 0 && dc == 0) continue;
                Location next = new Location(cell.row() + dr, cell.col() + dc);
                if(context.getTerrainMap().isPassable(next)) result.add(next);
            }
        }
        return result;
    }

    private static boolean hasEventBy(SimulationContext context,
                                      SimulationEvent.EventType type, long actorId)
    {
        for(SimulationEvent event : context.getCurrentEvents()) {
            if(event.type == type && event.actorId == actorId) return true;
        }
        return false;
    }

    private static Gazelle newbornOf(Field field, BirthScene scene)
    {
        for(Animal animal : field.getAnimals()) {
            if(animal instanceof Gazelle && animal != scene.mother &&
               !scene.blockerIds.contains(animal.getId())) return (Gazelle)animal;
        }
        throw new IllegalStateException("no newborn in field: animals=" +
            field.getAnimals().size() + " account=" + scene.context.getBirthAccount() +
            " pending=" + scene.mother.getReproductionState().getPendingOrdinals() +
            " due=" + scene.mother.getReproductionState().getDueStep() +
            " step=" + scene.context.getStep() + " lastAction=" +
            scene.mother.getLastBehaviorAction() + " blockers=" + scene.blockerIds.size() + " spawn0=" +
            (scene.context.getLastPositionPlan() == null ? "noplan" :
             describe(scene.context.getLastPositionPlan().getResolution(
                PositionActorKey.spawn(scene.mother.getId(), 0)))) +
            " adult=" + (scene.context.getLastPositionPlan() == null ? "" :
             describe(scene.context.getLastPositionPlan().getResolution(
                PositionActorKey.adult(scene.mother.getId())))));
    }

    private static String describe(PositionResolution resolution)
    {
        return resolution == null ? "none" : resolution.getDisposition() + "/" +
            resolution.getReason() + " req=" + resolution.getRequested() +
            " dest=" + resolution.getDestination();
    }

    /** A grassland mother with controllable neighbours in a 24x24 map. */
    private static final class BirthScene
    {
        private final SimulationContext context;
        private final Field field = new Field(24, 24);
        private final AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        private final Gazelle mother;
        private final Location origin;
        private final List<Location> neighbors;
        private final java.util.Set<Long> blockerIds = new java.util.HashSet<>();

        BirthScene(long seed)
        {
            context = new SimulationContext(24, 24, SimulationConfig.builder()
                .mapScale(1).randomSeed(seed).build());
            GrasslandFoodSystem grass = (GrasslandFoodSystem)context.getFoodSystem();
            Location found = null;
            List<Location> around = null;
            for(int row = 1; row < 23 && found == null; row++) {
                for(int col = 1; col < 23 && found == null; col++) {
                    Location cell = new Location(row, col);
                    List<Location> passable = passableNeighbors(cell);
                    if(context.getTerrainMap().getTerrainAt(cell) == TerrainType.GRASSLAND &&
                       grass.getCapacityAt(cell) > 0.0 && passable.size() >= 4) {
                        found = cell;
                        around = passable;
                    }
                }
            }
            origin = found;
            neighbors = around;
            mother = new Gazelle(false, origin, new FixedSex(Sex.FEMALE), identities);
            adult(mother);
            field.placeAnimal(mother, origin);
        }

        Gazelle addMale()
        {
            Location cell = neighbors.get(0);
            Gazelle male = new Gazelle(false, cell, new FixedSex(Sex.MALE), identities);
            adult(male);
            field.placeAnimal(male, cell);
            return male;
        }

        List<Gazelle> blockAllButOneNeighbor()
        {
            return block(neighbors.subList(1, neighbors.size()));
        }

        List<Gazelle> blockAllNeighbors()
        {
            return block(neighbors);
        }

        private List<Gazelle> block(List<Location> cells)
        {
            List<Gazelle> blockers = new java.util.ArrayList<>();
            for(Location cell : cells) {
                Gazelle blocker = new Gazelle(false, cell, new FixedSex(Sex.MALE),
                    identities);
                adult(blocker);
                field.placeAnimal(blocker, cell);
                blockers.add(blocker);
                blockerIds.add(blocker.getId());
            }
            return blockers;
        }

        private void adult(Gazelle animal)
        {
            TestSupport.setField(animal, "age", Integer.valueOf(
                animal.getProfile().getBreedingAge()));
        }

        private List<Location> passableNeighbors(Location cell)
        {
            List<Location> result = new java.util.ArrayList<>();
            for(int dr = -1; dr <= 1; dr++) {
                for(int dc = -1; dc <= 1; dc++) {
                    if(dr == 0 && dc == 0) continue;
                    Location next = new Location(cell.row() + dr, cell.col() + dc);
                    if(context.getTerrainMap().isPassable(next)) result.add(next);
                }
            }
            return result;
        }
    }

    private static final class FixedSex extends java.util.Random
    {
        private final Sex sex;

        FixedSex(Sex sex)
        {
            super(1L);
            this.sex = sex;
        }

        @Override
        public boolean nextBoolean()
        {
            return sex == Sex.MALE;
        }
    }

    /** Breeding roll that always succeeds with a litter of one. */
    private static final class AlwaysYes extends java.util.Random
    {
        AlwaysYes()
        {
            super(1L);
        }

        @Override
        public double nextDouble()
        {
            return 0.0;
        }

        @Override
        public int nextInt(int bound)
        {
            return 0;
        }
    }

    private static void grassCapacityFollowsTerrain()
    {
        TerrainMap terrain = new TerrainMap(80, 120, 20260629L);
        GrasslandFoodSystem grass = new GrasslandFoodSystem(terrain);
        boolean matches = true;
        boolean initialWithinCapacity = true;
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location cell = new Location(row, col);
                double capacity = GrasslandFoodSystem.capacityOf(
                    terrain.getTerrainAt(cell)) * EcologyParameters.current()
                        .get(EcologyParameters.Key.GRASS_CAPACITY_SCALE);
                matches &= grass.getCapacityAt(cell) == capacity;
                initialWithinCapacity &= grass.getFoodLevelAt(cell) <= capacity;
            }
        }
        TestSupport.assertTrue("M1 grass capacity follows the scaled terrain table", matches);
        TestSupport.assertTrue("M1 initial biomass stays within capacity",
            initialWithinCapacity);
        TestSupport.assertEquals("M1 waterhole capacity is zero", 0.0,
            GrasslandFoodSystem.capacityOf(TerrainType.WATERHOLE));
    }

    private static void grassLedgerClosesAndWaterStaysBare()
    {
        Simulator simulator = new Simulator(SimulationConfig.builder()
            .mapScale(1).randomSeed(1111L).build(), false);
        simulator.setVerbose(false);
        GrasslandFoodSystem grass =
            (GrasslandFoodSystem)simulator.getContext().getFoodSystem();
        TerrainMap terrain = simulator.getContext().getTerrainMap();
        boolean closes = true;
        boolean waterBare = true;
        boolean withinCapacity = true;
        for(int step = 0; step < 300 && simulator.getField().isViable(); step++) {
            simulator.simulateOneStep();
            double left = grass.getInitialBiomass() + grass.getGrownBiomass();
            double right = grass.getTotalBiomass() + grass.getConsumedBiomass();
            closes &= Math.abs(left - right) <= RELATIVE_TOLERANCE * Math.max(1.0, left);
        }
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location cell = new Location(row, col);
                double value = grass.getFoodLevelAt(cell);
                if(!terrain.isPassable(cell)) waterBare &= value == 0.0;
                withinCapacity &= value >= 0.0 && value <= grass.getCapacityAt(cell);
            }
        }
        TestSupport.assertTrue("M1 grass ledger closes every step for 300 steps",
            closes);
        TestSupport.assertTrue("M1 herbivores consumed grass", grass.getConsumedBiomass() > 0.0);
        TestSupport.assertTrue("M1 waterhole cells never hold grass", waterBare);
        TestSupport.assertTrue("M1 biomass stays within [0, K]", withinCapacity);
    }

    private static void grassGrowthConsumesNoEcologyRandom()
    {
        SimulationConfig config = SimulationConfig.builder().mapScale(1)
            .randomSeed(1111L).build();
        SimulationContext grown = new SimulationContext(80, 120, config);
        SimulationContext untouched = new SimulationContext(80, 120, config);
        Field field = new Field(80, 120);
        for(int index = 0; index < 10; index++) {
            grown.getFoodSystem().grow(grown, field);
        }
        TestSupport.assertEquals("M1 grass growth draws no ecology random numbers",
            untouched.getRandom().nextLong(), grown.getRandom().nextLong());
    }
}
