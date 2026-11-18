package savanna;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable step-start/step-end population facts and optional 100-step
 * resource/capacity boundary facts for V24-OBS-1B.
 */
public final class LongHorizonTrophicStepFact
{
    public static final int INTERVAL_STEPS = 100;

    private final String runId;
    private final int step;
    private final Map<String, PopulationFact> populations;
    private final boolean resourceBoundary;
    private final double grassAfterGrowthMean;
    private final double grassAtStepEndMean;
    private final Map<String, CapacityFact> capacities;

    private LongHorizonTrophicStepFact(String runId, int step,
                                      Map<String, PopulationFact> populations,
                                      boolean resourceBoundary,
                                      double grassAfterGrowthMean,
                                      double grassAtStepEndMean,
                                      Map<String, CapacityFact> capacities)
    {
        this.runId = runId;
        this.step = step;
        this.populations = immutable(populations);
        this.resourceBoundary = resourceBoundary;
        this.grassAfterGrowthMean = grassAfterGrowthMean;
        this.grassAtStepEndMean = grassAtStepEndMean;
        this.capacities = immutable(capacities);
    }

    static LongHorizonTrophicStepFact capture(Field committed,
                                               SimulationContext context)
    {
        if(committed == null || context == null || context.getStep() <= 0) {
            throw new IllegalArgumentException(
                "a completed positive simulation step is required");
        }
        Map<String, MutablePopulation> values = new LinkedHashMap<>();
        Map<String, MutableCapacity> capacityValues = new LinkedHashMap<>();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String species = factory.getProfile().getName();
            values.put(species, new MutablePopulation());
            capacityValues.put(species, new MutableCapacity());
        }
        SpatialEcologyView spatial = context.getSpatialEcologyView();
        for(SpatialAnimalFact actor : spatial.getAnimalFacts()) {
            MutablePopulation value = require(values, actor.getSpecies());
            value.startPopulation++;
        }
        for(Animal actor : committed.getAnimals()) {
            if(!(actor instanceof SavannahAnimal) || !actor.isAlive()) continue;
            SavannahAnimal animal = (SavannahAnimal)actor;
            MutablePopulation value = require(values,
                animal.getProfile().getName());
            value.endPopulation++;
            if(animal.getLifeStage() == LifeStage.ADULT) {
                if(animal.getSex() == Sex.FEMALE) value.adultFemale++;
                else value.adultMale++;
            }
            else {
                value.juvenile++;
                if(animal.getLifeStage() == LifeStage.CUB) value.cub++;
            }
        }
        Map<String, PopulationFact> populations = new LinkedHashMap<>();
        for(Map.Entry<String, MutablePopulation> entry : values.entrySet()) {
            populations.put(entry.getKey(), new PopulationFact(entry.getValue()));
        }

        boolean boundary = context.getStep() % INTERVAL_STEPS == 0;
        double afterGrowth = 0.0;
        double atEnd = 0.0;
        if(boundary) {
            FoodSystem food = context.getFoodSystem();
            afterGrowth = food.getAverageFoodLevel();
            double total = 0.0;
            for(int row = 0; row < committed.getDepth(); row++) {
                for(int col = 0; col < committed.getWidth(); col++) {
                    total += food.getFoodLevelAt(new Location(row, col));
                }
            }
            atEnd = total / (committed.getDepth() * committed.getWidth());
            Soc1aFactView capacityView = context.getSoc1aFactView();
            for(SpatialAnimalFact actor : spatial.getAnimalFacts()) {
                StepStartLocalCapacityFact fact =
                    capacityView.getLocalCapacity(actor.getId());
                MutableCapacity value = require(capacityValues,
                    actor.getSpecies());
                value.actorCount++;
                value.passableCellSum += fact.getPassableCellCount();
                value.livingOccupantSum += fact.getLivingOccupantCount();
                value.conspecificSum += fact.getConspecificCount();
                value.predatorSum += fact.getPredatorCount();
                value.ediblePreySum += fact.getEdiblePreyCount();
            }
        }
        Map<String, CapacityFact> capacities = new LinkedHashMap<>();
        for(Map.Entry<String, MutableCapacity> entry :
            capacityValues.entrySet()) {
            capacities.put(entry.getKey(), new CapacityFact(entry.getValue()));
        }
        String runId = "seed=" + context.getConfig().getRandomSeed() +
            ";terrain=" + context.getConfig().getTerrainSeed();
        return new LongHorizonTrophicStepFact(runId, context.getStep(),
            populations, boundary, afterGrowth, atEnd, capacities);
    }

    public String getRunId() { return runId; }
    public int getStep() { return step; }
    public Map<String, PopulationFact> getPopulations() { return populations; }
    public PopulationFact getPopulation(String species)
    {
        return populations.get(species);
    }
    public boolean isResourceBoundary() { return resourceBoundary; }
    public double getGrassAfterGrowthMean() { return grassAfterGrowthMean; }
    public double getGrassAtStepEndMean() { return grassAtStepEndMean; }
    public Map<String, CapacityFact> getCapacities() { return capacities; }
    public CapacityFact getCapacity(String species) { return capacities.get(species); }

    private static <T> Map<String, T> immutable(Map<String, T> values)
    {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static <T> T require(Map<String, T> values, String species)
    {
        T result = values.get(species);
        if(result == null) {
            throw new IllegalStateException(
                "unknown registry species in trophic fact: " + species);
        }
        return result;
    }

    public static final class PopulationFact
    {
        private final int startPopulation;
        private final int endPopulation;
        private final int juvenile;
        private final int cub;
        private final int adultFemale;
        private final int adultMale;

        private PopulationFact(MutablePopulation value)
        {
            startPopulation = value.startPopulation;
            endPopulation = value.endPopulation;
            juvenile = value.juvenile;
            cub = value.cub;
            adultFemale = value.adultFemale;
            adultMale = value.adultMale;
            if(endPopulation != juvenile + adultFemale + adultMale ||
               cub > juvenile) {
                throw new IllegalStateException(
                    "population lifecycle facts do not partition the species");
            }
        }

        public int getStartPopulation() { return startPopulation; }
        public int getEndPopulation() { return endPopulation; }
        public int getJuvenile() { return juvenile; }
        public int getCub() { return cub; }
        public int getAdultFemale() { return adultFemale; }
        public int getAdultMale() { return adultMale; }
    }

    public static final class CapacityFact
    {
        private final int actorCount;
        private final long passableCellSum;
        private final long livingOccupantSum;
        private final long conspecificSum;
        private final long predatorSum;
        private final long ediblePreySum;

        private CapacityFact(MutableCapacity value)
        {
            actorCount = value.actorCount;
            passableCellSum = value.passableCellSum;
            livingOccupantSum = value.livingOccupantSum;
            conspecificSum = value.conspecificSum;
            predatorSum = value.predatorSum;
            ediblePreySum = value.ediblePreySum;
        }

        public int getActorCount() { return actorCount; }
        public long getPassableCellSum() { return passableCellSum; }
        public long getLivingOccupantSum() { return livingOccupantSum; }
        public long getConspecificSum() { return conspecificSum; }
        public long getPredatorSum() { return predatorSum; }
        public long getEdiblePreySum() { return ediblePreySum; }
    }

    private static final class MutablePopulation
    {
        private int startPopulation;
        private int endPopulation;
        private int juvenile;
        private int cub;
        private int adultFemale;
        private int adultMale;
    }

    private static final class MutableCapacity
    {
        private int actorCount;
        private long passableCellSum;
        private long livingOccupantSum;
        private long conspecificSum;
        private long predatorSum;
        private long ediblePreySum;
    }
}
