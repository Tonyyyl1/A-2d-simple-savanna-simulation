/**
 * Runs the default no-dependency test suite.
 */
public class AllTests
{
    public static void main(String[] args)
    {
        boolean full = args.length > 0 && "full".equalsIgnoreCase(args[0]);
        TestSupport.startSuite(full ? "Savanna full test suite"
                                    : "Savanna default test suite");

        SimulationUnitTests.run();
        SimulationSystemTests.run();
        SimulationIntegrationTests.runDefault();
        if(full) {
            SimulationIntegrationTests.runFull();
        }

        TestSupport.finishSuite();
        if(TestSupport.hasFailures()) {
            System.exit(1);
        }
    }
}
