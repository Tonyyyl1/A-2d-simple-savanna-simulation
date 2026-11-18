package savanna;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The sole R2 production owner of one step's position proposal, resolution,
 * and commit flow.
 *
 * <p>No projected {@link Field} is exposed while proposals are collected.
 * Sequential actions operate on the old field, placements become typed claims,
 * and a new field is populated only after the pure resolver has validated one
 * complete board.</p>
 */
public final class PositionStepCoordinator
{
    /** Farthest cell the kernel's fallback search (budget 12) can assign: 3 steps at a corner. */
    static final int KERNEL_FALLBACK_REACH = 3;

    private final Field currentField;
    private final SimulationContext context;
    private final StepStartPositionSnapshot snapshot;
    private final InteractionGeometry interactionGeometry;
    private final Map<Long, SavannahAnimal> adultsById;
    private final Map<Long, SavannahAnimal.PositionProposal> proposalsById;

    public PositionStepCoordinator(Field currentField, SimulationContext context)
    {
        if(currentField == null || context == null) {
            throw new IllegalArgumentException("field and context are required");
        }
        if(RuntimeAudit.enabled() && !currentField.hasConsistentState()) {
            throw new IllegalStateException(
                "R2 cannot capture an inconsistent step-start field");
        }
        this.currentField = currentField;
        this.context = context;
        adultsById = captureAdults(currentField);
        snapshot = snapshotFor(adultsById, currentField.getDepth(),
            currentField.getWidth());
        interactionGeometry = StepStartInteractionGeometry.fromSpatialView(
            context.getSpatialEcologyView(), adultsById);
        proposalsById = new LinkedHashMap<>();
    }

    public StepStartPositionSnapshot getSnapshot()
    {
        return snapshot;
    }

    /** One immutable geometry shared by all R2 food and mate reads this step. */
    public InteractionGeometry getInteractionGeometry()
    {
        return interactionGeometry;
    }

    /** Builds the current read-only availability view after water reservations. */
    public PositionAvailabilityView createAvailabilityView()
    {
        return context.createPositionAvailabilityView(snapshot);
    }

    /** Collects an actor's sequential action effects and deferred claims. */
    public void propose(Animal actor, PositionAvailabilityView availability)
    {
        if(!(actor instanceof SavannahAnimal)) {
            throw new IllegalStateException(
                "R2 position board requires SavannahAnimal actors only");
        }
        SavannahAnimal animal = (SavannahAnimal)actor;
        if(!snapshot.containsAdult(animal.getId())) {
            return;
        }
        if(proposalsById.containsKey(animal.getId())) {
            throw new IllegalStateException("Adult proposed twice: " +
                animal.getId());
        }
        proposalsById.put(animal.getId(), animal.proposePositionStep(
            currentField, interactionGeometry, availability, context,
            snapshot.originOf(animal.getId())));
    }

    /**
     * Legacy/local adapter: resolves the submitted proposals and supplies
     * origin-stay claims for omitted adults. Controlled R2 fixtures use this
     * partial-board surface deliberately.
     */
    public Field resolveAndCommit()
    {
        return resolveAndCommit(false);
    }

    /** Production boundary: every step-start actor must publish one S4 ledger. */
    Field resolveAndCommitCompleteStep()
    {
        return resolveAndCommit(true);
    }

    private Field resolveAndCommit(boolean requireCompleteFrame)
    {
        PositionClaimBatch.Builder batchBuilder =
            PositionClaimBatch.builder(snapshot.size());
        Map<PositionActorKey, PositionClaim> spawnClaims = new LinkedHashMap<>();
        for(long adultId : snapshot.getAdultIds()) {
            SavannahAnimal animal = adultsById.get(adultId);
            PositionActorKey actor = adultKey(adultId, animal);
            SavannahAnimal.PositionProposal proposal = proposalsById.get(adultId);
            if(animal == null || !animal.isAlive()) {
                batchBuilder.release(actor);
                cancelSpawnClaims(proposal, batchBuilder);
                continue;
            }
            if(proposal == null || !proposal.hasAdultClaim()) {
                batchBuilder.add(PositionClaim.stay(actor,
                    PositionPriority.ORDINARY_MOVE,
                    context.getPositionWaitingDebt(adultId)));
            }
            else {
                batchBuilder.add(proposal.getAdultClaim());
            }
            addLiveSpawnClaims(proposal, spawnClaims, batchBuilder);
        }
        PositionClaimBatch batch = batchBuilder.build();
        PositionTopology topology = context.getPositionTopology();
        PositionPlan plan = new PurePositionReservationResolver(
            PositionKernelPolicyRegistry.current()).resolve(snapshot, batch,
            topology, Collections.<Location>emptySet(), context.getStep(),
            context.getPositionResolverScratch());
        if(RuntimeAudit.enabled()) {
            PositionPlanValidator.requireValid(snapshot, plan, topology,
                Collections.<Location>emptySet(), batch.getReleased(),
                adultId -> {
                    SavannahAnimal mover = adultsById.get(adultId);
                    // A crowded mover that loses every nearer cell is placed by the kernel's
                    // 12-cell search budget, which reaches 3 cells at a map corner; that is
                    // not a teleport.
                    return mover == null ? KERNEL_FALLBACK_REACH
                        : Math.max(mover.maxMoveDistance(), KERNEL_FALLBACK_REACH);
                });
        }

        PositionCommitReservationBoard reservations =
            new PositionCommitReservationBoard(currentField.getDepth(),
                currentField.getWidth(), snapshot.size());
        Map<Long, Location> adultDestinations = new LinkedHashMap<>();
        reserveAdults(reservations, plan, batch, adultDestinations);
        reserveWinningSpawns(reservations, plan, spawnClaims);

        Field committed = new Field(currentField.getDepth(),
                                    currentField.getWidth());
        commitAdults(committed, plan, adultDestinations);

        Map<Long, Integer> successfulBirths = new LinkedHashMap<>();
        Map<Long, Location> newbornDestinations = new LinkedHashMap<>();
        commitWinningSpawns(committed, reservations, plan, spawnClaims,
            successfulBirths, newbornDestinations);
        applyConceptions();
        resolveDuePregnancies();
        completeBehaviorOutcomes(plan, successfulBirths);
        if(requireCompleteFrame) {
            if(RuntimeAudit.enabled()) {
                context.requireCompleteStepFrameLedgers(
                    new LinkedHashSet<>(snapshot.getAdultIds()));
            }
            context.finishPredatorRecruitmentStep(
                new LinkedHashSet<>(snapshot.getAdultIds()));
        }

        if(RuntimeAudit.enabled()) {
            validateCommit(committed, plan, batch.getReleased(), adultDestinations,
                newbornDestinations);
        }
        context.recordPositionPlan(plan);
        context.reconcilePositionWaitingDebt(plan, committed);
        context.finishLongHorizonTrophicStep(committed);
        return committed;
    }

    private static Map<Long, SavannahAnimal> captureAdults(Field field)
    {
        Map<Long, SavannahAnimal> result = new LinkedHashMap<>();
        for(Animal actor : field.getAnimals()) {
            if(!actor.isAlive()) {
                continue;
            }
            if(!(actor instanceof SavannahAnimal)) {
                throw new IllegalStateException(
                    "Living field actor is not a SavannahAnimal: " + actor);
            }
            SavannahAnimal animal = (SavannahAnimal)actor;
            if(result.put(animal.getId(), animal) != null) {
                throw new IllegalStateException("Duplicate living adult ID: " +
                    animal.getId());
            }
        }
        return result;
    }

    private static StepStartPositionSnapshot snapshotFor(
        Map<Long, SavannahAnimal> adults, int depth, int width)
    {
        Map<Long, Location> origins = new LinkedHashMap<>();
        for(Map.Entry<Long, SavannahAnimal> entry : adults.entrySet()) {
            Location location = entry.getValue().getLocation();
            if(location == null) {
                throw new IllegalStateException("Living adult has no origin: " +
                    entry.getKey());
            }
            origins.put(entry.getKey(), new Location(location.row(),
                location.col()));
        }
        return StepStartPositionSnapshot.of(origins, depth, width,
            id -> adultKey(id.longValue(), adults.get(id)));
    }

    private void addLiveSpawnClaims(SavannahAnimal.PositionProposal proposal,
                                    Map<PositionActorKey, PositionClaim> claims,
                                    PositionClaimBatch.Builder batchBuilder)
    {
        if(proposal == null || proposal.getAnimal() == null ||
           !proposal.getAnimal().isAlive()) {
            cancelSpawnClaims(proposal, batchBuilder);
            return;
        }
        for(PositionClaim claim : proposal.getSpawnClaims()) {
            if(claims.put(claim.getActor(), claim) != null) {
                throw new IllegalStateException("Duplicate spawn token: " +
                    claim.getActor());
            }
            batchBuilder.add(claim);
        }
    }

    private static void cancelSpawnClaims(SavannahAnimal.PositionProposal proposal,
                                          PositionClaimBatch.Builder batchBuilder)
    {
        if(proposal == null) {
            return;
        }
        for(PositionClaim claim : proposal.getSpawnClaims()) {
            batchBuilder.cancel(claim.getActor());
        }
    }

    private void reserveAdults(PositionCommitReservationBoard reservations,
                               PositionPlan plan,
                               PositionClaimBatch batch,
                               Map<Long, Location> adultDestinations)
    {
        List<Long> adultIds = snapshot.getAdultIds();
        List<PositionActorKey> adultKeys = snapshot.getAdultKeys();
        for(int adultIndex = 0; adultIndex < adultIds.size(); adultIndex++) {
            long adultId = adultIds.get(adultIndex).longValue();
            SavannahAnimal animal = adultsById.get(adultId);
            PositionActorKey actor = adultKeys.get(adultIndex);
            PositionResolution resolution = plan.getResolution(actor);
            if(batch.isReleased(actor)) {
                if(animal != null && animal.isAlive()) {
                    throw new IllegalStateException(
                        "Live adult unexpectedly released: " + actor);
                }
                continue;
            }
            if(animal == null || !animal.isAlive() || resolution == null ||
               !resolution.isCommitted()) {
                throw new IllegalStateException(
                    "Living adult lacks a committed R2 destination: " + actor);
            }
            Location destination = resolution.getDestination();
            if(!reservations.tryReserveAdult(adultIndex, destination)) {
                throw new IllegalStateException(
                    "Validated R2 plan could not reserve adult destination: " +
                    actor + '@' + destination);
            }
            adultDestinations.put(adultId, destination);
        }
    }

    private static void reserveWinningSpawns(
        PositionCommitReservationBoard reservations, PositionPlan plan,
        Map<PositionActorKey, PositionClaim> claims)
    {
        for(PositionActorKey actor : claims.keySet()) {
            PositionResolution resolution = plan.getResolution(actor);
            if(resolution != null && resolution.isCommitted() &&
               !reservations.tryReserve(actor, resolution.getDestination())) {
                throw new IllegalStateException(
                    "Validated R2 plan could not reserve spawn destination: " +
                    actor + '@' + resolution.getDestination());
            }
        }
    }

    private void commitAdults(Field committed, PositionPlan plan,
                              Map<Long, Location> adultDestinations)
    {
        for(Map.Entry<Long, Location> entry : adultDestinations.entrySet()) {
            long adultId = entry.getKey();
            SavannahAnimal animal = adultsById.get(adultId);
            PositionActorKey actor = adultKey(adultId, animal);
            PositionResolution resolution = plan.getResolution(actor);
            Location origin = snapshot.originOf(adultId);
            Location destination = entry.getValue();
            animal.commitResolvedPosition(origin, destination, context);
            committed.placeAnimal(animal, destination);
            context.recordTransitionObservation(new TransitionObservation(adultId,
                origin, resolution.getRequested(), destination,
                adultStatus(origin, destination, resolution),
                resolution.getCandidateSource() ==
                    PositionCandidateSource.SEARCH_FALLBACK,
                context.getStep()));
        }
    }

    private void commitWinningSpawns(Field committed,
                                     PositionCommitReservationBoard reservations,
                                     PositionPlan plan,
                                     Map<PositionActorKey, PositionClaim> claims,
                                     Map<Long, Integer> successfulBirths,
                                     Map<Long, Location> newbornDestinations)
    {
        for(Map.Entry<PositionActorKey, PositionClaim> entry : claims.entrySet()) {
            PositionActorKey actor = entry.getKey();
            PositionClaim claim = entry.getValue();
            PositionResolution resolution = plan.getResolution(actor);
            if(resolution == null || !resolution.isCommitted()) {
                continue;
            }
            SavannahAnimal parent = adultsById.get(actor.getParentId());
            if(parent == null || !parent.isAlive()) {
                throw new IllegalStateException(
                    "Winning spawn has no living parent at commit: " + actor);
            }
            Location destination = resolution.getDestination();
            if(!destination.equals(reservations.destinationOf(actor))) {
                throw new IllegalStateException(
                    "Spawn destination differs from private reservation: " +
                    actor);
            }
            ReproductionState reproduction = parent.getReproductionState();
            int ordinal = actor.getLitterOrdinal();
            if(!reproduction.isDue(context.getStep()) ||
               !reproduction.getPendingOrdinals().contains(ordinal)) {
                throw new IllegalStateException(
                    "Winning spawn has no due pending pregnancy ordinal: " + actor);
            }
            SavannahAnimal child = parent.createChild(destination);
            // Plan v3.0 M1-2: the newborn holds exactly its ordinal escrow.
            child.initializeNewborn(reproduction.escrowOf(ordinal));
            child.setMotherId(parent.getId());
            context.recordBirthTransaction(reproduction.commitOrdinal(ordinal));
            committed.placeAnimal(child, destination);
            context.recordCommittedBirthFact(parent, child,
                actor.getLitterOrdinal(), destination);
            newbornDestinations.put(child.getId(), destination);
            successfulBirths.put(parent.getId(), Integer.valueOf(
                successfulBirths.getOrDefault(parent.getId(), Integer.valueOf(0))
                    .intValue() + 1));
            context.recordTransitionObservation(new TransitionObservation(
                child.getId(), null, claim.getRequested(), destination,
                TransitionStatus.SPAWNED, false, context.getStep()));
            context.recordEvent(SimulationEvent.birth(context.getStep(), parent,
                child, destination));
        }
    }

    /** Applies S1 conception decisions: maternal debit into ordinal escrow. */
    private void applyConceptions()
    {
        for(long adultId : snapshot.getAdultIds()) {
            SavannahAnimal mother = adultsById.get(adultId);
            SavannahAnimal.PositionProposal proposal = proposalsById.get(adultId);
            if(mother == null || !mother.isAlive() || proposal == null) continue;
            StepFrameLedger.ReproductionProposal reproduction =
                proposal.getFrameReproduction();
            if(reproduction == null || reproduction.getReason() !=
               StepFrameLedger.ProposalReason.CONCEPTION) continue;
            // S1 judged affordability on step-start food; a later same-step
            // provisioning transfer may have lowered it, so re-check here.
            int litter = Math.min(reproduction.getConceptionLitter(),
                ReproductionPolicy.affordableLitter(mother.getProfile(),
                    mother.getFoodLevel()));
            if(litter < 1) continue;
            BirthResourceTransactionResult result = mother.getReproductionState()
                .conceive(mother, context.getStep(), litter);
            mother.payReproductionFood((long)litter *
                ReproductionPolicy.childReserveFood(mother.getProfile()));
            mother.spendBreedingStamina();
            context.recordBirthTransaction(result);
        }
    }

    /** Expires over-long birth waits and closes fully resolved pregnancies. */
    private void resolveDuePregnancies()
    {
        int step = context.getStep();
        for(long adultId : snapshot.getAdultIds()) {
            SavannahAnimal mother = adultsById.get(adultId);
            if(mother == null || !mother.isAlive()) continue;
            ReproductionState reproduction = mother.getReproductionState();
            if(!reproduction.isDue(step)) continue;
            if(step - reproduction.getDueStep() >=
               ReproductionPolicy.MAX_BIRTH_WAIT_STEPS) {
                for(BirthResourceTransactionResult result :
                        reproduction.loseAllPending()) {
                    context.recordBirthTransaction(result);
                }
            }
            reproduction.closeIfResolved(step, mother.getProfile());
        }
    }

    private void completeBehaviorOutcomes(PositionPlan plan,
                                          Map<Long, Integer> successfulBirths)
    {
        for(long adultId : snapshot.getAdultIds()) {
            SavannahAnimal.PositionProposal proposal = proposalsById.get(adultId);
            if(proposal == null) {
                continue;
            }
            proposal.complete(plan,
                plan.getResolution(adultKey(adultId, adultsById.get(adultId))),
                successfulBirths.containsKey(adultId), context);
        }
    }

    /** The animal's cached adult key, or a fresh one when the ID has no animal (released). */
    private static PositionActorKey adultKey(long adultId, SavannahAnimal animal)
    {
        return animal != null && animal.getId() == adultId
            ? animal.getPositionActorKey() : PositionActorKey.adult(adultId);
    }

    private void validateCommit(Field committed, PositionPlan plan,
                                Set<PositionActorKey> released,
                                Map<Long, Location> adultDestinations,
                                Map<Long, Location> newbornDestinations)
    {
        if(!committed.hasConsistentState()) {
            throw new IllegalStateException(
                "R2 commit produced field/list mismatch");
        }
        Set<Long> expectedIds = new LinkedHashSet<>();
        expectedIds.addAll(adultDestinations.keySet());
        expectedIds.addAll(newbornDestinations.keySet());
        Set<Long> observedIds = new LinkedHashSet<>();
        Set<Location> observedLocations = new LinkedHashSet<>();
        for(Animal actor : committed.getAnimals()) {
            if(!(actor instanceof SavannahAnimal) || !actor.isAlive() ||
               !observedIds.add(actor.getId()) ||
               !observedLocations.add(actor.getLocation())) {
                throw new IllegalStateException(
                    "R2 commit has duplicate or invalid living actor facts");
            }
            if(!expectedIds.contains(actor.getId())) {
                throw new IllegalStateException(
                    "R2 commit contains an unledgered living actor: " +
                    actor.getId());
            }
        }
        if(!observedIds.equals(expectedIds)) {
            throw new IllegalStateException(
                "R2 commit lost or added a living actor outside the plan");
        }
        for(Map.Entry<Long, Location> entry : adultDestinations.entrySet()) {
            SavannahAnimal animal = adultsById.get(entry.getKey());
            if(animal == null || !entry.getValue().equals(animal.getLocation())) {
                throw new IllegalStateException(
                    "R2 adult location differs from committed ledger: " +
                    entry.getKey());
            }
        }
        for(PositionActorKey actor : released) {
            SavannahAnimal animal = adultsById.get(actor.getAdultId());
            if(animal != null && animal.isAlive()) {
                throw new IllegalStateException(
                    "Released origin remained alive: " + actor);
            }
            PositionResolution resolution = plan.getResolution(actor);
            if(resolution == null || resolution.getDisposition() !=
               PositionPlanDisposition.RELEASED) {
                throw new IllegalStateException(
                    "Released origin has no released plan fact: " + actor);
            }
        }
    }

    private static TransitionStatus adultStatus(Location origin,
                                                Location destination,
                                                PositionResolution resolution)
    {
        if(origin.equals(destination)) {
            return TransitionStatus.STAYED;
        }
        return resolution.getCandidateSource() ==
            PositionCandidateSource.REQUESTED ? TransitionStatus.MOVED
                                               : TransitionStatus.REROUTED;
    }

    /**
     * Private, non-observable ownership board used only between pure plan
     * validation and final Field materialization. It deliberately retains no
     * Field or Animal reference.
     */
    private static final class PositionCommitReservationBoard
    {
        private final int depth;
        private final int width;
        /** Per cell: true once a destination has been reserved there. */
        private final boolean[] reservedCells;
        /** Per adult (position in the snapshot's adult list): true once reserved. */
        private final boolean[] reservedAdults;
        /** Spawn actors are few, so they keep a small map (get and put only). */
        private final Map<PositionActorKey, Location> spawnDestinations;

        PositionCommitReservationBoard(int depth, int width, int adultCount)
        {
            if(depth <= 0 || width <= 0) {
                throw new IllegalArgumentException(
                    "reservation board dimensions must be positive");
            }
            this.depth = depth;
            this.width = width;
            reservedCells = new boolean[depth * width];
            reservedAdults = new boolean[adultCount];
            spawnDestinations = new HashMap<>();
        }

        /** Reserves the destination of the adult at {@code adultIndex} in the snapshot. */
        boolean tryReserveAdult(int adultIndex, Location location)
        {
            if(location == null || !inside(location) || reservedAdults[adultIndex] ||
               reservedCells[location.row() * width + location.col()]) {
                return false;
            }
            reservedAdults[adultIndex] = true;
            reservedCells[location.row() * width + location.col()] = true;
            return true;
        }

        boolean tryReserve(PositionActorKey actor, Location location)
        {
            if(actor == null || location == null || !inside(location) ||
               spawnDestinations.containsKey(actor) ||
               reservedCells[location.row() * width + location.col()]) {
                return false;
            }
            spawnDestinations.put(actor,
                new Location(location.row(), location.col()));
            reservedCells[location.row() * width + location.col()] = true;
            return true;
        }

        /** The destination reserved for a spawn actor, or null. */
        Location destinationOf(PositionActorKey actor)
        {
            return spawnDestinations.get(actor);
        }

        private boolean inside(Location location)
        {
            return location.row() >= 0 && location.row() < depth &&
                   location.col() >= 0 && location.col() < width;
        }
    }
}
