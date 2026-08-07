import java.awt.Color;

/**
 * Weather states and their ecological effects.
 */
public enum WeatherType
{
    CLEAR("Clear", new Color(250, 218, 110), 1.00, 1.00, 1.00),
    RAIN("Rain", new Color(80, 150, 220), 1.65, 0.62, 1.45),
    FOG("Fog", new Color(170, 180, 185), 1.15, 0.50, 1.08),
    DROUGHT("Drought", new Color(215, 155, 86), 0.82, 1.06, 0.60);

    private final String displayName;
    private final Color color;
    private final double infectionModifier;
    private final double visibilityModifier;
    private final double plantGrowthModifier;

    WeatherType(String displayName, Color color, double infectionModifier,
                double visibilityModifier, double plantGrowthModifier)
    {
        this.displayName = displayName;
        this.color = color;
        this.infectionModifier = infectionModifier;
        this.visibilityModifier = visibilityModifier;
        this.plantGrowthModifier = plantGrowthModifier;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public Color getColor()
    {
        return color;
    }

    public double getInfectionModifier()
    {
        return infectionModifier;
    }

    public double getVisibilityModifier()
    {
        return visibilityModifier;
    }

    public double getPlantGrowthModifier()
    {
        return plantGrowthModifier;
    }
}
