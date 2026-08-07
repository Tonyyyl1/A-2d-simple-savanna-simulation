/** Immutable render-only fact for one currently living animal. */
public final class RenderAnimalSnapshot
{
    public final long animalId;
    public final String species;
    public final int row;
    public final int col;
    public final boolean alive;
    public final String hydrationStage;
    public final String survivalStage;
    public final String lastBehavior;

    public RenderAnimalSnapshot(long animalId, String species, int row, int col,
                                boolean alive, String hydrationStage,
                                String survivalStage, String lastBehavior)
    {
        this.animalId = animalId;
        this.species = species == null ? "" : species;
        this.row = row;
        this.col = col;
        this.alive = alive;
        this.hydrationStage = hydrationStage == null ? "" : hydrationStage;
        this.survivalStage = survivalStage == null ? "" : survivalStage;
        this.lastBehavior = lastBehavior == null ? "" : lastBehavior;
    }
}
