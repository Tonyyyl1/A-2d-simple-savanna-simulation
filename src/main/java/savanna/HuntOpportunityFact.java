package savanna;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Immutable S0 HUNT opportunity consumed unchanged by S2.
 *
 * <p>The fact freezes the actor, action-frame, effective range and exact edible
 * candidate identities before a reproduction proposal can mutate physiology.
 * It retains the legacy row-major search domain so S2 can request the same
 * location shuffle at the same RNG boundary without rebuilding range or the
 * candidate set.</p>
 */
public final class HuntOpportunityFact
{
    private static final ThreadLocal<MessageDigest> SHA_256 =
        new ThreadLocal<MessageDigest>() {
            @Override
            protected MessageDigest initialValue()
            {
                try {
                    return MessageDigest.getInstance("SHA-256");
                }
                catch(NoSuchAlgorithmException e) {
                    throw new IllegalStateException("SHA-256 is unavailable", e);
                }
            }
        };

    private final int step;
    private final long actorId;
    private final String frameId;
    private final String opportunityId;
    private final String actorSpecies;
    private final Sex actorSex;
    private final LifeStage actorLifeStage;
    private final AnimalPhysiologyFact capturePhysiology;
    private final Location origin;
    private final int effectiveRange;
    private final String candidateDigest;
    private final List<Location> searchLocations;
    private final List<CandidateFact> candidates;
    private final Map<Location, CandidateFact> candidatesByLocation;

    private HuntOpportunityFact(int step, SavannahAnimal actor, String frameId,
                                AnimalPhysiologyFact physiology, Location origin,
                                int effectiveRange,
                                List<Location> searchLocations,
                                List<CandidateFact> candidates)
    {
        if(step < 0 || actor == null || frameId == null || frameId.isBlank() ||
           physiology == null || origin == null || effectiveRange < 0 ||
           searchLocations == null || candidates == null) {
            throw new IllegalArgumentException(
                "complete S0 hunt opportunity facts are required");
        }
        this.step = step;
        actorId = actor.getId();
        this.frameId = frameId;
        actorSpecies = actor.getProfile().getName();
        actorSex = actor.getSex();
        actorLifeStage = actor.getLifeStage();
        capturePhysiology = physiology;
        this.origin = copy(origin);
        this.effectiveRange = effectiveRange;
        this.searchLocations = immutableLocations(searchLocations);
        this.candidates = Collections.unmodifiableList(
            new ArrayList<>(candidates));
        Map<Location, CandidateFact> indexed = new LinkedHashMap<>();
        for(CandidateFact candidate : candidates) {
            if(candidate == null || indexed.put(candidate.getLocation(),
               candidate) != null) {
                throw new IllegalArgumentException(
                    "S0 hunt candidates must be non-null and location-unique");
            }
        }
        candidatesByLocation = Collections.unmodifiableMap(indexed);
        candidateDigest = digestCandidates(candidates);
        opportunityId = sha256(actorId + "|" + step + "|" + frameId + "|" +
            origin.row() + ',' + origin.col() + "|" + effectiveRange + "|" +
            candidateDigest + "|" + candidates.size());
    }

    /** Capture one deterministic, RNG-free S0 fact from the shared geometry. */
    public static HuntOpportunityFact capture(int step, SavannahAnimal actor,
                                              String frameId,
                                              InteractionGeometry geometry,
                                              SimulationContext context)
    {
        return capture(step, actor, frameId, actor == null ? null :
            actor.getLocation(), geometry, context);
    }

    static HuntOpportunityFact capture(int step, SavannahAnimal actor,
                                       String frameId, Location capturedOrigin,
                                       InteractionGeometry geometry,
                                       SimulationContext context)
    {
        if(actor == null || geometry == null || context == null) {
            throw new IllegalArgumentException(
                "actor, geometry and context are required");
        }
        Location origin = capturedOrigin;
        if(origin == null) {
            throw new IllegalStateException(
                "a frame actor must have an S0 origin");
        }
        AnimalPhysiologyFact physiology = AnimalPhysiologyFact.capture(actor);
        boolean eligible = actor.isAlive() && actor.isAdult() &&
            actor.getProfile().isPredator();
        int range = eligible
            ? HuntLocalOpportunity.effectiveRange(actor, context) : 0;
        List<Location> locations = eligible
            ? HuntLocalOpportunity.deterministicLocations(origin, range, geometry)
            : Collections.<Location>emptyList();
        List<CandidateFact> candidateFacts = new ArrayList<>();
        if(eligible) {
            TerrainNavigator.Reachability reachability =
                TerrainNavigator.reachableDistances(context.getTerrainMap(),
                    origin, range);
            HuntLocalOpportunity classified =
                HuntLocalOpportunity.fromHuntLocations(actor, geometry, context,
                    range, reachability, locations);
            for(HuntLocalOpportunity.Candidate candidate :
                classified.getCandidates()) {
                candidateFacts.add(CandidateFact.capture(candidate));
            }
        }
        return new HuntOpportunityFact(step, actor, frameId, physiology, origin,
            range, locations, candidateFacts);
    }

    /**
     * Request the legacy shuffle over the exact S0 search domain.
     * Package-private so only the production predation adapter can consume it.
     */
    List<Location> shuffledSearchLocations(Random random)
    {
        if(random == null) {
            throw new IllegalArgumentException("hunt shuffle RNG is required");
        }
        List<Location> shuffled = new ArrayList<>(searchLocations);
        Collections.shuffle(shuffled, random);
        return shuffled;
    }

    /** Resolve only the frozen identities, in the legacy shuffled order. */
    HuntLocalOpportunity resolveFrozenCandidates(
        SavannahAnimal predator, InteractionGeometry geometry,
        List<Location> shuffledLocations)
    {
        if(predator == null || predator.getId() != actorId || geometry == null ||
           shuffledLocations == null ||
           shuffledLocations.size() != searchLocations.size()) {
            throw new IllegalStateException(
                "S2 must consume the matching complete S0 opportunity");
        }
        List<HuntLocalOpportunity.Candidate> resolved = new ArrayList<>();
        for(Location location : shuffledLocations) {
            CandidateFact frozen = candidatesByLocation.get(location);
            if(frozen == null) continue;
            Animal observed = geometry.getAnimalAt(location);
            if(!(observed instanceof SavannahAnimal) ||
               observed.getId() != frozen.getPreyId()) {
                throw new IllegalStateException(
                    "S0 HUNT candidate identity changed before S2: " + frozen);
            }
            SavannahAnimal prey = (SavannahAnimal)observed;
            // A prior S2 winner may have committed this exact frozen prey.
            // This later claimant receives no candidate and no second credit.
            if(!prey.isAlive()) continue;
            if(!prey.getProfile().getName().equals(frozen.getPreySpecies())) {
                throw new IllegalStateException(
                    "S0 HUNT candidate species changed before S2: " + frozen);
            }
            resolved.add(new HuntLocalOpportunity.Candidate(prey, location,
                frozen.getTopologyDistance()));
        }
        return new HuntLocalOpportunity(effectiveRange, resolved);
    }

    public int getStep() { return step; }
    public long getActorId() { return actorId; }
    public String getFrameId() { return frameId; }
    public String getOpportunityId() { return opportunityId; }
    public String getActorSpecies() { return actorSpecies; }
    public Sex getActorSex() { return actorSex; }
    public LifeStage getActorLifeStage() { return actorLifeStage; }
    public AnimalPhysiologyFact getCapturePhysiology()
    {
        return capturePhysiology;
    }
    public Location getOrigin() { return copy(origin); }
    public int getEffectiveRange() { return effectiveRange; }
    public String getCandidateDigest() { return candidateDigest; }
    public int getCandidateCount() { return candidates.size(); }
    public int getSearchLocationCount() { return searchLocations.size(); }
    public boolean hasLocalReachableEdible() { return !candidates.isEmpty(); }
    public List<CandidateFact> getCandidates() { return candidates; }

    public boolean hasSameIdentity(HuntOpportunityFact other)
    {
        return other != null && actorId == other.actorId && step == other.step &&
            frameId.equals(other.frameId) &&
            opportunityId.equals(other.opportunityId) &&
            effectiveRange == other.effectiveRange &&
            candidateDigest.equals(other.candidateDigest) &&
            candidates.size() == other.candidates.size();
    }

    private static List<Location> immutableLocations(List<Location> values)
    {
        List<Location> copied = new ArrayList<>();
        for(Location value : values) {
            if(value == null) {
                throw new IllegalArgumentException(
                    "hunt search locations cannot contain null");
            }
            copied.add(copy(value));
        }
        return Collections.unmodifiableList(copied);
    }

    private static String digestCandidates(List<CandidateFact> values)
    {
        StringBuilder canonical = new StringBuilder();
        for(CandidateFact value : values) {
            canonical.append(value.getPreyId()).append('|')
                .append(value.getPreySpecies()).append('|')
                .append(value.getLocation().row()).append(',')
                .append(value.getLocation().col()).append('|')
                .append(value.getTopologyDistance()).append('\n');
        }
        if(canonical.length() == 0) canonical.append("NONE\n");
        return sha256(canonical.toString());
    }

    static String sha256(String value)
    {
        MessageDigest digest = SHA_256.get();
        digest.reset();
        return HexEncoding.lowercase(digest.digest(value.getBytes(
            StandardCharsets.UTF_8)));
    }

    private static Location copy(Location value)
    {
        return value == null ? null : new Location(value.row(), value.col());
    }

    /** Immutable identity-only member of the frozen edible candidate set. */
    public static final class CandidateFact
    {
        private final long preyId;
        private final String preySpecies;
        private final Location location;
        private final int topologyDistance;

        private CandidateFact(long preyId, String preySpecies, Location location,
                              int topologyDistance)
        {
            if(preyId <= 0L || preySpecies == null || preySpecies.isBlank() ||
               location == null || topologyDistance < 0) {
                throw new IllegalArgumentException(
                    "complete frozen HUNT candidate facts are required");
            }
            this.preyId = preyId;
            this.preySpecies = preySpecies;
            this.location = copy(location);
            this.topologyDistance = topologyDistance;
        }

        private static CandidateFact capture(HuntLocalOpportunity.Candidate value)
        {
            SavannahAnimal prey = value.getPrey();
            return new CandidateFact(prey.getId(), prey.getProfile().getName(),
                value.getLocation(), value.getDistance());
        }

        public long getPreyId() { return preyId; }
        public String getPreySpecies() { return preySpecies; }
        public Location getLocation() { return copy(location); }
        public int getTopologyDistance() { return topologyDistance; }

        @Override
        public String toString()
        {
            return preySpecies + '#' + preyId + '@' + location + ':' +
                topologyDistance;
        }
    }
}
