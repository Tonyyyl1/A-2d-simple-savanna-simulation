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
        Location here = animal.getLocation();
        if(animal.isDrinking()) {
            // A drinking session keeps its shore slot until it completes.
            if(thirst.isDrinkableShore(here)) {
                return new BehaviorIntent(animal.getId(), ActionType.DRINK,
                    BehaviorReason.THIRST, here);
            }
            animal.stopDrinkingSession();
        }
        HydrationAssessment assessment = thirst.assess(animal, context);
        if(!assessment.isWaterPriority()) {
            animal.resetWaterWait();
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
        Location step = thirst.stepTowardWater(animal, freeLocations, context.getStep());
        int distance = thirst.distanceToTargetPool(location, animal.getWaterTargetPoolId());
        EcologyParameters parameters = EcologyParameters.current();
        if(step == null &&
           distance <= parameters.getInt(EcologyParameters.Key.WATER_QUEUE_RADIUS)) {
            // M2-2 queue: every nearer cell is taken; wait, then retarget.
            context.recordEvent(SimulationEvent.waterBlocked(context.getStep(), animal,
                location));
            if(animal.recordWaterWait() >
               parameters.getInt(EcologyParameters.Key.WATER_MAX_WAIT_STEPS)) {
                animal.abandonWaterTarget(context.getStep());
                thirst.selectWaterTarget(animal, context);
                context.recordEvent(SimulationEvent.waterRetarget(context.getStep(), animal));
                step = thirst.stepTowardWater(animal, freeLocations, context.getStep());
            }
        }
        else if(step != null) {
            animal.resetWaterWait();
        }
        return new BehaviorIntent(animal.getId(), ActionType.SEEK_WATER, reason, step);
    }
}
