package savanna;

import java.util.List;

/** Converts food survival pressure into the existing hunt or graze action. */
public final class FoodBehaviorProvider implements BehaviorProvider
{
    private static final int TARGET_TIMEOUT_STEPS = 8;

    public BehaviorIntent propose(SavannahAnimal animal,
                                  List<Location> freeLocations,
                                  SimulationContext context)
    {
        if(animal == null || !animal.isAlive() || context == null) {
            return null;
        }
        SurvivalAssessment assessment = context.assessSurvival(animal);
        BehaviorReason reason = assessment.getFoodSurvivalPercent() < 30.0
            ? BehaviorReason.CRITICAL_FOOD : BehaviorReason.FOOD;
        ActionType action = animal.getProfile().isPredator()
            ? ActionType.HUNT : ActionType.GRAZE;
        animal.ensureProductionTarget(animal.getProfile().isPredator()
            ? ProductionTargetState.Type.HUNT_REGION
            : ProductionTargetState.Type.GRAZE_REGION,
            animal.getLocation(), context.getStep(), TARGET_TIMEOUT_STEPS);
        return new BehaviorIntent(animal.getId(), action, reason,
                                  animal.getLocation());
    }
}
