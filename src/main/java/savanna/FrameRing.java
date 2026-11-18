package savanna;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Newest encoded frames, thread safe (plan v3.2 V2). The simulation thread
 * pushes through the {@link FrameSink} methods; HTTP threads read with
 * {@link #after}. Frames of an older generation are dropped when a new world
 * arrives.
 */
public final class FrameRing implements FrameSink
{
    public static final int CAPACITY = 64;

    /** Outcome of {@link #after}. */
    public enum Status { OK, NO_CONTENT, GENERATION_MISMATCH }

    public static final class Result
    {
        public final Status status;
        public final List<byte[]> frames;
        public final int runGeneration;

        Result(Status status, List<byte[]> frames, int runGeneration)
        {
            this.status = status;
            this.frames = frames;
            this.runGeneration = runGeneration;
        }
    }

    private final ArrayDeque<byte[]> frames = new ArrayDeque<>();
    private final ArrayDeque<Integer> sequences = new ArrayDeque<>();
    private int generation = -1;
    private int latestSequence = -1;
    private int latestStep = -1;
    private byte[] worldJson;
    private byte[] terrainBytes;
    private byte[] heightBytes;
    private byte[] bodyBytes;
    private boolean ended;
    private String endReason = "";

    @Override
    public synchronized void onWorld(WorldDescriptor world)
    {
        setWorld(world.runGeneration, WorldJson.toJson(world), world.terrainBytes(),
            world.heightBytes(), world.bodyBytes());
    }

    /** Replay path: the world arrives already serialised. */
    public synchronized void setWorld(int runGeneration, byte[] json, byte[] terrain)
    {
        setWorld(runGeneration, json, terrain, new byte[0], new byte[0]);
    }

    public synchronized void setWorld(int runGeneration, byte[] json, byte[] terrain,
                                      byte[] height, byte[] bodies)
    {
        if(runGeneration != generation) {
            frames.clear();
            sequences.clear();
            latestSequence = -1;
            latestStep = -1;
        }
        generation = runGeneration;
        worldJson = json;
        terrainBytes = terrain;
        heightBytes = height;
        bodyBytes = bodies;
        ended = false;
        endReason = "";
        notifyAll();
    }

    @Override
    public synchronized void onFrame(SimFrame frame)
    {
        onEncodedFrame(frame.generation, frame.sequence, frame.step, SimFrameCodec.encode(frame));
    }

    public synchronized void onEncodedFrame(int frameGeneration, int sequence, int step, byte[] encoded)
    {
        if(frameGeneration != generation) return;
        frames.addLast(encoded);
        sequences.addLast(sequence);
        while(frames.size() > CAPACITY) {
            frames.removeFirst();
            sequences.removeFirst();
        }
        latestSequence = sequence;
        latestStep = step;
        notifyAll();
    }

    @Override
    public synchronized void onRunEnd(String reason)
    {
        ended = true;
        endReason = reason == null ? "" : reason;
        notifyAll();
    }

    public synchronized int getGeneration() { return generation; }
    public synchronized int getLatestSequence() { return latestSequence; }
    public synchronized int getLatestStep() { return latestStep; }
    public synchronized byte[] getWorldJson() { return worldJson; }
    public synchronized byte[] getTerrainBytes() { return terrainBytes; }
    public synchronized byte[] getHeightBytes() { return heightBytes; }
    public synchronized byte[] getBodyBytes() { return bodyBytes; }
    public synchronized boolean isEnded() { return ended; }
    public synchronized String getEndReason() { return endReason; }

    /**
     * Frames with a sequence number above {@code afterSequence}, at most
     * {@code max}; waits up to {@code waitMillis} when there are none. When
     * {@code afterSequence} is older than the oldest kept frame the oldest is
     * returned first (the reader sees the gap in the sequence numbers).
     */
    public synchronized Result after(int requestedGeneration, int afterSequence, int max,
                                     long waitMillis) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + Math.max(0L, waitMillis);
        while(true) {
            if(requestedGeneration != generation) {
                return new Result(Status.GENERATION_MISMATCH, new ArrayList<>(), generation);
            }
            List<byte[]> out = new ArrayList<>();
            java.util.Iterator<byte[]> frameIterator = frames.iterator();
            java.util.Iterator<Integer> sequenceIterator = sequences.iterator();
            while(frameIterator.hasNext() && out.size() < Math.max(1, max)) {
                byte[] frame = frameIterator.next();
                int sequence = sequenceIterator.next();
                if(sequence > afterSequence) out.add(frame);
            }
            if(!out.isEmpty()) return new Result(Status.OK, out, generation);
            long remaining = deadline - System.currentTimeMillis();
            if(remaining <= 0L) return new Result(Status.NO_CONTENT, out, generation);
            wait(remaining);
        }
    }
}
