package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, observable result of one animal's completed behavior step. */
public final class BehaviorOutcome
{
    private final BehaviorIntent primaryIntent;
    private final List<ExecutedEffect> executedEffects;
    private final MovementDisposition movementDisposition;
    private final ActionType displayAction;
    private final boolean restCredit;

    public BehaviorOutcome(BehaviorIntent primaryIntent,
                           List<ExecutedEffect> executedEffects,
                           MovementDisposition movementDisposition,
                           ActionType displayAction,
                           boolean restCredit)
    {
        this.primaryIntent = primaryIntent;
        this.executedEffects = Collections.unmodifiableList(new ArrayList<>(
            executedEffects == null ? Collections.<ExecutedEffect>emptyList()
                                    : executedEffects));
        this.movementDisposition = movementDisposition == null
            ? MovementDisposition.NOT_ATTEMPTED : movementDisposition;
        this.displayAction = displayAction;
        this.restCredit = restCredit;
        if(restCredit && !this.executedEffects.contains(ExecutedEffect.REST)) {
            throw new IllegalArgumentException("rest credit requires REST effect");
        }
    }

    public BehaviorIntent getPrimaryIntent()
    {
        return primaryIntent;
    }

    public List<ExecutedEffect> getExecutedEffects()
    {
        return executedEffects;
    }

    public MovementDisposition getMovementDisposition()
    {
        return movementDisposition;
    }

    public ActionType getDisplayAction()
    {
        return displayAction;
    }

    public boolean receivesRestCredit()
    {
        return restCredit;
    }
}
