package savanna;

import java.util.HashMap;
import java.util.Map;

/** Per-step bounded SOC1-aF capacity projection over the accepted L1-F view. */
public final class Soc1aFactIndex implements Soc1aFactView
{
    private final SpatialEcologyView spatialView;
    private final SpeciesEcologyFactRegistry registry;
    private final Map<Long, StepStartLocalCapacityFact> capacityCache =
        new HashMap<>();

    private Soc1aFactIndex(SpatialEcologyView spatialView,
                           SpeciesEcologyFactRegistry registry)
    {
        if(spatialView == null || registry == null) {
            throw new IllegalArgumentException(
                "L1-F view and SOC1-aF registry are required");
        }
        this.spatialView = spatialView;
        this.registry = registry;
    }

    public static Soc1aFactIndex capture(SpatialEcologyView spatialView,
                                         SpeciesEcologyFactRegistry registry)
    {
        return new Soc1aFactIndex(spatialView, registry);
    }

    @Override
    public int getStep() { return spatialView.getStep(); }

    @Override
    public SpeciesEcologyFactRegistry getSpeciesRegistry()
    {
        return registry;
    }

    @Override
    public synchronized StepStartLocalCapacityFact getLocalCapacity(
        long actorId)
    {
        StepStartLocalCapacityFact cached = capacityCache.get(actorId);
        if(cached != null) return cached;
        SpatialAnimalFact actor = spatialView.getAnimalFact(actorId);
        if(actor == null) {
            throw new IllegalArgumentException(
                "unknown step-start animal ID: " + actorId);
        }
        SpeciesEcologyFactProfile profile = registry.get(actor.getSpecies());
        if(profile == null) {
            throw new IllegalStateException(
                "unknown SOC1-aF species: " + actor.getSpecies());
        }
        LocalOccupancyFact occupancy = spatialView.getLocalOccupancy(
            actor.getLocation(), profile.getBreedingRange(),
            profile.getSpeciesKey());
        StepStartLocalCapacityFact result = new StepStartLocalCapacityFact(
            getStep(), actor, profile.getBreedingRange(), occupancy);
        capacityCache.put(actorId, result);
        return result;
    }

    @Override
    public synchronized int getCachedCapacityFactCount()
    {
        return capacityCache.size();
    }
}
