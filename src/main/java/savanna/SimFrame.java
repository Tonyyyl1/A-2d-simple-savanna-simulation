package savanna;

/**
 * One complete, immutable snapshot of a simulation step (plan v3.2 V1, wire
 * layout in appendix B). The arrays are shared with readers and must never be
 * modified; field names follow the appendix. A frame never holds a live kernel
 * object, so a consumer sees exactly what the kernel committed.
 */
public final class SimFrame
{
    public static final int FLAG_THIRST = 1;
    public static final int FLAG_GRASS = 2;
    public static final int FLAG_LAST = 4;

    public final int generation;
    public final int sequence;
    public final int step;
    public final int hour;
    public final int phase;
    public final int weather;
    public final int flags;
    public final int[] speciesCounts;
    /** Moves for which no land path of at most 6 steps exists (0 after B-1). */
    public final int teleportCount;

    public final int animalCount;
    public final long[] id;
    public final byte[] species;
    public final byte[] animalFlags;
    public final short[] col;
    public final short[] row;
    public final short[] prevCol;
    public final short[] prevRow;
    public final byte[] action;
    public final byte[] reason;
    public final byte[] effects;
    public final byte[] moveResult;
    public final short[] age1000;
    public final byte[] food;
    public final byte[] stamina;
    public final byte[] water;
    public final byte[] pathLength;
    public final long[] target;

    /** Intermediate path cells of all animals, in animal order. */
    public final short[] pathCol;
    public final short[] pathRow;

    public final long[] killPredator;
    public final long[] killPrey;
    public final short[] killCol;
    public final short[] killRow;

    public final long[] birthMother;
    public final long[] birthChild;

    public final long[] deathId;
    public final short[] deathCol;
    public final short[] deathRow;
    public final byte[] deathSpecies;
    public final byte[] deathCause;
    public final byte[] deathFlags;

    /** Quantized grass fill (depth x width), or null when this frame carries none. */
    public final byte[] grass;

    SimFrame(Builder b)
    {
        generation = b.generation;
        sequence = b.sequence;
        step = b.step;
        hour = b.hour;
        phase = b.phase;
        weather = b.weather;
        flags = b.flags;
        speciesCounts = b.speciesCounts;
        teleportCount = b.teleportCount;
        animalCount = b.id.length;
        id = b.id;
        species = b.species;
        animalFlags = b.animalFlags;
        col = b.col;
        row = b.row;
        prevCol = b.prevCol;
        prevRow = b.prevRow;
        action = b.action;
        reason = b.reason;
        effects = b.effects;
        moveResult = b.moveResult;
        age1000 = b.age1000;
        food = b.food;
        stamina = b.stamina;
        water = b.water;
        pathLength = b.pathLength;
        target = b.target;
        pathCol = b.pathCol;
        pathRow = b.pathRow;
        killPredator = b.killPredator;
        killPrey = b.killPrey;
        killCol = b.killCol;
        killRow = b.killRow;
        birthMother = b.birthMother;
        birthChild = b.birthChild;
        deathId = b.deathId;
        deathCol = b.deathCol;
        deathRow = b.deathRow;
        deathSpecies = b.deathSpecies;
        deathCause = b.deathCause;
        deathFlags = b.deathFlags;
        grass = b.grass;
    }

    public boolean hasGrass()
    {
        return grass != null;
    }

    public boolean isLast()
    {
        return (flags & FLAG_LAST) != 0;
    }

    /** Plain field holder used by {@link SimFrameBuilder} and the codec. */
    static final class Builder
    {
        int generation;
        int sequence;
        int step;
        int hour;
        int phase;
        int weather;
        int flags;
        int[] speciesCounts = new int[5];
        int teleportCount;
        long[] id = new long[0];
        byte[] species = new byte[0];
        byte[] animalFlags = new byte[0];
        short[] col = new short[0];
        short[] row = new short[0];
        short[] prevCol = new short[0];
        short[] prevRow = new short[0];
        byte[] action = new byte[0];
        byte[] reason = new byte[0];
        byte[] effects = new byte[0];
        byte[] moveResult = new byte[0];
        short[] age1000 = new short[0];
        byte[] food = new byte[0];
        byte[] stamina = new byte[0];
        byte[] water = new byte[0];
        byte[] pathLength = new byte[0];
        long[] target = new long[0];
        short[] pathCol = new short[0];
        short[] pathRow = new short[0];
        long[] killPredator = new long[0];
        long[] killPrey = new long[0];
        short[] killCol = new short[0];
        short[] killRow = new short[0];
        long[] birthMother = new long[0];
        long[] birthChild = new long[0];
        long[] deathId = new long[0];
        short[] deathCol = new short[0];
        short[] deathRow = new short[0];
        byte[] deathSpecies = new byte[0];
        byte[] deathCause = new byte[0];
        byte[] deathFlags = new byte[0];
        byte[] grass;

        SimFrame build()
        {
            return new SimFrame(this);
        }
    }
}
