package savanna;

import java.util.HashMap;
import java.util.Map;

/**
 * Open-addressing {@code int -> int} counter (plan v3.2 A-3c). Entries whose
 * count reaches zero are removed, so {@link #size()} equals the number of keys
 * with a positive count, like the boxed {@code HashMap} it replaces. Keys are
 * mixed with a multiplicative hash because the packed heatmap keys differ only
 * in high and middle bits.
 */
final class IntCounter
{
    private int[] keys = new int[64];
    private int[] counts = new int[64];
    private int size;

    private static int slotOf(int key, int mask)
    {
        return (key * 0x9E3779B9) >>> 7 & mask;
    }

    int get(int key)
    {
        int mask = keys.length - 1;
        for(int slot = slotOf(key, mask);; slot = (slot + 1) & mask) {
            if(counts[slot] == 0) return 0;
            if(keys[slot] == key) return counts[slot];
        }
    }

    void add(int key, int amount)
    {
        int mask = keys.length - 1;
        int slot = slotOf(key, mask);
        for(;; slot = (slot + 1) & mask) {
            if(counts[slot] == 0) break;
            if(keys[slot] == key) {
                counts[slot] += amount;
                return;
            }
        }
        if(amount == 0) return;
        keys[slot] = key;
        counts[slot] = amount;
        if(++size * 2 > keys.length) grow();
    }

    /** Subtracts one; the entry disappears when the count would reach zero. */
    void decrement(int key)
    {
        int mask = keys.length - 1;
        for(int slot = slotOf(key, mask);; slot = (slot + 1) & mask) {
            if(counts[slot] == 0) return;
            if(keys[slot] == key) {
                if(counts[slot] <= 1) removeAt(slot);
                else counts[slot]--;
                return;
            }
        }
    }

    private void removeAt(int slot)
    {
        int mask = keys.length - 1;
        counts[slot] = 0;
        size--;
        int hole = slot;
        for(int next = (slot + 1) & mask; counts[next] != 0; next = (next + 1) & mask) {
            int home = slotOf(keys[next], mask);
            // Move the entry into the hole when its home is not strictly inside (hole, next].
            boolean inside = hole <= next ? (hole < home && home <= next)
                                          : (hole < home || home <= next);
            if(!inside) {
                keys[hole] = keys[next];
                counts[hole] = counts[next];
                counts[next] = 0;
                hole = next;
            }
        }
    }

    private void grow()
    {
        int[] oldKeys = keys;
        int[] oldCounts = counts;
        keys = new int[oldKeys.length * 2];
        counts = new int[oldKeys.length * 2];
        int mask = keys.length - 1;
        for(int index = 0; index < oldKeys.length; index++) {
            if(oldCounts[index] == 0) continue;
            int slot = slotOf(oldKeys[index], mask);
            while(counts[slot] != 0) slot = (slot + 1) & mask;
            keys[slot] = oldKeys[index];
            counts[slot] = oldCounts[index];
        }
    }

    int size()
    {
        return size;
    }

    int max()
    {
        int max = 0;
        for(int count : counts) if(count > max) max = count;
        return max;
    }

    void clear()
    {
        java.util.Arrays.fill(counts, 0);
        size = 0;
    }

    Map<Integer, Integer> toMap()
    {
        Map<Integer, Integer> result = new HashMap<>();
        for(int index = 0; index < keys.length; index++) {
            if(counts[index] != 0) result.put(keys[index], counts[index]);
        }
        return result;
    }
}
