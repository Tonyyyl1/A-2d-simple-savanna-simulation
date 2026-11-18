package savanna;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Canonical registry-complete SOC1-aF schema derived from existing profiles. */
public final class SpeciesEcologyFactRegistry
{
    public static final String SCHEMA_VERSION = "SOC1-aF-v1";

    private final List<SpeciesEcologyFactProfile> profiles;
    private final Map<String, SpeciesEcologyFactProfile> profilesByKey;
    private final String canonicalSerialization;
    private final String schemaHash;

    private SpeciesEcologyFactRegistry(
        List<SpeciesEcologyFactProfile> profiles)
    {
        List<SpeciesEcologyFactProfile> sorted = new ArrayList<>(profiles);
        sorted.sort(Comparator.comparing(
            SpeciesEcologyFactProfile::getSpeciesKey));
        this.profiles = Collections.unmodifiableList(sorted);
        Map<String, SpeciesEcologyFactProfile> byKey = new LinkedHashMap<>();
        StringBuilder canonical = new StringBuilder(SCHEMA_VERSION).append('\n');
        for(SpeciesEcologyFactProfile profile : sorted) {
            if(byKey.put(profile.getSpeciesKey(), profile) != null) {
                throw new IllegalStateException(
                    "duplicate SOC1-aF species key: " +
                    profile.getSpeciesKey());
            }
            canonical.append(profile.canonicalLine()).append('\n');
        }
        profilesByKey = Collections.unmodifiableMap(byKey);
        canonicalSerialization = canonical.toString();
        schemaHash = sha256(canonicalSerialization);
    }

    public static SpeciesEcologyFactRegistry capture()
    {
        List<SpeciesFactory> factories = SpeciesRegistry.getFactories();
        Map<String, SpeciesProfile> sourceProfiles = new LinkedHashMap<>();
        for(SpeciesFactory factory : factories) {
            SpeciesProfile profile = factory == null ? null :
                factory.getProfile();
            if(profile == null || profile.getName() == null ||
               profile.getName().isBlank() ||
               sourceProfiles.put(profile.getName(), profile) != null) {
                throw new IllegalStateException(
                    "SpeciesRegistry must be complete and unique");
            }
        }
        if(sourceProfiles.size() != factories.size()) {
            throw new IllegalStateException(
                "SOC1-aF registry count differs from factory count");
        }

        List<SpeciesEcologyFactProfile> facts = new ArrayList<>();
        for(SpeciesProfile profile : sourceProfiles.values()) {
            List<String> edible = new ArrayList<>();
            for(String candidate : sourceProfiles.keySet()) {
                if(profile.canEat(candidate)) edible.add(candidate);
            }
            facts.add(new SpeciesEcologyFactProfile(profile, edible));
        }
        SpeciesEcologyFactRegistry registry =
            new SpeciesEcologyFactRegistry(facts);
        if(registry.size() != factories.size()) {
            throw new IllegalStateException(
                "SOC1-aF fact registry is incomplete");
        }
        return registry;
    }

    public int size() { return profiles.size(); }

    public List<SpeciesEcologyFactProfile> getProfiles()
    {
        return profiles;
    }

    public SpeciesEcologyFactProfile get(String speciesKey)
    {
        return profilesByKey.get(speciesKey);
    }

    public String getCanonicalSerialization()
    {
        return canonicalSerialization;
    }

    public String getSchemaHash() { return schemaHash; }

    private static String sha256(String value)
    {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(value.getBytes(StandardCharsets.UTF_8));
            return HexEncoding.lowercase(digest.digest());
        }
        catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
