import java.awt.Rectangle;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Locks the A2 display cadence and inspect-animation structure without opening
 * a Swing window. Bytecode-level call-chain checks live in
 * verify-display-contract.sh.
 */
public final class DisplayContractProbe
{
    private DisplayContractProbe()
    {
    }

    public static void run()
    {
        testVisualRefreshCadence();
        testInspectStructureIsRetained();
        testPollingReplacementIsAbsent();
        testLivingInspectActorChangesOverTime();
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("A2 display contract probe");
        TestSupport.startGroup("Display contract");
        run();
        TestSupport.finishGroup();
        TestSupport.finishSuite();
        if(TestSupport.hasFailures()) {
            System.exit(1);
        }
    }

    private static void testVisualRefreshCadence()
    {
        Simulator simulator = new Simulator(3, 3, false);
        setField(simulator, "view", allocateWithoutConstructor(SimulatorView.class));

        assertRefreshAt(simulator, 0, true);
        assertRefreshAt(simulator, 1, false);
        assertRefreshAt(simulator, 99, false);
        assertRefreshAt(simulator, 100, true);
        assertRefreshAt(simulator, 101, false);
        assertRefreshAt(simulator, 200, true);
    }

    private static void assertRefreshAt(Simulator simulator, int step,
                                        boolean expected)
    {
        setField(simulator, "step", Integer.valueOf(step));
        boolean actual = invokeBoolean(simulator, "shouldRefreshVisualStep");
        TestSupport.assertEquals("visual refresh at step " + step,
                                 Boolean.valueOf(expected),
                                 Boolean.valueOf(actual));
    }

    private static void testInspectStructureIsRetained()
    {
        TestSupport.assertTrue("SimulatorView retains storyboard field",
                               hasDeclaredField("storyboard"));
        TestSupport.assertTrue("SimulatorView retains ambientActors field",
                               hasDeclaredField("ambientActors"));
        TestSupport.assertTrue("SimulatorView retains sceneTimer field",
                               hasDeclaredField("sceneTimer"));
        TestSupport.assertTrue("SimulatorView retains pauseBeforeInspect field",
                               hasDeclaredField("pauseBeforeInspect"));
        TestSupport.assertTrue("SimulatorView retains rebuildStoryboard method",
                               hasDeclaredMethod("rebuildStoryboard"));
        TestSupport.assertTrue("SimulatorView retains advanceStoryboard method",
                               hasDeclaredMethod("advanceStoryboard"));
        TestSupport.assertTrue("SimulatorView retains enterInspectMode method",
                               hasDeclaredMethod("enterInspectMode"));
        TestSupport.assertTrue("SimulatorView retains exitInspectMode method",
                               hasDeclaredMethod("exitInspectMode"));
    }

    private static void testPollingReplacementIsAbsent()
    {
        TestSupport.assertFalse("SimulatorView has no renderTimer field",
                                hasDeclaredField("renderTimer"));
        TestSupport.assertFalse("SimulatorView has no pollRenderFrame method",
                                hasDeclaredMethod("pollRenderFrame"));
        TestSupport.assertFalse("SimulatorView has no setRenderSource method",
                                hasDeclaredMethod("setRenderSource"));
    }

    private static void testLivingInspectActorChangesOverTime()
    {
        Field field = new Field(5, 5);
        Gazelle gazelle = new Gazelle(false, new Location(2, 2));
        field.placeAnimal(gazelle, gazelle.getLocation());
        List<SceneDirector.SceneActor> actors = SceneDirector.buildBehaviorActors(
            java.util.Collections.<SimulationEvent>emptyList(), field,
            new Rectangle(0, 0, 5, 5));

        TestSupport.assertEquals("living inspect actor is present", 1,
                                 actors.size());
        if(actors.isEmpty()) {
            return;
        }
        SceneDirector.SceneActor actor = actors.get(0);
        SceneDirector.Keyframe start = actor.at(0);
        SceneDirector.Keyframe later = actor.at(1200);
        boolean positionChanged = Math.abs(start.row - later.row) > 0.01 ||
                                  Math.abs(start.col - later.col) > 0.01;
        TestSupport.assertEquals("inspect actor keeps living animal ID",
                                 Long.valueOf(gazelle.getId()),
                                 Long.valueOf(actor.animalId));
        TestSupport.assertTrue("living inspect actor changes position by 1200 ms",
                               positionChanged);
    }

    private static boolean hasDeclaredField(String name)
    {
        try {
            SimulatorView.class.getDeclaredField(name);
            return true;
        }
        catch(NoSuchFieldException expected) {
            return false;
        }
    }

    private static boolean hasDeclaredMethod(String name)
    {
        for(Method method : SimulatorView.class.getDeclaredMethods()) {
            if(method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean invokeBoolean(Object target, String methodName)
    {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return ((Boolean)method.invoke(target)).booleanValue();
        }
        catch(ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot invoke " + methodName, e);
        }
    }

    private static void setField(Object target, String fieldName, Object value)
    {
        try {
            java.lang.reflect.Field field =
                target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        }
        catch(ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set " + fieldName, e);
        }
    }

    private static Object allocateWithoutConstructor(Class<?> type)
    {
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            java.lang.reflect.Field unsafeField =
                unsafeType.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            Method allocate = unsafeType.getMethod("allocateInstance", Class.class);
            return allocate.invoke(unsafe, type);
        }
        catch(ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Cannot allocate a constructor-free SimulatorView for headless cadence checks",
                e);
        }
    }
}
