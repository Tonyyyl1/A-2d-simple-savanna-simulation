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
    private static final int PREDATOR_BIT = 0x40;
    private static final int SPECIES_MASK = 0x3F;
    /** A cell code holds species index + 1 in six bits, so at most 63 species. */
    private static final int MAX_SPECIES = SPECIES_MASK;

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

    /** Species name to a small index (the order of the diet map). */
    private final Map<String, Integer> speciesIndexByName;
    /** The same names by index; callers pass the profile's own String, so == usually hits. */
    private final String[] speciesNames;
    /** Bit i is set when species {@code index} can eat the species with index i. */
    private final long[] edibleMaskBySpecies;
    /** Per cell: 0 when empty, else (species index + 1) with PREDATOR_BIT for predators. */
    private final byte[] cellCode;

    /** (origin, range) pairs already charged to this step's ledger. */
    private final LongSet topologySeen;
    private final Map<ResourceKey, ReachableResourceFact> resourceCache =
        new HashMap<>();
    private final OccupancyCache occupancyCache;

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

        List<String> speciesNames = new ArrayList<>(edibleSpeciesByProfile.keySet());
        if(speciesNames.size() > MAX_SPECIES) {
            throw new IllegalStateException("too many species for spatial cell codes");
        }
        speciesIndexByName = new HashMap<>();
        for(int index = 0; index < speciesNames.size(); index++) {
            speciesIndexByName.put(speciesNames.get(index), index);
        }
        this.speciesNames = speciesNames.toArray(new String[0]);
        edibleMaskBySpecies = new long[speciesNames.size()];
        for(int consumer = 0; consumer < speciesNames.size(); consumer++) {
            for(String candidate : edibleSpeciesByProfile.get(speciesNames.get(consumer))) {
                Integer candidateIndex = speciesIndexByName.get(candidate);
                if(candidateIndex != null) {
                    edibleMaskBySpecies[consumer] |= 1L << candidateIndex.intValue();
                }
            }
        }
        cellCode = new byte[depth * width];
        occupancyCache = new OccupancyCache(sorted.size());
        topologySeen = new LongSet(sorted.size());

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
            Integer speciesIndex = speciesIndexByName.get(fact.getSpecies());
            if(speciesIndex == null) {
                throw new IllegalStateException(
                    "captured animal species has no diet entry: " + fact.getSpecies());
            }
            cellCode[cell] = (byte)((speciesIndex.intValue() + 1) |
                                    (fact.isPredator() ? PREDATOR_BIT : 0));
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
           (RuntimeAudit.enabled() && !field.hasConsistentState())) {
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

    /**
     * Window scan over the one-byte cell codes: only a cell whose code matches the
     * species and predator filters has its fact read, and no Location is built per cell.
     */
    @Override
    public void forEachAnimalFactAround(Location origin, int radius, String species,
                                        boolean predatorsOnly, AnimalFactSink sink)
    {
        if(origin == null) return;
        int wantedSpecies = -1;
        if(species != null) {
            int index = speciesIndexOf(species);
            if(index < 0) return; // every captured fact's species has an index
            wantedSpecies = index + 1;
        }
        int firstRow = (int)Math.max(0L, (long)origin.row() - radius);
        int lastRow = (int)Math.min(depth - 1L, (long)origin.row() + radius);
        int firstCol = (int)Math.max(0L, (long)origin.col() - radius);
        int lastCol = (int)Math.min(width - 1L, (long)origin.col() + radius);
        for(int row = firstRow; row <= lastRow; row++) {
            int base = row * width;
            int end = base + lastCol + 1;
            for(int cell = nextOccupied(base + firstCol, end); cell < end;
                cell = nextOccupied(cell + 1, end)) {
                int code = cellCode[cell];
                if((wantedSpecies >= 0 && (code & SPECIES_MASK) != wantedSpecies) ||
                   (predatorsOnly && (code & PREDATOR_BIT) == 0)) {
                    continue;
                }
                sink.accept(factsByCell[cell]);
            }
        }
    }

    private static final java.lang.invoke.VarHandle CODE_WORDS =
        java.lang.invoke.MethodHandles.byteArrayViewVarHandle(long[].class,
            java.nio.ByteOrder.LITTLE_ENDIAN);

    /**
     * Index of the first occupied cell in {@code [from, end)}, or {@code end} when there is
     * none. Most cells are empty, so eight cell codes are tested per step; the little-endian
     * word keeps the lowest index in the lowest byte, so the scan order stays row-major.
     */
    private int nextOccupied(int from, int end)
    {
        int index = from;
        while(index + 8 <= end) {
            long word = (long)CODE_WORDS.get(cellCode, index);
            if(word != 0L) {
                return index + (Long.numberOfTrailingZeros(word) >>> 3);
            }
            index += 8;
        }
        while(index < end) {
            if(cellCode[index] != 0) return index;
            index++;
        }
        return end;
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
        int focal = requireQueryIndex(origin, range, profileKey);
        occupancyQueryCount++;
        long key = ((long)origin.row() * width + origin.col()) * (MAX_SPECIES + 1) + focal;
        LocalOccupancyFact cached = occupancyCache.get(key, range);
        if(cached != null) {
            resultCacheHitCount++;
            return cached;
        }

        TerrainPassabilityIndex.Window window = topology(origin, range);
        long edibleMask = edibleMaskBySpecies[focal];
        int living = 0;
        int conspecific = 0;
        int predators = 0;
        int edible = 0;
        for(int row = window.firstRow; row < window.firstRow + window.localDepth; row++) {
            int base = row * width;
            int end = base + window.firstCol + window.localWidth;
            for(int cell = nextOccupied(base + window.firstCol, end); cell < end;
                cell = nextOccupied(cell + 1, end)) {
                if(window.distanceAt(row, cell - base) < 0) continue;
                int code = cellCode[cell];
                living++;
                int species = (code & SPECIES_MASK) - 1;
                if(species == focal) conspecific++;
                if((code & PREDATOR_BIT) != 0) predators++;
                if(((edibleMask >>> species) & 1L) != 0L) edible++;
            }
        }
        LocalOccupancyFact result = new LocalOccupancyFact(origin, range,
            profileKey, window.reachableCount, living, conspecific,
            predators, edible);
        occupancyCache.put(key, range, result);
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
        requireQueryIndex(origin, range, profileKey);
        return profileKey;
    }

    /** Validates a query and returns the small index of its focal species. */
    private int requireQueryIndex(Location origin, int range, String profileKey)
    {
        int index = -1;
        if(origin == null || range < 0 || profileKey == null ||
           origin.row() < 0 || origin.row() >= depth ||
           origin.col() < 0 || origin.col() >= width ||
           (index = speciesIndexOf(profileKey)) < 0) {
            throw new IllegalArgumentException(
                "known profile, origin, and non-negative range are required");
        }
        return index;
    }

    /** Index of a species name, or -1: an identity scan of the few names, then the map. */
    private int speciesIndexOf(String name)
    {
        for(int index = 0; index < speciesNames.length; index++) {
            if(speciesNames[index] == name) return index;
        }
        Integer index = speciesIndexByName.get(name);
        return index == null ? -1 : index.intValue();
    }

    private static Location copy(Location location)
    {
        return java.util.Objects.requireNonNull(location);
    }

    private record ResourceKey(Location origin, int range, String profileKey)
    {
    }

    private static int mix(long key, int range)
    {
        long z = key * 0x9E3779B97F4A7C15L ^ (long)range * 0xC2B2AE3D27D4EB4FL;
        z ^= z >>> 32;
        z *= 0x9E3779B97F4A7C15L;
        return (int)(z >>> 32);
    }

    private static int tableSize(int expected)
    {
        int size = 64;
        while(size < expected * 2L) size <<= 1;
        return size;
    }

    /**
     * Open-addressing cache of occupancy facts keyed by (cell and species, range).
     * It is never iterated and never removes an entry; only get, put and size exist.
     */
    private static final class OccupancyCache
    {
        private long[] keys;
        private int[] ranges;
        private LocalOccupancyFact[] values;
        private int size;

        OccupancyCache(int expectedEntries)
        {
            allocate(tableSize(expectedEntries));
        }

        int size() { return size; }

        LocalOccupancyFact get(long key, int range)
        {
            int mask = keys.length - 1;
            int slot = mix(key, range) & mask;
            while(true) {
                LocalOccupancyFact value = values[slot];
                if(value == null) return null;
                if(keys[slot] == key && ranges[slot] == range) return value;
                slot = (slot + 1) & mask;
            }
        }

        /** The caller has just seen {@code get} miss, so the key is not present. */
        void put(long key, int range, LocalOccupancyFact value)
        {
            if((size + 1) * 2 > keys.length) grow();
            insert(key, range, value);
            size++;
        }

        private void insert(long key, int range, LocalOccupancyFact value)
        {
            int mask = keys.length - 1;
            int slot = mix(key, range) & mask;
            while(values[slot] != null) slot = (slot + 1) & mask;
            keys[slot] = key;
            ranges[slot] = range;
            values[slot] = value;
        }

        private void grow()
        {
            long[] oldKeys = keys;
            int[] oldRanges = ranges;
            LocalOccupancyFact[] oldValues = values;
            allocate(oldKeys.length * 2);
            for(int slot = 0; slot < oldKeys.length; slot++) {
                if(oldValues[slot] != null) {
                    insert(oldKeys[slot], oldRanges[slot], oldValues[slot]);
                }
            }
        }

        private void allocate(int capacity)
        {
            keys = new long[capacity];
            ranges = new int[capacity];
            values = new LocalOccupancyFact[capacity];
        }
    }

    /** Open-addressing set of non-negative longs (stored as key + 1; 0 means empty). */
    private static final class LongSet
    {
        private long[] slots;
        private int size;

        LongSet(int expectedEntries)
        {
            slots = new long[tableSize(expectedEntries)];
        }

        int size() { return size; }

        /** @return true when the key was not yet present. */
        boolean add(long key)
        {
            long stored = key + 1;
            int mask = slots.length - 1;
            int slot = mix(stored, 0) & mask;
            while(slots[slot] != 0L) {
                if(slots[slot] == stored) return false;
                slot = (slot + 1) & mask;
            }
            if((size + 1) * 2 > slots.length) {
                grow();
                mask = slots.length - 1;
                slot = mix(stored, 0) & mask;
                while(slots[slot] != 0L) slot = (slot + 1) & mask;
            }
            slots[slot] = stored;
            size++;
            return true;
        }

        private void grow()
        {
            long[] old = slots;
            slots = new long[old.length * 2];
            int mask = slots.length - 1;
            for(long stored : old) {
                if(stored == 0L) continue;
                int slot = mix(stored, 0) & mask;
                while(slots[slot] != 0L) slot = (slot + 1) & mask;
                slots[slot] = stored;
            }
        }
    }
}
