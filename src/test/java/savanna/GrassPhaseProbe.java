package savanna;

/** A0 read-only baseline probe for the legacy single-layer grass model. */
public final class GrassPhaseProbe
{
    private GrassPhaseProbe() { }

    public static void main(String[] args)
    {
        int steps = args.length == 0 ? 100 : Integer.parseInt(args[0]);
        Simulator simulator = new Simulator(SimulationConfig.default3x(), false);
        simulator.setVerbose(false);
        System.out.println("GRASS_PHASE schema=" + SimulationFingerprint.SCHEMA_VERSION +
            " step=0 average=" + simulator.getContext().getFoodSystem().getAverageFoodLevel() +
            " hash=" + SimulationFingerprint.capture(simulator));
        for(int index = 0; index < steps && simulator.getField().isViable(); index++) {
            simulator.simulateOneStep();
        }
        System.out.println("GRASS_PHASE step=" + simulator.getStep() + " average=" +
            simulator.getContext().getFoodSystem().getAverageFoodLevel() + " hash=" +
            SimulationFingerprint.capture(simulator));
    }
}
