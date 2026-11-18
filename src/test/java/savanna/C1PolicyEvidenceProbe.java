package savanna;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only C1 evidence report for calibration, breeding and food policy. */
public final class C1PolicyEvidenceProbe
{
    private C1PolicyEvidenceProbe()
    {
    }

    public static void main(String[] args)
    {
        int steps = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        WaterFoodSecondaryPolicy foodPolicy = args.length > 1 &&
            "water-only".equalsIgnoreCase(args[1])
                ? WaterFoodSecondaryPolicy.WATER_ONLY
                : WaterFoodSecondaryPolicy.LEGACY_EXPLICIT;
        SimulationConfig config = SimulationConfig.builder().mapScale(3)
            .thirstEnabled(true).waterFoodSecondaryPolicy(foodPolicy).build();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        Map<String, Integer> animalHours = new LinkedHashMap<>();
        Map<String, Integer> drinks = new LinkedHashMap<>();
        Map<String, Double> minimumHydration = new LinkedHashMap<>();
        Map<HydrationStage, Integer> breedingByStage =
            new EnumMap<>(HydrationStage.class);
        Map<DeathCause, Integer> deaths = new EnumMap<>(DeathCause.class);
        int waterFoodSecondary = 0;

        for(int step = 1; step <= steps && simulator.getField().isViable(); step++) {
            simulator.simulateOneStep();
            SimulationContext context = simulator.getContext();
            for(SimulationEvent event : context.getCurrentEvents()) {
                if(event.type == SimulationEvent.EventType.DRINK) {
                    drinks.put(event.actorSpecies,
                        drinks.getOrDefault(event.actorSpecies, 0) + 1);
                }
            }
            for(Animal animal : simulator.getField().getAnimals()) {
                if(!(animal instanceof SavannahAnimal) || !animal.isAlive()) {
                    continue;
                }
                SavannahAnimal savannahAnimal = (SavannahAnimal)animal;
                String species = savannahAnimal.getProfile().getName();
                animalHours.put(species, animalHours.getOrDefault(species, 0) + 1);
                minimumHydration.put(species, Math.min(
                    minimumHydration.getOrDefault(species, 100.0),
                    savannahAnimal.getHydrationPercent()));
                BehaviorOutcome outcome = savannahAnimal.getLastBehaviorOutcome();
                if(outcome == null || outcome.getPrimaryIntent() == null) {
                    continue;
                }
                HydrationStage stage = context.getThirstSystem()
                    .assess(savannahAnimal, context).getStage();
                if(outcome.getPrimaryIntent().getActionType() == ActionType.BREED &&
                   outcome.getExecutedEffects().contains(ExecutedEffect.BREED)) {
                    breedingByStage.put(stage,
                        breedingByStage.getOrDefault(stage, 0) + 1);
                }
                if(outcome.getPrimaryIntent().getActionType() == ActionType.SEEK_WATER &&
                   outcome.getExecutedEffects().contains(ExecutedEffect.GRAZE)) {
                    waterFoodSecondary++;
                }
            }
            for(DeathCause cause : DeathCause.values()) {
                deaths.put(cause, context.getDeathCount(cause));
            }
        }

        System.out.println("C1 evidence steps=" + simulator.getStep() +
                           " waterFoodPolicy=" + foodPolicy);
        System.out.println("animalHours=" + animalHours);
        System.out.println("drinks=" + drinks);
        System.out.println("minimumHydration=" + minimumHydration);
        System.out.println("breedEffectsByHydrationStage=" + breedingByStage);
        System.out.println("seekWaterCriticalFoodSecondary=" + waterFoodSecondary);
        System.out.println("deathCounts=" + deaths);
        for(String species : animalHours.keySet()) {
            int hours = animalHours.get(species);
            int drinkCount = drinks.getOrDefault(species, 0);
            double rate = hours == 0 ? 0.0 : 100.0 * drinkCount / hours;
            System.out.println("drinkPer100AnimalHours " + species + "=" +
                               String.format("%.3f", rate));
        }
    }
}
