/**
 * Records what happens during a simulation run.
 */
public interface SimulationRecorder
{
    /**
     * Prepare for a new run.
     */
    void start(Field field, SimulationContext context);

    /**
     * Record one completed simulation step.
     */
    void recordStep(int step, Field field, SimulationContext context);

    /**
     * Finish the run and write any output documents.
     */
    void finish(Field field, SimulationContext context, String reason);
}
