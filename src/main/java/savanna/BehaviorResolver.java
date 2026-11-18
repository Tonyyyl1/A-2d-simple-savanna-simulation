package savanna;

import java.util.List;

/** Selects one primary intent deterministically from the available proposals. */
public final class BehaviorResolver
{
    public BehaviorIntent resolve(List<BehaviorIntent> intents)
    {
        BehaviorIntent selected = null;
        if(intents == null) {
            return null;
        }
        for(BehaviorIntent candidate : intents) {
            if(candidate == null) {
                continue;
            }
            if(selected == null || outranks(candidate, selected)) {
                selected = candidate;
            }
        }
        return selected;
    }

    private boolean outranks(BehaviorIntent candidate, BehaviorIntent selected)
    {
        int priority = candidate.getReason().getPriority();
        int selectedPriority = selected.getReason().getPriority();
        if(priority != selectedPriority) {
            return priority > selectedPriority;
        }
        return actionPriority(candidate.getActionType()) <
               actionPriority(selected.getActionType());
    }

    private int actionPriority(ActionType action)
    {
        if(action == null) {
            return Integer.MAX_VALUE;
        }
        switch(action) {
            case DRINK:      return 0;
            case SEEK_WATER: return 1;
            case HUNT:       return 2;
            case GRAZE:      return 3;
            case BREED:      return 4;
            case MOVE:       return 5;
            case REST:       return 6;
            case WAIT:       return 7;
            default:         return Integer.MAX_VALUE;
        }
    }
}
