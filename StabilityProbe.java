/**
 * Prints population snapshots during a headless simulation.
 */
public class StabilityProbe
{
    public static void main(String[] args)
    {
        int steps = 1000;
        int interval = 100;
        if(args.length > 0) {
            steps = Integer.parseInt(args[0]);
        }
        if(args.length > 1) {
            interval = Integer.parseInt(args[1]);
        }

        Simulator simulator = new Simulator(false);
        simulator.setVerbose(false);
        System.out.println(simulator.getSummary());
        for(int index = 1; index <= steps && simulator.getField().isViable(); index++) {
            simulator.simulateOneStep();
            if(index % interval == 0 || !simulator.getField().isViable()) {
                System.out.println(simulator.getSummary());
            }
        }
    }
}
