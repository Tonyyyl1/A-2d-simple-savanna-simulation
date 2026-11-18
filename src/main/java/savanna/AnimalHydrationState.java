package savanna;

/** Mutable hydration facts for one animal; water policy remains external. */
final class AnimalHydrationState
{
    private final double maxPercent;
    private double percent;
    private int dehydrationSteps;

    AnimalHydrationState(double initialPercent, double maxPercent)
    {
        if(maxPercent <= 0.0) {
            throw new IllegalArgumentException("maxPercent must be positive");
        }
        this.maxPercent = maxPercent;
        percent = clamp(initialPercent);
    }

    double getPercent() { return percent; }
    int getDehydrationSteps() { return dehydrationSteps; }
    boolean isBelow(double threshold) { return percent < threshold; }

    void drain(double amount)
    {
        percent = clamp(percent - Math.max(0.0, amount));
    }

    void rehydrate()
    {
        percent = maxPercent;
        dehydrationSteps = 0;
    }

    void addDehydrationStep() { dehydrationSteps++; }

    /** One finite service step of drinking (plan v3.0 M2-2). */
    void drink(double amount)
    {
        percent = clamp(percent + Math.max(0.0, amount));
        if(percent > 0.0) dehydrationSteps = 0;
    }

    /** Newborn initialization from a declared birth allowance. */
    void setPercent(double value)
    {
        percent = clamp(value);
        dehydrationSteps = 0;
    }

    private double clamp(double value)
    {
        return Math.max(0.0, Math.min(maxPercent, value));
    }
}
