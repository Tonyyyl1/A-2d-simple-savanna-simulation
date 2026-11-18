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
            System.err.println("Usage: AllTests [full|unit|core|system|water|frame|facts|integration-1000|v27-perf|v27-terrain|v27-ecology|v27-capacity-water|v27-ui|v27-config|v27-history|v27-checkpoint|v27-contamination]");
            System.exit(2);
        }
        if(isReservedV27Mode(mode) && !"v27-perf".equals(mode) &&
           !"v27-terrain".equals(mode) && !"v27-ecology".equals(mode)) {
            System.err.println("V2.7 test group is registered but has no production-entry tests yet: " + mode);
            System.exit(1);
        }
        boolean full = "full".equals(mode);
        TestSupport.startSuite(suiteName(mode));

        if("default".equals(mode) || full || "unit".equals(mode)) {
            SimulationUnitTests.run();
        }
        if("v27-perf".equals(mode)) {
            V27PerfTests.run();
        }
        if("v27-terrain".equals(mode)) {
            V27TerrainTests.run();
        }
        if("v27-ecology".equals(mode)) {
            V27EcologyTests.run();
        }
        if("default".equals(mode) || full || "core".equals(mode)) {
            CoreIdTests.run();
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
            V24Obs1aTests.run();
            V24Obs1bTests.run();
        }
        if("default".equals(mode) || full || "facts".equals(mode)) {
            A1aR2rD2Tests.run();
            L1FSpatialEcologyTests.run();
            Soc1aFactTests.run();
            Pf1aFactTests.run();
            A1bB0TransactionKernelTests.run();
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
               "integration-1000".equals(mode) || isReservedV27Mode(mode);
    }

    private static boolean isReservedV27Mode(String mode)
    {
        return "v27-perf".equals(mode) ||
               "v27-terrain".equals(mode) ||
               "v27-ecology".equals(mode) ||
               "v27-capacity-water".equals(mode) ||
               "v27-ui".equals(mode) ||
               "v27-config".equals(mode) ||
               "v27-history".equals(mode) ||
               "v27-checkpoint".equals(mode) ||
               "v27-contamination".equals(mode);
    }

    private static String suiteName(String mode)
    {
        if("full".equals(mode)) return "Savanna full test suite";
        if("default".equals(mode)) return "Savanna default test suite";
        return "Savanna development group: " + mode;
    }
}
