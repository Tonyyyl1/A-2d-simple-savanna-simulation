package savanna;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Frozen FRAME-1 production-surface expected-red.
 *
 * <p>This suite is reflection-only: it does not construct a Simulator, consume
 * ecology RNG, or depend on implementation source text.</p>
 */
public final class V24FrameExpectedRedTests
{
    private V24FrameExpectedRedTests() {}

    public static void main(String[] args)
    {
        TestSupport.startSuite("V24-FRAME-1 production-surface expected-red");
        TestSupport.startGroup("FRAME-1 frozen production surfaces");

        Class<?> ledger = load("savanna.StepFrameLedger");
        Class<?> opportunity = load("savanna.HuntOpportunityFact");
        Class<?> listener = load("savanna.StepFrameLedgerListener");
        Class<?> phase = load("savanna.StepFrameLedger$Phase");
        Class<?> draft = load("savanna.StepFrameLedger$FrameDraft");

        TestSupport.assertTrue("FRAME-ER F01 immutable StepFrameLedger exists",
            ledger != null);
        TestSupport.assertTrue("FRAME-ER F02 immutable HuntOpportunityFact exists",
            opportunity != null);
        TestSupport.assertEquals("FRAME-ER F03 phase order is exact S0 through S4",
            List.of("S0_CAPTURE", "S1_REPRODUCTION_PROPOSAL",
                "S2_PRIMARY_ACTION", "S3_REPRODUCTION_COMMIT", "S4_PUBLISH"),
            enumNames(phase));
        TestSupport.assertTrue("FRAME-ER F04 internal phase draft exists",
            draft != null);
        TestSupport.assertTrue("FRAME-ER F05 context exposes frame-ledger listener",
            hasMethod(load("savanna.SimulationContext"),
                "addStepFrameLedgerListener", listener));
        TestSupport.assertTrue("FRAME-ER F06 predation consumes frozen opportunity",
            hasFourArgumentHunt(load("savanna.PredationSystem"), opportunity));
        TestSupport.assertTrue("FRAME-ER F07 HuntResult retains opportunity identity",
            hasMethod(load("savanna.HuntResult"), "getOpportunityFact"));
        TestSupport.assertTrue("FRAME-ER F08 decision exposes opportunity ID",
            hasMethod(load("savanna.HuntDecisionObservation"),
                "getOpportunityId"));
        TestSupport.assertTrue("FRAME-ER F09 decision exposes candidate digest",
            hasMethod(load("savanna.HuntDecisionObservation"),
                "getCandidateDigest"));
        TestSupport.assertTrue("FRAME-ER F10 action executor returns typed primary fact",
            hasNamedMethod(load("savanna.ActionExecutor"),
                "executePrimaryFoodAction"));

        TestSupport.finishGroup("V24_FRAME_EXPECTED_RED_FIXTURES");
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    private static Class<?> load(String name)
    {
        try {
            return Class.forName(name);
        }
        catch(ClassNotFoundException expected) {
            return null;
        }
    }

    private static List<String> enumNames(Class<?> type)
    {
        if(type == null || !type.isEnum()) return List.of();
        return Arrays.stream(type.getEnumConstants())
            .map(value -> ((Enum<?>)value).name())
            .collect(Collectors.toList());
    }

    private static boolean hasMethod(Class<?> owner, String name,
                                     Class<?>... parameterTypes)
    {
        if(owner == null || Arrays.stream(parameterTypes)
            .anyMatch(value -> value == null)) return false;
        try {
            owner.getMethod(name, parameterTypes);
            return true;
        }
        catch(NoSuchMethodException expected) {
            return false;
        }
    }

    private static boolean hasFourArgumentHunt(Class<?> owner,
                                               Class<?> opportunity)
    {
        if(owner == null || opportunity == null) return false;
        for(Method method : owner.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if(method.getName().equals("huntWithOutcome") &&
               parameters.length == 4 &&
               parameters[3].equals(opportunity)) return true;
        }
        return false;
    }

    private static boolean hasNamedMethod(Class<?> owner, String name)
    {
        if(owner == null) return false;
        return Arrays.stream(owner.getMethods())
            .anyMatch(method -> method.getName().equals(name));
    }
}
