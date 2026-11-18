package savanna;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** CORE-FIELD contracts for atomic public placement and private reservation. */
public final class CoreFieldTests
{
    private CoreFieldTests() {}

    public static void main(String[] args)
    {
        TestSupport.startSuite("CORE-FIELD fixtures");
        run();
        TestSupport.finishSuite();
        if(TestSupport.hasFailures()) System.exit(1);
    }

    public static void run()
    {
        TestSupport.startGroup(
            "CORE-FIELD atomic placement and private reservation");
        testInitialPlacementSynchronizesLocation();
        testExistingAnimalMoveSynchronizesEveryLedger();
        testFailedMoveHasNoSideEffects();
        testResolverReservationIsPrivateAndFieldFree();
        testConsistencyCheckDetectsListedAnimalOutsideCells();
        testConsistencyCheckDetectsDuplicateListedAnimal();
        testConsistencyCheckDetectsLocationMismatch();
        testConsistencyCheckDetectsDeadAnimalInCell();
        TestSupport.finishGroup();
    }

    private static void testInitialPlacementSynchronizesLocation()
    {
        Field field = new Field(5, 5);
        Location constructed = new Location(0, 0);
        Location placed = new Location(3, 4);
        Gazelle animal = gazelle(constructed, new AnimalIdentityAllocator());

        boolean accepted = field.tryPlaceAnimal(animal, placed);

        TestSupport.assertTrue("CORE-FIELD initial placement succeeds", accepted);
        TestSupport.assertEquals(
            "CORE-FIELD initial placement synchronizes animal location",
            placed, animal.getLocation());
        TestSupport.assertEquals(
            "CORE-FIELD initial placement owns requested cell", animal,
            field.getAnimalAt(placed));
        TestSupport.assertTrue(
            "CORE-FIELD initial placement leaves a consistent field",
            field.hasConsistentState());
    }

    private static void testExistingAnimalMoveSynchronizesEveryLedger()
    {
        Field field = new Field(5, 5);
        Location origin = new Location(1, 1);
        Location destination = new Location(2, 3);
        Gazelle animal = gazelle(origin, new AnimalIdentityAllocator());
        field.placeAnimal(animal, origin);

        boolean accepted = field.tryPlaceAnimal(animal, destination);

        TestSupport.assertTrue("CORE-FIELD public move succeeds", accepted);
        TestSupport.assertEquals("CORE-FIELD public move clears origin", null,
                                 field.getAnimalAt(origin));
        TestSupport.assertEquals("CORE-FIELD public move owns destination",
                                 animal, field.getAnimalAt(destination));
        TestSupport.assertEquals("CORE-FIELD public move synchronizes location",
                                 destination, animal.getLocation());
        TestSupport.assertEquals("CORE-FIELD public move lists animal once", 1,
                                 field.getAnimals().size());
        TestSupport.assertTrue("CORE-FIELD public move remains consistent",
                               field.hasConsistentState());
    }

    private static void testFailedMoveHasNoSideEffects()
    {
        Field field = new Field(5, 5);
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Location origin = new Location(1, 1);
        Location occupied = new Location(2, 2);
        Gazelle mover = gazelle(origin, identities);
        Gazelle blocker = gazelle(occupied, identities);
        field.placeAnimal(mover, origin);
        field.placeAnimal(blocker, occupied);
        List<Animal> before = field.getAnimals();

        boolean accepted = field.tryPlaceAnimal(mover, occupied);

        TestSupport.assertFalse("CORE-FIELD occupied move is rejected", accepted);
        TestSupport.assertEquals("CORE-FIELD failed move keeps origin owner", mover,
                                 field.getAnimalAt(origin));
        TestSupport.assertEquals(
            "CORE-FIELD failed move keeps destination owner", blocker,
            field.getAnimalAt(occupied));
        TestSupport.assertEquals("CORE-FIELD failed move keeps animal location",
                                 origin, mover.getLocation());
        TestSupport.assertEquals("CORE-FIELD failed move keeps list exact", before,
                                 field.getAnimals());
        TestSupport.assertTrue("CORE-FIELD failed move remains consistent",
                               field.hasConsistentState());
    }

    /*
     * hasConsistentState() counter-examples. Every corruption starts from a field the
     * check accepts, so a false result can only come from the corruption itself.
     */

    private static void testConsistencyCheckDetectsListedAnimalOutsideCells()
    {
        Field field = new Field(5, 5);
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Gazelle placed = gazelle(new Location(1, 1), identities);
        Gazelle stray = gazelle(new Location(3, 3), identities);
        field.placeAnimal(placed, new Location(1, 1));
        TestSupport.assertTrue("CORE-FIELD corruption control: field starts consistent",
                               field.hasConsistentState());

        TestSupport.setField(field, "animals",
                             new ArrayList<Animal>(List.of(placed, stray)));

        TestSupport.assertFalse(
            "CORE-FIELD consistency check rejects a listed animal that owns no cell",
            field.hasConsistentState());
    }

    private static void testConsistencyCheckDetectsDuplicateListedAnimal()
    {
        Field field = new Field(5, 5);
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Gazelle first = gazelle(new Location(1, 1), identities);
        Gazelle second = gazelle(new Location(2, 2), identities);
        field.placeAnimal(first, new Location(1, 1));
        field.placeAnimal(second, new Location(2, 2));
        TestSupport.assertTrue("CORE-FIELD duplicate control: field starts consistent",
                               field.hasConsistentState());

        // Same length as the cell count, so a count-only comparison would accept it.
        TestSupport.setField(field, "animals",
                             new ArrayList<Animal>(List.of(first, first)));

        TestSupport.assertFalse(
            "CORE-FIELD consistency check rejects the same animal listed twice",
            field.hasConsistentState());
    }

    private static void testConsistencyCheckDetectsLocationMismatch()
    {
        Field field = new Field(5, 5);
        Gazelle animal = gazelle(new Location(1, 1), new AnimalIdentityAllocator());
        field.placeAnimal(animal, new Location(1, 1));
        TestSupport.assertTrue("CORE-FIELD location control: field starts consistent",
                               field.hasConsistentState());

        animal.setLocation(new Location(2, 2));

        TestSupport.assertFalse(
            "CORE-FIELD consistency check rejects an animal whose location left its cell",
            field.hasConsistentState());
    }

    private static void testConsistencyCheckDetectsDeadAnimalInCell()
    {
        Field field = new Field(5, 5);
        Gazelle animal = gazelle(new Location(1, 1), new AnimalIdentityAllocator());
        field.placeAnimal(animal, new Location(1, 1));
        TestSupport.assertTrue("CORE-FIELD dead-animal control: field starts consistent",
                               field.hasConsistentState());

        animal.setDead();

        TestSupport.assertFalse(
            "CORE-FIELD consistency check rejects a dead animal that still owns a cell",
            field.hasConsistentState());
    }

    private static void testResolverReservationIsPrivateAndFieldFree()
    {
        boolean found = false;
        boolean privateType = false;
        boolean fieldFree = true;
        try {
            Class<?> board = Class.forName(
                "savanna.PositionStepCoordinator$PositionCommitReservationBoard");
            found = true;
            privateType = Modifier.isPrivate(board.getModifiers());
            for(java.lang.reflect.Field member : board.getDeclaredFields()) {
                Class<?> type = member.getType();
                fieldFree = fieldFree && !Field.class.isAssignableFrom(type) &&
                    !Animal.class.isAssignableFrom(type);
            }
        }
        catch(ClassNotFoundException expected) {
            fieldFree = false;
        }
        TestSupport.assertTrue(
            "CORE-FIELD resolver has a dedicated reservation board", found);
        TestSupport.assertTrue(
            "CORE-FIELD reservation board is coordinator-private", privateType);
        TestSupport.assertTrue(
            "CORE-FIELD reservation board retains no Field or Animal", fieldFree);
    }

    private static Gazelle gazelle(Location location,
                                   AnimalIdentityAllocator identities)
    {
        return new Gazelle(false, location, new Random(1L), identities);
    }
}
