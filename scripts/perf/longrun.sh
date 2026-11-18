#!/usr/bin/env bash
# usage: longrun.sh <tag> <classpath> ; candidate A, seed 1111, 10000 steps, thirst off, --diag, audit off
TAG="$1"; CP="$2"
cd "$(cd "$(dirname "$0")/../.." && pwd)"
O=build/perf-run/long-$TAG; rm -rf $O; mkdir -p $O/runs
PARAMS=capacity.preyOverload=10,grass.capacityScale=0.4,grazing.fullIntakeFill=0.5,grazing.refugeFill=0.15,init.lion=0,init.cheetah=0
pgrep -fl 'savanna|EcologySweep'; uptime
/usr/bin/time -l java -Djava.awt.headless=true -Xmx1g -XX:+UseParallelGC -XX:ParallelGCThreads=1 -Dsavanna.audit=off -Xlog:gc:file=$O/gc.log -cp "$CP" savanna.EcologyMatrixRunner 1111 10000 off --params=$PARAMS --diag > $O/runs/338bc0b9bd47-1111-10000-off.log 2> $O/time.txt
echo "LONG_RC $TAG rc=$?"
cat $O/time.txt | grep -E 'real|user|sys|instructions retired|maximum resident|peak memory footprint'
echo "== equivalence vs build/v33-p1-confirm4 (seed 1111 candidate A)"
mkdir -p build/perf-run/long-ref/runs; cp build/v33-p1-confirm4/runs/338bc0b9bd47-1111-10000-off.log build/perf-run/long-ref/runs/
scripts/eco-equivalence.sh build/perf-run/long-ref $O; echo "EQUIV_RC $?"
diff <(grep '^ECO_DIAG' build/perf-run/long-ref/runs/338bc0b9bd47-1111-10000-off.log) <(grep '^ECO_DIAG' $O/runs/338bc0b9bd47-1111-10000-off.log) >/dev/null && echo "DIAG_SAME" || echo "DIAG_DIFF"
grep -c '^ECO_DIAG' $O/runs/*.log
python3 - <<PY
import re
t=0.0;n=0
for l in open("$O/gc.log"):
    m=re.search(r'Pause .*? ([0-9.]+)ms',l)
    if m: t+=float(m.group(1));n+=1
print(f"GC_PAUSE_TOTAL_MS {t:.1f} pauses={n}")
PY
echo LONG_DONE
