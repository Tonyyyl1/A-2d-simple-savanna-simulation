package savanna;

import java.util.List;

/** Names the legacy post-action fallback as an explicit REST or MOVE intent. */
public final class MovementBehaviorProvider
{
    public BehaviorIntent propose(SavannahAnimal animal,
                                  List<Location> freeLocations,
                                  boolean canMove)
    {
        if(animal == null || !animal.isAlive()) {
            return null;
        }
        if(canMove && freeLocations != null && !freeLocations.isEmpty()) {
            return new BehaviorIntent(animal.getId(), ActionType.MOVE,
                BehaviorReason.ORDINARY_MOVEMENT, freeLocations.get(0));
        }
        return new BehaviorIntent(animal.getId(), ActionType.REST,
            BehaviorReason.REST, animal.getLocation());
    }

    /**
     * Names the fallback accurately once the caller knows why ordinary
     * movement is unavailable. The legacy overload remains for old callers.
     */
    public BehaviorIntent propose(SavannahAnimal animal,
                                  List<Location> freeLocations,
                                  boolean canMove,
                                  boolean intentionalRest,
                                  BehaviorReason restReason)
    {
        if(animal == null || !animal.isAlive()) {
            return null;
        }
        if(canMove && freeLocations != null && !freeLocations.isEmpty()) {
            return new BehaviorIntent(animal.getId(), ActionType.MOVE,
                BehaviorReason.ORDINARY_MOVEMENT, freeLocations.get(0));
        }
        if(intentionalRest) {
            BehaviorReason reason = restReason == null ? BehaviorReason.REST
                                                        : restReason;
            return new BehaviorIntent(animal.getId(), ActionType.REST,
                reason, animal.getLocation());
        }
        return new BehaviorIntent(animal.getId(), ActionType.WAIT,
            BehaviorReason.BLOCKED, animal.getLocation());
    }
}
