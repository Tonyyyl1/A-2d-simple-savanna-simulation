package savanna;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only M3 diagnostic (plan A, 2026-10-23): per-species time series for one
 * headless run, to see why large grazers fall under thirst on. Usage:
 * {@code ThirstDiagnosticTool <ecologySeed> <steps> <off|on> [window] [--spread] [--params=key=value,...]}. Prints
 * one tab-separated line per window and species. The run uses the default
 * parameters and never changes behaviour: it listens to events and published
 * frame ledgers and reads animal state after each window. The optional
 * {@code --spread} is an experiment, not a model change: after construction it
 * gives the initial animals hydration spread evenly over 50-100% (from a
 * separate fixed-seed random, so the ecology stream is untouched) to test
 * whether the synchronized initial thirst causes the crash.
 *
 * <p>Columns: step, species, pop, adults, births, starve (starvation deaths),
 * pred (predation deaths), other (other deaths), foodPct (mean food as % of max),
 * zeroFood% (share at food 0), hydr (mean hydration %), thirsty% (share
 * thirsty), waterSupp% (share of the window's breeding proposals suppressed by
 * water priority), conceive (paid conceptions), drink / blocked / retarget
 * (water events), fill (mean local grass fill under the species' animals).</p>
 */
public final class ThirstDiagnosticTool
{
    private ThirstDiagnosticTool()
    {
    }

    public static void main(String[] args)
    {
        if(args.length < 3 || !("on".equals(args[2]) || "off".equals(args[2]))) {
            System.err.println("usage: ThirstDiagnosticTool <ecologySeed> <steps> <off|on> [window]");
            System.exit(2);
        }
        long seed = Long.parseLong(args[0]);
        int steps = Integer.parseInt(args[1]);
        boolean thirst = "on".equals(args[2]);
        int window = 50;
        boolean spread = false;
        for(int index = 3; index < args.length; index++) {
            if("--spread".equals(args[index])) spread = true;
            else if(args[index].startsWith("--params=")) {
                EcologyParameters.override(
                    EcologyParameters.defaults().with(args[index].substring(9)));
            }
            else window = Integer.parseInt(args[index]);
        }
        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(seed).thirstEnabled(thirst).build();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        if(spread) {
            java.util.Random spreadRandom = new java.util.Random(seed * 31L + 7L);
            for(Animal animal : simulator.getField().getAnimals()) {
                if(!(animal instanceof SavannahAnimal)) continue;
                TestSupport.setField(animal, "hydrationState", new AnimalHydrationState(
                    50.0 + spreadRandom.nextDouble() * 50.0, SavannahAnimal.MAX_HYDRATION));
            }
        }
        TerrainMap terrain = simulator.getContext().getTerrainMap();
        int[][] waterDistance = waterDistance(terrain);
        Window counters = new Window(waterDistance);
        simulator.getContext().addEventListener(counters);
        simulator.getContext().addStepFrameLedgerListener(counters);
        System.out.println("step\tspecies\tpop\tadults\tbirths\tstarve\tpred\tother\tfoodPct\t" +
            "zeroFood%\thydr\tthirsty%\twaterSupp%\tconceive\tdrink\tblocked\tretarget\tfill");
        for(int index = 1; index <= steps; index++) {
            simulator.simulateOneStep();
            if(index % 5 == 0) counters.sample(simulator.getField());
            if(index % window == 0) {
                report(simulator, counters, thirst);
                counters.reset();
            }
            if(!simulator.getField().isViable()) break;
        }
        counters.printProximity(thirst, simulator.getField(), waterDistance);
    }

    /** Chebyshev distance from every cell to the nearest waterhole cell. */
    private static int[][] waterDistance(TerrainMap terrain)
    {
        int depth = terrain.getDepth();
        int width = terrain.getWidth();
        int[][] distance = new int[depth][width];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                if(terrain.getTerrainAt(new Location(row, col)) == TerrainType.WATERHOLE) {
                    distance[row][col] = 0;
                    queue.add(new int[] {row, col});
                }
                else {
                    distance[row][col] = Integer.MAX_VALUE;
                }
            }
        }
        while(!queue.isEmpty()) {
            int[] cell = queue.poll();
            for(int dr = -1; dr <= 1; dr++) {
                for(int dc = -1; dc <= 1; dc++) {
                    int row = cell[0] + dr;
                    int col = cell[1] + dc;
                    if(row < 0 || col < 0 || row >= depth || col >= width ||
                       distance[row][col] != Integer.MAX_VALUE) continue;
                    distance[row][col] = distance[cell[0]][cell[1]] + 1;
                    queue.add(new int[] {row, col});
                }
            }
        }
        return distance;
    }

    private static void report(Simulator simulator, Window counters, boolean thirst)
    {
        Field field = simulator.getField();
        SimulationContext context = simulator.getContext();
        Map<String, double[]> state = new LinkedHashMap<>();
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            // pop, adults, food%, zero, hydration, thirsty, fill, sampled
            state.put(factory.getProfile().getName(), new double[8]);
        }
        for(Animal animal : field.getAnimals()) {
            if(!(animal instanceof SavannahAnimal) || !animal.isAlive()) continue;
            SavannahAnimal savannah = (SavannahAnimal)animal;
            SpeciesProfile profile = savannah.getProfile();
            double[] row = state.get(profile.getName());
            row[0]++;
            if(savannah.isAdult()) row[1]++;
            row[2] += 100.0 * savannah.getFoodLevel() / Math.max(1, profile.getMaxFoodLevel());
            if(savannah.getFoodLevel() <= 0) row[3]++;
            row[4] += savannah.getHydrationPercent();
            if(savannah.isThirsty()) row[5]++;
            if(((int)row[0]) % 10 == 0 && savannah.getLocation() != null) {
                row[6] += context.getFoodSystem().getLocalFill(
                    savannah.getLocation(), profile.getSearchRange());
                row[7]++;
            }
        }
        System.out.println("GRASS\t" + simulator.getStep() + "\t" +
            f(context.getFoodSystem().getAverageFoodLevel()));
        for(Map.Entry<String, double[]> entry : state.entrySet()) {
            String species = entry.getKey();
            double[] row = entry.getValue();
            double pop = Math.max(1.0, row[0]);
            Map<DeathCause, Integer> deaths = counters.deaths.getOrDefault(species,
                new EnumMap<>(DeathCause.class));
            int starve = deaths.getOrDefault(DeathCause.STARVATION, 0);
            int pred = deaths.getOrDefault(DeathCause.PREDATION, 0);
            int all = 0;
            for(int count : deaths.values()) all += count;
            Map<StepFrameLedger.ProposalReason, Integer> reasons = counters.reasons.getOrDefault(
                species, new EnumMap<>(StepFrameLedger.ProposalReason.class));
            int proposals = 0;
            for(int count : reasons.values()) proposals += count;
            System.out.println(String.join("\t",
                Integer.toString(simulator.getStep()), species,
                Integer.toString((int)row[0]), Integer.toString((int)row[1]),
                Integer.toString(counters.births.getOrDefault(species, 0)),
                Integer.toString(starve), Integer.toString(pred),
                Integer.toString(all - starve - pred),
                f(row[2] / pop), f(100.0 * row[3] / pop),
                f(row[4] / pop), f(100.0 * row[5] / pop),
                f(proposals == 0 ? 0.0 : 100.0 * reasons.getOrDefault(
                    StepFrameLedger.ProposalReason.WATER_SUPPRESSED, 0) / proposals),
                Integer.toString(reasons.getOrDefault(
                    StepFrameLedger.ProposalReason.CONCEPTION, 0)),
                Integer.toString(counters.drink.getOrDefault(species, 0)),
                Integer.toString(counters.blocked.getOrDefault(species, 0)),
                Integer.toString(counters.retarget.getOrDefault(species, 0)),
                f(row[7] == 0 ? 0.0 : row[6] / row[7])));
        }
    }

    private static String f(double value)
    {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** Event and ledger counts since the last report. */
    private static final class Window implements SimulationEventListener,
        StepFrameLedgerListener
    {
        final Map<String, Integer> births = new LinkedHashMap<>();
        final Map<String, Map<DeathCause, Integer>> deaths = new LinkedHashMap<>();
        final Map<String, Map<StepFrameLedger.ProposalReason, Integer>> reasons =
            new LinkedHashMap<>();
        final Map<String, Integer> drink = new LinkedHashMap<>();
        final Map<String, Integer> blocked = new LinkedHashMap<>();
        final Map<String, Integer> retarget = new LinkedHashMap<>();
        private static final int NEAR = 8;
        private final int[][] waterDistance;
        /** Whole-run kills by target species: total and within NEAR cells of water. */
        private final Map<String, int[]> killsNearWater = new LinkedHashMap<>();
        /** Whole-run sampled population-steps by species: total and near water. */
        private final Map<String, long[]> animalStepsNearWater = new LinkedHashMap<>();

        Window(int[][] waterDistance)
        {
            this.waterDistance = waterDistance;
        }

        void sample(Field field)
        {
            for(Animal animal : field.getAnimals()) {
                if(!(animal instanceof SavannahAnimal) || !animal.isAlive() ||
                   animal.getLocation() == null) continue;
                String species = ((SavannahAnimal)animal).getProfile().getName();
                long[] cell = animalStepsNearWater.computeIfAbsent(species, key -> new long[2]);
                cell[0]++;
                if(waterDistance[animal.getLocation().row()][animal.getLocation().col()] <= NEAR) {
                    cell[1]++;
                }
            }
        }

        void printProximity(boolean thirst, Field field, int[][] unused)
        {
            for(Map.Entry<String, int[]> entry : killsNearWater.entrySet()) {
                long[] stock = animalStepsNearWater.getOrDefault(entry.getKey(), new long[2]);
                System.out.println("PROXIMITY thirst=" + (thirst ? "on" : "off") +
                    " preySpecies=" + entry.getKey() + " kills=" + entry.getValue()[0] +
                    " killsWithin" + NEAR + "OfWater=" + entry.getValue()[1] +
                    " killShare=" + f(100.0 * entry.getValue()[1] / Math.max(1, entry.getValue()[0])) +
                    " populationShareWithin" + NEAR + "=" +
                    f(100.0 * stock[1] / Math.max(1L, stock[0])));
            }
        }

        void reset()
        {
            births.clear();
            deaths.clear();
            reasons.clear();
            drink.clear();
            blocked.clear();
            retarget.clear();
        }

        @Override
        public void onEvent(SimulationEvent event)
        {
            switch(event.type) {
                case BIRTH:
                    births.merge(event.targetSpecies, 1, Integer::sum);
                    break;
                case DEATH:
                    deaths.computeIfAbsent(event.actorSpecies,
                        key -> new EnumMap<>(DeathCause.class))
                        .merge(event.deathCause, 1, Integer::sum);
                    break;
                case HUNT:
                    int[] kill = killsNearWater.computeIfAbsent(event.targetSpecies,
                        key -> new int[2]);
                    kill[0]++;
                    if(event.row >= 0 && event.col >= 0 &&
                       waterDistance[event.row][event.col] <= NEAR) kill[1]++;
                    break;
                case DRINK:
                    drink.merge(event.actorSpecies, 1, Integer::sum);
                    break;
                case WATER_BLOCKED:
                    blocked.merge(event.actorSpecies, 1, Integer::sum);
                    break;
                case WATER_RETARGET:
                    retarget.merge(event.actorSpecies, 1, Integer::sum);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onStepFrameLedger(StepFrameLedger ledger)
        {
            StepFrameLedger.ReproductionProposal proposal = ledger.getReproductionProposal();
            if(proposal == null) return;
            reasons.computeIfAbsent(ledger.getActorSpecies(),
                key -> new EnumMap<>(StepFrameLedger.ProposalReason.class))
                .merge(proposal.getReason(), 1, Integer::sum);
        }
    }
}
