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
    public static final int DEHYDRATION_CAUSE_STEPS =
        HydrationPolicy.DEFAULT_DEHYDRATION_GRACE_STEPS;
    private static final int HOURS_PER_DAY = 24;

    private final SpeciesProfile profile;
    private final Random rand;
    private final Sex sex;
    private int age;
    private int foodLevel;
    private int starvingSteps;
    /** M3 change F: fractional food use carried to the next step. */
    private double metabolismDebt;
    private double upkeepDebt;
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
    private int waterWaitSteps;
    private String excludedPoolId = WaterSourceIndex.NONE;
    private int excludedPoolUntilStep = -1;
    private final ProductionTargetState productionTarget =
        new ProductionTargetState();
    private final ReproductionState reproduction = new ReproductionState();
    private long motherId;

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
        // A due pregnancy gives birth even while seeking water; new
        // conceptions yield to water priority unless M3 change A
        // (repro.conceiveWhileThirsty) lets a thirsty but not critically
        // dehydrated, not survival-critical animal still conceive.
        boolean dueBirth = reproduction.isDue(context.getStep()) &&
            breedingIntent != null;
        boolean thirstyConception = waterPriority && breedingIntent != null &&
            intParam(EcologyParameters.Key.CONCEIVE_WHILE_THIRSTY) == 1 &&
            !isSurvivalCritical() &&
            primaryIntent.getReason() != BehaviorReason.CRITICAL_DEHYDRATION;
        if(dueBirth || ((!waterPriority || thirstyConception) && breedingIntent != null)) {
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
        // A mother giving birth stays put unless she must reach water.
        boolean givingBirth = !spawnClaims.isEmpty();
        // G04 mate encounter and D6 dependency: steer ordinary movement toward
        // the nearest sensed mate, or keep a dependent predator cub near its
        // mother. Stable sort, so habitat order breaks ties; no random draws.
        Location socialTarget = reproductionProposal.getReason() ==
            StepFrameLedger.ProposalReason.NO_MATE ? nearestSensedMate(context)
            : dependentMotherLocation(context, origin);
        if(socialTarget != null) {
            candidates.sort(java.util.Comparator.comparingInt(
                location -> chebyshev(location, socialTarget)));
        }
        // M2-3 threat avoidance: prey move away from the nearest sensed
        // predator; it outranks grazing moves but not water priority.
        Location threat = !profile.isPredator() && !waterPriority
            ? nearestThreat(context, origin) : null;
        boolean fleeing = false;
        if(threat != null && !candidates.isEmpty()) {
            candidates.sort(java.util.Comparator.comparingInt(
                location -> -chebyshev(location, threat)));
            fleeing = chebyshev(candidates.get(0), threat) > chebyshev(origin, threat);
        }
        // M2-3 predator exclusion: leave a zone crowded with other predators.
        Location crowdCentre = profile.isPredator() && !waterPriority &&
            socialTarget == null ? predatorCrowdCentre(context, origin) : null;
        if(crowdCentre != null) {
            candidates.sort(java.util.Comparator.comparingInt(
                location -> -chebyshev(location, crowdCentre)));
        }

        // M3 family 2: a prey that needs water but is not survival-critical
        // may flee an active predator instead of approaching water (mode 1)
        // or instead of staying on the shore (mode 2). Mode 0 keeps water first.
        Location waterFlee = waterPriority && !profile.isPredator()
            ? waterFleeTarget(context, origin, candidates, primaryIntent) : null;
        boolean drinkingNow = waterFlee == null && context.getActionExecutor()
            .executeImmediate(primaryIntent, this, context);
        if(drinkingNow) {
            effects.add(ExecutedEffect.DRINK);
        }

        Location thirstDestination = waterPriority && waterFlee == null &&
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
        List<Location> chaseFallbacks = null;
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
            Location foodMove = framePrimary.getDestination();
            // B-1: out of contact, a predator advances along a shortening path
            // at its own speed instead of jumping onto the prey's cell.
            if(profile.isPredator() && foodMove != null &&
               framePrimary.getHuntResult() != null &&
               framePrimary.getHuntResult().getOutcome() == HuntOutcome.OUT_OF_CONTACT) {
                ChasePlanner.Plan chase = ChasePlanner.plan(context.getTerrainMap(),
                    availability, origin, foodMove, maxMoveDistance(),
                    huntOpportunity.getEffectiveRange());
                foodMove = chase.getTarget();
                chaseFallbacks = chase.getFallbacks();
            }
            // B-2: a hungry predator with no reachable prey keeps one search
            // direction (re-drawn after searchPersistence steps or when blocked)
            // instead of circling inside the same habitat; any other hunt
            // result ends the search.
            if(profile.isPredator() && framePrimary.getHuntResult() != null) {
                HuntOutcome huntOutcome = framePrimary.getHuntResult().getOutcome();
                if(huntOutcome == HuntOutcome.NO_LOCAL_REACHABLE_PREY) {
                    if(foodMove == null && !givingBirth &&
                       (isSurvivalCritical() || hungerPressure() >= 0.5)) {
                        foodMove = hungerSearchMove(candidates, origin, context);
                    }
                }
                else if(huntOutcome != HuntOutcome.INELIGIBLE) {
                    productionTarget.clearSearch();
                }
            }
            // A grazing move that also gains distance from the threat is kept.
            boolean fleeingGraze = fleeing && foodMove != null &&
                chebyshev(foodMove, threat) > chebyshev(origin, threat);
            nextLocation = givingBirth || (fleeing && !fleeingGraze) ? null : foodMove;
            if(fleeingGraze) {
                fleeing = false;
                context.recordSocialMove(BehaviorReason.THREAT_AVOIDANCE);
            }
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
        if(waterFlee != null) {
            stopDrinkingSession();
            movementIntent = new BehaviorIntent(getId(), ActionType.MOVE,
                BehaviorReason.THREAT_AVOIDANCE, waterFlee);
            context.recordSocialMove(BehaviorReason.THREAT_AVOIDANCE);
            nextLocation = context.getActionExecutor()
                .resolveMovementDestination(movementIntent, this);
            candidates.remove(nextLocation);
        }
        // C-3: an adult that senses no mate keeps a search heading instead of wandering, so
        // sparse populations can still meet (social.mateSearch, 0 = off).
        if(!waterPriority && nextLocation == null && !givingBirth && isActive(context) &&
           reproductionProposal.getReason() == StepFrameLedger.ProposalReason.NO_MATE &&
           intParam(EcologyParameters.Key.MATE_SEARCH) == 1 && !candidates.isEmpty()) {
            nextLocation = hungerSearchMove(candidates, origin, context);
        }
        if(!waterPriority && nextLocation == null && !givingBirth) {
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
            if(fleeing && active && !candidates.isEmpty()) {
                canMove = true;
            }
            if(fleeing && canMove) {
                movementIntent = new BehaviorIntent(getId(), ActionType.MOVE,
                    BehaviorReason.THREAT_AVOIDANCE, candidates.get(0));
            }
            else if(crowdCentre != null && canMove && !candidates.isEmpty()) {
                movementIntent = new BehaviorIntent(getId(), ActionType.MOVE,
                    BehaviorReason.EGRESS, candidates.get(0));
            }
            else if(!hasNonRestEffect || canMove) {
                // B-2: a predator's ordinary wandering is a weighted random
                // choice over habitat quality, not always the best neighbour.
                if(profile.isPredator() && canMove && socialTarget == null &&
                   crowdCentre == null && candidates.size() > 1) {
                    promoteWeightedCandidate(candidates, context);
                }
                movementIntent = context.getMovementBehaviorProvider().propose(
                    this, candidates, canMove, intentionalRest,
                    illnessRecovery ? BehaviorReason.ILLNESS_RECOVERY
                                    : BehaviorReason.REST);
            }
            if(movementIntent != null) {
                if(movementIntent.getReason() == BehaviorReason.THREAT_AVOIDANCE ||
                   movementIntent.getReason() == BehaviorReason.EGRESS) {
                    context.recordSocialMove(movementIntent.getReason());
                }
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

        // A water flee is an ordinary move: no water priority or fallbacks.
        boolean waterMove = waterPriority && waterFlee == null;
        PositionClaim adultClaim = buildPositionClaim(origin, nextLocation,
            candidates, context, waterMove, primaryIntent,
            nextLocation != null ? chaseFallbacks : null);
        return new PositionProposal(this, origin, adultClaim, spawnClaims,
            waterFlee == null ? primaryIntent : movementIntent, movementIntent,
            foodIntent, effects, waterMove, drinkingNow, false, false, frame,
            reproductionProposal);
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

    public long getMotherId()
    {
        return motherId;
    }

    void setMotherId(long id)
    {
        motherId = id;
    }

    /** D6: a predator cub that still depends on its living mother. */
    boolean isDependentOf(SavannahAnimal mother)
    {
        return mother != null && motherId == mother.getId() && isAlive() &&
               !isAdult() && profile.isPredator();
    }

    /** D6 provisioning transfer: conserved food from mother to her cub. */
    void transferFoodTo(SavannahAnimal young, int amount)
    {
        if(amount <= 0) return;
        payReproductionFood(amount);
        young.feed(amount);
    }

    private Location nearestSensedMate(SimulationContext context)
    {
        if(sex != Sex.FEMALE || getLocation() == null) return null;
        SpatialEcologyView view = context.getSpatialEcologyView();
        // Females look this many breeding ranges away for a mate (G04).
        int radius = profile.getBreedingRange() *
            intParam(EcologyParameters.Key.MATE_SENSE_FACTOR);
        Location origin = getLocation();
        Location best = null;
        int bestDistance = Integer.MAX_VALUE;
        long bestId = Long.MAX_VALUE;
        for(int row = origin.row() - radius; row <= origin.row() + radius; row++) {
            for(int col = origin.col() - radius; col <= origin.col() + radius; col++) {
                if(row < 0 || col < 0 || row >= context.getTerrainMap().getDepth() ||
                   col >= context.getTerrainMap().getWidth()) continue;
                SpatialAnimalFact fact = view.getAnimalFactAt(new Location(row, col));
                if(fact == null || fact.getSex() != Sex.MALE ||
                   fact.getLifeStage() != LifeStage.ADULT ||
                   !profile.getName().equals(fact.getSpecies())) continue;
                int distance = chebyshev(origin, fact.getLocation());
                if(distance < bestDistance ||
                   (distance == bestDistance && fact.getId() < bestId)) {
                    best = fact.getLocation();
                    bestDistance = distance;
                    bestId = fact.getId();
                }
            }
        }
        return best;
    }

    private Location dependentMotherLocation(SimulationContext context,
                                             Location origin)
    {
        if(motherId <= 0 || isAdult() || !profile.isPredator() || origin == null) {
            return null;
        }
        SpatialAnimalFact mother = context.getSpatialEcologyView()
            .getAnimalFact(motherId);
        if(mother == null || chebyshev(origin, mother.getLocation()) <= 1) return null;
        return mother.getLocation();
    }

    /** Plan v3.0 M2-3 radii and counts live in {@link EcologyParameters}. */
    private static int intParam(EcologyParameters.Key key)
    {
        return EcologyParameters.current().getInt(key);
    }

    /** Nearest adult predator within the threat radius that can eat this prey. */
    private Location nearestThreat(SimulationContext context, Location origin)
    {
        Location best = null;
        int bestDistance = Integer.MAX_VALUE;
        long bestId = Long.MAX_VALUE;
        for(SpatialAnimalFact fact : factsAround(context, origin,
                intParam(EcologyParameters.Key.THREAT_RADIUS))) {
            if(!fact.isPredator() || fact.getLifeStage() != LifeStage.ADULT) continue;
            SpeciesProfile hunter = SpeciesRegistry.getProfile(fact.getSpecies());
            // Only predators that can eat this prey and are active now count.
            if(hunter == null || !HuntLocalOpportunity.canHunt(hunter, this,
                   intParam(EcologyParameters.Key.CHEETAH_ZEBRA_STAGE)) ||
               !hunter.isActiveDuring(context.getClock().getPhase())) continue;
            int distance = chebyshev(origin, fact.getLocation());
            if(distance < bestDistance || (distance == bestDistance && fact.getId() < bestId)) {
                best = fact.getLocation();
                bestDistance = distance;
                bestId = fact.getId();
            }
        }
        return best;
    }

    /**
     * Flee move for a thirsty prey under water priority, or null. Allowed by
     * {@code avoidance.waterFleeMode} (1: while seeking water, 2: also while
     * drinking) only when the animal is neither survival-critical nor
     * critically dehydrated nor holding a critical water reservation, and only
     * when some candidate gains distance from the nearest active predator.
     */
    private Location waterFleeTarget(SimulationContext context, Location origin,
                                     List<Location> candidates,
                                     BehaviorIntent primaryIntent)
    {
        int mode = intParam(EcologyParameters.Key.WATER_FLEE_MODE);
        if(mode == 0 || candidates.isEmpty() || isSurvivalCritical() ||
           primaryIntent.getReason() == BehaviorReason.CRITICAL_DEHYDRATION ||
           context.getCriticalWaterReservation(getId()) != null ||
           (primaryIntent.getActionType() == ActionType.DRINK && mode < 2)) {
            return null;
        }
        Location threat = nearestThreat(context, origin);
        if(threat == null) return null;
        Location best = null;
        int bestDistance = chebyshev(origin, threat);
        for(Location candidate : candidates) {
            int distance = chebyshev(candidate, threat);
            if(distance > bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Centroid of other adult predators when more than the exclusion count are near. */
    private Location predatorCrowdCentre(SimulationContext context, Location origin)
    {
        int count = 0;
        long rows = 0;
        long cols = 0;
        for(SpatialAnimalFact fact : factsAround(context, origin,
                intParam(EcologyParameters.Key.PREDATOR_EXCLUSION_RADIUS))) {
            if(!fact.isPredator() || fact.getId() == getId() ||
               fact.getLifeStage() != LifeStage.ADULT || fact.getId() == motherId) continue;
            count++;
            rows += fact.getLocation().row();
            cols += fact.getLocation().col();
        }
        if(count <= intParam(EcologyParameters.Key.PREDATOR_EXCLUSION_COUNT)) return null;
        return new Location((int)(rows / count), (int)(cols / count));
    }

    private static List<SpatialAnimalFact> factsAround(SimulationContext context,
                                                       Location origin, int radius)
    {
        List<SpatialAnimalFact> facts = new ArrayList<>();
        if(origin == null) return facts;
        SpatialEcologyView view = context.getSpatialEcologyView();
        int depth = context.getTerrainMap().getDepth();
        int width = context.getTerrainMap().getWidth();
        for(int row = Math.max(0, origin.row() - radius);
            row <= Math.min(depth - 1, origin.row() + radius); row++) {
            for(int col = Math.max(0, origin.col() - radius);
                col <= Math.min(width - 1, origin.col() + radius); col++) {
                SpatialAnimalFact fact = view.getAnimalFactAt(new Location(row, col));
                if(fact != null && fact.wasAliveAtCapture()) facts.add(fact);
            }
        }
        return facts;
    }

    private static int chebyshev(Location first, Location second)
    {
        return Math.max(Math.abs(first.row() - second.row()),
                        Math.abs(first.col() - second.col()));
    }

    /** Pregnancy and postpartum state; only the commit phase mutates it. */
    public ReproductionState getReproductionState()
    {
        return reproduction;
    }

    /** Conception debit; the policy guarantees the maternal floor remains. */
    void payReproductionFood(long food)
    {
        if(food < 0 || food > foodLevel) {
            throw new IllegalStateException(
                "conception debit exceeds the mother's food: " + food);
        }
        foodLevel -= (int)food;
    }

    /** Newborn resources come only from the committed ordinal escrow. */
    void initializeNewborn(ResourceVector escrow)
    {
        foodLevel = (int)Math.min(profile.getMaxFoodLevel(), escrow.getFoodUnits());
        stamina = clamp(ReproductionPolicy.percent(escrow.getStaminaMicros()),
            0.0, MAX_STAMINA);
        hydrationState.setPercent(ReproductionPolicy.percent(
            escrow.getHydrationMicros()));
        starvingSteps = foodLevel <= 0 ? 1 : 0;
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

    /** One service step on a shore slot; ends the session when hydrated. */
    void drinkServing(double percent)
    {
        hydrationState.drink(percent);
        waterAccessState = WaterAccessState.DRINKING;
    }

    boolean isDrinking()
    {
        return waterAccessState == WaterAccessState.DRINKING;
    }

    void stopDrinkingSession()
    {
        if(waterAccessState == WaterAccessState.DRINKING) {
            waterAccessState = WaterAccessState.NORMAL;
        }
    }

    public int getWaterWaitSteps()
    {
        return waterWaitSteps;
    }

    /** @return the queued-step count after this blocked step. */
    int recordWaterWait()
    {
        return ++waterWaitSteps;
    }

    void resetWaterWait()
    {
        waterWaitSteps = 0;
    }

    /** Abandon the current pool: avoid it for a while and pick another. */
    void abandonWaterTarget(int step)
    {
        excludedPoolId = waterTargetPoolId;
        excludedPoolUntilStep = step +
            intParam(EcologyParameters.Key.WATER_RETARGET_EXCLUSION_STEPS);
        clearWaterTarget();
        waterWaitSteps = 0;
    }

    boolean isPoolExcluded(String poolId, int step)
    {
        return poolId != null && poolId.equals(excludedPoolId) &&
               step < excludedPoolUntilStep;
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

        StepFrameLedger.ReproductionProposal getFrameReproduction()
        {
            return frameReproduction;
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
            vulnerability *= EcologyParameters.current()
                .get(EcologyParameters.Key.CUB_VULNERABILITY);
        }
        else if(getLifeStage() == LifeStage.JUVENILE) {
            vulnerability *= EcologyParameters.current()
                .get(EcologyParameters.Key.JUVENILE_VULNERABILITY);
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
        int baseUse = diseaseState.isInfected() ? 2 : 1;
        // M3 change F: per-species metabolism; 1.0 keeps whole units per step.
        double scale = metabolismScale();
        int energyUse = baseUse;
        if(scale != 1.0) {
            metabolismDebt += baseUse * scale;
            energyUse = (int)Math.floor(metabolismDebt);
            metabolismDebt -= energyUse;
        }
        if(!profile.isPredator() &&
           context.getWeatherSystem().getCurrentWeather() == WeatherType.DROUGHT) {
            energyUse++;
        }
        energyUse += gestationUpkeepUse();
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

    /**
     * M3 change B2: a pregnant mother uses {@code repro.gestationUpkeep} extra
     * food per step; the fraction carries to the next step. 0 = off.
     */
    private int gestationUpkeepUse()
    {
        double upkeep = EcologyParameters.current().get(
            EcologyParameters.Key.GESTATION_UPKEEP);
        if(upkeep <= 0.0 || !reproduction.isPregnant()) return 0;
        upkeepDebt += upkeep;
        int whole = (int)Math.floor(upkeepDebt);
        upkeepDebt -= whole;
        return whole;
    }

    /** Per-species metabolism scale (M3 change F); herbivores stay at 1.0. */
    private double metabolismScale()
    {
        if(SpeciesRegistry.LION.equals(profile.getName())) {
            return EcologyParameters.current().get(EcologyParameters.Key.LION_METABOLISM);
        }
        if(SpeciesRegistry.CHEETAH.equals(profile.getName())) {
            return EcologyParameters.current().get(EcologyParameters.Key.CHEETAH_METABOLISM);
        }
        return 1.0;
    }

    private void resolveStarvation(SimulationContext context)
    {
        int limit = intParam(profile.isPredator()
            ? EcologyParameters.Key.STARVATION_PREDATOR_STEPS
            : EcologyParameters.Key.STARVATION_HERBIVORE_STEPS);
        if(foodLevel <= 0 && starvingSteps >= limit) {
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

    private double hungerPressure()
    {
        return 1.0 - (double)foodLevel / Math.max(1, profile.getMaxFoodLevel());
    }

    private static double squaredHabitat(SavannahAnimal animal, Location cell,
                                         SimulationContext context)
    {
        double modifier = HabitatPreferences.movementModifier(animal,
            context.getTerrainMap().getTerrainAt(cell), context);
        return modifier * modifier;
    }

    /** B-2: moves one weighted random candidate (weight = habitat modifier squared) to the front. */
    private void promoteWeightedCandidate(List<Location> candidates, SimulationContext context)
    {
        double total = 0.0;
        double[] weights = new double[candidates.size()];
        for(int index = 0; index < weights.length; index++) {
            weights[index] = squaredHabitat(this, candidates.get(index), context);
            total += weights[index];
        }
        if(total <= 0.0) return;
        double draw = context.getRandom().nextDouble() * total;
        int chosen = weights.length - 1;
        for(int index = 0; index < weights.length; index++) {
            draw -= weights[index];
            if(draw < 0.0) {
                chosen = index;
                break;
            }
        }
        if(chosen > 0) java.util.Collections.swap(candidates, 0, chosen);
    }

    /**
     * B-2: the candidate best aligned with the current search direction; a new
     * direction is drawn (8 directions, weight = neighbour habitat squared)
     * when there is none, it is older than searchPersistence steps, or no
     * candidate points along it.
     */
    private Location hungerSearchMove(List<Location> candidates, Location origin,
                                      SimulationContext context)
    {
        if(candidates.isEmpty() || origin == null) return null;
        int persistence = intParam(EcologyParameters.Key.SEARCH_PERSISTENCE);
        boolean fresh = !productionTarget.hasSearchDirection() ||
            context.getStep() - productionTarget.getSearchStartStep() >= persistence;
        Location pick = null;
        if(!fresh) {
            pick = bestAlongDirection(candidates, origin,
                productionTarget.getSearchDRow(), productionTarget.getSearchDCol());
            fresh = pick == null;
        }
        if(fresh) {
            int[] direction = drawSearchDirection(origin, context);
            if(direction == null) return null;
            productionTarget.startSearch(direction[0], direction[1], context.getStep());
            pick = bestAlongDirection(candidates, origin, direction[0], direction[1]);
        }
        return pick;
    }

    private static Location bestAlongDirection(List<Location> candidates, Location origin,
                                               int dRow, int dCol)
    {
        Location best = null;
        int bestDot = 0;
        for(Location cell : candidates) {
            int dot = (cell.row() - origin.row()) * dRow + (cell.col() - origin.col()) * dCol;
            if(dot > bestDot || (dot == bestDot && dot > 0 && best != null &&
               (cell.row() < best.row() ||
                (cell.row() == best.row() && cell.col() < best.col())))) {
                best = cell;
                bestDot = dot;
            }
        }
        return best;
    }

    private int[] drawSearchDirection(Location origin, SimulationContext context)
    {
        TerrainMap terrain = context.getTerrainMap();
        int[][] directions = new int[8][];
        double[] weights = new double[8];
        int count = 0;
        double total = 0.0;
        for(int dRow = -1; dRow <= 1; dRow++) {
            for(int dCol = -1; dCol <= 1; dCol++) {
                if(dRow == 0 && dCol == 0) continue;
                Location neighbour = new Location(origin.row() + dRow, origin.col() + dCol);
                double weight = terrain.isPassableAt(neighbour.row(), neighbour.col())
                    ? squaredHabitat(this, neighbour, context) : 0.0;
                directions[count] = new int[] {dRow, dCol};
                weights[count] = weight;
                total += weight;
                count++;
            }
        }
        if(total <= 0.0) return null;
        double draw = context.getRandom().nextDouble() * total;
        int chosen = -1;
        for(int index = 0; index < count; index++) {
            if(weights[index] <= 0.0) continue;
            chosen = index;
            draw -= weights[index];
            if(draw < 0.0) break;
        }
        return chosen < 0 ? null : directions[chosen];
    }

    /** Most land steps this animal may advance in one step (B-1). */
    int maxMoveDistance()
    {
        return maxMoveDistanceOf(profile);
    }

    static int maxMoveDistanceOf(SpeciesProfile profile)
    {
        EcologyParameters parameters = EcologyParameters.current();
        if(SpeciesRegistry.LION.equals(profile.getName())) {
            return parameters.getInt(EcologyParameters.Key.LION_SPEED);
        }
        if(SpeciesRegistry.CHEETAH.equals(profile.getName())) {
            return parameters.getInt(EcologyParameters.Key.CHEETAH_SPEED);
        }
        return 2;
    }

    private PositionClaim buildPositionClaim(Location origin,
                                             Location requested,
                                             List<Location> candidates,
                                             SimulationContext context,
                                             boolean waterPriority,
                                             BehaviorIntent primaryIntent,
                                             List<Location> chaseFallbacks)
    {
        PositionPriority priority = positionPriority(waterPriority, primaryIntent);
        int debt = context.getPositionWaitingDebt(getId());
        if(origin == null || requested == null || requested.equals(origin)) {
            return PositionClaim.stay(getId(), priority, debt);
        }

        List<Location> fallbacks;
        int searchBudget;
        if(chaseFallbacks != null && !waterPriority) {
            // B-1 chase: only cells that also shorten the distance to the
            // prey; no wider search, so the step never exceeds the speed.
            fallbacks = new ArrayList<>(chaseFallbacks);
            fallbacks.remove(requested);
            searchBudget = 0;
        }
        else if(waterPriority) {
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
            int cells = oldLocation == null || destination == null ? 1 :
                Math.max(1, chebyshev(oldLocation, destination));
            spendStamina(STAMINA_MOVE_COST * cells);
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

    /**
     * Plan v3.0 M2-1: overload in the animal's own window costs energy. The
     * prey limit is the same one that suppresses prey births.
     */
    private int populationPressureEnergyUse(SimulationContext context)
    {
        if(getLocation() == null || context.getSpatialEcologyView() == null) return 0;
        int conspecific = context.getSpatialEcologyView().getLocalOccupancy(
            getLocation(), intParam(EcologyParameters.Key.LOCAL_WINDOW_RADIUS),
            profile).getConspecificCount();
        int limit = intParam(profile.isPredator()
            ? EcologyParameters.Key.PREDATOR_OVERLOAD_COUNT
            : EcologyParameters.Key.PREY_OVERLOAD_COUNT);
        if(conspecific <= limit) {
            return 0;
        }
        double pressure = (double)(conspecific - limit) / limit;
        if(profile.isPredator()) {
            return pressure > 0.70 ? 1 : 0;
        }
        return Math.min(3, (int)Math.ceil(pressure * 1.35));
    }

    private double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
