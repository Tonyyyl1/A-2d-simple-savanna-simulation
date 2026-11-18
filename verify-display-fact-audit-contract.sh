#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
classes=${1:-build/classes}

for required in \
    "$classes/DisplayFactAudit.class" \
    "$classes/DisplayFactAuditProbe.class"; do
    if [ ! -f "$required" ]; then
        echo "Display fact audit verification requires compiled class: $required" >&2
        exit 1
    fi
done

if grep -q 'RenderFrame' DisplayFactAudit.java DisplayFactAuditProbe.java; then
    echo "FAIL B3 audit sidecar must not consume or attach A2 RenderFrame" >&2
    exit 1
fi

javap -classpath "$classes" -c -p SimulatorView > /tmp/savanna-b3-view.javap
if grep -q 'RenderFrame' /tmp/savanna-b3-view.javap; then
    echo "FAIL B3 attached RenderFrame to SimulatorView" >&2
    exit 1
fi

javap -classpath "$classes" -c -p 'SimulatorView$FieldView' \
    > /tmp/savanna-b3-field-view.javap
grep -q 'DisplayFactAudit.factFor' /tmp/savanna-b3-field-view.javap || {
    echo "FAIL B3 ordinary marker boundary is not recording fact inputs" >&2
    exit 1
}
grep -q 'refreshInspectFactAudit' /tmp/savanna-b3-view.javap || {
    echo "FAIL B3 inspect storyboard boundary is not recording fact inputs" >&2
    exit 1
}

java -Djava.awt.headless=true -cp "$classes" DisplayFactAuditProbe
echo "Display fact audit contract verification passed"
