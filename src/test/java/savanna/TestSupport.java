package savanna;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Minimal test helpers for this no-dependency BlueJ project.
 */
public class TestSupport
{
    private static GateResult.Builder resultBuilder;
    private static GateResult lastResult;

    public static void startSuite(String name)
    {
        resultBuilder = GateResult.builder(name);
        lastResult = null;
    }

    public static void startGroup(String name)
    {
        ensureSuite();
        resultBuilder.startGroup(name);
    }

    public static GateResult.Group finishGroup()
    {
        return resultBuilder.finishGroup(null);
    }

    public static GateResult.Group finishGroup(String fixtureId)
    {
        return resultBuilder.finishGroup(fixtureId);
    }

    public static GateResult finishSuite()
    {
        ensureSuite();
        lastResult = resultBuilder.finish();
        lastResult.printConsole(System.out);
        String outputDirectory = System.getProperty("savanna.gate.output", "");
        if(!outputDirectory.isBlank()) {
            try {
                lastResult.writeArtifacts(Path.of(outputDirectory));
            }
            catch(IOException error) {
                throw new IllegalStateException(
                    "could not write canonical gate artifacts", error);
            }
        }
        return lastResult;
    }

    public static boolean hasFailures()
    {
        if(lastResult != null) return !lastResult.isPassed();
        return resultBuilder != null && resultBuilder.getFailed() > 0;
    }

    public static GateResult getLastResult()
    {
        if(lastResult == null) {
            throw new IllegalStateException("suite result is not final");
        }
        return lastResult;
    }

    public static void assertTrue(String name, boolean condition)
    {
        record(name, condition, "expected true");
    }

    public static void assertFalse(String name, boolean condition)
    {
        record(name, !condition, "expected false");
    }

    public static void assertEquals(String name, int expected, int actual)
    {
        record(name, expected == actual,
               "expected " + expected + " but was " + actual);
    }

    public static void assertEquals(String name, Object expected, Object actual)
    {
        boolean passed = expected == null ? actual == null : expected.equals(actual);
        record(name, passed, "expected " + expected + " but was " + actual);
    }

    public static void assertClose(String name, double expected, double actual,
                                   double tolerance)
    {
        record(name, Math.abs(expected - actual) <= tolerance,
               "expected " + expected + " +/- " + tolerance + " but was " + actual);
    }

    public static void assertRange(String name, double value, double min, double max)
    {
        record(name, value >= min && value <= max,
               "expected " + value + " in range " + min + "-" + max);
    }

    public static void setField(Object target, String fieldName, Object value)
    {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        }
        catch(ReflectiveOperationException e) {
            throw new RuntimeException("Could not set field " + fieldName, e);
        }
    }

    public static void setHydration(SavannahAnimal animal, double percent)
    {
        if(percent < 0.0 || percent > SavannahAnimal.MAX_HYDRATION) {
            throw new IllegalArgumentException("hydration percent out of range");
        }
        animal.rehydrate();
        animal.drainHydration(SavannahAnimal.MAX_HYDRATION - percent);
    }

    public static void setDehydrationSteps(SavannahAnimal animal, int steps)
    {
        if(steps < animal.getDehydrationSteps()) {
            throw new IllegalArgumentException(
                "cannot lower dehydration steps through the public state API");
        }
        while(animal.getDehydrationSteps() < steps) {
            animal.addDehydrationStep();
        }
    }

    public static int getIntField(Object target, String fieldName)
    {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            return field.getInt(target);
        }
        catch(ReflectiveOperationException e) {
            throw new RuntimeException("Could not read field " + fieldName, e);
        }
    }

    public static void invokePrivate(Object target, String methodName,
                                     Class<?>[] parameterTypes, Object[] args)
    {
        try {
            Method method = findMethod(target.getClass(), methodName, parameterTypes);
            method.setAccessible(true);
            method.invoke(target, args);
        }
        catch(ReflectiveOperationException e) {
            throw new RuntimeException("Could not invoke method " + methodName, e);
        }
    }

    public static double invokePrivateDouble(Object target, String methodName,
                                             Class<?>[] parameterTypes, Object[] args)
    {
        try {
            Method method = findMethod(target.getClass(), methodName, parameterTypes);
            method.setAccessible(true);
            return ((Double)method.invoke(target, args)).doubleValue();
        }
        catch(ReflectiveOperationException e) {
            throw new RuntimeException("Could not invoke method " + methodName, e);
        }
    }

    private static Field findField(Class<?> type, String fieldName)
        throws NoSuchFieldException
    {
        Class<?> current = type;
        while(current != null) {
            try {
                return current.getDeclaredField(fieldName);
            }
            catch(NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static Method findMethod(Class<?> type, String methodName,
                                     Class<?>[] parameterTypes)
        throws NoSuchMethodException
    {
        Class<?> current = type;
        while(current != null) {
            try {
                return current.getDeclaredMethod(methodName, parameterTypes);
            }
            catch(NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(methodName);
    }

    private static void record(String name, boolean passed, String failureMessage)
    {
        ensureSuite();
        resultBuilder.record(name, passed, failureMessage);
    }

    private static void ensureSuite()
    {
        if(resultBuilder == null) {
            startSuite("Standalone test suite");
        }
    }
}
