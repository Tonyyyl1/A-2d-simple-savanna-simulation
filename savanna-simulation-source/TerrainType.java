import java.awt.Color;

/**
 * Visual terrain categories for the savanna map background.
 */
public enum TerrainType
{
    WATERHOLE("Waterhole", new Color(69, 126, 155)),
    GRASSLAND("Grassland", new Color(139, 170, 82)),
    BUSH("Bush", new Color(92, 122, 68)),
    OPEN_PLAIN("Open plain", new Color(211, 191, 117)),
    DRY_SOIL("Dry soil", new Color(184, 139, 88));

    private final String displayName;
    private final Color color;

    TerrainType(String displayName, Color color)
    {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public Color getColor()
    {
        return color;
    }
}
