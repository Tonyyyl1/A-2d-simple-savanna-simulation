package savanna;

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
    /** Habitat score per terrain, by TerrainType.ordinal(); fixed because the name is. */
    private final double[] habitatByTerrain;
    /** v3.2 A-4: when set, five life-history getters read {@link EcologyParameters}. */
    private EcologyParameters.Key[] card;

    /**
     * Binds breeding probability, litter size, food value, grazing rate and
     * plant food value to parameter keys (in that order). Called once, while
     * the registry builds its profiles.
     */
    BasicSpeciesProfile withParameterCard(EcologyParameters.Key breeding,
        EcologyParameters.Key litter, EcologyParameters.Key food,
        EcologyParameters.Key grazing, EcologyParameters.Key plant)
    {
        card = new EcologyParameters.Key[] {breeding, litter, food, grazing, plant};
        return this;
    }

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
        TerrainType[] terrains = TerrainType.values();
        habitatByTerrain = new double[terrains.length];
        for(TerrainType terrain : terrains) {
            habitatByTerrain[terrain.ordinal()] =
                HabitatPreferences.computeSpawnModifier(name, terrain);
        }
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

    @Override
    public double habitatSpawnModifier(TerrainType terrain)
    {
        return terrain == null ? HabitatPreferences.computeSpawnModifier(name, null)
                               : habitatByTerrain[terrain.ordinal()];
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
        return card == null ? breedingProbability :
            EcologyParameters.current().get(card[0]);
    }

    public int getMaxLitterSize()
    {
        return card == null ? maxLitterSize :
            EcologyParameters.current().getInt(card[1]);
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
        return card == null ? foodValue :
            EcologyParameters.current().getInt(card[2]);
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
        return card == null ? grazingRate :
            EcologyParameters.current().get(card[3]);
    }

    public int getPlantFoodValue()
    {
        return card == null ? plantFoodValue :
            EcologyParameters.current().getInt(card[4]);
    }
}
