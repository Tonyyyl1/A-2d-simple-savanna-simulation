package savanna;

/**
 * Interface for predator-prey interactions.
 */
public interface PredationSystem
{
    /**
     * Legacy/direct-call adapter. Production captures the same immutable S0
     * opportunity explicitly before entering S1.
     */
    default HuntResult huntWithOutcome(SavannahAnimal predator,
                                       InteractionGeometry geometry,
                                       SimulationContext context)
    {
        if(predator == null || context == null) {
            return new HuntResult(HuntOutcome.INELIGIBLE, null, "", 0, 0,
                0.0, HuntRandomLedger.none());
        }
        HuntOpportunityFact opportunity = HuntOpportunityFact.capture(
            context.getStep(), predator,
            StepFrameLedger.frameId(context.getStep(), predator.getId()),
            geometry, context);
        return huntWithOutcome(predator, geometry, context, opportunity);
    }

    /** Executes HUNT from the exact immutable S0 opportunity. */
    HuntResult huntWithOutcome(SavannahAnimal predator,
                               InteractionGeometry geometry,
                               SimulationContext context,
                               HuntOpportunityFact opportunity);

    /** Legacy destination adapter retained for existing callers. */
    default Location hunt(SavannahAnimal predator, InteractionGeometry geometry,
                          SimulationContext context)
    {
        return huntWithOutcome(predator, geometry, context).getDestination();
    }

    /** Legacy/direct-call adapter; R2 supplies its captured geometry directly. */
    default Location hunt(SavannahAnimal predator, Field field,
                          SimulationContext context)
    {
        return hunt(predator, StepStartInteractionGeometry.capture(field), context);
    }
}
