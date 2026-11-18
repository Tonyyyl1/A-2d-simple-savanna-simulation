#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
classes=${1:-build/classes}
class_dir="$classes/savanna"

for required in \
    "$class_dir/DisplayFactAudit.class" \
    "$class_dir/DisplayFactAuditProbe.class"; do
    if [ ! -f "$required" ]; then
        echo "Display fact audit verification requires compiled class: $required" >&2
        exit 1
    fi
done

javap -classpath "$classes" -c -p savanna.SimulatorView > /tmp/savanna-b3-view.javap
grep -q 'RenderFrame' /tmp/savanna-b3-view.javap || {
    echo "FAIL shared rendering no longer commits immutable RenderFrame facts" >&2
    exit 1
}

javap -classpath "$classes" -c -p 'savanna.SimulatorView$FieldView' \
    > /tmp/savanna-b3-field-view.javap
grep -q 'DisplayFactAudit.captureFrameFacts' /tmp/savanna-b3-field-view.javap || {
    echo "FAIL ordinary renderer boundary is not auditing shared frame inputs" >&2
    exit 1
}
grep -q 'refreshInspectFactAudit' /tmp/savanna-b3-view.javap || {
    echo "FAIL B3 inspect storyboard boundary is not recording fact inputs" >&2
    exit 1
}

java -Djava.awt.headless=true -cp "$classes" savanna.DisplayFactAuditProbe
echo "Display fact audit contract verification passed"
