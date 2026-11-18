package savanna;

/**
 * Paid-reproduction parameters (plan v3.0 M1-2, decisions D4/D5).
 *
 * <p>A conception reserves {@link #childReserveFood} food per litter
 * ordinal from the mother, who must keep {@link #maternalFloor}. Newborn
 * hydration and stamina are a declared birth allowance rather than a
 * maternal payment.</p>
 */
public final class ReproductionPolicy
{
    public static final int MAX_BIRTH_WAIT_STEPS = 12;
    public static final double NEWBORN_HYDRATION_PERCENT = 70.0;
    public static final double NEWBORN_STAMINA_PERCENT = 60.0;
    static final long MICROS_PER_PERCENT = 1_000_000L;

    private ReproductionPolicy()
    {
    }

    /** D4 values live in {@link EcologyParameters} (plan v3.0 M3-0). */
    public static int gestationSteps(SpeciesProfile profile)
    {
        return EcologyParameters.current().getInt(profile.isPredator()
            ? EcologyParameters.Key.PREDATOR_GESTATION
            : EcologyParameters.Key.HERBIVORE_GESTATION);
    }

    /** Postpartum cooldown: a fraction of the gestation length (D4). */
    public static int cooldownSteps(SpeciesProfile profile)
    {
        return (int)Math.round(gestationSteps(profile) *
            EcologyParameters.current().get(EcologyParameters.Key.COOLDOWN_FACTOR));
    }

    public static int childReserveFood(SpeciesProfile profile)
    {
        EcologyParameters p = EcologyParameters.current();
        if(!profile.isPredator()) {
            return (int)Math.ceil(profile.getMaxFoodLevel() *
                p.get(EcologyParameters.Key.HERBIVORE_RESERVE_FRACTION));
        }
        double fraction = p.get(SpeciesRegistry.CHEETAH.equals(profile.getName())
            ? EcologyParameters.Key.CHEETAH_RESERVE_FRACTION
            : EcologyParameters.Key.PREDATOR_RESERVE_FRACTION);
        int byFraction = (int)Math.ceil(profile.getMaxFoodLevel() * fraction);
        return Math.max(1, Math.max(byFraction, profile.getAdultAge() +
            p.getInt(EcologyParameters.Key.PREDATOR_ADULT_MARGIN)));
    }

    /**
     * Explicit multiplier on the per-step conception probability (M1.6). It
     * replaces, as one visible constant per trophic group, the bonus the
     * removed global low-population and population-balance modifiers added.
     */
    public static double conceptionRateMultiplier(SpeciesProfile profile)
    {
        return EcologyParameters.current().get(profile.isPredator()
            ? EcologyParameters.Key.PREDATOR_CONCEPTION_MULTIPLIER
            : EcologyParameters.Key.HERBIVORE_CONCEPTION_MULTIPLIER);
    }

    /** Food the mother must keep for herself after paying every reserve. */
    public static int maternalFloor(SpeciesProfile profile)
    {
        return profile.getMaxFoodLevel() / 3;
    }

    /** Largest litter the mother can pay for now; 0 means she cannot conceive. */
    public static int affordableLitter(SpeciesProfile profile, int foodLevel)
    {
        int spare = foodLevel - maternalFloor(profile);
        return spare <= 0 ? 0 : spare / childReserveFood(profile);
    }

    /** Escrow of one ordinal: paid food plus the declared newborn allowance. */
    static ResourceVector ordinalEscrow(SpeciesProfile profile)
    {
        return new ResourceVector(childReserveFood(profile),
            micros(NEWBORN_HYDRATION_PERCENT), micros(NEWBORN_STAMINA_PERCENT), 0L);
    }

    static ResourceVector newbornAllowance()
    {
        return new ResourceVector(0L, micros(NEWBORN_HYDRATION_PERCENT),
            micros(NEWBORN_STAMINA_PERCENT), 0L);
    }

    static double percent(long micros)
    {
        return (double)micros / MICROS_PER_PERCENT;
    }

    private static long micros(double percent)
    {
        return Math.round(percent * MICROS_PER_PERCENT);
    }
}
