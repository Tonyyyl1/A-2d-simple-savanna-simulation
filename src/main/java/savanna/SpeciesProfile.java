package savanna;

import java.awt.Color;

/**
 * Read-only parameters describing a species.
 * The behavioural systems use this interface instead of hard-coding species.
 */
public interface SpeciesProfile
{
    String getName();

    /**
     * Habitat score of this species on a terrain (see {@link HabitatPreferences}).
     * Implementations may precompute it; the default computes it from the name.
     */
    default double habitatSpawnModifier(TerrainType terrain)
    {
        return HabitatPreferences.computeSpawnModifier(getName(), terrain);
    }

    Color getColor();

    boolean isPredator();

    boolean canEat(String speciesName);

    boolean isActiveDuring(DayPhase phase);

    double getCreationProbability();

    int getFoundingPopulation();

    int getCubAgeLimit();

    int getAdultAge();

    int getBreedingAge();

    int getMaxAge();

    double getBreedingProbability();

    int getMaxLitterSize();

    int getBreedingRange();

    int getSearchRange();

    int getMaxFoodLevel();

    int getFoodValue();

    double getHuntingSkill();

    double getDiseaseResistance();

    double getYoungDiseasePenalty();

    double getPredationVulnerability();

    double getGrazingRate();

    int getPlantFoodValue();
}
