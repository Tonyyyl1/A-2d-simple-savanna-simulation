package savanna;

/**
 * Diagnostic-only legacy breeding RNG observer. Implementations must not
 * mutate ecology, position claims, or the shared random stream.
 */
public interface BreedingRandomObservationListener
{
    void onBreedingRandomObservation(BreedingRandomObservation observation);
}
