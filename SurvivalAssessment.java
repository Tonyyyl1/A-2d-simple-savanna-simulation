/** Immutable, resource-specific survival evaluation for one animal. */
public final class SurvivalAssessment
{
    private final double foodSurvivalPercent;
    private final double hydrationSurvivalPercent;
    private final double overallSurvivalPercent;

    public SurvivalAssessment(double foodSurvivalPercent,
                              double hydrationSurvivalPercent)
    {
        this.foodSurvivalPercent = clamp(foodSurvivalPercent);
        this.hydrationSurvivalPercent = clamp(hydrationSurvivalPercent);
        overallSurvivalPercent = Math.min(this.foodSurvivalPercent,
                                          this.hydrationSurvivalPercent);
    }

    public double getFoodSurvivalPercent() { return foodSurvivalPercent; }
    public double getHydrationSurvivalPercent() { return hydrationSurvivalPercent; }
    public double getOverallSurvivalPercent() { return overallSurvivalPercent; }

    public double getFoodPressure() { return pressure(foodSurvivalPercent); }
    public double getHydrationPressure() { return pressure(hydrationSurvivalPercent); }
    public double getOverallPressure() { return pressure(overallSurvivalPercent); }
    public boolean isOverallCritical() { return overallSurvivalPercent < 30.0; }

    private static double pressure(double percent)
    {
        if(percent > 70.0) return 0.0;
        if(percent >= 30.0) return (70.0 - percent) / 40.0;
        return 1.0;
    }

    private static double clamp(double value)
    {
        if(Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(100.0, value));
    }
}
