/**
 * Receives control commands from the visual simulation window.
 */
public interface SimulationControlHandler
{
    void togglePaused();

    void requestStopAndExit();
}
