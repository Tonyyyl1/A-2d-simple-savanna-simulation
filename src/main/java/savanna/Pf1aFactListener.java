package savanna;

/** Read-only PF1-aF sink; callbacks never feed a simulation decision. */
public interface Pf1aFactListener
{
    default void onRealizedKill(RealizedKillRecord record) { }

    default void onLegacyBreeding(LegacyBreedingObservation observation) { }

    default void onBreedingCommit(BreedingCommitFact commit) { }

    default void onPredatorFemaleAssessment(
        PredatorFemaleAssessmentFact assessment) { }
}
