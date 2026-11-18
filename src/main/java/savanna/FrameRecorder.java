package savanna;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Writes a {@code .savrec} recording (plan v3.2 V2): magic {@code SAVREC1\n},
 * the world description (four length-prefixed blocks: JSON, terrain bytes,
 * height bytes, water body bytes) and then {@code [u32 compressed length][deflated frame]}
 * records. Only the first generation seen is recorded.
 */
public final class FrameRecorder implements FrameSink
{
    static final byte[] MAGIC = "SAVREC1\n".getBytes(StandardCharsets.US_ASCII);

    private final DataOutputStream out;
    private int recordedGeneration = -1;
    private boolean closed;

    public FrameRecorder(Path file) throws IOException
    {
        out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)));
        out.write(MAGIC);
    }

    @Override
    public synchronized void onWorld(WorldDescriptor world)
    {
        if(closed || recordedGeneration >= 0) return;
        recordedGeneration = world.runGeneration;
        try {
            byte[] json = WorldJson.toJson(world);
            out.write(le(json.length));
            out.write(json);
            byte[] terrain = world.terrainBytes();
            out.write(le(terrain.length));
            out.write(terrain);
            byte[] height = world.heightBytes();
            out.write(le(height.length));
            out.write(height);
            byte[] bodies = world.bodyBytes();
            out.write(le(bodies.length));
            out.write(bodies);
        }
        catch(IOException e) {
            throw new IllegalStateException("cannot write recording", e);
        }
    }

    @Override
    public synchronized void onFrame(SimFrame frame)
    {
        if(closed || frame.generation != recordedGeneration) return;
        try {
            byte[] raw = SimFrameCodec.encode(frame);
            java.io.ByteArrayOutputStream packed = new java.io.ByteArrayOutputStream(raw.length / 2 + 64);
            Deflater deflater = new Deflater(Deflater.BEST_SPEED);
            try(OutputStream zip = new DeflaterOutputStream(packed, deflater)) {
                zip.write(raw);
            }
            deflater.end();
            out.write(le(packed.size()));
            packed.writeTo(out);
        }
        catch(IOException e) {
            throw new IllegalStateException("cannot write recording", e);
        }
    }

    @Override
    public synchronized void onRunEnd(String reason)
    {
        close();
    }

    public synchronized void close()
    {
        if(closed) return;
        closed = true;
        try {
            out.close();
        }
        catch(IOException e) {
            throw new IllegalStateException("cannot close recording", e);
        }
    }

    static byte[] le(int value)
    {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }
}
