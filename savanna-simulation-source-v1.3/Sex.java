import java.util.Random;

/**
 * Biological sex used by the breeding system.
 */
public enum Sex
{
    MALE,
    FEMALE;

    /**
     * Choose a sex with an even probability.
     */
    public static Sex random(Random rand)
    {
        return rand.nextBoolean() ? MALE : FEMALE;
    }
}
