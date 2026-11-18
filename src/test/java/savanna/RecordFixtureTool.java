package savanna;

import java.io.IOException;
import java.nio.file.Path;

/** Records a headless run into a .savrec file: {@code RecordFixtureTool <seed> <steps> <file>}. */
public final class RecordFixtureTool
{
    private RecordFixtureTool()
    {
    }

    public static void main(String[] args) throws IOException
    {
        Simulator simulator = new Simulator(SimulationConfig.builder()
            .randomSeed(Long.parseLong(args[0])).build(), false);
        simulator.setVerbose(false);
        FrameRecorder recorder = new FrameRecorder(Path.of(args[2]));
        simulator.addFrameSink(recorder);
        int steps = Integer.parseInt(args[1]);
        for(int step = 0; step < steps && simulator.getField().isViable(); step++) simulator.simulateOneStep();
        recorder.close();
        System.out.println("RECORDED steps=" + simulator.getStep() + " file=" + args[2]);
    }
}
