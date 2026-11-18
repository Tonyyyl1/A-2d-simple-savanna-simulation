package savanna;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lossless, validated, and locale-independent resolved runtime configuration. */
public final class ResolvedRuntimeConfig
{
    public static final int CANONICAL_SCHEMA_VERSION = 3;

    private final SimulationConfig ecologyConfig;
    private final ObservationConfig observationConfig;
    private final DisplayConfig displayConfig;
    private final String provenance;
    private final String canonicalForm;
    private final String configurationHash;

    public ResolvedRuntimeConfig(SimulationConfig ecologyConfig,
                                 ObservationConfig observationConfig,
                                 DisplayConfig displayConfig)
    {
        this.ecologyConfig = ecologyConfig == null
            ? SimulationConfig.baseline() : ecologyConfig;
        this.observationConfig = observationConfig == null
            ? ObservationConfig.defaults() : observationConfig;
        this.displayConfig = displayConfig == null
            ? DisplayConfig.defaults() : displayConfig;
        provenance = "schema=" + CANONICAL_SCHEMA_VERSION +
            ";descriptorSchema=" + ConfigDescriptorRegistry.SCHEMA_VERSION +
            ";ecology=SimulationConfig-adapter";
        canonicalForm = buildCanonicalForm();
        configurationHash = sha256(canonicalForm);
    }

    public static ResolvedRuntimeConfig fromLegacy(SimulationConfig config)
    {
        return new ResolvedRuntimeConfig(config, ObservationConfig.defaults(),
            DisplayConfig.defaults());
    }

    public static ResolvedRuntimeConfig fromCanonicalForm(String canonical)
    {
        if(canonical == null) {
            throw new IllegalArgumentException("canonical configuration is required");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for(String line : canonical.split("\\n", -1)) {
            if(line.isEmpty()) continue;
            int separator = line.indexOf('=');
            if(separator <= 0 || separator == line.length() - 1) {
                throw new IllegalArgumentException(
                    "malformed canonical configuration row: " + line);
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if(values.put(key, value) != null) {
                throw new IllegalArgumentException(
                    "duplicate canonical configuration key: " + key);
            }
        }
        List<String> expected = canonicalKeys();
        if(!new ArrayList<>(values.keySet()).equals(expected)) {
            throw new IllegalArgumentException(
                "canonical configuration keys/order differ: expected=" +
                expected + " actual=" + values.keySet());
        }
        try {
            if(Integer.parseInt(values.get("schema")) !=
               CANONICAL_SCHEMA_VERSION) {
                throw new IllegalArgumentException(
                    "unsupported canonical configuration schema");
            }
            HydrationPolicy hydration = new HydrationPolicy(
                Boolean.parseBoolean(requiredBoolean(values,
                    "ecology.hydration.enabled")),
                number(values, "ecology.hydration.baseDrain"),
                number(values, "ecology.hydration.criticalThreshold"),
                number(values, "ecology.hydration.thirstyThreshold"),
                number(values, "ecology.hydration.criticalReserve"),
                number(values, "ecology.hydration.safetyMargin"),
                number(values, "ecology.hydration.droughtDrainMultiplier"),
                number(values, "ecology.hydration.rainDrainMultiplier"),
                number(values, "ecology.hydration.predatorDrainMultiplier"),
                number(values, "ecology.hydration.criticalStaminaCost"),
                integer(values, "ecology.hydration.dehydrationGraceSteps"),
                waterNeedProfile(values.get("ecology.hydration.waterNeedProfile")));
            SimulationConfig ecology = SimulationConfig.builder()
                .mapScale(integer(values, "ecology.mapScale"))
                .creationMultiplier(number(values,
                    "ecology.creationMultiplier"))
                .foundingMultiplier(number(values,
                    "ecology.foundingMultiplier"))
                .breedingMultiplier(number(values,
                    "ecology.breedingMultiplier"))
                .predatorCreationMultiplier(number(values,
                    "ecology.predatorCreationMultiplier"))
                .preyCreationMultiplier(number(values,
                    "ecology.preyCreationMultiplier"))
                .predatorFoundingMultiplier(number(values,
                    "ecology.predatorFoundingMultiplier"))
                .preyFoundingMultiplier(number(values,
                    "ecology.preyFoundingMultiplier"))
                .predatorBreedingMultiplier(number(values,
                    "ecology.predatorBreedingMultiplier"))
                .preyBreedingMultiplier(number(values,
                    "ecology.preyBreedingMultiplier"))
                .diseaseTransmissionMultiplier(number(values,
                    "ecology.diseaseTransmissionMultiplier"))
                .diseaseFatalityMultiplier(number(values,
                    "ecology.diseaseFatalityMultiplier"))
                .randomSeed(longValue(values, "ecology.randomSeed"))
                .terrainSeed(longValue(values, "ecology.terrainSeed"))
                .terrainGenerationProfile(TerrainGenerationProfile.valueOf(
                    values.get("ecology.terrainGenerationProfile")))
                .hydrationPolicy(hydration)
                .waterFoodSecondaryPolicy(WaterFoodSecondaryPolicy.valueOf(
                    values.get("ecology.waterFoodSecondaryPolicy")))
                .build();
            ObservationConfig observation = new ObservationConfig(
                integer(values, "observation.visibleCadence"),
                integer(values, "observation.reportCadence"),
                integer(values, "observation.chartCadence"),
                integer(values, "observation.diagnosticCadence"),
                integer(values, "observation.heatmapShortWindow"),
                integer(values, "observation.heatmapLongWindow"),
                integer(values, "observation.representativeEventCapacity"),
                integer(values, "observation.recentEventCapacity"),
                integer(values, "observation.detailEventCapacity"));
            DisplayConfig display = new DisplayConfig(
                integer(values, "display.boundaryDelayMillis"),
                integer(values, "display.inspectTickMillis"),
                integer(values, "display.inspectHoldMillis"),
                integer(values, "display.inspectAmbientCycleMillis"),
                number(values, "display.inspectZoomRatio"));
            ResolvedRuntimeConfig result = new ResolvedRuntimeConfig(ecology,
                observation, display);
            if(!result.getCanonicalForm().equals(canonical)) {
                throw new IllegalArgumentException(
                    "canonical configuration is not in normalized form");
            }
            return result;
        }
        catch(NumberFormatException exception) {
            throw new IllegalArgumentException(
                "canonical configuration contains an invalid number", exception);
        }
    }

    public SimulationConfig getEcologyConfig() { return ecologyConfig; }
    public ObservationConfig getObservationConfig() { return observationConfig; }
    public DisplayConfig getDisplayConfig() { return displayConfig; }
    public String getProvenance() { return provenance; }
    public String getCanonicalForm() { return canonicalForm; }
    public String getConfigurationHash() { return configurationHash; }

    private String buildCanonicalForm()
    {
        SimulationConfig ecology = ecologyConfig;
        HydrationPolicy hydration = ecology.getHydrationPolicy();
        StringBuilder value = new StringBuilder();
        append(value, "schema", CANONICAL_SCHEMA_VERSION);
        append(value, "ecology.mapScale", ecology.getMapScale());
        append(value, "ecology.creationMultiplier",
            exact(ecology.getCreationMultiplier()));
        append(value, "ecology.foundingMultiplier",
            exact(ecology.getFoundingMultiplier()));
        append(value, "ecology.breedingMultiplier",
            exact(ecology.getBreedingMultiplier()));
        append(value, "ecology.predatorCreationMultiplier",
            exact(ecology.getPredatorCreationMultiplier()));
        append(value, "ecology.preyCreationMultiplier",
            exact(ecology.getPreyCreationMultiplier()));
        append(value, "ecology.predatorFoundingMultiplier",
            exact(ecology.getPredatorFoundingMultiplier()));
        append(value, "ecology.preyFoundingMultiplier",
            exact(ecology.getPreyFoundingMultiplier()));
        append(value, "ecology.predatorBreedingMultiplier",
            exact(ecology.getPredatorBreedingMultiplier()));
        append(value, "ecology.preyBreedingMultiplier",
            exact(ecology.getPreyBreedingMultiplier()));
        append(value, "ecology.diseaseTransmissionMultiplier",
            exact(ecology.getDiseaseTransmissionMultiplier()));
        append(value, "ecology.diseaseFatalityMultiplier",
            exact(ecology.getDiseaseFatalityMultiplier()));
        append(value, "ecology.randomSeed", ecology.getRandomSeed());
        append(value, "ecology.terrainSeed", ecology.getTerrainSeed());
        append(value, "ecology.terrainGenerationProfile",
            ecology.getTerrainGenerationProfile().name());
        append(value, "ecology.hydration.enabled", hydration.isEnabled());
        append(value, "ecology.hydration.baseDrain",
            exact(hydration.getBaseDrain()));
        append(value, "ecology.hydration.criticalThreshold",
            exact(hydration.getCriticalHydrationThreshold()));
        append(value, "ecology.hydration.thirstyThreshold",
            exact(hydration.getThirstyHydrationThreshold()));
        append(value, "ecology.hydration.criticalReserve",
            exact(hydration.getCriticalReserve()));
        append(value, "ecology.hydration.safetyMargin",
            exact(hydration.getSafetyMargin()));
        append(value, "ecology.hydration.droughtDrainMultiplier",
            exact(hydration.getDroughtDrainMultiplier()));
        append(value, "ecology.hydration.rainDrainMultiplier",
            exact(hydration.getRainDrainMultiplier()));
        append(value, "ecology.hydration.predatorDrainMultiplier",
            exact(hydration.getPredatorDrainMultiplier()));
        append(value, "ecology.hydration.criticalStaminaCost",
            exact(hydration.getCriticalStaminaCost()));
        append(value, "ecology.hydration.dehydrationGraceSteps",
            hydration.getDehydrationGraceSteps());
        append(value, "ecology.hydration.waterNeedProfile",
            hydration.getWaterNeedProfileId());
        append(value, "ecology.waterFoodSecondaryPolicy",
            ecology.getWaterFoodSecondaryPolicy().name());
        append(value, "observation.visibleCadence",
            observationConfig.getVisibleCadence());
        append(value, "observation.reportCadence",
            observationConfig.getReportCadence());
        append(value, "observation.chartCadence",
            observationConfig.getChartCadence());
        append(value, "observation.diagnosticCadence",
            observationConfig.getDiagnosticCadence());
        append(value, "observation.heatmapShortWindow",
            observationConfig.getHeatmapShortWindow());
        append(value, "observation.heatmapLongWindow",
            observationConfig.getHeatmapLongWindow());
        append(value, "observation.representativeEventCapacity",
            observationConfig.getRepresentativeEventCapacity());
        append(value, "observation.recentEventCapacity",
            observationConfig.getRecentEventCapacity());
        append(value, "observation.detailEventCapacity",
            observationConfig.getDetailEventCapacity());
        append(value, "display.boundaryDelayMillis",
            displayConfig.getBoundaryDelayMillis());
        append(value, "display.inspectTickMillis",
            displayConfig.getInspectTickMillis());
        append(value, "display.inspectHoldMillis",
            displayConfig.getInspectHoldMillis());
        append(value, "display.inspectAmbientCycleMillis",
            displayConfig.getInspectAmbientCycleMillis());
        append(value, "display.inspectZoomRatio",
            exact(displayConfig.getInspectZoomRatio()));
        return value.toString();
    }

    private static List<String> canonicalKeys()
    {
        ResolvedRuntimeConfig defaults = new ResolvedRuntimeConfig(
            SimulationConfig.default3x(), ObservationConfig.defaults(),
            DisplayConfig.defaults());
        List<String> keys = new ArrayList<>();
        for(String line : defaults.getCanonicalForm().split("\\n")) {
            keys.add(line.substring(0, line.indexOf('=')));
        }
        return keys;
    }

    private static void append(StringBuilder target, String key, Object value)
    {
        target.append(key).append('=').append(value).append('\n');
    }

    private static String exact(double value)
    {
        return Double.toHexString(value);
    }

    private static double number(Map<String, String> values, String key)
    {
        return Double.valueOf(values.get(key));
    }

    private static int integer(Map<String, String> values, String key)
    {
        return Integer.parseInt(values.get(key));
    }

    private static long longValue(Map<String, String> values, String key)
    {
        return Long.parseLong(values.get(key));
    }

    private static String requiredBoolean(Map<String, String> values, String key)
    {
        String value = values.get(key);
        if(!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException(
                "canonical boolean must be true or false: " + key);
        }
        return value;
    }

    private static WaterNeedProfile waterNeedProfile(String id)
    {
        if(DefaultWaterNeedProfile.class.getName().equals(id)) {
            return DefaultWaterNeedProfile.INSTANCE;
        }
        throw new IllegalArgumentException(
            "unsupported canonical water-need profile: " + id);
    }

    private static String sha256(String value)
    {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                value.getBytes(StandardCharsets.UTF_8));
            return HexEncoding.lowercase(bytes);
        }
        catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
