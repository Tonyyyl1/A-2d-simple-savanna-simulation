package savanna;

/**
 * Mutable production target owned by exactly one animal.  It deliberately
 * holds no RNG and is updated only by an already-selected production action.
 */
public final class ProductionTargetState
{
    public enum Type { NONE, HUNT_REGION, HUNT_PREY, GRAZE_REGION }
    public enum Status { NONE, ACTIVE, COMPLETED, BLOCKED, NO_ROUTE, TIMED_OUT, SATIATED, TARGET_DEAD }

    private Type type = Type.NONE;
    private Status status = Status.NONE;
    private Status lastInvalidation = Status.NONE;
    private long targetAnimalId = -1L;
    private Location targetRegion;
    private int establishedStep = -1;
    private int lastProgressStep = -1;
    /** B-2 hunger search direction: each of -1, 0, 1, not both zero; start step -1 when none. */
    private int searchDRow;
    private int searchDCol;
    private int searchStartStep = -1;

    public Type getType() { return type; }
    public Status getStatus() { return status; }
    public Status getLastInvalidation() { return lastInvalidation; }
    public long getTargetAnimalId() { return targetAnimalId; }
    public Location getTargetRegion()
    {
        return targetRegion == null ? null : new Location(targetRegion.row(), targetRegion.col());
    }
    public int getEstablishedStep() { return establishedStep; }
    public int getLastProgressStep() { return lastProgressStep; }
    public boolean isActive() { return status == Status.ACTIVE; }

    void begin(Type requestedType, Location region, int step)
    {
        if(requestedType == null || requestedType == Type.NONE || step < 0) {
            throw new IllegalArgumentException("active target type, region and step are required");
        }
        type = requestedType;
        status = Status.ACTIVE;
        targetAnimalId = -1L;
        targetRegion = copy(region);
        establishedStep = step;
        lastProgressStep = step;
    }

    void acquirePrey(long preyId, Location region, int step)
    {
        if(preyId < 0L || step < 0) throw new IllegalArgumentException(
            "prey id and step are required");
        if(!isActive() || (type != Type.HUNT_REGION && type != Type.HUNT_PREY)) {
            begin(Type.HUNT_PREY, region, step);
        }
        type = Type.HUNT_PREY;
        targetAnimalId = preyId;
        targetRegion = copy(region);
        lastProgressStep = step;
    }

    void progress(Location region, int step)
    {
        if(!isActive() || step < 0) return;
        if(region != null) targetRegion = copy(region);
        lastProgressStep = step;
    }

    void invalidate(Status reason)
    {
        if(reason == null || reason == Status.NONE || reason == Status.ACTIVE) {
            throw new IllegalArgumentException("an explicit terminal target reason is required");
        }
        status = reason;
        lastInvalidation = reason;
    }

    boolean hasSearchDirection() { return searchStartStep >= 0; }
    int getSearchDRow() { return searchDRow; }
    int getSearchDCol() { return searchDCol; }
    int getSearchStartStep() { return searchStartStep; }

    void startSearch(int dRow, int dCol, int step)
    {
        if((dRow == 0 && dCol == 0) || Math.abs(dRow) > 1 || Math.abs(dCol) > 1 || step < 0) {
            throw new IllegalArgumentException("search direction must be a non-zero unit step");
        }
        searchDRow = dRow;
        searchDCol = dCol;
        searchStartStep = step;
    }

    void clearSearch()
    {
        searchDRow = 0;
        searchDCol = 0;
        searchStartStep = -1;
    }

    boolean targets(long animalId)
    {
        return isActive() && type == Type.HUNT_PREY && targetAnimalId == animalId;
    }

    private static Location copy(Location value)
    {
        return value;
    }
}
