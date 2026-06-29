import java.util.ArrayList;
import java.util.List;

/**
 * Focused tests for small simulation rules.
 */
public class SimulationUnitTests
{
    public static void run()
    {
        TestSupport.startGroup("Unit tests");
        Randomizer.reset();
        testSimulationSettings();
        testClock();
        testSurvivalPressure();
        testStaminaStagesAndBurst();
        testStarvationNeedsThreeSteps();
        testFeedingClearsStarvation();
        testReportInterval();
        TestSupport.finishGroup();
    }

    private static void testSimulationSettings()
    {
        TestSupport.assertEquals("visual refresh interval is 100",
                                 100, SimulationSettings.VISUAL_REFRESH_INTERVAL);
        TestSupport.assertEquals("record interval is 100",
                                 100, SimulationSettings.RECORD_INTERVAL);
        TestSupport.assertEquals("chart update interval is 100",
                                 100, SimulationSettings.CHART_UPDATE_INTERVAL);
    }

    private static void testClock()
    {
        SimulationClock clock = new SimulationClock();
        clock.setStep(0);
        TestSupport.assertEquals("step 0 is hour 0", 0, clock.getHour());
        TestSupport.assertEquals("hour 0 is night", DayPhase.NIGHT, clock.getPhase());
        clock.setStep(5);
        TestSupport.assertEquals("hour 5 is dawn", DayPhase.DAWN, clock.getPhase());
        clock.setStep(8);
        TestSupport.assertEquals("hour 8 is day", DayPhase.DAY, clock.getPhase());
        clock.setStep(18);
        TestSupport.assertEquals("hour 18 is dusk", DayPhase.DUSK, clock.getPhase());
        clock.setStep(24);
        TestSupport.assertEquals("24 steps wraps to hour 0", 0, clock.getHour());
    }

    private static void testSurvivalPressure()
    {
        Gazelle gazelle = new Gazelle(false, new Location(1, 1));
        int maxFood = gazelle.getProfile().getMaxFoodLevel();

        TestSupport.setField(gazelle, "foodLevel", maxFood * 2);
        TestSupport.assertClose("survival percent is capped at 100",
                                100.0, gazelle.getSurvivalPercent(), 0.01);
        TestSupport.assertClose("normal survival has no pressure",
                                0.0, gazelle.getSurvivalPressure(), 0.01);

        TestSupport.setField(gazelle, "foodLevel", maxFood / 2);
        TestSupport.assertRange("mid survival is between 30 and 70",
                                gazelle.getSurvivalPercent(), 30.0, 70.0);
        TestSupport.assertClose("50 percent survival has 0.5 pressure",
                                0.5, gazelle.getSurvivalPressure(), 0.12);

        TestSupport.setField(gazelle, "foodLevel", -10);
        TestSupport.assertClose("survival percent is capped at 0",
                                0.0, gazelle.getSurvivalPercent(), 0.01);
        TestSupport.assertClose("critical survival has full pressure",
                                1.0, gazelle.getSurvivalPressure(), 0.01);
        TestSupport.assertTrue("critical survival is detected",
                               gazelle.isSurvivalCritical());
    }

    private static void testStaminaStagesAndBurst()
    {
        Gazelle gazelle = new Gazelle(false, new Location(1, 1));
        TestSupport.setField(gazelle, "stamina", 80.0);
        TestSupport.assertEquals("80 stamina is high", StaminaStage.HIGH,
                                 gazelle.getStaminaStage());
        TestSupport.setField(gazelle, "stamina", 50.0);
        TestSupport.assertEquals("50 stamina is medium", StaminaStage.MEDIUM,
                                 gazelle.getStaminaStage());
        TestSupport.setField(gazelle, "stamina", 10.0);
        TestSupport.assertEquals("10 stamina is low", StaminaStage.LOW,
                                 gazelle.getStaminaStage());

        TestSupport.setField(gazelle, "stamina", 50.0);
        TestSupport.setField(gazelle, "foodLevel", gazelle.getProfile().getMaxFoodLevel());
        double normalMultiplier = gazelle.getEffectiveStaminaMultiplier();
        TestSupport.setField(gazelle, "foodLevel", 1);
        double survivalMultiplier = gazelle.getEffectiveStaminaMultiplier();
        TestSupport.assertTrue("critical survival gives temporary stamina burst",
                               survivalMultiplier > normalMultiplier);

        double beforeSpend = gazelle.getStaminaPercent();
        gazelle.spendStamina(5.0);
        TestSupport.assertClose("spending stamina reduces stamina",
                                beforeSpend - 5.0, gazelle.getStaminaPercent(), 0.01);
    }

    private static void testStarvationNeedsThreeSteps()
    {
        Lion lion = new Lion(false, new Location(1, 1));
        Field field = new Field(3, 3);
        field.placeAnimal(lion, lion.getLocation());
        SimulationContext context = new SimulationContext(3, 3);
        context.startStep(1, field);
        TestSupport.setField(lion, "foodLevel", 0);
        TestSupport.setField(lion, "starvingSteps", 0);

        TestSupport.invokePrivate(lion, "useEnergy",
            new Class<?>[] { SimulationContext.class }, new Object[] { context });
        TestSupport.assertTrue("first starving step keeps animal alive", lion.isAlive());
        TestSupport.assertEquals("first starving counter is 1", 1,
                                 lion.getStarvingSteps());

        TestSupport.invokePrivate(lion, "useEnergy",
            new Class<?>[] { SimulationContext.class }, new Object[] { context });
        TestSupport.assertTrue("second starving step keeps animal alive", lion.isAlive());
        TestSupport.assertEquals("second starving counter is 2", 2,
                                 lion.getStarvingSteps());

        TestSupport.invokePrivate(lion, "useEnergy",
            new Class<?>[] { SimulationContext.class }, new Object[] { context });
        TestSupport.assertFalse("third starving step kills animal", lion.isAlive());
    }

    private static void testFeedingClearsStarvation()
    {
        Gazelle gazelle = new Gazelle(false, new Location(1, 1));
        TestSupport.setField(gazelle, "foodLevel", -2);
        TestSupport.setField(gazelle, "starvingSteps", 2);
        gazelle.feed(10);
        TestSupport.assertTrue("feeding restores food above zero",
                               gazelle.getFoodLevel() > 0);
        TestSupport.assertEquals("feeding clears starving counter",
                                 0, gazelle.getStarvingSteps());
    }

    private static void testReportInterval()
    {
        Field field = new Field(3, 3);
        SimulationContext context = new SimulationContext(3, 3);
        CountingRecorder recorder =
            new CountingRecorder("/private/tmp/savanna-alltests-report.html");
        context.startStep(0, field);
        recorder.start(field, context);
        for(int step = 1; step <= 250; step++) {
            context.startStep(step, field);
            recorder.recordStep(step, field, context);
        }
        recorder.finish(field, context, "unit interval test");
        TestSupport.assertEquals("interval report records start, 100, 200, final",
                                 4, recorder.getRecordedSteps().size());
        TestSupport.assertEquals("first report step is 0",
                                 0, recorder.getRecordedSteps().get(0).intValue());
        TestSupport.assertEquals("second report step is 100",
                                 100, recorder.getRecordedSteps().get(1).intValue());
        TestSupport.assertEquals("third report step is 200",
                                 200, recorder.getRecordedSteps().get(2).intValue());
        TestSupport.assertEquals("final report step is 250",
                                 250, recorder.getRecordedSteps().get(3).intValue());
    }

    private static class CountingRecorder extends StepReportRecorder
    {
        private final List<Integer> recordedSteps = new ArrayList<>();

        CountingRecorder(String reportFileName)
        {
            super(reportFileName);
        }

        protected void onStepRecorded(StepSnapshot snapshot)
        {
            recordedSteps.add(snapshot.getStep());
        }

        List<Integer> getRecordedSteps()
        {
            return recordedSteps;
        }
    }
}
