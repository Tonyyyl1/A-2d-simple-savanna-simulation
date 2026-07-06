import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

/**
 * Immutable implementation of SpeciesProfile.
 */
public class BasicSpeciesProfile implements SpeciesProfile
{
    private final String name;
    private final Color color;
    private final boolean predator;
    private final Set<String> preyNames;
    private final Set<DayPhase> activePhases;
    private final double creationProbability;
    private final int foundingPopulation;
    private final int cubAgeLimit;
    private final int adultAge;
    private final int breedingAge;
    private final int maxAge;
    private final double breedingProbability;
    private final int maxLitterSize;
    private final int breedingRange;
    private final int searchRange;
    private final int maxFoodLevel;
    private final int foodValue;
    private final double huntingSkill;
    private final double diseaseResistance;
    private final double youngDiseasePenalty;
    private final double predationVulnerability;
    private final double grazingRate;
    private final int plantFoodValue;

    public BasicSpeciesProfile(String name, Color color, boolean predator,
                               Set<String> preyNames, Set<DayPhase> activePhases,
                               double creationProbability, int foundingPopulation,
                               int cubAgeLimit, int adultAge, int breedingAge, int maxAge,
                               double breedingProbability, int maxLitterSize,
                               int breedingRange, int searchRange, int maxFoodLevel,
                               int foodValue, double huntingSkill,
                               double diseaseResistance, double youngDiseasePenalty,
                               double predationVulnerability, double grazingRate,
                               int plantFoodValue)
    {
        this.name = name;
        this.color = color;
        this.predator = predator;
        this.preyNames = new HashSet<>(preyNames);
        this.activePhases = new HashSet<>(activePhases);
        this.creationProbability = creationProbability;
        this.foundingPopulation = foundingPopulation;
        this.cubAgeLimit = cubAgeLimit;
        this.adultAge = adultAge;
        this.breedingAge = breedingAge;
        this.maxAge = maxAge;
        this.breedingProbability = breedingProbability;
        this.maxLitterSize = maxLitterSize;
        this.breedingRange = breedingRange;
        this.searchRange = searchRange;
        this.maxFoodLevel = maxFoodLevel;
        this.foodValue = foodValue;
        this.huntingSkill = huntingSkill;
        this.diseaseResistance = diseaseResistance;
        this.youngDiseasePenalty = youngDiseasePenalty;
        this.predationVulnerability = predationVulnerability;
        this.grazingRate = grazingRate;
        this.plantFoodValue = plantFoodValue;
    }

    public String getName()
    {
        return name;
    }

    public Color getColor()
    {
        return color;
    }

    public boolean isPredator()
    {
        return predator;
    }

    public boolean canEat(String speciesName)
    {
        return preyNames.contains(speciesName);
    }

    public boolean isActiveDuring(DayPhase phase)
    {
        return activePhases.contains(phase);
    }

    public double getCreationProbability()
    {
        return creationProbability;
    }

    public int getFoundingPopulation()
    {
        return foundingPopulation;
    }

    public int getCubAgeLimit()
    {
        return cubAgeLimit;
    }

    public int getAdultAge()
    {
        return adultAge;
    }

    public int getBreedingAge()
    {
        return breedingAge;
    }

    public int getMaxAge()
    {
        return maxAge;
    }

    public double getBreedingProbability()
    {
        return breedingProbability;
    }

    public int getMaxLitterSize()
    {
        return maxLitterSize;
    }

    public int getBreedingRange()
    {
        return breedingRange;
    }

    public int getSearchRange()
    {
        return searchRange;
    }

    public int getMaxFoodLevel()
    {
        return maxFoodLevel;
    }

    public int getFoodValue()
    {
        return foodValue;
    }

    public double getHuntingSkill()
    {
        return huntingSkill;
    }

    public double getDiseaseResistance()
    {
        return diseaseResistance;
    }

    public double getYoungDiseasePenalty()
    {
        return youngDiseasePenalty;
    }

    public double getPredationVulnerability()
    {
        return predationVulnerability;
    }

    public double getGrazingRate()
    {
        return grazingRate;
    }

    public int getPlantFoodValue()
    {
        return plantFoodValue;
    }
}
