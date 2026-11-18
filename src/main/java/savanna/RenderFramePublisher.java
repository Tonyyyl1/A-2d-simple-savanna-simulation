package savanna;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Latest-wins transport for immutable display frames.
 *
 * <p>Replacing an unconsumed frame is intentional: display is a lossy sink,
 * unlike the durable report recorder. The counter makes that loss observable
 * without queuing unbounded Swing work.</p>
 */
public final class RenderFramePublisher
{
    private final AtomicReference<RenderFrame> latest = new AtomicReference<>();
    private final AtomicLong publishedFrameCount = new AtomicLong();
    private final AtomicLong droppedFrameCount = new AtomicLong();

    public void publish(RenderFrame frame)
    {
        if(frame == null) return;
        RenderFrame replaced = latest.getAndSet(frame);
        publishedFrameCount.incrementAndGet();
        if(replaced != null) droppedFrameCount.incrementAndGet();
    }

    public RenderFrame latest()
    {
        return latest.get();
    }

    public long getPublishedFrameCount()
    {
        return publishedFrameCount.get();
    }

    public long getDroppedFrameCount()
    {
        return droppedFrameCount.get();
    }
}
