package savanna;

/**
 * D2-only observation hook. Implementations must not mutate ecology, position
 * claims, Field state, or the ecological random stream.
 */
public interface HuntObservationListener
{
    default void onHuntDecision(HuntDecisionObservation observation)
    {
    }

    default void onHuntResult(HuntObservation observation)
    {
    }
}
