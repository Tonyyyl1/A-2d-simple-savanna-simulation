package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable SOC1-aF projection of existing SpeciesProfile values only. */
public final class SpeciesEcologyFactProfile
{
    private final String speciesKey;
    private final boolean predator;
    private final SpeciesReproductionAdapter reproductionAdapter;
    private final List<String> edibleSpeciesSorted;
    private final int breedingRange;
    private final int searchRange;
    private final int maxFoodLevel;
    private final int foodValue;
    private final int breedingAge;
    private final int adultAge;
    private final int maxLitterSize;
    private final int foundingPopulation;

    SpeciesEcologyFactProfile(SpeciesProfile profile,
                              List<String> edibleSpecies)
    {
        if(profile == null || profile.getName() == null ||
           profile.getName().isBlank() || edibleSpecies == null) {
            throw new IllegalArgumentException(
                "complete species fact inputs are required");
        }
        speciesKey = profile.getName();
        predator = profile.isPredator();
        reproductionAdapter = predator
            ? SpeciesReproductionAdapter.PREDATOR_FACT_ONLY
            : SpeciesReproductionAdapter.LEGACY_GLOBAL;
        List<String> diet = new ArrayList<>(edibleSpecies);
        Collections.sort(diet);
        edibleSpeciesSorted = Collections.unmodifiableList(diet);
        breedingRange = profile.getBreedingRange();
        searchRange = profile.getSearchRange();
        maxFoodLevel = profile.getMaxFoodLevel();
        foodValue = profile.getFoodValue();
        breedingAge = profile.getBreedingAge();
        adultAge = profile.getAdultAge();
        maxLitterSize = profile.getMaxLitterSize();
        foundingPopulation = profile.getFoundingPopulation();
    }

    public String getSpeciesKey() { return speciesKey; }

    public String getDietKey() { return speciesKey; }

    public boolean isPredator() { return predator; }

    public SpeciesReproductionAdapter getReproductionAdapter()
    {
        return reproductionAdapter;
    }

    public List<String> getEdibleSpeciesSorted()
    {
        return edibleSpeciesSorted;
    }

    public int getBreedingRange() { return breedingRange; }

    /** Existing profile search range; weather/infection/stamina remain dynamic inputs. */
    public int getSearchRange() { return searchRange; }

    public int getMaxFoodLevel() { return maxFoodLevel; }

    public int getFoodValue() { return foodValue; }

    public int getBreedingAge() { return breedingAge; }

    public int getAdultAge() { return adultAge; }

    public int getMaxLitterSize() { return maxLitterSize; }

    public int getFoundingPopulation() { return foundingPopulation; }

    String canonicalLine()
    {
        return speciesKey + "|predator=" + predator + "|adapter=" +
            reproductionAdapter + "|diet=" +
            String.join(",", edibleSpeciesSorted) + "|breedingRange=" +
            breedingRange + "|searchRange=" + searchRange +
            "|maxFood=" + maxFoodLevel + "|foodValue=" + foodValue +
            "|breedingAge=" + breedingAge + "|adultAge=" + adultAge +
            "|maxLitter=" + maxLitterSize + "|founding=" +
            foundingPopulation;
    }
}
