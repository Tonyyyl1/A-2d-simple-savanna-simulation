package savanna;

/** Deterministic command-line proof for the O0b1 frame-event contract. */
public final class FrameEventSummaryProbe
{
    private FrameEventSummaryProbe()
    {
    }

    public static void main(String[] args)
    {
        Field field = new Field(2, 2);
        Gazelle gazelle = new Gazelle(false, new Location(0, 0));
        field.placeAnimal(gazelle, gazelle.getLocation());
        SimulationContext context = new SimulationContext(2, 2);
        EventAccumulator listener = new EventAccumulator();
        context.addEventListener(listener);

        for(int step = 1; step <= 100; step++) {
            context.startStep(step, field);
            int eventCount = step == 100
                ? FrameEventSummary.MAX_REPRESENTATIVE_EVENTS + 7 : 0;
            for(int event = 0; event < eventCount; event++) {
                context.recordEvent(SimulationEvent.graze(step, gazelle,
                                                           gazelle.getLocation()));
            }
        }
        RenderFrame frame100 = RenderFrame.capture(100, field, context, 20, 20);
        FrameEventSummary first = frame100.frameEventSummary;
        require(first.getStartStep() == 1 && first.getEndStep() == 100,
                "step 100 interval is not [1,100]");
        require(first.getTotalEventCount() ==
                FrameEventSummary.MAX_REPRESENTATIVE_EVENTS + 7,
                "dense interval total is incomplete");
        require(first.getTotalEventCount() == listener.getTotalEvents(),
                "listener total does not reconcile");
        require(first.getRepresentativeEvents().size() ==
                FrameEventSummary.MAX_REPRESENTATIVE_EVENTS &&
                first.isRepresentativeSamplesTruncated(),
                "representative sample contract failed");

        context.startStep(101, field);
        context.recordEvent(SimulationEvent.drink(101, gazelle,
                                                   gazelle.getLocation()));
        for(int step = 102; step <= 137; step++) {
            context.startStep(step, field);
        }
        FrameEventSummary partial = RenderFrame.capture(137, field, context,
            20, 20).frameEventSummary;
        require(partial.getStartStep() == 101 && partial.getEndStep() == 137,
                "partial interval is not [101,137]");
        require(frame100.frameEventSummary.getTotalEventCount() ==
                FrameEventSummary.MAX_REPRESENTATIVE_EVENTS + 7,
                "published frame changed after later events");

        System.out.println("O0B1_FRAME_EVENT_SUMMARY PASS interval100=[" +
            first.getStartStep() + ',' + first.getEndStep() + "] total=" +
            first.getTotalEventCount() + " samples=" +
            first.getRepresentativeEvents().size() + " truncated=" +
            first.isRepresentativeSamplesTruncated() + " interval137=[" +
            partial.getStartStep() + ',' + partial.getEndStep() + "] total=" +
            partial.getTotalEventCount());
    }

    private static void require(boolean condition, String message)
    {
        if(!condition) {
            throw new IllegalStateException(message);
        }
    }
}
