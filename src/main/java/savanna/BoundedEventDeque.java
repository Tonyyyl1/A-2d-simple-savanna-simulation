package savanna;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A fixed-capacity FIFO buffer with O(1) eviction of its oldest item.
 *
 * <p>The buffer deliberately exposes only snapshots.  It is used for
 * best-effort recent-event inspection and must never be treated as the source
 * of complete interval statistics.</p>
 */
public final class BoundedEventDeque<T>
{
    private final int capacity;
    private final ArrayDeque<T> entries;

    public BoundedEventDeque(int capacity)
    {
        if(capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        entries = new ArrayDeque<>(capacity);
    }

    public void addLast(T item)
    {
        if(item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        if(entries.size() == capacity) {
            entries.removeFirst();
        }
        entries.addLast(item);
    }

    public int size()
    {
        return entries.size();
    }

    /** Return oldest-to-newest order, matching the former recent-events API. */
    public List<T> snapshot()
    {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }
}
