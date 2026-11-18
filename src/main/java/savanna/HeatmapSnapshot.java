package savanna;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Immutable, same-step canonical heatmap facts for all O0b3 selections. */
public final class HeatmapSnapshot
{
    private final int frameStep;
    private final Map<HeatmapWindow, Map<Integer, Integer>> packedCounts;
    private final Map<HeatmapWindow, Map<Integer, Integer>> totalsByDimension;
    private final Map<HeatmapWindow, Integer> startSteps;

    private HeatmapSnapshot(int frameStep,
                            Map<HeatmapWindow, Map<Integer, Integer>> packedCounts,
                            Map<HeatmapWindow, Map<Integer, Integer>> totalsByDimension,
                            Map<HeatmapWindow, Integer> startSteps)
    {
        if(frameStep < 0) throw new IllegalArgumentException("negative frame step");
        this.frameStep = frameStep;
        this.packedCounts = immutableWindowMaps(packedCounts);
        this.totalsByDimension = immutableWindowMaps(totalsByDimension);
        this.startSteps = immutableStartSteps(startSteps, frameStep);
    }

    static HeatmapSnapshot fromAccumulator(int frameStep,
                                            Map<HeatmapWindow, Map<Integer, Integer>> packedCounts,
                                            Map<HeatmapWindow, Map<Integer, Integer>> totalsByDimension)
    {
        return new HeatmapSnapshot(Math.max(0, frameStep), packedCounts,
                                   totalsByDimension, Collections.emptyMap());
    }

    static HeatmapSnapshot fromAccumulator(int frameStep,
                                            Map<HeatmapWindow, Map<Integer, Integer>> packedCounts,
                                            Map<HeatmapWindow, Map<Integer, Integer>> totalsByDimension,
                                            Map<HeatmapWindow, Integer> startSteps)
    {
        return new HeatmapSnapshot(Math.max(0, frameStep), packedCounts,
                                   totalsByDimension, startSteps);
    }

    public static HeatmapSnapshot emptyAtStep(int frameStep)
    {
        return fromAccumulator(frameStep,
            Collections.<HeatmapWindow, Map<Integer, Integer>>emptyMap(),
            Collections.<HeatmapWindow, Map<Integer, Integer>>emptyMap());
    }

    public int getFrameStep() { return frameStep; }

    /** Compatibility API: Whole-run / All-species selection facts. */
    public int getStatisticsStartStep()
    {
        return HeatmapWindow.WHOLE_RUN.startStep(frameStep);
    }

    public int getStatisticsEndStep() { return frameStep; }
    public int getTotalEventCount()
    {
        return select(new HeatmapSelection(HeatmapWindow.WHOLE_RUN, null, null))
            .getTotalEventCount();
    }
    public int getNonZeroCellCount()
    {
        return select(new HeatmapSelection(HeatmapWindow.WHOLE_RUN, null, null))
            .getNonZeroCellCount();
    }
    public int getNonZeroCellCount(SimulationEvent.EventType type)
    {
        return select(new HeatmapSelection(HeatmapWindow.WHOLE_RUN, type, null))
            .getNonZeroCellCount();
    }
    public int getMaxCount(SimulationEvent.EventType type)
    {
        return select(new HeatmapSelection(HeatmapWindow.WHOLE_RUN, type, null))
            .getMaximum();
    }
    public int getTotalCount(SimulationEvent.EventType type)
    {
        return select(new HeatmapSelection(HeatmapWindow.WHOLE_RUN, type, null))
            .getTotalEventCount();
    }
    public int getCountAt(Location location, SimulationEvent.EventType type)
    {
        return select(new HeatmapSelection(HeatmapWindow.WHOLE_RUN, type, null))
            .getCountAt(location);
    }
    public Map<Location, Integer> getCounts(SimulationEvent.EventType type)
    {
        return select(new HeatmapSelection(HeatmapWindow.WHOLE_RUN, type, null))
            .getCounts();
    }

    /** Derive one immutable selection once; callers must retain it for paint. */
    public HeatmapSelectionFacts select(HeatmapSelection requested)
    {
        HeatmapSelection selection = requested == null
            ? HeatmapSelection.defaultSelection() : requested;
        HeatmapWindow window = selection.getWindow();
        int start = startSteps.get(window);
        if(frameStep <= 0) {
            return new HeatmapSelectionFacts(selection, 0, 0, 0,
                Collections.<Location, Integer>emptyMap());
        }
        Map<Integer, Integer> rawCounts = packedCounts.get(window);
        Map<Integer, Integer> rawTotals = totalsByDimension.get(window);
        Map<Location, Integer> selectedCounts = new HashMap<>();
        if(rawCounts != null) {
            for(Map.Entry<Integer, Integer> entry : rawCounts.entrySet()) {
                int packed = entry.getKey();
                if(!matches(packed, selection)) continue;
                Location location = new Location(rowOf(packed), colOf(packed));
                selectedCounts.put(location, selectedCounts.getOrDefault(location, 0) +
                                             entry.getValue());
            }
        }
        int total = 0;
        if(rawTotals != null) {
            for(Map.Entry<Integer, Integer> entry : rawTotals.entrySet()) {
                if(matchesDimension(entry.getKey(), selection)) total += entry.getValue();
            }
        }
        return new HeatmapSelectionFacts(selection, start, frameStep, total,
                                         selectedCounts);
    }

    static int pack(int row, int col, SimulationEvent.EventType type,
                    String actorSpecies)
    {
        if(row < 0 || col < 0 || row > 4095 || col > 4095 || type == null) {
            return 0;
        }
        return (row << 20) | (col << 8) | (type.ordinal() << 4) |
               speciesCode(actorSpecies);
    }

    static int dimensionOf(int packed)
    {
        return packed & 0xff;
    }

    private static boolean matches(int packed, HeatmapSelection selection)
    {
        return matchesDimension(dimensionOf(packed), selection);
    }

    private static boolean matchesDimension(int dimension, HeatmapSelection selection)
    {
        int type = (dimension >>> 4) & 0x0f;
        int species = dimension & 0x0f;
        SimulationEvent.EventType requestedType = selection.getEventType();
        if(requestedType != null && requestedType.ordinal() != type) return false;
        String requestedSpecies = selection.getActorSpecies();
        return requestedSpecies == null || speciesCode(requestedSpecies) == species;
    }

    private static int rowOf(int packed) { return packed >>> 20; }
    private static int colOf(int packed) { return (packed >>> 8) & 0x0fff; }

    private static int speciesCode(String species)
    {
        if(SpeciesRegistry.LION.equals(species)) return 1;
        if(SpeciesRegistry.CHEETAH.equals(species)) return 2;
        if(SpeciesRegistry.ZEBRA.equals(species)) return 3;
        if(SpeciesRegistry.BUFFALO.equals(species)) return 4;
        if(SpeciesRegistry.GAZELLE.equals(species)) return 5;
        return 0;
    }

    private static Map<HeatmapWindow, Map<Integer, Integer>> immutableWindowMaps(
        Map<HeatmapWindow, Map<Integer, Integer>> source)
    {
        EnumMap<HeatmapWindow, Map<Integer, Integer>> copy =
            new EnumMap<>(HeatmapWindow.class);
        for(HeatmapWindow window : HeatmapWindow.values()) {
            Map<Integer, Integer> entries = source.get(window);
            copy.put(window, Collections.unmodifiableMap(entries == null
                ? Collections.<Integer, Integer>emptyMap() : new HashMap<>(entries)));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<HeatmapWindow, Integer> immutableStartSteps(
        Map<HeatmapWindow, Integer> source, int frameStep)
    {
        EnumMap<HeatmapWindow, Integer> copy = new EnumMap<>(HeatmapWindow.class);
        for(HeatmapWindow window : HeatmapWindow.values()) {
            Integer start = source == null ? null : source.get(window);
            copy.put(window, start == null ? window.startStep(frameStep)
                                           : Math.max(0, start));
        }
        return Collections.unmodifiableMap(copy);
    }
}
