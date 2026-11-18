package savanna;

import java.nio.charset.StandardCharsets;

/** JSON form of a {@link WorldDescriptor} for {@code GET /api/world} and recordings (plan v3.2 V2). */
public final class WorldJson
{
    /** Display scale defaults chosen by the user at the end of V0 (2026-11-11). */
    public static final double DEFAULT_CELL_METERS = 5.0;
    public static final double DEFAULT_STEP_SECONDS = 2.0;

    private WorldJson()
    {
    }

    public static byte[] toJson(WorldDescriptor world)
    {
        StringBuilder out = new StringBuilder(4096);
        out.append("{\"protocol\":").append(SimFrameCodec.VERSION)
            .append(",\"runGeneration\":").append(world.runGeneration)
            .append(",\"rulesVersion\":").append(quote(world.rulesVersion))
            .append(",\"paramsHash\":").append(quote(world.paramsHash))
            .append(",\"config\":{\"ecologySeed\":").append(world.ecologySeed)
            .append(",\"terrainSeed\":").append(world.terrainSeed)
            .append(",\"terrainProfile\":").append(quote(world.terrainProfile))
            .append(",\"thirst\":").append(world.thirstEnabled).append('}')
            .append(",\"terrain\":{\"depth\":").append(world.depth)
            .append(",\"width\":").append(world.width)
            .append(",\"fingerprint\":").append(quote(world.terrainFingerprint))
            .append(",\"cellMeters\":").append(DEFAULT_CELL_METERS)
            .append(",\"waterLevels\":").append(levels(world.waterLevels())).append('}')
            .append(",\"time\":{\"stepsPerDay\":24,\"defaultStepSeconds\":")
            .append(DEFAULT_STEP_SECONDS).append('}')
            .append(",\"species\":[");
        boolean first = true;
        for(WorldDescriptor.SpeciesEntry entry : world.species) {
            if(!first) out.append(',');
            first = false;
            out.append("{\"code\":").append(entry.code)
                .append(",\"name\":").append(quote(entry.name))
                .append(",\"predator\":").append(entry.predator)
                .append(",\"target\":").append(entry.targetCount)
                .append(",\"adultAgeShare\":").append(entry.adultAgeShare)
                .append(",\"speed\":").append(entry.moveSpeed).append('}');
        }
        out.append("],\"codes\":{");
        out.append("\"action\":{");
        boolean f = true;
        for(ActionType value : ActionType.values()) {
            f = entry(out, f, value.name(), SimFrameBuilder.actionCode(value));
        }
        out.append("},\"reason\":{");
        f = true;
        for(BehaviorReason value : BehaviorReason.values()) {
            f = entry(out, f, value.name(), SimFrameBuilder.reasonCode(value));
        }
        out.append("},\"moveResult\":{");
        f = true;
        for(MovementDisposition value : MovementDisposition.values()) {
            f = entry(out, f, value.name(), SimFrameBuilder.moveCode(value));
        }
        out.append("},\"deathCause\":{");
        f = true;
        for(DeathCause value : DeathCause.values()) {
            f = entry(out, f, value.name(), SimFrameBuilder.causeCode(value));
        }
        out.append("},\"phase\":{");
        f = true;
        for(DayPhase value : DayPhase.values()) {
            f = entry(out, f, value.name(), SimFrameBuilder.phaseCode(value));
        }
        out.append("},\"weather\":{");
        f = true;
        for(WeatherType value : WeatherType.values()) {
            f = entry(out, f, value.name(), SimFrameBuilder.weatherCode(value));
        }
        out.append("},\"lifeStage\":{");
        f = true;
        for(LifeStage value : LifeStage.values()) {
            f = entry(out, f, value.name(), SimFrameBuilder.lifeStageCode(value));
        }
        out.append("},\"terrain\":{");
        f = true;
        for(TerrainType value : TerrainType.values()) {
            f = entry(out, f, value.name(), WorldDescriptor.terrainCode(value));
        }
        out.append("},\"effectBits\":{");
        f = true;
        for(ExecutedEffect value : ExecutedEffect.values()) {
            f = entry(out, f, value.name(),
                SimFrameBuilder.effectBits(java.util.Collections.singletonList(value)));
        }
        out.append("}}}");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String levels(float[] values)
    {
        StringBuilder out = new StringBuilder("[");
        for(int index = 0; index < values.length; index++) {
            if(index > 0) out.append(',');
            out.append(values[index]);
        }
        return out.append(']').toString();
    }

    private static boolean entry(StringBuilder out, boolean first, String name, int code)
    {
        if(!first) out.append(',');
        out.append(quote(name)).append(':').append(code);
        return false;
    }

    static String quote(String text)
    {
        StringBuilder out = new StringBuilder("\"");
        for(int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if(c == '"' || c == '\\') out.append('\\').append(c);
            else if(c < 0x20) out.append(String.format("\\u%04x", (int)c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
}
