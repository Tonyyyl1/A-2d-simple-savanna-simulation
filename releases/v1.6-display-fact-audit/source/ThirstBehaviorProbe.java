import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Repeatable, read-only behavior report for the v1.6 thirst experiment.
 * It reports observables without changing the simulation's random stream or
 * adding any behavior to the production loop.
 */
public class ThirstBehaviorProbe
{
    private static int drinkWindow;
    private static int drinkTotal;
    private static final Map<String, Integer> drinkBySpecies =
        new LinkedHashMap<>();
    private ThirstBehaviorProbe() {}

    public static void main(String[] args)
    {
        int steps = args.length > 0 ? Integer.parseInt(args[0]) : 800;
        int interval = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        SimulationConfig config = SimulationConfig.builder()
            .mapScale(3).thirstEnabled(true).build();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        SimulationContext context = simulator.getContext();
        ThirstSystem thirst = context.getThirstSystem();
        TerrainMap terrain = context.getTerrainMap();

        int passable = 0;
        int water = 0;
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                Location location = new Location(row, col);
                if(terrain.isPassable(location)) {
                    passable++;
                }
                else if(terrain.getTerrainAt(location) == TerrainType.WATERHOLE) {
                    water++;
                }
            }
        }
        System.out.println("Thirst behavior probe: steps=" + steps +
                           " interval=" + interval + " config=" +
                           config.describe());
        System.out.println("Map: passable=" + passable + " water=" + water +
                           " drinkableShore=" + thirst.countDrinkableShores() +
                           " maxShoreDistance=" +
                           thirst.maximumReachableShoreDistance());

        for(int step = 1; step <= steps && simulator.getField().isViable(); step++) {
            simulator.simulateOneStep();
            for(SimulationEvent event : context.getCurrentEvents()) {
                if(event.type == SimulationEvent.EventType.DRINK) {
                    drinkWindow++;
                    drinkTotal++;
                    drinkBySpecies.put(event.actorSpecies,
                        drinkBySpecies.getOrDefault(event.actorSpecies, 0) + 1);
                }
            }
            if(step % interval == 0 || step == steps) {
                printWindow(simulator, step, drinkWindow);
                drinkWindow = 0;
            }
        }
        System.out.println("Probe complete at step " + simulator.getStep() +
                           " totalDrink=" + drinkTotal +
                           " drinkBySpecies=" + drinkBySpecies);
    }

    private static void printWindow(Simulator simulator, int step,
                                    int drinkInWindow)
    {
        SimulationContext context = simulator.getContext();
        Map<String, Map<HydrationStage, Integer>> stageBySpecies =
            new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Double> hydration = new LinkedHashMap<>();
        int shoreAnimals = 0;
        for(Animal animal : simulator.getField().getAnimals()) {
            if(!(animal instanceof SavannahAnimal) || !animal.isAlive()) {
                continue;
            }
            SavannahAnimal savannahAnimal = (SavannahAnimal)animal;
            String species = savannahAnimal.getProfile().getName();
            counts.put(species, counts.containsKey(species) ? counts.get(species) + 1 : 1);
            double total = hydration.containsKey(species) ? hydration.get(species) : 0.0;
            hydration.put(species, total + savannahAnimal.getHydrationPercent());
            HydrationAssessment assessment = context.getThirstSystem()
                .assess(savannahAnimal, context);
            Map<HydrationStage, Integer> stages = stageBySpecies.get(species);
            if(stages == null) {
                stages = new java.util.EnumMap<>(HydrationStage.class);
                stageBySpecies.put(species, stages);
            }
            stages.put(assessment.getStage(),
                stages.getOrDefault(assessment.getStage(), 0) + 1);
            if(context.getThirstSystem().isDrinkableShore(savannahAnimal.getLocation())) {
                shoreAnimals++;
            }
        }
        for(String species : counts.keySet()) {
            double average = hydration.get(species) / counts.get(species);
            System.out.println("step " + step + " " + species +
                " count=" + counts.get(species) +
                " avgHydration=" + String.format("%.1f", average) +
                " hydrationStages=" + stageBySpecies.get(species));
        }
        System.out.println("step " + step + " drinkInterval=" + drinkInWindow +
                           " shoreAnimals=" + shoreAnimals);
    }
}
