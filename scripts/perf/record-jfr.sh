#!/usr/bin/env bash
cd "$(cd "$(dirname "$0")/../.." && pwd)"
CP="${JFR_CP:-build/perf-cur/classes}"
JFR=$(/usr/libexec/java_home)/bin/jfr
CANDA=capacity.preyOverload=10,grass.capacityScale=0.4,grazing.fullIntakeFill=0.5,grazing.refugeFill=0.15,init.lion=0,init.cheetah=0
rec() { # name flags... -- args...
  local name="$1"; shift
  local flags=(); while [ "$1" != "--" ]; do flags+=("$1"); shift; done; shift
  java -Djava.awt.headless=true -Xmx1g -XX:+UseParallelGC -XX:ParallelGCThreads=1 "${flags[@]}" -XX:StartFlightRecording=filename=build/perf-s2/$name.jfr,settings=profile -cp $CP savanna.EcologyMatrixRunner "$@" > build/perf-s2/$name.run.txt 2>&1
  echo "REC $name rc=$?"
  $JFR print --events jdk.ExecutionSample --stack-depth 64 build/perf-s2/$name.jfr > build/perf-s2/$name.txt
  grep -c '^jdk.ExecutionSample' build/perf-s2/$name.txt
}
uptime; pgrep -fl "(^|/)java .*(savanna\.[A-Z]|EcologySweep)" | wc -l
rec audit-on-default -- 1111 1500 off
rec audit-off-default -Dsavanna.audit=off -- 1111 1500 off
rec audit-on-candA -- 1111 2000 off --params=$CANDA
rec audit-off-thirst-on -Dsavanna.audit=off -- 2111 1000 on
echo JFR_DONE
