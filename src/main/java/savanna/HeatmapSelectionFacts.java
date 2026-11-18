package savanna;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Immutable cell facts derived from one frozen snapshot and one selection. */
public final class HeatmapSelectionFacts
{
    private final HeatmapSelection selection;
    private final int startStep;
    private final int endStep;
    private final int totalEventCount;
    private final Map<Location, Integer> counts;
    private final int maximum;

    HeatmapSelectionFacts(HeatmapSelection selection, int startStep, int endStep,
                          int totalEventCount, Map<Location, Integer> counts)
    {
        this.selection = selection == null ? HeatmapSelection.defaultSelection()
                                           : selection;
        this.startStep = startStep;
        this.endStep = endStep;
        this.totalEventCount = totalEventCount;
        this.counts = Collections.unmodifiableMap(new HashMap<>(counts));
        int candidate = 0;
        for(Integer count : this.counts.values()) {
            if(count != null && count > candidate) candidate = count;
        }
        maximum = candidate;
    }

    public HeatmapSelection getSelection() { return selection; }
    public int getStartStep() { return startStep; }
    public int getEndStep() { return endStep; }
    public int getTotalEventCount() { return totalEventCount; }
    public int getNonZeroCellCount() { return counts.size(); }
    public int getMaximum() { return maximum; }
    public int getCountAt(Location location)
    {
        Integer count = counts.get(location);
        return count == null ? 0 : count;
    }
    public Map<Location, Integer> getCounts() { return counts; }
}
