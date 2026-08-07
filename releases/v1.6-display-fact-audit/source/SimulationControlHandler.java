/**
 * Receives control commands from the visual simulation window.
 */
public interface SimulationControlHandler
{
    void togglePaused();

    void setPaused(boolean paused);

    boolean isPaused();

    void requestStopAndExit();
}
