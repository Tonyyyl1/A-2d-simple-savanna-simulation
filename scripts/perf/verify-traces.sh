#!/usr/bin/env bash
# usage: verify-traces.sh <tag> <classpath> [extra JVM flags...]  -- all five reference traces
set -u
TAG="$1"; CP="$2"; shift 2
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/build/perf-run/$TAG"; mkdir -p "$OUT"
JVM=(java -Djava.awt.headless=true -Xmx1g -XX:+UseParallelGC -XX:ParallelGCThreads=1 "$@" -cp "$CP")
CANDA=capacity.preyOverload=10,grass.capacityScale=0.4,grazing.fullIntakeFill=0.5,grazing.refugeFill=0.15,init.lion=0,init.cheetah=0
cd "$ROOT"
declare -a NAMES ARGS
NAMES=(fp-default-1111-off-3000 fp-default-2111-on-1000 fp-candA-1111-off-3000 fp-proc4201-1111-off-1500 fp-proc4201-2111-on-1000)
ARGS=("1111 3000 off 100" "2111 1000 on 100" "1111 3000 off 100 --params=$CANDA" "1111 1500 off 100 --terrain-profile=PROCEDURAL_V2 --terrain-seed=4201" "2111 1000 on 100 --terrain-profile=PROCEDURAL_V2 --terrain-seed=4201")
ONLY="${TRACE_ONLY:-}"
rc=0
for i in 0 1 2 3 4; do
  n="${NAMES[$i]}"
  if [ -n "$ONLY" ] && [[ " $ONLY " != *" $i "* ]]; then continue; fi
  "${JVM[@]}" savanna.FingerprintTrace ${ARGS[$i]} > "$OUT/$n.txt" 2>"$OUT/err-$i.txt"
  wc -l < "$OUT/$n.txt" | tr -d ' ' | sed "s/^/$n lines=/"
  diff -s "$ROOT/build/perf-base/$n.txt" "$OUT/$n.txt" || rc=1
done
cat "$OUT"/err-*.txt | head -5
echo "TRACE_RESULT tag=$TAG rc=$rc"
