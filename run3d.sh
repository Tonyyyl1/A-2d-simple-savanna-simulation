#!/usr/bin/env sh
# Starts the local 3D server (plan v3.2 V2): builds, then serves http://127.0.0.1:8787/.
#   ./run3d.sh [--port= --seed= --steps= --speed= --record= --replay= --no-open ...]
set -eu
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
BUILD_ID=local-dev ./build.sh >/dev/null
exec java -Xmx2g -Dsavanna.candidate.class=WORKTREE -cp build/classes savanna.SimServer "$@"
