package savanna;

/** Defines species-aware water requirements for a hydration policy. */
public interface WaterNeedProfile
{
    double baseDrain(SavannahAnimal animal, HydrationPolicy policy);
}
