#!/usr/bin/env sh
# Rebuild the workspace production classes and start the GUI.
#   ./run.sh [VisualSimulationRunner args]
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILD_ID="${BUILD_ID:-local-dev}" "$ROOT/build.sh" >/dev/null
exec java -Dsavanna.candidate.class=WORKTREE -cp "$ROOT/build/classes" \
    savanna.VisualSimulationRunner "$@"
