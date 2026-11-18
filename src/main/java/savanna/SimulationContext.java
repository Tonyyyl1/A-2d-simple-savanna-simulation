package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Shared ecological systems and step-level state. Event retention and completed
 * observation facts are delegated to their own components behind this API.
 */
public class SimulationContext
{
    private final Random random;
    private final SimulationClock clock;
    private final WeatherSystem weatherSystem;
    private final DiseaseSystem diseaseSystem;
    private final PredationSystem predationSystem;
    private final BreedingSystem breedingSystem;
    private final FoodSystem foodSystem;
    private final TerrainMap terrainMap;
    private final WaterTopology waterTopology;
    private final ThirstSystem thirstSystem;
    private final SurvivalSystem survivalSystem;
    private final BehaviorProvider thirstBehaviorProvider;
    private final BehaviorProvider foodBehaviorProvider;
    private final MovementBehaviorProvider movementBehaviorProvider;
    private final BehaviorProvider breedingBehaviorProvider;
    private final BehaviorResolver behaviorResolver;
    private final ActionExecutor actionExecutor;
    private final SimulationConfig config;
    private final ResolvedRuntimeConfig runtimeConfig;
    private final SimulationEventJournal eventJournal;
    private final StepObservationLedger stepObservations =
        new StepObservationLedger();
    private final RenderProvenance renderProvenance;
    private final SpeciesEcologyFactRegistry speciesEcologyFactRegistry;
    private final BirthResourceAccount birthAccount = new BirthResourceAccount();
    private SpatialEcologyView spatialEcologyView;
    private Soc1aFactView soc1aFactView;
    private int spatialEcologyCaptureCount;
    private FieldTransition fieldTransition;
    private PositionTopology positionTopology;
    private PositionPlan lastPositionPlan;
    private WaterholeCongestionAssessment waterholeCongestionAssessment;
    private final Map<Long, Location> criticalWaterReservations = new java.util.HashMap<>();
    private final Map<Location, Long> criticalWaterReservedCells = new java.util.HashMap<>();
    private final Map<Long, Integer> positionWaitingDebt =
        new java.util.HashMap<>();
    private Map<String, Integer> populationSnapshot;
    private Map<String, Integer> livePopulationBySpecies;
    private Field currentStepField;
    private List<SavannahAnimal> stepPredatorList;
    /** Per-cell table reused by every step's position resolution; empty between steps. */
    private final PurePositionReservationResolver.Scratch positionResolverScratch =
        new PurePositionReservationResolver.Scratch();
    private final Set<Long> committedBirthPopulationIds = new java.util.HashSet<>();
    private int step;
    private final List<TransitionObservationListener> transitionListeners =
        new ArrayList<>();
    private final List<PositionCandidateObservationListener>
        positionCandidateListeners = new ArrayList<>();
    private final List<HuntObservationListener> huntObservationListeners =
        new ArrayList<>();
    private final List<BreedingRandomObservationListener>
        breedingRandomObservationListeners = new ArrayList<>();
    private final List<DecisionBirthCandidateFactListener>
        decisionBirthCandidateFactListeners = new ArrayList<>();
    private final Map<DeathCause, Integer> deathCounts =
        new EnumMap<>(DeathCause.class);

    public SimulationContext(int depth, int width)
    {
        this(depth, width, SimulationConfig.baseline());
    }

    public SimulationContext(int depth, int width, SimulationConfig config)
    {
        this(depth, width, ResolvedRuntimeConfig.fromLegacy(config));
    }

    public SimulationContext(int depth, int width,
                             ResolvedRuntimeConfig runtimeConfig)
    {
        this.runtimeConfig = runtimeConfig == null
            ? ResolvedRuntimeConfig.fromLegacy(SimulationConfig.baseline())
            : runtimeConfig;
        this.config = this.runtimeConfig.getEcologyConfig();
        eventJournal = new SimulationEventJournal(
            this.runtimeConfig.getObservationConfig());
        random = new Random(this.config.getRandomSeed());
        clock = new SimulationClock();
        weatherSystem = new SeasonalWeatherSystem(random);
        diseaseSystem = new SavannahDiseaseSystem(random);
        predationSystem = new FoodChainPredationSystem(random);
        breedingSystem = new MateFindingBreedingSystem(random);
        terrainMap = new TerrainMap(depth, width, this.config.getTerrainSeed(),
            this.config.getTerrainGenerationProfile());
        foodSystem = new GrasslandFoodSystem(terrainMap);
        waterTopology = this.config.isThirstEnabled()
            ? WaterTopology.forTerrain(terrainMap) : null;
        renderProvenance = RenderProvenance.capture(this.runtimeConfig, terrainMap);
        speciesEcologyFactRegistry = SpeciesEcologyFactRegistry.capture();
        thirstSystem = this.config.isThirstEnabled()
            ? new SavannahThirstSystem(terrainMap, waterTopology,
                this.config.getHydrationPolicy()) : null;
        survivalSystem = new SavannahSurvivalSystem();
        thirstBehaviorProvider = new ThirstBehaviorProvider();
        foodBehaviorProvider = new FoodBehaviorProvider();
        movementBehaviorProvider = new MovementBehaviorProvider();
        breedingBehaviorProvider = new BreedingBehaviorProvider();
        behaviorResolver = new BehaviorResolver();
        actionExecutor = new ActionExecutor();
    }

    public void startStep(int step, Field field)
    {
        if(field == null) throw new IllegalArgumentException("step field is required");
        this.step = step;
        currentStepField = field;
        stepPredatorList = null;
        eventJournal.startStep(step);
        clock.setStep(step);
        weatherSystem.update(this);
        foodSystem.grow(this, field);
        populationSnapshot = field.countLivingBySpecies();
        livePopulationBySpecies = new java.util.LinkedHashMap<>(populationSnapshot);
        spatialEcologyView = SpatialEcologyIndex.capture(step, field, terrainMap);
        soc1aFactView = Soc1aFactIndex.capture(spatialEcologyView,
            speciesEcologyFactRegistry);
        spatialEcologyCaptureCount++;
        waterholeCongestionAssessment = isThirstEnabled()
            ? new WaterholeCongestionAssessment(field, waterTopology) : null;
        criticalWaterReservations.clear();
        criticalWaterReservedCells.clear();
        committedBirthPopulationIds.clear();
        stepObservations.startStep();
        fieldTransition = null;
        lastPositionPlan = null;
    }

    public void recordEvent(SimulationEvent event)
    {
        eventJournal.record(event);
    }

    /** Atomically assign a death cause, record its event, and update totals. */
    public boolean recordDeath(SavannahAnimal animal, DeathCause cause)
    {
        if(animal == null || !animal.isAlive()) {
            return false;
        }
        DeathCause actualCause = cause == null || cause == DeathCause.NONE
            ? DeathCause.UNKNOWN : cause;
        Location location = animal.getLocation();
        animal.setDead(actualCause);
        ReproductionState reproduction = animal.getReproductionState();
        if(reproduction.isPregnant()) {
            // E07: a mother's death turns every pending ordinal into loss.
            for(BirthResourceTransactionResult result :
                    reproduction.loseAllPending()) {
                birthAccount.record(result);
            }
            reproduction.closeIfResolved(step, animal.getProfile());
        }
        invalidateTargetsForDeadAnimal(animal.getId());
        decrementLivePopulation(animal.getProfile().getName());
        deathCounts.put(actualCause, getDeathCount(actualCause) + 1);
        recordEvent(SimulationEvent.death(step, animal, location, actualCause));
        return true;
    }

    PurePositionReservationResolver.Scratch getPositionResolverScratch()
    {
        return positionResolverScratch;
    }

    private void invalidateTargetsForDeadAnimal(long deadAnimalId)
    {
        if(currentStepField == null) return;
        // Only predators ever hold a HUNT_PREY target (acquireHuntTarget runs after the
        // isPredator() guard in HUNT), so the ~160 predators are scanned instead of a fresh
        // copy of the whole animal list for every death. Same order as the full list.
        for(SavannahAnimal hunter : stepPredators()) {
            if(hunter.getProductionTarget().targets(deadAnimalId)) {
                hunter.invalidateProductionTarget(
                    ProductionTargetState.Status.TARGET_DEAD);
            }
        }
    }

    /** The current step field's predators, captured on the first death of the step. */
    private List<SavannahAnimal> stepPredators()
    {
        List<SavannahAnimal> predators = stepPredatorList;
        if(predators == null) {
            predators = new ArrayList<>();
            for(Animal candidate : currentStepField.getAnimals()) {
                if(candidate instanceof SavannahAnimal &&
                   ((SavannahAnimal)candidate).getProfile().isPredator()) {
                    predators.add((SavannahAnimal)candidate);
                }
            }
            stepPredatorList = predators;
        }
        return predators;
    }

    public int getDeathCount(DeathCause cause)
    {
        Integer count = deathCounts.get(cause);
        return count == null ? 0 : count;
    }

    public Map<DeathCause, Integer> getDeathCounts()
    {
        return Collections.unmodifiableMap(new EnumMap<>(deathCounts));
    }

    public String getDeathSummary()
    {
        StringBuilder summary = new StringBuilder();
        for(DeathCause cause : DeathCause.values()) {
            int count = getDeathCount(cause);
            if(cause == DeathCause.NONE || count == 0) {
                continue;
            }
            if(summary.length() > 0) {
                summary.append(',');
            }
            summary.append(cause.name()).append('=').append(count);
        }
        return summary.length() == 0 ? "none" : summary.toString();
    }

    public void addEventListener(SimulationEventListener listener)
    {
        eventJournal.addListener(listener);
    }

    public void removeEventListener(SimulationEventListener listener)
    {
        eventJournal.removeListener(listener);
    }

    /** Register a diagnostic-only post-decision transition observer. */
    public void addTransitionObservationListener(TransitionObservationListener listener)
    {
        if(listener != null && !transitionListeners.contains(listener)) {
            transitionListeners.add(listener);
        }
    }

    public void removeTransitionObservationListener(
        TransitionObservationListener listener)
    {
        transitionListeners.remove(listener);
    }

    /** Register a diagnostic-only R2 candidate-query observer. */
    public void addPositionCandidateObservationListener(
        PositionCandidateObservationListener listener)
    {
        if(listener != null && !positionCandidateListeners.contains(listener)) {
            positionCandidateListeners.add(listener);
        }
    }

    public void removePositionCandidateObservationListener(
        PositionCandidateObservationListener listener)
    {
        positionCandidateListeners.remove(listener);
    }

    /** Register a D2 read-only HUNT decision/result observer. */
    public void addHuntObservationListener(HuntObservationListener listener)
    {
        if(listener != null && !huntObservationListeners.contains(listener)) {
            huntObservationListeners.add(listener);
        }
    }

    public void removeHuntObservationListener(HuntObservationListener listener)
    {
        huntObservationListeners.remove(listener);
    }

    boolean hasHuntObservationListeners()
    {
        return !huntObservationListeners.isEmpty();
    }

    /** Register a read-only observer for complete immutable S0-S4 ledgers. */
    public void addStepFrameLedgerListener(StepFrameLedgerListener listener)
    {
        stepObservations.addFrameListener(listener);
    }

    public void removeStepFrameLedgerListener(StepFrameLedgerListener listener)
    {
        stepObservations.removeFrameListener(listener);
    }

    /** Register a V24-OBS-1A-only all-predator assessment sink. */
    public void addPredatorRecruitmentAssessmentListener(
        PredatorRecruitmentAssessmentListener listener)
    {
        stepObservations.addPredatorListener(listener);
    }

    public void removePredatorRecruitmentAssessmentListener(
        PredatorRecruitmentAssessmentListener listener)
    {
        stepObservations.removePredatorListener(listener);
    }

    /** Register a read-only V24-OBS-1B completed-step fact sink. */
    public void addLongHorizonTrophicStepListener(
        LongHorizonTrophicStepListener listener)
    {
        stepObservations.addTrophicListener(listener);
    }

    public void removeLongHorizonTrophicStepListener(
        LongHorizonTrophicStepListener listener)
    {
        stepObservations.removeTrophicListener(listener);
    }

    void finishLongHorizonTrophicStep(Field committed)
    {
        stepObservations.finishTrophicStep(committed, this);
    }

    boolean hasPredatorRecruitmentAssessmentListeners()
    {
        return stepObservations.hasPredatorListeners();
    }

    /**
     * Captures only values already available at S0. It is deliberately gated
     * by listener presence so observer-OFF performs no extra work or reads.
     */
    void recordPredatorRecruitmentS0(SavannahAnimal actor,
                                     StepFrameLedger.ResourceClock resourceClock,
                                     List<Location> decisionCandidates)
    {
        stepObservations.recordPredatorS0(actor, resourceClock,
            decisionCandidates, this);
    }

    void recordStepFrameLedger(StepFrameLedger ledger)
    {
        stepObservations.recordFrame(ledger, step);
    }

    /** Validates all step-start predator rows before releasing observer state. */
    void finishPredatorRecruitmentStep(java.util.Set<Long> expectedActorIds)
    {
        stepObservations.finishPredatorStep(expectedActorIds, this);
    }

    void requireCompleteStepFrameLedgers(java.util.Set<Long> expectedActorIds)
    {
        stepObservations.requireCompleteFrames(expectedActorIds);
    }

    public List<StepFrameLedger> getCurrentStepFrameLedgers()
    {
        return stepObservations.getCurrentFrames();
    }

    public void addBreedingRandomObservationListener(
        BreedingRandomObservationListener listener)
    {
        if(listener != null &&
           !breedingRandomObservationListeners.contains(listener)) {
            breedingRandomObservationListeners.add(listener);
        }
    }

    public void removeBreedingRandomObservationListener(
        BreedingRandomObservationListener listener)
    {
        breedingRandomObservationListeners.remove(listener);
    }

    void recordBreedingRandomObservation(
        BreedingRandomObservation observation)
    {
        if(observation == null) return;
        for(BreedingRandomObservationListener listener :
            breedingRandomObservationListeners) {
            listener.onBreedingRandomObservation(observation);
        }
    }

    void recordHuntDecisionObservation(HuntDecisionObservation observation)
    {
        if(observation == null) {
            return;
        }
        for(HuntObservationListener listener : huntObservationListeners) {
            listener.onHuntDecision(observation);
        }
    }

    void recordHuntObservation(HuntObservation observation)
    {
        if(observation == null) {
            return;
        }
        for(HuntObservationListener listener : huntObservationListeners) {
            listener.onHuntResult(observation);
        }
    }

    public void addDecisionBirthCandidateFactListener(
        DecisionBirthCandidateFactListener listener)
    {
        if(listener != null &&
           !decisionBirthCandidateFactListeners.contains(listener)) {
            decisionBirthCandidateFactListeners.add(listener);
        }
    }

    public void removeDecisionBirthCandidateFactListener(
        DecisionBirthCandidateFactListener listener)
    {
        decisionBirthCandidateFactListeners.remove(listener);
    }

    boolean hasDecisionBirthCandidateFactListeners()
    {
        return !decisionBirthCandidateFactListeners.isEmpty();
    }

    void recordDecisionBirthCandidateFact(DecisionBirthCandidateFact fact)
    {
        if(fact == null) return;
        for(DecisionBirthCandidateFactListener listener :
            decisionBirthCandidateFactListeners) {
            listener.onDecisionBirthCandidates(fact);
        }
    }

    private final Map<BehaviorReason, Integer> socialMoveCounts =
        new EnumMap<>(BehaviorReason.class);

    /** Run totals of M2 movement decisions (threat avoidance, egress). */
    void recordSocialMove(BehaviorReason reason)
    {
        if(reason != null) socialMoveCounts.merge(reason, 1, Integer::sum);
    }

    public Map<BehaviorReason, Integer> getSocialMoveCounts()
    {
        return Collections.unmodifiableMap(new EnumMap<>(socialMoveCounts));
    }

    /**
     * D6: after a kill the mother shares food with each dependent cub within
     * the provisioning radius, never going below her maternal floor. The
     * transfer is conserved and recorded in the birth account.
     */
    void provisionDependents(SavannahAnimal mother)
    {
        if(mother == null || currentStepField == null || mother.getLocation() == null) {
            return;
        }
        List<SavannahAnimal> dependents = new ArrayList<>();
        // isDependentOf() requires a predator, so only the step's predators can qualify;
        // the result is sorted by id below, so the scan order does not matter.
        for(SavannahAnimal young : stepPredators()) {
            Location location = young.getLocation();
            if(young.isDependentOf(mother) && location != null &&
               Math.max(Math.abs(location.row() - mother.getLocation().row()),
                        Math.abs(location.col() - mother.getLocation().col())) <=
                   EcologyParameters.current().getInt(
                       EcologyParameters.Key.PROVISION_RADIUS)) {
                dependents.add(young);
            }
        }
        dependents.sort((first, second) -> Long.compare(first.getId(), second.getId()));
        int floor = ReproductionPolicy.maternalFloor(mother.getProfile());
        int cap = ReproductionPolicy.childReserveFood(mother.getProfile());
        for(SavannahAnimal young : dependents) {
            int need = young.getProfile().getMaxFoodLevel() - young.getFoodLevel();
            int share = Math.min(cap, Math.min(need, mother.getFoodLevel() - floor));
            if(share <= 0) break;
            mother.transferFoodTo(young, share);
            birthAccount.recordProvision(share);
        }
    }

    void recordBirthTransaction(BirthResourceTransactionResult result)
    {
        birthAccount.record(result);
    }

    /** Run-wide production birth resource totals (plan v3.0 M1-2). */
    public BirthResourceAccount getBirthAccount()
    {
        return birthAccount;
    }

    void recordCommittedBirthFact(SavannahAnimal parent,
                                  SavannahAnimal child,
                                  int litterOrdinal,
                                  Location location)
    {
        if(child == null || !child.isAlive() ||
           !committedBirthPopulationIds.add(child.getId())) {
            throw new IllegalStateException(
                "each committed live birth must update population exactly once");
        }
        incrementLivePopulation(child.getProfile().getName());
    }

    void recordPositionCandidateObservation(
        PositionCandidateObservation observation)
    {
        if(observation == null) {
            return;
        }
        for(PositionCandidateObservationListener listener :
                positionCandidateListeners) {
            listener.onPositionCandidates(observation);
        }
    }

    void recordTransitionObservation(TransitionObservation observation)
    {
        if(observation == null) {
            return;
        }
        for(TransitionObservationListener listener : transitionListeners) {
            listener.onTransition(observation);
        }
    }

    public List<SimulationEvent> getCurrentEvents()
    {
        return eventJournal.getCurrentEvents();
    }

    /**
     * Returns whether this actor produced an event at or after an exact current
     * event-list boundary.  The index is reset every step and is observational;
     * it never influences ecological decisions or random use.
     */
    boolean hasCurrentActorEventSince(int eventIndex,
                                      SimulationEvent.EventType type,
                                      long actorId)
    {
        return eventJournal.hasCurrentActorEventSince(eventIndex, type, actorId);
    }

    public List<SimulationEvent> getPreviousEvents()
    {
        return eventJournal.getPreviousEvents();
    }

    public List<SimulationEvent> getRecentEvents()
    {
        return eventJournal.getRecentEvents();
    }

    /** Return immutable complete facts for this producer-defined frame interval. */
    public FrameEventSummary getFrameEventSummary()
    {
        return eventJournal.getFrameEventSummary();
    }

    public int getStep()
    {
        return step;
    }

    public Random getRandom()
    {
        return random;
    }

    public SimulationClock getClock()
    {
        return clock;
    }

    public ResolvedRuntimeConfig getRuntimeConfig()
    {
        return runtimeConfig;
    }

    public WeatherSystem getWeatherSystem()
    {
        return weatherSystem;
    }

    public DiseaseSystem getDiseaseSystem()
    {
        return diseaseSystem;
    }

    public PredationSystem getPredationSystem()
    {
        return predationSystem;
    }

    public BreedingSystem getBreedingSystem()
    {
        return breedingSystem;
    }

    public FoodSystem getFoodSystem()
    {
        return foodSystem;
    }

    public ThirstSystem getThirstSystem()
    {
        return thirstSystem;
    }

    public WaterholeCongestionAssessment getWaterholeCongestionAssessment()
    {
        return waterholeCongestionAssessment;
    }

    WaterTopology getWaterTopology() { return waterTopology; }

    /**
     * Atomically reserves one critical-water destination and the claimant's
     * origin for this proposal phase.  Earlier claims keep ownership when a
     * later claimant conflicts; a claimant may retarget only to an unclaimed
     * cell, and its old target is then released.
     */
    public boolean reserveCriticalWaterMove(SavannahAnimal animal, Location destination)
    {
        if(animal == null || destination == null) return false;
        long animalId = animal.getId();
        Location origin = animal.getLocation();
        if(isCriticalWaterReservedForOther(destination, animalId) ||
           (origin != null && isCriticalWaterReservedForOther(origin, animalId))) {
            return false;
        }

        Location previousDestination = criticalWaterReservations.get(animalId);
        if(previousDestination != null) {
            releaseCriticalWaterCell(previousDestination, animalId);
        }
        criticalWaterReservations.put(animalId, destination);
        criticalWaterReservedCells.put(destination, animalId);
        if(origin != null) criticalWaterReservedCells.put(origin, animalId);
        return true;
    }

    public Location getCriticalWaterReservation(long animalId)
    {
        return criticalWaterReservations.get(animalId);
    }

    public boolean isCriticalWaterReservedForOther(Location location, long animalId)
    {
        Long owner = criticalWaterReservedCells.get(location);
        return owner != null && owner.longValue() != animalId;
    }

    private void releaseCriticalWaterCell(Location location, long animalId)
    {
        Long owner = criticalWaterReservedCells.get(location);
        if(owner != null && owner.longValue() == animalId) {
            criticalWaterReservedCells.remove(location);
        }
    }

    /** Immutable critical-water reservation facts for one proposal view. */
    public Map<Location, Long> getCriticalWaterReservedCells()
    {
        Map<Location, Long> result = new java.util.HashMap<>();
        for(Map.Entry<Location, Long> entry : criticalWaterReservedCells.entrySet()) {
            Location location = entry.getKey();
            if(location != null) {
                result.put(new Location(location.row(), location.col()),
                    entry.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /** Lazily creates the one immutable position topology for this terrain. */
    public PositionTopology getPositionTopology()
    {
        if(positionTopology == null) {
            positionTopology = PositionTopology.fromTerrain(terrainMap,
                PositionKernelPolicyRegistry.current());
        }
        return positionTopology;
    }

    public PositionAvailabilityView createPositionAvailabilityView(
        StepStartPositionSnapshot snapshot)
    {
        return new PositionAvailabilityView(snapshot, getPositionTopology(),
            getCriticalWaterReservedCells(), step,
            positionCandidateListeners.isEmpty() ? null
                : this::recordPositionCandidateObservation);
    }

    public int getPositionWaitingDebt(long animalId)
    {
        Integer value = positionWaitingDebt.get(Long.valueOf(animalId));
        return value == null ? 0 : value.intValue();
    }

    /** Replaces debt facts with the surviving adults of one committed plan. */
    void reconcilePositionWaitingDebt(PositionPlan plan, Field committedField)
    {
        positionWaitingDebt.clear();
        if(plan == null || committedField == null) {
            return;
        }
        for(Animal animal : committedField.getAnimals()) {
            if(!(animal instanceof SavannahAnimal) || !animal.isAlive()) {
                continue;
            }
            long id = animal.getId();
            PositionActorKey key = ((SavannahAnimal)animal).getPositionActorKey();
            PositionResolution resolution = plan.getResolution(key);
            if(resolution != null && resolution.isCommitted()) {
                positionWaitingDebt.put(Long.valueOf(id), Integer.valueOf(
                    plan.nextWaitingDebtFor(key)));
            }
        }
    }

    void recordPositionPlan(PositionPlan plan)
    {
        lastPositionPlan = plan;
    }

    /** Latest fully validated R2 plan, or null before a step has committed. */
    public PositionPlan getLastPositionPlan()
    {
        return lastPositionPlan;
    }

    public boolean isThirstEnabled()
    {
        return thirstSystem != null;
    }

    public SurvivalSystem getSurvivalSystem()
    {
        return survivalSystem;
    }

    public BehaviorProvider getThirstBehaviorProvider()
    {
        return thirstBehaviorProvider;
    }

    public BehaviorProvider getFoodBehaviorProvider()
    {
        return foodBehaviorProvider;
    }

    public MovementBehaviorProvider getMovementBehaviorProvider()
    {
        return movementBehaviorProvider;
    }

    public BehaviorProvider getBreedingBehaviorProvider()
    {
        return breedingBehaviorProvider;
    }

    public BehaviorResolver getBehaviorResolver()
    {
        return behaviorResolver;
    }

    public ActionExecutor getActionExecutor()
    {
        return actionExecutor;
    }

    /** Return the single reservation coordinator for this step's target field. */
    public FieldTransition getFieldTransition(Field targetField)
    {
        if(targetField == null) {
            throw new IllegalArgumentException("target field is required");
        }
        if(fieldTransition == null ||
           fieldTransition.getTargetField() != targetField) {
            fieldTransition = new FieldTransition(targetField, terrainMap, this);
        }
        return fieldTransition;
    }

    public SurvivalAssessment assessSurvival(SavannahAnimal animal)
    {
        return survivalSystem.assess(animal, this);
    }

    public TerrainMap getTerrainMap()
    {
        return terrainMap;
    }

    /** The one immutable spatial-fact view captured at this step start. */
    public SpatialEcologyView getSpatialEcologyView()
    {
        if(spatialEcologyView == null) {
            throw new IllegalStateException(
                "spatial ecology is unavailable before startStep");
        }
        return spatialEcologyView;
    }

    /** Cumulative startStep capture count for transparency evidence. */
    public int getSpatialEcologyCaptureCount()
    {
        return spatialEcologyCaptureCount;
    }

    /** Current SOC1-aF registry and raw local-capacity facts. */
    public Soc1aFactView getSoc1aFactView()
    {
        if(soc1aFactView == null) {
            throw new IllegalStateException(
                "SOC1-aF facts are unavailable before startStep");
        }
        return soc1aFactView;
    }

    public SpeciesEcologyFactRegistry getSpeciesEcologyFactRegistry()
    {
        return speciesEcologyFactRegistry;
    }

    public SimulationConfig getConfig()
    {
        return config;
    }

    public RenderProvenance getRenderProvenance()
    {
        return renderProvenance;
    }

    public double getBreedingProbability(SpeciesProfile profile)
    {
        return config.breedingProbability(profile);
    }

    public int getFoundingPopulation(SpeciesProfile profile)
    {
        return config.foundingPopulation(profile);
    }

    public int getPopulation(String speciesName)
    {
        Integer count = populationSnapshot == null ? null :
            populationSnapshot.get(speciesName);
        return count == null ? 0 : count;
    }

    /** Immutable step-start population used by compatibility decision paths. */
    public int getStepStartPopulation(String speciesName)
    {
        Integer count = populationSnapshot == null ? null :
            populationSnapshot.get(speciesName);
        return count == null ? 0 : count;
    }

    /** Live committed count for observation/reporting; never drives ecology. */
    public int getLivePopulation(String speciesName)
    {
        Integer count = livePopulationBySpecies == null ? null :
            livePopulationBySpecies.get(speciesName);
        return count == null ? 0 : count;
    }

    private void decrementLivePopulation(String speciesName)
    {
        if(livePopulationBySpecies == null || speciesName == null) return;
        int current = livePopulationBySpecies.getOrDefault(speciesName, 0);
        if(current <= 0) {
            throw new IllegalStateException(
                "death population update has no live species count: " + speciesName);
        }
        livePopulationBySpecies.put(speciesName, current - 1);
    }

    private void incrementLivePopulation(String speciesName)
    {
        if(livePopulationBySpecies == null || speciesName == null) return;
        livePopulationBySpecies.put(speciesName,
            livePopulationBySpecies.getOrDefault(speciesName, 0) + 1);
    }

    public String getEnvironmentSummary(Field field)
    {
        int infected = diseaseSystem.countInfected(field);
        int grassPercent = (int)Math.round(foodSystem.getAverageFoodLevel() * 100);
        return "Weather: " + weatherSystem.getDisplayText() +
               " | " + clock.getDisplayText() +
               " | Grass: " + grassPercent + "%" +
               " | Diseased: " + infected +
               " | Thirst: " + (isThirstEnabled() ? "on" : "off");
    }
}
