package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The sole immutable step-start owner of topology-local animal facts.
 *
 * <p>It retains neither {@link Field} nor {@link Animal}. Query caches belong
 * only to this captured step and disappear when {@link SimulationContext}
 * replaces the view at the next startStep call.</p>
 */
public final class SpatialEcologyIndex implements SpatialEcologyView
{
    private final int step;
    private final int depth;
    private final int width;
    private final TerrainMap terrainMap;
    private final List<SpatialAnimalFact> animalFacts;
    private final Map<Long, SpatialAnimalFact> animalsById;
    private final Map<Location, SpatialAnimalFact> animalsByLocation;
    private final Map<String, Set<String>> edibleSpeciesByProfile;
    private final Map<String, Integer> offeredFoodBySpecies;
    private final int terrainPassableCellCount;

    private final Map<TopologyKey, TopologyFact> topologyCache =
        new HashMap<>();
    private final Map<ResourceKey, ReachableResourceFact> resourceCache =
        new HashMap<>();
    private final Map<ResourceKey, LocalOccupancyFact> occupancyCache =
        new HashMap<>();

    private int resourceQueryCount;
    private int occupancyQueryCount;
    private int resultCacheHitCount;
    private int topologyCacheHitCount;
    private int topologyComputationCount;
    private long passableCellVisitCount;
    private int maxPassableCellsVisitedByOneComputation;

    private SpatialEcologyIndex(int step, TerrainMap terrainMap,
                                List<SpatialAnimalFact> animalFacts,
                                Map<String, Set<String>> edibleSpeciesByProfile,
                                Map<String, Integer> offeredFoodBySpecies)
    {
        this.step = step;
        this.terrainMap = terrainMap;
        depth = terrainMap.getDepth();
        width = terrainMap.getWidth();

        List<SpatialAnimalFact> sorted = new ArrayList<>(animalFacts);
        sorted.sort(Comparator.comparingLong(SpatialAnimalFact::getId));
        this.animalFacts = Collections.unmodifiableList(sorted);

        Map<Long, SpatialAnimalFact> byId = new LinkedHashMap<>();
        Map<Location, SpatialAnimalFact> byLocation = new LinkedHashMap<>();
        for(SpatialAnimalFact fact : sorted) {
            if(byId.put(fact.getId(), fact) != null) {
                throw new IllegalStateException(
                    "duplicate captured animal ID: " + fact.getId());
            }
            SpatialAnimalFact previous = byLocation.put(fact.getLocation(), fact);
            if(previous != null) {
                throw new IllegalStateException(
                    "duplicate captured location: " + fact.getLocation());
            }
        }
        animalsById = Collections.unmodifiableMap(byId);
        animalsByLocation = Collections.unmodifiableMap(byLocation);

        Map<String, Set<String>> diets = new LinkedHashMap<>();
        for(Map.Entry<String, Set<String>> entry :
            edibleSpeciesByProfile.entrySet()) {
            diets.put(entry.getKey(), Collections.unmodifiableSet(
                new HashSet<>(entry.getValue())));
        }
        this.edibleSpeciesByProfile = Collections.unmodifiableMap(diets);
        this.offeredFoodBySpecies = Collections.unmodifiableMap(
            new LinkedHashMap<>(offeredFoodBySpecies));

        int passable = 0;
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                if(terrainMap.isPassable(new Location(row, col))) {
                    passable++;
                }
            }
        }
        terrainPassableCellCount = passable;
    }

    /**
     * Capture once after growth/population snapshot and before animal actions.
     */
    public static SpatialEcologyIndex capture(int step, Field field,
                                               TerrainMap terrainMap)
    {
        if(step < 0 || field == null || terrainMap == null ||
           field.getDepth() != terrainMap.getDepth() ||
           field.getWidth() != terrainMap.getWidth() ||
           !field.hasConsistentState()) {
            throw new IllegalArgumentException(
                "consistent field and matching terrain are required");
        }

        Map<String, SpeciesProfile> profilesByName = new LinkedHashMap<>();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            SpeciesProfile profile = factory.getProfile();
            if(profile == null || profile.getName() == null ||
               profilesByName.put(profile.getName(), profile) != null) {
                throw new IllegalStateException(
                    "species registry must contain unique complete profiles");
            }
        }

        List<SavannahAnimal> livingAnimals = new ArrayList<>();
        for(Animal actor : field.getAnimals()) {
            if(!actor.isAlive()) continue;
            if(!(actor instanceof SavannahAnimal)) {
                throw new IllegalStateException(
                    "living spatial actor is not a SavannahAnimal: " + actor);
            }
            SavannahAnimal animal = (SavannahAnimal)actor;
            SpeciesProfile profile = animal.getProfile();
            if(profile == null || profile.getName() == null ||
               profile.getName().isBlank()) {
                throw new IllegalStateException(
                    "living spatial actor has no profile identity");
            }
            profilesByName.putIfAbsent(profile.getName(), profile);
            livingAnimals.add(animal);
        }

        Map<String, Set<String>> diets = new LinkedHashMap<>();
        Map<String, Integer> offeredFood = new LinkedHashMap<>();
        List<SpeciesProfile> profiles =
            new ArrayList<>(profilesByName.values());
        for(SpeciesProfile profile : profiles) {
            diets.put(profile.getName(), new HashSet<String>());
            offeredFood.put(profile.getName(), profile.getFoodValue());
        }
        for(SpeciesProfile consumer : profiles) {
            Set<String> edible = diets.get(consumer.getName());
            for(SpeciesProfile candidate : profiles) {
                if(consumer.canEat(candidate.getName())) {
                    edible.add(candidate.getName());
                }
            }
        }

        List<SpatialAnimalFact> facts = new ArrayList<>();
        for(SavannahAnimal animal : livingAnimals) {
            Location location = animal.getLocation();
            SpeciesProfile profile = animal.getProfile();
            if(location == null || field.getAnimalAt(location) != animal ||
               !diets.containsKey(profile.getName())) {
                throw new IllegalStateException(
                    "field and captured profile facts disagree");
            }
            facts.add(new SpatialAnimalFact(animal.getId(), profile.getName(),
                location, animal.getSex(), animal.getLifeStage(),
                profile.isPredator(), true));
        }
        return new SpatialEcologyIndex(step, terrainMap, facts, diets,
            offeredFood);
    }

    @Override
    public int getStep() { return step; }

    @Override
    public int getDepth() { return depth; }

    @Override
    public int getWidth() { return width; }

    @Override
    public int getCapturedAnimalCount() { return animalFacts.size(); }

    @Override
    public List<SpatialAnimalFact> getAnimalFacts() { return animalFacts; }

    @Override
    public SpatialAnimalFact getAnimalFact(long animalId)
    {
        return animalsById.get(animalId);
    }

    @Override
    public SpatialAnimalFact getAnimalFactAt(Location location)
    {
        return location == null ? null : animalsByLocation.get(location);
    }

    @Override
    public synchronized ReachableResourceFact getReachableResources(
        Location origin, int range, SpeciesProfile profile)
    {
        if(profile == null) {
            throw new IllegalArgumentException("profile is required");
        }
        return getReachableResources(origin, range, profile.getName());
    }

    @Override
    public synchronized ReachableResourceFact getReachableResources(
        Location origin, int range, String profileKey)
    {
        profileKey = requireQuery(origin, range, profileKey);
        resourceQueryCount++;
        ResourceKey key = new ResourceKey(copy(origin), range, profileKey);
        ReachableResourceFact cached = resourceCache.get(key);
        if(cached != null) {
            resultCacheHitCount++;
            return cached;
        }

        TopologyFact topology = topology(origin, range);
        Set<String> edibleSpecies = edibleSpeciesByProfile.get(profileKey);
        List<Long> ids = new ArrayList<>();
        int offeredFood = 0;
        int nearest = ReachableResourceFact.NO_EDIBLE_DISTANCE;
        for(Map.Entry<Location, Integer> entry :
            topology.getDistances().entrySet()) {
            SpatialAnimalFact animal = animalsByLocation.get(entry.getKey());
            if(animal == null ||
               !edibleSpecies.contains(animal.getSpecies())) {
                continue;
            }
            ids.add(animal.getId());
            offeredFood = Math.addExact(offeredFood,
                offeredFoodBySpecies.get(animal.getSpecies()));
            if(nearest < 0 || entry.getValue().intValue() < nearest) {
                nearest = entry.getValue().intValue();
            }
        }
        Collections.sort(ids);
        ReachableResourceFact result = new ReachableResourceFact(origin, range,
            profileKey, topology.getPassableCellCount(), ids.size(),
            offeredFood, nearest, ids);
        resourceCache.put(key, result);
        return result;
    }

    @Override
    public synchronized LocalOccupancyFact getLocalOccupancy(
        Location origin, int range, SpeciesProfile profile)
    {
        if(profile == null) {
            throw new IllegalArgumentException("profile is required");
        }
        return getLocalOccupancy(origin, range, profile.getName());
    }

    @Override
    public synchronized LocalOccupancyFact getLocalOccupancy(
        Location origin, int range, String profileKey)
    {
        profileKey = requireQuery(origin, range, profileKey);
        occupancyQueryCount++;
        ResourceKey key = new ResourceKey(copy(origin), range, profileKey);
        LocalOccupancyFact cached = occupancyCache.get(key);
        if(cached != null) {
            resultCacheHitCount++;
            return cached;
        }

        TopologyFact topology = topology(origin, range);
        Set<String> edibleSpecies = edibleSpeciesByProfile.get(profileKey);
        int living = 0;
        int conspecific = 0;
        int predators = 0;
        int edible = 0;
        for(Location location : topology.getDistances().keySet()) {
            SpatialAnimalFact animal = animalsByLocation.get(location);
            if(animal == null) continue;
            living++;
            if(profileKey.equals(animal.getSpecies())) conspecific++;
            if(animal.isPredator()) predators++;
            if(edibleSpecies.contains(animal.getSpecies())) edible++;
        }
        LocalOccupancyFact result = new LocalOccupancyFact(origin, range,
            profileKey, topology.getPassableCellCount(), living, conspecific,
            predators, edible);
        occupancyCache.put(key, result);
        return result;
    }

    @Override
    public synchronized SpatialQueryLedger getQueryLedger()
    {
        return new SpatialQueryLedger(resourceQueryCount, occupancyQueryCount,
            resultCacheHitCount, topologyCacheHitCount,
            topologyComputationCount, passableCellVisitCount,
            maxPassableCellsVisitedByOneComputation,
            terrainPassableCellCount,
            resourceCache.size() + occupancyCache.size(),
            topologyCache.size());
    }

    private TopologyFact topology(Location origin, int range)
    {
        TopologyKey key = new TopologyKey(copy(origin), range);
        TopologyFact cached = topologyCache.get(key);
        if(cached != null) {
            topologyCacheHitCount++;
            return cached;
        }
        TerrainNavigator.Reachability reachability =
            TerrainNavigator.reachableDistances(terrainMap, origin, range);
        Map<Location, Integer> distances = new LinkedHashMap<>();
        int firstRow = Math.max(0, origin.row() - range);
        int lastRow = Math.min(depth - 1, origin.row() + range);
        int firstCol = Math.max(0, origin.col() - range);
        int lastCol = Math.min(width - 1, origin.col() + range);
        for(int row = firstRow; row <= lastRow; row++) {
            for(int col = firstCol; col <= lastCol; col++) {
                Location location = new Location(row, col);
                int distance = reachability.distanceTo(location);
                if(distance >= 0 && distance <= range) {
                    distances.put(location, distance);
                }
            }
        }
        TopologyFact result = new TopologyFact(distances);
        topologyCache.put(key, result);
        topologyComputationCount++;
        int visits = result.getPassableCellCount();
        passableCellVisitCount += visits;
        maxPassableCellsVisitedByOneComputation = Math.max(
            maxPassableCellsVisitedByOneComputation, visits);
        if(visits > terrainPassableCellCount) {
            throw new IllegalStateException(
                "spatial query exceeded terrain passable-cell budget");
        }
        return result;
    }

    private String requireQuery(Location origin, int range,
                                String profileKey)
    {
        if(origin == null || range < 0 || profileKey == null ||
           origin.row() < 0 || origin.row() >= depth ||
           origin.col() < 0 || origin.col() >= width ||
           !edibleSpeciesByProfile.containsKey(profileKey)) {
            throw new IllegalArgumentException(
                "known profile, origin, and non-negative range are required");
        }
        return profileKey;
    }

    private static Location copy(Location location)
    {
        return new Location(location.row(), location.col());
    }

    private record TopologyKey(Location origin, int range)
    {
    }

    private record ResourceKey(Location origin, int range, String profileKey)
    {
    }

    private static final class TopologyFact
    {
        private final Map<Location, Integer> distances;

        TopologyFact(Map<Location, Integer> distances)
        {
            this.distances = Collections.unmodifiableMap(
                new HashMap<>(distances));
        }

        int distanceTo(Location location)
        {
            Integer distance = distances.get(location);
            return distance == null ? -1 : distance;
        }

        int getPassableCellCount()
        {
            return distances.size();
        }

        Map<Location, Integer> getDistances()
        {
            return distances;
        }
    }
}
