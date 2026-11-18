#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
. "$ROOT/runtime-launch-lib.sh"
runtime_exec_classes WORKTREE "$ROOT/build/classes" "$@"
