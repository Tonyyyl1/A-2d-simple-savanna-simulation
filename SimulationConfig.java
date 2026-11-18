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
               " diseaseFatal=" + format(diseaseFatalityMultiplier);
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
