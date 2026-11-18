#!/usr/bin/env sh
set -u

ROOT="$(cd "$(dirname "$0")" && pwd)"
HELPER="$ROOT/verify-runtime-tree-equivalence.sh"
TMP="$(mktemp -d)" || exit 1
LEFT="$TMP/candidate"
RIGHT="$TMP/rebuilt"
EVIDENCE="$TMP/evidence"
LOG="$TMP/result.log"
PASS_COUNT=0

cleanup() {
    rm -rf "$TMP"
}
trap cleanup EXIT HUP INT TERM

fail() {
    printf 'RUNTIME_TREE_FIXTURES FAILED: %s\n' "$1" >&2
    exit 1
}

pass() {
    PASS_COUNT=$((PASS_COUNT + 1))
    printf 'PASS %s\n' "$1"
}

write_stamp() {
    path="$1"
    build_id="$2"
    source_hash="$3"
    test_hash="$4"
    main_class="$5"
    built_at="$6"
    {
        printf 'build.id=%s\n' "$build_id"
        printf 'source.hash=%s\n' "$source_hash"
        printf 'test.source.hash=%s\n' "$test_hash"
        printf 'runtime.tree.hash=dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd\n'
        printf 'main.class=%s\n' "$main_class"
        printf 'built.at.utc=%s\n' "$built_at"
    } > "$path"
}

write_manifest() {
    path="$1"
    version="$2"
    {
        printf 'Manifest-Version: 1.0\n'
        printf 'Main-Class: savanna.VisualSimulationRunner\n'
        printf 'Implementation-Title: Savanna Simulation\n'
        printf 'Implementation-Version: %s\n' "$version"
        printf 'Savanna-Source-Hash: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n'
        printf '\n'
    } > "$path"
}

reset_fixture() {
    rm -rf "$LEFT" "$RIGHT" "$EVIDENCE"
    mkdir -p "$LEFT/savanna" "$LEFT/assets" "$LEFT/META-INF"
    mkdir -p "$RIGHT/savanna" "$RIGHT/assets" "$RIGHT/META-INF"
    printf 'core bytecode\n' > "$LEFT/savanna/Core.class"
    printf 'non-core bytecode\n' > "$LEFT/savanna/NonCore.class"
    printf 'theme=ochre\n' > "$LEFT/assets/theme.properties"
    cp -R "$LEFT/savanna/." "$RIGHT/savanna/"
    cp -R "$LEFT/assets/." "$RIGHT/assets/"
    write_stamp "$LEFT/META-INF/savanna-build.properties" \
        candidate-build \
        aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
        bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb \
        savanna.VisualSimulationRunner 2026-08-01T00:00:00Z
    write_stamp "$RIGHT/META-INF/savanna-build.properties" \
        rebuilt-build \
        aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
        bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb \
        savanna.VisualSimulationRunner 2026-08-01T01:00:00Z
    write_manifest "$LEFT/META-INF/MANIFEST.MF" candidate-build
    write_manifest "$RIGHT/META-INF/MANIFEST.MF" rebuilt-build
}

expect_pass_unchanged() {
    before="$(find "$LEFT" "$RIGHT" -type f -exec shasum -a 256 {} \; | LC_ALL=C sort)" \
        || fail 'cannot snapshot runtime-tree fixture'
    if ! "$HELPER" "$LEFT" "$RIGHT" "$EVIDENCE" > "$LOG" 2>&1
    then
        sed -n '1,120p' "$LOG" >&2
        fail 'equivalent full runtime trees were rejected'
    fi
    after="$(find "$LEFT" "$RIGHT" -type f -exec shasum -a 256 {} \; | LC_ALL=C sort)" \
        || fail 'cannot resnapshot runtime-tree fixture'
    test "$before" = "$after" || fail 'runtime-tree verifier modified an input tree'
    pass 'equivalent full class/resource trees'
}

expect_fail() {
    name="$1"
    expected="$2"
    if "$HELPER" "$LEFT" "$RIGHT" "$EVIDENCE" > "$LOG" 2>&1
    then
        fail "$name: divergent runtime trees were accepted"
    fi
    if ! grep -Fq "$expected" "$LOG"
    then
        sed -n '1,120p' "$LOG" >&2
        fail "$name: rejection did not explain '$expected'"
    fi
    pass "$name"
}

test -x "$HELPER" || fail "missing executable helper: $HELPER"

reset_fixture
expect_pass_unchanged

reset_fixture
printf 'tamper\n' >> "$RIGHT/savanna/NonCore.class"
expect_fail 'non-core class tamper' 'class/resource tree differs'

reset_fixture
rm "$RIGHT/assets/theme.properties"
expect_fail 'missing resource' 'class/resource tree differs'

reset_fixture
write_stamp "$RIGHT/META-INF/savanna-build.properties" rebuilt-build \
    cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc \
    bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb \
    savanna.VisualSimulationRunner 2026-08-01T01:00:00Z
expect_fail 'main source identity tamper' 'source.hash differs'

reset_fixture
write_stamp "$RIGHT/META-INF/savanna-build.properties" rebuilt-build \
    aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
    cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc \
    savanna.VisualSimulationRunner 2026-08-01T01:00:00Z
expect_fail 'test source identity tamper' 'test.source.hash differs'

reset_fixture
sed -i.bak '/^main.class=/d' "$RIGHT/META-INF/savanna-build.properties"
rm "$RIGHT/META-INF/savanna-build.properties.bak"
expect_fail 'missing build-stamp field' 'must occur exactly once'

reset_fixture
sed 's/Savanna-Source-Hash: a/Savanna-Source-Hash: c/' \
    "$RIGHT/META-INF/MANIFEST.MF" > "$RIGHT/META-INF/MANIFEST.MF.tmp"
mv "$RIGHT/META-INF/MANIFEST.MF.tmp" "$RIGHT/META-INF/MANIFEST.MF"
expect_fail 'JAR manifest source identity tamper' 'JAR manifest source hash differs'

printf 'RUNTIME_TREE_FIXTURES PASS: %s/%s\n' "$PASS_COUNT" "$PASS_COUNT"
