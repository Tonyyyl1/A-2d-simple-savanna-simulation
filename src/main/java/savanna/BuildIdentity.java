package savanna;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Properties;
import java.util.Set;

/** Immutable identity stamped into a canonical development build. */
public final class BuildIdentity
{
    public static final String RESOURCE = "META-INF/savanna-build.properties";
    private static final String UNSTAMPED_BUILD = "UNSTAMPED-BLUEJ";
    private static volatile BuildIdentity cached;

    private final String buildId;
    private final String sourceHash;
    private final String testSourceHash;
    private final String runtimeTreeHash;
    private final String mainClass;
    private final String builtAtUtc;
    private final URI codeSource;
    private final boolean stamped;

    private BuildIdentity(String buildId, String sourceHash,
                          String testSourceHash, String runtimeTreeHash,
                          String mainClass,
                          String builtAtUtc, URI codeSource, boolean stamped)
    {
        this.buildId = clean(buildId, UNSTAMPED_BUILD);
        this.sourceHash = clean(sourceHash, "UNSTAMPED");
        this.testSourceHash = clean(testSourceHash, "UNSTAMPED");
        this.runtimeTreeHash = clean(runtimeTreeHash, "UNSTAMPED");
        this.mainClass = clean(mainClass, "VisualSimulationRunner");
        this.builtAtUtc = clean(builtAtUtc, "unknown");
        this.codeSource = codeSource;
        this.stamped = stamped;
    }

    public static BuildIdentity current()
    {
        BuildIdentity value = cached;
        if(value == null) {
            synchronized(BuildIdentity.class) {
                value = cached;
                if(value == null) {
                    value = load();
                    cached = value;
                }
            }
        }
        return value;
    }

    private static BuildIdentity load()
    {
        Properties properties = new Properties();
        boolean stamped = false;
        ClassLoader loader = BuildIdentity.class.getClassLoader();
        try(InputStream input = loader == null
                ? ClassLoader.getSystemResourceAsStream(RESOURCE)
                : loader.getResourceAsStream(RESOURCE)) {
            if(input != null) {
                properties.load(input);
                stamped = true;
            }
        }
        catch(IOException e) {
            throw new IllegalStateException("Could not read build identity stamp.", e);
        }
        if(stamped) {
            validateStampedProperties(properties);
        }
        return new BuildIdentity(
            properties.getProperty("build.id"),
            properties.getProperty("source.hash"),
            properties.getProperty("test.source.hash"),
            properties.getProperty("runtime.tree.hash"),
            properties.getProperty("main.class"),
            properties.getProperty("built.at.utc"),
            locateCodeSource(BuildIdentity.class), stamped);
    }

    private static void validateStampedProperties(Properties properties)
    {
        Set<String> required = Set.of("build.id", "source.hash",
            "test.source.hash", "runtime.tree.hash", "main.class",
            "built.at.utc");
        if(!properties.stringPropertyNames().equals(required)) {
            throw new IllegalStateException(
                "Malformed build identity stamp fields: expected " + required +
                " but found " + properties.stringPropertyNames());
        }
        String buildId = required(properties, "build.id");
        String sourceHash = required(properties, "source.hash");
        String testSourceHash = required(properties, "test.source.hash");
        String runtimeTreeHash = required(properties, "runtime.tree.hash");
        String mainClass = required(properties, "main.class");
        String builtAtUtc = required(properties, "built.at.utc");
        if(!buildId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalStateException(
                "Malformed build identity stamp build.id: " + buildId);
        }
        if(!sourceHash.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException(
                "Malformed build identity stamp source.hash: " + sourceHash);
        }
        if(!testSourceHash.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException(
                "Malformed build identity stamp test.source.hash: " +
                testSourceHash);
        }
        if(!runtimeTreeHash.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException(
                "Malformed build identity stamp runtime.tree.hash: " +
                runtimeTreeHash);
        }
        if(!"savanna.VisualSimulationRunner".equals(mainClass)) {
            throw new IllegalStateException(
                "Malformed build identity stamp main.class: " + mainClass);
        }
        try {
            Instant.parse(builtAtUtc);
        }
        catch(DateTimeException exception) {
            throw new IllegalStateException(
                "Malformed build identity stamp built.at.utc: " + builtAtUtc,
                exception);
        }
    }

    private static String required(Properties properties, String key)
    {
        String value = properties.getProperty(key);
        if(value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(
                "Malformed build identity stamp " + key + ": missing or empty");
        }
        return value.trim();
    }

    public static URI locateCodeSource(Class<?> type)
    {
        if(type == null) {
            return URI.create("unknown:/");
        }
        CodeSource source = type.getProtectionDomain() == null ? null
            : type.getProtectionDomain().getCodeSource();
        URL location = source == null ? null : source.getLocation();
        if(location == null) {
            return URI.create("unknown:/");
        }
        try {
            return location.toURI().normalize();
        }
        catch(URISyntaxException e) {
            throw new IllegalStateException("Invalid CodeSource for " +
                                            type.getName(), e);
        }
    }

    private static String clean(String value, String fallback)
    {
        if(value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    public String getBuildId() { return buildId; }
    public String getSourceHash() { return sourceHash; }
    public String getTestSourceHash() { return testSourceHash; }
    public String getRuntimeTreeHash() { return runtimeTreeHash; }
    public String getShortSourceHash() { return shortHash(sourceHash); }
    public String getMainClass() { return mainClass; }
    public String getBuiltAtUtc() { return builtAtUtc; }
    public URI getCodeSource() { return codeSource; }
    public boolean isStamped() { return stamped; }

    public String detailedSummary()
    {
        return "build=" + buildId +
               " sourceHash=" + sourceHash +
               " testSourceHash=" + testSourceHash +
               " runtimeTreeHash=" + runtimeTreeHash +
               " builtAtUtc=" + builtAtUtc +
               " codeSource=" + codeSource;
    }

    public static String shortHash(String hash)
    {
        if(hash == null) {
            return "unknown";
        }
        String value = hash.trim();
        return value.length() <= 12 ? value : value.substring(0, 12);
    }
}
