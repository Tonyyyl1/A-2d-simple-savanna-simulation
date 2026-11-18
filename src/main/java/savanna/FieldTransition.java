package savanna;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy direct reservation helper retained for isolated compatibility tests.
 * R2 production commits through {@link PositionStepCoordinator} and never
 * invokes this type.
 */
public final class FieldTransition
{
    private final Field targetField;
    private final TerrainMap terrain;
    private final MovementResolver movementResolver;
    private final Map<TransitionStatus, Integer> counts;
    private final SimulationContext context;

    public FieldTransition(Field targetField, TerrainMap terrain)
    {
        this(targetField, terrain, null);
    }

    FieldTransition(Field targetField, TerrainMap terrain,
                    SimulationContext context)
    {
        if(targetField == null || terrain == null) {
            throw new IllegalArgumentException("target field and terrain are required");
        }
        this.targetField = targetField;
        this.terrain = terrain;
        this.context = context;
        movementResolver = new MovementResolver();
        counts = new EnumMap<>(TransitionStatus.class);
    }

    public Field getTargetField()
    {
        return targetField;
    }

    public FieldTransitionResult tryMove(SavannahAnimal animal,
                                         Location requested,
                                         List<Location> alternatives,
                                         int maxSearchRange)
    {
        if(animal == null || !animal.isAlive()) {
            return record(animal, null, new FieldTransitionResult(
                TransitionStatus.BLOCKED, requested, null, false));
        }
        Location origin = animal.getLocation();
        MovementResolver.Resolution resolution = movementResolver.resolve(
            targetField, terrain, requested, alternatives, origin,
            Math.max(0, maxSearchRange));
        Location destination = resolution.getDestination();
        if(destination == null) {
            TransitionStatus status = resolution.encounteredConflict()
                ? TransitionStatus.CONFLICT_LOST : TransitionStatus.BLOCKED;
            return record(animal, origin, new FieldTransitionResult(status,
                requested, null, resolution.usedSearchFallback()));
        }
        if(!targetField.tryPlaceAnimal(animal, destination)) {
            return record(animal, origin, new FieldTransitionResult(
                TransitionStatus.CONFLICT_LOST, requested, null,
                resolution.usedSearchFallback()));
        }
        TransitionStatus status;
        if(destination.equals(origin)) {
            status = TransitionStatus.STAYED;
        }
        else if(destination.equals(requested)) {
            status = TransitionStatus.MOVED;
        }
        else {
            status = TransitionStatus.REROUTED;
        }
        return record(animal, origin, new FieldTransitionResult(status, requested,
            destination, resolution.usedSearchFallback()));
    }

    public FieldTransitionResult trySpawn(SavannahAnimal child,
                                          Location requested)
    {
        if(child == null || !child.isAlive() || requested == null ||
           !inBounds(requested) || !terrain.isPassable(requested)) {
            if(child != null && child.isAlive()) {
                child.setDead(DeathCause.POSITION_CONFLICT);
            }
            return record(child, null, new FieldTransitionResult(
                TransitionStatus.BLOCKED, requested, null, false));
        }
        if(!targetField.tryPlaceAnimal(child, requested)) {
            child.setDead(DeathCause.POSITION_CONFLICT);
            return record(child, null, new FieldTransitionResult(
                TransitionStatus.CONFLICT_LOST, requested, null, false));
        }
        return record(child, null, new FieldTransitionResult(
            TransitionStatus.SPAWNED, requested, requested, false));
    }

    public int getCount(TransitionStatus status)
    {
        Integer count = counts.get(status);
        return count == null ? 0 : count;
    }

    private FieldTransitionResult record(SavannahAnimal animal, Location origin,
                                         FieldTransitionResult result)
    {
        TransitionStatus status = result.getStatus();
        counts.put(status, getCount(status) + 1);
        if(context != null) {
            context.recordTransitionObservation(new TransitionObservation(
                animal == null ? -1L : animal.getId(), origin,
                result.getRequestedLocation(), result.getDestination(), status,
                result.usedSearchFallback(), context.getStep()));
        }
        return result;
    }

    private boolean inBounds(Location location)
    {
        return location.row() >= 0 && location.row() < terrain.getDepth() &&
               location.col() >= 0 && location.col() < terrain.getWidth();
    }
}
