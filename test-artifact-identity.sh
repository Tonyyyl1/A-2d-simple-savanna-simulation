#!/usr/bin/env sh
set -u

ROOT="$(cd "$(dirname "$0")" && pwd)"
HELPER="$ROOT/verify-artifact-checksums.sh"
RUNTIME_VERIFIER="$ROOT/verify-runtime-identity.sh"
TMP="$(mktemp -d)" || exit 1
FIXTURE="$TMP/candidate"
LOG="$TMP/result.log"
PASS_COUNT=0

cleanup() {
    rm -rf "$TMP"
}
trap cleanup EXIT HUP INT TERM

fail() {
    printf 'ARTIFACT_IDENTITY_FIXTURES FAILED: %s\n' "$1" >&2
    exit 1
}

pass() {
    PASS_COUNT=$((PASS_COUNT + 1))
    printf 'PASS %s\n' "$1"
}

jar_name='savanna-simulation-v1.8-trust-v1-fixture-dev.jar'
zip_name='savanna-simulation-v1.8-trust-v1-fixture-dev-source.zip'

reset_fixture() {
    rm -rf "$FIXTURE"
    mkdir -p "$FIXTURE" || fail 'cannot create fixture directory'
    printf 'canonical jar fixture\n' > "$FIXTURE/$jar_name" || fail 'cannot write JAR fixture'
    printf 'canonical source ZIP fixture\n' > "$FIXTURE/$zip_name" || fail 'cannot write ZIP fixture'
    (
        cd "$FIXTURE" || exit 1
        shasum -a 256 "$jar_name" "$zip_name" > SHA256SUMS
    ) || fail 'cannot write canonical checksum fixture'
}

snapshot() {
    (
        cd "$FIXTURE" || exit 1
        find . -type f -print | LC_ALL=C sort | while IFS= read -r file
        do
            shasum -a 256 "$file"
        done
    )
}

expect_pass_unchanged() {
    name="$1"
    before="$(snapshot)" || fail "$name: cannot snapshot fixture"
    if ! "$HELPER" "$FIXTURE" > "$LOG" 2>&1
    then
        sed -n '1,120p' "$LOG" >&2
        fail "$name: canonical fixture was rejected"
    fi
    after="$(snapshot)" || fail "$name: cannot resnapshot fixture"
    if test "$before" != "$after"
    then
        fail "$name: verifier modified candidate content"
    fi
    pass "$name"
}

expect_fail() {
    name="$1"
    expected="$2"
    if "$HELPER" "$FIXTURE" > "$LOG" 2>&1
    then
        fail "$name: malformed fixture was accepted"
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
expect_pass_unchanged 'canonical relative checksum manifest'

reset_fixture
printf 'tamper\n' >> "$FIXTURE/$jar_name"
expect_fail 'tampered JAR' 'JAR checksum mismatch'

reset_fixture
printf 'tamper\n' >> "$FIXTURE/$zip_name"
expect_fail 'tampered source ZIP' 'source ZIP checksum mismatch'

reset_fixture
sed '1s/^[0-9a-f]/0/' "$FIXTURE/SHA256SUMS" > "$FIXTURE/SHA256SUMS.tmp"
mv "$FIXTURE/SHA256SUMS.tmp" "$FIXTURE/SHA256SUMS"
expect_fail 'tampered SHA256SUMS' 'JAR checksum mismatch'

reset_fixture
sed -n '2p' "$FIXTURE/SHA256SUMS" > "$FIXTURE/SHA256SUMS.tmp"
mv "$FIXTURE/SHA256SUMS.tmp" "$FIXTURE/SHA256SUMS"
expect_fail 'missing checksum entry' 'exactly two entries'

reset_fixture
sed -n '1p;1p;2p' "$FIXTURE/SHA256SUMS" > "$FIXTURE/SHA256SUMS.tmp"
mv "$FIXTURE/SHA256SUMS.tmp" "$FIXTURE/SHA256SUMS"
expect_fail 'duplicate checksum entry' 'exactly two entries'

reset_fixture
(
    cd "$FIXTURE" || exit 1
    shasum -a 256 "$FIXTURE/$jar_name" "$FIXTURE/$zip_name" > SHA256SUMS
) || fail 'cannot construct absolute-path fixture'
expect_fail 'absolute checksum paths' 'bare basenames'

reset_fixture
sed "s/$jar_name/not-the-jar.jar/" "$FIXTURE/SHA256SUMS" > "$FIXTURE/SHA256SUMS.tmp"
mv "$FIXTURE/SHA256SUMS.tmp" "$FIXTURE/SHA256SUMS"
expect_fail 'checksum basename mismatch' 'unexpected artifact name'

reset_fixture
cp "$FIXTURE/$jar_name" "$FIXTURE/savanna-simulation-v1.8-duplicate-dev.jar"
expect_fail 'duplicate JAR artifact' 'exactly one candidate JAR'

reset_fixture
rm "$FIXTURE/$zip_name"
expect_fail 'missing source ZIP artifact' 'exactly one source ZIP'

if SKIP_TRUST_GATE=true "$RUNTIME_VERIFIER" "$FIXTURE" > "$LOG" 2>&1
then
    fail 'SKIP_* bypass was accepted'
fi
if ! grep -Fq 'SKIP_*/EXPECTED_* gate overrides are forbidden' "$LOG"
then
    sed -n '1,80p' "$LOG" >&2
    fail 'SKIP_* bypass did not fail at policy validation'
fi
pass 'SKIP_* bypass rejected before verification'

if VERIFY_MODE=acceptance "$RUNTIME_VERIFIER" "$FIXTURE" > "$LOG" 2>&1
then
    fail 'direct acceptance mode was accepted'
fi
if ! grep -Fq 'direct acceptance is forbidden' "$LOG"
then
    sed -n '1,80p' "$LOG" >&2
    fail 'direct acceptance did not fail at policy validation'
fi
pass 'direct acceptance is delegated to the promotion state machine'

printf 'ARTIFACT_IDENTITY_FIXTURES PASS: %s/%s\n' "$PASS_COUNT" "$PASS_COUNT"
