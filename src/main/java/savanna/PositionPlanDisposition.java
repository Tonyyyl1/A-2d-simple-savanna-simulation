package savanna;

/** A plan either reserves a destination or rejects a provisional spawn token. */
public enum PositionPlanDisposition
{
    COMMITTED,
    REJECTED,

    /**
     * A step-start adult died during the sequential action phase. Its origin
     * is deliberately released before reservation resolution, so it has no
     * destination in this plan. This is distinct from a cancelled adult
     * claim, which remains an origin-protecting stay for R1 compatibility.
     */
    RELEASED
}
