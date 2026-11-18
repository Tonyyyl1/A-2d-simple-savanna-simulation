package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable result of validating a proposed position plan. */
public final class PositionPlanValidation
{
    private final List<String> errors;

    PositionPlanValidation(List<String> errors)
    {
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public boolean isValid()
    {
        return errors.isEmpty();
    }

    public List<String> getErrors()
    {
        return errors;
    }

    public void requireValid()
    {
        if(!isValid()) {
            throw new IllegalStateException("Invalid position plan: " + errors);
        }
    }
}
