package savanna;

/** Immutable physiology values captured at one PF1-aF boundary. */
public final class AnimalPhysiologyFact
{
    private final int age;
    private final int food;
    private final double hydration;
    private final double stamina;
    private final double survivalPressure;

    private AnimalPhysiologyFact(int age, int food, double hydration,
                                 double stamina, double survivalPressure)
    {
        this.age = age;
        this.food = food;
        this.hydration = hydration;
        this.stamina = stamina;
        this.survivalPressure = survivalPressure;
    }

    public static AnimalPhysiologyFact capture(SavannahAnimal animal)
    {
        if(animal == null) {
            throw new IllegalArgumentException("animal is required");
        }
        return new AnimalPhysiologyFact(animal.getAge(),
            animal.getFoodLevel(), animal.getHydrationPercent(),
            animal.getStaminaPercent(), animal.getSurvivalPressure());
    }

    public int getAge() { return age; }

    public int getFood() { return food; }

    public double getHydration() { return hydration; }

    public double getStamina() { return stamina; }

    public double getSurvivalPressure() { return survivalPressure; }
}
