#!/usr/bin/env sh
set -eu

TAG="${1:?usage: verify-jar.sh <version-tag>}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
PROJECT="$ROOT"
DIR="$ROOT/releases/$TAG"
JAR="$DIR/savanna-simulation-$TAG.jar"
SOURCE_ZIP="$DIR/savanna-simulation-$TAG-source.zip"
MANIFEST="$DIR/MANIFEST.txt"

test -f "$JAR"
if jar tf "$JAR" | grep -q '^savanna/VisualSimulationRunner.class$'
then
    "$PROJECT/verify-production-jar-boundary.sh" "$JAR"
    TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/savanna-release-verify.XXXXXX")"
    trap 'rm -rf "$TEMP_DIR"' EXIT HUP INT TERM
    test -f "$SOURCE_ZIP"
    unzip -q "$SOURCE_ZIP" -d "$TEMP_DIR/source"
    test -f "$TEMP_DIR/source/SOURCE_ARCHIVE_FILES.txt"
    test -f "$TEMP_DIR/source/SOURCE_ARCHIVE_MANIFEST.sha256"
    (
        cd "$TEMP_DIR/source"
        shasum -a 256 -c SOURCE_ARCHIVE_MANIFEST.sha256
    )
    unzip -Z1 "$SOURCE_ZIP" | sed '/\/$/d' | LC_ALL=C sort \
        > "$TEMP_DIR/archive-actual-files.txt"
    {
        cat "$TEMP_DIR/source/SOURCE_ARCHIVE_FILES.txt"
        printf '%s\n' SOURCE_ARCHIVE_FILES.txt SOURCE_ARCHIVE_MANIFEST.sha256
    } | LC_ALL=C sort -u > "$TEMP_DIR/archive-expected-files.txt"
    diff -u "$TEMP_DIR/archive-expected-files.txt" \
        "$TEMP_DIR/archive-actual-files.txt"

    "$TEMP_DIR/source/generate-source-manifest.sh" \
        "$TEMP_DIR/source/src/main/java" "$TEMP_DIR/main-source-manifest.sha256"
    SOURCE_HASH="$(shasum -a 256 "$TEMP_DIR/main-source-manifest.sha256" |
        awk '{print $1}')"
    STAMP_HASH="$(unzip -p "$JAR" META-INF/savanna-build.properties |
        awk -F= '$1 == "source.hash" {print $2}')"
    test "$SOURCE_HASH" = "$STAMP_HASH"
    BUILD_ID="$(unzip -p "$JAR" META-INF/savanna-build.properties |
        awk -F= '$1 == "build.id" {print $2}')"
    (
        cd "$TEMP_DIR/source"
        BUILD_ID="$BUILD_ID-source-roundtrip" sh ./build.sh
    )
    mkdir -p "$TEMP_DIR/jar"
    (
        cd "$TEMP_DIR/jar"
        jar xf "$JAR"
    )
    for CLASS in VisualSimulationRunner Simulator SimulationContext TerrainMap RenderFrame SimulatorView
    do
        cmp "$TEMP_DIR/jar/savanna/$CLASS.class" \
            "$TEMP_DIR/source/build/classes/savanna/$CLASS.class"
    done

    TEST_SOURCE="$TEMP_DIR/source/src/test/java"
    test -d "$TEST_SOURCE"
    mkdir -p "$TEMP_DIR/test-classes"
    find "$TEST_SOURCE" -type f -name '*.java' -print | LC_ALL=C sort \
        > "$TEMP_DIR/test-sources.list"
    javac --release 17 -cp "$JAR" -d "$TEMP_DIR/test-classes" \
        @"$TEMP_DIR/test-sources.list"
    TEST_CLASSPATH="$JAR:$TEMP_DIR/test-classes"
    java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" savanna.AllTests
    java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" \
        savanna.DisplayFactAuditProbe
    java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" \
        savanna.RenderConsistencyProbe
    java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" \
        savanna.WaterSafetyProbe 1000 100
    java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" \
        savanna.WaterSafetyProbe 18500 500
else
    # Historical default-package releases retain their original verification.
    jar tf "$JAR" > "$DIR/.legacy-jar-list.tmp"
    grep -q '^AllTests.class$' "$DIR/.legacy-jar-list.tmp"
    grep -q '^WaterSafetyProbe.class$' "$DIR/.legacy-jar-list.tmp"
    java -Djava.awt.headless=true -cp "$JAR" AllTests
    java -Djava.awt.headless=true -cp "$JAR" WaterSafetyProbe 1000 100
    java -Djava.awt.headless=true -cp "$JAR" WaterSafetyProbe 18500 500
    rm -f "$DIR/.legacy-jar-list.tmp"
fi
printf 'Verified %s: production boundary + tests + water probes passed\n' \
    "$(date '+%F %T')" >> "$MANIFEST"
