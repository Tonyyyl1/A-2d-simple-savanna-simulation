package savanna;

/** Provides the shared survival assessment without changing behavior policy. */
public interface SurvivalSystem
{
    SurvivalAssessment assess(SavannahAnimal animal, SimulationContext context);
}
