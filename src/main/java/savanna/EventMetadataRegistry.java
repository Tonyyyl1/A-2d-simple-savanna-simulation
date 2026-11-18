package savanna;

import java.awt.Color;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Single source for event names, colors, icons and inspect scene categories. */
public final class EventMetadataRegistry
{
    private static final Map<SimulationEvent.EventType, EventMetadata> METADATA;
    private static final SimulationEvent.EventType[] HEATMAP_TYPES = {
        SimulationEvent.EventType.MOVE,
        SimulationEvent.EventType.HUNT,
        SimulationEvent.EventType.GRAZE,
        SimulationEvent.EventType.BIRTH,
        SimulationEvent.EventType.INFECTION,
        SimulationEvent.EventType.RECOVERY,
        SimulationEvent.EventType.DEATH,
        SimulationEvent.EventType.DRINK
    };

    static {
        EnumMap<SimulationEvent.EventType, EventMetadata> entries =
            new EnumMap<>(SimulationEvent.EventType.class);
        add(entries, SimulationEvent.EventType.HUNT, "Hunt", "捕猎",
            new Color(220, 70, 48), "X", "HUNT");
        add(entries, SimulationEvent.EventType.GRAZE, "Graze", "进食",
            new Color(96, 174, 67), "eat", "GRAZE");
        add(entries, SimulationEvent.EventType.BIRTH, "Birth", "出生",
            new Color(72, 190, 104), "+", "BIRTH");
        add(entries, SimulationEvent.EventType.INFECTION, "Infection", "感染",
            new Color(92, 194, 84), "sick", "INFECTION");
        add(entries, SimulationEvent.EventType.RECOVERY, "Recovery", "康复",
            new Color(62, 178, 132), "heal", "RECOVERY");
        add(entries, SimulationEvent.EventType.DISEASE_DEATH, "Disease death", "病死",
            new Color(154, 52, 158), "!", "DEATH");
        add(entries, SimulationEvent.EventType.MOVE, "Move", "移动",
            new Color(215, 208, 176), "move", "MOVE");
        add(entries, SimulationEvent.EventType.DRINK, "Drink", "饮水",
            new Color(52, 156, 222), "drink", "DRINK");
        add(entries, SimulationEvent.EventType.WATER_BLOCKED, "Water blocked", "寻水受阻",
            new Color(49, 111, 175), "~", "STATUS");
        add(entries, SimulationEvent.EventType.WATER_ARRIVAL_DEFICIT,
            "Water arrival deficit", "到岸水分不足",
            new Color(215, 109, 58), "!", "STATUS");
        add(entries, SimulationEvent.EventType.WATER_RETARGET,
            "Water retarget", "重新选水池",
            new Color(85, 129, 215), "~", "STATUS");
        add(entries, SimulationEvent.EventType.POSITION_CONFLICT, "Position conflict", "位置冲突",
            new Color(218, 126, 42), "!", "STATUS");
        add(entries, SimulationEvent.EventType.DEATH, "Death", "死亡",
            new Color(154, 52, 158), "!", "DEATH");
        for(SimulationEvent.EventType type : SimulationEvent.EventType.values()) {
            if(!entries.containsKey(type)) {
                throw new IllegalStateException("Missing event metadata for " + type);
            }
        }
        METADATA = Collections.unmodifiableMap(entries);
    }

    private EventMetadataRegistry()
    {
    }

    private static void add(Map<SimulationEvent.EventType, EventMetadata> entries,
                            SimulationEvent.EventType type, String english,
                            String chinese, Color color, String icon,
                            String sceneKind)
    {
        entries.put(type, new EventMetadata(type, english, chinese, color,
                                            icon, sceneKind));
    }

    public static EventMetadata forType(SimulationEvent.EventType type)
    {
        return type == null ? null : METADATA.get(type);
    }

    public static Map<SimulationEvent.EventType, EventMetadata> all()
    {
        return METADATA;
    }

    public static SimulationEvent.EventType heatmapTypeAt(int selectorIndex)
    {
        int index = selectorIndex - 1;
        return index < 0 || index >= HEATMAP_TYPES.length
            ? null : HEATMAP_TYPES[index];
    }

    public static String[] heatmapLabels()
    {
        String[] labels = new String[HEATMAP_TYPES.length + 1];
        labels[0] = "All / 全部";
        for(int index = 0; index < HEATMAP_TYPES.length; index++) {
            labels[index + 1] = forType(HEATMAP_TYPES[index]).getBilingualName();
        }
        return labels;
    }
}
