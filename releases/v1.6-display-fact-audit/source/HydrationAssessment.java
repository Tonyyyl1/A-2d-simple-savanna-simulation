/** Immutable hydration assessment including distance-aware water reserve. */
public final class HydrationAssessment
{
    private final double hydrationPercent;
    private final int distanceToShore;
    private final double effectiveDrain;
    private final double requiredReserve;
    private final HydrationStage stage;

    public HydrationAssessment(double hydrationPercent, int distanceToShore,
                               double effectiveDrain, double safetyMargin)
    {
        this.hydrationPercent = clamp(hydrationPercent);
        this.distanceToShore = distanceToShore;
        this.effectiveDrain = Math.max(0.0, effectiveDrain);
        requiredReserve = distanceToShore == Integer.MAX_VALUE
            ? 100.0 : Math.min(100.0,
                Math.max(0.0, distanceToShore * this.effectiveDrain + safetyMargin));
        if(this.hydrationPercent <= 30.0) {
            stage = HydrationStage.CRITICAL;
        }
        else if(this.hydrationPercent <= Math.max(70.0, requiredReserve)) {
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
    public HydrationStage getStage() { return stage; }
    public boolean isWaterPriority() { return stage != HydrationStage.NORMAL; }

    private static double clamp(double value)
    {
        if(Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(100.0, value));
    }
}
