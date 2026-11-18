package savanna;

/** Stable, inspectable reason for a proposed primary action. */
public enum BehaviorReason
{
    CRITICAL_DEHYDRATION(1000),
    WATER_DEPARTURE(1100),
    CRITICAL_FOOD(900),
    /** Plan v3.0 M2-3: prey fleeing a sensed predator. */
    THREAT_AVOIDANCE(850),
    /** Plan v3.0 M2-3: a predator leaving a crowded predator zone. */
    EGRESS(450),
    THIRST(800),
    FOOD(700),
    REST(600),
    ILLNESS_RECOVERY(600),
    BLOCKED(600),
    BREEDING(500),
    ORDINARY_MOVEMENT(400);

    private final int priority;

    BehaviorReason(int priority)
    {
        this.priority = priority;
    }

    public int getPriority()
    {
        return priority;
    }
}
