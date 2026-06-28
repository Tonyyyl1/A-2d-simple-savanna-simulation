/**
 * Anything that can take a turn in the simulation implements this interface.
 */
public interface Actor
{
    /**
     * Perform one step of behaviour.
     * @param currentField The field at the start of this step.
     * @param nextFieldState The field being built for the next step.
     * @param context The shared environmental systems for this step.
     */
    void act(Field currentField, Field nextFieldState, SimulationContext context);
}
