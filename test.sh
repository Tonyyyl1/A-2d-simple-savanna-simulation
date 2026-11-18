#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
LC_ALL=C
TZ=UTC
export LC_ALL TZ
TEST_BUILD_ID="${BUILD_ID:-local-source-roundtrip-test}"
BUILD_ID="$TEST_BUILD_ID" ./build.sh
rm -rf build/test-classes
mkdir -p build/test-classes
find src/test/java -type f -name '*.java' -print | LC_ALL=C sort \
    > build/metadata/test-sources.list
javac --release 17 -Xlint:unchecked -Xlint:deprecation \
    -cp build/classes -d build/test-classes @build/metadata/test-sources.list
rm -rf build/verification-classes
mkdir -p build/verification-classes
cp -R build/classes/. build/verification-classes/
cp -R build/test-classes/. build/verification-classes/

rm -rf build/test-runtime
mkdir -p build/test-runtime/extracted build/test-runtime/expected/META-INF
SOURCE_HASH="$(shasum -a 256 build/metadata/MAIN_SOURCE_MANIFEST.sha256 | awk '{print $1}')"
TEST_JAR_MANIFEST="build/test-runtime/MANIFEST.MF"
TEST_JAR="build/test-runtime/savanna-simulation-$TEST_BUILD_ID.jar"
{
    printf 'Manifest-Version: 1.0\n'
    printf 'Main-Class: savanna.VisualSimulationRunner\n'
    printf 'Implementation-Title: Savanna Simulation\n'
    printf 'Implementation-Version: %s\n' "$TEST_BUILD_ID"
    printf 'Savanna-Source-Hash: %s\n' "$SOURCE_HASH"
    printf '\n'
} > "$TEST_JAR_MANIFEST"
jar cfm "$TEST_JAR" "$TEST_JAR_MANIFEST" -C build/classes .
./verify-production-jar-boundary.sh "$TEST_JAR"
(
    cd build/test-runtime/extracted
    jar xf "$(cd ../../.. && pwd)/$TEST_JAR"
)
cp -R build/classes/. build/test-runtime/expected/
cp "$TEST_JAR_MANIFEST" build/test-runtime/expected/META-INF/MANIFEST.MF
./verify-runtime-tree-equivalence.sh \
    build/test-runtime/extracted build/test-runtime/expected \
    build/test-runtime/equivalence
./test-runtime-classpath-identity.sh "$TEST_JAR" build/classes

./verify-display-contract.sh build/verification-classes
sh ./verify-overview-frame-contract.sh build/verification-classes
sh ./verify-display-fact-audit-contract.sh build/verification-classes
sh ./verify-render-pipeline-contract.sh build/verification-classes
legacy_dir=$(mktemp -d "${TMPDIR:-/tmp}/savanna-b1-overview-red.XXXXXX")
trap 'rm -rf "$legacy_dir"' EXIT HUP INT TERM
find src/test/fixtures/b1-expected-red -type f -name '*.java' -print | \
    LC_ALL=C sort > "$legacy_dir/sources.list"
test -s "$legacy_dir/sources.list" || {
    echo 'Missing synthetic B1 expected-red Java fixture.' >&2
    exit 1
}
javac --release 17 -d "$legacy_dir" @"$legacy_dir/sources.list"
if legacy_output=$(sh ./verify-overview-frame-contract.sh "$legacy_dir" 2>&1); then
    echo "FAIL B1 unexpectedly passed the B2 overview-frame red gate" >&2
    exit 1
fi
printf '%s\n' "$legacy_output" | grep -q \
    'legacy B1 transparent-front race detected' || {
    echo "FAIL B1 red gate did not identify the legacy transparent-front race" >&2
    exit 1
}
echo "Synthetic B1 expected-red overview-frame verification passed"

core_id_red_dir=$(mktemp -d "${TMPDIR:-/tmp}/savanna-core-id-red.XXXXXX")
trap 'rm -rf "$legacy_dir" "$core_id_red_dir"' EXIT HUP INT TERM
find src/test/fixtures/core-id-expected-red -type f -name '*.java' -print | \
    LC_ALL=C sort > "$core_id_red_dir/sources.list"
test -s "$core_id_red_dir/sources.list" || {
    echo 'Missing CORE-ID expected-red Java fixture.' >&2
    exit 1
}
javac --release 17 -d "$core_id_red_dir" @"$core_id_red_dir/sources.list"
if core_id_red_output=$(java -cp "$core_id_red_dir" \
    savanna.LegacyGlobalIdExpectedRed 2>&1)
then
    echo 'FAIL legacy process-global identity unexpectedly passed' >&2
    exit 1
fi
printf '%s\n' "$core_id_red_output" | grep -q 'LEGACY_CORE_ID_COLLISION' || {
    echo 'FAIL CORE-ID red gate did not reproduce the legacy collision' >&2
    exit 1
}
echo "Synthetic CORE-ID expected-red verification passed"

./test-artifact-identity.sh
./test-runtime-tree-equivalence.sh
java -Duser.language=en -Duser.country=US -Djava.awt.headless=true \
    -cp "$TEST_JAR:build/test-classes" savanna.AllTests
java -Djava.awt.headless=true -jar "$TEST_JAR" --verify-only \
    > build/test-runtime/JAVA_JAR_VERIFY_ONLY.txt
grep -q '^Canonical candidate check PASS: ' \
    build/test-runtime/JAVA_JAR_VERIFY_ONLY.txt
