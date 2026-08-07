/**
 * Interface for disease progression and transmission.
 */
public interface DiseaseSystem
{
    void progressDisease(SavannahAnimal animal, SimulationContext context, Field field);

    void expose(SavannahAnimal source, SavannahAnimal target, double contactIntensity,
                SimulationContext context);

    void afterPredation(SavannahAnimal prey, SavannahAnimal predator, SimulationContext context);

    int countInfected(Field field);
}
