package savanna;

/** Immutable proposal from one behavior provider; it does not mutate the Field. */
public final class BehaviorIntent
{
    private final long animalId;
    private final ActionType actionType;
    private final BehaviorReason reason;
    private final Location destination;

    public BehaviorIntent(long animalId, ActionType actionType,
                          BehaviorReason reason, Location destination)
    {
        if(actionType == null || reason == null) {
            throw new IllegalArgumentException("An intent needs an action and reason.");
        }
        this.animalId = animalId;
        this.actionType = actionType;
        this.reason = reason;
        this.destination = destination == null ? null :
            new Location(destination.row(), destination.col());
    }

    public long getAnimalId()
    {
        return animalId;
    }

    public ActionType getActionType()
    {
        return actionType;
    }

    public BehaviorReason getReason()
    {
        return reason;
    }

    public Location getDestination()
    {
        return destination == null ? null :
            new Location(destination.row(), destination.col());
    }
}
