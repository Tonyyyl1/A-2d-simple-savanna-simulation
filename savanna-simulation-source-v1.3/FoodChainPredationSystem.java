import java.util.List;
import java.util.Random;

/**
 * Generic food-chain implementation driven by species profiles.
 */
public class FoodChainPredationSystem implements PredationSystem
{
    private static final double STAMINA_GAP_THRESHOLD = 18.0;
    private static final double MAX_FAILURE_COST_BONUS = 0.90;
    private static final double MAX_SUCCESS_COST_REDUCTION = 0.45;
    private static final double NO_TARGET_HUNT_COST_MULTIPLIER = 0.65;

    private final Random rand = Randomizer.getRandom();

    public Location hunt(SavannahAnimal predator, Field field, SimulationContext context)
    {
        if(!predator.getProfile().isPredator() || !predator.isAdult()) {
            return null;
        }

        double hungerPressure = 1.0 -
            ((double)predator.getFoodLevel() / Math.max(1, predator.getProfile().getMaxFoodLevel()));
        double survivalDrive = Math.max(hungerPressure, predator.getSurvivalPressure());
        if(!predator.isSurvivalCritical() &&
           survivalDrive < 0.25 && rand.nextDouble() > survivalDrive) {
            return null;
        }
        int range = Math.max(1, (int)Math.round(
            context.getWeatherSystem().getHuntingRange(predator) *
            predator.getEffectiveStaminaMultiplier()));
        List<Location> locations = field.getLocationsWithin(predator.getLocation(), range);
        SavannahAnimal bestPrey = null;
        Location bestLocation = null;
        double bestScore = 0.0;
        for(Location location : locations) {
            Animal animal = field.getAnimalAt(location);
            if(animal instanceof SavannahAnimal) {
                SavannahAnimal prey = (SavannahAnimal)animal;
                if(prey.isAlive() && predator.getProfile().canEat(prey.getProfile().getName())) {
                    int distance = distance(predator.getLocation(), location);
                    double abundance = preyAbundanceShare(predator, prey, context);
                    double score = prey.getPredationVulnerability() *
                                   preyStaminaVulnerability(prey) *
                                   Math.sqrt(abundance) / Math.max(1, distance);
                    if(score > bestScore) {
                        bestScore = score;
                        bestPrey = prey;
                        bestLocation = location;
                    }
                }
            }
        }

        if(bestPrey == null) {
            predator.spendStamina(SavannahAnimal.STAMINA_HUNT_COST *
                                  NO_TARGET_HUNT_COST_MULTIPLIER);
            return null;
        }

        double successChance = predator.getProfile().getHuntingSkill() *
                               context.getWeatherSystem().getVisibilityModifier() *
                               predator.getHuntingCondition() *
                               bestPrey.getPredationVulnerability() *
                               staminaContestModifier(predator, bestPrey) *
                               Math.sqrt(preyAbundanceShare(predator, bestPrey, context));
        successChance = Math.min(predator.isSurvivalCritical() ? 0.72 : 0.62,
                                 successChance);
        if(rand.nextDouble() <= successChance) {
            predator.spendStamina(successfulHuntCost(predator, bestPrey));
            context.recordEvent(SimulationEvent.hunt(context.getStep(),
                                                     predator, bestPrey));
            bestPrey.setDead();
            predator.feed(bestPrey.getProfile().getFoodValue());
            context.getDiseaseSystem().afterPredation(bestPrey, predator, context);
            return bestLocation;
        }
        predator.spendStamina(failedHuntCost(predator, bestPrey));
        return null;
    }

    private int distance(Location first, Location second)
    {
        int rowDistance = Math.abs(first.row() - second.row());
        int colDistance = Math.abs(first.col() - second.col());
        return Math.max(rowDistance, colDistance);
    }

    private double preyAbundanceShare(SavannahAnimal predator, SavannahAnimal prey,
                                      SimulationContext context)
    {
        int totalPrey = 0;
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            SpeciesProfile profile = factory.getProfile();
            if(predator.getProfile().canEat(profile.getName())) {
                totalPrey += context.getPopulation(profile.getName());
            }
        }
        if(totalPrey <= 0) {
            return 0.0;
        }
        int preyCount = context.getPopulation(prey.getProfile().getName());
        return Math.max(0.005, (double)preyCount / totalPrey);
    }

    private double preyStaminaVulnerability(SavannahAnimal prey)
    {
        return 1.35 - prey.getEffectiveStaminaMultiplier() * 0.35;
    }

    private double staminaContestModifier(SavannahAnimal predator, SavannahAnimal prey)
    {
        double gap = predator.getStaminaPercent() - prey.getStaminaPercent();
        double adjustedGap = Math.abs(gap) - STAMINA_GAP_THRESHOLD;
        if(adjustedGap <= 0.0) {
            return 1.0;
        }
        double scaledGap = Math.min(1.0, adjustedGap / (100.0 - STAMINA_GAP_THRESHOLD));
        if(gap > 0.0) {
            return 1.0 + scaledGap * 0.35;
        }
        else {
            return 1.0 - scaledGap * 0.55;
        }
    }

    private double failedHuntCost(SavannahAnimal predator, SavannahAnimal prey)
    {
        double preyAdvantage = prey.getStaminaPercent() - predator.getStaminaPercent();
        double adjustedGap = preyAdvantage - STAMINA_GAP_THRESHOLD;
        if(adjustedGap <= 0.0) {
            return SavannahAnimal.STAMINA_HUNT_COST;
        }
        double scaledGap = Math.min(1.0, adjustedGap / (100.0 - STAMINA_GAP_THRESHOLD));
        return SavannahAnimal.STAMINA_HUNT_COST *
               (1.0 + scaledGap * MAX_FAILURE_COST_BONUS);
    }

    private double successfulHuntCost(SavannahAnimal predator, SavannahAnimal prey)
    {
        double predatorAdvantage = predator.getStaminaPercent() - prey.getStaminaPercent();
        double adjustedGap = predatorAdvantage - STAMINA_GAP_THRESHOLD;
        if(adjustedGap <= 0.0) {
            return SavannahAnimal.STAMINA_HUNT_COST;
        }
        double scaledGap = Math.min(1.0, adjustedGap / (100.0 - STAMINA_GAP_THRESHOLD));
        return SavannahAnimal.STAMINA_HUNT_COST *
               (1.0 - scaledGap * MAX_SUCCESS_COST_REDUCTION);
    }
}
