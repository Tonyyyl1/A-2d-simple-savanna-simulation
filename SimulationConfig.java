/**
 * Runtime configuration for simulation experiments.
 *
 * The default config preserves the tuned baseline. Experiment runners can
 * scale map size, starting density, breeding, and disease pressure without
 * changing the normal BlueJ/visual entry points.
 */
public class SimulationConfig
{
    public static final int BASE_WIDTH = 120;
    public static final int BASE_DEPTH = 80;
    public static final long DEFAULT_RANDOM_SEED = 1111L;
    public static final long DEFAULT_TERRAIN_SEED = 20260629L;

    private final int mapScale;
    private final double creationMultiplier;
    private final double foundingMultiplier;
    private final double breedingMultiplier;
    private final double predatorCreationMultiplier;
    private final double preyCreationMultiplier;
    private final double predatorFoundingMultiplier;
    private final double preyFoundingMultiplier;
    private final double predatorBreedingMultiplier;
    private final double preyBreedingMultiplier;
    private final double diseaseTransmissionMultiplier;
    private final double diseaseFatalityMultiplier;
    private final long randomSeed;
    private final long terrainSeed;

    public SimulationConfig()
    {
        this(1, 1.0, 1.0, 1.0, 1.0, 1.0);
    }

    public SimulationConfig(int mapScale, double creationMultiplier,
                            double foundingMultiplier,
                            double breedingMultiplier,
                            double diseaseTransmissionMultiplier,
                            double diseaseFatalityMultiplier)
    {
        this(mapScale, creationMultiplier, foundingMultiplier,
             breedingMultiplier, diseaseTransmissionMultiplier,
             diseaseFatalityMultiplier, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
    }

    public SimulationConfig(int mapScale, double creationMultiplier,
                            double foundingMultiplier,
                            double breedingMultiplier,
                            double diseaseTransmissionMultiplier,
                            double diseaseFatalityMultiplier,
                            double predatorCreationMultiplier,
                            double preyCreationMultiplier,
                            double predatorFoundingMultiplier,
                            double preyFoundingMultiplier,
                            double predatorBreedingMultiplier,
                            double preyBreedingMultiplier)
    {
        this(mapScale, creationMultiplier, foundingMultiplier,
             breedingMultiplier, diseaseTransmissionMultiplier,
             diseaseFatalityMultiplier, predatorCreationMultiplier,
             preyCreationMultiplier, predatorFoundingMultiplier,
             preyFoundingMultiplier, predatorBreedingMultiplier,
             preyBreedingMultiplier, DEFAULT_RANDOM_SEED, DEFAULT_TERRAIN_SEED);
    }

    public SimulationConfig(int mapScale, double creationMultiplier,
                            double foundingMultiplier,
                            double breedingMultiplier,
                            double diseaseTransmissionMultiplier,
                            double diseaseFatalityMultiplier,
                            double predatorCreationMultiplier,
                            double preyCreationMultiplier,
                            double predatorFoundingMultiplier,
                            double preyFoundingMultiplier,
                            double predatorBreedingMultiplier,
                            double preyBreedingMultiplier,
                            long randomSeed,
                            long terrainSeed)
    {
        this.mapScale = Math.max(1, Math.min(4, mapScale));
        this.creationMultiplier = positiveOrDefault(creationMultiplier);
        this.foundingMultiplier = positiveOrDefault(foundingMultiplier);
        this.breedingMultiplier = positiveOrDefault(breedingMultiplier);
        this.predatorCreationMultiplier =
            positiveOrDefault(predatorCreationMultiplier);
        this.preyCreationMultiplier = positiveOrDefault(preyCreationMultiplier);
        this.predatorFoundingMultiplier =
            positiveOrDefault(predatorFoundingMultiplier);
        this.preyFoundingMultiplier = positiveOrDefault(preyFoundingMultiplier);
        this.predatorBreedingMultiplier =
            positiveOrDefault(predatorBreedingMultiplier);
        this.preyBreedingMultiplier = positiveOrDefault(preyBreedingMultiplier);
        this.diseaseTransmissionMultiplier =
            positiveOrDefault(diseaseTransmissionMultiplier);
        this.diseaseFatalityMultiplier =
            positiveOrDefault(diseaseFatalityMultiplier);
        this.randomSeed = randomSeed;
        this.terrainSeed = terrainSeed;
    }

    public static SimulationConfig baseline()
    {
        return new SimulationConfig();
    }

    public static SimulationConfig target3xBalanced()
    {
        return new SimulationConfig(3, 1.0 / 9.0, 1.0, 0.58,
            1.75, 1.75, 2.60, 0.75, 2.00, 0.85, 2.00, 0.55);
    }

    public static SimulationConfig default3x()
    {
        return target3xBalanced();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public int getMapScale()
    {
        return mapScale;
    }

    public int getScaledWidth()
    {
        return BASE_WIDTH * mapScale;
    }

    public int getScaledDepth()
    {
        return BASE_DEPTH * mapScale;
    }

    public double getCreationMultiplier()
    {
        return creationMultiplier;
    }

    public double getFoundingMultiplier()
    {
        return foundingMultiplier;
    }

    public double getBreedingMultiplier()
    {
        return breedingMultiplier;
    }

    public double getDiseaseTransmissionMultiplier()
    {
        return diseaseTransmissionMultiplier;
    }

    public double getDiseaseFatalityMultiplier()
    {
        return diseaseFatalityMultiplier;
    }

    public long getRandomSeed()
    {
        return randomSeed;
    }

    public long getTerrainSeed()
    {
        return terrainSeed;
    }

    public double creationProbability(SpeciesProfile profile)
    {
        double category = profile.isPredator() ? predatorCreationMultiplier :
            preyCreationMultiplier;
        return Math.max(0.0, Math.min(1.0,
            profile.getCreationProbability() * creationMultiplier * category));
    }

    public int foundingPopulation(SpeciesProfile profile)
    {
        double category = profile.isPredator() ? predatorFoundingMultiplier :
            preyFoundingMultiplier;
        return Math.max(1, (int)Math.round(
            profile.getFoundingPopulation() * foundingMultiplier * category));
    }

    public double breedingProbability(SpeciesProfile profile)
    {
        double category = profile.isPredator() ? predatorBreedingMultiplier :
            preyBreedingMultiplier;
        return Math.max(0.0, Math.min(1.0,
            profile.getBreedingProbability() * breedingMultiplier * category));
    }

    public String describe()
    {
        return "scale=" + mapScale +
               " creation=" + format(creationMultiplier) +
               " founding=" + format(foundingMultiplier) +
               " breeding=" + format(breedingMultiplier) +
               " predC=" + format(predatorCreationMultiplier) +
               " preyC=" + format(preyCreationMultiplier) +
               " predF=" + format(predatorFoundingMultiplier) +
               " preyF=" + format(preyFoundingMultiplier) +
               " predB=" + format(predatorBreedingMultiplier) +
               " preyB=" + format(preyBreedingMultiplier) +
               " diseaseTx=" + format(diseaseTransmissionMultiplier) +
               " diseaseFatal=" + format(diseaseFatalityMultiplier) +
               " randomSeed=" + randomSeed +
               " terrainSeed=" + terrainSeed;
    }

    public static class Builder
    {
        private int mapScale = 3;
        private double creationMultiplier = 1.0 / 9.0;
        private double foundingMultiplier = 1.0;
        private double breedingMultiplier = 0.58;
        private double diseaseTransmissionMultiplier = 1.75;
        private double diseaseFatalityMultiplier = 1.75;
        private double predatorCreationMultiplier = 2.60;
        private double preyCreationMultiplier = 0.75;
        private double predatorFoundingMultiplier = 2.00;
        private double preyFoundingMultiplier = 0.85;
        private double predatorBreedingMultiplier = 2.00;
        private double preyBreedingMultiplier = 0.55;
        private long randomSeed = DEFAULT_RANDOM_SEED;
        private long terrainSeed = DEFAULT_TERRAIN_SEED;

        public Builder mapScale(int value) { mapScale = value; return this; }
        public Builder creationMultiplier(double value) { creationMultiplier = value; return this; }
        public Builder foundingMultiplier(double value) { foundingMultiplier = value; return this; }
        public Builder breedingMultiplier(double value) { breedingMultiplier = value; return this; }
        public Builder diseaseTransmissionMultiplier(double value) { diseaseTransmissionMultiplier = value; return this; }
        public Builder diseaseFatalityMultiplier(double value) { diseaseFatalityMultiplier = value; return this; }
        public Builder predatorCreationMultiplier(double value) { predatorCreationMultiplier = value; return this; }
        public Builder preyCreationMultiplier(double value) { preyCreationMultiplier = value; return this; }
        public Builder predatorFoundingMultiplier(double value) { predatorFoundingMultiplier = value; return this; }
        public Builder preyFoundingMultiplier(double value) { preyFoundingMultiplier = value; return this; }
        public Builder predatorBreedingMultiplier(double value) { predatorBreedingMultiplier = value; return this; }
        public Builder preyBreedingMultiplier(double value) { preyBreedingMultiplier = value; return this; }
        public Builder randomSeed(long value) { randomSeed = value; return this; }
        public Builder terrainSeed(long value) { terrainSeed = value; return this; }

        public SimulationConfig build()
        {
            return new SimulationConfig(mapScale, creationMultiplier,
                foundingMultiplier, breedingMultiplier,
                diseaseTransmissionMultiplier, diseaseFatalityMultiplier,
                predatorCreationMultiplier, preyCreationMultiplier,
                predatorFoundingMultiplier, preyFoundingMultiplier,
                predatorBreedingMultiplier, preyBreedingMultiplier,
                randomSeed, terrainSeed);
        }
    }

    private static double positiveOrDefault(double value)
    {
        if(Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0) {
            return 1.0;
        }
        return value;
    }

    private static String format(double value)
    {
        return String.format("%.2f", value);
    }
}
