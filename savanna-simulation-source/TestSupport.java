import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Minimal test helpers for this no-dependency BlueJ project.
 */
public class TestSupport
{
    private static int totalTests;
    private static int failedTests;
    private static int groupTests;
    private static int groupFailures;
    private static long suiteStartedAt;
    private static long groupStartedAt;
    private static String groupName;

    public static void startSuite(String name)
    {
        totalTests = 0;
        failedTests = 0;
        suiteStartedAt = System.currentTimeMillis();
        System.out.println("Running " + name);
    }

    public static void startGroup(String name)
    {
        groupName = name;
        groupTests = 0;
        groupFailures = 0;
        groupStartedAt = System.currentTimeMillis();
        System.out.println();
        System.out.println("== " + name + " ==");
    }

    public static void finishGroup()
    {
        long elapsed = System.currentTimeMillis() - groupStartedAt;
        System.out.println("Group result: " + (groupTests - groupFailures) +
                           "/" + groupTests + " passed in " + elapsed + " ms");
    }

    public static void finishSuite()
    {
        long elapsed = System.currentTimeMillis() - suiteStartedAt;
        System.out.println();
        System.out.println("Passed " + (totalTests - failedTests) + "/" +
                           totalTests + " tests in " + elapsed + " ms");
    }

    public static boolean hasFailures()
    {
        return failedTests > 0;
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
        totalTests++;
        groupTests++;
        if(passed) {
            System.out.println("PASS " + name);
        }
        else {
            failedTests++;
            groupFailures++;
            System.out.println("FAIL " + name + " - " + failureMessage);
        }
    }
}
