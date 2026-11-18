package savanna;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Local simulation server for the browser client (plan v3.2 V2). One process:
 * a simulation thread owns the {@link Simulator} and pushes frames into a
 * {@link FrameRing}; HTTP threads (bound to 127.0.0.1 only) read the ring and a
 * small locked control state. Static files come from the class path under
 * {@code /web/}. There is no endpoint that writes files.
 *
 * <p>Arguments: {@code --port=8787 --seed=1111 --terrain-seed=20260629
 * --terrain-profile=LEGACY_V1 --thirst=off --steps=200000 --speed=1
 * --record=<file> --replay=<file> --no-open}. {@code --speed} is steps per
 * second (0 = unlimited).</p>
 */
public final class SimServer
{
    private final FrameRing ring = new FrameRing();
    private final Object lock = new Object();
    private final int stepBudget;
    private final String recordFile;
    private final String replayFile;

    // Control state, guarded by lock.
    private boolean paused;
    private double stepsPerSecond;
    private boolean stepOnce;
    private boolean stopRequested;
    private SimulationConfig pendingReset;
    private boolean running;
    private boolean replay;
    private SimulationConfig currentConfig;
    private int runGeneration = 0;

    private HttpServer http;
    private Thread simulationThread;

    public SimServer(SimulationConfig config, int stepBudget, double stepsPerSecond,
                     String recordFile, String replayFile)
    {
        this.currentConfig = config;
        this.stepBudget = stepBudget;
        this.stepsPerSecond = stepsPerSecond;
        this.recordFile = recordFile;
        this.replayFile = replayFile;
    }

    public static void main(String[] args) throws Exception
    {
        int port = 8787;
        long seed = 1111L;
        long terrainSeed = SimulationConfig.builder().build().getTerrainSeed();
        TerrainGenerationProfile profile = TerrainGenerationProfile.LEGACY_V1;
        boolean thirst = false;
        int steps = 200_000;
        double speed = 0.5;
        String record = null;
        String replayPath = null;
        boolean open = true;
        for(String arg : args) {
            if(arg.startsWith("--port=")) port = Integer.parseInt(arg.substring(7));
            else if(arg.startsWith("--seed=")) seed = Long.parseLong(arg.substring(7));
            else if(arg.startsWith("--terrain-seed=")) terrainSeed = Long.parseLong(arg.substring(15));
            else if(arg.startsWith("--terrain-profile=")) {
                profile = TerrainGenerationProfile.valueOf(arg.substring(18));
            }
            else if(arg.startsWith("--thirst=")) thirst = "on".equals(arg.substring(9));
            else if(arg.startsWith("--steps=")) steps = Integer.parseInt(arg.substring(8));
            else if(arg.startsWith("--speed=")) speed = Double.parseDouble(arg.substring(8));
            else if(arg.startsWith("--record=")) record = arg.substring(9);
            else if(arg.startsWith("--replay=")) replayPath = arg.substring(9);
            else if("--no-open".equals(arg)) open = false;
            else {
                System.err.println("unknown option: " + arg);
                System.exit(2);
            }
        }
        SimulationConfig config = SimulationConfig.builder().randomSeed(seed)
            .terrainSeed(terrainSeed).terrainGenerationProfile(profile).thirstEnabled(thirst).build();
        SimServer server = new SimServer(config, steps, speed, record, replayPath);
        server.start(port);
        String url = "http://127.0.0.1:" + server.getPort() + "/";
        System.out.println("SimServer listening on " + url);
        if(open) openBrowser(url);
        server.join();
    }

    public void start(int port) throws IOException
    {
        http = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 64);
        http.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "sim-http");
            thread.setDaemon(true);
            return thread;
        }));
        http.createContext("/api/state", this::handleState);
        http.createContext("/api/world", this::handleWorld);
        http.createContext("/api/frames", this::handleFrames);
        http.createContext("/api/control", this::handleControl);
        http.createContext("/", this::handleStatic);
        http.start();
        simulationThread = new Thread(this::simulationMain, "sim-main");
        simulationThread.setDaemon(false);
        simulationThread.start();
    }

    public int getPort()
    {
        return http.getAddress().getPort();
    }

    public void join() throws InterruptedException
    {
        simulationThread.join();
        http.stop(1);
    }

    public void shutdown()
    {
        synchronized(lock) {
            stopRequested = true;
            lock.notifyAll();
        }
        try {
            simulationThread.join(5000);
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        http.stop(0);
    }

    // ---- simulation thread ----

    private void simulationMain()
    {
        try {
            if(replayFile != null) playReplay();
            else runLive();
        }
        catch(Exception e) {
            System.err.println("simulation thread failed: " + e);
            e.printStackTrace();
        }
        finally {
            synchronized(lock) {
                running = false;
            }
        }
    }

    private void playReplay() throws Exception
    {
        FrameReplayer replayer = new FrameReplayer(Path.of(replayFile));
        synchronized(lock) {
            replay = true;
            running = true;
        }
        replayer.play(ring, stepsPerSecondNow(), () -> {
            synchronized(lock) {
                return stopRequested;
            }
        });
        waitUntilStopped();
    }

    private double stepsPerSecondNow()
    {
        synchronized(lock) {
            return stepsPerSecond;
        }
    }

    private void waitUntilStopped() throws InterruptedException
    {
        synchronized(lock) {
            while(!stopRequested) lock.wait(500);
        }
    }

    private void runLive() throws Exception
    {
        FrameRecorder recorder = recordFile == null ? null : new FrameRecorder(Path.of(recordFile));
        int generation = 0;
        while(true) {
            SimulationConfig config;
            synchronized(lock) {
                config = currentConfig;
                pendingReset = null;
                running = true;
            }
            Simulator simulator = new Simulator(config, false);
            simulator.setVerbose(false);
            simulator.setInitialRunGeneration(generation + 1);
            simulator.addFrameSink(ring);
            if(recorder != null && generation == 0) simulator.addFrameSink(recorder);
            generation = simulator.getRunGeneration();
            synchronized(lock) {
                runGeneration = generation;
            }
            String endReason = "step budget reached";
            int done = 0;
            while(done < stepBudget) {
                long started = System.nanoTime();
                boolean doStep;
                synchronized(lock) {
                    while(paused && !stepOnce && !stopRequested && pendingReset == null) {
                        lock.wait(200);
                    }
                    if(stopRequested) {
                        endReason = "stopped";
                        break;
                    }
                    if(pendingReset != null) {
                        endReason = "reset";
                        break;
                    }
                    doStep = true;
                    stepOnce = false;
                }
                if(doStep) {
                    simulator.simulateOneStep();
                    done++;
                }
                if(!simulator.getField().isViable()) {
                    endReason = "extinction";
                    break;
                }
                double rate = stepsPerSecondNow();
                if(rate > 0.0) {
                    long budget = (long)(1_000_000_000L / rate);
                    long left = budget - (System.nanoTime() - started);
                    if(left > 0L) {
                        synchronized(lock) {
                            long millis = left / 1_000_000L;
                            if(!stopRequested && pendingReset == null && !stepOnce) {
                                lock.wait(Math.max(1L, millis));
                            }
                        }
                    }
                }
            }
            ring.onRunEnd(endReason);
            if(recorder != null && generation == 1) recorder.onRunEnd(endReason);
            SimulationConfig next;
            synchronized(lock) {
                if(stopRequested) return;
                next = pendingReset;
            }
            if(next != null) {
                synchronized(lock) {
                    currentConfig = next;
                }
                continue;
            }
            // The run ended by itself: keep serving its frames until stopped or reset.
            synchronized(lock) {
                running = false;
                while(!stopRequested && pendingReset == null) lock.wait(500);
                if(stopRequested) return;
            }
        }
    }

    // ---- HTTP handlers ----

    private void handleState(HttpExchange exchange) throws IOException
    {
        String json;
        synchronized(lock) {
            json = "{\"runGeneration\":" + ring.getGeneration() +
                ",\"latestSequence\":" + ring.getLatestSequence() +
                ",\"step\":" + ring.getLatestStep() +
                ",\"paused\":" + paused +
                ",\"stepsPerSecond\":" + stepsPerSecond +
                ",\"running\":" + (running && !ring.isEnded()) +
                ",\"replay\":" + replay + "}";
        }
        sendJson(exchange, 200, json);
    }

    private void handleWorld(HttpExchange exchange) throws IOException
    {
        String path = exchange.getRequestURI().getPath();
        if("/api/world".equals(path)) {
            byte[] json = ring.getWorldJson();
            if(json == null) {
                sendJson(exchange, 503, "{\"error\":\"world not ready\"}");
                return;
            }
            send(exchange, 200, "application/json", json);
        }
        else if("/api/world/terrain.bin".equals(path)) {
            byte[] terrain = ring.getTerrainBytes();
            if(terrain == null) {
                sendJson(exchange, 503, "{\"error\":\"world not ready\"}");
                return;
            }
            send(exchange, 200, "application/octet-stream", terrain);
        }
        else if("/api/world/height.bin".equals(path) || "/api/world/waterbody.bin".equals(path)) {
            byte[] data = path.endsWith("height.bin") ? ring.getHeightBytes() : ring.getBodyBytes();
            if(data == null) {
                sendJson(exchange, 503, "{\"error\":\"world not ready\"}");
                return;
            }
            send(exchange, 200, "application/octet-stream", data);
        }
        else {
            sendJson(exchange, 404, "{\"error\":\"not found\"}");
        }
    }

    private void handleFrames(HttpExchange exchange) throws IOException
    {
        Map<String, String> query = query(exchange);
        try {
            int generation = Integer.parseInt(query.getOrDefault("gen", "-1"));
            int after = Integer.parseInt(query.getOrDefault("after", "-1"));
            int max = Integer.parseInt(query.getOrDefault("max", "8"));
            long wait = Long.parseLong(query.getOrDefault("waitMs", "5000"));
            FrameRing.Result result = ring.after(generation, after, max, Math.min(wait, 30_000L));
            if(result.status == FrameRing.Status.GENERATION_MISMATCH) {
                sendJson(exchange, 409, "{\"runGeneration\":" + result.runGeneration + "}");
            }
            else if(result.status == FrameRing.Status.NO_CONTENT) {
                exchange.getResponseHeaders().add("X-Run-Generation", Integer.toString(result.runGeneration));
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            }
            else {
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                for(byte[] frame : result.frames) {
                    body.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(frame.length).array());
                    body.write(frame);
                }
                exchange.getResponseHeaders().add("X-Run-Generation", Integer.toString(result.runGeneration));
                send(exchange, 200, "application/octet-stream", body.toByteArray());
            }
        }
        catch(NumberFormatException e) {
            sendJson(exchange, 400, "{\"error\":\"bad number\"}");
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            exchange.close();
        }
    }

    private void handleControl(HttpExchange exchange) throws IOException
    {
        if(!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"POST only\"}");
            return;
        }
        String body = new String(readAll(exchange.getRequestBody(), 4096), StandardCharsets.UTF_8);
        String command = jsonString(body, "cmd");
        synchronized(lock) {
            switch(command == null ? "" : command) {
                case "pause": paused = true; break;
                case "resume": paused = false; break;
                case "step": paused = true; stepOnce = true; break;
                case "setSpeed": {
                    Double value = jsonNumber(body, "stepsPerSecond");
                    if(value == null || value < 0.0) {
                        sendJson(exchange, 400, "{\"error\":\"stepsPerSecond required\"}");
                        return;
                    }
                    stepsPerSecond = value;
                    break;
                }
                case "reset": {
                    if(replay) {
                        sendJson(exchange, 409, "{\"error\":\"replay cannot be reset\"}");
                        return;
                    }
                    SimulationConfig.Builder builder = SimulationConfig.builder();
                    Double seed = jsonNumber(body, "seed");
                    Double terrainSeed = jsonNumber(body, "terrainSeed");
                    String profile = jsonString(body, "terrainProfile");
                    String thirst = jsonString(body, "thirst");
                    builder.randomSeed(seed == null ? currentConfig.getRandomSeed() : seed.longValue());
                    builder.terrainSeed(terrainSeed == null ? currentConfig.getTerrainSeed()
                        : terrainSeed.longValue());
                    try {
                        builder.terrainGenerationProfile(profile == null
                            ? currentConfig.getTerrainGenerationProfile()
                            : TerrainGenerationProfile.valueOf(profile));
                    }
                    catch(IllegalArgumentException e) {
                        sendJson(exchange, 400, "{\"error\":\"unknown terrainProfile\"}");
                        return;
                    }
                    builder.thirstEnabled(thirst == null ? currentConfig.isThirstEnabled()
                        : "on".equals(thirst));
                    pendingReset = builder.build();
                    paused = false;
                    break;
                }
                case "stop": stopRequested = true; break;
                default:
                    sendJson(exchange, 400, "{\"error\":\"unknown cmd\"}");
                    return;
            }
            lock.notifyAll();
        }
        handleState(exchange);
    }

    private void handleStatic(HttpExchange exchange) throws IOException
    {
        String path = exchange.getRequestURI().getPath();
        if(!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"GET only\"}");
            return;
        }
        String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);
        if(decoded.contains("..") || decoded.contains("\\") || decoded.contains("\0")) {
            sendJson(exchange, 404, "{\"error\":\"not found\"}");
            return;
        }
        String name = "/".equals(decoded) ? "/index.html" : decoded;
        if(!name.startsWith("/")) name = "/" + name;
        try(InputStream stream = SimServer.class.getResourceAsStream("/web" + name)) {
            if(stream == null) {
                sendJson(exchange, 404, "{\"error\":\"not found\"}");
                return;
            }
            send(exchange, 200, contentType(name), readAll(stream, 64 * 1024 * 1024));
        }
    }

    // ---- helpers ----

    static String contentType(String name)
    {
        String lower = name.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".html")) return "text/html; charset=utf-8";
        if(lower.endsWith(".js") || lower.endsWith(".mjs")) return "text/javascript; charset=utf-8";
        if(lower.endsWith(".css")) return "text/css; charset=utf-8";
        if(lower.endsWith(".json")) return "application/json";
        if(lower.endsWith(".glb")) return "model/gltf-binary";
        if(lower.endsWith(".bin")) return "application/octet-stream";
        if(lower.endsWith(".png")) return "image/png";
        if(lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if(lower.endsWith(".webp")) return "image/webp";
        if(lower.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private static Map<String, String> query(HttpExchange exchange)
    {
        Map<String, String> values = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if(raw == null) return values;
        for(String pair : raw.split("&")) {
            int equals = pair.indexOf('=');
            if(equals > 0) {
                values.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    /** Tiny extraction for flat request bodies such as {"cmd":"pause"}; not a general JSON parser. */
    static String jsonString(String body, String key)
    {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    static Double jsonNumber(String body, String key)
    {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("\"" + key + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)").matcher(body);
        return matcher.find() ? Double.valueOf(matcher.group(1)) : null;
    }

    private static byte[] readAll(InputStream in, int limit) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while((read = in.read(chunk)) > 0) {
            out.write(chunk, 0, read);
            if(out.size() > limit) throw new IOException("request or resource too large");
        }
        return out.toByteArray();
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException
    {
        send(exchange, status, "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException
    {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if(body.length > 0) {
            try(OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        else {
            exchange.close();
        }
    }

    private static void openBrowser(String url)
    {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if(os.contains("mac")) new ProcessBuilder("open", url).start();
            else if(os.contains("win")) new ProcessBuilder("cmd", "/c", "start", url).start();
            else new ProcessBuilder("xdg-open", url).start();
        }
        catch(IOException e) {
            System.out.println("open " + url + " in a browser");
        }
    }
}
