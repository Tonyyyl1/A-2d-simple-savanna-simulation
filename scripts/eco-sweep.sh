#!/usr/bin/env sh
# Resumable M3 parameter sweep (plan v3.0 §7 M3-0/M3-1).
#   scripts/eco-sweep.sh <grid-file> <out-dir> [EcologySweep options]
# The first call compiles and freezes build/dev-classes + build/dev-test-classes
# into <out-dir>/classes; later calls with the same <out-dir> reuse those frozen
# classes, so editing the source never changes a sweep that is still running or
# being resumed. Options: --steps=1000 --seeds=1111,2111 --thirst=off,on
# --jobs=N (default ECO_JOBS or 3) --rank-only. Each run JVM gets -Xmx$ECO_XMX
# (default 1g) and one-thread parallel GC, about 1.2 GB and one core; keep the total
# well inside physical memory and never run a sweep next to ./check.sh
# milestone. See EcologySweep for the grid format and the ranking.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRID="${1:?usage: eco-sweep.sh <grid-file> <out-dir> [options]}"
OUT="${2:?usage: eco-sweep.sh <grid-file> <out-dir> [options]}"
shift 2
mkdir -p "$OUT"
if test ! -d "$OUT/classes"
then
    "$ROOT/compile-dev.sh" > "$OUT/compile.log" 2>&1 || {
        cat "$OUT/compile.log" >&2; exit 1; }
    mkdir -p "$OUT/classes.tmp"
    cp -R "$ROOT/build/dev-classes/." "$ROOT/build/dev-test-classes/." "$OUT/classes.tmp/"
    mv "$OUT/classes.tmp" "$OUT/classes"
    { git -C "$ROOT" rev-parse HEAD; git -C "$ROOT" status --short; } > "$OUT/classes.source"
fi
cp "$GRID" "$OUT/grid-$(date -u '+%Y%m%dT%H%M%SZ').txt"
exec java -Xmx256m -XX:+UseSerialGC -Djava.awt.headless=true -cp "$OUT/classes" \
    savanna.EcologySweep "$GRID" "$OUT" "$@"
