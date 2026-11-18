package savanna;

/**
 * Visual demonstration runner. It opens both the simulation window and the
 * live recording window, then generates the step report at the end.
 */
public class VisualSimulationRunner
{
    private static final int DEFAULT_STEPS = 200000;

    public static void main(String[] args)
    {
        RuntimeClasspathGuard.verifyOrThrow();
        if(args != null && args.length == 1 && "--verify-only".equals(args[0])) {
            System.out.println("Canonical candidate check PASS: " +
                               BuildIdentity.current().detailedSummary());
            return;
        }
        StartupConfigDialog.StartupOptions options =
            StartupConfigDialog.showDialogOrDefault();
        if(options.isCancelled()) {
            System.exit(0);
        }
        int steps = args.length > 0 ? Integer.parseInt(args[0]) :
            options.getSteps();
        Simulator simulator = new Simulator(options.getConfig(), true);
        simulator.setVerbose(false);
        simulator.setStepDelay(options.getStepDelay());
        System.out.println("Runtime identity: " +
                           simulator.getContext().getRenderProvenance().detailedSummary());
        System.out.println("Starting visual simulation: " +
                           options.getConfig().describe());
        simulator.simulate(steps);
        System.out.println(simulator.getSummary());
    }
}
