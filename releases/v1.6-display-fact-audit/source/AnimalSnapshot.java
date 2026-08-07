/**
 * Immutable visible state for one animal at one simulation step.
 */
public class AnimalSnapshot
{
    public final long id;
    public final String species;
    public final int row;
    public final int col;
    public final boolean alive;
    public final Sex sex;
    public final LifeStage lifeStage;
    public final int age;
    public final int foodLevel;
    public final double staminaPercent;
    public final boolean infected;
    public final double infectionLevel;

    public AnimalSnapshot(SavannahAnimal animal)
    {
        Location location = animal.getLocation();
        id = animal.getId();
        species = animal.getProfile().getName();
        row = location == null ? -1 : location.row();
        col = location == null ? -1 : location.col();
        alive = animal.isAlive();
        sex = animal.getSex();
        lifeStage = animal.getLifeStage();
        age = animal.getAge();
        foodLevel = animal.getFoodLevel();
        staminaPercent = animal.getStaminaPercent();
        infected = animal.isInfected();
        infectionLevel = animal.getInfectionLevel();
    }
}
