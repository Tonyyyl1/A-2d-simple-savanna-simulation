package savanna;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** v3.2 A-3c: the primitive counter behaves like the boxed map it replaced. */
public final class IntCounterTests
{
    private IntCounterTests()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("IntCounter");
        run();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    public static void run()
    {
        TestSupport.startGroup("A-3c IntCounter");
        Random random = new Random(7L);
        IntCounter counter = new IntCounter();
        Map<Integer, Integer> reference = new HashMap<>();
        boolean same = true;
        for(int operation = 0; operation < 200_000; operation++) {
            // Keys shaped like heatmap packs: high and middle bits only.
            int key = (random.nextInt(60) << 20) | (random.nextInt(90) << 8) |
                (random.nextInt(8) << 4) | random.nextInt(6);
            if(random.nextInt(10) < 6) {
                counter.add(key, 1);
                reference.merge(key, 1, Integer::sum);
            }
            else {
                counter.decrement(key);
                Integer prior = reference.get(key);
                if(prior != null) {
                    if(prior <= 1) reference.remove(key);
                    else reference.put(key, prior - 1);
                }
            }
            if(operation % 997 == 0 && counter.get(key) != reference.getOrDefault(key, 0)) {
                same = false;
            }
        }
        TestSupport.assertTrue("A3C IntCounter point reads match during mixed updates", same);
        TestSupport.assertEquals("A3C IntCounter final size matches", reference.size(),
            counter.size());
        TestSupport.assertEquals("A3C IntCounter final contents match", reference,
            counter.toMap());
        int max = 0;
        for(int value : reference.values()) max = Math.max(max, value);
        TestSupport.assertEquals("A3C IntCounter max matches", max, counter.max());
        counter.clear();
        TestSupport.assertEquals("A3C IntCounter clear empties", 0, counter.size());
        TestSupport.finishGroup("A3C_INT_COUNTER");
    }
}
