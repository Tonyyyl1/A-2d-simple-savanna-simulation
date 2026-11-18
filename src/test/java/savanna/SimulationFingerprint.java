package savanna;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic read-only digest of the observable ecological state. */
public final class SimulationFingerprint
{
    public static final String SCHEMA_VERSION = "A0-legacy-grass-v1";
    private SimulationFingerprint()
    {
    }

    public static String capture(Simulator simulator)
    {
        if(simulator == null) {
            throw new IllegalArgumentException("simulator is required");
        }
        return capture(simulator.getField(), simulator.getContext());
    }

    public static String capture(Field field, SimulationContext context)
    {
        if(field == null || context == null) {
            throw new IllegalArgumentException("field and context are required");
        }
        MessageDigest digest = sha256();
        append(digest, "schema=" + SCHEMA_VERSION);
        append(digest, "step=" + context.getStep());
        append(digest, "terrain=" + context.getTerrainMap().getFingerprint());
        append(digest, "weather=" + context.getWeatherSystem().getCurrentWeather());
        append(digest, "phase=" + context.getClock().getPhase());
        append(digest, "deaths=" + context.getDeathSummary());
        FoodSystem food = context.getFoodSystem();
        append(digest, "legacyGrass=" + (food instanceof GrasslandFoodSystem
            ? ((GrasslandFoodSystem)food).legacyStateHash() : "ABSENT"));
        // These systems are intentionally not implemented before G1/W3R.
        // Their explicit absence prevents an accidental schema ambiguity.
        append(digest, "growingLayer=ABSENT");
        append(digest, "edibleLayer=ABSENT");
        append(digest, "waterServiceQueue=ABSENT");
        List<SavannahAnimal> animals = new ArrayList<>();
        for(Animal actor : field.getAnimals()) {
            if(actor instanceof SavannahAnimal && actor.isAlive()) {
                animals.add((SavannahAnimal)actor);
            }
        }
        animals.sort(Comparator.comparingLong(Animal::getId));
        for(SavannahAnimal animal : animals) {
            Location location = animal.getLocation();
            append(digest, animal.getId() + "|" + animal.getProfile().getName() +
                "|" + animal.getSex() + "|" + animal.getAge() + "|" +
                animal.getFoodLevel() + "|" +
                Double.doubleToLongBits(animal.getStaminaPercent()) + "|" +
                Double.doubleToLongBits(animal.getHydrationPercent()) + "|" +
                animal.getStarvingSteps() + "|" + animal.getDehydrationSteps() +
                "|" + animal.isInfected() + "|" +
                Double.doubleToLongBits(animal.getInfectionLevel()) + "|" +
                (animal.getLastBehaviorAction() == null ? "" :
                    animal.getLastBehaviorAction().name()) + "|" +
                (animal.getLastBehaviorReason() == null ? "" :
                    animal.getLastBehaviorReason().name()) + "|" +
                behaviorOutcomeFingerprint(animal.getLastBehaviorOutcome()) + "|" +
                (location == null ? "-1,-1" : location.row() + "," + location.col()));
        }
        return hex(digest.digest());
    }

    private static MessageDigest sha256()
    {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch(NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private static String behaviorOutcomeFingerprint(BehaviorOutcome outcome)
    {
        if(outcome == null) {
            return "";
        }
        StringBuilder value = new StringBuilder();
        for(ExecutedEffect effect : outcome.getExecutedEffects()) {
            if(value.length() > 0) value.append(',');
            value.append(effect.name());
        }
        return outcome.getMovementDisposition().name() + ':' +
               outcome.receivesRestCredit() + ':' + value;
    }

    private static void append(MessageDigest digest, String value)
    {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte)0);
    }

    private static String hex(byte[] bytes)
    {
        return HexEncoding.lowercase(bytes);
    }
}
