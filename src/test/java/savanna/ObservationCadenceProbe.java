package savanna;

/** Deterministic CFG0 schedule contract for independently configured sinks. */
public final class ObservationCadenceProbe
{
    private ObservationCadenceProbe() { }

    public static void main(String[] args)
    {
        ObservationConfig config = new ObservationConfig(7, 11, 13, 17,
            3, 7, 96, 600, 5);
        ObservationSchedule schedule = new ObservationSchedule(config);
        require(schedule.checkpoints(29, config.getVisibleCadence()).equals(
            java.util.Arrays.asList(0, 7, 14, 21, 28, 29)),
            "visible checkpoints differ");
        require(schedule.checkpoints(29, config.getReportCadence()).equals(
            java.util.Arrays.asList(0, 11, 22, 29)),
            "report checkpoints differ");
        require(schedule.checkpoints(29, config.getChartCadence()).equals(
            java.util.Arrays.asList(0, 13, 26, 29)),
            "chart checkpoints differ");
        System.out.println("CFG0_OBSERVATION_CADENCE PASS visible=" +
            schedule.checkpoints(29, config.getVisibleCadence()) + " report=" +
            schedule.checkpoints(29, config.getReportCadence()) + " chart=" +
            schedule.checkpoints(29, config.getChartCadence()));
    }

    private static void require(boolean condition, String message)
    {
        if(!condition) throw new IllegalStateException(message);
    }
}
