package savanna;

import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Manual B2 acceptance probe for a real display. It drives the normal view
 * through 300 paint samples while repeatedly resizing the actual window. The
 * probe deliberately refuses to claim a GUI result in a headless runtime.
 */
public final class OverviewGuiStressProbe
{
    private static final int[][] WINDOW_SIZES = {
        { 3600, 2400 }, { 1732, 948 }, { 1387, 829 }, { 932, 712 }, { 640, 480 }
    };
    private static final int SAMPLE_COUNT = 300;

    private OverviewGuiStressProbe()
    {
    }

    public static void main(String[] args)
    {
        if(GraphicsEnvironment.isHeadless()) {
            System.out.println("OverviewGuiStressProbe requires a real display; " +
                               "headless runs are intentionally not counted.");
            return;
        }

        Simulator simulator = new Simulator(SimulationConfig.default3x(), true);
        SimulatorView view = (SimulatorView)getField(simulator, "view");
        Object fieldView = getField(view, "fieldView");
        ViewInteractionController interactionController =
            (ViewInteractionController)getField(view, "interactionController");
        OverviewFrameBuffer frameBuffer = (OverviewFrameBuffer)getField(
            fieldView, "frameBuffer");
        try {
            simulator.setVerbose(false);
            simulator.setStepDelay(0);
            int sample = 0;
            for(int step = 1; step <= 220; step++) {
                simulator.simulateOneStep();
                sampleNormalPaint(view, fieldView, frameBuffer, sample++);
                // Capture extra paints directly on both sides of the two
                // ordinary-layer refresh boundaries, rather than sampling
                // only after the simulation has already finished.
                if(step == 99 || step == 100 || step == 101 ||
                   step == 199 || step == 200 || step == 201) {
                    for(int extra = 0; extra < 10; extra++) {
                        sampleNormalPaint(view, fieldView, frameBuffer, sample++);
                    }
                }
            }
            while(sample < SAMPLE_COUNT) {
                sampleNormalPaint(view, fieldView, frameBuffer, sample++);
            }
            exercisePauseButton(simulator, view);
            exerciseInspectAndZoom(simulator, view, fieldView,
                                  interactionController, frameBuffer);
            exerciseStopAndExit(simulator, view);
            System.out.println("OverviewGuiStressProbe passed " + SAMPLE_COUNT +
                               " normal paints across five window sizes and " +
                               "steps 99->100 / 199->200; pause, inspect, " +
                               "zoom, resume and Stop & Exit all completed.");
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

    private static void assertCompleteFrontFrame(OverviewFrameBuffer frameBuffer,
                                                 int sample)
    {
        OverviewFrameBuffer.CompletedFrame frame = frameBuffer.currentFrame();
        if(frame == null || !frame.isOverlayComplete() ||
           frame.getDrawnAnimalCount() <= 0 ||
           frame.getTerrainImage() == null || frame.getOverlayImage() == null) {
            throw new IllegalStateException("Incomplete ordinary frame at sample " +
                                            sample + ".");
        }
        int panelPixel = frame.getOverlayImage().getRGB(8, 8);
        if(((panelPixel >>> 24) & 0xff) == 0) {
            throw new IllegalStateException("Terrain-only ordinary frame at sample " +
                                            sample + ".");
        }
    }

    private static void sampleNormalPaint(final SimulatorView view,
                                          final Object fieldView,
                                          final OverviewFrameBuffer frameBuffer,
                                          final int sample)
    {
        onEdt(new Runnable() {
            public void run()
            {
                int[] windowSize = WINDOW_SIZES[sample % WINDOW_SIZES.length];
                view.setSize(windowSize[0], windowSize[1]);
                view.validate();
                BufferedImage paintTarget = new BufferedImage(1, 1,
                    BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = paintTarget.createGraphics();
                try {
                    ((java.awt.Component)fieldView).paint(graphics);
                }
                finally {
                    graphics.dispose();
                }
                assertCompleteFrontFrame(frameBuffer, sample + 1);
            }
        });
    }

    private static void exercisePauseButton(final Simulator simulator,
                                            final SimulatorView view)
    {
        final JButton pauseButton = (JButton)getField(view, "pauseButton");
        onEdt(new Runnable() {
            public void run()
            {
                pauseButton.doClick();
                require(simulator.isPaused(),
                        "Pause button did not pause the real simulator.");
                pauseButton.doClick();
                require(!simulator.isPaused(),
                        "Pause button did not resume the real simulator.");
            }
        });
    }

    private static void exerciseInspectAndZoom(final Simulator simulator,
                                               final SimulatorView view,
                                               final Object fieldView,
                                               final ViewInteractionController controller,
                                               final OverviewFrameBuffer frameBuffer)
    {
        final ViewInteractionController.SizeProvider sizeProvider =
            (ViewInteractionController.SizeProvider)fieldView;
        for(int pausedCase = 0; pausedCase < 2; pausedCase++) {
            final boolean initiallyPaused = pausedCase == 1;
            for(int round = 0; round < 10; round++) {
                simulator.setPaused(initiallyPaused);
                onEdt(new Runnable() {
                    public void run()
                    {
                        zoomTo(controller, 2.49, sizeProvider);
                        require(!view.isInspectMode(),
                            "Zoom 2.49 entered inspect mode unexpectedly.");
                        require(simulator.isPaused() == initiallyPaused,
                            "Pre-inspect pause state changed at zoom 2.49.");

                        zoomTo(controller, 2.5, sizeProvider);
                        require(view.isInspectMode(),
                            "Zoom 2.5 did not enter inspect mode.");
                        require(simulator.isPaused(),
                            "Inspect mode did not pause a running simulator.");
                    }
                });
                allowInspectAnimation();
                onEdt(new Runnable() {
                    public void run()
                    {
                        Timer sceneTimer = (Timer)getField(view, "sceneTimer");
                        require(sceneTimer != null && sceneTimer.isRunning(),
                            "Inspect scene timer did not run after entry.");
                        paintForInspect(fieldView);
                        assertCompleteFrontFrame(frameBuffer, 0);
                        zoomTo(controller, 4.0, sizeProvider);
                        require(view.isInspectMode(),
                            "Zoom 4 left inspect mode unexpectedly.");
                        zoomTo(controller, 6.0, sizeProvider);
                        require(view.isInspectMode(),
                            "Zoom 6 left inspect mode unexpectedly.");
                    }
                });
                allowInspectAnimation();
                onEdt(new Runnable() {
                    public void run()
                    {
                        paintForInspect(fieldView);
                        zoomTo(controller, 1.0, sizeProvider);
                        require(!view.isInspectMode(),
                            "Returning to zoom 1 did not leave inspect mode.");
                        require(simulator.isPaused() == initiallyPaused,
                            "Inspect exit did not restore the prior pause state.");
                        assertCompleteFrontFrame(frameBuffer, 0);
                    }
                });
            }
            simulator.setPaused(false);
        }
    }

    private static void exerciseStopAndExit(final Simulator simulator,
                                            final SimulatorView view)
    {
        final JButton pauseButton = (JButton)getField(view, "pauseButton");
        final JButton stopButton = (JButton)getField(view, "stopButton");
        onEdt(new Runnable() {
            public void run()
            {
                stopButton.doClick();
                require(!pauseButton.isEnabled() && !stopButton.isEnabled(),
                    "Stop & Exit did not disable the window controls.");
                require("Stopping...".equals(stopButton.getText()),
                    "Stop & Exit did not expose the stopping state.");
                require(!simulator.isPaused(),
                    "Stop & Exit left the simulator paused.");
            }
        });
    }

    private static void zoomTo(ViewInteractionController controller,
                               double targetZoom,
                               ViewInteractionController.SizeProvider sizeProvider)
    {
        double currentZoom = controller.getTransform().getZoom();
        controller.zoomBy(targetZoom / currentZoom, 320, 240, sizeProvider);
    }

    private static void paintForInspect(Object fieldView)
    {
        BufferedImage paintTarget = new BufferedImage(1, 1,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = paintTarget.createGraphics();
        try {
            ((java.awt.Component)fieldView).paint(graphics);
        }
        finally {
            graphics.dispose();
        }
    }

    private static void allowInspectAnimation()
    {
        try {
            Thread.sleep(90L);
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for inspect animation",
                                            e);
        }
    }

    private static void require(boolean condition, String message)
    {
        if(!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static Object getField(Object target, String name)
    {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        }
        catch(ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot access " + name + " for B2 probe.",
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
            throw new IllegalStateException("EDT GUI stress action failed", e.getCause());
        }
    }
}
