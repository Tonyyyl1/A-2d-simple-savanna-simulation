package savanna;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Binary form of {@link SimFrame}: protocol v1, plan v3.2 appendix B. All
 * integers are little endian. A frame is the 80-byte header followed by the
 * animal, path, kill, birth, death and grass sections in that order.
 */
public final class SimFrameCodec
{
    public static final int VERSION = 1;
    public static final int HEADER_BYTES = 80;
    public static final int ANIMAL_BYTES = 28;
    public static final int KILL_BYTES = 12;
    public static final int BIRTH_BYTES = 8;
    public static final int DEATH_BYTES = 12;
    private static final int MAGIC = 'S' | ('V' << 8) | ('F' << 16) | ('1' << 24);
    private static final long ID_LIMIT = 0xFFFFFFFFL;

    private SimFrameCodec()
    {
    }

    public static byte[] encode(SimFrame frame)
    {
        int animals = frame.animalCount;
        int paths = frame.pathCol.length;
        int grass = frame.grass == null ? 0 : frame.grass.length;
        int size = HEADER_BYTES + animals * ANIMAL_BYTES + paths * 4 +
            frame.killPredator.length * KILL_BYTES + frame.birthMother.length * BIRTH_BYTES +
            frame.deathId.length * DEATH_BYTES + grass;
        ByteBuffer out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(MAGIC);
        out.putShort((short)VERSION);
        out.putShort((short)HEADER_BYTES);
        out.putInt(frame.generation);
        out.putInt(frame.sequence);
        out.putInt(frame.step);
        out.put((byte)frame.hour);
        out.put((byte)frame.phase);
        out.put((byte)frame.weather);
        out.put((byte)frame.flags);
        out.putInt(animals);
        out.putInt(paths);
        out.putInt(frame.killPredator.length);
        out.putInt(frame.birthMother.length);
        out.putInt(frame.deathId.length);
        out.putInt(grass);
        out.putInt(frame.teleportCount);
        for(int count : frame.speciesCounts) out.putInt(count);
        out.putLong(0L);
        for(int i = 0; i < animals; i++) {
            out.putInt(id32(frame.id[i]));
            out.put(frame.species[i]);
            out.put(frame.animalFlags[i]);
            out.putShort(frame.col[i]);
            out.putShort(frame.row[i]);
            out.putShort(frame.prevCol[i]);
            out.putShort(frame.prevRow[i]);
            out.put(frame.action[i]);
            out.put(frame.reason[i]);
            out.put(frame.effects[i]);
            out.put(frame.moveResult[i]);
            out.putShort(frame.age1000[i]);
            out.put(frame.food[i]);
            out.put(frame.stamina[i]);
            out.put(frame.water[i]);
            out.put(frame.pathLength[i]);
            out.putInt(id32(frame.target[i]));
        }
        for(int i = 0; i < paths; i++) {
            out.putShort(frame.pathCol[i]);
            out.putShort(frame.pathRow[i]);
        }
        for(int i = 0; i < frame.killPredator.length; i++) {
            out.putInt(id32(frame.killPredator[i]));
            out.putInt(id32(frame.killPrey[i]));
            out.putShort(frame.killCol[i]);
            out.putShort(frame.killRow[i]);
        }
        for(int i = 0; i < frame.birthMother.length; i++) {
            out.putInt(id32(frame.birthMother[i]));
            out.putInt(id32(frame.birthChild[i]));
        }
        for(int i = 0; i < frame.deathId.length; i++) {
            out.putInt(id32(frame.deathId[i]));
            out.putShort(frame.deathCol[i]);
            out.putShort(frame.deathRow[i]);
            out.put(frame.deathSpecies[i]);
            out.put(frame.deathCause[i]);
            out.put(frame.deathFlags[i]);
            out.put((byte)0);
        }
        if(frame.grass != null) out.put(frame.grass);
        return out.array();
    }

    public static SimFrame decode(byte[] bytes)
    {
        ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if(bytes.length < HEADER_BYTES || in.getInt() != MAGIC) {
            throw new IllegalArgumentException("not a simulation frame");
        }
        int version = in.getShort() & 0xFFFF;
        int header = in.getShort() & 0xFFFF;
        if(version != VERSION || header != HEADER_BYTES) {
            throw new IllegalArgumentException("unsupported frame protocol version " + version);
        }
        SimFrame.Builder b = new SimFrame.Builder();
        b.generation = in.getInt();
        b.sequence = in.getInt();
        b.step = in.getInt();
        b.hour = in.get() & 0xFF;
        b.phase = in.get() & 0xFF;
        b.weather = in.get() & 0xFF;
        b.flags = in.get() & 0xFF;
        int animals = in.getInt();
        int paths = in.getInt();
        int kills = in.getInt();
        int births = in.getInt();
        int deaths = in.getInt();
        int grass = in.getInt();
        b.teleportCount = in.getInt();
        for(int i = 0; i < 5; i++) b.speciesCounts[i] = in.getInt();
        in.getLong();
        long expected = HEADER_BYTES + (long)animals * ANIMAL_BYTES + paths * 4L +
            kills * (long)KILL_BYTES + births * (long)BIRTH_BYTES + deaths * (long)DEATH_BYTES + grass;
        if(expected != bytes.length) {
            throw new IllegalArgumentException("frame length " + bytes.length +
                " does not match its header (" + expected + ")");
        }
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
        for(int i = 0; i < animals; i++) {
            b.id[i] = in.getInt() & ID_LIMIT;
            b.species[i] = in.get();
            b.animalFlags[i] = in.get();
            b.col[i] = in.getShort();
            b.row[i] = in.getShort();
            b.prevCol[i] = in.getShort();
            b.prevRow[i] = in.getShort();
            b.action[i] = in.get();
            b.reason[i] = in.get();
            b.effects[i] = in.get();
            b.moveResult[i] = in.get();
            b.age1000[i] = in.getShort();
            b.food[i] = in.get();
            b.stamina[i] = in.get();
            b.water[i] = in.get();
            b.pathLength[i] = in.get();
            b.target[i] = in.getInt() & ID_LIMIT;
        }
        b.pathCol = new short[paths];
        b.pathRow = new short[paths];
        for(int i = 0; i < paths; i++) {
            b.pathCol[i] = in.getShort();
            b.pathRow[i] = in.getShort();
        }
        b.killPredator = new long[kills];
        b.killPrey = new long[kills];
        b.killCol = new short[kills];
        b.killRow = new short[kills];
        for(int i = 0; i < kills; i++) {
            b.killPredator[i] = in.getInt() & ID_LIMIT;
            b.killPrey[i] = in.getInt() & ID_LIMIT;
            b.killCol[i] = in.getShort();
            b.killRow[i] = in.getShort();
        }
        b.birthMother = new long[births];
        b.birthChild = new long[births];
        for(int i = 0; i < births; i++) {
            b.birthMother[i] = in.getInt() & ID_LIMIT;
            b.birthChild[i] = in.getInt() & ID_LIMIT;
        }
        b.deathId = new long[deaths];
        b.deathCol = new short[deaths];
        b.deathRow = new short[deaths];
        b.deathSpecies = new byte[deaths];
        b.deathCause = new byte[deaths];
        b.deathFlags = new byte[deaths];
        for(int i = 0; i < deaths; i++) {
            b.deathId[i] = in.getInt() & ID_LIMIT;
            b.deathCol[i] = in.getShort();
            b.deathRow[i] = in.getShort();
            b.deathSpecies[i] = in.get();
            b.deathCause[i] = in.get();
            b.deathFlags[i] = in.get();
            in.get();
        }
        if(grass > 0) {
            b.grass = new byte[grass];
            in.get(b.grass);
        }
        return b.build();
    }

    private static int id32(long id)
    {
        if(id < 0L || id > ID_LIMIT) {
            throw new IllegalArgumentException("animal id does not fit 32 bits: " + id);
        }
        return (int)id;
    }
}
