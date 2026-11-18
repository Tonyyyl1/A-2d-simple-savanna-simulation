package savanna;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;

/**
 * Read-only R2R diagnostic for the legacy mixed-geometry failure mode.
 *
 * <p>It replaces the simulator's step-start field with an equivalent field
 * that observes every spatial query. A query is incoherent when the field
 * maps an animal to one cell but that same animal reports another location.
 * The observer neither changes query results nor accesses any random source.</p>
 */
public final class A1aInteractionGeometryCoherenceProbe
{
    private A1aInteractionGeometryCoherenceProbe() {}

    public static void main(String[] args) throws ReflectiveOperationException
    {
        int steps = argument(args, 0, 300);
        int interval = argument(args, 1, 100);
        long ecologySeed = longArgument(args, 2, 2111L);
        long terrainSeed = longArgument(args, 3, 20260629L);
        if(steps <= 0 || interval <= 0) {
            throw new IllegalArgumentException("steps and interval must be positive");
        }

        Randomizer.reset();
        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(ecologySeed).terrainSeed(terrainSeed).build();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        GeometryLedger ledger = new GeometryLedger();
        replaceField(simulator, ledger);

        System.out.println("A1A_INTERACTION_GEOMETRY_COHERENCE" +
            " ecologySeed=" + ecologySeed + " terrainSeed=" + terrainSeed +
            " steps=" + steps + " interval=" + interval);
        for(int index = 1; index <= steps && simulator.getField().isViable(); index++) {
            simulator.simulateOneStep();
            if(index < steps && simulator.getField().isViable()) {
                replaceField(simulator, ledger);
            }
            if(index % interval == 0 || index == steps ||
               !simulator.getField().isViable()) {
                System.out.println("A1A_INTERACTION_GEOMETRY_CHECKPOINT step=" +
                    simulator.getStep() + " queryCalls=" + ledger.queryCalls +
                    " checkedOccupants=" + ledger.checkedOccupants +
                    " incoherentOccupants=" + ledger.incoherentOccupants +
                    " incoherentQueries=" + ledger.incoherentQueries);
            }
        }
        System.out.println("A1A_INTERACTION_GEOMETRY_COHERENCE PASS completed=" +
            simulator.getStep() + " viable=" + simulator.getField().isViable() +
            " incoherentOccupants=" + ledger.incoherentOccupants);
    }

    private static void replaceField(Simulator simulator, GeometryLedger ledger)
        throws ReflectiveOperationException
    {
        savanna.Field original = simulator.getField();
        ObservingField observing = new ObservingField(original.getDepth(),
            original.getWidth(), ledger);
        for(Animal animal : original.getAnimals()) {
            if(animal.isAlive()) {
                observing.placeAnimal(animal, animal.getLocation());
            }
        }
        Field simulatorField = Simulator.class.getDeclaredField("field");
        simulatorField.setAccessible(true);
        simulatorField.set(simulator, observing);
    }

    private static int argument(String[] args, int index, int fallback)
    {
        return args.length > index ? Integer.parseInt(args[index]) : fallback;
    }

    private static long longArgument(String[] args, int index, long fallback)
    {
        return args.length > index ? Long.parseLong(args[index]) : fallback;
    }

    private static final class GeometryLedger
    {
        private long queryCalls;
        private long checkedOccupants;
        private long incoherentOccupants;
        private long incoherentQueries;
    }

    private static final class ObservingField extends savanna.Field
    {
        private final GeometryLedger ledger;

        private ObservingField(int depth, int width, GeometryLedger ledger)
        {
            super(depth, width);
            this.ledger = ledger;
        }

        @Override
        public List<Location> getLocationsWithin(Location location, int range,
                                                 Random random)
        {
            List<Location> locations = super.getLocationsWithin(location, range,
                random);
            ledger.queryCalls++;
            boolean queryIncoherent = false;
            for(Location candidate : locations) {
                Animal occupant = getAnimalAt(candidate);
                if(occupant == null || !occupant.isAlive()) {
                    continue;
                }
                ledger.checkedOccupants++;
                if(!candidate.equals(occupant.getLocation())) {
                    ledger.incoherentOccupants++;
                    queryIncoherent = true;
                }
            }
            if(queryIncoherent) {
                ledger.incoherentQueries++;
            }
            return locations;
        }
    }
}
