import java.util.List;
import java.util.Random;

/**
 * Shared implementation for all savanna animals.
 */
public abstract class SavannahAnimal extends Animal
{
    private static final Random rand = Randomizer.getRandom();
    public static final double MAX_STAMINA = 100.0;
    public static final double STAMINA_MOVE_COST = 0.35;
    public static final double STAMINA_HUNT_COST = 1.20;
    public static final double STAMINA_GRAZE_COST = 0.35;
    public static final double STAMINA_BREED_COST = 1.20;
    private static final int STARVATION_LIMIT = 3;
    private static final int HOURS_PER_DAY = 24;

    private final SpeciesProfile profile;
    private final Sex sex;
    private int age;
    private int foodLevel;
    private int starvingSteps;
    private double stamina;
    private int dailyRestHours;
    private boolean infected;
    private double infectionLevel;

    public SavannahAnimal(SpeciesProfile profile, boolean randomAge, Location location)
    {
        super(location);
        this.profile = profile;
        sex = Sex.random(rand);
        if(randomAge) {
            age = rand.nextInt(profile.getMaxAge());
            foodLevel = 1 + rand.nextInt(Math.max(1, profile.getMaxFoodLevel()));
            stamina = 55.0 + rand.nextDouble() * 45.0;
            infected = rand.nextDouble() < 0.018;
            infectionLevel = infected ? 0.18 + rand.nextDouble() * 0.24 : 0.0;
        }
        else {
            age = 0;
            foodLevel = profile.getMaxFoodLevel();
            stamina = MAX_STAMINA;
            infected = false;
            infectionLevel = 0.0;
        }
        starvingSteps = foodLevel <= 0 ? 1 : 0;
        dailyRestHours = 0;
    }

    public abstract SavannahAnimal createChild(Location location);

    public void act(Field currentField, Field nextFieldState, SimulationContext context)
    {
        if(!isAlive()) {
            return;
        }

        recoverStaminaAtStartOfDay(context);
        incrementAge();
        context.getDiseaseSystem().progressDisease(this, context, currentField);
        useEnergy(context);

        if(isAlive()) {
            List<Location> freeLocations = nextFieldState.getFreeAdjacentLocations(getLocation());
            if(!freeLocations.isEmpty() && !isSurvivalCritical()) {
                context.getBreedingSystem().tryBreed(this, currentField, nextFieldState,
                                                     context, freeLocations);
            }

            Location nextLocation = null;
            if(isActive(context)) {
                if(profile.isPredator()) {
                    nextLocation = context.getPredationSystem().hunt(this, currentField, context);
                }
                else {
                    List<Location> grazingLocations = getGrazingLocations(nextFieldState, freeLocations);
                    nextLocation = context.getFoodSystem().chooseGrazingLocation(this, grazingLocations);
                    context.getFoodSystem().feedHerbivoreAt(this,
                        nextLocation == null ? getLocation() : nextLocation, context);
                }
                if(nextLocation == null && !freeLocations.isEmpty() && shouldMove()) {
                    nextLocation = freeLocations.remove(0);
                }
            }
            else if(!profile.isPredator()) {
                context.getFoodSystem().feedHerbivoreAt(this, getLocation(), context);
            }

            placeInNextField(nextFieldState, nextLocation, freeLocations, context);
        }
    }

    public SpeciesProfile getProfile()
    {
        return profile;
    }

    public Sex getSex()
    {
        return sex;
    }

    public int getAge()
    {
        return age;
    }

    public int getFoodLevel()
    {
        return foodLevel;
    }

    /**
     * @return Food level as a percentage of this species' maximum food level.
     */
    public double getSurvivalPercent()
    {
        double percent = ((double)Math.max(0, foodLevel) /
                          Math.max(1, profile.getMaxFoodLevel())) * 100.0;
        return clamp(percent, 0.0, 100.0);
    }

    /**
     * @return 0 in the normal zone, up to 1 in the survival zone.
     */
    public double getSurvivalPressure()
    {
        double survivalPercent = getSurvivalPercent();
        if(survivalPercent > 70.0) {
            return 0.0;
        }
        else if(survivalPercent >= 30.0) {
            return (70.0 - survivalPercent) / 40.0;
        }
        else {
            return 1.0;
        }
    }

    /**
     * @return true when survival behaviour should override ordinary priorities.
     */
    public boolean isSurvivalCritical()
    {
        return getSurvivalPercent() < 30.0;
    }

    /**
     * @return Current stamina percentage.
     */
    public double getStaminaPercent()
    {
        return stamina;
    }

    /**
     * @return Current stamina stage.
     */
    public StaminaStage getStaminaStage()
    {
        if(stamina >= 67.0) {
            return StaminaStage.HIGH;
        }
        else if(stamina >= 34.0) {
            return StaminaStage.MEDIUM;
        }
        else {
            return StaminaStage.LOW;
        }
    }

    /**
     * @return A behaviour multiplier based on stamina stage and survival burst.
     */
    public double getEffectiveStaminaMultiplier()
    {
        double multiplier;
        if(stamina >= 67.0) {
            multiplier = 0.95 + ((stamina - 67.0) / 33.0) * 0.10;
        }
        else if(stamina >= 34.0) {
            multiplier = 0.72 + ((stamina - 34.0) / 33.0) * 0.16;
        }
        else {
            multiplier = 0.50 + (stamina / 34.0) * 0.15;
        }
        if(isSurvivalCritical()) {
            multiplier *= 1.20;
        }
        return clamp(multiplier, 0.20, 1.20);
    }

    /**
     * @return Consecutive steps spent with no food.
     */
    public int getStarvingSteps()
    {
        return starvingSteps;
    }

    public LifeStage getLifeStage()
    {
        if(age <= profile.getCubAgeLimit()) {
            return LifeStage.CUB;
        }
        else if(age < profile.getAdultAge()) {
            return LifeStage.JUVENILE;
        }
        else {
            return LifeStage.ADULT;
        }
    }

    public boolean isAdult()
    {
        return getLifeStage() == LifeStage.ADULT;
    }

    public boolean canBreed()
    {
        return age >= profile.getBreedingAge() &&
               foodLevel > profile.getMaxFoodLevel() / 3 &&
               !isSurvivalCritical() &&
               stamina >= 30.0 &&
               isAlive();
    }

    public void feed(int food)
    {
        foodLevel = Math.min(profile.getMaxFoodLevel(), foodLevel + food);
        if(foodLevel > 0) {
            starvingSteps = 0;
        }
    }

    /**
     * Spend stamina on an activity.
     */
    public void spendStamina(double amount)
    {
        double cost = amount;
        if(infected) {
            cost *= 1.18;
        }
        stamina = clamp(stamina - cost, 0.0, MAX_STAMINA);
    }

    /**
     * Record a resting hour for daily stamina recovery.
     */
    public void recordRestHour()
    {
        dailyRestHours = Math.min(HOURS_PER_DAY, dailyRestHours + 1);
    }

    /**
     * Apply stamina cost for a successful breeding event.
     */
    public void spendBreedingStamina()
    {
        spendStamina(STAMINA_BREED_COST);
    }

    public boolean isInfected()
    {
        return infected;
    }

    public void infect(double startingLevel)
    {
        infected = true;
        infectionLevel = Math.max(infectionLevel, Math.min(1.0, startingLevel));
    }

    public void increaseInfection(double amount)
    {
        if(infected) {
            infectionLevel = Math.min(1.0, infectionLevel + amount);
        }
    }

    public void recoverFromDisease()
    {
        infected = false;
        infectionLevel = 0.0;
    }

    public double getInfectionLevel()
    {
        return infectionLevel;
    }

    public double getInfectiousness()
    {
        return infected ? 0.50 + infectionLevel : 0.0;
    }

    public double getDiseaseResistance()
    {
        double resistance = profile.getDiseaseResistance();
        if(getLifeStage() == LifeStage.CUB) {
            resistance -= profile.getYoungDiseasePenalty();
        }
        else if(getLifeStage() == LifeStage.JUVENILE) {
            resistance -= profile.getYoungDiseasePenalty() * 0.55;
        }
        return clamp(resistance, 0.05, 0.95);
    }

    public double getPredationVulnerability()
    {
        double vulnerability = profile.getPredationVulnerability();
        if(getLifeStage() == LifeStage.CUB) {
            vulnerability *= 1.70;
        }
        else if(getLifeStage() == LifeStage.JUVENILE) {
            vulnerability *= 1.32;
        }
        if(infected) {
            vulnerability *= 1.24;
        }
        return vulnerability;
    }

    public double getHuntingCondition()
    {
        double condition = 1.0;
        if(infected) {
            condition *= 0.72;
        }
        condition *= 0.88 + getSurvivalPressure() * 0.32;
        condition *= getEffectiveStaminaMultiplier();
        return condition;
    }

    @Override
    public String toString()
    {
        return profile.getName() + "{" +
               "sex=" + sex +
               ", age=" + age +
               ", stage=" + getLifeStage() +
               ", infected=" + infected +
               ", alive=" + isAlive() +
               ", location=" + getLocation() +
               ", foodLevel=" + foodLevel +
               ", survival=" + (int)Math.round(getSurvivalPercent()) +
               ", stamina=" + (int)Math.round(stamina) +
               '}';
    }

    private void incrementAge()
    {
        age++;
        if(age > profile.getMaxAge()) {
            setDead();
        }
    }

    private void useEnergy(SimulationContext context)
    {
        int energyUse = infected ? 2 : 1;
        if(!profile.isPredator() &&
           context.getWeatherSystem().getCurrentWeather() == WeatherType.DROUGHT) {
            energyUse++;
        }
        energyUse += populationPressureEnergyUse(context);
        foodLevel -= energyUse;
        if(foodLevel <= 0) {
            starvingSteps++;
            if(starvingSteps >= STARVATION_LIMIT) {
                setDead();
            }
        }
        else {
            starvingSteps = 0;
        }
    }

    private boolean isActive(SimulationContext context)
    {
        return profile.isActiveDuring(context.getClock().getPhase());
    }

    private boolean shouldMove()
    {
        if(infected && rand.nextDouble() > 0.68) {
            return false;
        }
        return true;
    }

    private List<Location> getGrazingLocations(Field nextFieldState, List<Location> freeLocations)
    {
        if(isSurvivalCritical() || getSurvivalPressure() > 0.65) {
            return nextFieldState.getFreeLocationsWithin(getLocation(), 2);
        }
        return freeLocations;
    }

    private void placeInNextField(Field nextFieldState, Location preferredLocation,
                                  List<Location> freeLocations,
                                  SimulationContext context)
    {
        Location oldLocation = getLocation();
        Location destination = preferredLocation;
        if(destination == null) {
            destination = getLocation();
        }
        if(destination != null && nextFieldState.getAnimalAt(destination) == null) {
            recordMovementOrRest(oldLocation, destination, context);
            setLocation(destination);
            nextFieldState.placeAnimal(this, destination);
        }
        else if(!freeLocations.isEmpty()) {
            destination = freeLocations.remove(0);
            recordMovementOrRest(oldLocation, destination, context);
            setLocation(destination);
            nextFieldState.placeAnimal(this, destination);
        }
        else {
            setDead();
        }
    }

    private void recordMovementOrRest(Location oldLocation, Location destination,
                                      SimulationContext context)
    {
        if(oldLocation != null && oldLocation.equals(destination)) {
            recordRestHour();
        }
        else {
            spendStamina(STAMINA_MOVE_COST);
            if(oldLocation != null && destination != null) {
                context.recordEvent(SimulationEvent.move(context.getStep(),
                                                         this, oldLocation,
                                                         destination));
            }
        }
    }

    private void recoverStaminaAtStartOfDay(SimulationContext context)
    {
        if(context.getStep() > 0 && context.getClock().getHour() == 0) {
            double foodFactor = ((double)Math.max(0, foodLevel)) /
                                Math.max(1, profile.getMaxFoodLevel());
            double restFactor = (double)dailyRestHours / HOURS_PER_DAY;
            double recoveryShare = clamp(foodFactor, 0.0, 1.0) * 0.50 +
                                   clamp(restFactor, 0.0, 1.0) * 0.50;
            stamina += (MAX_STAMINA - stamina) * recoveryShare;
            if(foodFactor < 1.0 || restFactor < 1.0) {
                stamina = Math.min(stamina, 99.0);
            }
            dailyRestHours = 0;
        }
    }

    private int populationPressureEnergyUse(SimulationContext context)
    {
        int population = context.getPopulation(profile.getName());
        int target = populationTarget(context);
        if(population <= target) {
            return 0;
        }
        double pressure = (double)(population - target) / Math.max(1, target);
        if(profile.isPredator()) {
            return pressure > 0.70 ? 1 : 0;
        }
        return Math.min(3, (int)Math.ceil(pressure * 1.35));
    }

    private int populationTarget(SimulationContext context)
    {
        int multiplier = profile.isPredator() ? 5 : 10;
        return Math.max(1, context.getFoundingPopulation(profile) * multiplier);
    }

    private double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
