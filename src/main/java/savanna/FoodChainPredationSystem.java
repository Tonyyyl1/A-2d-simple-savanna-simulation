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

        EcologyParameters parameters = EcologyParameters.current();
        // M3 change B: above the satiation fraction a fed predator does not
        // hunt at all (0 = off: only the discretionary decline below).
        double satiation = parameters.get(EcologyParameters.Key.SATIATION_FRACTION);
        if(satiation > 0.0 && !predator.isSurvivalCritical() &&
           predator.getFoodLevel() >= satiation * predator.getProfile().getMaxFoodLevel()) {
            predator.invalidateProductionTarget(
                ProductionTargetState.Status.SATIATED);
            return new HuntResult(HuntOutcome.DECLINED, null, "", 0, 0,
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
        // Plan v3.0 M2-1: prey "abundance" is the species share among the
        // frozen local candidates, not the map-wide population share.
        java.util.Map<String, Integer> localCounts = new java.util.HashMap<>();
        for(HuntLocalOpportunity.Candidate candidate : opportunity.getCandidates()) {
            localCounts.merge(candidate.getPrey().getProfile().getName(), 1, Integer::sum);
        }
        int localTotal = opportunity.getCandidates().size();
        double sizeExponent = parameters.get(EcologyParameters.Key.PREY_SIZE_PREFERENCE);
        double meanPreyFood = meanEdibleFoodValue(predator.getProfile(),
            parameters.getInt(EcologyParameters.Key.CHEETAH_ZEBRA_STAGE));
        for(HuntLocalOpportunity.Candidate candidate : opportunity.getCandidates()) {
            SavannahAnimal prey = candidate.getPrey();
            Location location = candidate.getLocation();
            int distance = candidate.getDistance();
            double abundance = localShare(localCounts, localTotal, prey);
            double habitat = HabitatPreferences.huntingModifier(predator, prey,
                context.getTerrainMap().getTerrainAt(predator.getLocation()),
                context.getTerrainMap().getTerrainAt(location));
            double score = prey.getPredationVulnerability() *
                           preyStaminaVulnerability(prey) *
                           Math.sqrt(abundance) * habitat /
                           Math.max(1, distance) *
                           sizePreference(prey.getProfile().getFoodValue(),
                               meanPreyFood, sizeExponent);
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
        if(!inStrikeReach(context, predator, bestLocation)) {
            return new HuntResult(HuntOutcome.OUT_OF_CONTACT, bestLocation,
                bestPrey.getProfile().getName(), range,
                opportunity.getLocalReachableEdibleCount(), 0.0,
                new HuntRandomLedger(false, true, locations.size(), false),
                frozenOpportunity);
        }

        int sameSpecies = localCounts.getOrDefault(bestPrey.getProfile().getName(), 0);
        double successChance = predator.getProfile().getHuntingSkill() *
                               skillScale(parameters, predator) *
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
                               Math.sqrt(localShare(localCounts, localTotal, bestPrey)) *
                               refugeFactor(sameSpecies, parameters.getInt(
                                   EcologyParameters.Key.REFUGE_COUNT)) *
                               vigilanceFactor(sameSpecies, parameters.get(
                                   EcologyParameters.Key.VIGILANCE_PER_NEIGHBOUR)) *
                               densityResponse(sameSpecies, parameters.getInt(
                                   EcologyParameters.Key.REFUGE_COUNT),
                                   parameters.get(
                                       EcologyParameters.Key.DENSITY_RESPONSE)) *
                               waterZoneFactor(parameters, context, bestLocation);
        double interference = parameters.get(SpeciesRegistry.LION.equals(predator.getProfile().getName())
            ? EcologyParameters.Key.INTERFERENCE_LION : EcologyParameters.Key.INTERFERENCE_CHEETAH);
        if(interference > 0.0) {
            int rivals = context.getSpatialEcologyView().getLocalOccupancy(predator.getLocation(),
                Math.max(1, range), predator.getProfile()).getConspecificCount();
            successChance /= 1.0 + interference * Math.max(0, rivals - 1);
        }
        successChance = Math.min(parameters.get(predator.isSurvivalCritical()
                ? EcologyParameters.Key.HUNT_SUCCESS_CAP_CRITICAL
                : EcologyParameters.Key.HUNT_SUCCESS_CAP), successChance);
        if(rand.nextDouble() <= successChance) {
            double cost = successfulHuntCost(predator, bestPrey);
            predator.spendStamina(cost);
            context.recordEvent(SimulationEvent.hunt(context.getStep(),
                                                     predator, bestPrey));
            context.recordDeath(bestPrey, DeathCause.PREDATION);
            predator.feed(bestPrey.getProfile().getFoodValue());
            context.provisionDependents(predator);
            predator.invalidateProductionTarget(
                ProductionTargetState.Status.COMPLETED);
            context.getDiseaseSystem().afterPredation(bestPrey, predator, context);
            HuntResult result = new HuntResult(HuntOutcome.SUCCESS, bestLocation,
                bestPrey.getProfile().getName(), range,
                opportunity.getLocalReachableEdibleCount(), cost,
                new HuntRandomLedger(false, true, locations.size(), true),
                frozenOpportunity);
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

    /**
     * M3 change E: target choice favours prey with more food,
     * {@code (food / meanFood)^k}; k = 0 leaves the score unchanged. Only a
     * predator with several prey species (Lion) is affected.
     */
    static double sizePreference(int foodValue, double meanFood, double exponent)
    {
        if(exponent == 0.0 || meanFood <= 0.0) return 1.0;
        return Math.pow(foodValue / meanFood, exponent);
    }

    /** Mean food value of the species this predator can eat. */
    private static double meanEdibleFoodValue(SpeciesProfile predator,
                                              int zebraStage)
    {
        double sum = 0.0;
        int count = 0;
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            SpeciesProfile prey = factory.getProfile();
            if(predator.canEat(prey.getName()) ||
               (zebraStage > 0 && SpeciesRegistry.CHEETAH.equals(predator.getName()) &&
                SpeciesRegistry.ZEBRA.equals(prey.getName()))) {
                sum += prey.getFoodValue();
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    /**
     * M3 change C, refuge: with fewer than {@code refugeCount} of the target's
     * species among the predator's local candidates, success falls in
     * proportion, so the last animals of a scarce prey are hard to find.
     * 0 turns it off.
     */
    static double refugeFactor(int sameSpecies, int refugeCount)
    {
        if(refugeCount <= 0 || sameSpecies >= refugeCount) return 1.0;
        return Math.max(0, sameSpecies) / (double)refugeCount;
    }

    /**
     * M3 change C, vigilance: each other local member of the target's
     * species lowers success to {@code 1 / (1 + v * (n - 1))}. 0 turns it off.
     */
    static double vigilanceFactor(int sameSpecies, double perNeighbour)
    {
        if(perNeighbour <= 0.0 || sameSpecies <= 1) return 1.0;
        return 1.0 / (1.0 + perNeighbour * (sameSpecies - 1));
    }

    /**
     * M3 change H2, density response: above {@code refugeCount} local members
     * of the target's species, success rises by {@code beta} per extra
     * refuge-count of prey, so dense prey are eaten faster. 0 turns it off.
     */
    static double densityResponse(int sameSpecies, int refugeCount, double beta)
    {
        if(beta <= 0.0 || refugeCount <= 0 || sameSpecies <= refugeCount) return 1.0;
        return 1.0 + beta * (sameSpecies - refugeCount) / (double)refugeCount;
    }

    /**
     * M3 change W, waterhole zone: hunt success against a target within
     * {@code waterZoneRadius} of water is multiplied by {@code waterZoneSuccess}.
     * Radius 0 or scale 1 leaves success unchanged (and skips the terrain lookup).
     */
    static double waterZoneFactor(EcologyParameters parameters,
                                  SimulationContext context, Location target)
    {
        int radius = parameters.getInt(EcologyParameters.Key.WATER_ZONE_RADIUS);
        double scale = parameters.get(EcologyParameters.Key.WATER_ZONE_SUCCESS);
        if(radius <= 0 || scale == 1.0) return 1.0;
        return waterZoneFactor(
            context.getTerrainMap().chebyshevDistanceToWater(target), radius, scale);
    }

    static double waterZoneFactor(int distanceToWater, int radius, double scale)
    {
        if(radius <= 0 || scale == 1.0 || distanceToWater > radius) return 1.0;
        return scale;
    }

    /** Per-species hunting-skill scale (M3 family 1); 1.0 leaves the profile value. */
    private static double skillScale(EcologyParameters parameters,
                                     SavannahAnimal predator)
    {
        String species = predator.getProfile().getName();
        if(SpeciesRegistry.LION.equals(species)) {
            return parameters.get(EcologyParameters.Key.LION_SKILL_SCALE);
        }
        if(SpeciesRegistry.CHEETAH.equals(species)) {
            return parameters.get(EcologyParameters.Key.CHEETAH_SKILL_SCALE);
        }
        return 1.0;
    }

    private static double localShare(java.util.Map<String, Integer> counts,
                                     int total, SavannahAnimal prey)
    {
        if(total <= 0) return 0.0;
        int count = counts.getOrDefault(prey.getProfile().getName(), 0);
        return Math.max(0.005, (double)count / total);
    }

    /**
     * B-1 strike reach: a predator strikes prey it could walk onto within one
     * step (its movement speed, along land); adjacent prey are always in reach.
     * Prey move at the same time as the hunter, so requiring adjacency at the
     * step start would let any moving prey stay one cell ahead for ever.
     * A successful strike moves the predator onto the prey's cell, which is
     * therefore never farther than the movement limit.
     */
    private boolean inStrikeReach(SimulationContext context, SavannahAnimal predator,
                                  Location target)
    {
        int cheb = chebyshevDistance(predator.getLocation(), target);
        if(cheb <= KILL_CONTACT_CHEBYSHEV_RANGE) return true;
        int reach = predator.maxMoveDistance();
        if(cheb > reach) return false;
        int land = TerrainNavigator.reachableDistances(context.getTerrainMap(),
            predator.getLocation(), reach).distanceTo(target);
        return land >= 1 && land <= reach;
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
