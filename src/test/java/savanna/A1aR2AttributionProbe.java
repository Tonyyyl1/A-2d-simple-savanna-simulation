package savanna;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Emits compatible R0/R2 checkpoints plus R2's named plan ledger through
 * reflection. Keeping the plan access reflective lets the same test source
 * compile against the frozen R0 reference JAR, where the R2 API does not
 * exist.
 */
public final class A1aR2AttributionProbe
    implements SimulationEventListener, TransitionObservationListener
{
    private final Map<Integer, EnumMap<SimulationEvent.EventType, Integer>> events =
        new LinkedHashMap<>();
    private final Map<Integer, EnumMap<TransitionStatus, Integer>> transitions =
        new LinkedHashMap<>();
    private final SpeciesLedger speciesTotals = new SpeciesLedger();
    private final SpeciesLedger speciesWindow = new SpeciesLedger();
    private final Map<String, Integer> planReasons = new TreeMap<>();
    private final Map<String, Integer> planDispositions = new TreeMap<>();
    private long totalCandidateVisits;
    private long totalCandidateVisitLimit;
    private int planCount;
    private boolean planAvailable;

    private A1aR2AttributionProbe()
    {
    }

    public static void main(String[] args)
    {
        int steps = argument(args, 0, 300);
        int interval = argument(args, 1, 100);
        long ecologySeed = longArgument(args, 2,
            SimulationConfig.DEFAULT_RANDOM_SEED);
        long terrainSeed = longArgument(args, 3,
            SimulationConfig.DEFAULT_TERRAIN_SEED);
        if(steps <= 0 || interval <= 0) {
            throw new IllegalArgumentException("steps and interval must be positive");
        }

        SimulationConfig config = SimulationConfig.builder()
            .randomSeed(ecologySeed).terrainSeed(terrainSeed).build();
        Randomizer.reset();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        A1aR2AttributionProbe collector = new A1aR2AttributionProbe();
        simulator.addEventListener(collector);
        simulator.getContext().addTransitionObservationListener(collector);
        System.out.println("A1A_R2_ATTRIBUTION schema=1 ecologySeed=" +
            ecologySeed + " terrainSeed=" + terrainSeed + " steps=" + steps +
            " interval=" + interval);
        for(int index = 1; index <= steps && simulator.getField().isViable();
            index++) {
            simulator.simulateOneStep();
            collector.capturePlan(simulator.getContext());
            String fingerprint = SimulationFingerprint.capture(simulator);
            System.out.println("A1A_R2_FINGERPRINT step=" + simulator.getStep() +
                " value=" + fingerprint);
            if(index % interval == 0 || index == steps ||
               !simulator.getField().isViable()) {
                int step = simulator.getStep();
                System.out.println("A1A_R2_CHECKPOINT step=" + step +
                    " counts=" + orderedCounts(simulator.getField()) +
                    " births=" + collector.eventCount(step,
                        SimulationEvent.EventType.BIRTH) +
                    " deaths=" + collector.deathCount(step) +
                    " transitions=" + collector.transitionSummary(step) +
                    " plan=" + planSummary(simulator.getContext()));
                System.out.println("A1A_R2_SPECIES_WINDOW step=" + step +
                    ' ' + collector.drainSpeciesWindowSummary());
            }
        }
        System.out.println("A1A_R2_ATTRIBUTION PASS completed=" +
            simulator.getStep() + " viable=" + simulator.getField().isViable() +
            " final=" + SimulationFingerprint.capture(simulator));
        System.out.println("A1A_R2_TOTALS births=" + collector.totalEventCount(
            SimulationEvent.EventType.BIRTH) + " deaths=" + collector.totalDeaths() +
            " events=" + collector.totalEventSummary() + " transitions=" +
            collector.totalTransitionSummary() + " plan=" +
            collector.totalPlanSummary());
        System.out.println("A1A_R2_SPECIES_TOTALS " +
            collector.totalSpeciesSummary());
    }

    @Override
    public void onEvent(SimulationEvent event)
    {
        if(event == null) return;
        EnumMap<SimulationEvent.EventType, Integer> counts = events.computeIfAbsent(
            event.step, ignored -> new EnumMap<>(SimulationEvent.EventType.class));
        counts.put(event.type, Integer.valueOf(counts.getOrDefault(event.type,
            Integer.valueOf(0)).intValue() + 1));
        speciesTotals.record(event);
        speciesWindow.record(event);
    }

    @Override
    public void onTransition(TransitionObservation observation)
    {
        if(observation == null) return;
        EnumMap<TransitionStatus, Integer> counts = transitions.computeIfAbsent(
            observation.getStep(), ignored -> new EnumMap<>(TransitionStatus.class));
        TransitionStatus status = observation.getStatus();
        counts.put(status, Integer.valueOf(counts.getOrDefault(status,
            Integer.valueOf(0)).intValue() + 1));
    }

    private int eventCount(int step, SimulationEvent.EventType type)
    {
        return events.getOrDefault(step, new EnumMap<>(SimulationEvent.EventType.class))
            .getOrDefault(type, Integer.valueOf(0)).intValue();
    }

    private int deathCount(int step)
    {
        EnumMap<SimulationEvent.EventType, Integer> counts = events.get(step);
        if(counts == null) return 0;
        return counts.getOrDefault(SimulationEvent.EventType.DEATH,
            Integer.valueOf(0)).intValue() + counts.getOrDefault(
            SimulationEvent.EventType.DISEASE_DEATH, Integer.valueOf(0)).intValue();
    }

    private int totalEventCount(SimulationEvent.EventType type)
    {
        int total = 0;
        for(EnumMap<SimulationEvent.EventType, Integer> counts : events.values()) {
            total += counts.getOrDefault(type, Integer.valueOf(0)).intValue();
        }
        return total;
    }

    private int totalDeaths()
    {
        return totalEventCount(SimulationEvent.EventType.DEATH) +
            totalEventCount(SimulationEvent.EventType.DISEASE_DEATH);
    }

    private String totalEventSummary()
    {
        EnumMap<SimulationEvent.EventType, Integer> totals = new EnumMap<>(
            SimulationEvent.EventType.class);
        for(EnumMap<SimulationEvent.EventType, Integer> counts : events.values()) {
            for(Map.Entry<SimulationEvent.EventType, Integer> entry : counts.entrySet()) {
                SimulationEvent.EventType type = entry.getKey();
                totals.put(type, Integer.valueOf(totals.getOrDefault(type,
                    Integer.valueOf(0)).intValue() + entry.getValue().intValue()));
            }
        }
        return summary(totals, SimulationEvent.EventType.values());
    }

    private String totalTransitionSummary()
    {
        EnumMap<TransitionStatus, Integer> totals = new EnumMap<>(
            TransitionStatus.class);
        for(EnumMap<TransitionStatus, Integer> counts : transitions.values()) {
            for(Map.Entry<TransitionStatus, Integer> entry : counts.entrySet()) {
                TransitionStatus status = entry.getKey();
                totals.put(status, Integer.valueOf(totals.getOrDefault(status,
                    Integer.valueOf(0)).intValue() + entry.getValue().intValue()));
            }
        }
        return summary(totals, TransitionStatus.values());
    }

    private String transitionSummary(int step)
    {
        EnumMap<TransitionStatus, Integer> counts = transitions.get(step);
        if(counts == null || counts.isEmpty()) return "{}";
        StringBuilder result = new StringBuilder("{");
        for(TransitionStatus status : TransitionStatus.values()) {
            int count = counts.getOrDefault(status, Integer.valueOf(0)).intValue();
            if(count <= 0) continue;
            if(result.length() > 1) result.append(',');
            result.append(status.name()).append('=').append(count);
        }
        return result.append('}').toString();
    }

    private <E extends Enum<E>> String summary(Map<E, Integer> counts,
                                                E[] values)
    {
        StringBuilder result = new StringBuilder("{");
        for(E value : values) {
            int count = counts.getOrDefault(value, Integer.valueOf(0)).intValue();
            if(count <= 0) continue;
            if(result.length() > 1) result.append(',');
            result.append(value.name()).append('=').append(count);
        }
        return result.append('}').toString();
    }

    private void capturePlan(SimulationContext context)
    {
        try {
            Method getPlan = context.getClass().getMethod("getLastPositionPlan");
            Object plan = getPlan.invoke(context);
            if(plan == null) return;
            Object resolutionsValue = plan.getClass().getMethod("getResolutions")
                .invoke(plan);
            if(!(resolutionsValue instanceof Map<?, ?>)) return;
            planAvailable = true;
            planCount++;
            for(Object resolution : ((Map<?, ?>)resolutionsValue).values()) {
                Object reason = resolution.getClass().getMethod("getReason")
                    .invoke(resolution);
                Object disposition = resolution.getClass().getMethod("getDisposition")
                    .invoke(resolution);
                increment(planReasons, String.valueOf(reason));
                increment(planDispositions, String.valueOf(disposition));
            }
            Object metrics = plan.getClass().getMethod("getMetrics").invoke(plan);
            totalCandidateVisits += ((Long)metrics.getClass().getMethod(
                "getCandidateVisits").invoke(metrics)).longValue();
            totalCandidateVisitLimit += ((Long)metrics.getClass().getMethod(
                "getCandidateVisitLimit").invoke(metrics)).longValue();
        }
        catch(ReflectiveOperationException unavailable)
        {
            // The frozen R0 reference has no position plan API.
        }
    }

    private String totalPlanSummary()
    {
        if(!planAvailable) return "LEGACY_UNAVAILABLE";
        return "plans=" + planCount + ";reasons=" + planReasons +
            ";dispositions=" + planDispositions + ";visits=" +
            totalCandidateVisits + '/' + totalCandidateVisitLimit;
    }

    private String totalSpeciesSummary()
    {
        return speciesTotals.summary();
    }

    private String drainSpeciesWindowSummary()
    {
        String summary = speciesWindow.summary();
        speciesWindow.clear();
        return summary;
    }

    private static void increment(Map<String, Integer> counts, String key)
    {
        counts.put(key, Integer.valueOf(counts.getOrDefault(key,
            Integer.valueOf(0)).intValue() + 1));
    }

    /**
     * Test-only ecological ledger. It derives all values from emitted events,
     * so collecting it cannot influence simulation order or random state.
     */
    private static final class SpeciesLedger
    {
        private final Map<String, Integer> birthsByChildSpecies = new TreeMap<>();
        private final Map<String, EnumMap<DeathCause, Integer>>
            deathsBySpeciesCause = new TreeMap<>();
        private final Map<String, Integer> huntsByPredatorPrey = new TreeMap<>();

        private void record(SimulationEvent event)
        {
            if(event.type == SimulationEvent.EventType.BIRTH) {
                increment(birthsByChildSpecies, species(event.targetSpecies));
            }
            if(event.type == SimulationEvent.EventType.DEATH ||
               event.type == SimulationEvent.EventType.DISEASE_DEATH) {
                EnumMap<DeathCause, Integer> causes =
                    deathsBySpeciesCause.computeIfAbsent(
                        species(event.actorSpecies), ignored ->
                            new EnumMap<>(DeathCause.class));
                DeathCause cause = event.deathCause;
                causes.put(cause, Integer.valueOf(causes.getOrDefault(cause,
                    Integer.valueOf(0)).intValue() + 1));
            }
            if(event.type == SimulationEvent.EventType.HUNT) {
                increment(huntsByPredatorPrey, species(event.actorSpecies) +
                    "->" + species(event.targetSpecies));
            }
        }

        private String summary()
        {
            return "birthsByChild=" + birthsByChildSpecies +
                " deathsBySpeciesCause=" + deathsBySpeciesCause +
                " huntsByPredatorPrey=" + huntsByPredatorPrey;
        }

        private void clear()
        {
            birthsByChildSpecies.clear();
            deathsBySpeciesCause.clear();
            huntsByPredatorPrey.clear();
        }

        private static String species(String value)
        {
            return value == null || value.isEmpty() ? "UNKNOWN" : value;
        }
    }

    private static String orderedCounts(Field field)
    {
        Map<String, Integer> counts = field.countLivingBySpecies();
        StringBuilder result = new StringBuilder("{");
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            if(result.length() > 1) result.append(',');
            String name = factory.getProfile().getName();
            result.append(name).append('=').append(counts.getOrDefault(name, 0));
        }
        return result.append('}').toString();
    }

    private static String planSummary(SimulationContext context)
    {
        try {
            Method getPlan = context.getClass().getMethod("getLastPositionPlan");
            Object plan = getPlan.invoke(context);
            if(plan == null) return "LEGACY_UNAVAILABLE";
            Object resolutionsValue = plan.getClass().getMethod("getResolutions")
                .invoke(plan);
            if(!(resolutionsValue instanceof Map<?, ?>)) {
                return "UNREADABLE";
            }
            Map<String, Integer> reasons = new TreeMap<>();
            Map<String, Integer> dispositions = new TreeMap<>();
            String representative = null;
            String cancellationRepresentative = null;
            String requestedRepresentative = null;
            for(Object resolution : ((Map<?, ?>)resolutionsValue).values()) {
                Object reason = resolution.getClass().getMethod("getReason")
                    .invoke(resolution);
                Object disposition = resolution.getClass().getMethod("getDisposition")
                    .invoke(resolution);
                Object actor = resolution.getClass().getMethod("getActor")
                    .invoke(resolution);
                String reasonName = String.valueOf(reason);
                String dispositionName = String.valueOf(disposition);
                reasons.put(reasonName, Integer.valueOf(reasons.getOrDefault(
                    reasonName, Integer.valueOf(0)).intValue() + 1));
                dispositions.put(dispositionName, Integer.valueOf(
                    dispositions.getOrDefault(dispositionName, Integer.valueOf(0))
                        .intValue() + 1));
                if(!"REQUESTED".equals(reasonName) &&
                   !"CLAIM_CANCELLED".equals(reasonName) &&
                   representative == null) {
                    representative = actor + ":" + reasonName + ":" +
                        dispositionName;
                }
                else if("CLAIM_CANCELLED".equals(reasonName) &&
                        cancellationRepresentative == null) {
                    cancellationRepresentative = actor + ":" + reasonName +
                        ":" + dispositionName;
                }
                else if("REQUESTED".equals(reasonName) &&
                        requestedRepresentative == null) {
                    requestedRepresentative = actor + ":" + reasonName + ":" +
                        dispositionName;
                }
            }
            if(representative == null) representative = cancellationRepresentative;
            if(representative == null) representative = requestedRepresentative;
            Object metrics = plan.getClass().getMethod("getMetrics").invoke(plan);
            long visits = ((Long)metrics.getClass().getMethod("getCandidateVisits")
                .invoke(metrics)).longValue();
            long limit = ((Long)metrics.getClass().getMethod("getCandidateVisitLimit")
                .invoke(metrics)).longValue();
            return "reasons=" + reasons + ";dispositions=" + dispositions +
                ";representative=" + representative + ";visits=" + visits +
                "/" + limit;
        }
        catch(ReflectiveOperationException unavailable)
        {
            return "LEGACY_UNAVAILABLE";
        }
    }

    private static int argument(String[] args, int index, int fallback)
    {
        return args.length > index ? Integer.parseInt(args[index]) : fallback;
    }

    private static long longArgument(String[] args, int index, long fallback)
    {
        return args.length > index ? Long.parseLong(args[index]) : fallback;
    }
}
