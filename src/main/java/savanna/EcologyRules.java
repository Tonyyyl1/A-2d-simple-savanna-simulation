package savanna;

/**
 * Version of the ecological rule set. Bump it in the same commit as any
 * intentional change to ecological semantics (plan v3.0 §2.5); runners and
 * reports print it so results can be tied to the rules that produced them.
 */
public final class EcologyRules
{
    public static final String VERSION = "v3.3-p1-herbivore";

    private EcologyRules()
    {
    }
}
