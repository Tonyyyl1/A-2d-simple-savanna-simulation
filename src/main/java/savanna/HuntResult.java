package savanna;

/** Immutable typed result with the legacy HUNT destination as an adapter. */
public final class HuntResult
{
    private final HuntOutcome outcome;
    private final Location destination;
    private final String selectedPreySpecies;
    private final int huntingRange;
    private final int localReachableEdibleCount;
    private final double staminaCost;
    private final HuntRandomLedger randomLedger;
    private final HuntOpportunityFact opportunityFact;

    public HuntResult(HuntOutcome outcome, Location destination,
                      String selectedPreySpecies, int huntingRange,
                      int localReachableEdibleCount, double staminaCost,
                      HuntRandomLedger randomLedger)
    {
        this(outcome, destination, selectedPreySpecies, huntingRange,
            localReachableEdibleCount, staminaCost, randomLedger, null);
    }

    public HuntResult(HuntOutcome outcome, Location destination,
                      String selectedPreySpecies, int huntingRange,
                      int localReachableEdibleCount, double staminaCost,
                      HuntRandomLedger randomLedger,
                      HuntOpportunityFact opportunityFact)
    {
        this.outcome = outcome == null ? HuntOutcome.INELIGIBLE : outcome;
        this.destination = destination == null ? null :
            new Location(destination.row(), destination.col());
        this.selectedPreySpecies = selectedPreySpecies == null ? "" :
            selectedPreySpecies;
        this.huntingRange = Math.max(0, huntingRange);
        this.localReachableEdibleCount = Math.max(0,
            localReachableEdibleCount);
        this.staminaCost = Math.max(0.0, staminaCost);
        this.randomLedger = randomLedger == null ? HuntRandomLedger.none()
                                                  : randomLedger;
        this.opportunityFact = opportunityFact;
    }

    public HuntOutcome getOutcome() { return outcome; }

    public Location getDestination()
    {
        return destination == null ? null :
            new Location(destination.row(), destination.col());
    }

    public String getSelectedPreySpecies() { return selectedPreySpecies; }

    public int getHuntingRange() { return huntingRange; }

    public int getLocalReachableEdibleCount()
    {
        return localReachableEdibleCount;
    }

    public boolean hasLocalReachableEdible()
    {
        return localReachableEdibleCount > 0;
    }

    public double getStaminaCost() { return staminaCost; }

    public HuntRandomLedger getRandomLedger() { return randomLedger; }

    /** Exact S0 identity consumed for this result, or null for old adapters. */
    public HuntOpportunityFact getOpportunityFact() { return opportunityFact; }
}
