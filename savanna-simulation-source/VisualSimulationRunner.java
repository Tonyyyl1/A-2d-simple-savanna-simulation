/**
 * Visual demonstration runner. It opens both the simulation window and the
 * live recording window, then generates the step report at the end.
 */
public class VisualSimulationRunner
{
    private static final int DEFAULT_STEPS = 200000;

    public static void main(String[] args)
    {
        int steps = DEFAULT_STEPS;
        if(args.length > 0) {
            steps = Integer.parseInt(args[0]);
        }

        Simulator simulator = new Simulator(true);
        simulator.setVerbose(false);
        simulator.runVisualSimulation(steps);
        System.out.println(simulator.getSummary());
    }
}
