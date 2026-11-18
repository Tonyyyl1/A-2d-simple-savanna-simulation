package savanna;

/** Named result of one legacy-compatible predator HUNT evaluation. */
public enum HuntOutcome
{
    INELIGIBLE,
    DECLINED,
    NO_LOCAL_REACHABLE_PREY,
    OUT_OF_CONTACT,
    FAILED_ATTEMPT,
    SUCCESS
}
