package savanna;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** v3.2 V2: codec round trips, the frame ring, recording/replay and the HTTP server. */
public final class SimServerTests
{
    private SimServerTests()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("V2 codec ring server");
        run();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    public static void run()
    {
        TestSupport.startGroup("V2 codec ring server");
        try {
            testCodecRoundTrip();
            testCodeTablesCoverEnums();
            testRing();
            testRecordReplay();
            testServer();
        }
        catch(Exception e) {
            throw new IllegalStateException(e);
        }
        TestSupport.finishGroup("V2_SERVER");
    }

    private static SimFrame randomFrame(Random random, int animals, boolean grass)
    {
        SimFrame.Builder b = new SimFrame.Builder();
        b.generation = random.nextInt(5);
        b.sequence = random.nextInt(100000);
        b.step = random.nextInt(100000);
        b.hour = random.nextInt(24);
        b.phase = random.nextInt(4);
        b.weather = random.nextInt(4);
        b.flags = random.nextInt(8);
        for(int i = 0; i < 5; i++) b.speciesCounts[i] = random.nextInt(5000);
        b.teleportCount = random.nextInt(3);
        b.id = new long[animals];
        b.species = new byte[animals];
        b.animalFlags = new byte[animals];
        b.col = new short[animals];
        b.row = new short[animals];
        b.prevCol = new short[animals];
        b.prevRow = new short[animals];
        b.action = new byte[animals];
        b.reason = new byte[animals];
        b.effects = new byte[animals];
        b.moveResult = new byte[animals];
        b.age1000 = new short[animals];
        b.food = new byte[animals];
        b.stamina = new byte[animals];
        b.water = new byte[animals];
        b.pathLength = new byte[animals];
        b.target = new long[animals];
        int paths = 0;
        for(int i = 0; i < animals; i++) {
            b.id[i] = 1L + random.nextInt(Integer.MAX_VALUE);
            b.species[i] = (byte)random.nextInt(5);
            b.animalFlags[i] = (byte)random.nextInt(256);
            b.col[i] = (short)random.nextInt(360);
            b.row[i] = (short)random.nextInt(240);
            b.prevCol[i] = (short)random.nextInt(360);
            b.prevRow[i] = (short)random.nextInt(240);
            b.action[i] = (byte)random.nextInt(9);
            b.reason[i] = (byte)random.nextInt(13);
            b.effects[i] = (byte)random.nextInt(128);
            b.moveResult[i] = (byte)random.nextInt(6);
            b.age1000[i] = (short)random.nextInt(1001);
            b.food[i] = (byte)random.nextInt(101);
            b.stamina[i] = (byte)random.nextInt(101);
            b.water[i] = (byte)random.nextInt(101);
            b.pathLength[i] = (byte)random.nextInt(6);
            b.target[i] = random.nextInt(3) == 0 ? 0L : 1L + random.nextInt(1000000);
            paths += b.pathLength[i];
        }
        b.pathCol = new short[paths];
        b.pathRow = new short[paths];
        for(int i = 0; i < paths; i++) {
            b.pathCol[i] = (short)random.nextInt(360);
            b.pathRow[i] = (short)random.nextInt(240);
        }
        int kills = random.nextInt(6);
        b.killPredator = new long[kills];
        b.killPrey = new long[kills];
        b.killCol = new short[kills];
        b.killRow = new short[kills];
        for(int i = 0; i < kills; i++) {
            b.killPredator[i] = 1L + random.nextInt(100000);
            b.killPrey[i] = 1L + random.nextInt(100000);
            b.killCol[i] = (short)random.nextInt(360);
            b.killRow[i] = (short)random.nextInt(240);
        }
        int births = random.nextInt(6);
        b.birthMother = new long[births];
        b.birthChild = new long[births];
        for(int i = 0; i < births; i++) {
            b.birthMother[i] = 1L + random.nextInt(100000);
            b.birthChild[i] = 1L + random.nextInt(100000);
        }
        int deaths = random.nextInt(6);
        b.deathId = new long[deaths];
        b.deathCol = new short[deaths];
        b.deathRow = new short[deaths];
        b.deathSpecies = new byte[deaths];
        b.deathCause = new byte[deaths];
        b.deathFlags = new byte[deaths];
        for(int i = 0; i < deaths; i++) {
            b.deathId[i] = 1L + random.nextInt(100000);
            b.deathCol[i] = (short)random.nextInt(360);
            b.deathRow[i] = (short)random.nextInt(240);
            b.deathSpecies[i] = (byte)random.nextInt(5);
            b.deathCause[i] = (byte)random.nextInt(8);
            b.deathFlags[i] = (byte)random.nextInt(8);
        }
        if(grass) {
            b.grass = new byte[240 * 360];
            random.nextBytes(b.grass);
        }
        return b.build();
    }

    private static boolean same(SimFrame a, SimFrame b)
    {
        return a.generation == b.generation && a.sequence == b.sequence && a.step == b.step &&
            a.hour == b.hour && a.phase == b.phase && a.weather == b.weather &&
            (a.flags & 7) == (b.flags & 7) && java.util.Arrays.equals(a.speciesCounts, b.speciesCounts) &&
            a.teleportCount == b.teleportCount && a.animalCount == b.animalCount &&
            java.util.Arrays.equals(a.id, b.id) && java.util.Arrays.equals(a.species, b.species) &&
            java.util.Arrays.equals(a.animalFlags, b.animalFlags) && java.util.Arrays.equals(a.col, b.col) &&
            java.util.Arrays.equals(a.row, b.row) && java.util.Arrays.equals(a.prevCol, b.prevCol) &&
            java.util.Arrays.equals(a.prevRow, b.prevRow) && java.util.Arrays.equals(a.action, b.action) &&
            java.util.Arrays.equals(a.reason, b.reason) && java.util.Arrays.equals(a.effects, b.effects) &&
            java.util.Arrays.equals(a.moveResult, b.moveResult) && java.util.Arrays.equals(a.age1000, b.age1000) &&
            java.util.Arrays.equals(a.food, b.food) && java.util.Arrays.equals(a.stamina, b.stamina) &&
            java.util.Arrays.equals(a.water, b.water) && java.util.Arrays.equals(a.pathLength, b.pathLength) &&
            java.util.Arrays.equals(a.target, b.target) && java.util.Arrays.equals(a.pathCol, b.pathCol) &&
            java.util.Arrays.equals(a.pathRow, b.pathRow) && java.util.Arrays.equals(a.killPredator, b.killPredator) &&
            java.util.Arrays.equals(a.killPrey, b.killPrey) && java.util.Arrays.equals(a.killCol, b.killCol) &&
            java.util.Arrays.equals(a.killRow, b.killRow) && java.util.Arrays.equals(a.birthMother, b.birthMother) &&
            java.util.Arrays.equals(a.birthChild, b.birthChild) && java.util.Arrays.equals(a.deathId, b.deathId) &&
            java.util.Arrays.equals(a.deathCol, b.deathCol) && java.util.Arrays.equals(a.deathRow, b.deathRow) &&
            java.util.Arrays.equals(a.deathSpecies, b.deathSpecies) && java.util.Arrays.equals(a.deathCause, b.deathCause) &&
            java.util.Arrays.equals(a.deathFlags, b.deathFlags) && java.util.Arrays.equals(a.grass, b.grass);
    }

    private static void testCodecRoundTrip()
    {
        Random random = new Random(42L);
        boolean allSame = true;
        for(int index = 0; index < 1000; index++) {
            SimFrame frame = randomFrame(random, random.nextInt(60), index % 7 == 0);
            // The grass flag is derived from the section, so keep the two consistent.
            byte[] bytes = SimFrameCodec.encode(frame);
            SimFrame back = SimFrameCodec.decode(bytes);
            allSame &= same(frame, back);
            allSame &= bytes.length == SimFrameCodec.HEADER_BYTES + frame.animalCount * 28 +
                frame.pathCol.length * 4 + frame.killPredator.length * 12 + frame.birthMother.length * 8 +
                frame.deathId.length * 12 + (frame.grass == null ? 0 : frame.grass.length);
        }
        TestSupport.assertTrue("V2 1000 random frames survive encode and decode field for field", allSame);
        boolean rejected = false;
        try {
            byte[] bad = SimFrameCodec.encode(randomFrame(random, 3, false));
            bad[4] = 2;
            SimFrameCodec.decode(bad);
        }
        catch(IllegalArgumentException e) {
            rejected = true;
        }
        TestSupport.assertTrue("V2 an unknown protocol version is rejected", rejected);
        boolean idRejected = false;
        try {
            SimFrame.Builder b = new SimFrame.Builder();
            b.id = new long[] {1L << 33};
            b.species = new byte[1];
            b.animalFlags = new byte[1];
            b.col = new short[1];
            b.row = new short[1];
            b.prevCol = new short[1];
            b.prevRow = new short[1];
            b.action = new byte[1];
            b.reason = new byte[1];
            b.effects = new byte[1];
            b.moveResult = new byte[1];
            b.age1000 = new short[1];
            b.food = new byte[1];
            b.stamina = new byte[1];
            b.water = new byte[1];
            b.pathLength = new byte[1];
            b.target = new long[1];
            SimFrameCodec.encode(b.build());
        }
        catch(IllegalArgumentException e) {
            idRejected = true;
        }
        TestSupport.assertTrue("V2 an animal id above 32 bits is rejected by the encoder", idRejected);
    }

    private static void testCodeTablesCoverEnums()
    {
        boolean covered = true;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for(ActionType value : ActionType.values()) covered &= seen.add(SimFrameBuilder.actionCode(value)) && SimFrameBuilder.actionCode(value) >= 1;
        seen.clear();
        for(BehaviorReason value : BehaviorReason.values()) covered &= seen.add(SimFrameBuilder.reasonCode(value)) && SimFrameBuilder.reasonCode(value) >= 1;
        seen.clear();
        for(DeathCause value : DeathCause.values()) covered &= seen.add(SimFrameBuilder.causeCode(value));
        seen.clear();
        for(MovementDisposition value : MovementDisposition.values()) covered &= seen.add(SimFrameBuilder.moveCode(value));
        seen.clear();
        for(DayPhase value : DayPhase.values()) covered &= seen.add(SimFrameBuilder.phaseCode(value));
        seen.clear();
        for(WeatherType value : WeatherType.values()) covered &= seen.add(SimFrameBuilder.weatherCode(value));
        seen.clear();
        for(LifeStage value : LifeStage.values()) covered &= seen.add(SimFrameBuilder.lifeStageCode(value));
        seen.clear();
        for(TerrainType value : TerrainType.values()) covered &= seen.add(WorldDescriptor.terrainCode(value));
        seen.clear();
        for(ExecutedEffect value : ExecutedEffect.values()) {
            covered &= seen.add(SimFrameBuilder.effectBits(java.util.Collections.singletonList(value)));
        }
        for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
            covered &= SimFrameBuilder.speciesCode(factory.getProfile().getName()) >= 0;
        }
        TestSupport.assertTrue("V2 explicit code tables cover every enum constant, each code once", covered);
    }

    private static void testRing() throws Exception
    {
        FrameRing ring = new FrameRing();
        ring.setWorld(3, "{}".getBytes(), new byte[0]);
        Random random = new Random(1L);
        for(int sequence = 0; sequence < 100; sequence++) {
            SimFrame.Builder b = randomFrame(random, 0, false) == null ? null : new SimFrame.Builder();
            b.generation = 3;
            b.sequence = sequence;
            b.step = sequence;
            ring.onFrame(b.build());
        }
        FrameRing.Result tail = ring.after(3, 97, 8, 0L);
        TestSupport.assertEquals("V2 ring: frames after 97 are 98 and 99", 2, tail.frames.size());
        FrameRing.Result old = ring.after(3, -1, 100, 0L);
        TestSupport.assertEquals("V2 ring keeps only the newest 64 frames", FrameRing.CAPACITY, old.frames.size());
        TestSupport.assertEquals("V2 a reader older than the buffer starts at the oldest kept frame", 36,
            SimFrameCodec.decode(old.frames.get(0)).sequence);
        TestSupport.assertEquals("V2 ring: wrong generation is reported", FrameRing.Status.GENERATION_MISMATCH,
            ring.after(2, 0, 8, 0L).status);
        long started = System.currentTimeMillis();
        TestSupport.assertEquals("V2 ring: nothing new yields NO_CONTENT after the wait", FrameRing.Status.NO_CONTENT,
            ring.after(3, 99, 8, 120L).status);
        TestSupport.assertTrue("V2 ring: the wait lasted about the requested time",
            System.currentTimeMillis() - started >= 100L);
        ring.setWorld(4, "{}".getBytes(), new byte[0]);
        TestSupport.assertEquals("V2 ring: a new generation clears the old frames", 0,
            ring.after(4, -1, 8, 0L).frames.size());
    }

    private static void testRecordReplay() throws Exception
    {
        Path file = Files.createTempFile("savanna-test", ".savrec");
        try {
            Simulator simulator = new Simulator(SimulationConfig.builder().randomSeed(1111L).build(), false);
            simulator.setVerbose(false);
            FrameRecorder recorder = new FrameRecorder(file);
            simulator.addFrameSink(recorder);
            List<int[]> counts = new ArrayList<>();
            FrameRing live = new FrameRing();
            simulator.addFrameSink(live);
            for(int step = 0; step < 20; step++) simulator.simulateOneStep();
            recorder.close();
            FrameReplayer replayer = new FrameReplayer(file);
            TestSupport.assertEquals("V2 recording holds frame 0 plus 20 steps", 21, replayer.getFrameCount());
            FrameRing replayRing = new FrameRing();
            replayer.play(replayRing, 0.0, () -> false);
            FrameRing.Result liveFrames = live.after(live.getGeneration(), -1, 64, 0L);
            FrameRing.Result replayFrames = replayRing.after(replayer.getGeneration(), -1, 64, 0L);
            boolean same = liveFrames.frames.size() == replayFrames.frames.size();
            for(int i = 0; same && i < liveFrames.frames.size(); i++) {
                same = java.util.Arrays.equals(liveFrames.frames.get(i), replayFrames.frames.get(i));
            }
            TestSupport.assertTrue("V2 the replayed frames equal the live frames byte for byte", same);
            SimFrame lastLive = SimFrameCodec.decode(liveFrames.frames.get(liveFrames.frames.size() - 1));
            TestSupport.assertEquals("V2 replayed species counts match the recording", java.util.Arrays.toString(
                lastLive.speciesCounts), java.util.Arrays.toString(replayer.frame(20).speciesCounts));
        }
        finally {
            Files.deleteIfExists(file);
        }
    }

    private static void testServer() throws Exception
    {
        SimServer server = new SimServer(SimulationConfig.builder().randomSeed(1111L).build(), 200, 0.0, null, null);
        server.start(0);
        try {
            String base = "http://127.0.0.1:" + server.getPort();
            String state = null;
            for(int tries = 0; tries < 100 && state == null; tries++) {
                Thread.sleep(100);
                Object[] response = get(base + "/api/world");
                if((Integer)response[0] == 200) state = "ready";
            }
            TestSupport.assertTrue("V2 server: the world description is served", state != null);
            Object[] stateResponse = get(base + "/api/state");
            TestSupport.assertEquals("V2 server: /api/state answers 200", 200, (int)(Integer)stateResponse[0]);
            Object[] escape = get(base + "/../../etc/passwd");
            TestSupport.assertEquals("V2 server: a parent path is a 404", 404, (int)(Integer)escape[0]);
            Object[] encoded = get(base + "/%2e%2e/secret");
            TestSupport.assertEquals("V2 server: an encoded parent path is a 404", 404, (int)(Integer)encoded[0]);
            Object[] frames = get(base + "/api/frames?gen=1&after=-1&max=8&waitMs=3000");
            TestSupport.assertEquals("V2 server: frames are returned", 200, (int)(Integer)frames[0]);
            byte[] body = (byte[])frames[1];
            ByteBuffer in = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
            int previous = -1;
            boolean consecutive = true;
            int decoded = 0;
            while(in.remaining() > 4) {
                byte[] one = new byte[in.getInt()];
                in.get(one);
                SimFrame frame = SimFrameCodec.decode(one);
                consecutive &= previous < 0 || frame.sequence == previous + 1;
                previous = frame.sequence;
                decoded++;
            }
            TestSupport.assertTrue("V2 server: returned frames decode and are consecutive", decoded > 0 && consecutive);
            Object[] wrong = get(base + "/api/frames?gen=99&after=-1&waitMs=0");
            TestSupport.assertEquals("V2 server: a wrong generation is 409", 409, (int)(Integer)wrong[0]);
            Object[] reset = post(base + "/api/control", "{\"cmd\":\"reset\",\"seed\":2111}");
            TestSupport.assertEquals("V2 server: reset is accepted", 200, (int)(Integer)reset[0]);
            boolean advanced = false;
            for(int tries = 0; tries < 100 && !advanced; tries++) {
                Thread.sleep(100);
                String json = new String((byte[])get(base + "/api/state")[1]);
                advanced = json.contains("\"runGeneration\":2");
            }
            TestSupport.assertTrue("V2 server: reset raises the run generation to 2", advanced);
        }
        finally {
            server.shutdown();
        }
    }

    private static Object[] get(String url) throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(15000);
        int status = connection.getResponseCode();
        InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        byte[] body = in == null ? new byte[0] : in.readAllBytes();
        return new Object[] {status, body};
    }

    private static Object[] post(String url, String json) throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.getOutputStream().write(json.getBytes());
        int status = connection.getResponseCode();
        InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        return new Object[] {status, in == null ? new byte[0] : in.readAllBytes()};
    }
}
