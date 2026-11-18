package savanna;

import java.util.List;

/**
 * Experimental thirst system. Animals drink from passable shoreline cells next
 * to waterholes; waterhole terrain itself remains impassable.
 */
public class SavannahThirstSystem implements ThirstSystem
{
    private final TerrainMap terrain;
    private final WaterTopology topology;
    private final WaterTargetPlanner targetPlanner;
    private final HydrationPolicy policy;

    public SavannahThirstSystem(TerrainMap terrain)
    {
        this(terrain, HydrationPolicy.w4Default());
    }

    public SavannahThirstSystem(TerrainMap terrain, HydrationPolicy policy)
    {
        this(terrain, WaterTopology.forTerrain(terrain), policy);
    }

    SavannahThirstSystem(TerrainMap terrain, WaterTopology topology,
                         HydrationPolicy policy)
    {
        if(terrain == null || topology == null || !topology.matches(terrain)) {
            throw new IllegalArgumentException("matching terrain water topology is required");
        }
        this.terrain = terrain;
        this.topology = topology;
        this.policy = policy == null ? HydrationPolicy.w4Default() : policy;
        targetPlanner = new WaterTargetPlanner(topology);
    }

    public void applyThirst(SavannahAnimal animal, SimulationContext context)
    {
        if(animal == null || !animal.isAlive()) {
            return;
        }

        double drain = getEffectiveDrain(animal, context);
        animal.drainHydration(drain);
        if(animal.getHydrationPercent() <= policy.getCriticalReserve()) {
            animal.spendStamina(policy.getCriticalStaminaCost());
        }
        if(animal.getHydrationPercent() <= 0.0) {
            animal.addDehydrationStep();
            if(animal.getDehydrationSteps() >=
               policy.getDehydrationGraceSteps()) {
                if(isDrinkableShore(animal.getLocation())) {
                    tryDrink(animal, context);
                }
                else {
                    context.recordDeath(animal, DeathCause.DEHYDRATION);
                }
            }
        }
    }

    public double getEffectiveDrain(SavannahAnimal animal,
                                    SimulationContext context)
    {
        double drain = policy.baseDrainFor(animal);
        WeatherType weather = context.getWeatherSystem().getCurrentWeather();
        if(weather == WeatherType.DROUGHT) {
            drain *= policy.getDroughtDrainMultiplier();
        }
        else if(weather == WeatherType.RAIN) {
            drain *= policy.getRainDrainMultiplier();
        }
        if(animal.getProfile().isPredator()) {
            drain *= policy.getPredatorDrainMultiplier();
        }
        return drain;
    }

    public boolean needsWater(SavannahAnimal animal)
    {
        return animal != null && assess(animal, null).isWaterPriority();
    }

    public HydrationAssessment assess(SavannahAnimal animal,
                                      SimulationContext context)
    {
        if(animal == null) {
            return new HydrationAssessment(0.0, Integer.MAX_VALUE, 0.0, 0.0);
        }
        SimulationContext safeContext = context;
        double drain = safeContext == null ? policy.baseDrainFor(animal) :
            getEffectiveDrain(animal, safeContext);
        String target = animal.getWaterTargetPoolId();
        int distance = WaterSourceIndex.NONE.equals(target)
            ? distanceToShore(animal.getLocation())
            : distanceToTargetPool(animal.getLocation(), target);
        return new HydrationAssessment(animal.getHydrationPercent(), distance,
            drain, policy.getSafetyMargin(), target, policy.getCriticalReserve(),
            policy.getCriticalHydrationThreshold(), policy.getThirstyHydrationThreshold());
    }

    public WaterTargetDecision selectWaterTarget(SavannahAnimal animal,
                                                  SimulationContext context)
    {
        WaterTargetDecision decision = decideWaterTarget(animal, context);
        if(animal != null) {
            animal.setWaterTarget(decision.getPoolId(),
                                  decision.hasArrivalDeficit());
        }
        return decision;
    }

    public WaterTargetDecision decideWaterTarget(SavannahAnimal animal,
                                                  SimulationContext context)
    {
        if(animal == null) {
            return new WaterTargetDecision(WaterSourceIndex.NONE,
                Integer.MAX_VALUE, true, false);
        }
        String locked = animal.getWaterTargetPoolId();
        int lockedDistance = distanceToTargetPool(animal.getLocation(), locked);
        if(!WaterSourceIndex.NONE.equals(locked) &&
           lockedDistance != Integer.MAX_VALUE) {
            return new WaterTargetDecision(locked, lockedDistance,
                animal.hasWaterArrivalDeficit(), false);
        }
        double drain = getEffectiveDrain(animal, context);
        String selected = WaterSourceIndex.NONE;
        int selectedDistance = Integer.MAX_VALUE;
        boolean safeSelected = false;
        long tie = Long.MAX_VALUE;
        double selectedScore = Double.POSITIVE_INFINITY;
        int step = context == null ? 0 : context.getStep();
        boolean alternative = false;
        for(String poolId : targetPlanner.getSourceIndex().getPoolIds()) {
            if(!animal.isPoolExcluded(poolId, step) &&
               distanceToTargetPool(animal.getLocation(), poolId) != Integer.MAX_VALUE) {
                alternative = true;
            }
        }
        for(String poolId : targetPlanner.getSourceIndex().getPoolIds()) {
            int distance = distanceToTargetPool(animal.getLocation(), poolId);
            if(distance == Integer.MAX_VALUE) continue;
            if(alternative && animal.isPoolExcluded(poolId, step)) continue;
            boolean safe = animal.getHydrationPercent() >=
                distance * drain + policy.getSafetyMargin();
            long candidateTie = stableTie(animal.getId(), poolId);
            WaterholeCongestionAssessment congestion = context == null ? null
                : context.getWaterholeCongestionAssessment();
            double load = congestion == null ? 0.0 : congestion.getNormalizedLoad(poolId);
            double score = distance + load * 10.0;
            if(selected.equals(WaterSourceIndex.NONE) ||
               (safe && !safeSelected) ||
               (safe == safeSelected && (animal.getHydrationPercent() <=
                policy.getCriticalReserve() ? distance < selectedDistance :
                score < selectedScore || (score == selectedScore &&
                candidateTie < tie)))) {
                selected = poolId;
                selectedDistance = distance;
                safeSelected = safe;
                tie = candidateTie;
                selectedScore = score;
            }
        }
        boolean deficit = selectedDistance == Integer.MAX_VALUE || !safeSelected;
        boolean retargeted = !WaterSourceIndex.NONE.equals(locked) &&
            !locked.equals(selected);
        return new WaterTargetDecision(selected, selectedDistance, deficit,
                                       retargeted);
    }

    public boolean tryDrink(SavannahAnimal animal, SimulationContext context)
    {
        Location location = animal == null ? null : animal.getLocation();
        if(!isDrinkableShore(location)) {
            return false;
        }
        String poolId = targetPlanner.getSourceIndex().shorePool(location);
        // Plan v3.0 M2-2: drinking is a finite service on the occupied shore
        // slot; the session continues until hydrated, then departure starts.
        EcologyParameters parameters = EcologyParameters.current();
        animal.drinkServing(parameters.get(EcologyParameters.Key.DRINK_PERCENT_PER_STEP));
        animal.resetWaterWait();
        animal.spendStamina(SavannahAnimal.STAMINA_GRAZE_COST);
        context.recordEvent(SimulationEvent.drink(context.getStep(), animal,
                                                  location));
        if(animal.getHydrationPercent() >=
           parameters.get(EcologyParameters.Key.DRINK_COMPLETE_PERCENT)) {
            animal.clearWaterTarget();
            animal.beginWaterDeparture(poolId);
        }
        return true;
    }

    public Location stepTowardWater(SavannahAnimal animal,
                                    List<Location> freeLocations)
    {
        return stepTowardWater(animal, freeLocations, 0);
    }

    public Location stepTowardWater(SavannahAnimal animal,
                                    List<Location> freeLocations, int step)
    {
        if(freeLocations == null || freeLocations.isEmpty()) {
            return null;
        }

        Location best = null;
        String target = animal == null ? WaterSourceIndex.NONE
            : animal.getWaterTargetPoolId();
        Location origin = animal == null ? null : animal.getLocation();
        int currentDistance = WaterSourceIndex.NONE.equals(target)
            ? distanceToShore(origin) : distanceToTargetPool(origin, target);
        int bestDistance = Integer.MAX_VALUE;
        long bestTie = Long.MAX_VALUE;
        for(Location location : freeLocations) {
            int distance = WaterSourceIndex.NONE.equals(target)
                ? distanceToShore(location) : distanceToTargetPool(location, target);
            if(distance < currentDistance &&
               (distance < bestDistance || (distance == bestDistance &&
                candidateTie(animal == null ? -1L : animal.getId(), step, location) <
                bestTie))) {
                best = location;
                bestDistance = distance;
                bestTie = candidateTie(animal == null ? -1L : animal.getId(), step,
                                       location);
            }
        }
        return best;
    }

    public boolean isDrinkableShore(Location location)
    {
        return location != null &&
               terrain.isPassable(location) &&
               distanceToShore(location) == 0;
    }

    public int distanceToShore(Location location)
    {
        if(location == null ||
           location.row() < 0 || location.row() >= terrain.getDepth() ||
           location.col() < 0 || location.col() >= terrain.getWidth()) {
            return Integer.MAX_VALUE;
        }
        return topology.distanceToShore(location);
    }

    public int distanceToTargetPool(Location location, String poolId)
    {
        return targetPlanner.distanceToPool(location, poolId);
    }

    public int countDrinkableShores()
    {
        return topology.countDrinkableShores();
    }

    public int maximumReachableShoreDistance()
    {
        return topology.maximumReachableShoreDistance();
    }

    private long stableTie(long animalId, String poolId)
    {
        long value = animalId * 0x9E3779B97F4A7C15L;
        for(int index = 0; index < poolId.length(); index++) {
            value ^= poolId.charAt(index) * 0xC2B2AE3D27D4EB4FL;
            value = Long.rotateLeft(value, 13);
        }
        return value & Long.MAX_VALUE;
    }

    private long candidateTie(long animalId, int step, Location location)
    {
        long value = animalId * 0x9E3779B97F4A7C15L + step;
        value ^= ((long)location.row() << 32) ^ location.col();
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return value & Long.MAX_VALUE;
    }

}
