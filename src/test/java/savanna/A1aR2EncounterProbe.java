package savanna;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Read-only checkpoint metrics for every predator's food and mate opportunities.
 * It deliberately avoids context.getRandom(), so the observation cannot alter
 * either candidate's ecological trajectory.
 */
public final class A1aR2EncounterProbe
{
    private A1aR2EncounterProbe()
    {
    }

    public static void main(String[] args)
    {
        int steps = argument(args, 0, 1000);
        int interval = argument(args, 1, 100);
        long ecologySeed = longArgument(args, 2,
            SimulationConfig.DEFAULT_RANDOM_SEED);
        long terrainSeed = longArgument(args, 3,
            SimulationConfig.DEFAULT_TERRAIN_SEED);
        if(steps <= 0 || interval <= 0) {
            throw new IllegalArgumentException("steps and interval must be positive");
        }

        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(ecologySeed).terrainSeed(terrainSeed).build();
        Randomizer.reset();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        System.out.println("A1A_R2_ENCOUNTER schema=1 ecologySeed=" + ecologySeed +
            " terrainSeed=" + terrainSeed + " steps=" + steps +
            " interval=" + interval);
        for(int index = 1; index <= steps && simulator.getField().isViable();
            index++) {
            simulator.simulateOneStep();
            if(index % interval == 0 || index == steps ||
               !simulator.getField().isViable()) {
                System.out.println("A1A_R2_ENCOUNTER_CHECKPOINT step=" +
                    simulator.getStep() + ' ' + metrics(simulator));
            }
        }
        System.out.println("A1A_R2_ENCOUNTER PASS completed=" +
            simulator.getStep() + " viable=" + simulator.getField().isViable());
    }

    private static String metrics(Simulator simulator)
    {
        Field field = simulator.getField();
        SimulationContext context = simulator.getContext();
        List<SavannahAnimal> animals = new ArrayList<>();
        Map<String, List<SavannahAnimal>> predators = new LinkedHashMap<>();
        for(Animal actor : field.getAnimals()) {
            if(actor instanceof SavannahAnimal && actor.isAlive()) {
                SavannahAnimal animal = (SavannahAnimal)actor;
                animals.add(animal);
                if(animal.getProfile().isPredator()) {
                    predators.computeIfAbsent(animal.getProfile().getName(),
                        ignored -> new ArrayList<>()).add(animal);
                }
            }
        }

        StringBuilder result = new StringBuilder("predators={");
        boolean first = true;
        for(Map.Entry<String, List<SavannahAnimal>> entry : predators.entrySet()) {
            if(!first) result.append(',');
            first = false;
            result.append(entry.getKey()).append('=').append(metricsFor(
                entry.getValue(), animals, context));
        }
        return result.append('}').toString();
    }

    private static String metricsFor(List<SavannahAnimal> predators,
                                     List<SavannahAnimal> animals,
                                     SimulationContext context)
    {
        int adult = 0;
        int female = 0;
        int breedReady = 0;
        int breedReadyWithMate = 0;
        int foodTotal = 0;
        int survivalCritical = 0;
        int withReachablePrey = 0;
        int reachablePreyPairs = 0;
        for(SavannahAnimal predator : predators) {
            foodTotal += predator.getFoodLevel();
            if(predator.isSurvivalCritical()) {
                survivalCritical++;
            }
            if(predator.isAdult()) {
                adult++;
            }
            if(predator.getSex() == Sex.FEMALE) {
                female++;
                if(predator.canBreed()) {
                    breedReady++;
                    if(hasMateNearby(predator, predators, context)) {
                        breedReadyWithMate++;
                    }
                }
            }
            int range = Math.max(1, (int)Math.round(context.getWeatherSystem()
                .getHuntingRange(predator) * predator.getEffectiveStaminaMultiplier()));
            TerrainNavigator.Reachability reachability =
                TerrainNavigator.reachableDistances(context.getTerrainMap(),
                    predator.getLocation(), range);
            int reachable = 0;
            for(SavannahAnimal prey : animals) {
                if(!prey.isAlive() || !predator.getProfile().canEat(
                   prey.getProfile().getName())) {
                    continue;
                }
                int distance = reachability.distanceTo(prey.getLocation());
                if(distance > 0 && distance <= range) {
                    reachable++;
                }
            }
            if(reachable > 0) {
                withReachablePrey++;
                reachablePreyPairs += reachable;
            }
        }
        double averageFood = predators.isEmpty() ? 0.0 :
            (double)foodTotal / predators.size();
        return "{count=" + predators.size() + ",adult=" + adult +
            ",female=" + female + ",breedReady=" + breedReady +
            ",breedReadyWithMate=" + breedReadyWithMate +
            ",survivalCritical=" + survivalCritical + ",avgFood=" +
            String.format(Locale.ROOT, "%.2f", averageFood) +
            ",withReachablePrey=" + withReachablePrey +
            ",reachablePreyPairs=" + reachablePreyPairs + '}';
    }

    private static boolean hasMateNearby(SavannahAnimal parent,
                                         List<SavannahAnimal> cheetahs,
                                         SimulationContext context)
    {
        int range = parent.getProfile().getBreedingRange();
        int recoveryPopulation = Math.max(1,
            context.getFoundingPopulation(parent.getProfile()) * 2);
        if(cheetahs.size() < recoveryPopulation) {
            range *= 2;
        }
        Location parentLocation = parent.getLocation();
        for(SavannahAnimal possibleMate : cheetahs) {
            if(possibleMate == parent || !possibleMate.isAlive() ||
               possibleMate.getSex() != Sex.MALE || !possibleMate.isAdult()) {
                continue;
            }
            Location mateLocation = possibleMate.getLocation();
            if(Math.abs(mateLocation.row() - parentLocation.row()) <= range &&
               Math.abs(mateLocation.col() - parentLocation.col()) <= range &&
               !mateLocation.equals(parentLocation)) {
                return true;
            }
        }
        return false;
    }

    private static int argument(String[] args, int index, int fallback)
    {
        return args.length > index ? Integer.parseInt(args[index]) : fallback;
    }

    private static long longArgument(String[] args, int index, long fallback)
    {
        return args.length > index ? Long.parseLong(args[index]) : fallback;
    }
}
