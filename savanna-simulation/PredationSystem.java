/**
 * Interface for predator-prey interactions.
 */
public interface PredationSystem
{
    Location hunt(SavannahAnimal predator, Field field, SimulationContext context);
}
