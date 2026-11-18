#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:?usage: verify-runtime-identity.sh <candidate-dir>}"
EXPECTED_FIXED_SEED_POPULATION="${EXPECTED_FIXED_SEED_POPULATION:-Lion=299, Cheetah=183, Zebra=631, Buffalo=600, Gazelle=1064}"
EXPECTED_FIXED_SEED_BALANCE_RATIO="${EXPECTED_FIXED_SEED_BALANCE_RATIO:-5.81}"
VERIFY_FIXED_SEED_ECOLOGY="${VERIFY_FIXED_SEED_ECOLOGY:-true}"
VERIFY_MODE="${VERIFY_MODE:-staging}"

fail() {
    printf 'Runtime identity verification FAILED: %s\n' "$1" >&2
    exit 1
}

UNSUPPORTED_GATE_VARIABLES="$(env | awk -F= '
    $1 ~ /^SKIP_/ { print $1; next }
    $1 ~ /^EXPECTED_/ { print $1; next }
    $1 ~ /^VERIFY_/ && $1 != "VERIFY_MODE" && $1 != "VERIFY_FIXED_SEED_ECOLOGY" { print $1 }
' | LC_ALL=C sort -u)"
if test -n "$UNSUPPORTED_GATE_VARIABLES"
then
    printf 'Forbidden gate variables:\n%s\n' "$UNSUPPORTED_GATE_VARIABLES" >&2
    fail 'SKIP_*/EXPECTED_* gate overrides are forbidden; unrecognized VERIFY_* variables are also rejected'
fi
case "$VERIFY_MODE" in
    staging|diagnostic) ;;
    acceptance) fail 'direct acceptance is forbidden; use promote-dev-candidate.sh' ;;
    *) fail 'VERIFY_MODE must be staging or diagnostic' ;;
esac
case "$VERIFY_FIXED_SEED_ECOLOGY" in
    true|false) ;;
    *) fail 'VERIFY_FIXED_SEED_ECOLOGY must be true or false' ;;
esac
case "$OUT" in
    /*) ;;
    *) OUT="$ROOT/$OUT" ;;
esac
case "$VERIFY_MODE:$OUT" in
    staging:"$ROOT"/artifacts/candidates/staging/*.tmp) ;;
    diagnostic:"$ROOT"/artifacts/candidates/accepted/*) ;;
    staging:*) fail "staging verification target must be inside artifacts/candidates/staging/<id>.tmp: $OUT" ;;
    diagnostic:*) fail "diagnostic target must be inside artifacts/candidates/accepted: $OUT" ;;
esac
if ! test -d "$OUT"
then
    fail "candidate directory not found: $OUT"
fi
if test "$VERIFY_MODE" = diagnostic
then
    exec "$ROOT/verify-accepted-candidate.sh" "$OUT"
fi
CURRENT_POINTER="$ROOT/artifacts/candidates/CURRENT_DEV"
if test -f "$CURRENT_POINTER"
then
    if ! POINTER_SHA_BEFORE="$(shasum -a 256 "$CURRENT_POINTER" | awk '{print $1}')"
    then
        fail 'cannot hash CURRENT_DEV before verification'
    fi
else
    POINTER_SHA_BEFORE='MISSING'
fi
if ! "$ROOT/verify-artifact-checksums.sh" "$OUT"
then
    fail "artifact checksum gate rejected candidate: $OUT"
fi
set -- "$OUT"/savanna-simulation-*.jar
JAR="$1"
set -- "$OUT"/savanna-simulation-*-source.zip
SOURCE_ZIP="$1"
EVIDENCE="$OUT/evidence"
if ! mkdir -p "$EVIDENCE"
then
    fail "cannot create evidence directory: $EVIDENCE"
fi
if ! TMP="$(mktemp -d)"
then
    fail 'cannot create verifier temporary directory'
fi
trap 'rm -rf "$TMP"' EXIT HUP INT TERM

if ! ACTUAL_JAR_SHA="$(shasum -a 256 "$JAR" | awk '{print $1}')"
then
    fail 'cannot hash candidate JAR after checksum gate'
fi
if ! unzip -p "$JAR" META-INF/savanna-build.properties > "$TMP/savanna-build.properties"
then
    fail 'candidate JAR is missing META-INF/savanna-build.properties'
fi

property_value() {
    PROPERTY_FILE="$1"
    PROPERTY_KEY="$2"
    PROPERTY_COUNT="$(awk -F= -v key="$PROPERTY_KEY" '$1 == key { count++ } END { print count + 0 }' "$PROPERTY_FILE")"
    if test "$PROPERTY_COUNT" -ne 1
    then
        printf 'property %s must occur exactly once; found %s\n' \
            "$PROPERTY_KEY" "$PROPERTY_COUNT" >&2
        return 1
    fi
    awk -F= -v key="$PROPERTY_KEY" '$1 == key { sub(/^[^=]*=/, ""); print }' "$PROPERTY_FILE"
}

if ! BUILD_ID="$(property_value "$TMP/savanna-build.properties" build.id)"
then
    fail 'invalid build.id in JAR build stamp'
fi

if ! mkdir -p "$TMP/source" "$TMP/jar"
then
    fail 'cannot create source/JAR verification directories'
fi
if ! unzip -q "$SOURCE_ZIP" -d "$TMP/source"
then
    fail 'source ZIP cannot be extracted'
fi
if ! test -f "$TMP/source/SOURCE_ARCHIVE_FILES.txt"
then
    fail 'source ZIP is missing SOURCE_ARCHIVE_FILES.txt'
fi
if ! test -f "$TMP/source/SOURCE_ARCHIVE_MANIFEST.sha256"
then
    fail 'source ZIP is missing SOURCE_ARCHIVE_MANIFEST.sha256'
fi
if ! cmp "$OUT/SOURCE_ARCHIVE_FILES.txt" "$TMP/source/SOURCE_ARCHIVE_FILES.txt"
then
    fail 'external and archived source file lists differ'
fi
if ! cmp "$OUT/SOURCE_ARCHIVE_MANIFEST.sha256" \
    "$TMP/source/SOURCE_ARCHIVE_MANIFEST.sha256"
then
    fail 'external and archived source archive manifests differ'
fi
unzip -Z1 "$SOURCE_ZIP" | sed '/\/$/d' | LC_ALL=C sort \
    > "$TMP/archive-actual-files.txt"
{
    cat "$OUT/SOURCE_ARCHIVE_FILES.txt"
    printf '%s\n' SOURCE_ARCHIVE_FILES.txt SOURCE_ARCHIVE_MANIFEST.sha256
} | LC_ALL=C sort -u > "$TMP/archive-expected-files.txt"
if ! diff -u "$TMP/archive-expected-files.txt" "$TMP/archive-actual-files.txt" \
    > "$EVIDENCE/SOURCE_ARCHIVE_CONTENT_DIFF.txt"
then
    fail 'source ZIP content differs from its declared file list'
fi
if ! (
    cd "$TMP/source"
    shasum -a 256 -c SOURCE_ARCHIVE_MANIFEST.sha256
) > "$EVIDENCE/SOURCE_ARCHIVE_MANIFEST_CHECK.txt"
then
    fail 'source archive per-file checksum verification failed'
fi
if ! "$ROOT/generate-source-manifest.sh" \
    "$TMP/source/src/main/java" "$TMP/source-manifest.sha256"
then
    fail 'cannot regenerate main source manifest from source ZIP'
fi
if ! diff -u "$OUT/MAIN_SOURCE_MANIFEST.sha256" "$TMP/source-manifest.sha256" \
    > "$EVIDENCE/MAIN_SOURCE_MANIFEST_DIFF.txt"
then
    fail 'main source manifest does not match source ZIP content'
fi
if ! cmp "$OUT/SOURCE_MANIFEST.sha256" "$OUT/MAIN_SOURCE_MANIFEST.sha256"
then
    fail 'legacy SOURCE_MANIFEST and MAIN_SOURCE_MANIFEST disagree'
fi
if ! "$ROOT/generate-source-manifest.sh" \
    "$TMP/source/src/test/java" "$TMP/test-source-manifest.sha256"
then
    fail 'cannot regenerate test source manifest from source ZIP'
fi
if ! diff -u "$OUT/TEST_SOURCE_MANIFEST.sha256" "$TMP/test-source-manifest.sha256" \
    > "$EVIDENCE/TEST_SOURCE_MANIFEST_DIFF.txt"
then
    fail 'test source manifest does not match source ZIP content'
fi
if ! SOURCE_HASH="$(shasum -a 256 "$OUT/MAIN_SOURCE_MANIFEST.sha256" | awk '{print $1}')"
then
    fail 'cannot hash main source manifest'
fi
if ! TEST_SOURCE_HASH="$(shasum -a 256 "$OUT/TEST_SOURCE_MANIFEST.sha256" | awk '{print $1}')"
then
    fail 'cannot hash test source manifest'
fi
if ! STAMP_HASH="$(property_value "$TMP/savanna-build.properties" source.hash)"
then
    fail 'invalid source.hash in JAR build stamp'
fi
if ! STAMP_TEST_HASH="$(property_value "$TMP/savanna-build.properties" test.source.hash)"
then
    fail 'invalid test.source.hash in JAR build stamp'
fi
if ! STAMP_MAIN_CLASS="$(property_value "$TMP/savanna-build.properties" main.class)"
then
    fail 'invalid main.class in JAR build stamp'
fi
MANIFEST_HASH="$(unzip -p "$JAR" META-INF/MANIFEST.MF | tr -d '\r' | awk '
    /^Savanna-Source-Hash: / {
        sub(/^Savanna-Source-Hash: /, ""); value=$0; collecting=1; next
    }
    collecting && /^ / { sub(/^ /, ""); value=value $0; next }
    collecting { print value; printed=1; exit }
    END { if(collecting && !printed) print value }
')"
if test "$SOURCE_HASH" != "$STAMP_HASH"
then
    fail "main source manifest/build stamp mismatch: manifest=$SOURCE_HASH stamp=$STAMP_HASH"
fi
if test "$TEST_SOURCE_HASH" != "$STAMP_TEST_HASH"
then
    fail "test source manifest/build stamp mismatch: manifest=$TEST_SOURCE_HASH stamp=$STAMP_TEST_HASH"
fi
if test "$SOURCE_HASH" != "$MANIFEST_HASH"
then
    fail "main source manifest/JAR manifest mismatch: source=$SOURCE_HASH jar_manifest=$MANIFEST_HASH"
fi
if test "$STAMP_MAIN_CLASS" != savanna.VisualSimulationRunner
then
    fail "unexpected build stamp main.class: $STAMP_MAIN_CLASS"
fi

cd "$TMP/jar"
jar xf "$JAR"
cd "$ROOT"
: > "$EVIDENCE/JAR_CORE_CLASSES.sha256"
for CLASS in VisualSimulationRunner Simulator SimulationContext TerrainMap RenderFrame SimulatorView
do
    shasum -a 256 "$TMP/jar/savanna/$CLASS.class" |
        awk -v name="$CLASS.class" '{print $1 "  " name}' \
        >> "$EVIDENCE/JAR_CORE_CLASSES.sha256"
done
diff -u "$OUT/CORE_CLASSES.sha256" "$EVIDENCE/JAR_CORE_CLASSES.sha256" \
    > "$EVIDENCE/CORE_CLASS_DIFF.txt"
"$ROOT/audit-candidate-orphans.sh" "$TMP/jar" "$TMP/source/src/main/java" \
    > "$EVIDENCE/JAR_ORPHAN_AUDIT.txt"

ROUNDTRIP_BUILD_ID="$BUILD_ID-source-roundtrip"
if ! (
    cd "$TMP/source"
    BUILD_ID="$ROUNDTRIP_BUILD_ID" sh ./test.sh
) > "$EVIDENCE/ALL_TESTS.txt" 2>&1
then
    fail 'self-contained source ZIP ./test.sh failed'
fi
ROUNDTRIP_JAR="$TMP/source/build/test-runtime/savanna-simulation-$ROUNDTRIP_BUILD_ID.jar"
if ! test -f "$ROUNDTRIP_JAR"
then
    fail 'source round-trip test did not produce its rebuilt JAR'
fi
if ! "$ROOT/verify-production-jar-boundary.sh" "$ROUNDTRIP_JAR" \
    > "$EVIDENCE/SOURCE_ROUNDTRIP_JAR_BOUNDARY.txt"
then
    fail 'source round-trip JAR violates the production boundary'
fi
if ! java -Djava.awt.headless=true -jar "$ROUNDTRIP_JAR" --verify-only \
    > "$EVIDENCE/SOURCE_ROUNDTRIP_CANONICAL_LAUNCHER.txt" 2>&1
then
    fail 'source round-trip JAR failed java -jar --verify-only launch'
fi
: > "$EVIDENCE/SOURCE_ROUNDTRIP_CORE_CLASSES.sha256"
for CLASS in VisualSimulationRunner Simulator SimulationContext TerrainMap RenderFrame SimulatorView
do
    shasum -a 256 "$TMP/source/build/classes/savanna/$CLASS.class" |
        awk -v name="$CLASS.class" '{print $1 "  " name}' \
        >> "$EVIDENCE/SOURCE_ROUNDTRIP_CORE_CLASSES.sha256"
done
diff -u "$OUT/CORE_CLASSES.sha256" \
    "$EVIDENCE/SOURCE_ROUNDTRIP_CORE_CLASSES.sha256" \
    > "$EVIDENCE/SOURCE_ROUNDTRIP_CORE_CLASS_DIFF.txt"
if ! mkdir -p "$TMP/roundtrip-jar"
then
    fail 'cannot create source round-trip JAR extraction directory'
fi
if ! (
    cd "$TMP/roundtrip-jar"
    jar xf "$ROUNDTRIP_JAR"
)
then
    fail 'cannot extract source round-trip JAR'
fi
if ! "$ROOT/verify-runtime-tree-equivalence.sh" \
    "$TMP/jar" "$TMP/roundtrip-jar" "$EVIDENCE/runtime-tree"
then
    fail 'candidate and source round-trip full runtime trees differ'
fi
TEST_CLASSES="$TMP/source/build/test-classes"
if ! test -d "$TEST_CLASSES"
then
    fail 'source round-trip test classes are missing'
fi
TEST_CLASSPATH="$ROUNDTRIP_JAR:$TEST_CLASSES"
if ! VERSIONED_TRANSPARENCY_PROFILE="$(
    sh "$TMP/source/resolve-current-transparency-profile.sh" "$TMP/source"
)"
then
    fail 'cannot resolve the current versioned transparency profile'
fi
VERSIONED_TRANSPARENCY_PROFILE_NAME="$(basename "$VERSIONED_TRANSPARENCY_PROFILE")"
if test -f "$VERSIONED_TRANSPARENCY_PROFILE"
then
    if ! (
        cd "$TMP/source"
        sh ./test-versioned-ecology-gate-policy.sh "$JAR" "$TEST_CLASSES"
    ) > "$EVIDENCE/VERSIONED_GATE_POLICY_FIXTURES.txt" 2>&1
    then
        fail 'versioned gate policy fixtures failed'
    fi
    if ! (
        cd "$TMP/source"
        sh ./run-versioned-ecology-gate.sh \
            "gate-profiles/$VERSIONED_TRANSPARENCY_PROFILE_NAME" \
            "$JAR" "$TEST_CLASSES" \
            "$EVIDENCE/TRANSPARENCY_GATE.txt"
    ) > "$EVIDENCE/TRANSPARENCY_GATE_CONSOLE.txt" 2>&1
    then
        fail 'versioned transparency gate rejected candidate'
    fi
fi

(
    cd "$TMP/source"
    sh ./build-dev-candidate.sh source-repack
)
REPACK="$TMP/source/artifacts/candidates/staging/source-repack.tmp"
set -- "$REPACK"/savanna-simulation-*-source.zip
REPACK_ZIP="$1"
mkdir -p "$TMP/repacked-source"
unzip -q "$REPACK_ZIP" -d "$TMP/repacked-source"
(
    cd "$TMP/repacked-source"
    shasum -a 256 -c SOURCE_ARCHIVE_MANIFEST.sha256
) > "$EVIDENCE/SOURCE_REPACK_MANIFEST_CHECK.txt"
unzip -Z1 "$REPACK_ZIP" | sed '/\/$/d' | LC_ALL=C sort \
    > "$TMP/repack-actual-files.txt"
{
    cat "$TMP/repacked-source/SOURCE_ARCHIVE_FILES.txt"
    printf '%s\n' SOURCE_ARCHIVE_FILES.txt SOURCE_ARCHIVE_MANIFEST.sha256
} | LC_ALL=C sort -u > "$TMP/repack-expected-files.txt"
diff -u "$TMP/repack-expected-files.txt" "$TMP/repack-actual-files.txt" \
    > "$EVIDENCE/SOURCE_REPACK_CONTENT_DIFF.txt"

TEST_SUMMARY="$(rg -o 'Passed [0-9]+/[0-9]+ tests' "$EVIDENCE/ALL_TESTS.txt" | tail -1)"
case "$TEST_SUMMARY" in
    'Passed '[0-9]*/[0-9]*' tests') ;;
    *) printf 'No passing AllTests summary found.\n' >&2; exit 1 ;;
esac
TEST_COUNTS="$(printf '%s\n' "$TEST_SUMMARY" | sed -n 's/^Passed \([0-9]*\/[0-9]*\) tests$/\1/p')"
case "$TEST_COUNTS" in
    */*) ;;
    *) printf 'Invalid AllTests summary: %s\n' "$TEST_SUMMARY" >&2; exit 1 ;;
esac
test "${TEST_COUNTS%%/*}" = "${TEST_COUNTS##*/}"

mkdir -p "$EVIDENCE/runtime-output"
java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" savanna.RuntimeIdentityProbe \
    "$EVIDENCE/runtime-output" > "$EVIDENCE/RUNTIME_IDENTITY.txt" 2>&1
grep -q 'RUNTIME_IDENTITY PASS' "$EVIDENCE/RUNTIME_IDENTITY.txt"

cp "$JAR" "$TMP/duplicate.jar"
if java -Djava.awt.headless=true \
    -cp "$ROUNDTRIP_JAR:$TMP/duplicate.jar:$TEST_CLASSES" \
    savanna.RuntimeIdentityProbe \
    > "$EVIDENCE/DUPLICATE_CLASSPATH_REJECTION.txt" 2>&1
then
    printf 'Duplicate candidate classpath was not rejected.\n' >&2
    exit 1
fi
grep -q 'classpath identity check failed' \
    "$EVIDENCE/DUPLICATE_CLASSPATH_REJECTION.txt"

OLD_JAR="$ROOT/artifacts/candidates/accepted/m1b-named-package-20260723/savanna-simulation-v1.8-m1b-named-package-20260723-dev.jar"
if test -f "$OLD_JAR"
then
    if java -Djava.awt.headless=true \
        -cp "$OLD_JAR:$ROUNDTRIP_JAR:$TEST_CLASSES" \
        savanna.RuntimeIdentityProbe \
        > "$EVIDENCE/OLD_FIRST_CLASSPATH_REJECTION.txt" 2>&1
    then
        printf 'Old-first mixed classpath was not rejected.\n' >&2
        exit 1
    fi
    grep -Eq 'classpath identity check failed|NoSuchMethodError|NoClassDefFoundError' \
        "$EVIDENCE/OLD_FIRST_CLASSPATH_REJECTION.txt"
fi

if ! java -Djava.awt.headless=true -cp "$ROUNDTRIP_JAR" savanna.SimulationRunner 1000 \
    > "$EVIDENCE/FIXED_SEED_1000.txt" 2>&1
then
    fail 'fixed-seed execution failed'
fi
if test -f "$VERSIONED_TRANSPARENCY_PROFILE"
then
    printf 'Legacy EXPECTED_* assertions replaced by immutable versioned transparency profile.\n' \
        >> "$EVIDENCE/FIXED_SEED_1000.txt"
elif test "$VERIFY_FIXED_SEED_ECOLOGY" = true
then
    if ! grep -Fq "{$EXPECTED_FIXED_SEED_POPULATION}" \
        "$EVIDENCE/FIXED_SEED_1000.txt"
    then
        fail 'fixed-seed population differs from the acceptance baseline'
    fi
    if ! grep -Fq "Final balance ratio: $EXPECTED_FIXED_SEED_BALANCE_RATIO" \
        "$EVIDENCE/FIXED_SEED_1000.txt"
    then
        fail 'fixed-seed balance differs from the acceptance baseline'
    fi
else
    printf 'Fixed-seed ecology assertion intentionally disabled; build identity and execution still verified.\n' \
        >> "$EVIDENCE/FIXED_SEED_1000.txt"
fi

ROOT_CLASSES="$(find "$ROOT" -maxdepth 1 -type f -name '*.class' | wc -l | tr -d ' ')"
if test "$ROOT_CLASSES" -ne 0
then
    fail "root-level class audit found $ROOT_CLASSES class files"
fi
printf 'root_level_class_files=%s\n' "$ROOT_CLASSES" \
    > "$EVIDENCE/ROOT_CLASS_AUDIT.txt"

if ! "$ROOT/verify-artifact-checksums.sh" "$OUT" \
    > "$EVIDENCE/ARTIFACT_CHECKSUMS_AFTER_VERIFICATION.txt"
then
    fail 'candidate JAR or source ZIP changed during verification'
fi

if test -f "$CURRENT_POINTER"
then
    POINTER_SHA_AFTER="$(shasum -a 256 "$CURRENT_POINTER" | awk '{print $1}')"
else
    POINTER_SHA_AFTER='MISSING'
fi
if test "$POINTER_SHA_BEFORE" != "$POINTER_SHA_AFTER"
then
    fail 'staging verification changed CURRENT_DEV'
fi
{
    printf 'Runtime identity staging verification PASS\n'
    printf 'verify_mode=staging\n'
    printf 'build_id=%s\n' "$BUILD_ID"
    printf 'source_hash=%s\n' "$SOURCE_HASH"
    printf 'test_source_hash=%s\n' "$TEST_SOURCE_HASH"
    printf 'jar_sha256=%s\n' "$ACTUAL_JAR_SHA"
    printf 'tests=%s\n' "$TEST_COUNTS"
    if test -f "$VERSIONED_TRANSPARENCY_PROFILE"
    then
        printf 'ecology_gate=versioned_transparency\n'
        printf 'gate_profile_sha256=%s\n' "$(awk '{print $1}' \
            "$VERSIONED_TRANSPARENCY_PROFILE.sha256")"
        printf 'gate_profile_name=%s\n' \
            "$VERSIONED_TRANSPARENCY_PROFILE_NAME"
        printf 'transparency_seed_order=1111,2111\n'
        printf 'legacy_expected_overrides=forbidden\n'
    else
        printf 'fixed_seed_ecology_verified=%s\n' "$VERIFY_FIXED_SEED_ECOLOGY"
    fi
    printf 'current_dev_sha256_before=%s\n' "$POINTER_SHA_BEFORE"
    printf 'current_dev_sha256_after=%s\n' "$POINTER_SHA_AFTER"
    printf 'current_dev_updated=false\n'
    printf 'root_level_class_files=0\n'
} > "$EVIDENCE/RUNTIME_IDENTITY_VERIFIED.txt"
printf 'Runtime identity staging verification PASS: %s\n' "$OUT"
