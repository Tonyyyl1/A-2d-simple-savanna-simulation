package savanna;

/** Closed command vocabulary for the birth-resource transaction reducer. */
public enum BirthResourceCommandType
{
    PLAN,
    RESERVE_CONCEPTION,
    COMMIT_ORDINAL,
    KEEP_PENDING,
    RECORD_PREGNANCY_LOSS,
    CLOSE
}
