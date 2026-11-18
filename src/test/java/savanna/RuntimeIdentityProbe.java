package savanna;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** R0 acceptance probe for runtime identity, terrain fingerprints and outputs. */
public final class RuntimeIdentityProbe
{
    private RuntimeIdentityProbe()
    {
    }

    public static void main(String[] args) throws Exception
    {
        RuntimeClasspathGuard.verifyOrThrow();
        File output = args.length == 0 ? null : new File(args[0]).getAbsoluteFile();
        if(output != null && !output.exists() && !output.mkdirs()) {
            throw new IllegalStateException("Could not create output directory: " + output);
        }

        SimulationConfig first = config(1111L, 20260629L);
        SimulationConfig ecologyOnly = config(999999L, 20260629L);
        SimulationConfig terrainChanged = config(1111L, 20260630L);
        SimulationContext firstContext = new SimulationContext(240, 360, first);
        SimulationContext repeatContext = new SimulationContext(240, 360, first);
        SimulationContext ecologyContext = new SimulationContext(240, 360, ecologyOnly);
        SimulationContext changedContext = new SimulationContext(240, 360,
                                                                 terrainChanged);

        String fingerprint = firstContext.getTerrainMap().getFingerprint();
        require(fingerprint.equals(repeatContext.getTerrainMap().getFingerprint()),
                "same seeds did not reproduce terrain fingerprint");
        require(fingerprint.equals(ecologyContext.getTerrainMap().getFingerprint()),
                "ecology seed changed terrain fingerprint");
        require(!fingerprint.equals(changedContext.getTerrainMap().getFingerprint()),
                "locked terrain seeds 20260629 and 20260630 produced one fingerprint");

        Simulator simulator = new Simulator(first, false);
        simulator.setVerbose(false);
        RenderFrame frame = simulator.getRenderFramePublisher().latest();
        RenderProvenance provenance = frame.provenance;
        BuildIdentity identity = BuildIdentity.current();
        require(identity.isStamped(), "candidate probe requires a stamped build");
        require(identity.getBuildId().equals(provenance.getBuildId()),
                "frame build ID differs from runtime stamp");
        require(identity.getSourceHash().equals(provenance.getSourceHash()),
                "frame source hash differs from runtime stamp");
        require(identity.getTestSourceHash().equals(provenance.getTestSourceHash()),
                "frame test source hash differs from runtime stamp");
        require(ResolvedRuntimeConfig.fromLegacy(first).getConfigurationHash().equals(
                    provenance.getConfigurationHash()),
                "frame configuration hash differs from runtime config");
        require(provenance.getEcologyRandomSeed() == first.getRandomSeed(),
                "frame ecology seed differs from config");
        require(provenance.getTerrainSeed() == first.getTerrainSeed(),
                "frame terrain seed differs from config");
        require(fingerprint.equals(provenance.getTerrainFingerprint()),
                "frame terrain fingerprint differs from logical map");

        String[] hud = OverviewRenderer.provenanceLines(frame);
        require(hud[0].contains(identity.getBuildId()),
                "HUD build ID is incomplete");
        require(hud[1].contains(identity.getShortSourceHash()),
                "HUD main manifest is incomplete");
        require(hud[1].contains(BuildIdentity.shortHash(identity.getTestSourceHash())),
                "HUD test manifest is incomplete");
        require(hud[2].contains(BuildIdentity.shortHash(
                    provenance.getConfigurationHash())),
                "HUD config hash is incomplete");
        require(hud[3].contains(Long.toString(first.getRandomSeed())) &&
                hud[3].contains(Long.toString(first.getTerrainSeed())),
                "HUD seed identity is incomplete");
        require(hud[4].contains(TerrainMap.GENERATOR_VERSION) &&
                hud[4].contains(provenance.getShortTerrainFingerprint()),
                "HUD terrain identity is incomplete");

        if(output != null) {
            File png = new File(output, "runtime-identity.png");
            RenderSnapshotTool.writePng(frame, 1387, 829, png);
            File report = new File(output, "runtime-identity-report.html");
            StepReportRecorder recorder = new StepReportRecorder(report.getPath());
            recorder.start(simulator.getField(), simulator.getContext());
            recorder.finish(simulator.getField(), simulator.getContext(),
                            "R0 runtime identity probe");
            String html = Files.readString(report.toPath(), StandardCharsets.UTF_8);
            require(html.contains(identity.getBuildId()), "HTML build ID is missing");
            require(html.contains(identity.getSourceHash()), "HTML source hash is missing");
            require(html.contains(identity.getTestSourceHash()),
                    "HTML test source hash is missing");
            require(html.contains(provenance.getConfigurationHash()),
                    "HTML config hash is missing");
            require(html.contains(provenance.getCandidateClass()),
                    "HTML candidate class is missing");
            require(html.contains(fingerprint), "HTML terrain fingerprint is missing");
        }

        System.out.println("RUNTIME_IDENTITY " + provenance.detailedSummary());
        System.out.println("RUNTIME_IDENTITY terrainSeed20260630=" +
                           changedContext.getTerrainMap().getFingerprint());
        System.out.println("RUNTIME_IDENTITY PASS");
    }

    private static SimulationConfig config(long ecologySeed, long terrainSeed)
    {
        return SimulationConfig.builder().mapScale(3)
            .randomSeed(ecologySeed).terrainSeed(terrainSeed).build();
    }

    private static void require(boolean condition, String message)
    {
        if(!condition) {
            throw new IllegalStateException("R0 identity failure: " + message);
        }
    }
}
