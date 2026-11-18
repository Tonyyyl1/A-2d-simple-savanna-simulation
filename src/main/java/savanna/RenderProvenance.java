package savanna;

/** Immutable build, seed and terrain identity carried by every RenderFrame. */
public final class RenderProvenance
{
    private final String buildId;
    private final String sourceHash;
    private final String testSourceHash;
    private final String configurationHash;
    private final String candidateClass;
    private final String codeSource;
    private final long ecologyRandomSeed;
    private final long terrainSeed;
    private final String terrainProfile;
    private final String terrainGeneratorVersion;
    private final String terrainFingerprint;

    public RenderProvenance(String buildId, String sourceHash,
                            String testSourceHash, String configurationHash,
                            String candidateClass, String codeSource,
                            long ecologyRandomSeed, long terrainSeed,
                            String terrainProfile,
                            String terrainGeneratorVersion,
                            String terrainFingerprint)
    {
        this.buildId = value(buildId, "UNKNOWN");
        this.sourceHash = value(sourceHash, "UNKNOWN");
        this.testSourceHash = value(testSourceHash, "UNKNOWN");
        this.configurationHash = value(configurationHash, "UNKNOWN");
        this.candidateClass = RuntimeLaunchIdentity.requireKnownClass(
            candidateClass);
        this.codeSource = value(codeSource, "unknown:/");
        this.ecologyRandomSeed = ecologyRandomSeed;
        this.terrainSeed = terrainSeed;
        this.terrainProfile = value(terrainProfile, "UNKNOWN");
        this.terrainGeneratorVersion = value(terrainGeneratorVersion, "UNKNOWN");
        this.terrainFingerprint = value(terrainFingerprint, "UNKNOWN");
    }

    public static RenderProvenance capture(ResolvedRuntimeConfig runtimeConfig,
                                           TerrainMap terrainMap)
    {
        BuildIdentity identity = BuildIdentity.current();
        SimulationConfig config = runtimeConfig == null ? null
            : runtimeConfig.getEcologyConfig();
        long ecologySeed = config == null ? 0L : config.getRandomSeed();
        long layoutSeed = config == null ? 0L : config.getTerrainSeed();
        return new RenderProvenance(identity.getBuildId(),
            identity.getSourceHash(), identity.getTestSourceHash(),
            runtimeConfig == null ? "UNKNOWN" :
                runtimeConfig.getConfigurationHash(),
            RuntimeLaunchIdentity.currentCandidateClass(),
            identity.getCodeSource().toString(),
            ecologySeed, layoutSeed,
            terrainMap == null ? "UNKNOWN" : terrainMap.getGenerationProfile().name(),
            terrainMap == null ? "UNKNOWN" : terrainMap.getGeneratorVersion(),
            terrainMap == null ? "UNKNOWN" : terrainMap.getFingerprint());
    }

    public static RenderProvenance unavailable()
    {
        return new RenderProvenance("UNKNOWN", "UNKNOWN", "UNKNOWN",
                                    "UNKNOWN", "UNDECLARED", "unknown:/", 0L, 0L,
                                    "UNKNOWN", "UNKNOWN", "UNKNOWN");
    }

    private static String value(String text, String fallback)
    {
        return text == null || text.trim().isEmpty() ? fallback : text.trim();
    }

    public String getBuildId() { return buildId; }
    public String getSourceHash() { return sourceHash; }
    public String getTestSourceHash() { return testSourceHash; }
    public String getConfigurationHash() { return configurationHash; }
    public String getCandidateClass() { return candidateClass; }
    public String getShortSourceHash() { return BuildIdentity.shortHash(sourceHash); }
    public String getShortTestSourceHash()
    {
        return BuildIdentity.shortHash(testSourceHash);
    }
    public String getShortConfigurationHash()
    {
        return BuildIdentity.shortHash(configurationHash);
    }
    public String getCodeSource() { return codeSource; }
    public long getEcologyRandomSeed() { return ecologyRandomSeed; }
    public long getTerrainSeed() { return terrainSeed; }
    public String getTerrainProfile() { return terrainProfile; }
    public String getTerrainGeneratorVersion() { return terrainGeneratorVersion; }
    public String getTerrainFingerprint() { return terrainFingerprint; }
    public String getShortTerrainFingerprint()
    {
        return BuildIdentity.shortHash(terrainFingerprint);
    }

    public String compactSummary()
    {
        return "build=" + buildId +
               " src=" + getShortSourceHash() +
               " test=" + getShortTestSourceHash() +
               " config=" + getShortConfigurationHash() +
               " candidateClass=" + candidateClass +
               " ecologySeed=" + ecologyRandomSeed +
               " terrainSeed=" + terrainSeed +
               " terrain=" + terrainProfile + "/" + terrainGeneratorVersion + "/" +
               getShortTerrainFingerprint();
    }

    public String detailedSummary()
    {
        return "build=" + buildId +
               " sourceHash=" + sourceHash +
               " testSourceHash=" + testSourceHash +
               " configurationHash=" + configurationHash +
               " candidateClass=" + candidateClass +
               " codeSource=" + codeSource +
               " ecologyRandomSeed=" + ecologyRandomSeed +
               " terrainSeed=" + terrainSeed +
               " terrainProfile=" + terrainProfile +
               " terrainGenerator=" + terrainGeneratorVersion +
               " terrainFingerprint=" + terrainFingerprint;
    }
}
