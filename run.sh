#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
printf '%s\n' 'Compatibility wrapper: run.sh delegates only to the current worktree build.'
exec "$ROOT/run-worktree.sh" "$@"
