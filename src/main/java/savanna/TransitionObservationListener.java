package savanna;

/** Read-only observer of completed movement or spawn reservation decisions. */
public interface TransitionObservationListener
{
    void onTransition(TransitionObservation observation);
}
