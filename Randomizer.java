import java.util.Random;

/**
 * Provide control over the randomization of the simulation. By using the shared, fixed-seed 
 * randomizer, repeated runs will perform exactly the same (which helps with testing). Set 
 * 'useShared' to false to get different random behaviour every time.
 * 
 * @author David J. Barnes and Michael Kölling
 * @version 7.0
 */
public class Randomizer
{
    // The default seed for control of randomization.
    private static final long DEFAULT_SEED = 1111L;
    private static long currentSeed = DEFAULT_SEED;
    // A shared Random object, if required.
    private static final Random rand = new Random(DEFAULT_SEED);
    // Determine whether a shared random generator is to be provided.
    private static final boolean useShared = true;

    /**
     * Constructor for objects of class Randomizer
     */
    public Randomizer()
    {
    }

    /**
     * Provide a random generator.
     * @return A random object.
     */
    public static Random getRandom()
    {
        if(useShared) {
            return rand;
        }
        else {
            return new Random();
        }
    }
    
    /**
     * Reset the randomization.
     * This will have no effect if randomization is not 
     * through a shared Random generator.
     */
    public static void reset()
    {
        reset(DEFAULT_SEED);
    }

    public static void reset(long seed)
    {
        currentSeed = seed;
        if(useShared) {
            rand.setSeed(seed);
        }
    }

    public static long getSeed()
    {
        return currentSeed;
    }

    public static long getDefaultSeed()
    {
        return DEFAULT_SEED;
    }
}
