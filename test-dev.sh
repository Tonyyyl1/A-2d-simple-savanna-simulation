#!/usr/bin/env sh
# Worktree-only, named test groups.  This deliberately cannot create formal
# qualification evidence; use test.sh/full only at the declared milestone.
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
MODE="${1:?usage: test-dev.sh <unit|core|system|water|frame|facts|integration-1000|v27-perf|v27-terrain|v27-ecology|v27-capacity-water|scenario <id>>}"
shift
case "$MODE" in
    unit|core|system|water|frame|facts|integration-1000|v27-perf|v27-terrain|v27-ecology|v27-capacity-water)
        test "$#" -eq 0 || { echo 'A test group accepts no extra arguments.' >&2; exit 2; }
        GROUP="$MODE"
        ;;
    scenario)
        ID="${1:?usage: test-dev.sh scenario <core-field|food-semantics|obs-water>}"
        test "$#" -eq 1 || { echo 'A scenario accepts exactly one id.' >&2; exit 2; }
        case "$ID" in
            core-field) GROUP=core ;;
            food-semantics) GROUP=core ;;
            obs-water) GROUP=water ;;
            *) echo "Unknown development scenario: $ID" >&2; exit 2 ;;
        esac
        ;;
    *) echo "Unknown development group: $MODE" >&2; exit 2 ;;
esac

"$ROOT/compile-dev.sh"
exec java -Djava.awt.headless=true -Duser.language=en -Duser.country=US \
    -cp "$ROOT/build/dev-classes:$ROOT/build/dev-test-classes" savanna.AllTests "$GROUP"
