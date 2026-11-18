package savanna;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable step-start pool load facts; it never changes animal behavior itself. */
public final class WaterholeCongestionAssessment
{
    private final WaterTargetPlanner planner;
    private final Map<String, Integer> nearCounts = new LinkedHashMap<>();

    public WaterholeCongestionAssessment(Field field, TerrainMap terrain)
    {
        this(field, WaterTopology.forTerrain(terrain));
    }

    WaterholeCongestionAssessment(Field field, WaterTopology topology)
    {
        planner = new WaterTargetPlanner(topology);
        for(String poolId : planner.getSourceIndex().getPoolIds()) {
            nearCounts.put(poolId, 0);
        }
        if(field == null) return;
        for(Animal animal : field.getAnimals()) {
            if(!(animal instanceof SavannahAnimal) || !animal.isAlive()) continue;
            String nearest = nearestPool(animal.getLocation());
            if(!WaterSourceIndex.NONE.equals(nearest) &&
               planner.distanceToPool(animal.getLocation(), nearest) <= 6) {
                nearCounts.put(nearest, nearCounts.get(nearest) + 1);
            }
        }
    }

    public double getNormalizedLoad(String poolId)
    {
        int capacity = planner.getSourceIndex().getShoreCellCount(poolId);
        return capacity <= 0 ? Double.POSITIVE_INFINITY :
            (double)nearCounts.getOrDefault(poolId, 0) / capacity;
    }

    public int getNearCount(String poolId)
    {
        return nearCounts.getOrDefault(poolId, 0);
    }

    WaterTopology getTopology() { return planner.getTopology(); }

    private String nearestPool(Location location)
    {
        String selected = WaterSourceIndex.NONE;
        int distance = Integer.MAX_VALUE;
        for(String poolId : planner.getSourceIndex().getPoolIds()) {
            int candidate = planner.distanceToPool(location, poolId);
            if(candidate < distance || (candidate == distance &&
               poolId.compareTo(selected) < 0)) {
                selected = poolId;
                distance = candidate;
            }
        }
        return selected;
    }
}
