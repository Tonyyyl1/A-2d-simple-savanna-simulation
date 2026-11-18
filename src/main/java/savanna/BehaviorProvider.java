package savanna;

import java.util.List;

/** Read-only behavior proposal source. Providers must not consume random state. */
public interface BehaviorProvider
{
    BehaviorIntent propose(SavannahAnimal animal, List<Location> freeLocations,
                           SimulationContext context);
}
