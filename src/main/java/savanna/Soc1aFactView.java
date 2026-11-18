package savanna;

/** Read-only registry and per-step raw capacity facts for SOC1-aF. */
public interface Soc1aFactView
{
    int getStep();

    SpeciesEcologyFactRegistry getSpeciesRegistry();

    StepStartLocalCapacityFact getLocalCapacity(long actorId);

    int getCachedCapacityFactCount();
}
