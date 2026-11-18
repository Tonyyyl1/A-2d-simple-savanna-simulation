package savanna;

import java.util.List;

/** Identifies an eligible female's low-priority, position-reserving breed action. */
public final class BreedingBehaviorProvider implements BehaviorProvider
{
    public BehaviorIntent propose(SavannahAnimal animal,
                                  List<Location> freeLocations,
                                  SimulationContext context)
    {
        if(animal == null || !animal.isAlive() || context == null ||
           animal.getSex() != Sex.FEMALE || !animal.canBreed() ||
           freeLocations == null || freeLocations.isEmpty()) {
            return null;
        }
        return new BehaviorIntent(animal.getId(), ActionType.BREED,
                                  BehaviorReason.BREEDING,
                                  freeLocations.get(0));
    }
}
