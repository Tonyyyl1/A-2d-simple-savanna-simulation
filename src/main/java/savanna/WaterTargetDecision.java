package savanna;

/** Immutable target-pool decision for one seek-water cycle. */
public final class WaterTargetDecision
{
    private final String poolId;
    private final int distanceToPool;
    private final boolean arrivalDeficit;
    private final boolean retargeted;

    public WaterTargetDecision(String poolId, int distanceToPool,
                               boolean arrivalDeficit, boolean retargeted)
    {
        this.poolId = poolId == null ? WaterSourceIndex.NONE : poolId;
        this.distanceToPool = distanceToPool;
        this.arrivalDeficit = arrivalDeficit;
        this.retargeted = retargeted;
    }

    public String getPoolId() { return poolId; }
    public int getDistanceToPool() { return distanceToPool; }
    public boolean hasArrivalDeficit() { return arrivalDeficit; }
    public boolean wasRetargeted() { return retargeted; }
    public boolean isReachable() { return distanceToPool != Integer.MAX_VALUE; }
}
