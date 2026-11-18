package savanna;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** v3.2 V1: frames are complete, consistent and side-effect free. */
public final class SimFrameTests
{
    private static final int STEPS = 30;

    private SimFrameTests()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("V1 sim frames");
        run();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    private static final class Recorder implements FrameSink
    {
        final List<SimFrame> frames = new ArrayList<>();
        final List<WorldDescriptor> worlds = new ArrayList<>();
        String endReason;

        @Override public void onWorld(WorldDescriptor world) { worlds.add(world); }
        @Override public void onFrame(SimFrame frame) { frames.add(frame); }
        @Override public void onRunEnd(String reason) { endReason = reason; }
    }

    public static void run()
    {
        TestSupport.startGroup("V1 sim frames");
        Simulator simulator = new Simulator(SimulationConfig.builder().randomSeed(1111L).build(), false);
        simulator.setVerbose(false);
        Recorder recorder = new Recorder();
        simulator.addFrameSink(recorder);
        TestSupport.assertEquals("V1 registering sends the world description once", 1,
            recorder.worlds.size());
        TestSupport.assertEquals("V1 registering sends frame 0", 1, recorder.frames.size());
        for(int step = 0; step < STEPS; step++) simulator.simulateOneStep();
        TestSupport.assertEquals("V1 one frame per step plus frame 0", STEPS + 1,
            recorder.frames.size());
        checkFrames(simulator, recorder);
        checkSideEffectFree();
        TestSupport.finishGroup("V1_SIM_FRAMES");
    }

    private static void checkFrames(Simulator simulator, Recorder recorder)
    {
        List<SimFrame> frames = recorder.frames;
        SimFrame last = frames.get(frames.size() - 1);
        Set<Long> living = new HashSet<>();
        for(Animal animal : simulator.getField().getAnimals()) {
            if(animal.isAlive()) living.add(animal.getId());
        }
        Set<Long> inFrame = new HashSet<>();
        for(long id : last.id) inFrame.add(id);
        TestSupport.assertTrue("V1 the last frame holds exactly the living animals",
            living.equals(inFrame) && last.animalCount == living.size());

        boolean sequenceOk = true;
        boolean prevOk = true;
        boolean killsOk = true;
        boolean birthsOk = true;
        boolean pathsOk = true;
        boolean grassOk = true;
        boolean countsOk = true;
        int teleports = 0;
        TerrainMap terrain = simulator.getContext().getTerrainMap();
        Map<Long, int[]> before = new HashMap<>();
        Set<Long> beforeIds = new HashSet<>();
        for(int index = 0; index < frames.size(); index++) {
            SimFrame frame = frames.get(index);
            sequenceOk &= frame.sequence == index && frame.generation == frames.get(0).generation;
            teleports += frame.teleportCount;
            int total = 0;
            for(int count : frame.speciesCounts) total += count;
            countsOk &= total == frame.animalCount;
            Set<Long> now = new HashSet<>();
            Map<Long, Integer> positionOf = new HashMap<>();
            for(int i = 0; i < frame.animalCount; i++) {
                now.add(frame.id[i]);
                positionOf.put(frame.id[i], i);
                int[] was = before.get(frame.id[i]);
                if(was != null) {
                    prevOk &= frame.prevRow[i] == was[0] && frame.prevCol[i] == was[1];
                }
            }
            int pathOffset = 0;
            for(int i = 0; i < frame.animalCount; i++) {
                int length = frame.pathLength[i];
                int speed = SavannahAnimal.maxMoveDistanceOf(SpeciesRegistry.getFactories()
                    .get(frame.species[i]).getProfile());
                pathsOk &= length <= Math.max(0, speed - 1);
                int prevRow = frame.prevRow[i];
                int prevCol = frame.prevCol[i];
                for(int p = 0; p < length; p++) {
                    int row = frame.pathRow[pathOffset + p];
                    int col = frame.pathCol[pathOffset + p];
                    pathsOk &= terrain.isPassable(new Location(row, col));
                    pathsOk &= Math.max(Math.abs(row - prevRow), Math.abs(col - prevCol)) == 1;
                    prevRow = row;
                    prevCol = col;
                }
                if(length > 0) {
                    pathsOk &= Math.max(Math.abs(frame.row[i] - prevRow),
                        Math.abs(frame.col[i] - prevCol)) == 1;
                }
                pathOffset += length;
            }
            pathsOk &= pathOffset == frame.pathRow.length;
            for(int k = 0; k < frame.killPredator.length; k++) {
                killsOk &= now.contains(frame.killPredator[k]) &&
                    !now.contains(frame.killPrey[k]) && beforeIds.contains(frame.killPrey[k]);
            }
            for(int k = 0; k < frame.birthChild.length; k++) {
                birthsOk &= now.contains(frame.birthChild[k]);
            }
            boolean expectGrass = index == 0 || frame.step % 6 == 0;
            grassOk &= expectGrass == frame.hasGrass();
            if(frame.hasGrass()) grassOk &= frame.grass.length == terrain.getDepth() * terrain.getWidth();
            before = new HashMap<>();
            for(int i = 0; i < frame.animalCount; i++) {
                before.put(frame.id[i], new int[] {frame.row[i], frame.col[i]});
            }
            beforeIds = now;
        }
        TestSupport.assertTrue("V1 sequence numbers are consecutive within one generation", sequenceOk);
        TestSupport.assertTrue("V1 species counts add up to the animal count", countsOk);
        TestSupport.assertTrue("V1 an animal's previous cell is its cell in the previous frame", prevOk);
        TestSupport.assertTrue("V1 every kill: predator present, prey gone and seen before", killsOk);
        TestSupport.assertTrue("V1 every born animal is in its frame", birthsOk);
        TestSupport.assertTrue("V1 path cells are land, 8-adjacent and within speed - 1", pathsOk);
        TestSupport.assertTrue("V1 grass appears on frame 0 and every sixth step only", grassOk);
        TestSupport.assertEquals("V1 no move lacks a land path", 0, teleports);
    }

    /** Attaching a sink must not change the simulation (no random numbers, no state). */
    private static void checkSideEffectFree()
    {
        Map<String, Integer> plain = counts(false);
        Map<String, Integer> watched = counts(true);
        TestSupport.assertEquals("V1 a frame sink leaves the species counts unchanged", plain, watched);
    }

    private static Map<String, Integer> counts(boolean withSink)
    {
        Simulator simulator = new Simulator(SimulationConfig.builder().randomSeed(2111L).build(), false);
        simulator.setVerbose(false);
        if(withSink) simulator.addFrameSink(new Recorder());
        for(int step = 0; step < 40; step++) simulator.simulateOneStep();
        return simulator.getField().countLivingBySpecies();
    }
}
