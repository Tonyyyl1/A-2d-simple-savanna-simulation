package savanna;

/** Mutable disease state owned by one animal; disease rules remain in the system layer. */
final class AnimalDiseaseState
{
    private boolean infected;
    private double level;

    boolean isInfected() { return infected; }
    double getLevel() { return level; }

    void infect(double startingLevel)
    {
        infected = true;
        level = Math.max(level, Math.min(1.0, startingLevel));
    }

    void increase(double amount)
    {
        if(infected) level = Math.min(1.0, level + amount);
    }

    void recover()
    {
        infected = false;
        level = 0.0;
    }
}
