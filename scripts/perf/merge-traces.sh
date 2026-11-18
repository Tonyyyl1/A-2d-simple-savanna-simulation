#!/usr/bin/env bash
# usage: scripts/perf/merge-traces.sh <outdir> <classpath> <jobs> [extra JVM flags...]
# The six merge-validation FingerprintTrace runs (v3.3 defaults): the five S0 definitions with the
# old "candidate A" replaced by "no predators" (init.lion=0,init.cheetah=0), plus the P-2 candidate B
# trace. One JVM per trace, -Xmx1g ParallelGC on one thread, <jobs> at a time.
set -u
OUT="$1"; CP="$2"; JOBS="$3"; shift 3
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
mkdir -p "$OUT"; cd "$ROOT"
NOPRED=init.lion=0,init.cheetah=0
CANDB=territory.lion=4,territory.lionRadius=27,species.cheetah.breedingProbability=0.035,predation.cheetahInterference=1.75
NAMES=(fp-default-1111-off-3000 fp-default-2111-on-1000 fp-nopred-1111-off-3000 fp-proc4201-1111-off-1500 fp-proc4201-2111-on-1000 fp-candB-2111-off-3000)
ARGS=("1111 3000 off 100" "2111 1000 on 100" "1111 3000 off 100 --params=$NOPRED" \
      "1111 1500 off 100 --terrain-profile=PROCEDURAL_V2 --terrain-seed=4201" \
      "2111 1000 on 100 --terrain-profile=PROCEDURAL_V2 --terrain-seed=4201" \
      "2111 3000 off 100 --params=$CANDB")
export OUT CP
export FLAGS="$*"
export NAMES_STR="${NAMES[*]}"
one() {
  i=$1
  IFS=' ' read -r -a N <<< "$NAMES_STR"
  A=("1111 3000 off 100" "2111 1000 on 100" "1111 3000 off 100 --params=init.lion=0,init.cheetah=0" \
     "1111 1500 off 100 --terrain-profile=PROCEDURAL_V2 --terrain-seed=4201" \
     "2111 1000 on 100 --terrain-profile=PROCEDURAL_V2 --terrain-seed=4201" \
     "2111 3000 off 100 --params=territory.lion=4,territory.lionRadius=27,species.cheetah.breedingProbability=0.035,predation.cheetahInterference=1.75")
  # shellcheck disable=SC2086
  java -Djava.awt.headless=true -Xmx1g -XX:+UseParallelGC -XX:ParallelGCThreads=1 $FLAGS -cp "$CP" \
    savanna.FingerprintTrace ${A[$i]} > "$OUT/${N[$i]}.txt" 2> "$OUT/err-$i.txt"
  echo "${N[$i]} rc=$? lines=$(wc -l < "$OUT/${N[$i]}.txt" | tr -d ' ')"
}
export -f one
printf '%s\n' 0 1 2 3 4 5 | xargs -P "$JOBS" -I{} bash -c 'one {}'
