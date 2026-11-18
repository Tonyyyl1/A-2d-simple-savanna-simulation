package savanna;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Writes deterministic fixed-size PNGs through the shared OverviewRenderer. */
public final class RenderSnapshotTool
{
    private RenderSnapshotTool()
    {
    }

    public static BufferedImage render(RenderFrame frame, int width, int height)
    {
        return render(frame, width, height, false, HeatmapSelection.defaultSelection());
    }

    public static BufferedImage render(RenderFrame frame, int width, int height,
                                       boolean includeHeatmap,
                                       SimulationEvent.EventType heatmapEventType)
    {
        return render(frame, width, height, includeHeatmap,
            new HeatmapSelection(HeatmapWindow.WHOLE_RUN, heatmapEventType, null));
    }

    public static BufferedImage render(RenderFrame frame, int width, int height,
                                       boolean includeHeatmap,
                                       HeatmapSelection heatmapSelection)
    {
        return renderLayers(frame, width, height, includeHeatmap,
            heatmapSelection).getImage();
    }

    /** Returns the completed layers used by the default fixed-PNG path. */
    static OverviewRenderResult renderLayers(RenderFrame frame, int width, int height,
                                             boolean includeHeatmap,
                                             HeatmapSelection heatmapSelection)
    {
        return renderLayers(frame, width, height, includeHeatmap,
            heatmapSelection, TerrainBackgroundCache.shared());
    }

    /**
     * Returns the same layers that are combined for a fixed PNG.  Supplying a
     * cache lets an export share the exact completed terrain bitmap already
     * used by an overview/inspect frame in the same JVM.
     */
    static OverviewRenderResult renderLayers(RenderFrame frame, int width, int height,
                                             boolean includeHeatmap,
                                             HeatmapSelection heatmapSelection,
                                             TerrainBackgroundCache terrainBackgroundCache)
    {
        return new OverviewRenderer(terrainBackgroundCache)
            .render(frame, new RenderOptions(width, height, true, true,
                                             includeHeatmap, heatmapSelection));
    }

    public static void writePng(RenderFrame frame, int width, int height,
                                File output) throws IOException
    {
        if(output == null) {
            throw new IllegalArgumentException("A PNG output file is required.");
        }
        File parent = output.getAbsoluteFile().getParentFile();
        if(parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create output directory: " + parent);
        }
        if(!ImageIO.write(render(frame, width, height), "png", output)) {
            throw new IOException("No PNG writer is available.");
        }
    }

    public static void main(String[] args) throws Exception
    {
        if(args.length < 1 || args.length > 4) {
            System.err.println("usage: java RenderSnapshotTool <output.png> " +
                               "[steps] [width] [height]");
            System.exit(2);
        }
        int steps = args.length >= 2 ? Integer.parseInt(args[1]) : 100;
        int width = args.length >= 3 ? Integer.parseInt(args[2]) : 1387;
        int height = args.length >= 4 ? Integer.parseInt(args[3]) : 829;
        Simulator simulator = new Simulator(SimulationConfig.default3x(), false);
        simulator.setVerbose(false);
        simulator.simulate(Math.max(0, steps));
        RenderFrame frame = simulator.captureVisibleRenderFrame();
        writePng(frame, width, height, new File(args[0]));
        System.out.println("Wrote shared-frame PNG: " +
                           new File(args[0]).getAbsolutePath());
    }
}
