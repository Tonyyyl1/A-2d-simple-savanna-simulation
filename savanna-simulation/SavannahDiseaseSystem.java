import java.util.List;
import java.util.Random;

/**
 * Disease model linking rain, close contact, foodborne infection, and age.
 */
public class SavannahDiseaseSystem implements DiseaseSystem
{
    private static final double ENVIRONMENTAL_INFECTION_RISK = 0.00008;
    private static final double CONTACT_TRANSMISSION_RISK = 0.018;
    private static final double FOODBORNE_TRANSMISSION_RISK = 0.28;
    private static final double RECOVERY_BASE = 0.030;
    private static final double FATALITY_BASE = 0.004;

    private final Random rand;
    private int diseaseDeaths;

    public SavannahDiseaseSystem()
    {
        this(Randomizer.getRandom());
    }

    public SavannahDiseaseSystem(Random rand)
    {
        this.rand = rand;
    }

    public void progressDisease(SavannahAnimal animal, SimulationContext context, Field field)
    {
        if(animal.isInfected()) {
            animal.increaseInfection(0.012 * context.getWeatherSystem().getInfectionModifier());
            double recoveryChance = RECOVERY_BASE * animal.getDiseaseResistance();
            if(context.getWeatherSystem().getCurrentWeather() == WeatherType.RAIN) {
                recoveryChance *= 0.82;
            }
            if(rand.nextDouble() < recoveryChance) {
                animal.recoverFromDisease();
                context.recordEvent(SimulationEvent.recovery(context.getStep(), animal));
            }
            else {
                double fatalityChance = FATALITY_BASE *
                                        context.getConfig().getDiseaseFatalityMultiplier() *
                                        animal.getInfectionLevel() *
                                        (1.0 - animal.getDiseaseResistance());
                if(rand.nextDouble() < fatalityChance) {
                    context.recordEvent(SimulationEvent.diseaseDeath(context.getStep(), animal));
                    animal.setDead();
                    diseaseDeaths++;
                    return;
                }
            }

            List<Location> adjacent =
                field.getAdjacentLocations(animal.getLocation(),
                                           context.getRandom());
            for(Location location : adjacent) {
                Animal other = field.getAnimalAt(location);
                if(other instanceof SavannahAnimal) {
                    expose(animal, (SavannahAnimal)other, CONTACT_TRANSMISSION_RISK, context);
                }
            }
        }
        else {
            double risk = ENVIRONMENTAL_INFECTION_RISK *
                          context.getConfig().getDiseaseTransmissionMultiplier() *
                          context.getWeatherSystem().getInfectionModifier() *
                          (1.0 - animal.getDiseaseResistance());
            if(rand.nextDouble() < risk) {
                animal.infect(0.16);
                context.recordEvent(SimulationEvent.infection(context.getStep(), animal));
            }
        }
    }

    public void expose(SavannahAnimal source, SavannahAnimal target, double contactIntensity,
                       SimulationContext context)
    {
        if(source == null || target == null || !source.isInfected() ||
           !target.isAlive() || target.isInfected()) {
            return;
        }
        double chance = contactIntensity *
                        context.getConfig().getDiseaseTransmissionMultiplier() *
                        context.getWeatherSystem().getInfectionModifier() *
                        (1.0 - target.getDiseaseResistance()) *
                        source.getInfectiousness();
        if(rand.nextDouble() < chance) {
            target.infect(0.22 + source.getInfectionLevel() * 0.20);
            context.recordEvent(SimulationEvent.infection(context.getStep(),
                                                          source, target));
        }
    }

    public void afterPredation(SavannahAnimal prey, SavannahAnimal predator, SimulationContext context)
    {
        if(prey != null && predator != null && prey.isInfected()) {
            expose(prey, predator, FOODBORNE_TRANSMISSION_RISK, context);
        }
    }

    public int countInfected(Field field)
    {
        int infected = 0;
        for(Animal animal : field.getAnimals()) {
            if(animal instanceof SavannahAnimal) {
                SavannahAnimal savannahAnimal = (SavannahAnimal)animal;
                if(savannahAnimal.isAlive() && savannahAnimal.isInfected()) {
                    infected++;
                }
            }
        }
        return infected;
    }

    public int getDiseaseDeaths()
    {
        return diseaseDeaths;
    }

    public void resetDiseaseDeaths()
    {
        diseaseDeaths = 0;
    }
}
