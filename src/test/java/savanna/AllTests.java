package savanna;

/**
 * Runs the default no-dependency test suite.
 */
public class AllTests
{
    public static void main(String[] args)
    {
        String mode = args.length == 0 ? "default" : args[0].toLowerCase();
        if(args.length > 1 || !isKnownMode(mode)) {
            System.err.println("Usage: AllTests [full|unit|core|system|water|frame|facts|integration-1000|v27-perf|v27-terrain|v27-ecology|v27-capacity-water]");
            System.exit(2);
        }
        boolean full = "full".equals(mode);
        TestSupport.startSuite(suiteName(mode));

        if("default".equals(mode) || full || "unit".equals(mode)) {
            SimulationUnitTests.run();
        }
        if("default".equals(mode) || full || "v27-perf".equals(mode)) {
            V27PerfTests.run();
        }
        if("default".equals(mode) || full || "v27-terrain".equals(mode)) {
            V27TerrainTests.run();
            TerrainReliefTests.run();
        }
        if("default".equals(mode) || full || "v27-ecology".equals(mode)) {
            V27EcologyTests.run();
            ChaseMovementTests.run();
            EcologyTargetsTests.run();
            M1ResourceTests.run();
        }
        if("default".equals(mode) || full || "v27-capacity-water".equals(mode)) {
            M2CapacityWaterTests.run();
        }
        if("default".equals(mode) || full || "core".equals(mode)) {
            IntCounterTests.run();
            CoreIdTests.run();
            RuntimeAuditTests.run();
            CoreFieldTests.run();
            FoodSemTests.run();
        }
        if("default".equals(mode) || full || "water".equals(mode)) {
            ObsWaterTests.run();
        }
        if("default".equals(mode) || full || "system".equals(mode)) {
            SimulationSystemTests.run();
        }
        if("default".equals(mode) || full || "frame".equals(mode)) {
            SimulationObservationTests.run();
            V24Frame1Tests.run();
            SimFrameTests.run();
            SimServerTests.run();
        }
        if("default".equals(mode) || full || "facts".equals(mode)) {
            A1aR2rD2Tests.run();
            L1FSpatialEcologyTests.run();
            Soc1aFactTests.run();
            A1bB0TransactionKernelTests.run();
            SpeciesParameterCardTests.run();
        }
        if("default".equals(mode) || full || "integration-1000".equals(mode)) {
            SimulationIntegrationTests.runDefault();
        }
        if(full) {
            SimulationIntegrationTests.runFull();
        }

        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    private static boolean isKnownMode(String mode)
    {
        return "default".equals(mode) || "full".equals(mode) ||
               "unit".equals(mode) || "core".equals(mode) ||
               "system".equals(mode) || "water".equals(mode) ||
               "frame".equals(mode) || "facts".equals(mode) ||
               "integration-1000".equals(mode) || isV27Mode(mode);
    }

    /** Groups are added only together with real tests (plan v3.0 §4 M0-1). */
    private static boolean isV27Mode(String mode)
    {
        return "v27-perf".equals(mode) ||
               "v27-terrain".equals(mode) ||
               "v27-ecology".equals(mode) ||
               "v27-capacity-water".equals(mode);
    }

    private static String suiteName(String mode)
    {
        if("full".equals(mode)) return "Savanna full test suite";
        if("default".equals(mode)) return "Savanna default test suite";
        return "Savanna development group: " + mode;
    }
}
