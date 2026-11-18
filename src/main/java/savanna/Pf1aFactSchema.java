package savanna;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Canonical field identity for PF1-aF evidence. */
public final class Pf1aFactSchema
{
    public static final String VERSION = "PF1-aF-v1";

    private static final List<String> FIELDS = Collections.unmodifiableList(
        Arrays.asList(
            "kill.step:int",
            "kill.predatorId:long",
            "kill.preyId:long",
            "kill.predatorSpecies:string",
            "kill.preySpecies:string",
            "kill.offeredFood:int",
            "kill.acceptedFood:int",
            "window.step:int",
            "window.predatorId:long",
            "window.windowSteps:int",
            "window.count:int",
            "window.lastKillStep:int",
            "window.offeredFood:long",
            "window.acceptedFood:long",
            "window.leftCensored:boolean",
            "female.step:int",
            "female.parentId:long",
            "female.species:string",
            "female.location:location",
            "female.decision.age:int",
            "female.decision.food:int",
            "female.decision.hydration:double",
            "female.decision.stamina:double",
            "female.decision.survivalPressure:double",
            "female.effectiveHuntingRange:int",
            "female.resource.reachablePreyCount:int",
            "female.resource.reachableOfferedFood:long",
            "female.resource.nearestPreyDistance:int",
            "female.resource.passableVisited:int",
            "female.capacity.breedingRange:int",
            "female.capacity.passableCellCount:int",
            "female.capacity.livingOccupantCount:int",
            "female.capacity.conspecificCount:int",
            "female.capacity.predatorCount:int",
            "female.capacity.ediblePreyCount:int",
            "female.candidates.actualDecisionCandidateCount:int",
            "female.candidates.orderedLocations:list<location>",
            "female.candidates.orderDigest:string",
            "breeding.canBreed:boolean",
            "breeding.waterSuppressed:boolean",
            "breeding.actualDecisionCandidateCount:int",
            "breeding.mateQueryPerformed:boolean",
            "breeding.mateEligible:boolean",
            "breeding.baseProbability:double",
            "breeding.infectionModifier:double",
            "breeding.survivalModifier:double",
            "breeding.staminaModifier:double",
            "breeding.weatherModifier:double",
            "breeding.foodModifier:double",
            "breeding.lowPopulationModifier:double",
            "breeding.populationBalanceModifier:double",
            "breeding.crowdingModifier:double",
            "breeding.finalProbability:double",
            "breeding.probabilityRollPerformed:boolean",
            "breeding.probabilityRollAccepted:boolean",
            "breeding.litterDrawPerformed:boolean",
            "breeding.litterDrawBound:int",
            "breeding.provisionalClaimCount:int",
            "rng.MATE_QUERY_SHUFFLE:int",
            "rng.LEGACY_BREEDING_ROLL:boolean",
            "rng.LITTER_DRAW:int",
            "resolution.step:int",
            "resolution.parentId:long",
            "resolution.species:string",
            "resolution.litterOrdinal:int",
            "resolution.requested:location",
            "resolution.resolved:location",
            "resolution.candidateSource:string",
            "resolution.candidateIndex:int",
            "resolution.disposition:string",
            "resolution.reason:string",
            "commit.step:int",
            "commit.parentId:long",
            "commit.species:string",
            "commit.committedBirthCount:int",
            "commit.parentPreBirth.age:int",
            "commit.parentPreBirth.food:int",
            "commit.parentPreBirth.hydration:double",
            "commit.parentPreBirth.stamina:double",
            "commit.parentPreBirth.survivalPressure:double",
            "commit.parentPostBirth.age:int",
            "commit.parentPostBirth.food:int",
            "commit.parentPostBirth.hydration:double",
            "commit.parentPostBirth.stamina:double",
            "commit.parentPostBirth.survivalPressure:double",
            "newborn.childId:long",
            "newborn.litterOrdinal:int",
            "newborn.species:string",
            "newborn.location:location",
            "newborn.reserve.age:int",
            "newborn.reserve.food:int",
            "newborn.reserve.hydration:double",
            "newborn.reserve.stamina:double",
            "newborn.reserve.survivalPressure:double"));

    private Pf1aFactSchema()
    {
    }

    public static List<String> getFields() { return FIELDS; }

    public static String canonicalSerialization()
    {
        return VERSION + "\n" + String.join("\n", FIELDS) + "\n";
    }

    public static String sha256()
    {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest(canonicalSerialization().getBytes(
                StandardCharsets.UTF_8));
            return HexEncoding.lowercase(value);
        }
        catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public static boolean hasUniqueFields()
    {
        Set<String> unique = new LinkedHashSet<>(FIELDS);
        return unique.size() == FIELDS.size();
    }
}
