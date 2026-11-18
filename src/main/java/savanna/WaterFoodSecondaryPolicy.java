package savanna;

/** Governs food recovery while SEEK_WATER remains the primary behavior. */
public enum WaterFoodSecondaryPolicy
{
    LEGACY_EXPLICIT,
    WATER_ONLY;

    public boolean allowsCriticalFoodSecondary()
    {
        return this == LEGACY_EXPLICIT;
    }
}
