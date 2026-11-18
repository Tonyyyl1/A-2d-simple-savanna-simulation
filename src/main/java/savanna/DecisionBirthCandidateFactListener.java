package savanna;

/** Observation-only consumer of the already-decided breeding candidate list. */
public interface DecisionBirthCandidateFactListener
{
    void onDecisionBirthCandidates(DecisionBirthCandidateFact fact);
}
