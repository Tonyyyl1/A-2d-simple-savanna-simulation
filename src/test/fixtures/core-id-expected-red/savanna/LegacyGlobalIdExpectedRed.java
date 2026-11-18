package savanna;

import java.util.HashSet;
import java.util.Set;

/** Reproduces the removed process-global reset collision. */
public final class LegacyGlobalIdExpectedRed
{
    private static long nextId = 1L;

    private static long allocate() { return nextId++; }
    private static void reset() { nextId = 1L; }

    public static void main(String[] args)
    {
        Set<Long> firstSimulatorLiving = new HashSet<>();
        firstSimulatorLiving.add(Long.valueOf(allocate()));
        firstSimulatorLiving.add(Long.valueOf(allocate()));
        firstSimulatorLiving.add(Long.valueOf(allocate()));
        reset();
        allocate();
        allocate();
        long firstSimulatorChild = allocate();
        if(firstSimulatorLiving.contains(Long.valueOf(firstSimulatorChild))) {
            System.err.println("LEGACY_CORE_ID_COLLISION child=" +
                firstSimulatorChild);
            System.exit(1);
        }
        System.out.println("legacy global identity unexpectedly avoided collision");
    }
}
