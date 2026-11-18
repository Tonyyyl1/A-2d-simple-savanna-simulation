package savanna;

/** Synthetic stale non-core class used only by the V3 mixed-classpath red gate. */
public final class ResourceVector
{
    private final long staleMarker = -1L;

    public long staleMarker()
    {
        return staleMarker;
    }
}
