package savanna;

/** Fail-fast ordering guard used before any ecology stage is eligible to run. */
public final class V24GatePipeline
{
    private V24GatePipeline() {}

    public static GateResult runEcologyOnlyAfterDefault(
        GateResult defaultResult, Runnable ecologyInvocation)
    {
        if(defaultResult == null) {
            throw new IllegalArgumentException("default result is required");
        }
        if(!defaultResult.isPassed()) return defaultResult;
        if(ecologyInvocation == null) {
            throw new IllegalArgumentException(
                "ecology invocation is required after a passing default gate");
        }
        ecologyInvocation.run();
        return defaultResult;
    }
}
