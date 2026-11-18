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

    public void tryBreed(SavannahAnimal parent, Field currentField, Field nextFieldState,
                         SimulationContext context, List<Location> freeLocations)
    {
        if(parent.getSex() != Sex.FEMALE || !parent.canBreed() || freeLocations.isEmpty()) {
            return;
        }
        if(!hasMateNearby(parent, currentField, context)) {
            return;
        }

        double probability = breedingProbability(parent, currentField, context);

        if(rand.nextDouble() <= probability) {
            int births = rand.nextInt(parent.getProfile().getMaxLitterSize()) + 1;
            int spawned = 0;
            for(int index = 0; index < births && !freeLocations.isEmpty(); index++) {
                Location birthLocation = freeLocations.remove(0);
                SavannahAnimal child = parent.createChild(birthLocation);
                FieldTransitionResult result = context
                    .getFieldTransition(nextFieldState)
                    .trySpawn(child, birthLocation);
                if(result.isSuccessful()) {
                    spawned++;
                    context.recordEvent(SimulationEvent.birth(context.getStep(),
                                                              parent, child,
                                                              birthLocation));
                }
            }
            if(spawned > 0) {
                parent.spendBreedingStamina();
            }
        }
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
        DecisionBirthCandidateFact candidateFact = null;
        if(parent != null && freeLocations != null && context != null &&
           context.isPf1aFactCaptureEnabled()) {
            candidateFact = DecisionBirthCandidateFact.capture(
                context.getStep(), parent, freeLocations);
        }
        return propose(parent, geometry, context, freeLocations,
            candidateFact);
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
           parent.getSex() != Sex.FEMALE || !parent.canBreed()) {
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
        int births = rand.nextInt(litterBound) + 1;
        int actualCandidateCount = freeLocations.size();
        List<PositionClaim> claims = new ArrayList<>();
        for(int index = 0; index < births && !freeLocations.isEmpty(); index++) {
            Location birthLocation = freeLocations.remove(0);
            // The token retains only the already-visible local alternatives.
            // It does not search beyond the breeding decision's candidate
            // view, create a child, or reserve a fixed cell before resolve.
            List<Location> fallbacks = new ArrayList<>(freeLocations);
            claims.add(PositionClaim.spawn(parent.getId(), index, birthLocation,
                fallbacks, birthLocation, 0, 0));
        }
        LegacyBreedingAssessment assessment = factors.toAssessment(true,
            false, actualCandidateCount, mateQuery, true, true, true,
            litterBound, claims.size());
        recordAssessment(parent, context, candidateFact, assessment);
        return proposal(parent, context, resourceClock, frameId,
            StepFrameLedger.ProposalReason.PROVISIONAL_CLAIMS, claims,
            assessment, before);
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
        if(!context.isPf1aFactCaptureEnabled()) {
            return;
        }
        DecisionBirthCandidateFact actualCandidateFact = candidateFact;
        if(actualCandidateFact == null) {
            throw new IllegalStateException(
                "PF1-aF capture requires the exact SOC1-aF candidate fact");
        }
        context.recordLegacyBreedingAssessment(parent, actualCandidateFact,
            assessment);
    }

    private ProbabilityFactors breedingProbabilityFactors(
        SavannahAnimal parent, InteractionGeometry geometry,
        SimulationContext context)
    {
        double base = context.getBreedingProbability(parent.getProfile());
        double probability = base;
        double infection = parent.isInfected() ? 0.55 : 1.0;
        probability *= infection;
        double survival = Math.max(0.10,
            1.0 - parent.getSurvivalPressure() * 0.82);
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
        double food = !parent.getProfile().isPredator()
            ? plantFoodModifier(context)
            : predatorFoodModifier(parent, context);
        probability *= food;
        double lowPopulation = lowPopulationRecoveryModifier(parent, context);
        probability *= lowPopulation;
        double populationBalance = populationBalanceModifier(parent, context);
        probability *= populationBalance;
        double crowding = crowdingModifier(geometry);
        return new ProbabilityFactors(base, infection, survival, stamina,
            weather, food, lowPopulation, populationBalance, crowding,
            probability * crowding);
    }

    /** Legacy path retains the old Field read contract unchanged. */
    private double breedingProbability(SavannahAnimal parent, Field currentField,
                                       SimulationContext context)
    {
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
        return probability * crowdingModifier(currentField);
    }

    private MateQueryResult findMateNearby(SavannahAnimal parent,
                                           InteractionGeometry geometry,
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
            geometry.getLocationsWithin(parent.getLocation(), range,
                                        context.getRandom());
        boolean eligible = false;
        for(Location location : locations) {
            Animal animal = geometry.getAnimalAt(location);
            if(animal instanceof SavannahAnimal) {
                SavannahAnimal possibleMate = (SavannahAnimal)animal;
                if(possibleMate.isAlive() &&
                   possibleMate.getSex() == Sex.MALE &&
                   possibleMate.isAdult() &&
                   possibleMate.getProfile().getName().equals(parent.getProfile().getName())) {
                    eligible = true;
                    break;
                }
            }
        }
        return new MateQueryResult(true, locations.size(), eligible);
    }

    /** Legacy path retains the old Field lookup contract unchanged. */
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
        List<Location> locations = currentField.getLocationsWithin(
            parent.getLocation(), range, context.getRandom());
        for(Location location : locations) {
            Animal animal = currentField.getAnimalAt(location);
            if(animal instanceof SavannahAnimal) {
                SavannahAnimal possibleMate = (SavannahAnimal)animal;
                if(possibleMate.isAlive() && possibleMate.getSex() == Sex.MALE &&
                   possibleMate.isAdult() && possibleMate.getProfile().getName()
                       .equals(parent.getProfile().getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private double crowdingModifier(InteractionGeometry geometry)
    {
        double density = (double)geometry.getLivingPopulation() /
            (geometry.getDepth() * geometry.getWidth());
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

    private double crowdingModifier(Field field)
    {
        double density = (double)field.getLivingPopulation() /
            (field.getDepth() * field.getWidth());
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
