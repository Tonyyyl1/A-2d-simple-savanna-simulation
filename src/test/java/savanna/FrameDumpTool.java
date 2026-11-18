package savanna;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * V0 one-off tool: runs the default world and writes the terrain grid and every
 * step's animal positions for the browser feasibility page. Observation only.
 *
 * <p>Usage: {@code FrameDumpTool <outDir> [steps=200] [seed=1111]}. Writes
 * {@code meta.json}, {@code terrain.bin} (one byte per cell, row-major, the
 * {@link TerrainType} ordinal) and {@code frames.bin} (per step: u32 count,
 * then per animal u32 id, u8 species index, u16 row, u16 col, big endian).</p>
 */
public final class FrameDumpTool
{
    private FrameDumpTool()
    {
    }

    public static void main(String[] args) throws IOException
    {
        Path out = Path.of(args.length > 0 ? args[0] : "build/v0");
        int steps = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 1111L;
        Files.createDirectories(out);
        Simulator simulator = new Simulator(SimulationConfig.builder().randomSeed(seed).build(), false);
        simulator.setVerbose(false);
        TerrainMap terrain = simulator.getContext().getTerrainMap();
        int depth = terrain.getDepth();
        int width = terrain.getWidth();
        byte[] cells = new byte[depth * width];
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                cells[row * width + col] =
                    (byte)terrain.getTerrainAt(new Location(row, col)).ordinal();
            }
        }
        Files.write(out.resolve("terrain.bin"), cells);
        StringBuilder species = new StringBuilder();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            if(species.length() > 0) species.append(',');
            species.append('"').append(factory.getProfile().getName()).append('"');
        }
        StringBuilder types = new StringBuilder();
        for(TerrainType type : TerrainType.values()) {
            if(types.length() > 0) types.append(',');
            types.append('"').append(type.name()).append('"');
        }
        Files.writeString(out.resolve("meta.json"), "{\"depth\":" + depth + ",\"width\":" + width +
            ",\"steps\":" + (steps + 1) + ",\"species\":[" + species + "],\"terrain\":[" + types + "]}\n");
        try(DataOutputStream frames = new DataOutputStream(new java.io.BufferedOutputStream(
            Files.newOutputStream(out.resolve("frames.bin"))))) {
            write(frames, simulator);
            for(int step = 0; step < steps; step++) {
                simulator.simulateOneStep();
                write(frames, simulator);
            }
        }
        System.out.println("FRAME_DUMP steps=" + (steps + 1) + " out=" + out);
    }

    private static void write(DataOutputStream frames, Simulator simulator) throws IOException
    {
        java.util.List<Animal> animals = simulator.getField().getAnimals();
        int living = 0;
        for(Animal animal : animals) if(animal instanceof SavannahAnimal && animal.isAlive()) living++;
        frames.writeInt(living);
        for(Animal animal : animals) {
            if(!(animal instanceof SavannahAnimal) || !animal.isAlive()) continue;
            SavannahAnimal savannah = (SavannahAnimal)animal;
            int index = 0;
            for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
                if(factory.getProfile().getName().equals(savannah.getProfile().getName())) break;
                index++;
            }
            frames.writeInt((int)savannah.getId());
            frames.writeByte(index);
            frames.writeShort(savannah.getLocation().row());
            frames.writeShort(savannah.getLocation().col());
        }
    }
}
