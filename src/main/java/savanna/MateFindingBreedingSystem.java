package savanna;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * Produces provisional R2 spawn claims while preserving the legacy mate,
     * probability, litter-size, and candidate-consumption decisions. No child
     * object exists until a winning token is committed.
     */
    @Override
    public List<PositionClaim> propose(SavannahAnimal parent,
                                       InteractionGeometry geometry,
                                       SimulationContext context,
                                       List<Location> freeLocations)
    {
        return propose(parent, geometry, context, freeLocations, null);
    }

    @Override
    public List<PositionClaim> propose(
        SavannahAnimal parent, InteractionGeometry geometry,
        SimulationContext context, List<Location> freeLocations,
        DecisionBirthCandidateFact candidateFact)
    {
        if(parent == null || context == null) return Collections.emptyList();
        StepFrameLedger.ResourceClock clock =
            StepFrameLedger.ResourceClock.capture(context.getStep(), parent,
                StepFrameLedger.frameId(context.getStep(), parent.getId()));
        return proposeFrame(parent, geometry, context, freeLocations, clock,
            candidateFact).getPositionClaims();
    }

    @Override
    public StepFrameLedger.ReproductionProposal proposeFrame(
        SavannahAnimal parent, InteractionGeometry geometry,
        SimulationContext context, List<Location> freeLocations,
        StepFrameLedger.ResourceClock resourceClock,
        DecisionBirthCandidateFact candidateFact)
    {
        if(parent == null || context == null || resourceClock == null) {
            throw new IllegalArgumentException(
                "FRAME reproduction requires actor, context and S0 resource clock");
        }
        String frameId = StepFrameLedger.frameId(context.getStep(),
            parent.getId());
        AnimalPhysiologyFact before = AnimalPhysiologyFact.capture(parent);
        if(geometry == null || freeLocations == null ||
           parent.getSex() != Sex.FEMALE) {
            return proposal(parent, context, resourceClock, frameId,
                StepFrameLedger.ProposalReason.NOT_ELIGIBLE,
                Collections.<PositionClaim>emptyList(), null, before);
        }
        ReproductionState reproduction = parent.getReproductionState();
        int step = context.getStep();
        if(reproduction.isPregnant()) {
            return pregnancyProposal(parent, geometry, context, resourceClock,
                frameId, freeLocations, reproduction, before);
        }
        int affordable = ReproductionPolicy.affordableLitter(
            parent.getProfile(), parent.getFoodLevel());
        if(!parent.canBreed() || !reproduction.mayConceive(step) ||
           affordable < 1) {
            return proposal(parent, context, resourceClock, frameId,
                StepFrameLedger.ProposalReason.NOT_ELIGIBLE,
                Collections.<PositionClaim>emptyList(), null, before);
        }
        if(freeLocations.isEmpty()) {
            return proposal(parent, context, resourceClock, frameId,
                StepFrameLedger.ProposalReason.NO_POSITION_CANDIDATE,
                Collections.<PositionClaim>emptyList(), null, before);
        }
        MateQueryResult mateQuery = findMateNearby(parent, geometry, context);
        ProbabilityFactors factors = breedingProbabilityFactors(parent,
            geometry, context);
        if(!mateQuery.mateEligible) {
            LegacyBreedingAssessment assessment = factors.toAssessment(true,
                false, freeLocations.size(), mateQuery, false, false, false,
                0, 0);
            recordAssessment(parent, context, candidateFact, assessment);
            return proposal(parent, context, resourceClock, frameId,
                StepFrameLedger.ProposalReason.NO_MATE,
                Collections.<PositionClaim>emptyList(), assessment, before);
        }

        boolean rollAccepted = rand.nextDouble() <=
            factors.finalProbability;
        if(!rollAccepted) {
            LegacyBreedingAssessment assessment = factors.toAssessment(true,
                false, freeLocations.size(), mateQuery, true, false, false,
                0, 0);
            recordAssessment(parent, context, candidateFact, assessment);
            return proposal(parent, context, resourceClock, frameId,
                StepFrameLedger.ProposalReason.PROBABILITY_REJECTED,
                Collections.<PositionClaim>emptyList(), assessment, before);
        }
        int litterBound = parent.getProfile().getMaxLitterSize();
        // The litter is limited by what the mother can pay now (D4).
        int litter = Math.min(rand.nextInt(litterBound) + 1, affordable);
        // A conception owns no position claims yet; birth claims come when due.
        LegacyBreedingAssessment assessment = factors.toAssessment(true,
            false, freeLocations.size(), mateQuery, true, true, true,
            litterBound, 0);
        recordAssessment(parent, context, candidateFact, assessment);
        return StepFrameLedger.ReproductionProposal.conception(frameId,
            step, parent, resourceClock, litter, assessment, before,
            AnimalPhysiologyFact.capture(parent));
    }

    /**
     * A pregnant female proposes nothing until due; once due, each pending
     * ordinal claims one visible free cell. Uses no random numbers.
     */
    private StepFrameLedger.ReproductionProposal pregnancyProposal(
        SavannahAnimal parent, InteractionGeometry geometry,
        SimulationContext context,
        StepFrameLedger.ResourceClock resourceClock, String frameId,
        List<Location> freeLocations, ReproductionState reproduction,
        AnimalPhysiologyFact before)
    {
        if(!reproduction.isDue(context.getStep())) {
            return proposal(parent, context, resourceClock, frameId,
                StepFrameLedger.ProposalReason.PREGNANT,
                Collections.<PositionClaim>emptyList(), null, before);
        }
        // Prefer cells that are empty at step start; occupied candidates are
        // only conditionally vacatable. Stable, so no random draw is needed.
        List<Location> ordered = new ArrayList<>(freeLocations);
        ordered.sort((first, second) -> Boolean.compare(
            geometry.getAnimalAt(first) != null,
            geometry.getAnimalAt(second) != null));
        freeLocations.clear();
        freeLocations.addAll(ordered);
        List<PositionClaim> claims = new ArrayList<>();
        for(Integer ordinal : reproduction.getPendingOrdinals()) {
            if(freeLocations.isEmpty()) break;
            Location birthLocation = freeLocations.remove(0);
            List<Location> fallbacks = new ArrayList<>(freeLocations);
            claims.add(PositionClaim.spawn(parent.getId(), ordinal.intValue(),
                birthLocation, fallbacks, birthLocation, 0, 0));
        }
        return proposal(parent, context, resourceClock, frameId,
            claims.isEmpty() ? StepFrameLedger.ProposalReason.NO_POSITION_CANDIDATE
                             : StepFrameLedger.ProposalReason.PROVISIONAL_CLAIMS,
            claims, null, before);
    }

    private StepFrameLedger.ReproductionProposal proposal(
        SavannahAnimal parent, SimulationContext context,
        StepFrameLedger.ResourceClock resourceClock, String frameId,
        StepFrameLedger.ProposalReason reason, List<PositionClaim> claims,
        LegacyBreedingAssessment assessment, AnimalPhysiologyFact before)
    {
        return StepFrameLedger.ReproductionProposal.create(frameId,
            context.getStep(), parent, resourceClock, reason, claims,
            assessment, before, AnimalPhysiologyFact.capture(parent));
    }

    private void recordAssessment(SavannahAnimal parent,
                                  SimulationContext context,
                                  DecisionBirthCandidateFact candidateFact,
                                  LegacyBreedingAssessment assessment)
    {
        context.recordBreedingRandomObservation(
            new BreedingRandomObservation(context.getStep(), parent,
                assessment.getRandomLedger()));
    }

    private ProbabilityFactors breedingProbabilityFactors(
        SavannahAnimal parent, InteractionGeometry geometry,
        SimulationContext context)
    {
        double base = context.getBreedingProbability(parent.getProfile()) *
            ReproductionPolicy.conceptionRateMultiplier(parent.getProfile());
        double probability = base;
        double infection = parent.isInfected() ? 0.55 : 1.0;
        probability *= infection;
        double survival = Math.max(0.10,
            1.0 - parent.getSurvivalPressure() * 0.82);
        double conditionMin = EcologyParameters.current()
            .get(EcologyParameters.Key.HERBIVORE_CONDITION_MIN);
        if(conditionMin > 0.0 && !parent.getProfile().isPredator()) {
            // N2: conception follows the body condition (food share), at most the old factor.
            survival = Math.min(survival, conditionFactor(
                (double)parent.getFoodLevel() / parent.getProfile().getMaxFoodLevel(),
                conditionMin, EcologyParameters.current()
                    .get(EcologyParameters.Key.HERBIVORE_CONDITION_FULL)));
        }
        probability *= survival;
        double stamina = Math.max(0.20,
            parent.getEffectiveStaminaMultiplier());
        probability *= stamina;
        double weather = 1.0;
        if(context.getWeatherSystem().getCurrentWeather() ==
           WeatherType.DROUGHT) {
            weather = 0.72;
        }
        else if(context.getWeatherSystem().getCurrentWeather() ==
                WeatherType.RAIN && !parent.getProfile().isPredator()) {
            weather = 1.12;
        }
        probability *= weather;
        // Plan v3.0 M1-2: no global food or low-population bonuses. Predators
        // are limited by the reserve they can pay; herbivores read the grass
        // around them; crowding is the local occupancy, not map density.
        double food = !parent.getProfile().isPredator()
            ? localPlantFoodModifier(parent, context) : 1.0;
        probability *= food;
        double lowPopulation = 1.0;
        // Plan v3.0 M1-2: the global population target no longer steers
        // births; M2 local carrying capacity takes over that role.
        double populationBalance = 1.0;
        double crowding = localCrowdingModifier(parent, context);
        return new ProbabilityFactors(base, infection, survival, stamina,
            weather, food, lowPopulation, populationBalance, crowding,
            probability * crowding);
    }

    private MateQueryResult findMateNearby(SavannahAnimal parent,
                                           InteractionGeometry geometry,
                                           SimulationContext context)
    {
        int range = parent.getProfile().getBreedingRange();
        List<Location> locations =
            geometry.getLocationsWithin(parent.getLocation(), range,
                                        context.getRandom());
        boolean eligible = false;
        TerrainNavigator.Reachability reachable = null;
        for(Location location : locations) {
            Animal animal = geometry.getAnimalAt(location);
            if(animal instanceof SavannahAnimal) {
                SavannahAnimal possibleMate = (SavannahAnimal)animal;
                if(possibleMate.isAlive() &&
                   possibleMate.getSex() == Sex.MALE &&
                   possibleMate.isAdult() &&
                   possibleMate.getProfile().getName().equals(parent.getProfile().getName())) {
                    // Mates must be reachable over land within the range (G02).
                    if(reachable == null) {
                        reachable = TerrainNavigator.reachableDistances(
                            context.getTerrainMap(), parent.getLocation(), range);
                    }
                    int distance = reachable.distanceTo(location);
                    if(distance >= 0 && distance <= range) {
                        eligible = true;
                        break;
                    }
                }
            }
        }
        return new MateQueryResult(true, locations.size(), eligible);
    }

    /** Grass fill (biomass / capacity) around the mother, on the old scale. */
    private double localPlantFoodModifier(SavannahAnimal parent,
                                          SimulationContext context)
    {
        EcologyParameters p = EcologyParameters.current();
        if(p.getInt(EcologyParameters.Key.GRASS_GATE) == 1 &&
           grassGateApplies(p.getInt(EcologyParameters.Key.GRASS_GATE_SPECIES),
               parent.getProfile().getName())) {
            double fill = context.getFoodSystem().getLocalFill(parent.getLocation(),
                p.getInt(EcologyParameters.Key.GRASS_WINDOW));
            return grassGate(fill, p.get(EcologyParameters.Key.GRASS_MIN),
                p.get(EcologyParameters.Key.GRASS_MAX));
        }
        double fill = context.getFoodSystem().getLocalFill(parent.getLocation(),
            parent.getProfile().getSearchRange());
        return Math.max(0.15, Math.min(1.12, fill * 1.55));
    }

    /**
     * N2: 0 at or below {@code min}, rising linearly to 1 at {@code full}
     * (a step at {@code min} when {@code full} is not above it).
     */
    static double conditionFactor(double foodShare, double min, double full)
    {
        if(full <= min) return foodShare > min ? 1.0 : 0.0;
        return Math.max(0.0, Math.min(1.0, (foodShare - min) / (full - min)));
    }

    /** M3 change B': whether the grass gate covers this species (0 all herbivores, 1 Gazelle only). */
    static boolean grassGateApplies(int speciesMode, String species)
    {
        return speciesMode != 1 || SpeciesRegistry.GAZELLE.equals(species);
    }

    /**
     * M3 change B1: herbivore grass factor as a gate: 0 at or below
     * {@code min}, rising linearly to 1.0 at {@code max}, never above 1.12.
     */
    static double grassGate(double fill, double min, double max)
    {
        if(max <= min) return fill > min ? 1.0 : 0.0;
        return Math.max(0.0, Math.min(1.12, (fill - min) / (max - min)));
    }

    /** Old density bands, applied to reachable cells around the mother. */
    private double localCrowdingModifier(SavannahAnimal parent,
                                         SimulationContext context)
    {
        LocalOccupancyFact occupancy = context.getSpatialEcologyView()
            .getLocalOccupancy(parent.getLocation(),
                parent.getProfile().getBreedingRange(), parent.getProfile());
        double density = occupancy.getPassableCellCount() <= 0 ? 0.0 :
            (double)occupancy.getLivingOccupantCount() /
                occupancy.getPassableCellCount();
        double band;
        if(density > 0.60) band = 0.03;
        else if(density > 0.48) band = 0.10;
        else if(density > 0.36) band = 0.30;
        else if(density > 0.25) band = 0.65;
        else if(density < 0.12) band = 1.18;
        else band = 1.0;
        return band * localOverloadModifier(parent, context);
    }

    /** Plan v3.0 M2-3: local carrying capacity replaces the global target. */
    private double localOverloadModifier(SavannahAnimal parent,
                                         SimulationContext context)
    {
        if(parent.getProfile().isPredator()) {
            return territoryModifier(parent, context);
        }
        LocalOccupancyFact window = context.getSpatialEcologyView().getLocalOccupancy(
            parent.getLocation(),
            EcologyParameters.current().getInt(EcologyParameters.Key.LOCAL_WINDOW_RADIUS),
            parent.getProfile());
        double factor = overloadFactor(
            window.getLivingOccupantCount() - window.getPredatorCount());
        EcologyParameters parameters = EcologyParameters.current();
        int speciesLimit = 0;
        String species = parent.getProfile().getName();
        if(SpeciesRegistry.GAZELLE.equals(species)) {
            speciesLimit = parameters.getInt(EcologyParameters.Key.OVERLOAD_GAZELLE);
        }
        else if(SpeciesRegistry.ZEBRA.equals(species)) {
            speciesLimit = parameters.getInt(EcologyParameters.Key.OVERLOAD_ZEBRA);
        }
        else if(SpeciesRegistry.BUFFALO.equals(species)) {
            speciesLimit = parameters.getInt(EcologyParameters.Key.OVERLOAD_BUFFALO);
        }
        if(speciesLimit > 0) {
            factor = Math.min(factor, overloadFactor(
                window.getConspecificCount(), speciesLimit));
        }
        return factor;
    }

    /**
     * M3 change A1: predator births fall with the number of conspecifics in
     * the territory window, so a predator's numbers follow area instead of
     * following the prey peak. Off (1.0, no extra query) unless the species
     * has a territory limit.
     */
    private double territoryModifier(SavannahAnimal parent, SimulationContext context)
    {
        EcologyParameters p = EcologyParameters.current();
        int limit = territoryLimit(parent.getProfile(), p);
        if(limit <= 0) return 1.0;
        int radius = p.getInt(EcologyParameters.Key.TERRITORY_RADIUS);
        int own = p.getInt(SpeciesRegistry.LION.equals(parent.getProfile().getName())
            ? EcologyParameters.Key.TERRITORY_LION_RADIUS
            : EcologyParameters.Key.TERRITORY_CHEETAH_RADIUS);
        if(own > 0) radius = own;
        LocalOccupancyFact window = context.getSpatialEcologyView().getLocalOccupancy(
            parent.getLocation(), radius, parent.getProfile());
        return overloadFactor(window.getConspecificCount(), limit);
    }

    /** A1: the territory limit of a predator species (0 = off, also for non-predators). */
    static int territoryLimit(SpeciesProfile profile, EcologyParameters parameters)
    {
        if(SpeciesRegistry.LION.equals(profile.getName())) {
            return parameters.getInt(EcologyParameters.Key.TERRITORY_LION);
        }
        if(SpeciesRegistry.CHEETAH.equals(profile.getName())) {
            return parameters.getInt(EcologyParameters.Key.TERRITORY_CHEETAH);
        }
        return 0;
    }

    /**
     * Birth multiplier for a local window holding {@code prey} prey animals:
     * {@code max(floor, (limit / prey)^exponent)} above the shared prey
     * overload count (the same limit that adds overload energy use).
     */
    static double overloadFactor(int prey)
    {
        return overloadFactor(prey, EcologyParameters.current().getInt(
            EcologyParameters.Key.PREY_OVERLOAD_COUNT));
    }

    /** {@link #overloadFactor(int)} against an explicit limit (M3 change H1). */
    static double overloadFactor(int prey, int limit)
    {
        EcologyParameters p = EcologyParameters.current();
        if(prey <= limit) return 1.0;
        double ratio = (double)limit / prey;
        double exponent = p.get(EcologyParameters.Key.OVERLOAD_EXPONENT);
        double factor = exponent == 2.0 ? ratio * ratio : Math.pow(ratio, exponent);
        return Math.max(p.get(EcologyParameters.Key.OVERLOAD_FLOOR), factor);
    }

    private static final class MateQueryResult
    {
        private final boolean performed;
        private final int shuffleInputSize;
        private final boolean mateEligible;

        MateQueryResult(boolean performed, int shuffleInputSize,
                        boolean mateEligible)
        {
            this.performed = performed;
            this.shuffleInputSize = shuffleInputSize;
            this.mateEligible = mateEligible;
        }
    }

    private static final class ProbabilityFactors
    {
        private final double base;
        private final double infection;
        private final double survival;
        private final double stamina;
        private final double weather;
        private final double food;
        private final double lowPopulation;
        private final double populationBalance;
        private final double crowding;
        private final double finalProbability;

        ProbabilityFactors(double base, double infection, double survival,
                           double stamina, double weather, double food,
                           double lowPopulation, double populationBalance,
                           double crowding, double finalProbability)
        {
            this.base = base;
            this.infection = infection;
            this.survival = survival;
            this.stamina = stamina;
            this.weather = weather;
            this.food = food;
            this.lowPopulation = lowPopulation;
            this.populationBalance = populationBalance;
            this.crowding = crowding;
            this.finalProbability = finalProbability;
        }

        LegacyBreedingAssessment toAssessment(
            boolean canBreed, boolean waterSuppressed, int candidateCount,
            MateQueryResult mateQuery, boolean rollPerformed,
            boolean rollAccepted, boolean litterDrawPerformed,
            int litterDrawBound, int provisionalClaims)
        {
            BreedingRandomLedger randomLedger = new BreedingRandomLedger(
                mateQuery.performed, mateQuery.shuffleInputSize,
                rollPerformed, litterDrawPerformed, litterDrawBound);
            return new LegacyBreedingAssessment(canBreed, waterSuppressed,
                candidateCount, mateQuery.performed,
                mateQuery.mateEligible, base, infection, survival, stamina,
                weather, food, lowPopulation, populationBalance, crowding,
                finalProbability, rollPerformed, rollAccepted,
                litterDrawPerformed, litterDrawBound, provisionalClaims,
                randomLedger);
        }
    }
}
