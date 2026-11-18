package savanna;

import java.util.Random;

/**
 * P1 controlled fixtures for critical-water reservation ownership.
 * They construct no Simulator and execute no ecology steps.
 */
public final class P1CriticalWaterReservationTests
{
    private P1CriticalWaterReservationTests()
    {
    }

    public static void main(String[] args)
    {
        TestSupport.startSuite("P1 critical-water reservation fixtures");
        TestSupport.startGroup("P1 critical-water ownership");
        run();
        TestSupport.finishGroup();
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }

    static void run()
    {
        firstClaimantKeepsContestedTarget();
        targetCannotSilentlyReplaceAnotherClaimantsOrigin();
        sameClaimantRetargetReleasesStaleTarget();
    }

    private static void firstClaimantKeepsContestedTarget()
    {
        Fixture fixture = fixture();
        Location target = new Location(3, 3);
        boolean firstAccepted = fixture.context.reserveCriticalWaterMove(fixture.first, target);
        boolean secondAccepted = fixture.context.reserveCriticalWaterMove(fixture.second, target);

        TestSupport.assertTrue("P1 first critical-water claim reports acceptance",
            firstAccepted);
        TestSupport.assertFalse("P1 conflicting critical-water claim reports rejection",
            secondAccepted);
        TestSupport.assertEquals("P1 first critical-water claimant keeps target",
            target, fixture.context.getCriticalWaterReservation(fixture.first.getId()));
        TestSupport.assertEquals("P1 conflicting claimant has no target reservation", null,
            fixture.context.getCriticalWaterReservation(fixture.second.getId()));
        TestSupport.assertTrue("P1 contested target remains reserved for first claimant",
            fixture.context.isCriticalWaterReservedForOther(target,
                fixture.second.getId()));
    }

    private static void targetCannotSilentlyReplaceAnotherClaimantsOrigin()
    {
        Fixture fixture = fixture();
        Location firstTarget = fixture.second.getLocation();
        Location secondTarget = new Location(4, 4);
        boolean firstAccepted = fixture.context.reserveCriticalWaterMove(fixture.first,
            firstTarget);
        boolean secondAccepted = fixture.context.reserveCriticalWaterMove(fixture.second,
            secondTarget);

        TestSupport.assertTrue("P1 origin owner reports acceptance", firstAccepted);
        TestSupport.assertFalse("P1 origin collision reports rejection", secondAccepted);
        TestSupport.assertEquals("P1 first target remains stable through origin collision",
            firstTarget, fixture.context.getCriticalWaterReservation(fixture.first.getId()));
        TestSupport.assertEquals("P1 origin-collision claimant has no target reservation", null,
            fixture.context.getCriticalWaterReservation(fixture.second.getId()));
        TestSupport.assertTrue("P1 first claimant retains ownership of contested origin",
            fixture.context.isCriticalWaterReservedForOther(firstTarget,
                fixture.second.getId()));
    }

    private static void sameClaimantRetargetReleasesStaleTarget()
    {
        Fixture fixture = fixture();
        Location oldTarget = new Location(3, 3);
        Location newTarget = new Location(3, 4);
        boolean firstAccepted = fixture.context.reserveCriticalWaterMove(fixture.first,
            oldTarget);
        boolean retargetAccepted = fixture.context.reserveCriticalWaterMove(fixture.first,
            newTarget);

        TestSupport.assertTrue("P1 initial critical-water claim reports acceptance",
            firstAccepted);
        TestSupport.assertTrue("P1 retarget reports acceptance", retargetAccepted);
        TestSupport.assertEquals("P1 repeated request keeps only the new target",
            newTarget, fixture.context.getCriticalWaterReservation(fixture.first.getId()));
        TestSupport.assertFalse("P1 repeated request releases the stale target",
            fixture.context.isCriticalWaterReservedForOther(oldTarget,
                fixture.second.getId()));
        TestSupport.assertTrue("P1 repeated request reserves the new target",
            fixture.context.isCriticalWaterReservedForOther(newTarget,
                fixture.second.getId()));
    }

    private static Fixture fixture()
    {
        SimulationContext context = new SimulationContext(8, 8,
            SimulationConfig.builder().mapScale(1).randomSeed(1111L)
                .terrainSeed(20260629L).build());
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        Gazelle first = new Gazelle(false, new Location(1, 1), new Random(71L),
            identities);
        Gazelle second = new Gazelle(false, new Location(1, 2), new Random(73L),
            identities);
        return new Fixture(context, first, second);
    }

    private static final class Fixture
    {
        private final SimulationContext context;
        private final Gazelle first;
        private final Gazelle second;

        private Fixture(SimulationContext context, Gazelle first, Gazelle second)
        {
            this.context = context;
            this.first = first;
            this.second = second;
        }
    }
}
