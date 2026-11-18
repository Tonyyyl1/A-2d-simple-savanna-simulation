package savanna;

/** Immutable configuration for the optional thirst system. */
public final class HydrationPolicy
{
    public static final int DEFAULT_DEHYDRATION_GRACE_STEPS = 18;

    private static final HydrationPolicy DISABLED = new HydrationPolicy(false,
        0.24, 30.0, 70.0, 12.0, 8.0, 1.65, 0.55, 1.10, 0.18,
        DEFAULT_DEHYDRATION_GRACE_STEPS, DefaultWaterNeedProfile.INSTANCE);
    /** The enabled policy built for one parameter set (M3: drain and thresholds are tunable). */
    private static volatile HydrationPolicy w4Cache;
    private static volatile EcologyParameters w4CacheFor;

    private final boolean enabled;
    private final double baseDrain;
    private final double criticalHydrationThreshold;
    private final double thirstyHydrationThreshold;
    private final double criticalReserve;
    private final double safetyMargin;
    private final double droughtDrainMultiplier;
    private final double rainDrainMultiplier;
    private final double predatorDrainMultiplier;
    private final double criticalStaminaCost;
    private final int dehydrationGraceSteps;
    private final WaterNeedProfile waterNeedProfile;

    public HydrationPolicy(boolean enabled, double baseDrain,
                           double criticalHydrationThreshold,
                           double thirstyHydrationThreshold,
                           double criticalReserve, double safetyMargin,
                           double droughtDrainMultiplier,
                           double rainDrainMultiplier,
                           double predatorDrainMultiplier,
                           double criticalStaminaCost,
                           int dehydrationGraceSteps,
                           WaterNeedProfile waterNeedProfile)
    {
        this.enabled = enabled;
        this.baseDrain = positive("hydration.baseDrain", baseDrain);
        this.criticalHydrationThreshold = bounded(
            "hydration.criticalHydrationThreshold", criticalHydrationThreshold);
        this.thirstyHydrationThreshold = bounded(
            "hydration.thirstyHydrationThreshold", thirstyHydrationThreshold);
        if(this.criticalHydrationThreshold > this.thirstyHydrationThreshold) {
            throw new IllegalArgumentException(
                "hydration thresholds must satisfy critical<=thirsty");
        }
        this.criticalReserve = bounded("hydration.criticalReserve",
            criticalReserve);
        this.safetyMargin = nonNegative("hydration.safetyMargin", safetyMargin);
        this.droughtDrainMultiplier = positive(
            "hydration.droughtDrainMultiplier", droughtDrainMultiplier);
        this.rainDrainMultiplier = positive(
            "hydration.rainDrainMultiplier", rainDrainMultiplier);
        this.predatorDrainMultiplier = positive(
            "hydration.predatorDrainMultiplier", predatorDrainMultiplier);
        this.criticalStaminaCost = nonNegative(
            "hydration.criticalStaminaCost", criticalStaminaCost);
        if(dehydrationGraceSteps < 1) {
            throw new IllegalArgumentException(
                "hydration.dehydrationGraceSteps must be >= 1: " +
                dehydrationGraceSteps);
        }
        this.dehydrationGraceSteps = dehydrationGraceSteps;
        if(waterNeedProfile == null) {
            throw new IllegalArgumentException(
                "hydration.waterNeedProfile is required");
        }
        this.waterNeedProfile = waterNeedProfile;
    }

    public static HydrationPolicy disabled() { return DISABLED; }
    /**
     * The enabled W4 policy. Base drain, the critical and thirsty thresholds
     * and the predator drain multiplier come from {@link EcologyParameters}
     * (code defaults 0.24, 30, 70 and 1.10); the rest are fixed.
     */
    public static HydrationPolicy w4Default()
    {
        EcologyParameters parameters = EcologyParameters.current();
        HydrationPolicy cached = w4Cache;
        if(cached != null && w4CacheFor == parameters) return cached;
        HydrationPolicy built = new HydrationPolicy(true,
            parameters.get(EcologyParameters.Key.HYDRATION_BASE_DRAIN),
            parameters.get(EcologyParameters.Key.HYDRATION_CRITICAL_THRESHOLD),
            parameters.get(EcologyParameters.Key.HYDRATION_THIRSTY_THRESHOLD),
            12.0, 8.0, 1.65, 0.55,
            parameters.get(EcologyParameters.Key.HYDRATION_PREDATOR_DRAIN),
            0.18, DEFAULT_DEHYDRATION_GRACE_STEPS, DefaultWaterNeedProfile.INSTANCE);
        w4Cache = built;
        w4CacheFor = parameters;
        return built;
    }
    public boolean isEnabled() { return enabled; }
    public double getBaseDrain() { return baseDrain; }
    public double getCriticalHydrationThreshold() { return criticalHydrationThreshold; }
    public double getThirstyHydrationThreshold() { return thirstyHydrationThreshold; }
    public double getCriticalReserve() { return criticalReserve; }
    public double getSafetyMargin() { return safetyMargin; }
    public double getDroughtDrainMultiplier() { return droughtDrainMultiplier; }
    public double getRainDrainMultiplier() { return rainDrainMultiplier; }
    public double getPredatorDrainMultiplier() { return predatorDrainMultiplier; }
    public double getCriticalStaminaCost() { return criticalStaminaCost; }
    public int getDehydrationGraceSteps() { return dehydrationGraceSteps; }
    public String getWaterNeedProfileId()
    {
        return waterNeedProfile.getClass().getName();
    }
    public double baseDrainFor(SavannahAnimal animal)
    {
        return waterNeedProfile.baseDrain(animal, this);
    }

    private static double positive(String key, double value)
    {
        if(!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(
                key + " must be finite and > 0: " + value);
        }
        return value;
    }

    private static double nonNegative(String key, double value)
    {
        if(!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(
                key + " must be finite and >= 0: " + value);
        }
        return value;
    }

    private static double bounded(String key, double value)
    {
        if(!Double.isFinite(value) || value < 0.0 || value > 100.0) {
            throw new IllegalArgumentException(
                key + " must be finite and in [0,100]: " + value);
        }
        return value;
    }
}
