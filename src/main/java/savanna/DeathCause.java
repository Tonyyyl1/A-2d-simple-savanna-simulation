package savanna;

/** Stable cause recorded whenever a simulation animal dies. */
public enum DeathCause
{
    NONE("None / 无"),
    AGE("Age / 衰老"),
    STARVATION("Starvation / 饥饿"),
    DEHYDRATION("Dehydration / 脱水"),
    DISEASE("Disease / 疾病"),
    PREDATION("Predation / 捕食"),
    POSITION_CONFLICT("Position conflict / 位置冲突"),
    UNKNOWN("Unknown / 未知");

    private final String displayText;

    DeathCause(String displayText)
    {
        this.displayText = displayText;
    }

    public String getDisplayText()
    {
        return displayText;
    }
}
