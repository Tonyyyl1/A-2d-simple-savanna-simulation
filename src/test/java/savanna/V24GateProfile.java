package savanna;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict, versioned V24 gate profile selected by a candidate manifest. */
public final class V24GateProfile
{
    public enum Kind
    {
        CORE_REBASE_TRANSPARENCY,
        FRAME_TRANSPARENCY,
        BR1P2_SEMANTIC
    }

    public static final String SCHEMA = "v24-gate-profile-v1";
    public static final String CORE_FILE =
        "v24-core-rebase-transparency-v1.properties";
    public static final String FRAME_FILE =
        "v24-frame-transparency-v1.properties";
    public static final String BR1P2_FILE =
        "v24-br1p2-semantic-v1.properties";

    private static final Set<String> KNOWN_FILES = Set.of(
        CORE_FILE, FRAME_FILE, BR1P2_FILE);
    private static final Set<String> REQUIRED_KEYS = Set.of(
        "schema",
        "profile.id",
        "profile.kind",
        "profile.version",
        "base.candidate",
        "base.jar.sha256",
        "base.main.source.count",
        "base.main.source.manifest.sha256",
        "base.default.pass",
        "base.default.total",
        "d2.contract",
        "d2.reference.step300.sha256",
        "d2.allowed.differences",
        "d2.exact.components",
        "a1b.contract",
        "a1b.transition.owner",
        "a1b.kernel.consumers",
        "a1b.forbidden.dependencies",
        "observer.exact.components",
        "cross.predecessor.allowed.owners",
        "semantic.reference.state",
        "ecology.execution");

    private final Path path;
    private final String sha256;
    private final Map<String, String> values;
    private final Kind kind;

    private V24GateProfile(Path path, String sha256,
                           Map<String, String> values, Kind kind)
    {
        this.path = path;
        this.sha256 = sha256;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.kind = kind;
    }

    public static V24GateProfile load(Path path, String expectedSha256)
        throws IOException
    {
        if(path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("gate profile is missing");
        }
        if(!isLowerSha256(expectedSha256)) {
            throw new IllegalArgumentException(
                "candidate gate profile hash is invalid");
        }
        byte[] bytes = Files.readAllBytes(path);
        String actualSha256 = sha256(bytes);
        if(!actualSha256.equals(expectedSha256)) {
            throw new IllegalStateException(
                "candidate gate profile hash mismatch");
        }
        Map<String, String> values = parse(bytes);
        if(!SCHEMA.equals(values.get("schema"))) {
            throw new IllegalStateException("unsupported V24 gate profile schema");
        }
        if(!values.keySet().equals(REQUIRED_KEYS)) {
            Set<String> missing = new LinkedHashSet<>(REQUIRED_KEYS);
            missing.removeAll(values.keySet());
            Set<String> extra = new LinkedHashSet<>(values.keySet());
            extra.removeAll(REQUIRED_KEYS);
            throw new IllegalStateException(
                "V24 gate profile key set mismatch missing=" + missing +
                " extra=" + extra);
        }
        Kind kind;
        try {
            kind = Kind.valueOf(values.get("profile.kind"));
        }
        catch(IllegalArgumentException error) {
            throw new IllegalStateException("unknown V24 gate profile kind", error);
        }
        validate(path.getFileName().toString(), values, kind);
        return new V24GateProfile(path, actualSha256, values, kind);
    }

    /**
     * Resolves and verifies the manifest-selected profile before invoking any
     * caller-supplied simulation stage.
     */
    public static V24GateProfile verifyCandidateThen(
        Path manifest, Path profileDirectory, Runnable afterVerification)
        throws IOException
    {
        if(manifest == null || !Files.isRegularFile(manifest)) {
            throw new IllegalArgumentException("candidate manifest is missing");
        }
        if(profileDirectory == null || !Files.isDirectory(profileDirectory)) {
            throw new IllegalArgumentException("gate profile directory is missing");
        }
        String json = Files.readString(manifest, StandardCharsets.UTF_8);
        String schema = jsonString(json, "schemaVersion");
        if(!"candidate-manifest-v2".equals(schema)) {
            throw new IllegalStateException(
                "candidate manifest does not use the V24 gate schema");
        }
        String profileName = jsonString(json, "gateProfile");
        if(!KNOWN_FILES.contains(profileName) ||
           !Path.of(profileName).getFileName().toString().equals(profileName)) {
            throw new IllegalStateException(
                "candidate manifest selects an unknown gate profile");
        }
        String expectedHash = jsonString(json, "gateProfileSha256");
        V24GateProfile profile = load(profileDirectory.resolve(profileName),
            expectedHash);
        if(!profile.getId().equals(jsonString(json, "gateProfileId")) ||
           !profile.getKind().name().equals(
               jsonString(json, "gateProfileKind"))) {
            throw new IllegalStateException(
                "candidate manifest gate identity does not match profile");
        }
        if(afterVerification != null) afterVerification.run();
        return profile;
    }

    public Path getPath() { return path; }
    public String getSha256() { return sha256; }
    public Kind getKind() { return kind; }
    public String getId() { return values.get("profile.id"); }
    public String get(String key) { return values.get(key); }

    public Set<String> getAllowedDifferences()
    {
        return csvSet(values.get("d2.allowed.differences"));
    }

    public Set<String> getExactComponents()
    {
        return csvSet(values.get("d2.exact.components"));
    }

    public Set<String> getKernelConsumers()
    {
        return csvSet(values.get("a1b.kernel.consumers"));
    }

    public Set<String> getForbiddenDependencies()
    {
        return csvSet(values.get("a1b.forbidden.dependencies"));
    }

    private static void validate(String fileName, Map<String, String> values,
                                 Kind kind)
    {
        if(!"1".equals(values.get("profile.version")) ||
           !"core-rebase-reach-and-graze-20260802".equals(
               values.get("base.candidate")) ||
           !isLowerSha256(values.get("base.jar.sha256")) ||
           !"218".equals(values.get("base.main.source.count")) ||
           !isLowerSha256(values.get("base.main.source.manifest.sha256")) ||
           !"875".equals(values.get("base.default.pass")) ||
           !"875".equals(values.get("base.default.total")) ||
           !isLowerSha256(values.get("d2.reference.step300.sha256")) ||
           !"FORBIDDEN_V24_GATE_1".equals(values.get("ecology.execution"))) {
            throw new IllegalStateException(
                "V24 gate profile baseline contract is invalid");
        }
        String expectedFile;
        String expectedId;
        String expectedD2;
        String expectedA1b;
        switch(kind) {
            case CORE_REBASE_TRANSPARENCY:
                expectedFile = CORE_FILE;
                expectedId = "v24-core-rebase-transparency-v1";
                expectedD2 = "EXACT_R2_FINGERPRINT";
                expectedA1b = "DISCONNECTED_ZERO_CALLERS";
                requireNone(values.get("d2.allowed.differences"),
                    "CORE D2 differences");
                requireNone(values.get("a1b.transition.owner"),
                    "CORE transition owner");
                requireNone(values.get("a1b.kernel.consumers"),
                    "CORE kernel consumers");
                break;
            case FRAME_TRANSPARENCY:
                expectedFile = FRAME_FILE;
                expectedId = "v24-frame-transparency-v1";
                expectedD2 = "VERSIONED_DIFFERENCE_LEDGER";
                expectedA1b = "DISCONNECTED_ZERO_CALLERS";
                requireExact(csvSet(values.get("d2.allowed.differences")),
                    Set.of("frame-phase-ledger", "hunt-opportunity-fact"),
                    "FRAME allowed differences");
                requireNone(values.get("a1b.transition.owner"),
                    "FRAME transition owner");
                requireNone(values.get("a1b.kernel.consumers"),
                    "FRAME kernel consumers");
                break;
            case BR1P2_SEMANTIC:
                expectedFile = BR1P2_FILE;
                expectedId = "v24-br1p2-semantic-v1";
                expectedD2 = "VERSIONED_DIFFERENCE_LEDGER";
                expectedA1b = "CONNECTED_EXACT_ALLOWLIST";
                requireExact(csvSet(values.get("d2.allowed.differences")),
                    Set.of("breeding-rng", "escrow-ledger",
                        "reproduction-lifecycle"),
                    "BR1P2 allowed differences");
                if(!"PredatorPregnancyState".equals(
                       values.get("a1b.transition.owner"))) {
                    throw new IllegalStateException(
                        "BR1P2 transition owner is not exact");
                }
                requireExact(csvSet(values.get("a1b.kernel.consumers")),
                    Set.of("PredatorPregnancyState",
                        "PredatorReproductionFingerprint", "SavannahAnimal"),
                    "BR1P2 kernel consumers");
                break;
            default:
                throw new IllegalStateException("unsupported profile kind");
        }
        if(!expectedFile.equals(fileName) ||
           !expectedId.equals(values.get("profile.id")) ||
           !expectedD2.equals(values.get("d2.contract")) ||
           !expectedA1b.equals(values.get("a1b.contract"))) {
            throw new IllegalStateException(
                "V24 gate profile identity/contract mismatch");
        }
        if(csvSet(values.get("d2.exact.components")).isEmpty() ||
           csvSet(values.get("observer.exact.components")).isEmpty() ||
           csvSet(values.get("a1b.forbidden.dependencies")).isEmpty()) {
            throw new IllegalStateException(
                "V24 exact/forbidden component sets cannot be empty");
        }
    }

    private static Map<String, String> parse(byte[] bytes)
    {
        Map<String, String> values = new LinkedHashMap<>();
        for(String line : new String(bytes, StandardCharsets.UTF_8)
            .lines().toList()) {
            if(line.isBlank() || line.startsWith("#")) continue;
            int separator = line.indexOf('=');
            if(separator <= 0 || separator == line.length() - 1) {
                throw new IllegalStateException(
                    "malformed V24 gate profile row: " + line);
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if(values.put(key, value) != null) {
                throw new IllegalStateException(
                    "duplicate V24 gate profile key: " + key);
            }
        }
        return values;
    }

    private static Set<String> csvSet(String value)
    {
        if(value == null || value.isBlank() || "NONE".equals(value)) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for(String token : value.split(",", -1)) {
            if(token.isBlank() || !result.add(token)) {
                throw new IllegalStateException(
                    "invalid or duplicate V24 profile list entry");
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static String jsonString(String json, String key)
    {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) +
            "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        if(!matcher.find()) {
            throw new IllegalStateException(
                "candidate manifest field is missing: " + key);
        }
        return matcher.group(1);
    }

    private static String sha256(byte[] bytes)
    {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder result = new StringBuilder();
            for(byte value : digest.digest(bytes)) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        }
        catch(NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static boolean isLowerSha256(String value)
    {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static void requireNone(String value, String label)
    {
        if(!"NONE".equals(value)) {
            throw new IllegalStateException(label + " must be NONE");
        }
    }

    private static void requireExact(Set<String> actual, Set<String> expected,
                                     String label)
    {
        if(!actual.equals(expected)) {
            throw new IllegalStateException(label + " is not exact: " + actual);
        }
    }
}
