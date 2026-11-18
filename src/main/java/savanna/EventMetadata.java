package savanna;

import java.awt.Color;

/** Immutable display metadata for one simulation event type. */
public final class EventMetadata
{
    private final SimulationEvent.EventType type;
    private final String englishName;
    private final String chineseName;
    private final Color color;
    private final String iconText;
    private final String sceneKind;

    public EventMetadata(SimulationEvent.EventType type, String englishName,
                         String chineseName, Color color, String iconText,
                         String sceneKind)
    {
        this.type = type;
        this.englishName = englishName == null ? "Event" : englishName;
        this.chineseName = chineseName == null ? "事件" : chineseName;
        this.color = color == null ? Color.white : color;
        this.iconText = iconText == null ? "" : iconText;
        this.sceneKind = sceneKind == null ? "NONE" : sceneKind;
    }

    public SimulationEvent.EventType getType() { return type; }
    public String getEnglishName() { return englishName; }
    public String getChineseName() { return chineseName; }
    public Color getColor() { return color; }
    public String getIconText() { return iconText; }
    public String getSceneKind() { return sceneKind; }

    public String getBilingualName()
    {
        return englishName + " / " + chineseName;
    }
}
