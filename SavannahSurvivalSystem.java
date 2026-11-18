/** Default survival evaluator for the savanna model. */
public class SavannahSurvivalSystem implements SurvivalSystem
{
    public SurvivalAssessment assess(SavannahAnimal animal,
                                     SimulationContext context)
    {
        if(animal == null) {
            return new SurvivalAssessment(0.0, 0.0);
        }
        double hydration = context != null && context.isThirstEnabled()
            ? animal.getHydrationPercent() : 100.0;
        return new SurvivalAssessment(animal.getFoodSurvivalPercent(), hydration);
    }
}
