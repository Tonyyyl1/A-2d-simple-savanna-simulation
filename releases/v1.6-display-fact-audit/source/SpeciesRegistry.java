import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The single place where the savanna species are configured.
 */
public class SpeciesRegistry
{
    public static final String LION = "Lion";
    public static final String CHEETAH = "Cheetah";
    public static final String ZEBRA = "Zebra";
    public static final String BUFFALO = "Buffalo";
    public static final String GAZELLE = "Gazelle";

    private static final List<SpeciesFactory> FACTORIES = new ArrayList<>();

    private static final SpeciesProfile LION_PROFILE =
        new BasicSpeciesProfile(LION, new Color(218, 132, 31), true,
            Set.of(GAZELLE, ZEBRA, BUFFALO),
            EnumSet.of(DayPhase.DAWN, DayPhase.DUSK, DayPhase.NIGHT),
            0.005, 24, 12, 30, 36, 680,
            0.038, 2, 6, 6, 190,
            105, 0.48, 0.84, 0.23, 0.09, 0.00, 0);

    private static final SpeciesProfile CHEETAH_PROFILE =
        new BasicSpeciesProfile(CHEETAH, new Color(230, 194, 47), true,
            Set.of(GAZELLE),
            EnumSet.of(DayPhase.DAWN, DayPhase.DAY, DayPhase.DUSK),
            0.005, 28, 10, 24, 30, 640,
            0.058, 2, 6, 7, 112,
            56, 0.62, 0.78, 0.26, 0.13, 0.00, 0);

    private static final SpeciesProfile ZEBRA_PROFILE =
        new BasicSpeciesProfile(ZEBRA, new Color(42, 105, 189), false,
            Set.of(), EnumSet.of(DayPhase.DAWN, DayPhase.DAY, DayPhase.DUSK),
            0.042, 46, 8, 20, 26, 720,
            0.068, 1, 4, 1, 68,
            42, 0.00, 0.79, 0.24, 0.46, 0.12, 9);

    private static final SpeciesProfile BUFFALO_PROFILE =
        new BasicSpeciesProfile(BUFFALO, new Color(72, 68, 62), false,
            Set.of(), EnumSet.of(DayPhase.DAWN, DayPhase.DAY, DayPhase.DUSK),
            0.036, 42, 10, 28, 38, 820,
            0.056, 1, 4, 1, 78,
            66, 0.00, 0.88, 0.18, 0.22, 0.15, 10);

    private static final SpeciesProfile GAZELLE_PROFILE =
        new BasicSpeciesProfile(GAZELLE, new Color(198, 48, 55), false,
            Set.of(), EnumSet.of(DayPhase.DAWN, DayPhase.DAY, DayPhase.DUSK),
            0.060, 74, 6, 14, 20, 600,
            0.128, 2, 4, 1, 58,
            34, 0.00, 0.72, 0.32, 0.50, 0.12, 9);

    static {
        FACTORIES.add(new SpeciesFactory() {
            public SpeciesProfile getProfile() { return LION_PROFILE; }
            public SavannahAnimal create(boolean randomAge, Location location,
                                         java.util.Random rand)
            {
                return new Lion(randomAge, location, rand);
            }
        });
        FACTORIES.add(new SpeciesFactory() {
            public SpeciesProfile getProfile() { return CHEETAH_PROFILE; }
            public SavannahAnimal create(boolean randomAge, Location location,
                                         java.util.Random rand)
            {
                return new Cheetah(randomAge, location, rand);
            }
        });
        FACTORIES.add(new SpeciesFactory() {
            public SpeciesProfile getProfile() { return ZEBRA_PROFILE; }
            public SavannahAnimal create(boolean randomAge, Location location,
                                         java.util.Random rand)
            {
                return new Zebra(randomAge, location, rand);
            }
        });
        FACTORIES.add(new SpeciesFactory() {
            public SpeciesProfile getProfile() { return BUFFALO_PROFILE; }
            public SavannahAnimal create(boolean randomAge, Location location,
                                         java.util.Random rand)
            {
                return new Buffalo(randomAge, location, rand);
            }
        });
        FACTORIES.add(new SpeciesFactory() {
            public SpeciesProfile getProfile() { return GAZELLE_PROFILE; }
            public SavannahAnimal create(boolean randomAge, Location location,
                                         java.util.Random rand)
            {
                return new Gazelle(randomAge, location, rand);
            }
        });
    }

    /**
     * @return All species factories in the simulation.
     */
    public static List<SpeciesFactory> getFactories()
    {
        return new ArrayList<>(FACTORIES);
    }

    /**
     * Find a species profile by name.
     */
    public static SpeciesProfile getProfile(String name)
    {
        for(SpeciesFactory factory : FACTORIES) {
            if(factory.getProfile().getName().equals(name)) {
                return factory.getProfile();
            }
        }
        return null;
    }
}
