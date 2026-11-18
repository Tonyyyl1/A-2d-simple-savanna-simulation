package savanna;

/** Supported O0b3 heatmap time windows. */
public enum HeatmapWindow
{
    LAST_100("Last 100 / 最近100"),
    LAST_1000("Last 1000 / 最近1000"),
    WHOLE_RUN("Whole run / 全程");

    private final String label;

    HeatmapWindow(String label)
    {
        this.label = label;
    }

    public String getLabel()
    {
        return label;
    }

    public int startStep(int frameStep)
    {
        if(frameStep <= 0) return 0;
        if(this == WHOLE_RUN) return 1;
        int length = this == LAST_100 ? 100 : 1000;
        return Math.max(1, frameStep - length + 1);
    }
}
