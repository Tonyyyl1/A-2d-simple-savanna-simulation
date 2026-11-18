#!/usr/bin/env sh
set -u

ROOT="$(cd "$(dirname "$0")" && pwd)"
JAR_INPUT="${1:?usage: test-runtime-classpath-identity.sh <runtime.jar> <classes-dir>}"
CLASSES_INPUT="${2:?usage: test-runtime-classpath-identity.sh <runtime.jar> <classes-dir>}"
case "$JAR_INPUT" in /*) JAR="$JAR_INPUT" ;; *) JAR="$ROOT/$JAR_INPUT" ;; esac
case "$CLASSES_INPUT" in /*) CLASSES="$CLASSES_INPUT" ;; *) CLASSES="$ROOT/$CLASSES_INPUT" ;; esac
TMP="$(mktemp -d)" || exit 1
LOG="$TMP/result.log"
PASS_COUNT=0

cleanup() {
    rm -rf "$TMP"
}
trap cleanup EXIT HUP INT TERM

fail() {
    printf 'RUNTIME_CLASSPATH_FIXTURES FAILED: %s\n' "$1" >&2
    exit 1
}

pass() {
    PASS_COUNT=$((PASS_COUNT + 1))
    printf 'PASS %s\n' "$1"
}

expect_pass() {
    name="$1"
    shift
    if ! "$@" > "$LOG" 2>&1
    then
        sed -n '1,100p' "$LOG" >&2
        fail "$name: canonical runtime was rejected"
    fi
    grep -q '^Canonical candidate check PASS: ' "$LOG" || \
        fail "$name: canonical pass marker is missing"
    pass "$name"
}

expect_reject() {
    name="$1"
    expected="$2"
    shift 2
    if "$@" > "$LOG" 2>&1
    then
        fail "$name: invalid runtime was accepted"
    fi
    if grep -q '^Canonical candidate check PASS: ' "$LOG"
    then
        fail "$name: invalid runtime emitted a trusted pass marker"
    fi
    if ! grep -Fqi "$expected" "$LOG"
    then
        sed -n '1,100p' "$LOG" >&2
        fail "$name: rejection did not explain '$expected'"
    fi
    pass "$name"
}

test -f "$JAR" || fail "runtime JAR not found: $JAR"
test -d "$CLASSES" || fail "classes directory not found: $CLASSES"
jar tf "$JAR" | grep -q '^META-INF/savanna-runtime-tree.sha256$' || \
    fail 'runtime JAR is missing the full runtime-tree identity manifest'

expect_pass 'single canonical JAR' \
    java -Djava.awt.headless=true -jar "$JAR" --verify-only
expect_pass 'single canonical classes directory' \
    java -Djava.awt.headless=true -cp "$CLASSES" \
    savanna.VisualSimulationRunner --verify-only

cp "$JAR" "$TMP/duplicate.jar" || fail 'cannot copy duplicate JAR fixture'
expect_reject 'duplicate full JAR' 'classpath identity check failed' \
    java -Djava.awt.headless=true -cp "$JAR:$TMP/duplicate.jar" \
    savanna.VisualSimulationRunner --verify-only
expect_reject 'classes plus JAR' 'classpath identity check failed' \
    java -Djava.awt.headless=true -cp "$CLASSES:$JAR" \
    savanna.VisualSimulationRunner --verify-only

mkdir -p "$TMP/old-classes" || fail 'cannot create stale-class fixture directory'
javac --release 17 -d "$TMP/old-classes" \
    "$ROOT/src/test/fixtures/runtime-classpath-old/savanna/ResourceVector.java" || \
    fail 'cannot compile stale non-core class fixture'
jar cf "$TMP/old-non-core.jar" -C "$TMP/old-classes" . || \
    fail 'cannot package stale non-core class fixture'
expect_reject 'old-first single non-core class' 'classpath identity check failed' \
    java -Djava.awt.headless=true -cp "$TMP/old-non-core.jar:$JAR" \
    savanna.VisualSimulationRunner --verify-only
expect_reject 'new-first single non-core class' 'classpath identity check failed' \
    java -Djava.awt.headless=true -cp "$JAR:$TMP/old-non-core.jar" \
    savanna.VisualSimulationRunner --verify-only

mkdir -p "$TMP/tree"
unzip -q "$JAR" -d "$TMP/tree" || fail 'cannot extract malformed-stamp fixture'
cp "$TMP/tree/META-INF/MANIFEST.MF" "$TMP/MANIFEST.MF"
rm "$TMP/tree/META-INF/MANIFEST.MF"

make_jar() {
    output="$1"
    (cd "$TMP/tree" && jar cfm "$output" "$TMP/MANIFEST.MF" .)
}

cp -R "$TMP/tree" "$TMP/tree-original"
rm "$TMP/tree/META-INF/savanna-build.properties"
make_jar "$TMP/unstamped.jar" || fail 'cannot package unstamped fixture'
expect_reject 'unstamped runtime' 'UNSTAMPED runtime refused' \
    java -Djava.awt.headless=true -jar "$TMP/unstamped.jar" --verify-only

rm -rf "$TMP/tree"
cp -R "$TMP/tree-original" "$TMP/tree"
sed 's/^source.hash=.*/source.hash=/' \
    "$TMP/tree/META-INF/savanna-build.properties" > "$TMP/stamp.tmp"
mv "$TMP/stamp.tmp" "$TMP/tree/META-INF/savanna-build.properties"
make_jar "$TMP/empty-stamp.jar" || fail 'cannot package empty-stamp fixture'
expect_reject 'empty stamp field' 'source.hash' \
    java -Djava.awt.headless=true -jar "$TMP/empty-stamp.jar" --verify-only

rm -rf "$TMP/tree"
cp -R "$TMP/tree-original" "$TMP/tree"
sed 's/^test.source.hash=.*/test.source.hash=not-a-sha256/' \
    "$TMP/tree/META-INF/savanna-build.properties" > "$TMP/stamp.tmp"
mv "$TMP/stamp.tmp" "$TMP/tree/META-INF/savanna-build.properties"
make_jar "$TMP/malformed-stamp.jar" || fail 'cannot package malformed-stamp fixture'
expect_reject 'malformed stamp hash' 'test.source.hash' \
    java -Djava.awt.headless=true -jar "$TMP/malformed-stamp.jar" --verify-only

rm -rf "$TMP/tree"
cp -R "$TMP/tree-original" "$TMP/tree"
rm "$TMP/tree/META-INF/savanna-runtime-tree.sha256"
make_jar "$TMP/missing-runtime-manifest.jar" || \
    fail 'cannot package missing-runtime-manifest fixture'
expect_reject 'missing full runtime manifest' 'runtime-tree identity manifest' \
    java -Djava.awt.headless=true -jar "$TMP/missing-runtime-manifest.jar" --verify-only

printf 'RUNTIME_CLASSPATH_FIXTURES PASS: %s/%s\n' "$PASS_COUNT" "$PASS_COUNT"
