import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

/**
 * Runs a same-machine overview render benchmark on a real display. It is
 * intentionally compatible with both the locked B1 jar and the B2 classes so
 * B2 can be compared against B1 without changing either renderer.
 */
public final class OverviewFramePerformanceProbe
{
    private static final int WIDTH = 1732;
    private static final int HEIGHT = 948;
    private static final int WARMUP_COUNT = 5;
    private static final int SAMPLE_COUNT = 20;

    private OverviewFramePerformanceProbe()
    {
    }

    public static void main(String[] args)
    {
        if(GraphicsEnvironment.isHeadless()) {
            System.out.println("OverviewFramePerformanceProbe requires a real display; " +
                               "headless runs are intentionally not counted.");
            return;
        }
        String label = args.length > 0 ? args[0] : "candidate";
        double baselineP95 = args.length > 1 ? Double.parseDouble(args[1]) : -1.0;

        Simulator simulator = new Simulator(SimulationConfig.default3x(), true);
        simulator.setVerbose(false);
        final SimulatorView view = (SimulatorView)getField(simulator, "view");
        final JButton pauseButton = (JButton)getField(view, "pauseButton");
        try {
            onEdt(new Runnable() {
                public void run()
                {
                    view.setSize(WIDTH, HEIGHT);
                    view.validate();
                }
            });

            for(int index = 0; index < WARMUP_COUNT; index++) {
                renderOnce(view, simulator);
            }
            long[] samples = new long[SAMPLE_COUNT];
            for(int index = 0; index < SAMPLE_COUNT; index++) {
                samples[index] = renderOnce(view, simulator);
            }
            long inputLatency = measureQueuedPauseLatency(view, simulator,
                                                          pauseButton);
            Arrays.sort(samples);
            double p95Millis = nanosToMillis(samples[(int)Math.ceil(
                SAMPLE_COUNT * 0.95) - 1]);
            double maxMillis = nanosToMillis(samples[SAMPLE_COUNT - 1]);
            double inputMillis = nanosToMillis(inputLatency);
            System.out.printf("OverviewFramePerformanceProbe %s: %dx%d " +
                              "p95=%.2f ms max=%.2f ms queued-input=%.2f ms%n",
                              label, WIDTH, HEIGHT, p95Millis, maxMillis,
                              inputMillis);

            if(baselineP95 >= 0.0) {
                if(p95Millis > baselineP95 * 1.25) {
                    throw new IllegalStateException("B2 p95 " + p95Millis +
                        " ms exceeds 125% of B1 baseline " + baselineP95 +
                        " ms.");
                }
                if(p95Millis > 150.0) {
                    throw new IllegalStateException("B2 EDT p95 exceeds 150 ms: " +
                                                    p95Millis + " ms.");
                }
                if(inputMillis > 250.0) {
                    throw new IllegalStateException(
                        "B2 queued input exceeds 250 ms: " + inputMillis + " ms.");
                }
            }
        }
        finally {
            onEdt(new Runnable() {
                public void run()
                {
                    view.dispose();
                }
            });
        }
    }

    private static long renderOnce(final SimulatorView view,
                                   final Simulator simulator)
    {
        final AtomicLong elapsed = new AtomicLong();
        onEdt(new Runnable() {
            public void run()
            {
                long started = System.nanoTime();
                view.showStatus(simulator.getStep(), simulator.getField(),
                                simulator.getContext());
                elapsed.set(System.nanoTime() - started);
            }
        });
        return elapsed.get();
    }

    private static long measureQueuedPauseLatency(final SimulatorView view,
                                                   final Simulator simulator,
                                                   final JButton pauseButton)
    {
        final CountDownLatch inputHandled = new CountDownLatch(1);
        final AtomicLong completedAt = new AtomicLong();
        long queuedAt = System.nanoTime();
        SwingUtilities.invokeLater(new Runnable() {
            public void run()
            {
                view.showStatus(simulator.getStep(), simulator.getField(),
                                simulator.getContext());
            }
        });
        SwingUtilities.invokeLater(new Runnable() {
            public void run()
            {
                pauseButton.doClick(0);
                pauseButton.doClick(0);
                completedAt.set(System.nanoTime());
                inputHandled.countDown();
            }
        });
        try {
            if(!inputHandled.await(3L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Queued pause input did not complete.");
            }
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for input", e);
        }
        return completedAt.get() - queuedAt;
    }

    private static double nanosToMillis(long nanos)
    {
        return nanos / 1_000_000.0;
    }

    private static Object getField(Object target, String name)
    {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        }
        catch(ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot access " + name + " for benchmark.",
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
            throw new IllegalStateException("EDT benchmark action failed", e.getCause());
        }
    }
}
