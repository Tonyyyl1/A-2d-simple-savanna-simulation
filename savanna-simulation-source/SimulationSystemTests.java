import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests interactions between the main ecological systems.
 */
public class SimulationSystemTests
{
    public static void run()
    {
        TestSupport.startGroup("System tests");
        Randomizer.reset();
        testWeatherModifiers();
        testGrassFeeding();
        testDiseasePredationTransmission();
        testStaminaAffectsPredationContest();
        testBreedingRequiresMate();
        TestSupport.finishGroup();
    }

    private static void testWeatherModifiers()
    {
        TestSupport.assertTrue("rain increases infection risk",
            WeatherType.RAIN.getInfectionModifier() > WeatherType.CLEAR.getInfectionModifier());
        TestSupport.assertTrue("rain increases plant growth",
            WeatherType.RAIN.getPlantGrowthModifier() > WeatherType.CLEAR.getPlantGrowthModifier());
        TestSupport.assertTrue("fog reduces visibility",
            WeatherType.FOG.getVisibilityModifier() < WeatherType.CLEAR.getVisibilityModifier());

        SeasonalWeatherSystem weather = new SeasonalWeatherSystem();
        Lion lion = new Lion(false, new Location(1, 1));
        TestSupport.setField(weather, "currentWeather", WeatherType.CLEAR);
        int clearRange = weather.getHuntingRange(lion);
        TestSupport.setField(weather, "currentWeather", WeatherType.FOG);
        int fogRange = weather.getHuntingRange(lion);
        TestSupport.assertTrue("fog lowers predator hunting range", fogRange < clearRange);
    }

    private static void testGrassFeeding()
    {
        Randomizer.reset();
        Field field = new Field(3, 3);
        Gazelle gazelle = new Gazelle(false, new Location(1, 1));
        TestSupport.setField(gazelle, "foodLevel", 1);
        field.placeAnimal(gazelle, gazelle.getLocation());
        SimulationContext context = new SimulationContext(3, 3);
        context.startStep(0, field);
        int beforeFood = gazelle.getFoodLevel();
        double beforeGrass = context.getFoodSystem().getFoodLevelAt(gazelle.getLocation());
        context.getFoodSystem().feedHerbivoreAt(gazelle, gazelle.getLocation(), context);
        TestSupport.assertTrue("grazing increases herbivore food",
                               gazelle.getFoodLevel() > beforeFood);
        TestSupport.assertTrue("grazing consumes local grass",
            context.getFoodSystem().getFoodLevelAt(gazelle.getLocation()) < beforeGrass);
    }

    private static void testDiseasePredationTransmission()
    {
        Randomizer.reset();
        SimulationContext context = new SimulationContext(3, 3);
        Field field = new Field(3, 3);
        context.startStep(0, field);
        SavannahDiseaseSystem disease = new SavannahDiseaseSystem();
        boolean transmitted = false;
        for(int attempt = 0; attempt < 80 && !transmitted; attempt++) {
            Gazelle prey = new Gazelle(false, new Location(1, 1));
            Lion predator = new Lion(false, new Location(1, 2));
            prey.infect(1.0);
            disease.afterPredation(prey, predator, context);
            transmitted = predator.isInfected();
        }
        TestSupport.assertTrue("infected prey can transmit disease to predator",
                               transmitted);
    }

    private static void testStaminaAffectsPredationContest()
    {
        FoodChainPredationSystem predation = new FoodChainPredationSystem();
        Lion predator = new Lion(false, new Location(1, 1));
        Gazelle prey = new Gazelle(false, new Location(1, 2));

        TestSupport.setField(predator, "stamina", 95.0);
        TestSupport.setField(prey, "stamina", 20.0);
        double predatorAdvantage = TestSupport.invokePrivateDouble(predation,
            "staminaContestModifier",
            new Class<?>[] { SavannahAnimal.class, SavannahAnimal.class },
            new Object[] { predator, prey });

        TestSupport.setField(predator, "stamina", 20.0);
        TestSupport.setField(prey, "stamina", 95.0);
        double preyAdvantage = TestSupport.invokePrivateDouble(predation,
            "staminaContestModifier",
            new Class<?>[] { SavannahAnimal.class, SavannahAnimal.class },
            new Object[] { predator, prey });

        TestSupport.assertTrue("predator stamina advantage improves contest",
                               predatorAdvantage > 1.0);
        TestSupport.assertTrue("prey stamina advantage reduces contest",
                               preyAdvantage < 1.0);
    }

    private static void testBreedingRequiresMate()
    {
        Randomizer.reset();
        MateFindingBreedingSystem breeding = new MateFindingBreedingSystem();
        TestAnimal female = createTestAnimal(Sex.FEMALE, new Location(2, 2));
        TestAnimal male = createTestAnimal(Sex.MALE, new Location(2, 3));
        Field currentField = new Field(5, 5);
        currentField.placeAnimal(female, female.getLocation());
        SimulationContext context = new SimulationContext(5, 5);
        context.startStep(0, currentField);

        Field noMateNext = new Field(5, 5);
        breeding.tryBreed(female, currentField, noMateNext, context,
                          noMateNext.getFreeAdjacentLocations(female.getLocation()));
        TestSupport.assertEquals("female without mate has no child",
                                 0, noMateNext.getLivingPopulation());

        currentField.placeAnimal(male, male.getLocation());
        context.startStep(1, currentField);
        Field mateNext = new Field(5, 5);
        breeding.tryBreed(female, currentField, mateNext, context,
                          mateNext.getFreeAdjacentLocations(female.getLocation()));
        TestSupport.assertTrue("female with adult male nearby can breed",
                               mateNext.getLivingPopulation() > 0);
    }

    private static TestAnimal createTestAnimal(Sex sex, Location location)
    {
        for(int index = 0; index < 100; index++) {
            TestAnimal animal = new TestAnimal(false, location);
            if(animal.getSex() == sex) {
                TestSupport.setField(animal, "age", TEST_PROFILE.getAdultAge());
                TestSupport.setField(animal, "foodLevel", TEST_PROFILE.getMaxFoodLevel());
                TestSupport.setField(animal, "stamina", 100.0);
                return animal;
            }
        }
        throw new IllegalStateException("Could not create test animal with sex " + sex);
    }

    private static final SpeciesProfile TEST_PROFILE =
        new BasicSpeciesProfile("TestAntelope", java.awt.Color.green, false,
            Set.of(), Set.of(DayPhase.DAY, DayPhase.DAWN, DayPhase.DUSK, DayPhase.NIGHT),
            0.0, 2, 1, 2, 2, 100,
            1.0, 1, 2, 1, 50,
            20, 0.0, 0.80, 0.10, 0.30, 0.20, 10);

    private static class TestAnimal extends SavannahAnimal
    {
        TestAnimal(boolean randomAge, Location location)
        {
            super(TEST_PROFILE, randomAge, location);
        }

        public SavannahAnimal createChild(Location location)
        {
            return new TestAnimal(false, location);
        }
    }
}
