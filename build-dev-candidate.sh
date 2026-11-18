#!/usr/bin/env sh
set -eu

BATCH_ID="${1:-r0-runtime-identity-20260721}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
CANDIDATE_ROOT="$ROOT/artifacts/candidates"
CANDIDATE_TOOL_ROOT="$ROOT"
. "$ROOT/candidate-state-machine-lib.sh"
candidate_require_new_id "$BATCH_ID"
OUT="$CANDIDATE_ROOT/staging/$BATCH_ID.tmp"
BUILD_ID="v1.8-$BATCH_ID-dev"
JAR="$OUT/savanna-simulation-$BUILD_ID.jar"
ZIP="$OUT/savanna-simulation-$BUILD_ID-source.zip"
MANIFEST="$OUT/JAR-MANIFEST.MF"

mkdir -p "$CANDIDATE_ROOT/staging" "$CANDIDATE_ROOT/failed"
mkdir -p "$OUT/evidence"
archive_failed_build() {
    STATUS=$?
    trap - EXIT
    if test "$STATUS" -ne 0 && test -d "$OUT"
    then
        candidate_archive_staging_failure "$BATCH_ID" build \
            'candidate build or initial artifact checksum failed' || true
    fi
    exit "$STATUS"
}
trap archive_failed_build EXIT
cd "$ROOT"
BUILD_ID="$BUILD_ID" ./build.sh
./audit-candidate-orphans.sh build/classes src/main/java > "$OUT/evidence/ORPHAN_AUDIT.txt"
cp build/metadata/SOURCE_MANIFEST.sha256 "$OUT/"
cp build/metadata/MAIN_SOURCE_MANIFEST.sha256 "$OUT/"
cp build/metadata/TEST_SOURCE_MANIFEST.sha256 "$OUT/"
cp build/metadata/CORE_CLASSES.sha256 "$OUT/"
SOURCE_HASH="$(shasum -a 256 build/metadata/SOURCE_MANIFEST.sha256 | awk '{print $1}')"
{
    printf 'Manifest-Version: 1.0\n'
    printf 'Main-Class: savanna.VisualSimulationRunner\n'
    printf 'Implementation-Title: Savanna Simulation\n'
    printf 'Implementation-Version: %s\n' "$BUILD_ID"
    printf 'Savanna-Source-Hash: %s\n' "$SOURCE_HASH"
    printf '\n'
} > "$MANIFEST"
jar cfm "$JAR" "$MANIFEST" -C build/classes .
./verify-production-jar-boundary.sh "$JAR" \
    > "$OUT/evidence/PRODUCTION_JAR_BOUNDARY.txt"
{
    find src -type f ! -name '.DS_Store' ! -path '*/build/*' -print
    find scripts docs legacy gate-profiles -type f ! -name '.DS_Store' -print 2>/dev/null
    find . -maxdepth 1 -type f \( \
        -name '*.sh' -o -name '*.md' -o -name '*.TXT' -o \
        -name '*.txt' -o -name '*.json' -o -name '*.csv' -o \
        -name '*.iml' -o -name '*.bluej' -o -name '*.ctxt' \
    \) ! -name 'SOURCE_ARCHIVE_FILES.txt' \
       ! -name 'SOURCE_ARCHIVE_MANIFEST.sha256' -print
} | sed 's#^\./##' | LC_ALL=C sort -u \
    > build/metadata/SOURCE_ARCHIVE_FILES.txt
: > build/metadata/SOURCE_ARCHIVE_MANIFEST.sha256
while IFS= read -r SOURCE_FILE
do
    shasum -a 256 "$SOURCE_FILE" \
        >> build/metadata/SOURCE_ARCHIVE_MANIFEST.sha256
done < build/metadata/SOURCE_ARCHIVE_FILES.txt
cp build/metadata/SOURCE_ARCHIVE_FILES.txt "$OUT/"
cp build/metadata/SOURCE_ARCHIVE_MANIFEST.sha256 "$OUT/"
zip -q "$ZIP" -@ < build/metadata/SOURCE_ARCHIVE_FILES.txt
zip -q -j "$ZIP" \
    build/metadata/SOURCE_ARCHIVE_FILES.txt \
    build/metadata/SOURCE_ARCHIVE_MANIFEST.sha256
if ! (
    cd "$OUT"
    shasum -a 256 "$(basename "$JAR")" "$(basename "$ZIP")" > SHA256SUMS
)
then
    printf 'Failed to write canonical candidate SHA256SUMS: %s\n' "$OUT" >&2
    exit 1
fi
if ! "$ROOT/verify-artifact-checksums.sh" "$OUT"
then
    printf 'Candidate checksum self-check failed: %s\n' "$OUT" >&2
    exit 1
fi
printf 'Created staging candidate %s\n' "$OUT"
