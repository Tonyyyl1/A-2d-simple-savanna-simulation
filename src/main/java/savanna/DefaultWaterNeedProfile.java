package savanna;

/** W4-compatible water need profile shared by every species. */
public final class DefaultWaterNeedProfile implements WaterNeedProfile
{
    public static final DefaultWaterNeedProfile INSTANCE = new DefaultWaterNeedProfile();

    private DefaultWaterNeedProfile()
    {
    }

    public double baseDrain(SavannahAnimal animal, HydrationPolicy policy)
    {
        return policy.getBaseDrain();
    }
}
