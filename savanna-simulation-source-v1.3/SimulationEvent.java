/**
 * A discrete ecological event recorded during one simulation step.
 *
 * Events are written by the simulation systems and consumed by the visual
 * inspect layer. They are observational only and never drive simulation logic.
 */
public class SimulationEvent
{
    public enum EventType
    {
        HUNT,
        GRAZE,
        BIRTH,
        INFECTION,
        RECOVERY,
        DISEASE_DEATH,
        MOVE
    }

    public final EventType type;
    public final int step;
    public final int row;
    public final int col;
    public final int fromRow;
    public final int fromCol;
    public final long actorId;
    public final long targetId;
    public final String actorSpecies;
    public final String targetSpecies;

    public SimulationEvent(EventType type, int step, int row, int col,
                           int fromRow, int fromCol, long actorId,
                           long targetId, String actorSpecies,
                           String targetSpecies)
    {
        this.type = type;
        this.step = step;
        this.row = row;
        this.col = col;
        this.fromRow = fromRow;
        this.fromCol = fromCol;
        this.actorId = actorId;
        this.targetId = targetId;
        this.actorSpecies = actorSpecies == null ? "" : actorSpecies;
        this.targetSpecies = targetSpecies == null ? "" : targetSpecies;
    }

    public static SimulationEvent hunt(int step, SavannahAnimal predator,
                                       SavannahAnimal prey)
    {
        Location preyLocation = prey.getLocation();
        Location predatorLocation = predator.getLocation();
        return new SimulationEvent(EventType.HUNT, step,
            preyLocation.row(), preyLocation.col(),
            predatorLocation.row(), predatorLocation.col(),
            predator.getId(), prey.getId(),
            predator.getProfile().getName(), prey.getProfile().getName());
    }

    public static SimulationEvent graze(int step, SavannahAnimal animal,
                                        Location location)
    {
        return new SimulationEvent(EventType.GRAZE, step,
            location.row(), location.col(), -1, -1,
            animal.getId(), -1L, animal.getProfile().getName(), "");
    }

    public static SimulationEvent birth(int step, SavannahAnimal parent,
                                        SavannahAnimal child, Location location)
    {
        Location parentLocation = parent.getLocation();
        return new SimulationEvent(EventType.BIRTH, step,
            location.row(), location.col(),
            parentLocation.row(), parentLocation.col(),
            parent.getId(), child.getId(),
            parent.getProfile().getName(), child.getProfile().getName());
    }

    public static SimulationEvent infection(int step, SavannahAnimal animal)
    {
        Location location = animal.getLocation();
        return new SimulationEvent(EventType.INFECTION, step,
            location.row(), location.col(), -1, -1,
            animal.getId(), -1L, animal.getProfile().getName(), "");
    }

    public static SimulationEvent infection(int step, SavannahAnimal source,
                                            SavannahAnimal target)
    {
        Location targetLocation = target.getLocation();
        Location sourceLocation = source.getLocation();
        int sourceRow = sourceLocation == null ? -1 : sourceLocation.row();
        int sourceCol = sourceLocation == null ? -1 : sourceLocation.col();
        return new SimulationEvent(EventType.INFECTION, step,
            targetLocation.row(), targetLocation.col(),
            sourceRow, sourceCol,
            source.getId(), target.getId(),
            source.getProfile().getName(), target.getProfile().getName());
    }

    public static SimulationEvent recovery(int step, SavannahAnimal animal)
    {
        Location location = animal.getLocation();
        return new SimulationEvent(EventType.RECOVERY, step,
            location.row(), location.col(), -1, -1,
            animal.getId(), -1L, animal.getProfile().getName(), "");
    }

    public static SimulationEvent diseaseDeath(int step, SavannahAnimal animal)
    {
        Location location = animal.getLocation();
        return new SimulationEvent(EventType.DISEASE_DEATH, step,
            location.row(), location.col(), -1, -1,
            animal.getId(), -1L, animal.getProfile().getName(), "");
    }

    public static SimulationEvent move(int step, SavannahAnimal animal,
                                       Location from, Location to)
    {
        return new SimulationEvent(EventType.MOVE, step,
            to.row(), to.col(), from.row(), from.col(),
            animal.getId(), -1L, animal.getProfile().getName(), "");
    }
}
