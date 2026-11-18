package savanna;

/** Simulator-owned, deterministic, thread-safe animal identity scope. */
public final class AnimalIdentityAllocator
{
    private long nextId = 1L;

    public synchronized long allocate()
    {
        if(nextId == Long.MAX_VALUE) {
            throw new IllegalStateException("animal identity space exhausted");
        }
        return nextId++;
    }

    public synchronized void reset()
    {
        nextId = 1L;
    }

    synchronized long peekNext()
    {
        return nextId;
    }
}
