package savanna;

/** v3.2 A-4: species life-history constants are parameter keys with unchanged defaults. */
public final class SpeciesParameterCardTests
{
    private SpeciesParameterCardTests()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("Species parameter cards");
        run();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    public static void run()
    {
        TestSupport.startGroup("A-4 species parameter cards (defaults as of C-1)");
        // name, breeding probability, litter, food value, grazing rate, plant food value
        Object[][] expected = {
            {"Lion", 0.012, 2, 105, 0.00, 0},
            {"Cheetah", 0.058, 2, 56, 0.00, 0},
            {"Zebra", 0.085, 1, 42, 0.12, 9},
            {"Buffalo", 0.04, 1, 66, 0.15, 10},
            {"Gazelle", 0.128, 2, 34, 0.12, 9}};
        for(Object[] row : expected) {
            SpeciesProfile profile = SpeciesRegistry.getProfile((String)row[0]);
            String name = (String)row[0];
            TestSupport.assertEquals("A4 " + name + " breedingProbability default",
                row[1], Double.valueOf(profile.getBreedingProbability()));
            TestSupport.assertEquals("A4 " + name + " maxLitterSize default",
                ((Integer)row[2]).intValue(), profile.getMaxLitterSize());
            TestSupport.assertEquals("A4 " + name + " foodValue default",
                ((Integer)row[3]).intValue(), profile.getFoodValue());
            TestSupport.assertEquals("A4 " + name + " grazingRate default",
                row[4], Double.valueOf(profile.getGrazingRate()));
            TestSupport.assertEquals("A4 " + name + " plantFoodValue default",
                ((Integer)row[5]).intValue(), profile.getPlantFoodValue());
        }
        SpeciesProfile gazelle = SpeciesRegistry.getProfile("Gazelle");
        EcologyParameters.override(EcologyParameters.defaults()
            .with("species.gazelle.maxLitterSize=1,species.lion.foodValue=120"));
        try {
            TestSupport.assertEquals("A4 override changes Gazelle litter", 1,
                gazelle.getMaxLitterSize());
            TestSupport.assertEquals("A4 override changes Lion food value", 120,
                SpeciesRegistry.getProfile("Lion").getFoodValue());
            TestSupport.assertEquals("A4 override leaves Zebra litter", 1,
                SpeciesRegistry.getProfile("Zebra").getMaxLitterSize());
        }
        finally {
            EcologyParameters.override(null);
        }
        TestSupport.assertEquals("A4 defaults restored after override", 2,
            gazelle.getMaxLitterSize());
        TestSupport.finishGroup("A4_SPECIES_CARDS");
    }
}
