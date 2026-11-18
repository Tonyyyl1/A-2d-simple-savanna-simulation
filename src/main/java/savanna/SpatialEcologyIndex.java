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
 * <p>It retains neither {@link Field} nor {@link Animal}. Result caches and the
 * per-step topology ledger belong only to this captured step and disappear when
 * {@link SimulationContext} replaces the view at the next startStep call.
 * Terrain-only topology (a pure function of the immutable terrain) is shared
 * across steps through {@link TerrainPassabilityIndex}.</p>
 */
public final class SpatialEcologyIndex implements SpatialEcologyView
{
    private final int step;
    private final int depth;
    private final int width;
    private final TerrainMap terrainMap;
    private final List<SpatialAnimalFact> animalFacts;
    private final Map<Long, SpatialAnimalFact> animalsById;
    private final SpatialAnimalFact[] factsByCell;
    private final TerrainPassabilityIndex passability;
    private final Map<String, Set<String>> edibleSpeciesByProfile;
    private final Map<String, Integer> offeredFoodBySpecies;
    private final int terrainPassableCellCount;

    /** (origin, range) pairs already charged to this step's ledger. */
    private final Set<Long> topologySeen = new HashSet<>();
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
        passability = terrainMap.passability();
        factsByCell = new SpatialAnimalFact[depth * width];
        for(SpatialAnimalFact fact : sorted) {
            if(byId.put(fact.getId(), fact) != null) {
                throw new IllegalStateException(
                    "duplicate captured animal ID: " + fact.getId());
            }
            Location at = fact.getLocation();
            if(at.row() < 0 || at.row() >= depth || at.col() < 0 || at.col() >= width) {
                throw new IllegalStateException("captured location outside field: " + at);
            }
            int cell = at.row() * width + at.col();
            if(factsByCell[cell] != null) {
                throw new IllegalStateException("duplicate captured location: " + at);
            }
            factsByCell[cell] = fact;
        }
        animalsById = Collections.unmodifiableMap(byId);

        Map<String, Set<String>> diets = new LinkedHashMap<>();
        for(Map.Entry<String, Set<String>> entry :
            edibleSpeciesByProfile.entrySet()) {
            diets.put(entry.getKey(), Collections.unmodifiableSet(
                new HashSet<>(entry.getValue())));
        }
        this.edibleSpeciesByProfile = Collections.unmodifiableMap(diets);
        this.offeredFoodBySpecies = Collections.unmodifiableMap(
            new LinkedHashMap<>(offeredFoodBySpecies));

        terrainPassableCellCount = passability.getPassableCellCount();
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
        if(location == null || location.row() < 0 || location.row() >= depth ||
           location.col() < 0 || location.col() >= width) {
            return null;
        }
        return factsByCell[location.row() * width + location.col()];
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

        TerrainPassabilityIndex.Window window = topology(origin, range);
        Set<String> edibleSpecies = edibleSpeciesByProfile.get(profileKey);
        List<Long> ids = new ArrayList<>();
        int offeredFood = 0;
        int nearest = ReachableResourceFact.NO_EDIBLE_DISTANCE;
        for(int row = window.firstRow; row < window.firstRow + window.localDepth; row++) {
            for(int col = window.firstCol; col < window.firstCol + window.localWidth; col++) {
                SpatialAnimalFact animal = factsByCell[row * width + col];
                if(animal == null) continue;
                int distance = window.distanceAt(row, col);
                if(distance < 0 || !edibleSpecies.contains(animal.getSpecies())) continue;
                ids.add(animal.getId());
                offeredFood = Math.addExact(offeredFood,
                    offeredFoodBySpecies.get(animal.getSpecies()));
                if(nearest < 0 || distance < nearest) nearest = distance;
            }
        }
        Collections.sort(ids);
        ReachableResourceFact result = new ReachableResourceFact(origin, range,
            profileKey, window.reachableCount, ids.size(),
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

        TerrainPassabilityIndex.Window window = topology(origin, range);
        Set<String> edibleSpecies = edibleSpeciesByProfile.get(profileKey);
        int living = 0;
        int conspecific = 0;
        int predators = 0;
        int edible = 0;
        for(int row = window.firstRow; row < window.firstRow + window.localDepth; row++) {
            for(int col = window.firstCol; col < window.firstCol + window.localWidth; col++) {
                SpatialAnimalFact animal = factsByCell[row * width + col];
                if(animal == null || window.distanceAt(row, col) < 0) continue;
                living++;
                if(profileKey.equals(animal.getSpecies())) conspecific++;
                if(animal.isPredator()) predators++;
                if(edibleSpecies.contains(animal.getSpecies())) edible++;
            }
        }
        LocalOccupancyFact result = new LocalOccupancyFact(origin, range,
            profileKey, window.reachableCount, living, conspecific,
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
            topologySeen.size());
    }

    private TerrainPassabilityIndex.Window topology(Location origin, int range)
    {
        TerrainPassabilityIndex.Window window = passability.window(origin, range);
        long key = ((long)origin.row() * width + origin.col()) * 1_000_003L + range;
        if(!topologySeen.add(key)) {
            topologyCacheHitCount++;
            return window;
        }
        topologyComputationCount++;
        int visits = window.reachableCount;
        passableCellVisitCount += visits;
        maxPassableCellsVisitedByOneComputation = Math.max(
            maxPassableCellsVisitedByOneComputation, visits);
        if(visits > terrainPassableCellCount) {
            throw new IllegalStateException(
                "spatial query exceeded terrain passable-cell budget");
        }
        return window;
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

    private record ResourceKey(Location origin, int range, String profileKey)
    {
    }
}
