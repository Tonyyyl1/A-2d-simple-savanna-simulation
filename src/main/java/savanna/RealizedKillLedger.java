package savanna;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The single bounded cross-step owner of PF1-aF realized-kill records. */
final class RealizedKillLedger
{
    static final int WINDOW_30 = 30;
    static final int WINDOW_60 = 60;
    static final int MAX_RETENTION_STEPS = WINDOW_60;

    private final List<RealizedKillRecord> retained = new ArrayList<>();
    private final Map<Long, List<RealizedKillRecord>> activeByPredator =
        new LinkedHashMap<>();
    private final Set<String> retainedKeys = new HashSet<>();
    private int currentStep;

    synchronized RealizedKillWindowView startStep(int step)
    {
        if(step <= currentStep) {
            throw new IllegalArgumentException(
                "realized-kill steps must increase monotonically");
        }
        currentStep = step;
        pruneBefore(step - MAX_RETENTION_STEPS);
        return new RealizedKillWindowView(step, activeByPredator);
    }

    synchronized void append(RealizedKillRecord record)
    {
        if(record == null || currentStep <= 0 ||
           record.getStep() != currentStep) {
            throw new IllegalArgumentException(
                "realized kill must belong to the current started step");
        }
        if(!retainedKeys.add(record.uniqueKey())) {
            throw new IllegalStateException(
                "duplicate realized-kill key: " + record.uniqueKey());
        }
        retained.add(record);
        activeByPredator.computeIfAbsent(
            Long.valueOf(record.getPredatorId()),
            ignored -> new ArrayList<>()).add(record);
    }

    /** Removes only the active lookup; bounded history remains for evidence. */
    synchronized void onAnimalDeath(long animalId)
    {
        activeByPredator.remove(Long.valueOf(animalId));
    }

    synchronized int getRetainedRecordCount()
    {
        return retained.size();
    }

    synchronized int getActivePredatorCount()
    {
        return activeByPredator.size();
    }

    synchronized int getCurrentStep()
    {
        return currentStep;
    }

    private void pruneBefore(int minimumStep)
    {
        Iterator<RealizedKillRecord> retainedIterator = retained.iterator();
        while(retainedIterator.hasNext()) {
            RealizedKillRecord record = retainedIterator.next();
            if(record.getStep() < minimumStep) {
                retainedIterator.remove();
                retainedKeys.remove(record.uniqueKey());
            }
        }
        Iterator<Map.Entry<Long, List<RealizedKillRecord>>> activeIterator =
            activeByPredator.entrySet().iterator();
        while(activeIterator.hasNext()) {
            Map.Entry<Long, List<RealizedKillRecord>> entry =
                activeIterator.next();
            entry.getValue().removeIf(
                record -> record.getStep() < minimumStep);
            if(entry.getValue().isEmpty()) {
                activeIterator.remove();
            }
        }
    }
}
