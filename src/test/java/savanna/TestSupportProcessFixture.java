package savanna;

/** Child-process fixture proving truthful group banners and exit status. */
public final class TestSupportProcessFixture
{
    private TestSupportProcessFixture() {}

    public static void main(String[] args)
    {
        if(args.length != 1 ||
           !("PASS".equals(args[0]) || "FAIL".equals(args[0]))) {
            throw new IllegalArgumentException(
                "usage: TestSupportProcessFixture PASS|FAIL");
        }
        TestSupport.startSuite("V24 TestSupport process fixture");
        TestSupport.startGroup("V24 injected assertion group");
        TestSupport.assertTrue("V24 G02 injected assertion",
            "PASS".equals(args[0]));
        TestSupport.finishGroup("V24_TEST_SUPPORT_FIXTURES");
        GateResult result = TestSupport.finishSuite();
        if(result.getExitCode() != 0) System.exit(result.getExitCode());
    }
}
