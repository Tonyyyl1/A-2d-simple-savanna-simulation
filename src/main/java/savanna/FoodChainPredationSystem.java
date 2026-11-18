package savanna;

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
    private static final int KILL_CONTACT_CHEBYSHEV_RANGE = 1;

    private final Random rand;

    public FoodChainPredationSystem()
    {
        this(Randomizer.getRandom());
    }

    public FoodChainPredationSystem(Random rand)
    {
        this.rand = rand;
    }

    public HuntResult huntWithOutcome(SavannahAnimal predator,
                                      InteractionGeometry geometry,
                                      SimulationContext context,
                                      HuntOpportunityFact frozenOpportunity)
    {
        if(predator == null || geometry == null || context == null ||
           frozenOpportunity == null ||
           frozenOpportunity.getActorId() != predator.getId() ||
           frozenOpportunity.getStep() != context.getStep()) {
            throw new IllegalArgumentException(
                "HUNT requires the matching S0 opportunity fact");
        }
        if(!predator.getProfile().isPredator() || !predator.isAdult()) {
            return new HuntResult(HuntOutcome.INELIGIBLE, null, "", 0, 0,
                0.0, HuntRandomLedger.none(), frozenOpportunity);
        }

        double hungerPressure = 1.0 -
            ((double)predator.getFoodLevel() / Math.max(1, predator.getProfile().getMaxFoodLevel()));
        double survivalDrive = Math.max(hungerPressure, predator.getSurvivalPressure());
        if(!predator.isSurvivalCritical() &&
           survivalDrive < 0.25 && rand.nextDouble() > survivalDrive) {
            predator.invalidateProductionTarget(
                ProductionTargetState.Status.SATIATED);
            return new HuntResult(HuntOutcome.DECLINED, null, "", 0, 0,
                0.0, new HuntRandomLedger(true, false, 0, false),
                frozenOpportunity);
        }
        int range = frozenOpportunity.getEffectiveRange();
        List<Location> locations = frozenOpportunity.shuffledSearchLocations(
            context.getRandom());
        HuntLocalOpportunity opportunity =
            frozenOpportunity.resolveFrozenCandidates(predator, geometry,
                locations);
        SavannahAnimal bestPrey = null;
        Location bestLocation = null;
        double bestScore = 0.0;
        for(HuntLocalOpportunity.Candidate candidate : opportunity.getCandidates()) {
            SavannahAnimal prey = candidate.getPrey();
            Location location = candidate.getLocation();
            int distance = candidate.getDistance();
            double abundance = preyAbundanceShare(predator, prey, context);
            double habitat = HabitatPreferences.huntingModifier(predator, prey,
                context.getTerrainMap().getTerrainAt(predator.getLocation()),
                context.getTerrainMap().getTerrainAt(location));
            double score = prey.getPredationVulnerability() *
                           preyStaminaVulnerability(prey) *
                           Math.sqrt(abundance) * habitat /
                           Math.max(1, distance);
            if(score > bestScore) {
                bestScore = score;
                bestPrey = prey;
                bestLocation = location;
            }
        }

        if(bestPrey == null) {
            predator.invalidateProductionTarget(
                ProductionTargetState.Status.NO_ROUTE);
            double cost = SavannahAnimal.STAMINA_HUNT_COST *
                NO_TARGET_HUNT_COST_MULTIPLIER;
            predator.spendStamina(cost);
            return new HuntResult(HuntOutcome.NO_LOCAL_REACHABLE_PREY, null,
                "", range, opportunity.getLocalReachableEdibleCount(), cost,
                new HuntRandomLedger(false, true, locations.size(), false),
                frozenOpportunity);
        }

        predator.acquireHuntTarget(bestPrey, bestLocation, context.getStep());
        if(chebyshevDistance(predator.getLocation(), bestLocation) >
           KILL_CONTACT_CHEBYSHEV_RANGE) {
            return new HuntResult(HuntOutcome.OUT_OF_CONTACT, bestLocation,
                bestPrey.getProfile().getName(), range,
                opportunity.getLocalReachableEdibleCount(), 0.0,
                new HuntRandomLedger(false, true, locations.size(), false),
                frozenOpportunity);
        }

        double successChance = predator.getProfile().getHuntingSkill() *
                               context.getWeatherSystem().getVisibilityModifier() *
                               predator.getHuntingCondition() *
                               bestPrey.getPredationVulnerability() *
                               staminaContestModifier(predator, bestPrey) *
                               HabitatPreferences.huntingModifier(predator,
                                   bestPrey,
                                   context.getTerrainMap()
                                       .getTerrainAt(predator.getLocation()),
                                   context.getTerrainMap()
                                       .getTerrainAt(bestLocation)) *
                               Math.sqrt(preyAbundanceShare(predator, bestPrey, context));
        successChance = Math.min(predator.isSurvivalCritical() ? 0.72 : 0.62,
                                 successChance);
        if(rand.nextDouble() <= successChance) {
            int preFeedFood = context.isPf1aFactCaptureEnabled()
                ? predator.getFoodLevel() : 0;
            double cost = successfulHuntCost(predator, bestPrey);
            predator.spendStamina(cost);
            context.recordEvent(SimulationEvent.hunt(context.getStep(),
                                                     predator, bestPrey));
            context.recordDeath(bestPrey, DeathCause.PREDATION);
            predator.feed(bestPrey.getProfile().getFoodValue());
            predator.invalidateProductionTarget(
                ProductionTargetState.Status.COMPLETED);
            context.getDiseaseSystem().afterPredation(bestPrey, predator, context);
            HuntResult result = new HuntResult(HuntOutcome.SUCCESS, bestLocation,
                bestPrey.getProfile().getName(), range,
                opportunity.getLocalReachableEdibleCount(), cost,
                new HuntRandomLedger(false, true, locations.size(), true),
                frozenOpportunity);
            if(context.isPf1aFactCaptureEnabled()) {
                context.recordRealizedKill(RealizedKillRecord.capture(
                    context.getStep(), predator, bestPrey, preFeedFood,
                    predator.getFoodLevel(), result));
            }
            return result;
        }
        double cost = failedHuntCost(predator, bestPrey);
        predator.spendStamina(cost);
        predator.recordProductionTargetProgress(bestLocation, context.getStep());
        return new HuntResult(HuntOutcome.FAILED_ATTEMPT, null,
            bestPrey.getProfile().getName(), range,
            opportunity.getLocalReachableEdibleCount(), cost,
            new HuntRandomLedger(false, true, locations.size(), true),
            frozenOpportunity);
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

    private int chebyshevDistance(Location first, Location second)
    {
        if(first == null || second == null) return Integer.MAX_VALUE;
        return Math.max(Math.abs(first.row() - second.row()),
            Math.abs(first.col() - second.col()));
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
