package savanna;

import java.util.Arrays;

/** Headless timing aid for isolating shared-frame capture and rendering cost. */
public final class RenderPerformanceDiagnostic
{
    private RenderPerformanceDiagnostic() {}

    public static void main(String[] args)
    {
        Simulator simulator = new Simulator(SimulationConfig.default3x(), false);
        simulator.setVerbose(false);
        OverviewRenderer renderer = new OverviewRenderer();
        RenderOptions options = new RenderOptions(1732, 948, true);
        for(int index = 0; index < 5; index++) {
            renderer.render(simulator.getRenderFramePublisher().latest(), options);
        }
        long[] capture = new long[20];
        long[] render = new long[20];
        for(int index = 0; index < render.length; index++) {
            long started = System.nanoTime();
            RenderFrame frame = RenderFrame.capture(simulator.getStep(),
                simulator.getField(), simulator.getContext(),
                simulator.getField().getWidth(), simulator.getField().getDepth());
            capture[index] = System.nanoTime() - started;
            started = System.nanoTime();
            renderer.render(frame, options);
            render[index] = System.nanoTime() - started;
        }
        Arrays.sort(capture);
        Arrays.sort(render);
        System.out.printf("RenderPerformanceDiagnostic capture-p95=%.2f ms " +
                          "render-p95=%.2f ms render-max=%.2f ms%n",
            millis(capture[18]), millis(render[18]), millis(render[19]));
    }

    private static double millis(long nanos)
    {
        return nanos / 1_000_000.0;
    }
}
