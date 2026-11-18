package savanna;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Target population pyramid and the normalized balance measure (plan v3.2 C-0,
 * user decision U6). The targets belong to the default map; maps with another
 * number of passable cells scale them in proportion.
 */
public final class EcologyTargets
{
    /** Passable cells of the default map (LEGACY_V1, terrain seed 20260629, 240 x 360). */
    public static final int REFERENCE_PASSABLE_CELLS = 84_667;

    private static final Map<String, Double> TARGETS = new LinkedHashMap<>();

    static {
        TARGETS.put(SpeciesRegistry.LION, 100.0);
        TARGETS.put(SpeciesRegistry.CHEETAH, 60.0);
        TARGETS.put(SpeciesRegistry.ZEBRA, 2000.0);
        TARGETS.put(SpeciesRegistry.BUFFALO, 1200.0);
        TARGETS.put(SpeciesRegistry.GAZELLE, 3000.0);
    }

    private EcologyTargets()
    {
    }

    /** Target count on the default map. */
    public static double target(String species)
    {
        Double value = TARGETS.get(species);
        if(value == null) throw new IllegalArgumentException("no target for " + species);
        return value.doubleValue();
    }

    /** Scale factor from the default map to a map with {@code passableCells} land cells. */
    public static double scale(int passableCells)
    {
        return (double)passableCells / REFERENCE_PASSABLE_CELLS;
    }

    /**
     * Largest over smallest of {@code mean / (target * scale)} across the
     * participating species; infinite when a participant's mean is not positive
     * (or nothing participates).
     */
    public static double normalizedBalance(Map<String, Double> means,
                                           Set<String> participants, double scale)
    {
        double min = Double.POSITIVE_INFINITY;
        double max = 0.0;
        int counted = 0;
        for(String species : participants) {
            double mean = means.getOrDefault(species, 0.0);
            if(mean <= 0.0) return Double.POSITIVE_INFINITY;
            double ratio = mean / (target(species) * scale);
            min = Math.min(min, ratio);
            max = Math.max(max, ratio);
            counted++;
        }
        return counted == 0 ? Double.POSITIVE_INFINITY : max / min;
    }
}
