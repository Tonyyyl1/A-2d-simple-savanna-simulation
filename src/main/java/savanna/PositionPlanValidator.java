package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates totality, uniqueness, passability and fixed-obstacle invariants. */
public final class PositionPlanValidator
{
    private PositionPlanValidator()
    {
    }

    public static PositionPlanValidation validate(
        StepStartPositionSnapshot snapshot, PositionPlan plan,
        PositionTopology topology, Set<Location> fixedOccupancy)
    {
        return validate(snapshot, plan, topology, fixedOccupancy,
            Collections.<PositionActorKey>emptySet());
    }

    /**
     * Validates a production step that may have released adults. A release is
     * legal only for an adult captured by the step-start snapshot; it removes
     * that origin from the destination-uniqueness and total-commit rules.
     */
    public static PositionPlanValidation validate(
        StepStartPositionSnapshot snapshot, PositionPlan plan,
        PositionTopology topology, Set<Location> fixedOccupancy,
        Set<PositionActorKey> releasedAdults)
    {
        List<String> errors = new ArrayList<>();
        if(snapshot == null) {
            errors.add("snapshot is required");
        }
        if(plan == null) {
            errors.add("plan is required");
        }
        if(topology == null) {
            errors.add("topology is required");
        }
        if(!errors.isEmpty()) {
            return new PositionPlanValidation(errors);
        }
        Set<Location> fixed = fixedOccupancy == null
            ? Collections.<Location>emptySet()
            : new HashSet<>(fixedOccupancy);
        Set<PositionActorKey> released = releasedAdults == null
            ? Collections.<PositionActorKey>emptySet()
            : new HashSet<>(releasedAdults);
        Set<Location> destinations = new HashSet<>();
        Map<PositionActorKey, PositionResolution> resolutions =
            plan.getResolutions();

        for(long adultId : snapshot.getAdultIds()) {
            PositionActorKey actor = PositionActorKey.adult(adultId);
            PositionResolution resolution = resolutions.get(actor);
            if(released.contains(actor)) {
                if(resolution == null || resolution.getDisposition() !=
                   PositionPlanDisposition.RELEASED ||
                   resolution.getDestination() != null ||
                   resolution.getReason() != PositionResolutionReason.CLAIM_CANCELLED) {
                    errors.add("released adult must release its origin exactly once: " +
                        actor);
                }
                continue;
            }
            if(resolution == null) {
                errors.add("missing living adult result: " + actor);
                continue;
            }
            if(resolution.getClaimKind() == PositionClaimKind.SPAWN ||
               !resolution.isCommitted()) {
                errors.add("living adult is not committed exactly once: " + actor);
                continue;
            }
            validateDestination(actor, resolution.getDestination(), topology,
                fixed, destinations, errors);
        }

        for(Map.Entry<PositionActorKey, PositionResolution> entry :
                resolutions.entrySet()) {
            PositionActorKey actor = entry.getKey();
            PositionResolution resolution = entry.getValue();
            if(actor == null || resolution == null ||
               !actor.equals(resolution.getActor())) {
                errors.add("resolution key does not match actor");
                continue;
            }
            if(actor.isAdult() && !snapshot.containsAdult(actor.getAdultId())) {
                errors.add("plan contains non-living adult: " + actor);
            }
            if(actor.isAdult() && resolution.getClaimKind() ==
               PositionClaimKind.SPAWN) {
                errors.add("adult key has spawn resolution: " + actor);
            }
            if(actor.isAdult() && resolution.getDisposition() ==
               PositionPlanDisposition.RELEASED && !released.contains(actor)) {
                errors.add("unannounced adult origin release: " + actor);
            }
            if(actor.isSpawn() && resolution.getClaimKind() !=
               PositionClaimKind.SPAWN) {
                errors.add("spawn key has adult resolution: " + actor);
            }
            if(actor.isSpawn() && resolution.getDisposition() ==
               PositionPlanDisposition.RELEASED) {
                errors.add("spawn token cannot release an origin: " + actor);
            }
            if(actor.isSpawn() && resolution.isCommitted()) {
                validateDestination(actor, resolution.getDestination(), topology,
                    fixed, destinations, errors);
            }
            if(actor.isSpawn() && !resolution.isCommitted() &&
               resolution.getDestination() != null) {
                errors.add("rejected spawn has a destination: " + actor);
            }
            int debt = plan.nextWaitingDebtFor(actor);
            if(debt < 0 || debt > topology.getPolicy().getWaitingDebtCap()) {
                errors.add("waiting debt outside policy for " + actor);
            }
        }
        for(PositionActorKey actor : released) {
            if(actor == null || !actor.isAdult() ||
               !snapshot.containsAdult(actor.getAdultId())) {
                errors.add("release is not a snapshot adult: " + actor);
            }
        }
        if(plan.getMetrics().getCandidateVisits() >
           plan.getMetrics().getCandidateVisitLimit()) {
            errors.add("candidate visit budget exceeded");
        }
        return new PositionPlanValidation(errors);
    }

    public static void requireValid(StepStartPositionSnapshot snapshot,
                                    PositionPlan plan,
                                    PositionTopology topology,
                                    Set<Location> fixedOccupancy)
    {
        validate(snapshot, plan, topology, fixedOccupancy).requireValid();
    }

    public static void requireValid(StepStartPositionSnapshot snapshot,
                                    PositionPlan plan,
                                    PositionTopology topology,
                                    Set<Location> fixedOccupancy,
                                    Set<PositionActorKey> releasedAdults)
    {
        validate(snapshot, plan, topology, fixedOccupancy, releasedAdults)
            .requireValid();
    }

    private static void validateDestination(PositionActorKey actor,
                                            Location destination,
                                            PositionTopology topology,
                                            Set<Location> fixed,
                                            Set<Location> destinations,
                                            List<String> errors)
    {
        if(destination == null || !topology.isPassable(destination)) {
            errors.add("non-passable destination for " + actor + ": " +
                destination);
            return;
        }
        if(fixed.contains(destination)) {
            errors.add("fixed destination for " + actor + ": " + destination);
        }
        if(!destinations.add(destination)) {
            errors.add("duplicate destination: " + destination);
        }
    }
}
