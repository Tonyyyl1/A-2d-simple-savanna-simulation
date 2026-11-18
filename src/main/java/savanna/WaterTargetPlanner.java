package savanna;

/** Read-only per-pool shoreline topology used to choose and retain water goals. */
public final class WaterTargetPlanner
{
    private final WaterTopology topology;

    public WaterTargetPlanner(TerrainMap terrain)
    {
        this(WaterTopology.forTerrain(terrain));
    }

    public WaterTargetPlanner(WaterTopology topology)
    {
        if(topology == null) throw new IllegalArgumentException("water topology is required");
        this.topology = topology;
    }

    public WaterSourceIndex getSourceIndex() { return topology.getSourceIndex(); }
    WaterTopology getTopology() { return topology; }

    public int distanceToPool(Location location, String poolId)
    {
        return topology.distanceToPool(location, poolId);
    }
}
