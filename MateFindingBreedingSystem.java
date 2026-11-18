import java.util.List;
import java.util.Random;

/**
 * Breeding requires a female and a nearby adult male of the same species.
 */
public class MateFindingBreedingSystem implements BreedingSystem
{
    private final Random rand;

    public MateFindingBreedingSystem()
    {
        this(Randomizer.getRandom());
    }

    public MateFindingBreedingSystem(Random rand)
    {
        this.rand = rand;
    }

    public void tryBreed(SavannahAnimal parent, Field currentField, Field nextFieldState,
                         SimulationContext context, List<Location> freeLocations)
    {
        if(parent.getSex() != Sex.FEMALE || !parent.canBreed() || freeLocations.isEmpty()) {
            return;
        }
        if(!hasMateNearby(parent, currentField, context)) {
            return;
        }

        double probability = context.getBreedingProbability(parent.getProfile());
        if(parent.isInfected()) {
            probability *= 0.55;
        }
        probability *= Math.max(0.10, 1.0 - parent.getSurvivalPressure() * 0.82);
        probability *= Math.max(0.20, parent.getEffectiveStaminaMultiplier());
        if(context.getWeatherSystem().getCurrentWeather() == WeatherType.DROUGHT) {
            probability *= 0.72;
        }
        else if(context.getWeatherSystem().getCurrentWeather() == WeatherType.RAIN &&
                !parent.getProfile().isPredator()) {
            probability *= 1.12;
        }
        if(!parent.getProfile().isPredator()) {
            probability *= plantFoodModifier(context);
        }
        else {
            probability *= predatorFoodModifier(parent, context);
        }
        probability *= lowPopulationRecoveryModifier(parent, context);
        probability *= populationBalanceModifier(parent, context);
        probability *= crowdingModifier(currentField);

        if(rand.nextDouble() <= probability) {
            int births = rand.nextInt(parent.getProfile().getMaxLitterSize()) + 1;
            for(int index = 0; index < births && !freeLocations.isEmpty(); index++) {
                Location birthLocation = freeLocations.remove(0);
                SavannahAnimal child = parent.createChild(birthLocation);
                nextFieldState.placeAnimal(child, birthLocation);
                context.recordEvent(SimulationEvent.birth(context.getStep(),
                                                          parent, child,
                                                          birthLocation));
            }
            parent.spendBreedingStamina();
        }
    }

    private boolean hasMateNearby(SavannahAnimal parent, Field currentField,
                                  SimulationContext context)
    {
        int range = parent.getProfile().getBreedingRange();
        int population = context.getPopulation(parent.getProfile().getName());
        int recoveryPopulation = Math.max(1,
            context.getFoundingPopulation(parent.getProfile()) * 2);
        if(population < recoveryPopulation) {
            range *= 2;
        }
        List<Location> locations =
            currentField.getLocationsWithin(parent.getLocation(), range,
                                            context.getRandom());
        for(Location location : locations) {
            Animal animal = currentField.getAnimalAt(location);
            if(animal instanceof SavannahAnimal) {
                SavannahAnimal possibleMate = (SavannahAnimal)animal;
                if(possibleMate.isAlive() &&
                   possibleMate.getSex() == Sex.MALE &&
                   possibleMate.isAdult() &&
                   possibleMate.getProfile().getName().equals(parent.getProfile().getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private double crowdingModifier(Field field)
    {
        double density = (double)field.getLivingPopulation() / (field.getDepth() * field.getWidth());
        if(density > 0.60) {
            return 0.03;
        }
        else if(density > 0.48) {
            return 0.10;
        }
        else if(density > 0.36) {
            return 0.30;
        }
        else if(density > 0.25) {
            return 0.65;
        }
        else if(density < 0.12) {
            return 1.18;
        }
        else {
            return 1.0;
        }
    }

    private double plantFoodModifier(SimulationContext context)
    {
        double grass = context.getFoodSystem().getAverageFoodLevel();
        return Math.max(0.15, Math.min(1.12, grass * 1.55));
    }

    private double predatorFoodModifier(SavannahAnimal parent, SimulationContext context)
    {
        int ediblePrey = 0;
        int preyTypes = 0;
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            SpeciesProfile profile = factory.getProfile();
            if(parent.getProfile().canEat(profile.getName())) {
                ediblePrey += context.getPopulation(profile.getName());
                preyTypes++;
            }
        }
        int predators = Math.max(1, context.getPopulation(parent.getProfile().getName()));
        double familyDemand = 28.0 / Math.sqrt(Math.max(1, preyTypes));
        double preyPerPredatorFamily = ediblePrey / (predators * familyDemand);
        double foodCondition = (double)parent.getFoodLevel() /
                               Math.max(1, parent.getProfile().getMaxFoodLevel());
        return Math.max(0.04, Math.min(1.0, preyPerPredatorFamily)) * foodCondition;
    }

    private double lowPopulationRecoveryModifier(SavannahAnimal parent,
                                                 SimulationContext context)
    {
        int population = context.getPopulation(parent.getProfile().getName());
        int recoveryPopulation = Math.max(1,
            context.getFoundingPopulation(parent.getProfile()) * 2);
        if(population >= recoveryPopulation) {
            return 1.0;
        }
        double shortage = (double)(recoveryPopulation - population) / recoveryPopulation;
        return 1.0 + shortage * 1.65;
    }

    private double populationBalanceModifier(SavannahAnimal parent,
                                             SimulationContext context)
    {
        int population = context.getPopulation(parent.getProfile().getName());
        int target = populationTarget(parent.getProfile(), context);
        if(population < target / 2) {
            double shortage = (double)(target / 2 - population) / Math.max(1, target / 2);
            return 1.0 + shortage * 1.25;
        }
        else if(population > target) {
            double excess = (double)(population - target) / Math.max(1, target);
            return Math.max(0.05, 1.0 - excess * 1.35);
        }
        else {
            return 1.0;
        }
    }

    private int populationTarget(SpeciesProfile profile, SimulationContext context)
    {
        int multiplier = profile.isPredator() ? 5 : 10;
        return Math.max(1, context.getFoundingPopulation(profile) * multiplier);
    }
}
