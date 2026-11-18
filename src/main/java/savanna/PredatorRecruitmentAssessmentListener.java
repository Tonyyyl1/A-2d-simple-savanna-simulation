package savanna;

import java.util.Set;

/**
 * Read-only V24-OBS-1A sink for the completed assessment of every
 * step-start predator. Implementations must not mutate simulation state or
 * consume the ecology random stream.
 */
public interface PredatorRecruitmentAssessmentListener
{
    void onPredatorRecruitmentAssessment(
        PredatorRecruitmentAssessment assessment);

    /** Called only after the complete step-start predator set is reconciled. */
    default void onPredatorRecruitmentStepComplete(int step,
                                                   Set<Long> predatorIds) { }
}
