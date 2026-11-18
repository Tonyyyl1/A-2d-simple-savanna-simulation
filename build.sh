#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
MAIN_SOURCE_ROOT="src/main/java"
test -d "$MAIN_SOURCE_ROOT" || {
    printf 'Missing production source root: %s\n' "$MAIN_SOURCE_ROOT" >&2
    exit 2
}
rm -rf build/classes
mkdir -p build/classes/META-INF build/metadata
./generate-source-manifest.sh "$MAIN_SOURCE_ROOT" \
    build/metadata/MAIN_SOURCE_MANIFEST.sha256
./generate-source-manifest.sh "src/test/java" \
    build/metadata/TEST_SOURCE_MANIFEST.sha256
cp build/metadata/MAIN_SOURCE_MANIFEST.sha256 \
    build/metadata/SOURCE_MANIFEST.sha256
SOURCE_HASH="$(shasum -a 256 build/metadata/MAIN_SOURCE_MANIFEST.sha256 | awk '{print $1}')"
TEST_SOURCE_HASH="$(shasum -a 256 build/metadata/TEST_SOURCE_MANIFEST.sha256 | awk '{print $1}')"
BUILD_ID="${BUILD_ID:-}"
if test -z "$BUILD_ID"
then
    printf '%s\n' 'BUILD_ID is required; use build-dev-candidate.sh for a candidate build.' >&2
    exit 2
fi
# Build against a broadly available desktop Java runtime instead of the
# JDK version used on the build machine.
find "$MAIN_SOURCE_ROOT" -type f -name '*.java' -print | LC_ALL=C sort \
    > build/metadata/main-sources.list
javac --release 17 -Xlint:unchecked -Xlint:deprecation -d build/classes \
    @build/metadata/main-sources.list
if test -d src/main/resources
then
    cp -R src/main/resources/. build/classes/
fi
{
    printf 'build.id=%s\n' "$BUILD_ID"
    printf 'source.hash=%s\n' "$SOURCE_HASH"
    printf 'test.source.hash=%s\n' "$TEST_SOURCE_HASH"
    printf 'main.class=savanna.VisualSimulationRunner\n'
    printf 'built.at.utc=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
} > build/classes/META-INF/savanna-build.properties
./generate-runtime-tree-manifest.sh build/classes \
    build/classes/META-INF/savanna-runtime-tree.sha256
RUNTIME_TREE_HASH="$(shasum -a 256 \
    build/classes/META-INF/savanna-runtime-tree.sha256 | awk '{print $1}')"
printf 'runtime.tree.hash=%s\n' "$RUNTIME_TREE_HASH" \
    >> build/classes/META-INF/savanna-build.properties

: > build/metadata/CORE_CLASSES.sha256
for CLASS in VisualSimulationRunner Simulator SimulationContext TerrainMap RenderFrame SimulatorView
do
    shasum -a 256 "build/classes/savanna/$CLASS.class" |
        awk -v name="$CLASS.class" '{print $1 "  " name}' \
        >> build/metadata/CORE_CLASSES.sha256
done
printf 'Built %s sourceHash=%s\n' "$BUILD_ID" "$SOURCE_HASH"
