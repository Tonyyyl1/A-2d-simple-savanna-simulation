#!/usr/bin/env sh
set -eu

TAG="${1:?usage: verify-jar.sh <version-tag>}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIR="$ROOT/releases/$TAG"
JAR="$DIR/savanna-simulation-$TAG.jar"
MANIFEST="$DIR/MANIFEST.txt"

test -f "$JAR"
jar tf "$JAR" > /tmp/savanna-simulation-jar-list.txt
grep -q '^AllTests.class$' /tmp/savanna-simulation-jar-list.txt
grep -q '^VisualWaterMask.class$' /tmp/savanna-simulation-jar-list.txt
grep -q '^WaterSafetyProbe.class$' /tmp/savanna-simulation-jar-list.txt
grep -q '^SceneDirector.class$' /tmp/savanna-simulation-jar-list.txt
grep -q '^SimulatorView.class$' /tmp/savanna-simulation-jar-list.txt
java -cp "$JAR" AllTests
java -cp "$JAR" WaterSafetyProbe 1000 100
java -cp "$JAR" WaterSafetyProbe 18500 500
printf 'Verified %s: jar AllTests + WaterSafetyProbe 1000/18500 passed\n' \
    "$(date '+%F %T')" >> "$MANIFEST"
