package savanna;

/** Read-only sink for one completed immutable V24-OBS-1B step fact. */
@FunctionalInterface
public interface LongHorizonTrophicStepListener
{
    void onLongHorizonTrophicStep(LongHorizonTrophicStepFact fact);
}
