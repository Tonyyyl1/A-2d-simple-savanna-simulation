#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
classes=${1:-build/classes}

if [ ! -f "$classes/Simulator.class" ] ||
   [ ! -f "$classes/SimulatorView.class" ] ||
   [ ! -f "$classes/SimulatorView\$FieldView.class" ]; then
    echo "Display contract verification requires compiled classes in: $classes" >&2
    exit 1
fi

tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/savanna-display-contract.XXXXXX")
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM

javap -classpath "$classes" -c -p Simulator > "$tmp_dir/Simulator.javap"
javap -classpath "$classes" -c -p SimulatorView > "$tmp_dir/SimulatorView.javap"
javap -classpath "$classes" -c -p 'SimulatorView$FieldView' \
    > "$tmp_dir/SimulatorView-FieldView.javap"

awk '
    /public void simulate\(int\);/ { in_method = 1; next }
    in_method && /public void simulateOneStep\(\);/ { in_method = 0 }
    in_method && /shouldRefreshVisualStep:\(\)Z/ { saw_refresh = 1; next }
    in_method && saw_refresh && /ifeq/ { saw_gate = 1; next }
    in_method && /delay:\(I\)V/ && saw_refresh && saw_gate { passed = 1 }
    END { exit passed ? 0 : 1 }
' "$tmp_dir/Simulator.javap" || {
    echo "FAIL Simulator visual delay is not gated by shouldRefreshVisualStep()" >&2
    exit 1
}

awk '
    /private javax\.swing\.Timer sceneTimer\(\);/ { in_method = 1; next }
    in_method && /private void stopSceneTimer\(\);/ { in_method = 0 }
    in_method && /bipush[[:space:]]+33/ { saw_33 = 1 }
    in_method && /javax\/swing\/Timer\."<init>"/ { saw_timer = 1 }
    END { exit saw_33 && saw_timer ? 0 : 1 }
' "$tmp_dir/SimulatorView.javap" || {
    echo "FAIL sceneTimer is not a 33 ms Swing Timer" >&2
    exit 1
}

awk '
    /private void lambda\$sceneTimer\$[0-9]+\(java\.awt\.event\.ActionEvent\);/ {
        in_method = 1
        next
    }
    in_method && /^  private / { in_method = 0 }
    in_method && /advanceStoryboard:\(\)V/ { passed = 1 }
    END { exit passed ? 0 : 1 }
' "$tmp_dir/SimulatorView.javap" || {
    echo "FAIL sceneTimer callback does not call advanceStoryboard()" >&2
    exit 1
}

awk '
    /private void drawSceneActor\(java\.awt\.Graphics2D, SceneDirector\$SceneActor, double\);/ {
        in_method = 1
        next
    }
    in_method && /^  private / { in_method = 0 }
    in_method && /dload(_3|[[:space:]]+3)/ {
        elapsed_loaded = 1
        next
    }
    in_method && /SceneDirector\$SceneActor\.at:\(D\)/ {
        if(elapsed_loaded) {
            passed = 1
        }
        elapsed_loaded = 0
        next
    }
    in_method && /^[[:space:]]*[0-9]+:/ { elapsed_loaded = 0 }
    END { exit passed ? 0 : 1 }
' "$tmp_dir/SimulatorView-FieldView.javap" || {
    echo "FAIL inspect drawing does not call SceneActor.at(elapsedMs)" >&2
    exit 1
}

awk '
    /private void enterInspectMode\(\);/ { in_method = 1; next }
    in_method && /private void exitInspectMode\(\);/ { in_method = 0 }
    in_method && /SimulationControlHandler\.isPaused:\(\)Z/ { saw_state = 1; next }
    in_method && saw_state && /putfield.*pauseBeforeInspect/ { saw_store = 1; next }
    in_method && saw_store && /getfield.*pauseBeforeInspect/ { saw_restore_gate = 1; next }
    in_method && saw_restore_gate && /ifne/ { saw_branch = 1; next }
    in_method && saw_branch && /iconst_1/ { saw_true = 1; next }
    in_method && saw_true && /SimulationControlHandler\.setPaused:\(Z\)V/ {
        saw_pause = 1
        next
    }
    in_method && saw_pause && /rebuildStoryboard:\(\)V/ { passed = 1 }
    END { exit passed ? 0 : 1 }
' "$tmp_dir/SimulatorView.javap" || {
    echo "FAIL inspect entry does not remember state and pause a running simulation" >&2
    exit 1
}

awk '
    /private void exitInspectMode\(\);/ { in_method = 1; next }
    in_method && /private void rebuildStoryboard\(\);/ { in_method = 0 }
    in_method && /stopSceneTimer:\(\)V/ { saw_stop = 1; next }
    in_method && saw_stop && /getfield.*pauseBeforeInspect/ { saw_restore_gate = 1; next }
    in_method && saw_restore_gate && /ifne/ { saw_branch = 1; next }
    in_method && saw_branch && /iconst_0/ { saw_false = 1; next }
    in_method && saw_false && /SimulationControlHandler\.setPaused:\(Z\)V/ {
        passed = 1
    }
    END { exit passed ? 0 : 1 }
' "$tmp_dir/SimulatorView.javap" || {
    echo "FAIL inspect exit does not restore a previously running simulation" >&2
    exit 1
}

if grep -Eq 'renderTimer|pollRenderFrame|setRenderSource' \
    "$tmp_dir/SimulatorView.javap" "$tmp_dir/SimulatorView-FieldView.javap"; then
    echo "FAIL forbidden render polling replacement found in SimulatorView classes" >&2
    exit 1
fi

echo "Display contract bytecode verification passed"
