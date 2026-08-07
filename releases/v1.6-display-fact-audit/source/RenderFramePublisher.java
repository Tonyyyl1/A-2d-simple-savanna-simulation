import java.util.concurrent.atomic.AtomicReference;

/** Publishes the latest immutable frame without changing existing rendering. */
public final class RenderFramePublisher
{
    private final AtomicReference<RenderFrame> latest = new AtomicReference<>();

    public void publish(RenderFrame frame)
    {
        if(frame != null) latest.set(frame);
    }

    public RenderFrame latest()
    {
        return latest.get();
    }
}
