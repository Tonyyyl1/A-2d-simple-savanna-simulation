#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
classes=${1:-build/classes}
class_dir="$classes/savanna"

for required in \
    RenderFrame RenderAnimalSnapshot OverviewRenderer InspectRenderer \
    HistoricalEventOverlay RenderSnapshotTool EventMetadataRegistry \
    RenderPipelineProbe; do
    if [ ! -f "$class_dir/$required.class" ]; then
        echo "Shared render pipeline requires compiled class: $required" >&2
        exit 1
    fi
done

tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/savanna-render-pipeline.XXXXXX")
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM
javap -classpath "$classes" -c -p savanna.SimulatorView > "$tmp_dir/SimulatorView.javap"
javap -classpath "$classes" -c -p 'savanna.SimulatorView$FieldView' \
    > "$tmp_dir/FieldView.javap"
javap -classpath "$classes" -c -p savanna.RenderSnapshotTool \
    > "$tmp_dir/RenderSnapshotTool.javap"

grep -q 'OverviewRenderer.render' "$tmp_dir/FieldView.javap" || {
    echo "FAIL live ordinary view does not use OverviewRenderer" >&2
    exit 1
}
grep -q 'InspectRenderer.render' "$tmp_dir/SimulatorView.javap" || {
    echo "FAIL live inspect view does not use InspectRenderer" >&2
    exit 1
}
grep -q 'OverviewRenderer.render' "$tmp_dir/RenderSnapshotTool.javap" || {
    echo "FAIL fixed PNG tool does not share OverviewRenderer" >&2
    exit 1
}
if grep -Eq '\bField\b|SavannahAnimal' \
    src/main/java/savanna/OverviewRenderer.java \
    src/main/java/savanna/InspectRenderer.java; then
    echo "FAIL shared renderers read mutable Field or SavannahAnimal facts" >&2
    exit 1
fi
grep -q 'HistoricalEventOverlay.Entry' "$tmp_dir/SimulatorView.javap" || {
    echo "FAIL historical events are not kept in a separate overlay" >&2
    exit 1
}

java -Djava.awt.headless=true -cp "$classes" savanna.RenderPipelineProbe
echo "Shared render pipeline contract verification passed"
