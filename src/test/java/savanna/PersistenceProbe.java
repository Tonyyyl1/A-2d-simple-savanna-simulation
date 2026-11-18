package savanna;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase P (persistence) probe: time series for one headless run (plan
 * {@code docs/plans/STABILITY_ANALYSIS_AND_PLAN_2026-11-12.zh.md}, P-0).
 *
 * <p>Usage: {@code PersistenceProbe <seed> <steps> <off|on> <interval>
 * [--params=key=value,...]}. Every {@code interval} steps it prints, for the
 * interval that just ended:</p>
 * <ul>
 * <li>{@code POP step,Lion,Cheetah,Zebra,Buffalo,Gazelle}: living counts;</li>
 * <li>{@code PRED}: per predator species, sex split, births, death causes, the
 * hunt funnel, rivals and territory counts, prey in range, food;</li>
 * <li>{@code HERB}: per herbivore species, births, death causes, food and
 * hydration, distance to water, drinks and blocked-at-the-shore events, and the
 * mean fill of the grass cell under the animal;</li>
 * <li>{@code GRASS}: drought and rain steps, and the grass fill per terrain;</li>
 * <li>{@code SEX}: per predator species, adult food and starvation deaths by
 * sex, and the food of mothers with cubs against other females.</li>
 * </ul>
 * <p>Observation only: it reads events, hunt observations and the published
 * field, never the ecological random stream, so a probed run ends exactly like
 * an {@code EcologyMatrixRunner} run with the same parameters. The last line is
 * {@code PROBE_END final=Lion:n,... extinctionStep=N}, in the same compact
 * form as the runner's {@code final=} and {@code extinctionStep=} fields.</p>
 */
public final class PersistenceProbe
{
    static final String[] SPECIES = {"Lion", "Cheetah", "Zebra", "Buffalo", "Gazelle"};
    static final String[] HERBIVORES = {"Zebra", "Buffalo", "Gazelle"};
    static final TerrainType[] HERBIVORE_HOME = {
        TerrainType.GRASSLAND, TerrainType.BUSH, TerrainType.OPEN_PLAIN};

    private PersistenceProbe()
    {
    }

    public static void main(String[] args)
    {
        if(args.length < 4) {
            System.err.println("usage: PersistenceProbe <seed> <steps> <off|on> <interval> " +
                "[--params=key=value,...]");
            System.exit(2);
        }
        long seed = Long.parseLong(args[0]);
        int steps = Integer.parseInt(args[1]);
        boolean thirst = "on".equals(args[2]);
        int interval = Integer.parseInt(args[3]);
        for(int index = 4; index < args.length; index++) {
            if(args[index].startsWith("--params=")) {
                EcologyParameters.override(
                    EcologyParameters.defaults().with(args[index].substring(9)));
            }
            else {
                System.err.println("unknown option: " + args[index]);
                System.exit(2);
            }
        }
        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(seed).thirstEnabled(thirst).build();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        Probe probe = new Probe();
        simulator.addEventListener(probe);
        simulator.getContext().addHuntObservationListener(probe);
        TerrainMap terrain = simulator.getContext().getTerrainMap();
        GrasslandFoodSystem food = (GrasslandFoodSystem)simulator.getContext().getFoodSystem();
        EcologyParameters parameters = EcologyParameters.current();
        Map<String, Integer> minimum = new HashMap<>(simulator.getField().countLivingBySpecies());

        System.out.println("PROBE_HEADER rules=" + EcologyRules.VERSION + " seed=" + seed +
            " thirst=" + args[2] + " steps=" + steps + " interval=" + interval +
            " params=" + parameters.overrides());
        System.out.println("POP step,Lion,Cheetah,Zebra,Buffalo,Gazelle");
        System.out.println("PRED species,step,adF,adM,young,pregnant,births,dAge,dStarveYoung," +
            "dStarveAdult,dDisease,dThirst,dOther,active,noPrey,outOfContact,attempts,kills," +
            "declined,meanRivalsInHuntRange,fracAdultsWithRival,fracFemMateIn6,fracFemMateIn30," +
            "meanTerritoryCount,fracFemOverTerritory,meanPreyInRange,fracAdultsNoPreyInRange," +
            "meanFoodFracAdult,fracAdultCanBreedFood");
        System.out.println("HERB species,step,n,births,dAge,dStarve,dThirst,dPredLion," +
            "dPredCheetah,dDisease,dOther,meanFoodFrac,meanHydration,fracThirsty,fracCritical," +
            "meanDistWater,drinks,waterBlocked,ownCellFill");
        System.out.println("GRASS step,drought,rain,fillGRASSLAND,fillBUSH,fillOPEN_PLAIN," +
            "fillDRY_SOIL");
        System.out.println("SEX species,step,adF,adM,foodF,foodM,starveAdF,starveAdM," +
            "starveYoungF,starveYoungM,mothersWithCubs,foodMotherWithCubs,foodFemNoCubs");

        int extinctionStep = -1;
        for(int index = 0; index < steps; index++) {
            simulator.simulateOneStep();
            int step = simulator.getStep();
            probe.weather.merge(simulator.getContext().getWeatherSystem().getCurrentWeather(),
                1, Integer::sum);
            Map<String, Integer> counts = simulator.getField().countLivingBySpecies();
            boolean alive = true;
            for(String name : SPECIES) {
                int count = counts.getOrDefault(name, 0);
                minimum.merge(name, count, Math::min);
                if(parameters.initialCount(name) > 0 && count <= 0) alive = false;
            }
            boolean report = step % interval == 0 || !alive || index == steps - 1;
            if(report) {
                StringBuilder pop = new StringBuilder("POP " + step);
                for(String name : SPECIES) pop.append(',').append(counts.getOrDefault(name, 0));
                System.out.println(pop);
                probe.sample(simulator.getField(), terrain, food, step);
                probe.resetInterval();
            }
            probe.track(simulator.getField());
            if(!alive) {
                extinctionStep = step;
                break;
            }
        }
        Map<String, Integer> finalCounts = simulator.getField().countLivingBySpecies();
        StringBuilder end = new StringBuilder("PROBE_END final=");
        StringBuilder least = new StringBuilder(" min=");
        for(int i = 0; i < SPECIES.length; i++) {
            if(i > 0) { end.append(','); least.append(','); }
            end.append(SPECIES[i]).append(':').append(finalCounts.getOrDefault(SPECIES[i], 0));
            least.append(SPECIES[i]).append(':').append(minimum.getOrDefault(SPECIES[i], 0));
        }
        System.out.println(end + least.toString() + " extinctionStep=" + extinctionStep);
    }

    static final class Probe implements SimulationEventListener, HuntObservationListener
    {
        final Map<String, Integer> births = new HashMap<>();
        final Map<String, Map<DeathCause, Integer>> deaths = new HashMap<>();
        final Map<String, Integer> starveYoung = new HashMap<>();
        final Map<String, Integer> predBy = new HashMap<>();
        final Map<String, Map<HuntOutcome, Integer>> hunt = new HashMap<>();
        final Map<String, Integer> active = new HashMap<>();
        final Map<String, Integer> drinks = new HashMap<>();
        final Map<String, Integer> blocked = new HashMap<>();
        final Map<WeatherType, Integer> weather = new EnumMap<>(WeatherType.class);
        /** id -> {age, sex (1 male), adult}: the previous step's published field. */
        final Map<Long, int[]> last = new HashMap<>();
        /** [species][female adult, male adult, female young, male young] starvation deaths. */
        final int[][] starveBySex = new int[2][4];

        void resetInterval()
        {
            births.clear();
            deaths.clear();
            starveYoung.clear();
            predBy.clear();
            hunt.clear();
            active.clear();
            drinks.clear();
            blocked.clear();
            weather.clear();
            starveBySex[0] = new int[4];
            starveBySex[1] = new int[4];
        }

        void track(Field field)
        {
            last.clear();
            for(Animal animal : field.getAnimals()) {
                if(animal instanceof SavannahAnimal && animal.isAlive()) {
                    SavannahAnimal a = (SavannahAnimal)animal;
                    last.put(a.getId(), new int[] {
                        a.getAge(), a.getSex() == Sex.MALE ? 1 : 0, a.isAdult() ? 1 : 0});
                }
            }
        }

        @Override
        public void onEvent(SimulationEvent event)
        {
            switch(event.type) {
                case BIRTH: births.merge(event.actorSpecies, 1, Integer::sum); break;
                case DRINK: drinks.merge(event.actorSpecies, 1, Integer::sum); break;
                case WATER_BLOCKED: blocked.merge(event.actorSpecies, 1, Integer::sum); break;
                case HUNT:
                    predBy.merge(event.targetSpecies + "<" + event.actorSpecies, 1, Integer::sum);
                    break;
                case DEATH:
                case DISEASE_DEATH: {
                    DeathCause cause = event.type == SimulationEvent.EventType.DISEASE_DEATH
                        ? DeathCause.DISEASE : event.deathCause;
                    deaths.computeIfAbsent(event.actorSpecies, k -> new EnumMap<>(DeathCause.class))
                        .merge(cause, 1, Integer::sum);
                    if(cause == DeathCause.STARVATION) {
                        int[] info = last.get(event.actorId);
                        SpeciesProfile profile = SpeciesRegistry.getProfile(event.actorSpecies);
                        // "young": below adult age + 36 steps, i.e. has not yet bred.
                        if(info != null && profile != null &&
                            info[0] < profile.getAdultAge() + 36) {
                            starveYoung.merge(event.actorSpecies, 1, Integer::sum);
                        }
                        int species = "Lion".equals(event.actorSpecies) ? 0
                            : "Cheetah".equals(event.actorSpecies) ? 1 : -1;
                        if(species >= 0 && info != null) {
                            starveBySex[species][info[1] + (info[2] == 1 ? 0 : 2)]++;
                        }
                    }
                    break;
                }
                default: break;
            }
        }

        @Override
        public void onHuntResult(HuntObservation observation)
        {
            String name = observation.getActorSpecies();
            if(observation.isActivePhase()) active.merge(name, 1, Integer::sum);
            hunt.computeIfAbsent(name, k -> new EnumMap<>(HuntOutcome.class))
                .merge(observation.getResult().getOutcome(), 1, Integer::sum);
        }

        int death(String species, DeathCause cause)
        {
            Map<DeathCause, Integer> map = deaths.get(species);
            return map == null ? 0 : map.getOrDefault(cause, 0);
        }

        int deathTotal(String species)
        {
            Map<DeathCause, Integer> map = deaths.get(species);
            int total = 0;
            if(map != null) for(int value : map.values()) total += value;
            return total;
        }

        int huntCount(String species, HuntOutcome outcome)
        {
            Map<HuntOutcome, Integer> map = hunt.get(species);
            return map == null ? 0 : map.getOrDefault(outcome, 0);
        }

        void sample(Field field, TerrainMap terrain, GrasslandFoodSystem food, int step)
        {
            int depth = terrain.getDepth();
            int width = terrain.getWidth();
            // Species code per cell: 0 none, 1..5 species; flags: 1 adult, 2 male.
            byte[] code = new byte[depth * width];
            byte[] flags = new byte[depth * width];
            Map<String, List<SavannahAnimal>> bySpecies = new HashMap<>();
            Map<Long, Integer> cubsOf = new HashMap<>();
            for(Animal animal : field.getAnimals()) {
                if(!(animal instanceof SavannahAnimal) || !animal.isAlive()) continue;
                SavannahAnimal a = (SavannahAnimal)animal;
                Location at = a.getLocation();
                if(at == null) continue;
                String name = a.getProfile().getName();
                int cell = at.row() * width + at.col();
                code[cell] = (byte)(indexOf(name) + 1);
                flags[cell] = (byte)((a.isAdult() ? 1 : 0) | (a.getSex() == Sex.MALE ? 2 : 0));
                bySpecies.computeIfAbsent(name, k -> new ArrayList<>()).add(a);
                if(a.getProfile().isPredator() && !a.isAdult() && a.getMotherId() > 0) {
                    cubsOf.merge(a.getMotherId(), 1, Integer::sum);
                }
            }
            EcologyParameters p = EcologyParameters.current();
            for(String name : new String[] {"Lion", "Cheetah"}) {
                samplePredator(name, step, bySpecies.getOrDefault(name, new ArrayList<>()),
                    code, flags, depth, width, p);
            }
            for(String name : HERBIVORES) {
                sampleHerbivore(name, step, bySpecies.getOrDefault(name, new ArrayList<>()),
                    terrain, food);
            }
            sampleGrass(step, terrain, food, depth, width);
            for(int species = 0; species < 2; species++) {
                sampleSex(species, step, bySpecies, cubsOf);
            }
        }

        private void samplePredator(String name, int step, List<SavannahAnimal> list,
                                    byte[] code, byte[] flags, int depth, int width,
                                    EcologyParameters p)
        {
            SpeciesProfile profile = SpeciesRegistry.getProfile(name);
            byte own = (byte)(indexOf(name) + 1);
            int huntRange = profile.getSearchRange();
            int mateRange = profile.getBreedingRange();
            int senseRange = mateRange * p.getInt(EcologyParameters.Key.MATE_SENSE_FACTOR);
            boolean lion = "Lion".equals(name);
            int terrRadius = p.getInt(lion ? EcologyParameters.Key.TERRITORY_LION_RADIUS
                : EcologyParameters.Key.TERRITORY_CHEETAH_RADIUS);
            int terrLimit = p.getInt(lion ? EcologyParameters.Key.TERRITORY_LION
                : EcologyParameters.Key.TERRITORY_CHEETAH);
            int adF = 0, adM = 0, young = 0, pregnant = 0;
            double rivalsSum = 0;
            int withRival = 0, adults = 0;
            int femMate6 = 0, femMate30 = 0, femOver = 0;
            double terrSum = 0, preySum = 0, foodSum = 0;
            int noPrey = 0, canBreedFood = 0;
            for(SavannahAnimal a : list) {
                if(!a.isAdult()) { young++; continue; }
                adults++;
                boolean female = a.getSex() == Sex.FEMALE;
                if(female) adF++; else adM++;
                if(a.getReproductionState().isPregnant()) pregnant++;
                Location at = a.getLocation();
                int rivals = 0, prey = 0, terr = 0;
                boolean mate6 = false, mate30 = false;
                int maxRadius = Math.max(senseRange, Math.max(terrRadius, huntRange));
                for(int row = Math.max(0, at.row() - maxRadius);
                    row <= Math.min(depth - 1, at.row() + maxRadius); row++) {
                    for(int col = Math.max(0, at.col() - maxRadius);
                        col <= Math.min(width - 1, at.col() + maxRadius); col++) {
                        byte c = code[row * width + col];
                        if(c == 0) continue;
                        int d = Math.max(Math.abs(row - at.row()), Math.abs(col - at.col()));
                        if(c == own) {
                            if(d <= huntRange) rivals++;          // includes self
                            if(d <= terrRadius) terr++;           // includes self
                            byte f = flags[row * width + col];
                            if(d > 0 && (f & 1) != 0 && (f & 2) != 0) {
                                if(d <= mateRange) mate6 = true;
                                if(d <= senseRange) mate30 = true;
                            }
                        }
                        else if(d <= huntRange && profile.canEat(SPECIES[c - 1])) prey++;
                    }
                }
                rivalsSum += rivals - 1;
                if(rivals > 1) withRival++;
                preySum += prey;
                if(prey == 0) noPrey++;
                foodSum += (double)a.getFoodLevel() / profile.getMaxFoodLevel();
                if(a.getFoodLevel() - ReproductionPolicy.maternalFloor(profile) >=
                    ReproductionPolicy.childReserveFood(profile)) canBreedFood++;
                if(female) {
                    if(mate6) femMate6++;
                    if(mate30) femMate30++;
                    terrSum += terr;
                    if(terrLimit > 0 && terr > terrLimit) femOver++;
                }
            }
            int other = deathTotal(name) - death(name, DeathCause.AGE) -
                death(name, DeathCause.STARVATION) - death(name, DeathCause.DISEASE) -
                death(name, DeathCause.DEHYDRATION);
            System.out.println(String.format(Locale.ROOT,
                "PRED %s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d," +
                    "%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.1f,%.2f,%.2f,%.2f",
                name, step, adF, adM, young, pregnant,
                births.getOrDefault(name, 0), death(name, DeathCause.AGE),
                starveYoung.getOrDefault(name, 0),
                death(name, DeathCause.STARVATION) - starveYoung.getOrDefault(name, 0),
                death(name, DeathCause.DISEASE), death(name, DeathCause.DEHYDRATION), other,
                active.getOrDefault(name, 0),
                huntCount(name, HuntOutcome.NO_LOCAL_REACHABLE_PREY),
                huntCount(name, HuntOutcome.OUT_OF_CONTACT),
                huntCount(name, HuntOutcome.FAILED_ATTEMPT) + huntCount(name, HuntOutcome.SUCCESS),
                huntCount(name, HuntOutcome.SUCCESS),
                huntCount(name, HuntOutcome.DECLINED),
                ratio(rivalsSum, adults), ratio(withRival, adults),
                ratio(femMate6, adF), ratio(femMate30, adF),
                ratio(terrSum, adF), ratio(femOver, adF),
                ratio(preySum, adults), ratio(noPrey, adults),
                ratio(foodSum, adults), ratio(canBreedFood, adults)));
        }

        private void sampleHerbivore(String name, int step, List<SavannahAnimal> list,
                                     TerrainMap terrain, GrasslandFoodSystem food)
        {
            SpeciesProfile profile = SpeciesRegistry.getProfile(name);
            double foodSum = 0, hydration = 0, distance = 0, fill = 0;
            int thirsty = 0, critical = 0;
            for(SavannahAnimal a : list) {
                foodSum += (double)a.getFoodLevel() / profile.getMaxFoodLevel();
                hydration += a.getHydrationPercent();
                if(a.isThirsty()) thirsty++;
                if(a.isSurvivalCritical()) critical++;
                Location at = a.getLocation();
                distance += terrain.chebyshevDistanceToWater(at);
                double capacity = food.getCapacityAt(at);
                fill += capacity <= 0 ? 0 : food.getFoodLevelAt(at) / capacity;
            }
            int n = list.size();
            int other = deathTotal(name) - death(name, DeathCause.AGE) -
                death(name, DeathCause.STARVATION) - death(name, DeathCause.DEHYDRATION) -
                death(name, DeathCause.PREDATION) - death(name, DeathCause.DISEASE);
            System.out.println(String.format(Locale.ROOT,
                "HERB %s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.2f,%.1f,%.2f,%.2f,%.1f,%d,%d,%.2f",
                name, step, n, births.getOrDefault(name, 0),
                death(name, DeathCause.AGE), death(name, DeathCause.STARVATION),
                death(name, DeathCause.DEHYDRATION),
                predBy.getOrDefault(name + "<Lion", 0), predBy.getOrDefault(name + "<Cheetah", 0),
                death(name, DeathCause.DISEASE), other,
                ratio(foodSum, n), ratio(hydration, n), ratio(thirsty, n), ratio(critical, n),
                ratio(distance, n), drinks.getOrDefault(name, 0), blocked.getOrDefault(name, 0),
                ratio(fill, n)));
        }

        private void sampleGrass(int step, TerrainMap terrain, GrasslandFoodSystem food,
                                 int depth, int width)
        {
            double[] biomass = new double[TerrainType.values().length];
            double[] capacity = new double[TerrainType.values().length];
            for(int row = 0; row < depth; row++) {
                for(int col = 0; col < width; col++) {
                    Location at = new Location(row, col);
                    int type = terrain.getTerrainAt(at).ordinal();
                    biomass[type] += food.getFoodLevelAt(at);
                    capacity[type] += food.getCapacityAt(at);
                }
            }
            System.out.println(String.format(Locale.ROOT, "GRASS %d,%d,%d,%.2f,%.2f,%.2f,%.2f",
                step, weather.getOrDefault(WeatherType.DROUGHT, 0),
                weather.getOrDefault(WeatherType.RAIN, 0),
                fill(biomass, capacity, TerrainType.GRASSLAND),
                fill(biomass, capacity, TerrainType.BUSH),
                fill(biomass, capacity, TerrainType.OPEN_PLAIN),
                fill(biomass, capacity, TerrainType.DRY_SOIL)));
        }

        private void sampleSex(int species, int step, Map<String, List<SavannahAnimal>> bySpecies,
                               Map<Long, Integer> cubsOf)
        {
            String name = species == 0 ? "Lion" : "Cheetah";
            int f = 0, m = 0, mothers = 0, noCubs = 0;
            double foodF = 0, foodM = 0, foodMothers = 0, foodNoCubs = 0;
            for(SavannahAnimal a : bySpecies.getOrDefault(name, new ArrayList<>())) {
                if(!a.isAdult()) continue;
                double frac = (double)a.getFoodLevel() / a.getProfile().getMaxFoodLevel();
                if(a.getSex() == Sex.FEMALE) {
                    f++;
                    foodF += frac;
                    if(cubsOf.containsKey(a.getId())) { mothers++; foodMothers += frac; }
                    else { noCubs++; foodNoCubs += frac; }
                }
                else {
                    m++;
                    foodM += frac;
                }
            }
            System.out.println(String.format(Locale.ROOT,
                "SEX %s,%d,%d,%d,%.2f,%.2f,%d,%d,%d,%d,%d,%.2f,%.2f",
                name, step, f, m, ratio(foodF, f), ratio(foodM, m),
                starveBySex[species][0], starveBySex[species][1],
                starveBySex[species][2], starveBySex[species][3],
                mothers, ratio(foodMothers, mothers), ratio(foodNoCubs, noCubs)));
        }

        static double ratio(double sum, int count)
        {
            return count == 0 ? 0.0 : sum / count;
        }

        static double fill(double[] biomass, double[] capacity, TerrainType type)
        {
            return capacity[type.ordinal()] <= 0 ? 0.0
                : biomass[type.ordinal()] / capacity[type.ordinal()];
        }

        static int indexOf(String name)
        {
            for(int i = 0; i < SPECIES.length; i++) if(SPECIES[i].equals(name)) return i;
            return -1;
        }
    }
}
