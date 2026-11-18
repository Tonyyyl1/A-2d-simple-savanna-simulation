package savanna;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Behavioral contracts exercised through the existing context/simulator API. */
public final class SimulationObservationTests
{
    private SimulationObservationTests() { }

    public static void main(String[] args)
    {
        TestSupport.startSuite("Simulation observation contracts");
        run();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    public static void run()
    {
        TestSupport.startGroup("Simulation event and step observation contracts");
        testEventListenerLifecycleAndPublicationOrder();
        testIndependentEventWindows();
        testActorEventBoundaries();
        testStepLedgerLifecycleAndCoverage();
        TestSupport.finishGroup("SIMULATION_OBSERVATIONS");
    }

    private static void testEventListenerLifecycleAndPublicationOrder()
    {
        SimulationContext context = context();
        Field field = new Field(4, 4);
        context.startStep(1, field);
        List<String> callbacks = new ArrayList<>();
        boolean[] publishedBeforeCallback = { true };
        SimulationEventListener first = event -> {
            callbacks.add("first:" + event.step);
            publishedBeforeCallback[0] &=
                context.getCurrentEvents().contains(event) &&
                context.getRecentEvents().contains(event) &&
                context.getFrameEventSummary().getTotalEventCount() > 0;
        };
        SimulationEventListener second = event -> callbacks.add("second:" + event.step);
        context.addEventListener(null);
        context.addEventListener(first);
        context.addEventListener(first);
        context.addEventListener(second);
        context.recordEvent(null);
        TestSupport.assertTrue("OBS null event is ignored", callbacks.isEmpty() &&
            context.getCurrentEvents().isEmpty());
        context.recordEvent(event(1, 7L));
        TestSupport.assertEquals("OBS listeners retain registration order and deduplicate",
            List.of("first:1", "second:1"), callbacks);
        TestSupport.assertTrue("OBS event facts are available before callbacks",
            publishedBeforeCallback[0]);

        context.removeEventListener(first);
        context.startStep(2, field);
        context.recordEvent(event(2, 7L));
        TestSupport.assertEquals("OBS removal survives the next step",
            List.of("first:1", "second:1", "second:2"), callbacks);
        context.addEventListener(first);
        context.recordEvent(event(2, 8L));
        TestSupport.assertEquals("OBS re-registered listener joins the end",
            List.of("first:1", "second:1", "second:2", "second:2", "first:2"),
            callbacks);
    }

    private static void testIndependentEventWindows()
    {
        SimulationContext context = context();
        Field field = new Field(4, 4);
        TestSupport.assertEquals("OBS initial frame starts at zero", 0,
            context.getFrameEventSummary().getStartStep());
        context.startStep(1, field);
        List<SimulationEvent> currentView = context.getCurrentEvents();
        List<SimulationEvent> recorded = new ArrayList<>();
        for(int index = 1; index <= 6; index++) {
            SimulationEvent event = event(1, index);
            recorded.add(event);
            context.recordEvent(event);
        }
        List<SimulationEvent> recentSnapshot = context.getRecentEvents();
        FrameEventSummary summary = context.getFrameEventSummary();
        TestSupport.assertEquals("OBS current event view retains all six events",
            recorded, currentView);
        TestSupport.assertEquals("OBS recent events evict oldest entries in FIFO order",
            recorded.subList(3, 6), recentSnapshot);
        TestSupport.assertEquals("OBS frame counts exceed both sample capacities",
            Long.valueOf(6L), Long.valueOf(summary.getTotalEventCount()));
        TestSupport.assertEquals("OBS frame samples retain first recorded events",
            recorded.subList(0, 2), summary.getRepresentativeEvents());
        TestSupport.assertTrue("OBS frame sample truncation is explicit",
            summary.isRepresentativeSamplesTruncated());
        TestSupport.assertTrue("OBS current events cannot be modified",
            rejects(UnsupportedOperationException.class, () -> currentView.clear()));

        context.startStep(2, field);
        context.recordEvent(event(2, 9L));
        TestSupport.assertEquals("OBS previous events retain the complete prior step",
            recorded, context.getPreviousEvents());
        TestSupport.assertEquals("OBS old current view remains bound to its step",
            recorded, currentView);
        TestSupport.assertEquals("OBS current events rotate at step start", 1,
            context.getCurrentEvents().size());
        TestSupport.assertEquals("OBS frame interval counts survive a non-boundary step",
            Long.valueOf(7L),
            Long.valueOf(context.getFrameEventSummary().getTotalEventCount()));
        TestSupport.assertEquals("OBS recent snapshot stays detached from future records",
            recorded.subList(3, 6), recentSnapshot);
        TestSupport.assertEquals("OBS frozen frame remains detached from later events",
            Long.valueOf(6L), Long.valueOf(summary.getTotalEventCount()));
        context.startStep(3, field);
        TestSupport.assertEquals("OBS configured cadence starts the next interval at three", 3,
            context.getFrameEventSummary().getStartStep());
        TestSupport.assertEquals("OBS frame counts reset without erasing recent history",
            Long.valueOf(0L),
            Long.valueOf(context.getFrameEventSummary().getTotalEventCount()));
        TestSupport.assertEquals("OBS recent buffer survives interval rotation", 3,
            context.getRecentEvents().size());
    }

    private static void testActorEventBoundaries()
    {
        SimulationContext context = context();
        Field field = new Field(4, 4);
        context.startStep(1, field);
        context.recordEvent(event(1, 7L));
        context.recordEvent(event(1, 8L));
        context.recordEvent(event(1, 7L));
        TestSupport.assertTrue("OBS actor event includes an exact last-event boundary",
            context.hasCurrentActorEventSince(2, SimulationEvent.EventType.GRAZE, 7L));
        TestSupport.assertFalse("OBS actor event excludes the next unused boundary",
            context.hasCurrentActorEventSince(3, SimulationEvent.EventType.GRAZE, 7L));
        TestSupport.assertFalse("OBS actor event excludes other actors after the boundary",
            context.hasCurrentActorEventSince(2, SimulationEvent.EventType.GRAZE, 8L));
        TestSupport.assertTrue("OBS invalid event queries cannot match",
            !context.hasCurrentActorEventSince(-1, SimulationEvent.EventType.GRAZE, 7L) &&
            !context.hasCurrentActorEventSince(0, null, 7L) &&
            !context.hasCurrentActorEventSince(0, SimulationEvent.EventType.GRAZE, 0L));
        context.startStep(2, field);
        TestSupport.assertFalse("OBS prior-step actor indices cannot leak into a new step",
            context.hasCurrentActorEventSince(0, SimulationEvent.EventType.GRAZE, 7L));
    }

    private static void testStepLedgerLifecycleAndCoverage()
    {
        Simulator simulator = new Simulator(24, 24, false, SimulationConfig.baseline());
        SimulationContext context = simulator.getContext();
        List<StepFrameLedger> delivered = new ArrayList<>();
        StepFrameLedgerListener frameListener = delivered::add;
        context.addStepFrameLedgerListener(null);
        context.addStepFrameLedgerListener(frameListener);
        context.addStepFrameLedgerListener(frameListener);
        List<String> order = new ArrayList<>();
        context.addStepFrameLedgerListener(ledger -> order.add("frame:" + ledger.getActorId()));
        List<PredatorRecruitmentAssessment> assessments = new ArrayList<>();
        List<Set<Long>> completedPredators = new ArrayList<>();
        PredatorRecruitmentAssessmentListener predatorListener =
            new PredatorRecruitmentAssessmentListener() {
                public void onPredatorRecruitmentAssessment(PredatorRecruitmentAssessment fact)
                {
                    assessments.add(fact);
                    order.add("predator:" + fact.getActorId());
                }

                public void onPredatorRecruitmentStepComplete(int step, Set<Long> ids)
                {
                    completedPredators.add(ids);
                    order.add("complete:" + step);
                }
            };
        List<LongHorizonTrophicStepFact> trophic = new ArrayList<>();
        LongHorizonTrophicStepListener trophicListener = fact -> {
            trophic.add(fact);
            order.add("trophic:" + fact.getStep());
        };
        context.addPredatorRecruitmentAssessmentListener(null);
        context.addPredatorRecruitmentAssessmentListener(predatorListener);
        context.addPredatorRecruitmentAssessmentListener(predatorListener);
        context.addLongHorizonTrophicStepListener(null);
        context.addLongHorizonTrophicStepListener(trophicListener);
        context.addLongHorizonTrophicStepListener(trophicListener);
        simulator.simulateOneStep();

        List<StepFrameLedger> snapshot = context.getCurrentStepFrameLedgers();
        TestSupport.assertFalse("OBS production step supplies nonempty ledger coverage",
            snapshot.isEmpty());
        TestSupport.assertEquals("OBS ledger delivery preserves commit order and deduplicates",
            snapshot, delivered);
        Set<Long> ids = new HashSet<>();
        Set<Long> predatorIds = new HashSet<>();
        for(StepFrameLedger ledger : snapshot) {
            ids.add(ledger.getActorId());
            if(SpeciesRegistry.getProfile(ledger.getActorSpecies()).isPredator()) {
                predatorIds.add(ledger.getActorId());
            }
        }
        context.requireCompleteStepFrameLedgers(ids);
        TestSupport.assertTrue("OBS duplicate ledger publication is rejected",
            rejects(IllegalStateException.class, () -> context.recordStepFrameLedger(snapshot.get(0))));
        TestSupport.assertTrue("OBS null ledger publication is rejected",
            rejects(IllegalStateException.class, () -> context.recordStepFrameLedger(null)));
        TestSupport.assertTrue("OBS incomplete ledger identities are rejected",
            rejects(IllegalStateException.class, () -> context.requireCompleteStepFrameLedgers(Set.of())));
        TestSupport.assertTrue("OBS ledger snapshots cannot be modified",
            rejects(UnsupportedOperationException.class, () -> snapshot.clear()));
        TestSupport.assertTrue("OBS fixture exercises predators", !predatorIds.isEmpty());
        TestSupport.assertEquals("OBS each predator has one joined S0/S4 assessment",
            predatorIds.size(), assessments.size());
        TestSupport.assertEquals("OBS completion reconciles the exact predator set",
            List.of(predatorIds), completedPredators);
        TestSupport.assertTrue("OBS completed predator identities cannot be modified",
            rejects(UnsupportedOperationException.class, () -> completedPredators.get(0).clear()));
        boolean frameBeforeAssessment = true;
        for(PredatorRecruitmentAssessment fact : assessments) {
            frameBeforeAssessment &= order.indexOf("frame:" + fact.getActorId()) <
                order.indexOf("predator:" + fact.getActorId());
        }
        TestSupport.assertTrue("OBS frame callbacks precede joined predator callbacks",
            frameBeforeAssessment);
        TestSupport.assertEquals("OBS step completion precedes trophic publication",
            List.of("complete:1", "trophic:1"), order.subList(order.size() - 2, order.size()));
        TestSupport.assertEquals("OBS duplicate trophic registration publishes one fact", 1,
            trophic.size());

        context.removeStepFrameLedgerListener(frameListener);
        context.removePredatorRecruitmentAssessmentListener(predatorListener);
        context.removeLongHorizonTrophicStepListener(trophicListener);
        simulator.simulateOneStep();
        TestSupport.assertEquals("OBS removed frame observer receives no second-step facts",
            snapshot, delivered);
        TestSupport.assertEquals("OBS removed predator observer receives no completion", 1,
            completedPredators.size());
        TestSupport.assertEquals("OBS removed trophic observer receives no second-step fact", 1,
            trophic.size());
        TestSupport.assertTrue("OBS ledger from an old step cannot be published",
            rejects(IllegalStateException.class, () -> context.recordStepFrameLedger(snapshot.get(0))));
        context.startStep(3, simulator.getField());
        TestSupport.assertTrue("OBS next step clears collected ledgers",
            context.getCurrentStepFrameLedgers().isEmpty());
        TestSupport.assertEquals("OBS earlier ledger snapshot survives step reset",
            ids.size(), snapshot.size());
        context.addPredatorRecruitmentAssessmentListener(predatorListener);
        TestSupport.assertTrue("OBS missing predator assessments fail completion",
            rejects(IllegalStateException.class, () -> context.finishPredatorRecruitmentStep(ids)));
    }

    private static SimulationContext context()
    {
        return new SimulationContext(4, 4, new ResolvedRuntimeConfig(
            SimulationConfig.baseline(), new ObservationConfig(2, 2, 2, 2,
                2, 4, 2, 3, 2), DisplayConfig.defaults()));
    }

    private static SimulationEvent event(int step, long actorId)
    {
        return new SimulationEvent(SimulationEvent.EventType.GRAZE, step,
            1, 1, -1, -1, actorId, -1L, SpeciesRegistry.GAZELLE, "");
    }

    private static boolean rejects(Class<? extends RuntimeException> type, Runnable action)
    {
        try {
            action.run();
            return false;
        }
        catch(RuntimeException exception) {
            return type.isInstance(exception);
        }
    }
}
