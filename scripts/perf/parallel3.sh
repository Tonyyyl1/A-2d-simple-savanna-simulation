#!/usr/bin/env bash
# usage: scripts/perf/parallel3.sh <tag> <classpath> [jvm flags...]
# Three-parallel throughput group (plan author 2026-11-15): candidate A parameters, 2000 steps,
# thirst off, --diag, seeds 1111, 2111, 11111 started together, each JVM with
# -Xmx1g -XX:+UseParallelGC -XX:ParallelGCThreads=1 under /usr/bin/time -l.
# Reports real, user+sys, effective clock and GC pause of each JVM and the mean real.
# Parallel slowdown = mean real / the same config's single-run real median.
# Env SEEDS="1111 2111 11111 21111" runs a group of another size (T1 four-parallel); an extra
# "-Xmx640m" argument overrides the default heap (the last -Xmx wins).
# Never run it next to another simulation JVM: it starts three of its own (about 3.6 GB).
TAG="$1"; CP="$2"; shift 2
cd "$(cd "$(dirname "$0")/../.." && pwd)"
O=build/perf-run/par3-$TAG; rm -rf "$O"; mkdir -p "$O"
PARAMS=capacity.preyOverload=10,grass.capacityScale=0.4,grazing.fullIntakeFill=0.5,grazing.refugeFill=0.15,init.lion=0,init.cheetah=0
others=$(pgrep -fl 'EcologySweep|EcologyMatrixRunner')
if [ -n "$others" ]; then echo "PAR3_ABORT other simulation JVMs running:"; echo "$others"; exit 3; fi
uptime
pids=()
for S in ${SEEDS:-1111 2111 11111}; do
  /usr/bin/time -l java -Djava.awt.headless=true -Xmx1g -XX:+UseParallelGC -XX:ParallelGCThreads=1 \
    -Xlog:gc:file=$O/gc-$S.log "$@" -cp "$CP" savanna.EcologyMatrixRunner $S 2000 off --params=$PARAMS --diag \
    > $O/out-$S.log 2> $O/time-$S.txt &
  pids+=($!)
done
rc=0; for p in "${pids[@]}"; do wait $p || rc=1; done
echo "PAR3_RC $TAG rc=$rc"
python3 -I - "$O" "$TAG" <<'PY'
import os, re, sys, statistics
o, tag = sys.argv[1], sys.argv[2]
reals = []
for s in os.environ.get("SEEDS", "1111 2111 11111").split():
    t = open(f"{o}/time-{s}.txt").read()
    m = re.search(r"^\s*([0-9.]+) real\s+([0-9.]+) user\s+([0-9.]+) sys", t, re.M)
    real, user, sysv = (float(m.group(i)) for i in (1, 2, 3))
    cyc = float(re.search(r"^\s*(\d+)\s+cycles elapsed", t, re.M).group(1))
    ins = float(re.search(r"^\s*(\d+)\s+instructions retired", t, re.M).group(1))
    gc = sum(float(x) for x in re.findall(r"Pause .*? ([0-9.]+)ms", open(f"{o}/gc-{s}.log").read()))
    fin = re.search(r" final=(\S+)", open(f"{o}/out-{s}.log").read())
    reals.append(real)
    print(f"PAR3 {tag} seed={s} real={real:.1f} cpu={user+sysv:.1f} ghz={cyc/1e9/(user+sysv):.3f} "
          f"ipc={ins/cyc:.2f} gc_s={gc/1000:.1f} final={fin.group(1) if fin else '?'}")
print(f"PAR3_MEAN_REAL {tag} {statistics.mean(reals):.1f} (max {max(reals):.1f})")
PY
