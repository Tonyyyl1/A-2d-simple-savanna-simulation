package savanna;

/** Exact immutable result of the existing legacy breeding decision path. */
public final class LegacyBreedingAssessment
{
    private final boolean canBreed;
    private final boolean waterSuppressed;
    private final int actualDecisionCandidateCount;
    private final boolean mateQueryPerformed;
    private final boolean mateEligible;
    private final double baseProbability;
    private final double infectionModifier;
    private final double survivalModifier;
    private final double staminaModifier;
    private final double weatherModifier;
    private final double foodModifier;
    private final double lowPopulationModifier;
    private final double populationBalanceModifier;
    private final double crowdingModifier;
    private final double finalProbability;
    private final boolean probabilityRollPerformed;
    private final boolean probabilityRollAccepted;
    private final boolean litterDrawPerformed;
    private final int litterDrawBound;
    private final int provisionalClaimCount;
    private final BreedingRandomLedger randomLedger;

    LegacyBreedingAssessment(boolean canBreed, boolean waterSuppressed,
                             int actualDecisionCandidateCount,
                             boolean mateQueryPerformed,
                             boolean mateEligible, double baseProbability,
                             double infectionModifier,
                             double survivalModifier,
                             double staminaModifier, double weatherModifier,
                             double foodModifier,
                             double lowPopulationModifier,
                             double populationBalanceModifier,
                             double crowdingModifier,
                             double finalProbability,
                             boolean probabilityRollPerformed,
                             boolean probabilityRollAccepted,
                             boolean litterDrawPerformed,
                             int litterDrawBound,
                             int provisionalClaimCount,
                             BreedingRandomLedger randomLedger)
    {
        if(actualDecisionCandidateCount < 0 || litterDrawBound < 0 ||
           provisionalClaimCount < 0 || randomLedger == null ||
           provisionalClaimCount > actualDecisionCandidateCount ||
           litterDrawPerformed != randomLedger.hasLitterDraw() ||
           probabilityRollPerformed !=
               randomLedger.hasLegacyBreedingRoll() ||
           litterDrawBound != randomLedger.getLitterDrawBound()) {
            throw new IllegalArgumentException(
                "invalid legacy breeding assessment");
        }
        this.canBreed = canBreed;
        this.waterSuppressed = waterSuppressed;
        this.actualDecisionCandidateCount =
            actualDecisionCandidateCount;
        this.mateQueryPerformed = mateQueryPerformed;
        this.mateEligible = mateEligible;
        this.baseProbability = baseProbability;
        this.infectionModifier = infectionModifier;
        this.survivalModifier = survivalModifier;
        this.staminaModifier = staminaModifier;
        this.weatherModifier = weatherModifier;
        this.foodModifier = foodModifier;
        this.lowPopulationModifier = lowPopulationModifier;
        this.populationBalanceModifier = populationBalanceModifier;
        this.crowdingModifier = crowdingModifier;
        this.finalProbability = finalProbability;
        this.probabilityRollPerformed = probabilityRollPerformed;
        this.probabilityRollAccepted = probabilityRollAccepted;
        this.litterDrawPerformed = litterDrawPerformed;
        this.litterDrawBound = litterDrawBound;
        this.provisionalClaimCount = provisionalClaimCount;
        this.randomLedger = randomLedger;
    }

    public boolean canBreed() { return canBreed; }

    public boolean isWaterSuppressed() { return waterSuppressed; }

    public int getActualDecisionCandidateCount()
    {
        return actualDecisionCandidateCount;
    }

    public boolean isMateQueryPerformed() { return mateQueryPerformed; }

    public boolean isMateEligible() { return mateEligible; }

    public double getBaseProbability() { return baseProbability; }

    public double getInfectionModifier() { return infectionModifier; }

    public double getSurvivalModifier() { return survivalModifier; }

    public double getStaminaModifier() { return staminaModifier; }

    public double getWeatherModifier() { return weatherModifier; }

    public double getFoodModifier() { return foodModifier; }

    public double getLowPopulationModifier()
    {
        return lowPopulationModifier;
    }

    public double getPopulationBalanceModifier()
    {
        return populationBalanceModifier;
    }

    public double getCrowdingModifier() { return crowdingModifier; }

    public double getFinalProbability() { return finalProbability; }

    public boolean isProbabilityRollPerformed()
    {
        return probabilityRollPerformed;
    }

    public boolean isProbabilityRollAccepted()
    {
        return probabilityRollAccepted;
    }

    public boolean isLitterDrawPerformed() { return litterDrawPerformed; }

    public int getLitterDrawBound() { return litterDrawBound; }

    public int getProvisionalClaimCount() { return provisionalClaimCount; }

    public BreedingRandomLedger getRandomLedger() { return randomLedger; }
}
