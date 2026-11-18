package savanna;

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
    private final TerrainGenerationProfile terrainGenerationProfile;
    private final HydrationPolicy hydrationPolicy;
    private final WaterFoodSecondaryPolicy waterFoodSecondaryPolicy;

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
        this(mapScale, creationMultiplier, foundingMultiplier,
             breedingMultiplier, diseaseTransmissionMultiplier,
             diseaseFatalityMultiplier, predatorCreationMultiplier,
             preyCreationMultiplier, predatorFoundingMultiplier,
             preyFoundingMultiplier, predatorBreedingMultiplier,
             preyBreedingMultiplier, randomSeed, terrainSeed, false);
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
                            long terrainSeed,
                            boolean thirstEnabled)
    {
        this(mapScale, creationMultiplier, foundingMultiplier,
             breedingMultiplier, diseaseTransmissionMultiplier,
             diseaseFatalityMultiplier, predatorCreationMultiplier,
             preyCreationMultiplier, predatorFoundingMultiplier,
             preyFoundingMultiplier, predatorBreedingMultiplier,
             preyBreedingMultiplier, randomSeed, terrainSeed,
             thirstEnabled ? HydrationPolicy.w4Default() :
             HydrationPolicy.disabled(), WaterFoodSecondaryPolicy.LEGACY_EXPLICIT,
             TerrainGenerationProfile.LEGACY_V1);
    }

    private SimulationConfig(int mapScale, double creationMultiplier,
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
                             long randomSeed, long terrainSeed,
                             HydrationPolicy hydrationPolicy,
                             WaterFoodSecondaryPolicy waterFoodSecondaryPolicy,
                             TerrainGenerationProfile terrainGenerationProfile)
    {
        if(mapScale < 1 || mapScale > 4) {
            throw new IllegalArgumentException(
                "ecology.mapScale must be in [1,4]: " + mapScale);
        }
        this.mapScale = mapScale;
        this.creationMultiplier = positive("ecology.creationMultiplier",
            creationMultiplier);
        this.foundingMultiplier = positive("ecology.foundingMultiplier",
            foundingMultiplier);
        this.breedingMultiplier = positive("ecology.breedingMultiplier",
            breedingMultiplier);
        this.predatorCreationMultiplier =
            positive("ecology.predatorCreationMultiplier",
                predatorCreationMultiplier);
        this.preyCreationMultiplier = positive(
            "ecology.preyCreationMultiplier", preyCreationMultiplier);
        this.predatorFoundingMultiplier =
            positive("ecology.predatorFoundingMultiplier",
                predatorFoundingMultiplier);
        this.preyFoundingMultiplier = positive(
            "ecology.preyFoundingMultiplier", preyFoundingMultiplier);
        this.predatorBreedingMultiplier =
            positive("ecology.predatorBreedingMultiplier",
                predatorBreedingMultiplier);
        this.preyBreedingMultiplier = positive(
            "ecology.preyBreedingMultiplier", preyBreedingMultiplier);
        this.diseaseTransmissionMultiplier =
            positive("ecology.diseaseTransmissionMultiplier",
                diseaseTransmissionMultiplier);
        this.diseaseFatalityMultiplier =
            positive("ecology.diseaseFatalityMultiplier",
                diseaseFatalityMultiplier);
        this.randomSeed = randomSeed;
        this.terrainSeed = terrainSeed;
        if(terrainGenerationProfile == null) {
            throw new IllegalArgumentException("ecology.terrainGenerationProfile is required");
        }
        this.terrainGenerationProfile = terrainGenerationProfile;
        if(hydrationPolicy == null) {
            throw new IllegalArgumentException(
                "ecology.hydrationPolicy is required");
        }
        if(waterFoodSecondaryPolicy == null) {
            throw new IllegalArgumentException(
                "ecology.waterFoodSecondaryPolicy is required");
        }
        this.hydrationPolicy = hydrationPolicy;
        this.waterFoodSecondaryPolicy = waterFoodSecondaryPolicy;
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

    public double getPredatorCreationMultiplier()
    {
        return predatorCreationMultiplier;
    }

    public double getPreyCreationMultiplier()
    {
        return preyCreationMultiplier;
    }

    public double getPredatorFoundingMultiplier()
    {
        return predatorFoundingMultiplier;
    }

    public double getPreyFoundingMultiplier()
    {
        return preyFoundingMultiplier;
    }

    public double getPredatorBreedingMultiplier()
    {
        return predatorBreedingMultiplier;
    }

    public double getPreyBreedingMultiplier()
    {
        return preyBreedingMultiplier;
    }

    public long getRandomSeed()
    {
        return randomSeed;
    }

    public long getTerrainSeed()
    {
        return terrainSeed;
    }

    public TerrainGenerationProfile getTerrainGenerationProfile()
    {
        return terrainGenerationProfile;
    }

    public boolean isThirstEnabled()
    {
        return hydrationPolicy.isEnabled();
    }

    public HydrationPolicy getHydrationPolicy()
    {
        return hydrationPolicy;
    }

    public WaterFoodSecondaryPolicy getWaterFoodSecondaryPolicy()
    {
        return waterFoodSecondaryPolicy;
    }

    /** @deprecated since v3.2 C-0 founders come from {@code init.*}; removed with stage S. */
    @Deprecated
    public double creationProbability(SpeciesProfile profile)
    {
        double category = profile.isPredator() ? predatorCreationMultiplier :
            preyCreationMultiplier;
        return Math.max(0.0, Math.min(1.0,
            profile.getCreationProbability() * creationMultiplier * category));
    }

    /** @deprecated since v3.2 C-0 founders come from {@code init.*}; removed with stage S. */
    @Deprecated
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
               " thirst=" + (isThirstEnabled() ? "on" : "off") +
               " randomSeed=" + randomSeed +
               " terrainSeed=" + terrainSeed +
               " terrainProfile=" + terrainGenerationProfile;
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
        private TerrainGenerationProfile terrainGenerationProfile =
            TerrainGenerationProfile.LEGACY_V1;
        private HydrationPolicy hydrationPolicy = HydrationPolicy.disabled();
        private WaterFoodSecondaryPolicy waterFoodSecondaryPolicy =
            WaterFoodSecondaryPolicy.LEGACY_EXPLICIT;

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
        public Builder terrainGenerationProfile(TerrainGenerationProfile value)
        {
            if(value == null) throw new IllegalArgumentException(
                "ecology.terrainGenerationProfile is required");
            terrainGenerationProfile = value;
            return this;
        }
        public Builder thirstEnabled(boolean value)
        {
            hydrationPolicy = value ? HydrationPolicy.w4Default() :
                HydrationPolicy.disabled();
            return this;
        }
        public Builder hydrationPolicy(HydrationPolicy value)
        {
            if(value == null) {
                throw new IllegalArgumentException(
                    "ecology.hydrationPolicy is required");
            }
            hydrationPolicy = value;
            return this;
        }
        public Builder waterFoodSecondaryPolicy(WaterFoodSecondaryPolicy value)
        {
            if(value == null) {
                throw new IllegalArgumentException(
                    "ecology.waterFoodSecondaryPolicy is required");
            }
            waterFoodSecondaryPolicy = value;
            return this;
        }

        public SimulationConfig build()
        {
            return new SimulationConfig(mapScale, creationMultiplier,
                foundingMultiplier, breedingMultiplier,
                diseaseTransmissionMultiplier, diseaseFatalityMultiplier,
                predatorCreationMultiplier, preyCreationMultiplier,
                predatorFoundingMultiplier, preyFoundingMultiplier,
                predatorBreedingMultiplier, preyBreedingMultiplier,
                randomSeed, terrainSeed, hydrationPolicy, waterFoodSecondaryPolicy,
                terrainGenerationProfile);
        }
    }

    private static double positive(String key, double value)
    {
        if(Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(
                key + " must be finite and > 0: " + value);
        }
        return value;
    }

    private static String format(double value)
    {
        return String.format("%.2f", value);
    }
}
