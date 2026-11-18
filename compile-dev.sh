#!/usr/bin/env sh
# Conservative worktree-only compilation cache.  It never writes the canonical
# build/classes tree used by formal tests, candidate creation, or releases.
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
MAIN_SOURCE_ROOT="$ROOT/src/main/java"
TEST_SOURCE_ROOT="$ROOT/src/test/java"
CACHE="$ROOT/build/dev-cache"
MAIN_CLASSES="$ROOT/build/dev-classes"
TEST_CLASSES="$ROOT/build/dev-test-classes"
OPTIONS='--release 17 -Xlint:unchecked -Xlint:deprecation'

test -d "$MAIN_SOURCE_ROOT" || { echo 'Missing production source root.' >&2; exit 2; }
test -d "$TEST_SOURCE_ROOT" || { echo 'Missing test source root.' >&2; exit 2; }
mkdir -p "$CACHE"
if ! mkdir "$CACHE/.compile-lock" 2>/dev/null
then
    echo 'Development compilation already running; refusing concurrent cache writer.' >&2
    exit 1
fi
trap 'rmdir "$CACHE/.compile-lock"' EXIT HUP INT TERM

write_manifest() {
    ROOT_DIR="$1"
    OUTPUT="$2"
    if test -d "$ROOT_DIR"
    then
        find "$ROOT_DIR" -type f -print | LC_ALL=C sort | while IFS= read -r file
        do
            shasum -a 256 "$file" | awk -v root="$ROOT/" '{sub(root, "", $2); print $1 "  " $2}'
        done > "$OUTPUT"
    else
        : > "$OUTPUT"
    fi
}

write_manifest "$MAIN_SOURCE_ROOT" "$CACHE/main-sources.manifest"
write_manifest "$TEST_SOURCE_ROOT" "$CACHE/test-sources.manifest"
write_manifest "$ROOT/src/main/resources" "$CACHE/main-resources.manifest"
write_manifest "$ROOT/src/test/resources" "$CACHE/test-resources.manifest"
JDK="$(javac -version 2>&1)"
HELPER_HASH="$(shasum -a 256 "$0" | awk '{print $1}')"
{
    printf 'kind=main\noptions=%s\njdk=%s\nhelper=%s\n' "$OPTIONS" "$JDK" "$HELPER_HASH"
    cat "$CACHE/main-sources.manifest" "$CACHE/main-resources.manifest"
} > "$CACHE/main-input.txt"
MAIN_KEY="$(shasum -a 256 "$CACHE/main-input.txt" | awk '{print $1}')"
{
    printf 'kind=test\nmain=%s\noptions=%s\njdk=%s\nhelper=%s\n' "$MAIN_KEY" "$OPTIONS" "$JDK" "$HELPER_HASH"
    cat "$CACHE/test-sources.manifest" "$CACHE/test-resources.manifest"
} > "$CACHE/test-input.txt"
TEST_KEY="$(shasum -a 256 "$CACHE/test-input.txt" | awk '{print $1}')"

MAIN_STATUS=reused
if test ! -f "$CACHE/main.ready" || test "$(cat "$CACHE/main.ready")" != "$MAIN_KEY" || test ! -d "$MAIN_CLASSES"
then
    MAIN_STATUS=compiled
    rm -rf "$MAIN_CLASSES"
    mkdir -p "$MAIN_CLASSES"
    find "$MAIN_SOURCE_ROOT" -type f -name '*.java' -print | LC_ALL=C sort > "$CACHE/main-sources.list"
    if ! javac --release 17 -Xlint:unchecked -Xlint:deprecation -d "$MAIN_CLASSES" "@$CACHE/main-sources.list"
    then
        rm -rf "$MAIN_CLASSES"
        rm -f "$CACHE/main.ready"
        echo 'DEV_COMPILE main=failed test=not-run' >&2
        exit 1
    fi
    if test -d "$ROOT/src/main/resources"
    then
        cp -R "$ROOT/src/main/resources/." "$MAIN_CLASSES/"
    fi
    "$ROOT/generate-runtime-tree-manifest.sh" "$MAIN_CLASSES" \
        "$MAIN_CLASSES/META-INF/savanna-runtime-tree.sha256"
    printf '%s\n' "$MAIN_KEY" > "$CACHE/main.ready"
fi

TEST_STATUS=reused
if test ! -f "$CACHE/test.ready" || test "$(cat "$CACHE/test.ready")" != "$TEST_KEY" || test ! -d "$TEST_CLASSES"
then
    TEST_STATUS=compiled
    rm -rf "$TEST_CLASSES"
    mkdir -p "$TEST_CLASSES"
    find "$TEST_SOURCE_ROOT" -type f -name '*.java' -print | LC_ALL=C sort > "$CACHE/test-sources.list"
    if ! javac --release 17 -Xlint:unchecked -Xlint:deprecation -cp "$MAIN_CLASSES" -d "$TEST_CLASSES" "@$CACHE/test-sources.list"
    then
        rm -rf "$TEST_CLASSES"
        rm -f "$CACHE/test.ready"
        echo "DEV_COMPILE main=$MAIN_STATUS test=failed" >&2
        exit 1
    fi
    printf '%s\n' "$TEST_KEY" > "$CACHE/test.ready"
fi

MAIN_SOURCE_HASH="$(shasum -a 256 "$CACHE/main-sources.manifest" | awk '{print $1}')"
TEST_SOURCE_HASH="$(shasum -a 256 "$CACHE/test-sources.manifest" | awk '{print $1}')"
RUNTIME_TREE_HASH="$(shasum -a 256 "$MAIN_CLASSES/META-INF/savanna-runtime-tree.sha256" | awk '{print $1}')"
mkdir -p "$MAIN_CLASSES/META-INF"
{
    printf 'build.id=dev-cache-%s\n' "$(printf '%s' "$MAIN_KEY" | cut -c1-12)"
    printf 'source.hash=%s\n' "$MAIN_SOURCE_HASH"
    printf 'test.source.hash=%s\n' "$TEST_SOURCE_HASH"
    printf 'main.class=savanna.VisualSimulationRunner\n'
    printf 'built.at.utc=2026-09-06T00:00:00Z\n'
    printf 'runtime.tree.hash=%s\n' "$RUNTIME_TREE_HASH"
} > "$MAIN_CLASSES/META-INF/savanna-build.properties"
printf 'DEV_COMPILE main=%s test=%s mainKey=%s testKey=%s\n' \
    "$MAIN_STATUS" "$TEST_STATUS" "$MAIN_KEY" "$TEST_KEY"
