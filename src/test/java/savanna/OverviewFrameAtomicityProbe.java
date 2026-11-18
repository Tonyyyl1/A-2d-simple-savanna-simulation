package savanna;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/**
 * Deterministically verifies the overview front-frame ownership contract.
 * It deliberately pauses a new back buffer while its overlay is transparent,
 * then proves that the EDT still sees the previous complete frame.
 */
public final class OverviewFrameAtomicityProbe
{
    private static final int SIZE = 24;

    private OverviewFrameAtomicityProbe()
    {
    }

    public static void run()
    {
        testUnfinishedBackBufferNeverBecomesFront();
        testInvalidAndExpiredFramesAreRejected();
        testAbortedBuildKeepsTheFrontFrame();
        testCommitRequiresTheEdt();
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("B2 overview frame atomicity probe");
        TestSupport.startGroup("Overview frame atomicity");
        run();
        TestSupport.finishGroup();
        TestSupport.finishSuite();
        if(TestSupport.hasFailures()) {
            System.exit(1);
        }
    }

    private static void testUnfinishedBackBufferNeverBecomesFront()
    {
        final OverviewFrameBuffer buffer = new OverviewFrameBuffer();
        final OverviewFrameBuffer.CompletedFrame frameA =
            completedFrame(buffer, 1L, Color.RED, true);
        onEdt(new Runnable() {
            public void run()
            {
                buffer.commit(frameA);
            }
        });

        final CountDownLatch backBufferCreated = new CountDownLatch(1);
        final CountDownLatch allowCompletion = new CountDownLatch(1);
        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicReference<OverviewFrameBuffer.CompletedFrame> frameB =
            new AtomicReference<>();
        final AtomicReference<OverviewFrameBuffer.WritableFrame> writableB =
            new AtomicReference<>();
        Thread builder = new Thread(new Runnable() {
            public void run()
            {
                OverviewFrameBuffer.WritableFrame writable = buffer.beginFrame(
                    2L, 200, SIZE, SIZE, terrain(Color.DARK_GRAY), null,
                    geometry(), null);
                writableB.set(writable);
                backBufferCreated.countDown();
                await(allowCompletion, "allow completion of back buffer");
                Graphics2D graphics = writable.graphics();
                graphics.setColor(Color.BLUE);
                graphics.fillRect(0, 0, SIZE, SIZE);
                frameB.set(writable.complete(7, true));
                completed.countDown();
            }
        }, "overview-frame-probe-builder");
        builder.start();
        boolean backBufferIsPaused = await(backBufferCreated,
                                            "create transparent back buffer");
        TestSupport.assertTrue("atomic probe reached unfinished back buffer",
                               backBufferIsPaused);

        onEdt(new Runnable() {
            public void run()
            {
                OverviewFrameBuffer.CompletedFrame seen = buffer.currentFrame();
                TestSupport.assertTrue("unfinished buffer retains frame A",
                                       seen == frameA);
                TestSupport.assertEquals("frame A sentinel remains visible",
                    Integer.valueOf(Color.RED.getRGB()),
                    Integer.valueOf(seen.getOverlayImage().getRGB(0, 0)));
            }
        });

        allowCompletion.countDown();
        boolean finished = await(completed, "complete back buffer");
        TestSupport.assertTrue("atomic probe completed frame B", finished);
        try {
            builder.join(2000L);
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while joining builder", e);
        }

        onEdt(new Runnable() {
            public void run()
            {
                buffer.commit(frameB.get());
                OverviewFrameBuffer.CompletedFrame seen = buffer.currentFrame();
                TestSupport.assertTrue("completed buffer atomically replaces frame A",
                                       seen == frameB.get());
                TestSupport.assertEquals("frame B sentinel is visible after commit",
                    Integer.valueOf(Color.BLUE.getRGB()),
                    Integer.valueOf(seen.getOverlayImage().getRGB(0, 0)));
            }
        });
        boolean writableRejected = false;
        try {
            writableB.get().graphics();
        }
        catch(IllegalStateException expected) {
            writableRejected = true;
        }
        TestSupport.assertTrue("completed frame exposes no writable graphics",
                               writableRejected);
    }

    private static void testInvalidAndExpiredFramesAreRejected()
    {
        final OverviewFrameBuffer buffer = new OverviewFrameBuffer();
        final OverviewFrameBuffer.CompletedFrame current =
            completedFrame(buffer, 5L, Color.GREEN, true);
        final OverviewFrameBuffer.CompletedFrame incompleteOverlay =
            completedFrame(buffer, 6L, Color.YELLOW, false);
        final OverviewFrameBuffer.CompletedFrame expired =
            completedFrame(buffer, 4L, Color.MAGENTA, true);
        onEdt(new Runnable() {
            public void run()
            {
                buffer.commit(current);
                boolean rejectedIncompleteOverlay = false;
                try {
                    buffer.commit(incompleteOverlay);
                }
                catch(IllegalArgumentException expected) {
                    rejectedIncompleteOverlay = true;
                }
                TestSupport.assertTrue("unfinished overlay cannot be committed",
                                       rejectedIncompleteOverlay);
                TestSupport.assertTrue("failed overlay keeps current frame",
                                       buffer.currentFrame() == current);

                boolean rejectedExpired = false;
                try {
                    buffer.commit(expired);
                }
                catch(IllegalArgumentException expected) {
                    rejectedExpired = true;
                }
                TestSupport.assertTrue("expired generation cannot replace front frame",
                                       rejectedExpired);
            }
        });
    }

    private static void testCommitRequiresTheEdt()
    {
        OverviewFrameBuffer buffer = new OverviewFrameBuffer();
        OverviewFrameBuffer.CompletedFrame frame =
            completedFrame(buffer, 1L, Color.CYAN, true);
        boolean rejected = false;
        try {
            buffer.commit(frame);
        }
        catch(IllegalStateException expected) {
            rejected = true;
        }
        TestSupport.assertTrue("non-EDT commit is rejected", rejected);
    }

    private static void testAbortedBuildKeepsTheFrontFrame()
    {
        final OverviewFrameBuffer buffer = new OverviewFrameBuffer();
        final OverviewFrameBuffer.CompletedFrame front =
            completedFrame(buffer, 3L, Color.ORANGE, true);
        final OverviewFrameBuffer.WritableFrame failingBuild = buffer.beginFrame(
            4L, 400, SIZE, SIZE, terrain(Color.GRAY), null, geometry(), null);
        onEdt(new Runnable() {
            public void run()
            {
                buffer.commit(front);
            }
        });
        failingBuild.abort();
        onEdt(new Runnable() {
            public void run()
            {
                TestSupport.assertTrue("aborted build keeps the previous front frame",
                                       buffer.currentFrame() == front);
                TestSupport.assertEquals("aborted build keeps the previous pixels",
                    Integer.valueOf(Color.ORANGE.getRGB()),
                    Integer.valueOf(front.getOverlayImage().getRGB(0, 0)));
            }
        });
    }

    private static OverviewFrameBuffer.CompletedFrame completedFrame(
        OverviewFrameBuffer buffer, long generation, Color color,
        boolean overlayComplete)
    {
        OverviewFrameBuffer.WritableFrame writable = buffer.beginFrame(
            generation, (int)generation, SIZE, SIZE, terrain(Color.GRAY), null,
            geometry(), null);
        Graphics2D graphics = writable.graphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, SIZE, SIZE);
        return writable.complete(3, overlayComplete);
    }

    private static BufferedImage terrain(Color color)
    {
        BufferedImage image = new BufferedImage(SIZE, SIZE,
                                                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, SIZE, SIZE);
        }
        finally {
            graphics.dispose();
        }
        return image;
    }

    private static VisualGridGeometry geometry()
    {
        return new VisualGridGeometry(3, 3, SIZE, SIZE);
    }

    private static boolean await(CountDownLatch latch, String action)
    {
        try {
            return latch.await(2L, TimeUnit.SECONDS);
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to " + action,
                                            e);
        }
    }

    private static void onEdt(Runnable action)
    {
        if(SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for EDT", e);
        }
        catch(InvocationTargetException e) {
            throw new IllegalStateException("EDT probe action failed", e.getCause());
        }
    }
}
