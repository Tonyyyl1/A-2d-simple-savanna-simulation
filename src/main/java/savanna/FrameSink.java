package savanna;

/**
 * Receiver of complete simulation frames (plan v3.2 V1). The kernel knows only
 * this interface, never a renderer. Implementations must not touch the
 * simulator: they only read the immutable arguments.
 */
public interface FrameSink
{
    /** Sent once per run generation, before its first frame (and on registration). */
    void onWorld(WorldDescriptor world);

    /** One complete snapshot per step, plus frame 0 for the initial state of a generation. */
    void onFrame(SimFrame frame);

    /** The run is over (step budget reached, extinction or stop request). */
    void onRunEnd(String reason);
}
