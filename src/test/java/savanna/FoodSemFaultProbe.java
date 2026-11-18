package savanna;

/** Process-level fault seed proving the FOOD-SEM reconciliation gate turns red. */
public final class FoodSemFaultProbe
{
    private FoodSemFaultProbe() {}

    public static void main(String[] args)
    {
        String mode = args.length == 0 ? "none" : args[0];
        if(!FoodSemTests.reconciles(FoodSemTests.faultLedger(mode))) {
            System.err.println("FOOD_SEM_CONTRACT_REJECTED fault=" + mode);
            System.exit(1);
        }
        System.out.println("FOOD_SEM_CONTRACT_PASS fault=" + mode);
    }
}
