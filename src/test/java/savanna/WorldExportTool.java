package savanna;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * V2 tool for the web tests: writes {@code world.json}, {@code terrain.bin} of the
 * default map and a 20-animal reference frame {@code golden-v1.bin} with its
 * field-by-field JSON {@code golden-v1.json}. Usage: {@code WorldExportTool <outDir>}.
 */
public final class WorldExportTool
{
    private WorldExportTool()
    {
    }

    public static void main(String[] args) throws IOException
    {
        Path out = Path.of(args.length > 0 ? args[0] : "build/web-test");
        Files.createDirectories(out);
        Simulator simulator = new Simulator(SimulationConfig.builder().randomSeed(1111L).build(), false);
        simulator.setVerbose(false);
        final WorldDescriptor[] world = new WorldDescriptor[1];
        simulator.addFrameSink(new FrameSink() {
            @Override public void onWorld(WorldDescriptor w) { world[0] = w; }
            @Override public void onFrame(SimFrame frame) { }
            @Override public void onRunEnd(String reason) { }
        });
        Files.write(out.resolve("world.json"), WorldJson.toJson(world[0]));
        Files.write(out.resolve("terrain.bin"), world[0].terrainBytes());
        Files.write(out.resolve("height.bin"), world[0].heightBytes());
        Files.write(out.resolve("waterbody.bin"), world[0].bodyBytes());
        SimFrame golden = goldenFrame();
        Files.write(out.resolve("golden-v1.bin"), SimFrameCodec.encode(golden));
        Files.writeString(out.resolve("golden-v1.json"), toJson(golden));
        System.out.println("WORLD_EXPORT out=" + out);
    }

    /** Fixed content: 20 animals with paths, two kills, a birth, two deaths and a 12-byte grass block. */
    static SimFrame goldenFrame()
    {
        Random random = new Random(20261111L);
        int animals = 20;
        SimFrame.Builder b = new SimFrame.Builder();
        b.generation = 2;
        b.sequence = 7;
        b.step = 55;
        b.hour = 7;
        b.phase = 0;
        b.weather = 1;
        b.flags = SimFrame.FLAG_THIRST | SimFrame.FLAG_GRASS;
        b.speciesCounts = new int[] {4, 3, 5, 4, 4};
        b.teleportCount = 0;
        b.id = new long[animals];
        b.species = new byte[animals];
        b.animalFlags = new byte[animals];
        b.col = new short[animals];
        b.row = new short[animals];
        b.prevCol = new short[animals];
        b.prevRow = new short[animals];
        b.action = new byte[animals];
        b.reason = new byte[animals];
        b.effects = new byte[animals];
        b.moveResult = new byte[animals];
        b.age1000 = new short[animals];
        b.food = new byte[animals];
        b.stamina = new byte[animals];
        b.water = new byte[animals];
        b.pathLength = new byte[animals];
        b.target = new long[animals];
        int paths = 0;
        for(int i = 0; i < animals; i++) {
            b.id[i] = 100L + i * 7L;
            b.species[i] = (byte)(i % 5);
            b.animalFlags[i] = (byte)random.nextInt(256);
            b.col[i] = (short)random.nextInt(360);
            b.row[i] = (short)random.nextInt(240);
            b.prevCol[i] = (short)random.nextInt(360);
            b.prevRow[i] = (short)random.nextInt(240);
            b.action[i] = (byte)random.nextInt(9);
            b.reason[i] = (byte)random.nextInt(13);
            b.effects[i] = (byte)random.nextInt(128);
            b.moveResult[i] = (byte)random.nextInt(6);
            b.age1000[i] = (short)random.nextInt(1001);
            b.food[i] = (byte)random.nextInt(101);
            b.stamina[i] = (byte)random.nextInt(101);
            b.water[i] = (byte)random.nextInt(101);
            b.pathLength[i] = (byte)(i % 4 == 0 ? 2 : 0);
            b.target[i] = i % 3 == 0 ? 0L : 100L + random.nextInt(100);
            paths += b.pathLength[i];
        }
        b.pathCol = new short[paths];
        b.pathRow = new short[paths];
        for(int i = 0; i < paths; i++) {
            b.pathCol[i] = (short)random.nextInt(360);
            b.pathRow[i] = (short)random.nextInt(240);
        }
        b.killPredator = new long[] {100L, 107L};
        b.killPrey = new long[] {114L, 121L};
        b.killCol = new short[] {10, 359};
        b.killRow = new short[] {20, 239};
        b.birthMother = new long[] {128L};
        b.birthChild = new long[] {4_000_000_000L};
        b.deathId = new long[] {114L, 121L};
        b.deathCol = new short[] {10, 359};
        b.deathRow = new short[] {20, 239};
        b.deathSpecies = new byte[] {4, 2};
        b.deathCause = new byte[] {5, 5};
        b.deathFlags = new byte[] {5, 2};
        b.grass = new byte[] {0, 1, 2, (byte)128, (byte)254, (byte)255, 7, 8, 9, 10, 11, 12};
        return b.build();
    }

    static String toJson(SimFrame f)
    {
        StringBuilder s = new StringBuilder("{");
        num(s, "generation", f.generation).append(',');
        num(s, "sequence", f.sequence).append(',');
        num(s, "step", f.step).append(',');
        num(s, "hour", f.hour).append(',');
        num(s, "phase", f.phase).append(',');
        num(s, "weather", f.weather).append(',');
        num(s, "flags", f.flags).append(',');
        ints(s, "speciesCounts", f.speciesCounts).append(',');
        num(s, "teleportCount", f.teleportCount).append(',');
        longs(s, "id", f.id).append(',');
        bytes(s, "species", f.species).append(',');
        bytes(s, "animalFlags", f.animalFlags).append(',');
        shorts(s, "col", f.col).append(',');
        shorts(s, "row", f.row).append(',');
        shorts(s, "prevCol", f.prevCol).append(',');
        shorts(s, "prevRow", f.prevRow).append(',');
        bytes(s, "action", f.action).append(',');
        bytes(s, "reason", f.reason).append(',');
        bytes(s, "effects", f.effects).append(',');
        bytes(s, "moveResult", f.moveResult).append(',');
        shorts(s, "age1000", f.age1000).append(',');
        bytes(s, "food", f.food).append(',');
        bytes(s, "stamina", f.stamina).append(',');
        bytes(s, "water", f.water).append(',');
        bytes(s, "pathLength", f.pathLength).append(',');
        longs(s, "target", f.target).append(',');
        shorts(s, "pathCol", f.pathCol).append(',');
        shorts(s, "pathRow", f.pathRow).append(',');
        longs(s, "killPredator", f.killPredator).append(',');
        longs(s, "killPrey", f.killPrey).append(',');
        shorts(s, "killCol", f.killCol).append(',');
        shorts(s, "killRow", f.killRow).append(',');
        longs(s, "birthMother", f.birthMother).append(',');
        longs(s, "birthChild", f.birthChild).append(',');
        longs(s, "deathId", f.deathId).append(',');
        shorts(s, "deathCol", f.deathCol).append(',');
        shorts(s, "deathRow", f.deathRow).append(',');
        bytes(s, "deathSpecies", f.deathSpecies).append(',');
        bytes(s, "deathCause", f.deathCause).append(',');
        bytes(s, "deathFlags", f.deathFlags).append(',');
        if(f.grass == null) s.append("\"grass\":null");
        else bytes(s, "grass", f.grass);
        return s.append("}\n").toString();
    }

    private static StringBuilder num(StringBuilder s, String key, long value)
    {
        return s.append('"').append(key).append("\":").append(value);
    }

    private static StringBuilder ints(StringBuilder s, String key, int[] values)
    {
        s.append('"').append(key).append("\":[");
        for(int i = 0; i < values.length; i++) s.append(i > 0 ? "," : "").append(values[i]);
        return s.append(']');
    }

    private static StringBuilder longs(StringBuilder s, String key, long[] values)
    {
        s.append('"').append(key).append("\":[");
        for(int i = 0; i < values.length; i++) s.append(i > 0 ? "," : "").append(values[i]);
        return s.append(']');
    }

    private static StringBuilder bytes(StringBuilder s, String key, byte[] values)
    {
        s.append('"').append(key).append("\":[");
        for(int i = 0; i < values.length; i++) s.append(i > 0 ? "," : "").append(values[i] & 0xFF);
        return s.append(']');
    }

    private static StringBuilder shorts(StringBuilder s, String key, short[] values)
    {
        s.append('"').append(key).append("\":[");
        for(int i = 0; i < values.length; i++) s.append(i > 0 ? "," : "").append(values[i] & 0xFFFF);
        return s.append(']');
    }
}
