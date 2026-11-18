package savanna;

/**
 * Named random-operation shape for one HUNT evaluation.
 *
 * <p>This intentionally records requested operations, not implementation-
 * specific {@link java.util.Random} primitive-call counts. In particular, a
 * location shuffle is represented by its request and input size.</p>
 */
public final class HuntRandomLedger
{
    private static final HuntRandomLedger NONE = new HuntRandomLedger(false,
        false, 0, false);

    private final boolean discretionaryDraw;
    private final boolean locationShuffleRequested;
    private final int locationShuffleInputSize;
    private final boolean successDraw;

    public HuntRandomLedger(boolean discretionaryDraw,
                            boolean locationShuffleRequested,
                            int locationShuffleInputSize,
                            boolean successDraw)
    {
        this.discretionaryDraw = discretionaryDraw;
        this.locationShuffleRequested = locationShuffleRequested;
        this.locationShuffleInputSize = Math.max(0, locationShuffleInputSize);
        this.successDraw = successDraw;
    }

    public static HuntRandomLedger none()
    {
        return NONE;
    }

    public boolean hasDiscretionaryDraw() { return discretionaryDraw; }

    public boolean isLocationShuffleRequested()
    {
        return locationShuffleRequested;
    }

    public int getLocationShuffleInputSize()
    {
        return locationShuffleInputSize;
    }

    public boolean hasSuccessDraw() { return successDraw; }
}
