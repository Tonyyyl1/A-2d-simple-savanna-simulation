/**
 * Recorder used for fast non-visual testing.
 */
public class NullSimulationRecorder implements SimulationRecorder
{
    public void start(Field field, SimulationContext context)
    {
    }

    public void recordStep(int step, Field field, SimulationContext context)
    {
    }

    public void finish(Field field, SimulationContext context, String reason)
    {
    }
}
