/**
 * Receives every event recorded by a simulation context.
 *
 * This is for observation only. Implementations must not mutate the field or
 * consume the shared simulation random stream.
 */
public interface SimulationEventListener
{
    void onEvent(SimulationEvent event);
}
