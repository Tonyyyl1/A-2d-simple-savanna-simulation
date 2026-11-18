package savanna;

import java.util.List;
import java.util.Collections;

/** Executes only the actions migrated to the v1.7 pipeline. */
public final class ActionExecutor
{
    /** @return true when the intent completed an immediate action. */
    public boolean executeImmediate(BehaviorIntent intent, SavannahAnimal animal,
                                    SimulationContext context)
    {
        if(intent == null || animal == null || context == null ||
           intent.getAnimalId() != animal.getId()) {
            return false;
        }
        if(intent.getActionType() == ActionType.DRINK && context.isThirstEnabled()) {
            return context.getThirstSystem().tryDrink(animal, context);
        }
        return false;
    }

    /**
     * Executes the established food action without choosing a second primary
     * action. A null grazing list means graze at the current location.
     */
    public Location executeFoodAction(BehaviorIntent intent,
                                      SavannahAnimal animal,
                                      InteractionGeometry geometry,
                                      List<Location> grazingLocations,
                                      SimulationContext context)
    {
        if(intent == null || animal == null || geometry == null ||
           context == null || intent.getAnimalId() != animal.getId()) {
            return null;
        }
        HuntOpportunityFact opportunity = HuntOpportunityFact.capture(
            context.getStep(), animal,
            StepFrameLedger.frameId(context.getStep(), animal.getId()),
            geometry, context);
        return executePrimaryFoodAction(intent, animal, geometry,
            grazingLocations, context, opportunity).getDestination();
    }

    /** Executes S2 and returns the typed fact joined to the exact S0 frame. */
    public StepFrameLedger.PrimaryAction executePrimaryFoodAction(
        BehaviorIntent intent, SavannahAnimal animal,
        InteractionGeometry geometry, List<Location> grazingLocations,
        SimulationContext context, HuntOpportunityFact opportunity)
    {
        if(animal == null || geometry == null || context == null ||
           opportunity == null || opportunity.getActorId() != animal.getId() ||
           opportunity.getStep() != context.getStep()) {
            throw new IllegalArgumentException(
                "S2 requires actor, geometry, context and matching S0 opportunity");
        }
        String frameId = opportunity.getFrameId();
        if(intent == null || intent.getAnimalId() != animal.getId()) {
            return StepFrameLedger.PrimaryAction.skipped(frameId,
                context.getStep(), animal, intent == null ? null :
                intent.getActionType());
        }
        if(intent.getActionType() == ActionType.HUNT) {
            int eventCount = context.getCurrentEvents().size();
            int predationDeaths = context.getDeathCount(DeathCause.PREDATION);
            HuntResult result = context.getPredationSystem().huntWithOutcome(
                animal, geometry, context, opportunity);
            if(context.hasHuntObservationListeners()) {
                boolean huntEvent = context.hasCurrentActorEventSince(eventCount,
                    SimulationEvent.EventType.HUNT, animal.getId());
                boolean predationDeath = context.getDeathCount(
                    DeathCause.PREDATION) > predationDeaths;
                context.recordHuntObservation(new HuntObservation(
                    context.getStep(), animal, animal.getProfile().isActiveDuring(
                    context.getClock().getPhase()), intent.getReason(), result,
                    huntEvent, predationDeath));
            }
            return StepFrameLedger.PrimaryAction.hunt(frameId,
                context.getStep(), animal, result);
        }
        if(intent.getActionType() == ActionType.GRAZE) {
            Location location = animal.getLocation();
            if(grazingLocations != null) {
                location = context.getFoodSystem().chooseGrazingLocation(animal,
                    grazingLocations, context);
            }
            context.getFoodSystem().feedHerbivoreAt(animal,
                location == null ? animal.getLocation() : location, context);
            animal.recordProductionTargetProgress(location == null
                ? animal.getLocation() : location, context.getStep());
            return StepFrameLedger.PrimaryAction.graze(frameId,
                context.getStep(), animal,
                grazingLocations == null ? null : location);
        }
        return StepFrameLedger.PrimaryAction.skipped(frameId,
            context.getStep(), animal, intent.getActionType());
    }

    /** Legacy action-path adapter; R2 passes one captured geometry per step. */
    public Location executeFoodAction(BehaviorIntent intent,
                                      SavannahAnimal animal,
                                      Field currentField,
                                      List<Location> grazingLocations,
                                      SimulationContext context)
    {
        return executeFoodAction(intent, animal,
            StepStartInteractionGeometry.capture(currentField),
            grazingLocations, context);
    }

    /** Resolves a non-mutating rest or ordinary-movement destination. */
    public Location resolveMovementDestination(BehaviorIntent intent,
                                               SavannahAnimal animal)
    {
        if(intent == null || animal == null ||
           intent.getAnimalId() != animal.getId() ||
           (intent.getActionType() != ActionType.MOVE &&
            intent.getActionType() != ActionType.REST)) {
            return null;
        }
        return intent.getDestination();
    }

    /** Delegates the existing mate, probability, cost, and slot-reservation rules. */
    public void executeBreedingAction(BehaviorIntent intent,
                                      SavannahAnimal animal,
                                      Field currentField,
                                      Field nextFieldState,
                                      List<Location> freeLocations,
                                      SimulationContext context)
    {
        if(intent == null || animal == null || currentField == null ||
           nextFieldState == null || freeLocations == null || context == null ||
           intent.getAnimalId() != animal.getId() ||
           intent.getActionType() != ActionType.BREED) {
            return;
        }
        context.getBreedingSystem().tryBreed(animal, currentField,
                                             nextFieldState, context,
                                             freeLocations);
    }

    /**
     * Generates R2 spawn claims only. Child construction and all birth side
     * effects belong to the single position-commit phase.
     */
    public List<PositionClaim> proposeBreedingAction(BehaviorIntent intent,
                                                       SavannahAnimal animal,
                                                       InteractionGeometry geometry,
                                                       List<Location> candidates,
                                                       SimulationContext context)
    {
        if(intent == null || animal == null || geometry == null ||
           candidates == null || context == null ||
           intent.getAnimalId() != animal.getId() ||
           intent.getActionType() != ActionType.BREED) {
            return Collections.emptyList();
        }
        StepFrameLedger.ResourceClock resourceClock =
            StepFrameLedger.ResourceClock.capture(context.getStep(), animal,
                StepFrameLedger.frameId(context.getStep(), animal.getId()));
        return proposeBreedingFrameAction(intent, animal, geometry, candidates,
            context, resourceClock).getPositionClaims();
    }

    /** Creates the immutable, physiology-pure S1 proposal. */
    public StepFrameLedger.ReproductionProposal proposeBreedingFrameAction(
        BehaviorIntent intent, SavannahAnimal animal,
        InteractionGeometry geometry, List<Location> candidates,
        SimulationContext context, StepFrameLedger.ResourceClock resourceClock)
    {
        if(animal == null || geometry == null || candidates == null ||
           context == null || resourceClock == null) {
            throw new IllegalArgumentException(
                "S1 requires actor, geometry, candidates, context and S0 clock");
        }
        if(intent == null || intent.getAnimalId() != animal.getId() ||
           intent.getActionType() != ActionType.BREED) {
            return StepFrameLedger.ReproductionProposal.none(
                StepFrameLedger.frameId(context.getStep(), animal.getId()),
                context.getStep(), animal, resourceClock,
                StepFrameLedger.ProposalReason.NOT_REQUESTED);
        }
        DecisionBirthCandidateFact candidateFact = null;
        if(context.hasDecisionBirthCandidateFactListeners() ||
           context.isPf1aFactCaptureEnabled()) {
            candidateFact = DecisionBirthCandidateFact.capture(
                context.getStep(), animal, candidates);
        }
        if(context.hasDecisionBirthCandidateFactListeners()) {
            context.recordDecisionBirthCandidateFact(
                candidateFact);
        }
        return context.getBreedingSystem().proposeFrame(animal, geometry,
            context, candidates, resourceClock, candidateFact);
    }
}
