import java.util.List;

/**
 * Interface for reproduction.
 */
public interface BreedingSystem
{
    void tryBreed(SavannahAnimal parent, Field currentField, Field nextFieldState,
                  SimulationContext context, List<Location> freeLocations);
}
