#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
out="${1:-build/a0-baseline}"
mkdir -p "$out"
BUILD_ID="a0-baseline-$(date -u '+%Y%m%dT%H%M%SZ')" ./build.sh
rm -rf build/test-classes
mkdir -p build/test-classes
find src/test/java -type f -name '*.java' -print | LC_ALL=C sort \
    > build/metadata/test-sources.list
javac --release 17 -cp build/classes -d build/test-classes \
    @build/metadata/test-sources.list
classpath="build/classes:build/test-classes"
java -Djava.awt.headless=true -cp "$classpath" savanna.RuntimeIdentityProbe "$out" \
    > "$out/runtime-identity.txt"
java -Djava.awt.headless=true -cp "$classpath" savanna.GrassPhaseProbe 100 \
    > "$out/grass-phase.txt"
java -Djava.awt.headless=true -cp "$classpath" savanna.WaterSafetyProbe 1000 100 \
    > "$out/water-service.txt"
if java -Djava.awt.headless=true -cp "$classpath" savanna.ObservationCadenceProbe \
    > "$out/observation-cadence-expected-red.txt" 2>&1
then
    printf '%s\n' 'Expected-red cadence contract unexpectedly passed.' >&2
    exit 1
fi
printf '%s\n' 'A0 baseline and expected-red evidence captured.'
