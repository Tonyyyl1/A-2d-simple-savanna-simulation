package savanna;

/** Immutable snapshot of one step's bounded spatial-query work. */
public final class SpatialQueryLedger
{
    private final int resourceQueryCount;
    private final int occupancyQueryCount;
    private final int resultCacheHitCount;
    private final int topologyCacheHitCount;
    private final int topologyComputationCount;
    private final long passableCellVisitCount;
    private final int maxPassableCellsVisitedByOneComputation;
    private final int terrainPassableCellCount;
    private final int resultCacheEntryCount;
    private final int topologyCacheEntryCount;

    SpatialQueryLedger(int resourceQueryCount, int occupancyQueryCount,
                       int resultCacheHitCount, int topologyCacheHitCount,
                       int topologyComputationCount,
                       long passableCellVisitCount,
                       int maxPassableCellsVisitedByOneComputation,
                       int terrainPassableCellCount,
                       int resultCacheEntryCount,
                       int topologyCacheEntryCount)
    {
        this.resourceQueryCount = resourceQueryCount;
        this.occupancyQueryCount = occupancyQueryCount;
        this.resultCacheHitCount = resultCacheHitCount;
        this.topologyCacheHitCount = topologyCacheHitCount;
        this.topologyComputationCount = topologyComputationCount;
        this.passableCellVisitCount = passableCellVisitCount;
        this.maxPassableCellsVisitedByOneComputation =
            maxPassableCellsVisitedByOneComputation;
        this.terrainPassableCellCount = terrainPassableCellCount;
        this.resultCacheEntryCount = resultCacheEntryCount;
        this.topologyCacheEntryCount = topologyCacheEntryCount;
    }

    public int getResourceQueryCount() { return resourceQueryCount; }

    public int getOccupancyQueryCount() { return occupancyQueryCount; }

    public int getResultCacheHitCount() { return resultCacheHitCount; }

    public int getTopologyCacheHitCount() { return topologyCacheHitCount; }

    public int getTopologyComputationCount()
    {
        return topologyComputationCount;
    }

    public long getPassableCellVisitCount() { return passableCellVisitCount; }

    public int getMaxPassableCellsVisitedByOneComputation()
    {
        return maxPassableCellsVisitedByOneComputation;
    }

    public int getTerrainPassableCellCount()
    {
        return terrainPassableCellCount;
    }

    public int getResultCacheEntryCount() { return resultCacheEntryCount; }

    public int getTopologyCacheEntryCount() { return topologyCacheEntryCount; }
}
