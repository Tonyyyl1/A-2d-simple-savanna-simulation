package savanna;

/** Immutable PF1-aF record for one fully completed successful HUNT chain. */
public final class RealizedKillRecord
{
    private final int step;
    private final long predatorId;
    private final long preyId;
    private final String predatorSpecies;
    private final String preySpecies;
    private final int offeredFood;
    private final int acceptedFood;

    RealizedKillRecord(int step, long predatorId, long preyId,
                       String predatorSpecies, String preySpecies,
                       int offeredFood, int acceptedFood)
    {
        if(step < 0 || predatorId <= 0L || preyId <= 0L ||
           predatorSpecies == null || predatorSpecies.isBlank() ||
           preySpecies == null || preySpecies.isBlank() ||
           offeredFood < 0 || acceptedFood < 0 ||
           acceptedFood > offeredFood) {
            throw new IllegalArgumentException(
                "complete realized-kill facts are required");
        }
        this.step = step;
        this.predatorId = predatorId;
        this.preyId = preyId;
        this.predatorSpecies = predatorSpecies;
        this.preySpecies = preySpecies;
        this.offeredFood = offeredFood;
        this.acceptedFood = acceptedFood;
    }

    public static RealizedKillRecord capture(int step,
                                             SavannahAnimal predator,
                                             SavannahAnimal prey,
                                             int preFeedFood,
                                             int postFeedFood,
                                             HuntResult result)
    {
        if(predator == null || prey == null || result == null ||
           result.getOutcome() != HuntOutcome.SUCCESS ||
           !predator.getProfile().isPredator() ||
           !HuntLocalOpportunity.canHunt(predator.getProfile(), prey, 1) ||
           !prey.getProfile().getName().equals(
               result.getSelectedPreySpecies())) {
            throw new IllegalArgumentException(
                "a complete successful HUNT result is required");
        }
        int offered = prey.getProfile().getFoodValue();
        int accepted = postFeedFood - preFeedFood;
        if(postFeedFood < preFeedFood ||
           postFeedFood > predator.getProfile().getMaxFoodLevel()) {
            throw new IllegalStateException(
                "realized accepted food does not match clamped feed state");
        }
        return new RealizedKillRecord(step, predator.getId(), prey.getId(),
            predator.getProfile().getName(), prey.getProfile().getName(),
            offered, accepted);
    }

    public int getStep() { return step; }

    public long getPredatorId() { return predatorId; }

    public long getPreyId() { return preyId; }

    public String getPredatorSpecies() { return predatorSpecies; }

    public String getPreySpecies() { return preySpecies; }

    public int getOfferedFood() { return offeredFood; }

    public int getAcceptedFood() { return acceptedFood; }

    String uniqueKey()
    {
        return step + ":" + predatorId + ":" + preyId;
    }
}
