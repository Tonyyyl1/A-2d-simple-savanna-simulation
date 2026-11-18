package savanna;

import java.util.List;
import java.util.Collections;

/**
 * Interface for reproduction.
 */
public interface BreedingSystem
{
    /**
     * R2 proposal API. It may consume the normal breeding random draws and
     * reserve positions only as provisional tokens; it must not create a
     * child, emit a birth, or charge the parent.
     */
    default List<PositionClaim> propose(SavannahAnimal parent,
                                        InteractionGeometry geometry,
                                        SimulationContext context,
                                        List<Location> candidates)
    {
        return Collections.emptyList();
    }

    /**
     * PF1-aF adapter carrying the immutable SOC1-aF candidate fact already
     * captured by the caller. Existing implementations retain the old method.
     */
    default List<PositionClaim> propose(
        SavannahAnimal parent, InteractionGeometry geometry,
        SimulationContext context, List<Location> candidates,
        DecisionBirthCandidateFact candidateFact)
    {
        return propose(parent, geometry, context, candidates);
    }

    /**
     * FRAME-1 typed S1 adapter. Implementations may retain the legacy named
     * random operations, but the returned proposal must leave physiology and
     * pregnancy state untouched.
     */
    default StepFrameLedger.ReproductionProposal proposeFrame(
        SavannahAnimal parent, InteractionGeometry geometry,
        SimulationContext context, List<Location> candidates,
        StepFrameLedger.ResourceClock resourceClock,
        DecisionBirthCandidateFact candidateFact)
    {
        if(parent == null || context == null || resourceClock == null) {
            throw new IllegalArgumentException(
                "FRAME reproduction proposal requires actor, context and S0 clock");
        }
        AnimalPhysiologyFact before = AnimalPhysiologyFact.capture(parent);
        List<PositionClaim> claims = propose(parent, geometry, context,
            candidates, candidateFact);
        AnimalPhysiologyFact after = AnimalPhysiologyFact.capture(parent);
        StepFrameLedger.ProposalReason reason = claims.isEmpty()
            ? StepFrameLedger.ProposalReason.NOT_REQUESTED
            : StepFrameLedger.ProposalReason.PROVISIONAL_CLAIMS;
        return StepFrameLedger.ReproductionProposal.create(
            StepFrameLedger.frameId(context.getStep(), parent.getId()),
            context.getStep(), parent, resourceClock, reason, claims, null,
            before, after);
    }

    /** Legacy/direct-call adapter; R2 supplies its captured geometry directly. */
    default List<PositionClaim> propose(SavannahAnimal parent,
                                        Field currentField,
                                        SimulationContext context,
                                        List<Location> candidates)
    {
        return propose(parent, StepStartInteractionGeometry.capture(currentField),
            context, candidates);
    }
}
