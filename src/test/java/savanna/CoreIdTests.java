package savanna;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** CORE-ID simulator-owned identity invariants. */
public final class CoreIdTests
{
    private CoreIdTests() {}

    public static void run()
    {
        TestSupport.startGroup("CORE-ID simulator-owned identity");
        noProcessGlobalAnimalIdentity();
        independentScopesAndDescendants();
        concurrentAllocation();
        interleavedSimulatorsAndReset();
        eventAndLivingIdentityUniqueness();
        noPermanentIdentityCache();
        TestSupport.finishGroup();
    }

    private static void noProcessGlobalAnimalIdentity()
    {
        boolean staticMutableLong = false;
        for(Field field : Animal.class.getDeclaredFields()) {
            if(Modifier.isStatic(field.getModifiers()) &&
               field.getType() == long.class &&
               !Modifier.isFinal(field.getModifiers())) {
                staticMutableLong = true;
            }
        }
        boolean resetMethod = false;
        for(Method method : Animal.class.getDeclaredMethods()) {
            if("resetIds".equals(method.getName())) resetMethod = true;
        }
        TestSupport.assertFalse("CORE-ID Animal has no process-global counter",
            staticMutableLong);
        TestSupport.assertFalse("CORE-ID Animal has no global reset hook",
            resetMethod);
    }

    private static void independentScopesAndDescendants()
    {
        AnimalIdentityAllocator first = new AnimalIdentityAllocator();
        AnimalIdentityAllocator second = new AnimalIdentityAllocator();
        Lion a1 = new Lion(false, new Location(0, 0),
            new java.util.Random(1L), first);
        Lion a2 = new Lion(false, new Location(0, 1),
            new java.util.Random(2L), first);
        Gazelle b1 = new Gazelle(false, new Location(1, 0),
            new java.util.Random(3L), second);
        second.reset();
        Gazelle bAfterReset = new Gazelle(false, new Location(1, 1),
            new java.util.Random(4L), second);
        SavannahAnimal aChild = a2.createChild(new Location(0, 2));
        TestSupport.assertEquals("CORE-ID first scope begins at one",
            Long.valueOf(1L), Long.valueOf(a1.getId()));
        TestSupport.assertEquals("CORE-ID first scope remains monotonic",
            Long.valueOf(2L), Long.valueOf(a2.getId()));
        TestSupport.assertEquals("CORE-ID second scope begins independently",
            Long.valueOf(1L), Long.valueOf(b1.getId()));
        TestSupport.assertEquals("CORE-ID second reset is local",
            Long.valueOf(1L), Long.valueOf(bAfterReset.getId()));
        TestSupport.assertEquals("CORE-ID descendant inherits parent scope",
            Long.valueOf(3L), Long.valueOf(aChild.getId()));
    }

    private static void concurrentAllocation()
    {
        AnimalIdentityAllocator allocator = new AnimalIdentityAllocator();
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        int threads = 6;
        int each = 1000;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        Thread[] workers = new Thread[threads];
        for(int index = 0; index < threads; index++) {
            workers[index] = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    for(int count = 0; count < each; count++) {
                        ids.add(Long.valueOf(allocator.allocate()));
                    }
                }
                catch(Throwable throwable) {
                    failure.compareAndSet(null, throwable);
                }
            }, "core-id-" + index);
            workers[index].start();
        }
        try {
            ready.await();
            start.countDown();
            for(Thread worker : workers) worker.join();
        }
        catch(InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CORE-ID concurrency interrupted",
                exception);
        }
        TestSupport.assertEquals("CORE-ID concurrent allocation has no error",
            null, failure.get());
        TestSupport.assertEquals("CORE-ID concurrent IDs are unique",
            threads * each, ids.size());
        TestSupport.assertTrue("CORE-ID concurrent IDs cover one",
            ids.contains(Long.valueOf(1L)));
        TestSupport.assertTrue("CORE-ID concurrent IDs cover final ordinal",
            ids.contains(Long.valueOf(threads * each)));
        TestSupport.assertEquals("CORE-ID next ordinal follows concurrent range",
            Long.valueOf(threads * each + 1L),
            Long.valueOf(allocator.peekNext()));
    }

    private static void interleavedSimulatorsAndReset()
    {
        Simulator first = new Simulator(20, 30, false,
            SimulationConfig.baseline());
        Simulator second = new Simulator(16, 22, false,
            SimulationConfig.baseline());
        assertLivingUnique("CORE-ID first simulator living IDs unique", first);
        assertLivingUnique("CORE-ID second simulator living IDs unique", second);
        long firstMax = maximumLivingId(first);
        long secondMax = maximumLivingId(second);
        SavannahAnimal firstParent = firstLiving(first);
        SavannahAnimal secondParent = firstLiving(second);
        second.reset();
        SavannahAnimal firstChild = firstParent.createChild(new Location(0, 0));
        TestSupport.assertEquals("CORE-ID B reset cannot rewind A descendants",
            Long.valueOf(firstMax + 1L), Long.valueOf(firstChild.getId()));
        first.reset();
        SavannahAnimal secondChild = secondParent.createChild(new Location(0, 0));
        TestSupport.assertEquals("CORE-ID A reset cannot rewind B descendants",
            Long.valueOf(secondMax + 1L), Long.valueOf(secondChild.getId()));
        TestSupport.assertEquals("CORE-ID reset simulator restarts its own scope",
            Long.valueOf(1L), Long.valueOf(minimumLivingId(first)));
        assertLivingUnique("CORE-ID reset living IDs remain unique", first);
    }

    private static void eventAndLivingIdentityUniqueness()
    {
        Simulator simulator = new Simulator(24, 36, false,
            SimulationConfig.baseline());
        Set<Long> observed = new HashSet<>();
        for(Animal animal : simulator.getField().getAnimals()) {
            if(!observed.add(Long.valueOf(animal.getId()))) {
                throw new IllegalStateException("duplicate initial animal ID");
            }
        }
        AtomicReference<Long> duplicateBirth = new AtomicReference<>();
        simulator.addEventListener(event -> {
            if(event.type == SimulationEvent.EventType.BIRTH &&
               !observed.add(Long.valueOf(event.targetId))) {
                duplicateBirth.compareAndSet(null, Long.valueOf(event.targetId));
            }
        });
        simulator.setVerbose(false);
        simulator.simulate(100);
        TestSupport.assertEquals("CORE-ID birth event IDs never repeat", null,
            duplicateBirth.get());
        assertLivingUnique("CORE-ID post-run living IDs unique", simulator);
    }

    private static void noPermanentIdentityCache()
    {
        boolean cacheField = false;
        for(Field field : AnimalIdentityAllocator.class.getDeclaredFields()) {
            Class<?> type = field.getType();
            if(java.util.Collection.class.isAssignableFrom(type) ||
               java.util.Map.class.isAssignableFrom(type)) {
                cacheField = true;
            }
        }
        TestSupport.assertFalse("CORE-ID allocator retains no death ID cache",
            cacheField);
    }

    private static void assertLivingUnique(String name, Simulator simulator)
    {
        Set<Long> ids = new HashSet<>();
        boolean unique = true;
        for(Animal animal : simulator.getField().getAnimals()) {
            if(animal.isAlive() && !ids.add(Long.valueOf(animal.getId()))) {
                unique = false;
            }
        }
        TestSupport.assertTrue(name, unique);
    }

    private static long maximumLivingId(Simulator simulator)
    {
        long result = 0L;
        for(Animal animal : simulator.getField().getAnimals()) {
            if(animal.isAlive()) result = Math.max(result, animal.getId());
        }
        return result;
    }

    private static long minimumLivingId(Simulator simulator)
    {
        long result = Long.MAX_VALUE;
        for(Animal animal : simulator.getField().getAnimals()) {
            if(animal.isAlive()) result = Math.min(result, animal.getId());
        }
        return result;
    }

    private static SavannahAnimal firstLiving(Simulator simulator)
    {
        for(Animal animal : simulator.getField().getAnimals()) {
            if(animal instanceof SavannahAnimal && animal.isAlive()) {
                return (SavannahAnimal)animal;
            }
        }
        throw new IllegalStateException("simulator has no living animal");
    }
}
