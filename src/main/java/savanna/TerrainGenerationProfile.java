package savanna;

/** Versioned logical terrain layouts; legacy remains the simulation default. */
public enum TerrainGenerationProfile
{
    /** Existing two-pool layout. Its grid and fingerprint must not change. */
    LEGACY_V1("LEGACY_V1"),
    /** Preview-only deterministic generator with seed-dependent macro layout. */
    PROCEDURAL_V2("PROCEDURAL_V2");

    private final String generatorVersion;

    TerrainGenerationProfile(String generatorVersion)
    {
        this.generatorVersion = generatorVersion;
    }

    public String getGeneratorVersion()
    {
        return generatorVersion;
    }

    public boolean isProcedural()
    {
        return this == PROCEDURAL_V2;
    }
}
