package savanna;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;

/**
 * Shared implementation for all savanna animals.
 */
public abstract class SavannahAnimal extends Animal
{
    public static final double MAX_STAMINA = 100.0;
    public static final double MAX_HYDRATION = 100.0;
    public static final double STAMINA_MOVE_COST = 0.35;
    public static final double STAMINA_HUNT_COST = 1.20;
    public static final double STAMINA_GRAZE_COST = 0.35;
    public static final double STAMINA_BREED_COST = 1.20;
    private static final double THIRST_THRESHOLD = 35.0;
    private static final int STARVATION_LIMIT = 3;
    public static final int DEHYDRATION_CAUSE_STEPS =
        HydrationPolicy.DEFAULT_DEHYDRATION_GRACE_STEPS;
    private static final int HOURS_PER_DAY = 24;

    private final SpeciesProfile profile;
    private final Random rand;
    private final Sex sex;
    private int age;
    private int foodLevel;
    private int starvingSteps;
    private double stamina;
    private final AnimalHydrationState hydrationState;
    private int dailyRestHours;
    private final AnimalDiseaseState diseaseState = new AnimalDiseaseState();
    private ActionType lastBehaviorAction;
    private BehaviorReason lastBehaviorReason;
    private BehaviorOutcome lastBehaviorOutcome;
    private String waterTargetPoolId = WaterSourceIndex.NONE;
    private boolean waterArrivalDeficit;
    private WaterAccessState waterAccessState = WaterAccessState.NORMAL;
    private String lastDrunkPoolId = WaterSourceIndex.NONE;
    private int waterCooldownSteps;
    private final ProductionTargetState productionTarget =
        new ProductionTargetState();

    public SavannahAnimal(SpeciesProfile profile, boolean randomAge, Location location)
    {
        this(profile, randomAge, location, Randomizer.getRandom());
    }

    public SavannahAnimal(SpeciesProfile profile, boolean randomAge, Location location,
                          Random rand)
    {
        this(profile, randomAge, location, rand,
            new AnimalIdentityAllocator());
    }

    public SavannahAnimal(SpeciesProfile profile, boolean randomAge,
                          Location location, Random rand,
                          AnimalIdentityAllocator identityAllocator)
    {
        super(location, identityAllocator);
        this.profile = profile;
        this.rand = rand;
        sex = Sex.random(rand);
        if(randomAge) {
            age = rand.nextInt(profile.getMaxAge());
            foodLevel = 1 + rand.nextInt(Math.max(1, profile.getMaxFoodLevel()));
            stamina = 55.0 + rand.nextDouble() * 45.0;
            hydrationState = new AnimalHydrationState(85.0, MAX_HYDRATION);
            if(rand.nextDouble() < 0.018) {
                diseaseState.infect(0.18 + rand.nextDouble() * 0.24);
            }
        }
        else {
            age = 0;
            foodLevel = profile.getMaxFoodLevel();
            stamina = MAX_STAMINA;
            hydrationState =
                new AnimalHydrationState(MAX_HYDRATION, MAX_HYDRATION);
        }
        starvingSteps = foodLevel <= 0 ? 1 : 0;
        dailyRestHours = 0;
    }

    public abstract SavannahAnimal createChild(Location location);

    protected Random getRandom()
    {
        return rand;
    }

    /**
     * R2 production proposal phase. It keeps physiology and established action
     * decisions sequential, but never places this adult or constructs a
     * child. All spatial reads come from the step's immutable availability
     * view; the returned claim is resolved once after every actor has acted.
     */
    PositionProposal proposePositionStep(Field currentField,
                                         InteractionGeometry interactionGeometry,
                                         PositionAvailabilityView availability,
                                         SimulationContext context,
                                         Location stepStartOrigin)
    {
        Location origin = getLocation() == null ? stepStartOrigin : getLocation();
        StepFrameLedger.FrameDraft frame = StepFrameLedger.FrameDraft.begin(
            context.getStep(), this);
        if(!isAlive()) {
            return releasedFrameProposal(origin, interactionGeometry, context,
                frame);
        }

        recoverStaminaAtStartOfDay(context);
        incrementAge(context);
        if(!isAlive()) {
            return releasedFrameProposal(origin, interactionGeometry, context,
                frame);
        }
        context.getDiseaseSystem().progressDisease(this, context, currentField);
        if(!isAlive()) {
            return releasedFrameProposal(origin, interactionGeometry, context,
                frame);
        }
        useEnergy(context);
        if(context.isThirstEnabled()) {
            context.getThirstSystem().applyThirst(this, context);
            if(!isAlive()) {
                return releasedFrameProposal(origin, interactionGeometry,
                    context, frame);
            }
        }
        resolveStarvation(context);
        if(!isAlive()) {
            return releasedFrameProposal(origin, interactionGeometry, context,
                frame);
        }

        List<Location> candidates = availability.adjacentCandidates(getId(),
            origin, context.getRandom());
        sortByHabitatPreference(candidates, context);
        frame.captureS0(this, origin, interactionGeometry, context);
        context.recordPredatorRecruitmentS0(this, frame.getResourceClock(),
            candidates);
        if(waterAccessState == WaterAccessState.DEPARTING) {
            return proposeWaterDeparture(origin, candidates, context, frame);
        }
        applyWaterCooldown(candidates, context);

        BehaviorIntent waterIntent = context.getThirstBehaviorProvider().propose(
            this, candidates, context);
        BehaviorIntent foodIntent = context.getFoodBehaviorProvider().propose(
            this, candidates, context);
        BehaviorIntent breedingIntent = context.getBreedingBehaviorProvider().propose(
            this, candidates, context);
        BehaviorIntent primaryIntent = context.getBehaviorResolver().resolve(
            java.util.Arrays.asList(waterIntent, foodIntent, breedingIntent));
        List<ExecutedEffect> effects = new ArrayList<>();
        boolean waterPriority = primaryIntent != null &&
            (primaryIntent.getActionType() == ActionType.DRINK ||
             primaryIntent.getActionType() == ActionType.SEEK_WATER);
        HuntOpportunityFact huntOpportunity = frame.getHuntOpportunity();
        if(context.hasHuntObservationListeners() && profile.isPredator()) {
            BehaviorReason foodReason = foodIntent == null ? null :
                foodIntent.getReason();
            boolean wouldSeek = !waterPriority && primaryIntent != null &&
                primaryIntent.getReason() == BehaviorReason.CRITICAL_FOOD &&
                !huntOpportunity.hasLocalReachableEdible();
            context.recordHuntDecisionObservation(new HuntDecisionObservation(
                context.getStep(), this, isActive(context), foodReason,
                primaryIntent, waterPriority, huntOpportunity, wouldSeek));
        }

        StepFrameLedger.ReproductionProposal reproductionProposal;
        if(!waterPriority && breedingIntent != null) {
            reproductionProposal = context.getActionExecutor()
                .proposeBreedingFrameAction(breedingIntent, this,
                    interactionGeometry, candidates, context,
                    frame.getResourceClock());
        }
        else {
            reproductionProposal = StepFrameLedger.ReproductionProposal.none(
                frame.getFrameId(), context.getStep(), this,
                frame.getResourceClock(), waterPriority
                    ? StepFrameLedger.ProposalReason.WATER_SUPPRESSED
                    : StepFrameLedger.ProposalReason.NOT_REQUESTED);
        }
        frame.recordS1(reproductionProposal);
        List<PositionClaim> spawnClaims =
            reproductionProposal.getPositionClaims();

        boolean drinkingNow = context.getActionExecutor().executeImmediate(
            primaryIntent, this, context);
        if(drinkingNow) {
            effects.add(ExecutedEffect.DRINK);
        }

        Location thirstDestination = waterPriority &&
            primaryIntent.getActionType() == ActionType.SEEK_WATER
                ? primaryIntent.getDestination() : null;
        Location reservedWaterDestination = context.getCriticalWaterReservation(
            getId());
        if(waterPriority && reservedWaterDestination != null) {
            thirstDestination = reservedWaterDestination;
        }

        StepFrameLedger.PrimaryAction framePrimary =
            StepFrameLedger.PrimaryAction.nonFood(frame.getFrameId(),
                context.getStep(), this,
                primaryIntent == null ? null : primaryIntent.getActionType(),
                drinkingNow, thirstDestination);
        Location nextLocation = null;
        if(thirstDestination != null) {
            nextLocation = thirstDestination;
        }
        else if(!waterPriority && isActive(context) && !drinkingNow) {
            List<Location> grazingLocations = profile.isPredator() ? null :
                getGrazingLocations(availability, candidates, context);
            int eventCount = context.getCurrentEvents().size();
            framePrimary = context.getActionExecutor().executePrimaryFoodAction(
                primaryIntent, this, interactionGeometry, grazingLocations,
                context, huntOpportunity);
            nextLocation = framePrimary.getDestination();
            recordFoodEffect(primaryIntent, context, eventCount, effects);
        }
        else if(!waterPriority && !profile.isPredator()) {
            int eventCount = context.getCurrentEvents().size();
            framePrimary = context.getActionExecutor().executePrimaryFoodAction(
                foodIntent, this, interactionGeometry, null, context,
                huntOpportunity);
            recordFoodEffect(foodIntent, context, eventCount, effects);
        }

        BehaviorIntent movementIntent = null;
        if(!waterPriority && nextLocation == null) {
            boolean active = isActive(context);
            boolean canMove = false;
            boolean illnessRecovery = false;
            if(active && !candidates.isEmpty() && !drinkingNow) {
                canMove = shouldMove();
                illnessRecovery = !canMove && diseaseState.isInfected();
            }
            boolean hasNonRestEffect = !effects.isEmpty();
            boolean intentionalRest = !hasNonRestEffect &&
                (!active || illnessRecovery);
            if(!hasNonRestEffect || canMove) {
                movementIntent = context.getMovementBehaviorProvider().propose(
                    this, candidates, canMove, intentionalRest,
                    illnessRecovery ? BehaviorReason.ILLNESS_RECOVERY
                                    : BehaviorReason.REST);
                nextLocation = context.getActionExecutor()
                    .resolveMovementDestination(movementIntent, this);
                if(movementIntent != null &&
                   movementIntent.getActionType() == ActionType.MOVE) {
                    candidates.remove(nextLocation);
                }
            }
        }

        if(waterPriority && !drinkingNow && isSurvivalCritical() && isAlive() &&
           context.getConfig().getWaterFoodSecondaryPolicy()
               .allowsCriticalFoodSecondary()) {
            int eventCount = context.getCurrentEvents().size();
            context.getActionExecutor().executePrimaryFoodAction(foodIntent,
                this, interactionGeometry, null, context, huntOpportunity);
            recordFoodEffect(foodIntent, context, eventCount, effects);
        }

        frame.recordS2(framePrimary);
        frame.recordS3(StepFrameLedger.ReproductionCommit.submit(
            reproductionProposal, isAlive()));

        PositionClaim adultClaim = buildPositionClaim(origin, nextLocation,
            candidates, context, waterPriority, primaryIntent);
        return new PositionProposal(this, origin, adultClaim, spawnClaims,
            primaryIntent, movementIntent, foodIntent, effects, waterPriority,
            drinkingNow, false, false, frame, reproductionProposal);
    }

    private PositionProposal releasedFrameProposal(
        Location origin, InteractionGeometry interactionGeometry,
        SimulationContext context, StepFrameLedger.FrameDraft frame)
    {
        frame.captureS0(this, origin, interactionGeometry, context);
        context.recordPredatorRecruitmentS0(this, frame.getResourceClock(),
            java.util.Collections.<Location>emptyList());
        StepFrameLedger.ReproductionProposal reproduction =
            StepFrameLedger.ReproductionProposal.none(frame.getFrameId(),
                context.getStep(), this, frame.getResourceClock(),
                StepFrameLedger.ProposalReason.ACTOR_TERMINAL);
        frame.recordS1(reproduction);
        frame.recordS2(StepFrameLedger.PrimaryAction.skipped(
            frame.getFrameId(), context.getStep(), this, null));
        frame.recordS3(StepFrameLedger.ReproductionCommit.submit(reproduction,
            false));
        return PositionProposal.released(this, origin, frame, reproduction);
    }

    /**
     * Legacy direct-placement behavior retained only for compatibility tests.
     * {@link Simulator} uses {@link #proposePositionStep(Field,
     * PositionAvailabilityView, SimulationContext)} instead.
     */
    public void act(Field currentField, Field nextFieldState, SimulationContext context)
    {
        if(!isAlive()) {
            return;
        }

        recoverStaminaAtStartOfDay(context);
        incrementAge(context);
        if(!isAlive()) {
            return;
        }
        context.getDiseaseSystem().progressDisease(this, context, currentField);
        if(!isAlive()) {
            return;
        }
        useEnergy(context);
        if(context.isThirstEnabled()) {
            context.getThirstSystem().applyThirst(this, context);
            if(!isAlive()) {
                return;
            }
        }
        resolveStarvation(context);
        if(!isAlive()) {
            return;
        }

        if(isAlive()) {
            List<Location> freeLocations = TerrainNavigator.freeAdjacent(
                nextFieldState, context.getTerrainMap(), getLocation(),
                context.getRandom());
            freeLocations.removeIf(location -> context
                .isCriticalWaterReservedForOther(location, getId()));
            sortByHabitatPreference(freeLocations, context);
            if(waterAccessState == WaterAccessState.DEPARTING) {
                executeWaterDeparture(nextFieldState, freeLocations, context);
                return;
            }
            applyWaterCooldown(freeLocations, context);
            BehaviorIntent waterIntent = context.getThirstBehaviorProvider()
                .propose(this, freeLocations, context);
            BehaviorIntent foodIntent = context.getFoodBehaviorProvider()
                .propose(this, freeLocations, context);
            BehaviorIntent breedingIntent = context.getBreedingBehaviorProvider()
                .propose(this, freeLocations, context);
            BehaviorIntent primaryIntent = context.getBehaviorResolver().resolve(
                java.util.Arrays.asList(waterIntent, foodIntent, breedingIntent));
            List<ExecutedEffect> effects = new ArrayList<>();
            boolean waterPriority = primaryIntent != null &&
                (primaryIntent.getActionType() == ActionType.DRINK ||
                 primaryIntent.getActionType() == ActionType.SEEK_WATER);
            boolean drinkingNow = context.getActionExecutor().executeImmediate(
                primaryIntent, this, context);
            if(drinkingNow) {
                effects.add(ExecutedEffect.DRINK);
            }
            Location thirstDestination = waterPriority &&
                primaryIntent.getActionType() == ActionType.SEEK_WATER
                    ? primaryIntent.getDestination() : null;
            Location reservedWaterDestination = context.getCriticalWaterReservation(getId());
            if(waterPriority && reservedWaterDestination != null) {
                thirstDestination = reservedWaterDestination;
            }
            if(!waterPriority && breedingIntent != null) {
                int eventCount = context.getCurrentEvents().size();
                context.getActionExecutor().executeBreedingAction(breedingIntent,
                    this, currentField, nextFieldState, freeLocations, context);
                if(hasEffectSince(context, eventCount,
                                  SimulationEvent.EventType.BIRTH)) {
                    effects.add(ExecutedEffect.BREED);
                }
            }

            Location nextLocation = null;
            if(thirstDestination != null) {
                nextLocation = thirstDestination;
            }
            else if(!waterPriority && isActive(context) && !drinkingNow) {
                List<Location> grazingLocations = profile.isPredator() ? null :
                    getGrazingLocations(nextFieldState, freeLocations, context);
                int eventCount = context.getCurrentEvents().size();
                nextLocation = context.getActionExecutor().executeFoodAction(
                    primaryIntent, this, currentField, grazingLocations, context);
                recordFoodEffect(primaryIntent, context, eventCount, effects);
            }
            else if(!waterPriority && !profile.isPredator()) {
                int eventCount = context.getCurrentEvents().size();
                context.getActionExecutor().executeFoodAction(foodIntent, this,
                    currentField, null, context);
                recordFoodEffect(foodIntent, context, eventCount, effects);
            }

            BehaviorIntent movementIntent = null;
            if(!waterPriority && nextLocation == null) {
                boolean active = isActive(context);
                boolean canMove = false;
                boolean illnessRecovery = false;
                if(active && !freeLocations.isEmpty() && !drinkingNow) {
                    canMove = shouldMove();
                    illnessRecovery = !canMove && diseaseState.isInfected();
                }
                boolean hasNonRestEffect = !effects.isEmpty();
                boolean intentionalRest = !hasNonRestEffect &&
                    (!active || illnessRecovery);
                if(!hasNonRestEffect || canMove) {
                    movementIntent = context.getMovementBehaviorProvider().propose(
                        this, freeLocations, canMove, intentionalRest,
                        illnessRecovery ? BehaviorReason.ILLNESS_RECOVERY
                                        : BehaviorReason.REST);
                    nextLocation = context.getActionExecutor()
                        .resolveMovementDestination(movementIntent, this);
                    if(movementIntent != null &&
                       movementIntent.getActionType() == ActionType.MOVE) {
                        freeLocations.remove(nextLocation);
                    }
                }
            }

            FieldTransitionResult transition = placeInNextField(nextFieldState,
                nextLocation, freeLocations, context, waterPriority);
            MovementDisposition disposition = transition == null
                ? MovementDisposition.NOT_ATTEMPTED
                : MovementDisposition.from(transition.getStatus());
            if(disposition.moved()) {
                effects.add(ExecutedEffect.MOVE);
            }
            boolean restCredit = movementIntent != null &&
                movementIntent.getActionType() == ActionType.REST &&
                disposition == MovementDisposition.STAYED && effects.isEmpty();
            if(restCredit) {
                effects.add(ExecutedEffect.REST);
                recordRestHour();
            }
            else if((movementIntent != null &&
                     movementIntent.getActionType() == ActionType.WAIT &&
                     disposition == MovementDisposition.STAYED) ||
                    (waterPriority && !drinkingNow &&
                     disposition == MovementDisposition.STAYED)) {
                effects.add(ExecutedEffect.WAIT);
            }
            if(waterPriority && !drinkingNow && isSurvivalCritical() && isAlive() &&
               context.getConfig().getWaterFoodSecondaryPolicy()
                   .allowsCriticalFoodSecondary()) {
                // Water remains the primary movement decision. A food-critical
                // animal may recover food as a secondary action so the new
                // hydration priority does not turn the legacy food model into
                // an artificial starvation trap during a long water trek.
                int eventCount = context.getCurrentEvents().size();
                context.getActionExecutor().executeFoodAction(foodIntent, this,
                    currentField, null, context);
                recordFoodEffect(foodIntent, context, eventCount, effects);
            }
            if(waterPriority &&
               primaryIntent.getActionType() == ActionType.SEEK_WATER) {
                boolean drankAfterMoving = context.getActionExecutor().executeImmediate(
                    new BehaviorIntent(
                    getId(), ActionType.DRINK, primaryIntent.getReason(),
                    getLocation()), this, context);
                if(drankAfterMoving) {
                    effects.add(ExecutedEffect.DRINK);
                }
            }
            rememberOutcome(new BehaviorOutcome(primaryIntent, effects, disposition,
                displayAction(primaryIntent, effects, disposition), restCredit));
        }
    }

    public SpeciesProfile getProfile()
    {
        return profile;
    }

    public Sex getSex()
    {
        return sex;
    }

    public ActionType getLastBehaviorAction()
    {
        return lastBehaviorAction;
    }

    public BehaviorReason getLastBehaviorReason()
    {
        return lastBehaviorReason;
    }

    public BehaviorOutcome getLastBehaviorOutcome()
    {
        return lastBehaviorOutcome;
    }

    public String getWaterTargetPoolId()
    {
        return waterTargetPoolId;
    }

    public boolean hasWaterArrivalDeficit()
    {
        return waterArrivalDeficit;
    }

    public void setWaterTarget(String poolId, boolean arrivalDeficit)
    {
        waterTargetPoolId = poolId == null ? WaterSourceIndex.NONE : poolId;
        waterArrivalDeficit = arrivalDeficit;
    }

    public void clearWaterTarget()
    {
        waterTargetPoolId = WaterSourceIndex.NONE;
        waterArrivalDeficit = false;
    }

    public WaterAccessState getWaterAccessState()
    {
        return waterAccessState;
    }

    public String getLastDrunkPoolId()
    {
        return lastDrunkPoolId;
    }

    public int getWaterCooldownSteps()
    {
        return waterCooldownSteps;
    }

    /** R2 production target state; observers may read it but never mutate it. */
    public ProductionTargetState getProductionTarget()
    {
        return productionTarget;
    }

    void ensureProductionTarget(ProductionTargetState.Type type,
                                Location region, int step, int timeoutSteps)
    {
        if(productionTarget.isActive() && step - productionTarget.getLastProgressStep()
           >= timeoutSteps) {
            productionTarget.invalidate(ProductionTargetState.Status.TIMED_OUT);
        }
        if(!productionTarget.isActive()) productionTarget.begin(type, region, step);
    }

    void acquireHuntTarget(SavannahAnimal prey, Location region, int step)
    {
        if(prey != null) productionTarget.acquirePrey(prey.getId(), region, step);
    }

    void recordProductionTargetProgress(Location region, int step)
    {
        productionTarget.progress(region, step);
    }

    void invalidateProductionTarget(ProductionTargetState.Status reason)
    {
        if(productionTarget.isActive()) productionTarget.invalidate(reason);
    }

    public void beginWaterDeparture(String poolId)
    {
        lastDrunkPoolId = poolId == null ? WaterSourceIndex.NONE : poolId;
        waterAccessState = WaterAccessState.DEPARTING;
        waterCooldownSteps = 0;
    }

    private void rememberOutcome(BehaviorOutcome outcome)
    {
        lastBehaviorOutcome = outcome;
        BehaviorIntent primary = outcome == null ? null : outcome.getPrimaryIntent();
        lastBehaviorAction = outcome == null ? null : outcome.getDisplayAction();
        lastBehaviorReason = primary == null ? null : primary.getReason();
    }

    /** One animal's deferred R2 position and behavior completion facts. */
    static final class PositionProposal
    {
        private final SavannahAnimal animal;
        private final Location origin;
        private final PositionClaim adultClaim;
        private final List<PositionClaim> spawnClaims;
        private final BehaviorIntent primaryIntent;
        private final BehaviorIntent movementIntent;
        private final BehaviorIntent foodIntent;
        private final List<ExecutedEffect> immediateEffects;
        private final boolean waterPriority;
        private final boolean drinkingNow;
        private final boolean waterDeparture;
        private final boolean terminalBeforeAction;
        private final StepFrameLedger.FrameDraft frameDraft;
        private final StepFrameLedger.ReproductionProposal frameReproduction;

        private PositionProposal(SavannahAnimal animal, Location origin,
                                 PositionClaim adultClaim,
                                 List<PositionClaim> spawnClaims,
                                 BehaviorIntent primaryIntent,
                                 BehaviorIntent movementIntent,
                                 BehaviorIntent foodIntent,
                                 List<ExecutedEffect> immediateEffects,
                                 boolean waterPriority, boolean drinkingNow,
                                 boolean waterDeparture,
                                 boolean terminalBeforeAction,
                                 StepFrameLedger.FrameDraft frameDraft,
                                 StepFrameLedger.ReproductionProposal
                                     frameReproduction)
        {
            this.animal = animal;
            this.origin = copy(origin);
            this.adultClaim = adultClaim;
            this.spawnClaims = new ArrayList<>(spawnClaims == null
                ? new ArrayList<PositionClaim>() : spawnClaims);
            this.primaryIntent = primaryIntent;
            this.movementIntent = movementIntent;
            this.foodIntent = foodIntent;
            this.immediateEffects = new ArrayList<>(immediateEffects == null
                ? new ArrayList<ExecutedEffect>() : immediateEffects);
            this.waterPriority = waterPriority;
            this.drinkingNow = drinkingNow;
            this.waterDeparture = waterDeparture;
            this.terminalBeforeAction = terminalBeforeAction;
            this.frameDraft = frameDraft;
            this.frameReproduction = frameReproduction;
        }

        static PositionProposal released(
            SavannahAnimal animal, Location origin,
            StepFrameLedger.FrameDraft frameDraft,
            StepFrameLedger.ReproductionProposal frameReproduction)
        {
            return new PositionProposal(animal, origin, null,
                new ArrayList<PositionClaim>(), null, null, null,
                new ArrayList<ExecutedEffect>(), false, false, false, true,
                frameDraft, frameReproduction);
        }

        SavannahAnimal getAnimal()
        {
            return animal;
        }

        Location getOrigin()
        {
            return copy(origin);
        }

        PositionClaim getAdultClaim()
        {
            return adultClaim;
        }

        List<PositionClaim> getSpawnClaims()
        {
            return new ArrayList<>(spawnClaims);
        }

        boolean hasAdultClaim()
        {
            return adultClaim != null;
        }

        void complete(PositionPlan plan, PositionResolution resolution,
                      boolean breedingSucceeded, SimulationContext context)
        {
            completeBehavior(resolution, breedingSucceeded, context);
            StepFrameLedger.TerminalReason terminal = terminalBeforeAction
                ? StepFrameLedger.TerminalReason.DEAD_BEFORE_ACTION
                : !animal.isAlive()
                    ? StepFrameLedger.TerminalReason.DEAD_AFTER_ACTION
                    : waterDeparture
                        ? StepFrameLedger.TerminalReason.WATER_DEPARTURE
                        : StepFrameLedger.TerminalReason.COMPLETED;
            StepFrameLedger ledger = frameDraft.publishS4(animal, resolution,
                StepFrameLedger.resolveOrdinals(frameReproduction, plan),
                terminal);
            context.recordStepFrameLedger(ledger);
        }

        private void completeBehavior(PositionResolution resolution,
                                      boolean breedingSucceeded,
                                      SimulationContext context)
        {
            if(animal == null || !animal.isAlive() || resolution == null ||
               !resolution.isCommitted()) {
                return;
            }
            MovementDisposition disposition = dispositionFor(resolution);
            List<ExecutedEffect> effects = new ArrayList<>(immediateEffects);
            if(disposition.moved()) {
                effects.add(ExecutedEffect.MOVE);
            }
            if(breedingSucceeded) {
                effects.add(ExecutedEffect.BREED);
            }

            boolean restCredit = movementIntent != null &&
                movementIntent.getActionType() == ActionType.REST &&
                disposition == MovementDisposition.STAYED && effects.isEmpty();
            if(restCredit) {
                effects.add(ExecutedEffect.REST);
                animal.recordRestHour();
            }
            else if(waterDeparture) {
                if(!disposition.moved()) {
                    effects.add(ExecutedEffect.WAIT);
                    context.recordEvent(SimulationEvent.waterBlocked(
                        context.getStep(), animal, origin));
                }
                if(context.getThirstSystem().distanceToTargetPool(
                    animal.getLocation(), animal.lastDrunkPoolId) >=
                    WaterBehaviorPolicy.CLEARANCE_DISTANCE) {
                    animal.waterAccessState = WaterAccessState.COOLDOWN;
                    animal.waterCooldownSteps = WaterBehaviorPolicy.COOLDOWN_STEPS;
                }
            }
            else if((movementIntent != null &&
                     movementIntent.getActionType() == ActionType.WAIT &&
                     disposition == MovementDisposition.STAYED) ||
                    (waterPriority && !drinkingNow &&
                     disposition == MovementDisposition.STAYED)) {
                effects.add(ExecutedEffect.WAIT);
                if(waterPriority) {
                    context.recordEvent(SimulationEvent.waterBlocked(
                        context.getStep(), animal, origin));
                }
            }

            if(waterPriority && primaryIntent != null &&
               primaryIntent.getActionType() == ActionType.SEEK_WATER) {
                boolean drankAfterMoving = context.getActionExecutor()
                    .executeImmediate(new BehaviorIntent(animal.getId(),
                        ActionType.DRINK, primaryIntent.getReason(),
                        animal.getLocation()), animal, context);
                if(drankAfterMoving) {
                    effects.add(ExecutedEffect.DRINK);
                }
            }
            animal.rememberOutcome(new BehaviorOutcome(primaryIntent, effects,
                disposition, animal.displayAction(primaryIntent, effects,
                disposition), restCredit));
        }

        private MovementDisposition dispositionFor(PositionResolution resolution)
        {
            Location destination = resolution.getDestination();
            if(destination == null) {
                return MovementDisposition.NOT_ATTEMPTED;
            }
            if(origin != null && origin.equals(destination)) {
                return MovementDisposition.STAYED;
            }
            return resolution.getCandidateSource() ==
                PositionCandidateSource.REQUESTED ? MovementDisposition.MOVED
                                                   : MovementDisposition.REROUTED;
        }

        private static Location copy(Location location)
        {
            return location == null ? null :
                new Location(location.row(), location.col());
        }
    }

    public int getAge()
    {
        return age;
    }

    public int getFoodLevel()
    {
        return foodLevel;
    }

    /**
     * @return Food level as a percentage of this species' maximum food level.
     */
    public double getSurvivalPercent()
    {
        return getFoodSurvivalPercent();
    }

    /** Compatibility-preserving food-only survival value. */
    public double getFoodSurvivalPercent()
    {
        double percent = ((double)Math.max(0, foodLevel) /
                          Math.max(1, profile.getMaxFoodLevel())) * 100.0;
        return clamp(percent, 0.0, 100.0);
    }

    /**
     * @return 0 in the normal zone, up to 1 in the survival zone.
     */
    public double getSurvivalPressure()
    {
        double survivalPercent = getSurvivalPercent();
        if(survivalPercent > 70.0) {
            return 0.0;
        }
        else if(survivalPercent >= 30.0) {
            return (70.0 - survivalPercent) / 40.0;
        }
        else {
            return 1.0;
        }
    }

    /**
     * @return true when survival behaviour should override ordinary priorities.
     */
    public boolean isSurvivalCritical()
    {
        return getSurvivalPercent() < 30.0;
    }

    /**
     * @return Current stamina percentage.
     */
    public double getStaminaPercent()
    {
        return stamina;
    }

    public double getHydrationPercent()
    {
        return hydrationState.getPercent();
    }

    public boolean isThirsty()
    {
        return hydrationState.isBelow(THIRST_THRESHOLD);
    }

    public int getDehydrationSteps()
    {
        return hydrationState.getDehydrationSteps();
    }

    /**
     * @return Current stamina stage.
     */
    public StaminaStage getStaminaStage()
    {
        if(stamina >= 67.0) {
            return StaminaStage.HIGH;
        }
        else if(stamina >= 34.0) {
            return StaminaStage.MEDIUM;
        }
        else {
            return StaminaStage.LOW;
        }
    }

    /**
     * @return A behaviour multiplier based on stamina stage and survival burst.
     */
    public double getEffectiveStaminaMultiplier()
    {
        double multiplier;
        if(stamina >= 67.0) {
            multiplier = 0.95 + ((stamina - 67.0) / 33.0) * 0.10;
        }
        else if(stamina >= 34.0) {
            multiplier = 0.72 + ((stamina - 34.0) / 33.0) * 0.16;
        }
        else {
            multiplier = 0.50 + (stamina / 34.0) * 0.15;
        }
        if(isSurvivalCritical()) {
            multiplier *= 1.20;
        }
        return clamp(multiplier, 0.20, 1.20);
    }

    /**
     * @return Consecutive steps spent with no food.
     */
    public int getStarvingSteps()
    {
        return starvingSteps;
    }

    public LifeStage getLifeStage()
    {
        if(age <= profile.getCubAgeLimit()) {
            return LifeStage.CUB;
        }
        else if(age < profile.getAdultAge()) {
            return LifeStage.JUVENILE;
        }
        else {
            return LifeStage.ADULT;
        }
    }

    public boolean isAdult()
    {
        return getLifeStage() == LifeStage.ADULT;
    }

    public boolean canBreed()
    {
        return age >= profile.getBreedingAge() &&
               foodLevel > profile.getMaxFoodLevel() / 3 &&
               !isSurvivalCritical() &&
               stamina >= 30.0 &&
               isAlive();
    }

    public void feed(int food)
    {
        foodLevel = Math.min(profile.getMaxFoodLevel(), foodLevel + food);
        if(foodLevel > 0) {
            starvingSteps = 0;
        }
    }

    /**
     * Spend stamina on an activity.
     */
    public void spendStamina(double amount)
    {
        double cost = amount;
        if(diseaseState.isInfected()) {
            cost *= 1.18;
        }
        stamina = clamp(stamina - cost, 0.0, MAX_STAMINA);
    }

    public void drainHydration(double amount)
    {
        hydrationState.drain(amount);
    }

    public void rehydrate()
    {
        hydrationState.rehydrate();
    }

    public void addDehydrationStep()
    {
        hydrationState.addDehydrationStep();
    }

    public void dehydrateOneFood()
    {
        foodLevel = Math.max(0, foodLevel - 1);
    }

    /**
     * Record a resting hour for daily stamina recovery.
     */
    public void recordRestHour()
    {
        dailyRestHours = Math.min(HOURS_PER_DAY, dailyRestHours + 1);
    }

    /**
     * Apply stamina cost for a successful breeding event.
     */
    public void spendBreedingStamina()
    {
        spendStamina(STAMINA_BREED_COST);
    }

    public boolean isInfected()
    {
        return diseaseState.isInfected();
    }

    public void infect(double startingLevel)
    {
        diseaseState.infect(startingLevel);
    }

    public void increaseInfection(double amount)
    {
        diseaseState.increase(amount);
    }

    public void recoverFromDisease()
    {
        diseaseState.recover();
    }

    public double getInfectionLevel()
    {
        return diseaseState.getLevel();
    }

    public double getInfectiousness()
    {
        return diseaseState.isInfected() ? 0.50 + diseaseState.getLevel() : 0.0;
    }

    public double getDiseaseResistance()
    {
        double resistance = profile.getDiseaseResistance();
        if(getLifeStage() == LifeStage.CUB) {
            resistance -= profile.getYoungDiseasePenalty();
        }
        else if(getLifeStage() == LifeStage.JUVENILE) {
            resistance -= profile.getYoungDiseasePenalty() * 0.55;
        }
        return clamp(resistance, 0.05, 0.95);
    }

    public double getPredationVulnerability()
    {
        double vulnerability = profile.getPredationVulnerability();
        if(getLifeStage() == LifeStage.CUB) {
            vulnerability *= 1.70;
        }
        else if(getLifeStage() == LifeStage.JUVENILE) {
            vulnerability *= 1.32;
        }
        if(diseaseState.isInfected()) {
            vulnerability *= 1.24;
        }
        return vulnerability;
    }

    public double getHuntingCondition()
    {
        double condition = 1.0;
        if(diseaseState.isInfected()) {
            condition *= 0.72;
        }
        condition *= 0.88 + getSurvivalPressure() * 0.32;
        condition *= getEffectiveStaminaMultiplier();
        return condition;
    }

    @Override
    public String toString()
    {
        return profile.getName() + "{" +
               "sex=" + sex +
               ", age=" + age +
               ", stage=" + getLifeStage() +
               ", infected=" + diseaseState.isInfected() +
               ", alive=" + isAlive() +
               ", location=" + getLocation() +
               ", foodLevel=" + foodLevel +
               ", survival=" + (int)Math.round(getSurvivalPercent()) +
               ", stamina=" + (int)Math.round(stamina) +
               ", hydration=" + (int)Math.round(hydrationState.getPercent()) +
               '}';
    }

    private void incrementAge(SimulationContext context)
    {
        age++;
        if(age > profile.getMaxAge()) {
            context.recordDeath(this, DeathCause.AGE);
        }
    }

    private void useEnergy(SimulationContext context)
    {
        int energyUse = diseaseState.isInfected() ? 2 : 1;
        if(!profile.isPredator() &&
           context.getWeatherSystem().getCurrentWeather() == WeatherType.DROUGHT) {
            energyUse++;
        }
        energyUse += populationPressureEnergyUse(context);
        foodLevel -= energyUse;
        if(foodLevel <= 0) {
            starvingSteps++;
        }
        else {
            starvingSteps = 0;
        }
        if(!context.isThirstEnabled()) {
            resolveStarvation(context);
        }
    }

    private void resolveStarvation(SimulationContext context)
    {
        if(foodLevel <= 0 && starvingSteps >= STARVATION_LIMIT) {
            context.recordDeath(this, DeathCause.STARVATION);
        }
    }

    private boolean isActive(SimulationContext context)
    {
        return profile.isActiveDuring(context.getClock().getPhase());
    }

    private boolean shouldMove()
    {
        if(diseaseState.isInfected() && rand.nextDouble() > 0.68) {
            return false;
        }
        return true;
    }

    private PositionProposal proposeWaterDeparture(Location origin,
                                                   List<Location> candidates,
                                                   SimulationContext context,
                                                   StepFrameLedger.FrameDraft frame)
    {
        int currentDistance = context.getThirstSystem().distanceToTargetPool(origin,
            lastDrunkPoolId);
        List<Location> outward = new ArrayList<>();
        for(Location candidate : candidates) {
            if(context.getThirstSystem().distanceToTargetPool(candidate,
                lastDrunkPoolId) > currentDistance) {
                outward.add(candidate);
            }
        }
        outward.sort((first, second) -> Long.compare(
            waterCandidateTie(first, context.getStep()),
            waterCandidateTie(second, context.getStep())));
        Location requested = outward.isEmpty() ? origin : outward.get(0);
        int debt = context.getPositionWaitingDebt(getId());
        PositionClaim claim = requested.equals(origin)
            ? PositionClaim.stay(getId(), PositionPriority.WATER_DEPARTURE, debt)
            : PositionClaim.move(getId(), PositionPriority.WATER_DEPARTURE,
                requested, outward, 0, debt);
        BehaviorIntent primary = new BehaviorIntent(getId(), ActionType.MOVE,
            BehaviorReason.WATER_DEPARTURE, requested);
        StepFrameLedger.ReproductionProposal reproduction =
            StepFrameLedger.ReproductionProposal.none(frame.getFrameId(),
                context.getStep(), this, frame.getResourceClock(),
                StepFrameLedger.ProposalReason.WATER_DEPARTURE);
        frame.recordS1(reproduction);
        frame.recordS2(StepFrameLedger.PrimaryAction.nonFood(frame.getFrameId(),
            context.getStep(), this, ActionType.MOVE, false, requested));
        frame.recordS3(StepFrameLedger.ReproductionCommit.submit(reproduction,
            true));
        return new PositionProposal(this, origin, claim,
            new ArrayList<PositionClaim>(), primary, null, null,
            new ArrayList<ExecutedEffect>(), false, false, true, false, frame,
            reproduction);
    }

    private PositionClaim buildPositionClaim(Location origin,
                                             Location requested,
                                             List<Location> candidates,
                                             SimulationContext context,
                                             boolean waterPriority,
                                             BehaviorIntent primaryIntent)
    {
        PositionPriority priority = positionPriority(waterPriority, primaryIntent);
        int debt = context.getPositionWaitingDebt(getId());
        if(origin == null || requested == null || requested.equals(origin)) {
            return PositionClaim.stay(getId(), priority, debt);
        }

        List<Location> fallbacks;
        int searchBudget;
        if(waterPriority) {
            fallbacks = strictWaterwardLocations(candidates, context, origin);
            fallbacks.remove(requested);
            searchBudget = 0;
        }
        else {
            fallbacks = new ArrayList<>(candidates);
            fallbacks.remove(requested);
            searchBudget = PositionKernelPolicyRegistry.current()
                .getFallbackCandidateBudget();
        }
        return PositionClaim.move(getId(), priority, requested, fallbacks,
            searchBudget, debt);
    }

    private PositionPriority positionPriority(boolean waterPriority,
                                              BehaviorIntent primaryIntent)
    {
        if(waterPriority) {
            return primaryIntent != null &&
                primaryIntent.getReason() == BehaviorReason.CRITICAL_DEHYDRATION
                ? PositionPriority.CRITICAL_WATER
                : PositionPriority.SURVIVAL_MOVE;
        }
        if(isSurvivalCritical() || (primaryIntent != null &&
            primaryIntent.getReason() == BehaviorReason.CRITICAL_FOOD)) {
            return PositionPriority.SURVIVAL_MOVE;
        }
        return PositionPriority.ORDINARY_MOVE;
    }

    private List<Location> getGrazingLocations(PositionAvailabilityView availability,
                                               List<Location> candidates,
                                               SimulationContext context)
    {
        if(isSurvivalCritical() || getSurvivalPressure() > 0.65) {
            List<Location> urgentLocations = availability.reachableCandidates(
                getLocation(), 2, context.getRandom());
            sortByHabitatPreference(urgentLocations, context);
            return urgentLocations;
        }
        return candidates;
    }

    private List<Location> getGrazingLocations(Field nextFieldState,
                                               List<Location> freeLocations,
                                               SimulationContext context)
    {
        if(isSurvivalCritical() || getSurvivalPressure() > 0.65) {
            List<Location> urgentLocations = TerrainNavigator.freeReachableWithin(
                nextFieldState, context.getTerrainMap(), getLocation(), 2,
                context.getRandom());
            sortByHabitatPreference(urgentLocations, context);
            return urgentLocations;
        }
        return freeLocations;
    }

    private FieldTransitionResult placeInNextField(Field nextFieldState,
                                                    Location preferredLocation,
                                                    List<Location> freeLocations,
                                                    SimulationContext context,
                                                    boolean waterPriority)
    {
        Location oldLocation = getLocation();
        Location requested = preferredLocation == null ? oldLocation
                                                       : preferredLocation;
        List<Location> alternatives = new ArrayList<>();
        int searchRange;
        if(waterPriority) {
            alternatives.addAll(strictWaterwardLocations(freeLocations, context,
                                                          oldLocation));
            alternatives.add(oldLocation);
            searchRange = 0;
        }
        else {
            alternatives.addAll(freeLocations);
            searchRange = 6;
        }

        FieldTransitionResult result = context.getFieldTransition(nextFieldState)
            .tryMove(this, requested, alternatives, searchRange);
        if(result.isSuccessful()) {
            Location destination = result.getDestination();
            freeLocations.remove(destination);
            recordMovement(oldLocation, destination, context);
            if(waterPriority && destination.equals(oldLocation)) {
                context.recordEvent(SimulationEvent.waterBlocked(
                    context.getStep(), this, oldLocation));
            }
            return result;
        }
        if(waterPriority) {
            context.recordEvent(SimulationEvent.waterBlocked(context.getStep(),
                                                             this, oldLocation));
        }
        context.recordEvent(SimulationEvent.positionConflict(context.getStep(),
                                                             this, oldLocation));
        context.recordDeath(this, DeathCause.POSITION_CONFLICT);
        return result;
    }

    private List<Location> strictWaterwardLocations(List<Location> freeLocations,
                                                    SimulationContext context,
                                                    Location oldLocation)
    {
        List<Location> candidates = new ArrayList<>();
        if(freeLocations == null || oldLocation == null) {
            return candidates;
        }
        String target = getWaterTargetPoolId();
        int currentDistance = WaterSourceIndex.NONE.equals(target)
            ? context.getThirstSystem().distanceToShore(oldLocation)
            : context.getThirstSystem().distanceToTargetPool(oldLocation, target);
        for(Location location : freeLocations) {
            int distance = WaterSourceIndex.NONE.equals(target)
                ? context.getThirstSystem().distanceToShore(location)
                : context.getThirstSystem().distanceToTargetPool(location, target);
            if(distance < currentDistance) {
                candidates.add(location);
            }
        }
        candidates.sort((first, second) -> Long.compare(
            waterCandidateTie(first, context.getStep()),
            waterCandidateTie(second, context.getStep())));
        return candidates;
    }

    private void executeWaterDeparture(Field nextFieldState,
                                       List<Location> freeLocations,
                                       SimulationContext context)
    {
        Location origin = getLocation();
        int currentDistance = context.getThirstSystem().distanceToTargetPool(origin,
            lastDrunkPoolId);
        List<Location> outward = new ArrayList<>();
        for(Location candidate : freeLocations) {
            if(context.getThirstSystem().distanceToTargetPool(candidate,
                lastDrunkPoolId) > currentDistance) {
                outward.add(candidate);
            }
        }
        outward.sort((first, second) -> Long.compare(
            waterCandidateTie(first, context.getStep()),
            waterCandidateTie(second, context.getStep())));
        Location requested = outward.isEmpty() ? origin : outward.get(0);
        List<Location> alternatives = new ArrayList<>(outward);
        alternatives.add(origin);
        FieldTransitionResult transition = context.getFieldTransition(nextFieldState)
            .tryMove(this, requested, alternatives, 0);
        MovementDisposition disposition = MovementDisposition.from(
            transition.getStatus());
        List<ExecutedEffect> effects = new ArrayList<>();
        if(disposition.moved()) {
            recordMovement(origin, transition.getDestination(), context);
            effects.add(ExecutedEffect.MOVE);
        }
        else {
            effects.add(ExecutedEffect.WAIT);
            context.recordEvent(SimulationEvent.waterBlocked(context.getStep(), this,
                                                             origin));
        }
        if(context.getThirstSystem().distanceToTargetPool(getLocation(),
            lastDrunkPoolId) >= WaterBehaviorPolicy.CLEARANCE_DISTANCE) {
            waterAccessState = WaterAccessState.COOLDOWN;
            waterCooldownSteps = WaterBehaviorPolicy.COOLDOWN_STEPS;
        }
        BehaviorIntent primary = new BehaviorIntent(getId(), ActionType.MOVE,
            BehaviorReason.WATER_DEPARTURE, requested);
        rememberOutcome(new BehaviorOutcome(primary, effects, disposition,
            disposition.moved() ? ActionType.MOVE : ActionType.WAIT, false));
    }

    private void applyWaterCooldown(List<Location> freeLocations,
                                    SimulationContext context)
    {
        if(waterAccessState != WaterAccessState.COOLDOWN) {
            return;
        }
        waterCooldownSteps = Math.max(0, waterCooldownSteps - 1);
        if(waterCooldownSteps == 0) {
            waterAccessState = WaterAccessState.NORMAL;
            return;
        }
        if(isSurvivalCritical()) {
            return;
        }
        freeLocations.removeIf(location -> context.getThirstSystem()
            .distanceToTargetPool(location, lastDrunkPoolId) <
            WaterBehaviorPolicy.CLEARANCE_DISTANCE);
    }

    private long waterCandidateTie(Location location, int step)
    {
        long value = getId() * 0x9E3779B97F4A7C15L + step;
        value ^= ((long)location.row() << 32) ^ location.col();
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        return (value ^ (value >>> 33)) & Long.MAX_VALUE;
    }

    private void sortByHabitatPreference(List<Location> locations,
                                         SimulationContext context)
    {
        locations.sort((first, second) -> {
            double firstScore = HabitatPreferences.movementModifier(this,
                context.getTerrainMap().getTerrainAt(first), context);
            double secondScore = HabitatPreferences.movementModifier(this,
                context.getTerrainMap().getTerrainAt(second), context);
            return Double.compare(secondScore, firstScore);
        });
    }

    private void recordMovement(Location oldLocation, Location destination,
                                SimulationContext context)
    {
        if(oldLocation == null || !oldLocation.equals(destination)) {
            spendStamina(STAMINA_MOVE_COST);
            if(oldLocation != null && destination != null) {
                context.recordEvent(SimulationEvent.move(context.getStep(),
                                                         this, oldLocation,
                                                         destination));
            }
        }
    }

    /** Applies the already-validated R2 position commit and emits one move. */
    void commitResolvedPosition(Location origin, Location destination,
                                SimulationContext context)
    {
        setLocation(destination);
        recordMovement(origin, destination, context);
    }

    private boolean hasEffectSince(SimulationContext context, int eventCount,
                                   SimulationEvent.EventType type)
    {
        return context.hasCurrentActorEventSince(eventCount, type, getId());
    }

    private void recordFoodEffect(BehaviorIntent intent, SimulationContext context,
                                  int eventCount, List<ExecutedEffect> effects)
    {
        if(intent == null) {
            return;
        }
        if(intent.getActionType() == ActionType.HUNT &&
           hasEffectSince(context, eventCount, SimulationEvent.EventType.HUNT)) {
            effects.add(ExecutedEffect.HUNT);
        }
        else if(intent.getActionType() == ActionType.GRAZE &&
                hasEffectSince(context, eventCount, SimulationEvent.EventType.GRAZE)) {
            effects.add(ExecutedEffect.GRAZE);
        }
    }

    private ActionType displayAction(BehaviorIntent primary,
                                     List<ExecutedEffect> effects,
                                     MovementDisposition disposition)
    {
        if(effects.contains(ExecutedEffect.DRINK)) return ActionType.DRINK;
        if(effects.contains(ExecutedEffect.HUNT)) return ActionType.HUNT;
        if(effects.contains(ExecutedEffect.GRAZE)) return ActionType.GRAZE;
        if(effects.contains(ExecutedEffect.BREED)) return ActionType.BREED;
        if(primary != null && primary.getActionType() == ActionType.SEEK_WATER &&
           disposition.moved()) return ActionType.SEEK_WATER;
        if(effects.contains(ExecutedEffect.MOVE)) return ActionType.MOVE;
        if(effects.contains(ExecutedEffect.REST)) return ActionType.REST;
        if(effects.contains(ExecutedEffect.WAIT)) return ActionType.WAIT;
        return primary == null ? null : primary.getActionType();
    }

    private void recoverStaminaAtStartOfDay(SimulationContext context)
    {
        if(context.getStep() > 0 && context.getClock().getHour() == 0) {
            double foodFactor = ((double)Math.max(0, foodLevel)) /
                                Math.max(1, profile.getMaxFoodLevel());
            double restFactor = (double)dailyRestHours / HOURS_PER_DAY;
            double recoveryShare = clamp(foodFactor, 0.0, 1.0) * 0.50 +
                                   clamp(restFactor, 0.0, 1.0) * 0.50;
            stamina += (MAX_STAMINA - stamina) * recoveryShare;
            if(foodFactor < 1.0 || restFactor < 1.0) {
                stamina = Math.min(stamina, 99.0);
            }
            dailyRestHours = 0;
        }
    }

    private int populationPressureEnergyUse(SimulationContext context)
    {
        int population = context.getPopulation(profile.getName());
        int target = populationTarget(context);
        if(population <= target) {
            return 0;
        }
        double pressure = (double)(population - target) / Math.max(1, target);
        if(profile.isPredator()) {
            return pressure > 0.70 ? 1 : 0;
        }
        return Math.min(3, (int)Math.ceil(pressure * 1.35));
    }

    private int populationTarget(SimulationContext context)
    {
        int multiplier = profile.isPredator() ? 5 : 10;
        return Math.max(1, context.getFoundingPopulation(profile) * multiplier);
    }

    private double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
