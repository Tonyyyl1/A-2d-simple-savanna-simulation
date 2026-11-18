package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Step-start PF1-aF snapshot; same-step HUNT records are necessarily absent. */
public final class RealizedKillWindowView
{
    private final int step;
    private final Map<Long, List<RealizedKillRecord>> recordsByPredator;
    private final List<RealizedKillRecord> records;

    RealizedKillWindowView(int step,
                           Map<Long, List<RealizedKillRecord>> source)
    {
        if(step < 0 || source == null) {
            throw new IllegalArgumentException(
                "step and realized-kill source are required");
        }
        this.step = step;
        Map<Long, List<RealizedKillRecord>> copied = new LinkedHashMap<>();
        List<RealizedKillRecord> flattened = new ArrayList<>();
        for(Map.Entry<Long, List<RealizedKillRecord>> entry :
            source.entrySet()) {
            List<RealizedKillRecord> values = new ArrayList<>();
            for(RealizedKillRecord record : entry.getValue()) {
                if(record == null || record.getPredatorId() !=
                   entry.getKey().longValue() ||
                   record.getStep() >= step) {
                    throw new IllegalStateException(
                        "step-start kill window contains an invalid record");
                }
                values.add(record);
                flattened.add(record);
            }
            values.sort(recordOrder());
            copied.put(entry.getKey(),
                Collections.unmodifiableList(values));
        }
        flattened.sort(recordOrder());
        recordsByPredator = Collections.unmodifiableMap(copied);
        records = Collections.unmodifiableList(flattened);
    }

    public int getStep() { return step; }

    public List<RealizedKillRecord> getRecords()
    {
        return records;
    }

    public RealizedKillWindowFact getWindow(long predatorId, int windowSteps)
    {
        if(predatorId <= 0L ||
           (windowSteps != RealizedKillLedger.WINDOW_30 &&
            windowSteps != RealizedKillLedger.WINDOW_60)) {
            throw new IllegalArgumentException(
                "known predator and registered window are required");
        }
        List<RealizedKillRecord> values = recordsByPredator.get(
            Long.valueOf(predatorId));
        int earliest = step - windowSteps;
        int count = 0;
        int last = -1;
        long offered = 0L;
        long accepted = 0L;
        if(values != null) {
            for(RealizedKillRecord record : values) {
                if(record.getStep() < earliest) continue;
                count++;
                last = Math.max(last, record.getStep());
                offered += record.getOfferedFood();
                accepted += record.getAcceptedFood();
            }
        }
        return new RealizedKillWindowFact(step, predatorId, windowSteps,
            count, last, offered, accepted, step <= windowSteps);
    }

    private static Comparator<RealizedKillRecord> recordOrder()
    {
        return Comparator.comparingInt(RealizedKillRecord::getStep)
            .thenComparingLong(RealizedKillRecord::getPredatorId)
            .thenComparingLong(RealizedKillRecord::getPreyId);
    }
}
