package savanna;

/** Immutable hydration assessment including distance-aware water reserve. */
public final class HydrationAssessment
{
    private final double hydrationPercent;
    private final int distanceToShore;
    private final double effectiveDrain;
    private final double requiredReserve;
    private final double expectedArrivalHydration;
    private final String targetPoolId;
    private final boolean arrivalDeficit;
    private final HydrationStage stage;

    public HydrationAssessment(double hydrationPercent, int distanceToShore,
                               double effectiveDrain, double safetyMargin)
    {
        this(hydrationPercent, distanceToShore, effectiveDrain, safetyMargin,
             WaterSourceIndex.NONE, HydrationPolicy.w4Default().getCriticalReserve(),
             HydrationPolicy.w4Default().getCriticalHydrationThreshold(),
             HydrationPolicy.w4Default().getThirstyHydrationThreshold());
    }

    public HydrationAssessment(double hydrationPercent, int distanceToShore,
                               double effectiveDrain, double safetyMargin,
                               String targetPoolId, double criticalReserve)
    {
        this(hydrationPercent, distanceToShore, effectiveDrain, safetyMargin,
             targetPoolId, criticalReserve,
             HydrationPolicy.w4Default().getCriticalHydrationThreshold(),
             HydrationPolicy.w4Default().getThirstyHydrationThreshold());
    }

    public HydrationAssessment(double hydrationPercent, int distanceToShore,
                               double effectiveDrain, double safetyMargin,
                               String targetPoolId, double criticalReserve,
                               double criticalHydrationThreshold,
                               double thirstyHydrationThreshold)
    {
        this.hydrationPercent = clamp(hydrationPercent);
        this.distanceToShore = distanceToShore;
        this.effectiveDrain = Math.max(0.0, effectiveDrain);
        requiredReserve = distanceToShore == Integer.MAX_VALUE
            ? 100.0 : Math.min(100.0,
                Math.max(0.0, distanceToShore * this.effectiveDrain + safetyMargin));
        expectedArrivalHydration = distanceToShore == Integer.MAX_VALUE ? 0.0 :
            this.hydrationPercent - distanceToShore * this.effectiveDrain;
        this.targetPoolId = targetPoolId == null ? WaterSourceIndex.NONE : targetPoolId;
        arrivalDeficit = distanceToShore == Integer.MAX_VALUE ||
            this.hydrationPercent < requiredReserve;
        if(this.hydrationPercent <= criticalHydrationThreshold ||
           expectedArrivalHydration < Math.max(0.0, criticalReserve)) {
            stage = HydrationStage.CRITICAL;
        }
        else if(this.hydrationPercent <= Math.max(thirstyHydrationThreshold,
                                                   requiredReserve)) {
            stage = HydrationStage.THIRSTY;
        }
        else {
            stage = HydrationStage.NORMAL;
        }
    }

    public double getHydrationPercent() { return hydrationPercent; }
    public int getDistanceToShore() { return distanceToShore; }
    public double getEffectiveDrain() { return effectiveDrain; }
    public double getRequiredReserve() { return requiredReserve; }
    public double getExpectedArrivalHydration() { return expectedArrivalHydration; }
    public String getTargetPoolId() { return targetPoolId; }
    public boolean hasArrivalDeficit() { return arrivalDeficit; }
    public HydrationStage getStage() { return stage; }
    public boolean isWaterPriority() { return stage != HydrationStage.NORMAL; }

    private static double clamp(double value)
    {
        if(Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(100.0, value));
    }
}
