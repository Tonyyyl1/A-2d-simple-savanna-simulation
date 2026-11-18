package savanna;

import java.util.List;

/** First v1.7 provider: converts water priority into a DRINK or SEEK_WATER intent. */
public final class ThirstBehaviorProvider implements BehaviorProvider
{
    public BehaviorIntent propose(SavannahAnimal animal,
                                  List<Location> freeLocations,
                                  SimulationContext context)
    {
        if(animal == null || !animal.isAlive() || context == null ||
           !context.isThirstEnabled()) {
            return null;
        }
        ThirstSystem thirst = context.getThirstSystem();
        HydrationAssessment assessment = thirst.assess(animal, context);
        if(!assessment.isWaterPriority()) {
            return null;
        }
        String previousTarget = animal.getWaterTargetPoolId();
        WaterTargetDecision decision = thirst.selectWaterTarget(animal, context);
        if(decision.wasRetargeted()) {
            context.recordEvent(SimulationEvent.waterRetarget(context.getStep(), animal));
        }
        if((WaterSourceIndex.NONE.equals(previousTarget) || decision.wasRetargeted()) &&
           decision.hasArrivalDeficit()) {
            context.recordEvent(SimulationEvent.waterArrivalDeficit(context.getStep(), animal));
        }
        assessment = thirst.assess(animal, context);
        BehaviorReason reason = assessment.getStage() == HydrationStage.CRITICAL
            ? BehaviorReason.CRITICAL_DEHYDRATION : BehaviorReason.THIRST;
        Location location = animal.getLocation();
        if(thirst.isDrinkableShore(location)) {
            return new BehaviorIntent(animal.getId(), ActionType.DRINK,
                                      reason, location);
        }
        return new BehaviorIntent(animal.getId(), ActionType.SEEK_WATER,
            reason, thirst.stepTowardWater(animal, freeLocations, context.getStep()));
    }
}
