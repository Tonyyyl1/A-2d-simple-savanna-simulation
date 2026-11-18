package savanna;

import java.lang.reflect.Field;

/** Exact O0b3 boundaries and actor-species filtering proof. */
public final class HeatmapWindowProbe
{
    private HeatmapWindowProbe()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("O0b3 heatmap window probe");
        TestSupport.startGroup("O0b3 windows and species");
        run();
        TestSupport.finishGroup();
        TestSupport.finishSuite();
        if(TestSupport.hasFailures()) System.exit(1);
    }

    public static void run()
    {
        EventAccumulator accumulator = new EventAccumulator();
        Location shared = new Location(4, 4);
        Location other = new Location(5, 5);
        accumulator.onEvent(event(1, SimulationEvent.EventType.GRAZE,
                                  SpeciesRegistry.LION, shared));
        for(int count = 0; count < 2; count++) {
            accumulator.onEvent(event(100, SimulationEvent.EventType.GRAZE,
                                      SpeciesRegistry.LION, shared));
        }
        for(int count = 0; count < 3; count++) {
            accumulator.onEvent(event(100, SimulationEvent.EventType.GRAZE,
                                      SpeciesRegistry.GAZELLE, shared));
        }
        HeatmapSnapshot step100 = accumulator.freeze(100);
        HeatmapSelection all100 = new HeatmapSelection(HeatmapWindow.LAST_100,
                                                        null, null);
        HeatmapSelectionFacts facts100 = step100.select(all100);
        TestSupport.assertEquals("O0b3 step 100 window starts at one", 1,
                                 facts100.getStartStep());
        TestSupport.assertEquals("O0b3 step 100 window total", 6,
                                 facts100.getTotalEventCount());
        TestSupport.assertEquals("O0b3 same-cell all species total", 6,
                                 facts100.getCountAt(shared));
        TestSupport.assertEquals("O0b3 Lion filter is exact", 3,
            step100.select(new HeatmapSelection(HeatmapWindow.LAST_100, null,
                SpeciesRegistry.LION)).getCountAt(shared));
        TestSupport.assertEquals("O0b3 Gazelle filter is exact", 3,
            step100.select(new HeatmapSelection(HeatmapWindow.LAST_100, null,
                SpeciesRegistry.GAZELLE)).getCountAt(shared));

        accumulator.onEvent(event(101, SimulationEvent.EventType.DRINK,
                                  SpeciesRegistry.GAZELLE, other));
        HeatmapSnapshot step101 = accumulator.freeze(101);
        TestSupport.assertEquals("O0b3 step 101 last-100 starts at two", 2,
            step101.select(all100).getStartStep());
        TestSupport.assertEquals("O0b3 step 101 excludes step one", 6,
            step101.select(all100).getTotalEventCount());
        TestSupport.assertEquals("O0b3 step 101 last-1000 starts at one", 1,
            step101.select(new HeatmapSelection(HeatmapWindow.LAST_1000, null, null))
                .getStartStep());
        TestSupport.assertEquals("O0b3 step 101 last-1000 includes step one", 7,
            step101.select(new HeatmapSelection(HeatmapWindow.LAST_1000, null, null))
                .getTotalEventCount());

        HeatmapSnapshot step999 = accumulator.freeze(999);
        TestSupport.assertEquals("O0b3 step 999 last-100 starts at 900", 900,
            step999.select(all100).getStartStep());
        TestSupport.assertEquals("O0b3 step 999 last-100 is empty", 0,
            step999.select(all100).getTotalEventCount());
        HeatmapSnapshot step1000 = accumulator.freeze(1000);
        TestSupport.assertEquals("O0b3 step 1000 last-1000 starts at one", 1,
            step1000.select(new HeatmapSelection(HeatmapWindow.LAST_1000, null, null))
                .getStartStep());
        TestSupport.assertEquals("O0b3 step 1000 last-1000 includes initial event", 7,
            step1000.select(new HeatmapSelection(HeatmapWindow.LAST_1000, null, null))
                .getTotalEventCount());
        accumulator.onEvent(event(1001, SimulationEvent.EventType.GRAZE,
                                  SpeciesRegistry.LION, shared));
        HeatmapSnapshot step1001 = accumulator.freeze(1001);
        TestSupport.assertEquals("O0b3 step 1001 last-1000 starts at two", 2,
            step1001.select(new HeatmapSelection(HeatmapWindow.LAST_1000, null, null))
                .getStartStep());
        TestSupport.assertEquals("O0b3 step 1001 expires only step one", 7,
            step1001.select(new HeatmapSelection(HeatmapWindow.LAST_1000, null, null))
                .getTotalEventCount());
        TestSupport.assertTrue("O0b3 buckets retain no SimulationEvent objects",
                               bucketsContainNoSimulationEvents(accumulator));
        verifyCfg0ConfiguredWindows(shared);
    }

    private static void verifyCfg0ConfiguredWindows(Location location)
    {
        ObservationConfig config = new ObservationConfig(7, 11, 13, 17,
            3, 7, 96, 600, 5);
        EventAccumulator accumulator = new EventAccumulator(config);
        accumulator.onEvent(event(1, SimulationEvent.EventType.GRAZE,
                                  SpeciesRegistry.GAZELLE, location));
        accumulator.onEvent(event(4, SimulationEvent.EventType.GRAZE,
                                  SpeciesRegistry.GAZELLE, location));
        accumulator.onEvent(event(5, SimulationEvent.EventType.GRAZE,
                                  SpeciesRegistry.GAZELLE, location));
        accumulator.onEvent(event(7, SimulationEvent.EventType.GRAZE,
                                  SpeciesRegistry.GAZELLE, location));
        HeatmapSnapshot step7 = accumulator.freeze(7);
        HeatmapSelection shortWindow = new HeatmapSelection(
            HeatmapWindow.LAST_100, null, null);
        HeatmapSelection longWindow = new HeatmapSelection(
            HeatmapWindow.LAST_1000, null, null);
        TestSupport.assertEquals("CFG0 short heatmap start uses configured 3", 5,
            step7.select(shortWindow).getStartStep());
        TestSupport.assertEquals("CFG0 short heatmap expires exact boundary", 2,
            step7.select(shortWindow).getTotalEventCount());
        TestSupport.assertEquals("CFG0 long heatmap start uses configured 7", 1,
            step7.select(longWindow).getStartStep());
        TestSupport.assertEquals("CFG0 long heatmap keeps whole configured span", 4,
            step7.select(longWindow).getTotalEventCount());
        HeatmapSnapshot step8 = accumulator.freeze(8);
        TestSupport.assertEquals("CFG0 short heatmap step 8 start", 6,
            step8.select(shortWindow).getStartStep());
        TestSupport.assertEquals("CFG0 short heatmap expires step five", 1,
            step8.select(shortWindow).getTotalEventCount());
        TestSupport.assertEquals("CFG0 long heatmap expires step one", 3,
            step8.select(longWindow).getTotalEventCount());
    }

    private static SimulationEvent event(int step, SimulationEvent.EventType type,
                                         String species, Location location)
    {
        return new SimulationEvent(type, step, location.row(), location.col(),
            -1, -1, 1L, -1L, species, "");
    }

    private static boolean bucketsContainNoSimulationEvents(EventAccumulator accumulator)
    {
        try {
            Field buckets = EventAccumulator.class.getDeclaredField("buckets");
            buckets.setAccessible(true);
            Object[] values = (Object[])buckets.get(accumulator);
            for(Object bucket : values) {
                for(Field field : bucket.getClass().getDeclaredFields()) {
                    if(field.getType() == SimulationEvent.class ||
                       (field.getType().isArray() &&
                        field.getType().getComponentType() == SimulationEvent.class)) {
                        return false;
                    }
                }
            }
            return true;
        }
        catch(ReflectiveOperationException e) {
            return false;
        }
    }
}
