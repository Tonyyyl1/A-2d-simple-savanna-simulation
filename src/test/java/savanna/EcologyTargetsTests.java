package savanna;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** v3.2 C-0: target pyramid, normalized balance and founders by target count. */
public final class EcologyTargetsTests
{
    private EcologyTargetsTests()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("C-0 ecology targets");
        run();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    public static void run()
    {
        TestSupport.startGroup("C-0 ecology targets");
        testReferencePassableCells();
        testNormalizedBalance();
        testFoundersEqualTargets();
        testRemovedSpeciesAreNotPlaced();
        TestSupport.finishGroup("C0_TARGETS");
    }

    private static void testReferencePassableCells()
    {
        SimulationContext context = new SimulationContext(240, 360, SimulationConfig.builder().build());
        TestSupport.assertEquals("C0 reference passable cell constant matches the default map",
            EcologyTargets.REFERENCE_PASSABLE_CELLS, passable(context.getTerrainMap()));
    }

    private static void testNormalizedBalance()
    {
        Set<String> all = new LinkedHashSet<>(java.util.Arrays.asList("Lion", "Cheetah", "Zebra",
            "Buffalo", "Gazelle"));
        Map<String, Double> onTarget = new LinkedHashMap<>();
        for(String species : all) onTarget.put(species, EcologyTargets.target(species));
        TestSupport.assertEquals("C0 balance is 1 when every species sits on its target",
            Double.valueOf(1.0), Double.valueOf(EcologyTargets.normalizedBalance(onTarget, all, 1.0)));
        Map<String, Double> skew = new LinkedHashMap<>(onTarget);
        skew.put("Lion", 300.0);
        skew.put("Gazelle", 1500.0);
        TestSupport.assertEquals("C0 balance is largest ratio over smallest ratio",
            Double.valueOf(6.0), Double.valueOf(EcologyTargets.normalizedBalance(skew, all, 1.0)));
        Map<String, Double> missing = new LinkedHashMap<>(onTarget);
        missing.put("Cheetah", 0.0);
        TestSupport.assertTrue("C0 a zero mean gives infinity",
            Double.isInfinite(EcologyTargets.normalizedBalance(missing, all, 1.0)));
        Set<String> herbivores = new LinkedHashSet<>(java.util.Arrays.asList("Zebra", "Buffalo", "Gazelle"));
        TestSupport.assertEquals("C0 non-participants are ignored",
            Double.valueOf(1.0), Double.valueOf(EcologyTargets.normalizedBalance(missing, herbivores, 1.0)));
    }

    private static void testFoundersEqualTargets()
    {
        Simulator simulator = new Simulator(SimulationConfig.builder().randomSeed(1111L).build(), false);
        simulator.setVerbose(false);
        Map<String, Integer> counts = simulator.getField().countLivingBySpecies();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            String species = factory.getProfile().getName();
            TestSupport.assertEquals("C0 founders of " + species + " equal the target",
                (int)EcologyTargets.target(species), counts.getOrDefault(species, 0).intValue());
        }
    }

    private static void testRemovedSpeciesAreNotPlaced()
    {
        EcologyParameters.override(EcologyParameters.defaults().with("init.lion=0,init.cheetah=0"));
        try {
            Simulator simulator = new Simulator(SimulationConfig.builder().randomSeed(1111L).build(), false);
            simulator.setVerbose(false);
            Map<String, Integer> counts = simulator.getField().countLivingBySpecies();
            TestSupport.assertEquals("C0 init.lion=0 places no Lion", 0, counts.getOrDefault("Lion", 0).intValue());
            TestSupport.assertEquals("C0 init.cheetah=0 places no Cheetah", 0,
                counts.getOrDefault("Cheetah", 0).intValue());
            TestSupport.assertEquals("C0 Gazelle founders are unchanged", 3000,
                counts.getOrDefault("Gazelle", 0).intValue());
        }
        finally {
            EcologyParameters.override(null);
        }
    }

    private static int passable(TerrainMap terrain)
    {
        int count = 0;
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                if(terrain.isPassable(new Location(row, col))) count++;
            }
        }
        return count;
    }
}
