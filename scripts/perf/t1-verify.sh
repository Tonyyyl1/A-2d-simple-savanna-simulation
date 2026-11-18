#!/usr/bin/env bash
# usage: scripts/perf/t1-verify.sh <tag> <xmx>   e.g. t1-verify.sh v1 640m
# T1 heap-cap verification (plan author 2026-11-16): one JVM, candidate A, seed 2111, 20000 steps, thirst off,
# audit off, ParallelGC with one GC thread, the given -Xmx. Records /usr/bin/time -l, the GC log and ECO_RESULT.
TAG="$1"; XMX="$2"
cd "$(cd "$(dirname "$0")/../.." && pwd)"
O=build/perf-run/t1-$TAG; rm -rf "$O"; mkdir -p "$O"
PARAMS=capacity.preyOverload=10,grass.capacityScale=0.4,grazing.fullIntakeFill=0.5,grazing.refugeFill=0.15,init.lion=0,init.cheetah=0
echo "T1_START $TAG xmx=$XMX $(date +%T)"; pgrep -fl 'EcologySweep|EcologyMatrixRunner'; uptime
/usr/bin/time -l java -Djava.awt.headless=true -Xmx$XMX -XX:+UseParallelGC -XX:ParallelGCThreads=1 -Dsavanna.audit=off \
  -Xlog:gc:file=$O/gc.log -cp build/perf-s3i/classes savanna.EcologyMatrixRunner 2111 20000 off --params=$PARAMS --diag \
  > $O/out.log 2> $O/time.txt
echo "T1_RC $TAG rc=$? $(date +%T)"
python3 -I scripts/perf/gc-summary.py $O
