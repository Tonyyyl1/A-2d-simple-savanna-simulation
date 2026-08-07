#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
classes=${1:-build/classes}

for required in \
    "$classes/Simulator.class" \
    "$classes/SimulatorView.class" \
    "$classes/SimulatorView\$FieldView.class"; do
    if [ ! -f "$required" ]; then
        echo "Overview frame verification requires compiled class: $required" >&2
        exit 1
    fi
done

tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/savanna-overview-contract.XXXXXX")
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM

javap -classpath "$classes" -c -p SimulatorView > "$tmp_dir/SimulatorView.javap"
javap -classpath "$classes" -c -p 'SimulatorView$FieldView' \
    > "$tmp_dir/SimulatorView-FieldView.javap"

for class_file in "$classes"/SimulatorView\$*.class; do
    if [ ! -f "$class_file" ]; then
        continue
    fi
    class_name=$(basename "$class_file" .class)
    javap -classpath "$classes" -c -p "$class_name" \
        > "$tmp_dir/$class_name.javap"
done

# This is the planned B1 expected-red gate. It intentionally runs before the
# B2-class requirements so an old candidate fails because its real race is
# still present, rather than merely because OverviewFrameBuffer did not exist.
legacy_mutable_front=false
legacy_resize_rebuild=false
if grep -Eq 'animalImage|animalGraphics|clearAnimalLayer' \
    "$tmp_dir/SimulatorView-FieldView.javap"; then
    legacy_mutable_front=true
fi
for dump in "$tmp_dir"/SimulatorView\$*.javap; do
    if [ -f "$dump" ] &&
       grep -q 'public void componentResized' "$dump" &&
       grep -q 'SimulatorView.showStatus' "$dump"; then
        legacy_resize_rebuild=true
    fi
done
if [ "$legacy_mutable_front" = true ] &&
   [ "$legacy_resize_rebuild" = true ]; then
    echo "FAIL legacy B1 transparent-front race detected: mutable ordinary " \
         "front image and resize-triggered showStatus" >&2
    exit 1
fi

for required in \
    "$classes/OverviewFrameBuffer.class" \
    "$classes/OverviewFrameAtomicityProbe.class"; do
    if [ ! -f "$required" ]; then
        echo "Overview frame verification requires B2 class: $required" >&2
        exit 1
    fi
done

javap -classpath "$classes" -c -p OverviewFrameBuffer \
    > "$tmp_dir/OverviewFrameBuffer.javap"

if grep -Eq 'animal(Graphics|Image)' "$tmp_dir/SimulatorView-FieldView.javap"; then
    echo "FAIL FieldView still keeps a mutable ordinary-layer front buffer" >&2
    exit 1
fi

awk '
    /public void showStatus\(int, Field, SimulationContext\);/ {
        in_method = 1
        next
    }
    in_method && /^  (public|private|protected) / { in_method = 0 }
    in_method && /runOnEdtAndWait:\(Ljava\/lang\/Runnable;\)V/ { passed = 1 }
    END { exit passed ? 0 : 1 }
' "$tmp_dir/SimulatorView.javap" || {
    echo "FAIL showStatus does not synchronously marshal its build to the EDT" >&2
    exit 1
}

awk '
    /public void paintComponent\(java\.awt\.Graphics\);/ {
        in_method = 1
        next
    }
    in_method && /^  (public|private|protected) / { in_method = 0 }
    in_method && /OverviewFrameBuffer\.currentFrame/ { reads++ }
    END { exit reads == 1 ? 0 : 1 }
' "$tmp_dir/SimulatorView-FieldView.javap" || {
    echo "FAIL one paint pass does not retain exactly one completed frame" >&2
    exit 1
}

awk '
    /public void commit\(OverviewFrameBuffer\$CompletedFrame\);/ {
        in_method = 1
        next
    }
    in_method && /^  (public|private|protected) / { in_method = 0 }
    in_method && /isOverlayComplete/ { overlay = 1 }
    in_method && /getGeneration/ { generation = 1 }
    in_method && /putfield.*currentFrame/ { publish = 1 }
    END { exit overlay && generation && publish ? 0 : 1 }
' "$tmp_dir/OverviewFrameBuffer.javap" || {
    echo "FAIL OverviewFrameBuffer commit lacks completion or generation gates" >&2
    exit 1
}

for dump in "$tmp_dir"/SimulatorView\$*.javap; do
    if [ -f "$dump" ] &&
       grep -q 'public void componentResized' "$dump" &&
       grep -q 'SimulatorView.showStatus' "$dump"; then
        echo "FAIL resize callback still requests a Field redraw" >&2
        exit 1
    fi
done

if ! grep -q 'SimulatorView.createOnEventDispatchThread' Simulator.java; then
    echo "FAIL Simulator no longer creates SimulatorView on the EDT" >&2
    exit 1
fi

echo "Overview frame atomicity contract verification passed"
