package savanna;

/** One immutable 30- or 60-step realized-kill aggregate. */
public final class RealizedKillWindowFact
{
    private final int step;
    private final long predatorId;
    private final int windowSteps;
    private final int count;
    private final int lastKillStep;
    private final long offeredFood;
    private final long acceptedFood;
    private final boolean leftCensored;

    RealizedKillWindowFact(int step, long predatorId, int windowSteps,
                           int count, int lastKillStep, long offeredFood,
                           long acceptedFood, boolean leftCensored)
    {
        if(step < 0 || predatorId <= 0L ||
           (windowSteps != RealizedKillLedger.WINDOW_30 &&
            windowSteps != RealizedKillLedger.WINDOW_60) ||
           count < 0 || offeredFood < 0L || acceptedFood < 0L ||
           acceptedFood > offeredFood ||
           (count == 0 && lastKillStep != -1) ||
           (count > 0 && (lastKillStep < 0 || lastKillStep >= step))) {
            throw new IllegalArgumentException(
                "invalid realized-kill window facts");
        }
        this.step = step;
        this.predatorId = predatorId;
        this.windowSteps = windowSteps;
        this.count = count;
        this.lastKillStep = lastKillStep;
        this.offeredFood = offeredFood;
        this.acceptedFood = acceptedFood;
        this.leftCensored = leftCensored;
    }

    public int getStep() { return step; }

    public long getPredatorId() { return predatorId; }

    public int getWindowSteps() { return windowSteps; }

    public int getCount() { return count; }

    public int getLastKillStep() { return lastKillStep; }

    public long getOfferedFood() { return offeredFood; }

    public long getAcceptedFood() { return acceptedFood; }

    public boolean isLeftCensored() { return leftCensored; }

    public boolean isComplete() { return !leftCensored; }
}
