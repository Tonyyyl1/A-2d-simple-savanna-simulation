package savanna;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.InflaterInputStream;

/** Reads a {@code .savrec} recording and plays it into a {@link FrameRing} (plan v3.2 V2). */
public final class FrameReplayer
{
    private final byte[] worldJson;
    private final byte[] terrain;
    private final byte[] height;
    private final byte[] bodies;
    private final List<byte[]> frames = new ArrayList<>();
    private final int generation;

    public FrameReplayer(Path file) throws IOException
    {
        try(DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            byte[] magic = new byte[FrameRecorder.MAGIC.length];
            in.readFully(magic);
            if(!java.util.Arrays.equals(magic, FrameRecorder.MAGIC)) {
                throw new IOException("not a SAVREC1 recording: " + file);
            }
            worldJson = readBlock(in);
            terrain = readBlock(in);
            height = readBlock(in);
            bodies = readBlock(in);
            while(true) {
                int length;
                try {
                    length = readInt(in);
                }
                catch(EOFException end) {
                    break;
                }
                byte[] packed = new byte[length];
                in.readFully(packed);
                frames.add(inflate(packed));
            }
        }
        generation = SimFrameCodec.decode(frames.isEmpty() ? emptyFrame() : frames.get(0)).generation;
    }

    private static byte[] emptyFrame()
    {
        return SimFrameCodec.encode(new SimFrame.Builder().build());
    }

    public int getFrameCount() { return frames.size(); }
    public int getGeneration() { return generation; }
    public byte[] getWorldJson() { return worldJson; }
    public byte[] getTerrain() { return terrain; }

    public SimFrame frame(int index)
    {
        return SimFrameCodec.decode(frames.get(index));
    }

    /**
     * Pushes the recording into {@code ring} at {@code framesPerSecond} (0 = as
     * fast as possible), stopping early when {@code stop} says so.
     */
    public void play(FrameRing ring, double framesPerSecond, java.util.function.BooleanSupplier stop)
        throws InterruptedException
    {
        ring.setWorld(generation, worldJson, terrain, height, bodies);
        long interval = framesPerSecond <= 0.0 ? 0L : (long)(1000.0 / framesPerSecond);
        for(byte[] encoded : frames) {
            if(stop.getAsBoolean()) return;
            SimFrame frame = SimFrameCodec.decode(encoded);
            ring.onEncodedFrame(frame.generation, frame.sequence, frame.step, encoded);
            if(interval > 0L) Thread.sleep(interval);
        }
        ring.onRunEnd("replay finished");
    }

    private static byte[] readBlock(DataInputStream in) throws IOException
    {
        byte[] block = new byte[readInt(in)];
        in.readFully(block);
        return block;
    }

    private static int readInt(DataInputStream in) throws IOException
    {
        byte[] raw = new byte[4];
        in.readFully(raw);
        return ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static byte[] inflate(byte[] packed) throws IOException
    {
        try(InputStream zip = new InflaterInputStream(new java.io.ByteArrayInputStream(packed))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(packed.length * 3);
            byte[] chunk = new byte[8192];
            int read;
            while((read = zip.read(chunk)) > 0) out.write(chunk, 0, read);
            return out.toByteArray();
        }
    }
}
