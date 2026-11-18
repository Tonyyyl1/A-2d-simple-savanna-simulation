#!/usr/bin/env sh
set -eu

JAR="${1:?usage: verify-production-jar-boundary.sh <candidate.jar>}"
test -f "$JAR"
LIST="$(mktemp)"
trap 'rm -f "$LIST"' EXIT HUP INT TERM
jar tf "$JAR" > "$LIST"

grep -q '^savanna/VisualSimulationRunner.class$' "$LIST"
grep -q '^savanna/Simulator.class$' "$LIST"
grep -q '^META-INF/savanna-build.properties$' "$LIST"
grep -q '^META-INF/savanna-runtime-tree.sha256$' "$LIST"
CLASS_COUNT="$(grep -c '\.class$' "$LIST")"
test "$CLASS_COUNT" -gt 0

if rg -q '^savanna/(AllTests|TestSupport|.*Tests|.*Probe|RenderPerformanceDiagnostic|RenderSnapshotTool|SimulationExperimentRunner|SimulationFingerprint|SnapshotRecorder)(\$.*)?\.class$' \
    "$LIST"
then
    printf 'Production JAR contains test, probe, or developer-tool classes:\n' >&2
    rg '^savanna/(AllTests|TestSupport|.*Tests|.*Probe|RenderPerformanceDiagnostic|RenderSnapshotTool|SimulationExperimentRunner|SimulationFingerprint|SnapshotRecorder)(\$.*)?\.class$' \
        "$LIST" >&2
    exit 1
fi

ROOT_CLASSES="$(rg '^[^/]+\.class$' "$LIST" || true)"
test -z "$ROOT_CLASSES" || {
    printf 'Production JAR contains default-package classes:\n%s\n' \
        "$ROOT_CLASSES" >&2
    exit 1
}
printf 'Production JAR boundary PASS: classes=%s\n' "$CLASS_COUNT"
