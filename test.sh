#!/usr/bin/env sh
# Formal build check: stamped production build, production-only JAR boundary,
# the default AllTests suite against that JAR, the display/render bytecode
# contracts and the JAR's own --verify-only entry. ./check.sh milestone runs it.
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
mkdir -p build/test-runtime
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

for CONTRACT in scripts/contracts/*.sh
do
    sh "$CONTRACT" "$(pwd)/build/verification-classes"
done

java -Duser.language=en -Duser.country=US -Djava.awt.headless=true \
    -cp "$TEST_JAR:build/test-classes" savanna.AllTests
java -Djava.awt.headless=true -jar "$TEST_JAR" --verify-only \
    > build/test-runtime/JAVA_JAR_VERIFY_ONLY.txt
grep -q '^Canonical candidate check PASS: ' \
    build/test-runtime/JAVA_JAR_VERIFY_ONLY.txt
echo "TEST_SH PASS jar=$TEST_JAR"
