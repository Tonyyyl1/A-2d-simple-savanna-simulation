package savanna;

import java.nio.file.Path;

/** Command-line preflight that verifies the manifest-selected V24 profile. */
public final class V24GateProfileVerifier
{
    private V24GateProfileVerifier() {}

    public static void main(String[] args) throws Exception
    {
        if(args.length != 2) {
            throw new IllegalArgumentException(
                "usage: V24GateProfileVerifier <MANIFEST.json> <profile-dir>");
        }
        V24GateProfile profile = V24GateProfile.verifyCandidateThen(
            Path.of(args[0]), Path.of(args[1]), null);
        System.out.println("V24_GATE_PROFILE_VERIFIED id=" + profile.getId() +
            " kind=" + profile.getKind() + " sha256=" +
            profile.getSha256());
    }
}
