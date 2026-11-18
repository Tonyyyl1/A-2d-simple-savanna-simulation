package savanna;

/** Receives read-only R2 adjacent-candidate facts for diagnostic probes. */
public interface PositionCandidateObservationListener
{
    void onPositionCandidates(PositionCandidateObservation observation);
}
