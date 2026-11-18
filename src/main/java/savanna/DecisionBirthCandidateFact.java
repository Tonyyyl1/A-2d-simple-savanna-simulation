package savanna;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exact read-only copy of the list already passed to breeding proposal. */
public final class DecisionBirthCandidateFact
{
    private final int step;
    private final long actorId;
    private final String speciesKey;
    private final List<Location> orderedLocations;
    private final String orderDigest;

    private DecisionBirthCandidateFact(int step, long actorId,
                                       String speciesKey,
                                       List<Location> candidates)
    {
        if(step < 0 || actorId <= 0L || speciesKey == null ||
           speciesKey.isBlank() || candidates == null) {
            throw new IllegalArgumentException(
                "complete breeding-candidate inputs are required");
        }
        this.step = step;
        this.actorId = actorId;
        this.speciesKey = speciesKey;
        List<Location> copied = new ArrayList<>();
        StringBuilder canonical = new StringBuilder();
        for(int index = 0; index < candidates.size(); index++) {
            Location location = candidates.get(index);
            if(location == null) {
                throw new IllegalArgumentException(
                    "breeding candidates cannot contain null");
            }
            Location factLocation = copy(location);
            copied.add(factLocation);
            canonical.append(index).append('@').append(factLocation.row())
                .append(',').append(factLocation.col()).append('|');
        }
        orderedLocations = Collections.unmodifiableList(copied);
        orderDigest = sha256(canonical.toString());
    }

    public static DecisionBirthCandidateFact capture(int step,
                                                     SavannahAnimal actor,
                                                     List<Location> candidates)
    {
        if(actor == null || actor.getProfile() == null) {
            throw new IllegalArgumentException(
                "breeding actor and profile are required");
        }
        return new DecisionBirthCandidateFact(step, actor.getId(),
            actor.getProfile().getName(), candidates);
    }

    public int getStep() { return step; }

    public long getActorId() { return actorId; }

    public String getSpeciesKey() { return speciesKey; }

    public int getActualDecisionCandidateCount()
    {
        return orderedLocations.size();
    }

    public List<Location> getOrderedLocations()
    {
        return orderedLocations;
    }

    public String getOrderDigest() { return orderDigest; }

    private static Location copy(Location location)
    {
        return new Location(location.row(), location.col());
    }

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
