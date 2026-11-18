#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
DIR="${1:?usage: seal-candidate.sh <staging-candidate-dir>}"
case "$DIR" in /*) ;; *) DIR="$ROOT/$DIR" ;; esac
case "$DIR" in
    "$ROOT"/artifacts/candidates/staging/*.tmp) ;;
    *) printf 'Refusing to seal outside candidate staging: %s\n' "$DIR" >&2; exit 1 ;;
esac
test -d "$DIR" || { printf 'Staging candidate not found: %s\n' "$DIR" >&2; exit 1; }
test ! -e "$DIR/VERIFIED" || { printf 'VERIFIED already exists: %s\n' "$DIR" >&2; exit 1; }
test ! -e "$DIR/MANIFEST.json" || { printf 'MANIFEST.json already exists: %s\n' "$DIR" >&2; exit 1; }
test ! -e "$DIR/SEALED_CONTENT.sha256" || { printf 'Candidate is already sealed: %s\n' "$DIR" >&2; exit 1; }
"$ROOT/verify-artifact-checksums.sh" "$DIR" >/dev/null
test -f "$DIR/evidence/RUNTIME_IDENTITY_VERIFIED.txt" || {
    printf 'Runtime identity verified evidence is missing\n' >&2
    exit 1
}

set -- "$DIR"/savanna-simulation-*.jar
JAR="$1"
set -- "$DIR"/savanna-simulation-*-source.zip
ZIP="$1"
BUILD_ID="$(unzip -p "$JAR" META-INF/savanna-build.properties | awk -F= '$1 == "build.id" {print $2}')"
SOURCE_HASH="$(unzip -p "$JAR" META-INF/savanna-build.properties | awk -F= '$1 == "source.hash" {print $2}')"
TEST_SOURCE_HASH="$(unzip -p "$JAR" META-INF/savanna-build.properties | awk -F= '$1 == "test.source.hash" {print $2}')"
RUNTIME_TREE_HASH="$(unzip -p "$JAR" META-INF/savanna-build.properties | awk -F= '$1 == "runtime.tree.hash" {print $2}')"
JAR_SHA="$(shasum -a 256 "$JAR" | awk '{print $1}')"
ZIP_SHA="$(shasum -a 256 "$ZIP" | awk '{print $1}')"
CANDIDATE_BASENAME="$(basename "$DIR")"
CANDIDATE_ID="${CANDIDATE_BASENAME%.tmp}"
CANDIDATE_CLASS="${CANDIDATE_CLASS:-}"
case "$CANDIDATE_CLASS" in
    TRANSITIONAL_CANDIDATE|INFRASTRUCTURE_CANDIDATE|ECOLOGY_CANDIDATE|RELEASE_CANDIDATE) ;;
    *) printf 'CANDIDATE_CLASS must be a declared non-baseline candidate class\n' >&2; exit 1 ;;
esac
TRANSPARENCY_POINTER_LINES="$(unzip -p "$ZIP" \
    gate-profiles/TRANSPARENCY_CURRENT 2>/dev/null | wc -l | tr -d ' ')"
test "$TRANSPARENCY_POINTER_LINES" = 1 || {
    printf 'Archived transparency pointer must contain exactly one line\n' >&2
    exit 1
}
TRANSPARENCY_NAME="$(unzip -p "$ZIP" \
    gate-profiles/TRANSPARENCY_CURRENT 2>/dev/null | sed -n '1p')"
case "$TRANSPARENCY_NAME" in
    transparency-*.properties) ;;
    *) printf 'Archived transparency pointer is invalid\n' >&2; exit 1 ;;
esac
case "$TRANSPARENCY_NAME" in
    *[!A-Za-z0-9._-]*|.*|*/*)
        printf 'Archived transparency pointer is unsafe\n' >&2
        exit 1 ;;
esac
unzip -Z1 "$ZIP" | grep -Fxq \
    "gate-profiles/$TRANSPARENCY_NAME" || {
    printf 'Archived transparency profile is missing\n' >&2
    exit 1
}
unzip -Z1 "$ZIP" | grep -Fxq \
    "gate-profiles/$TRANSPARENCY_NAME.sha256" || {
    printf 'Archived transparency checksum sidecar is missing\n' >&2
    exit 1
}
TRANSPARENCY_SIDECAR="$(unzip -p "$ZIP" \
    "gate-profiles/$TRANSPARENCY_NAME.sha256" 2>/dev/null)"
test "$(printf '%s\n' "$TRANSPARENCY_SIDECAR" | wc -l | tr -d ' ')" = 1 || {
    printf 'Archived transparency checksum sidecar must contain one line\n' >&2
    exit 1
}
TRANSPARENCY_SHA="$(printf '%s\n' "$TRANSPARENCY_SIDECAR" | awk '{print $1}')"
TRANSPARENCY_SIDECAR_NAME="$(printf '%s\n' "$TRANSPARENCY_SIDECAR" | awk '{print $2}')"
test "${#TRANSPARENCY_SHA}" = 64 || {
    printf 'Archived transparency checksum must have 64 hex characters\n' >&2
    exit 1
}
case "$TRANSPARENCY_SHA" in
    *[!0-9a-f]*)
        printf 'Archived transparency checksum is not lowercase hex\n' >&2
        exit 1 ;;
esac
test "$TRANSPARENCY_SIDECAR_NAME" = "$TRANSPARENCY_NAME" || {
    printf 'Archived transparency checksum sidecar names the wrong profile\n' >&2
    exit 1
}
TRANSPARENCY_ACTUAL_SHA="$(unzip -p "$ZIP" \
    "gate-profiles/$TRANSPARENCY_NAME" 2>/dev/null | shasum -a 256 | awk '{print $1}')"
test "$TRANSPARENCY_SHA" = "$TRANSPARENCY_ACTUAL_SHA" || {
    printf 'Archived transparency profile checksum mismatch\n' >&2
    exit 1
}
GATE_POINTER_LINES="$(unzip -p "$ZIP" \
    gate-profiles/GATE_PROFILE_CURRENT 2>/dev/null | wc -l | tr -d ' ')"
test "$GATE_POINTER_LINES" = 1 || {
    printf 'Archived V24 gate pointer must contain exactly one line\n' >&2
    exit 1
}
GATE_PROFILE_NAME="$(unzip -p "$ZIP" \
    gate-profiles/GATE_PROFILE_CURRENT 2>/dev/null | sed -n '1p')"
case "$GATE_PROFILE_NAME" in
    v24-*.properties) ;;
    *) printf 'Archived V24 gate pointer is invalid\n' >&2; exit 1 ;;
esac
case "$GATE_PROFILE_NAME" in
    *[!A-Za-z0-9._-]*|.*|*/*)
        printf 'Archived V24 gate pointer is unsafe\n' >&2
        exit 1 ;;
esac
unzip -Z1 "$ZIP" | grep -Fxq "gate-profiles/$GATE_PROFILE_NAME" || {
    printf 'Archived V24 gate profile is missing\n' >&2
    exit 1
}
unzip -Z1 "$ZIP" | grep -Fxq \
    "gate-profiles/$GATE_PROFILE_NAME.sha256" || {
    printf 'Archived V24 gate checksum sidecar is missing\n' >&2
    exit 1
}
GATE_SIDECAR="$(unzip -p "$ZIP" \
    "gate-profiles/$GATE_PROFILE_NAME.sha256" 2>/dev/null)"
test "$(printf '%s\n' "$GATE_SIDECAR" | wc -l | tr -d ' ')" = 1 || {
    printf 'Archived V24 gate checksum sidecar must contain one line\n' >&2
    exit 1
}
GATE_PROFILE_SHA="$(printf '%s\n' "$GATE_SIDECAR" | awk '{print $1}')"
GATE_SIDECAR_NAME="$(printf '%s\n' "$GATE_SIDECAR" | awk '{print $2}')"
test "${#GATE_PROFILE_SHA}" = 64 || {
    printf 'Archived V24 gate checksum must have 64 hex characters\n' >&2
    exit 1
}
case "$GATE_PROFILE_SHA" in
    *[!0-9a-f]*)
        printf 'Archived V24 gate checksum is not lowercase hex\n' >&2
        exit 1 ;;
esac
test "$GATE_SIDECAR_NAME" = "$GATE_PROFILE_NAME" || {
    printf 'Archived V24 gate checksum sidecar names the wrong profile\n' >&2
    exit 1
}
GATE_ACTUAL_SHA="$(unzip -p "$ZIP" \
    "gate-profiles/$GATE_PROFILE_NAME" 2>/dev/null | shasum -a 256 | awk '{print $1}')"
test "$GATE_PROFILE_SHA" = "$GATE_ACTUAL_SHA" || {
    printf 'Archived V24 gate profile checksum mismatch\n' >&2
    exit 1
}
GATE_PROFILE_ID="$(unzip -p "$ZIP" \
    "gate-profiles/$GATE_PROFILE_NAME" 2>/dev/null | \
    awk -F= '$1 == "profile.id" {print $2}')"
GATE_PROFILE_KIND="$(unzip -p "$ZIP" \
    "gate-profiles/$GATE_PROFILE_NAME" 2>/dev/null | \
    awk -F= '$1 == "profile.kind" {print $2}')"
case "$GATE_PROFILE_ID" in
    v24-*) ;;
    *) printf 'Archived V24 gate profile ID is invalid\n' >&2; exit 1 ;;
esac
case "$GATE_PROFILE_KIND" in
    CORE_REBASE_TRANSPARENCY|FRAME_TRANSPARENCY|BR1P2_SEMANTIC) ;;
    *) printf 'Archived V24 gate profile kind is unknown\n' >&2; exit 1 ;;
esac
SEMANTIC_SHA="$(unzip -p "$ZIP" gate-profiles/semantic-ecology-v1.properties.sha256 2>/dev/null | awk '{print $1}')"
VERIFIED_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
{
    printf '{\n'
    printf '  "schemaVersion": "candidate-manifest-v2",\n'
    printf '  "state": "verified",\n'
    printf '  "candidateId": "%s",\n' "$CANDIDATE_ID"
    printf '  "candidateClass": "%s",\n' "$CANDIDATE_CLASS"
    printf '  "buildId": "%s",\n' "$BUILD_ID"
    printf '  "verifiedAtUtc": "%s",\n' "$VERIFIED_AT"
    printf '  "jarSha256": "%s",\n' "$JAR_SHA"
    printf '  "sourceZipSha256": "%s",\n' "$ZIP_SHA"
    printf '  "mainSourceManifestSha256": "%s",\n' "$SOURCE_HASH"
    printf '  "testSourceManifestSha256": "%s",\n' "$TEST_SOURCE_HASH"
    printf '  "runtimeTreeManifestSha256": "%s",\n' "$RUNTIME_TREE_HASH"
    printf '  "gateProfile": "%s",\n' "$GATE_PROFILE_NAME"
    printf '  "gateProfileSha256": "%s",\n' "$GATE_PROFILE_SHA"
    printf '  "gateProfileId": "%s",\n' "$GATE_PROFILE_ID"
    printf '  "gateProfileKind": "%s",\n' "$GATE_PROFILE_KIND"
    printf '  "transparencyProfile": "%s",\n' "$TRANSPARENCY_NAME"
    printf '  "transparencyProfileSha256": "%s",\n' "$TRANSPARENCY_SHA"
    printf '  "semanticProfileSha256": "%s"\n' "$SEMANTIC_SHA"
    printf '}\n'
} > "$DIR/MANIFEST.json"
{
    printf 'state=verified\n'
    printf 'candidate_id=%s\n' "$CANDIDATE_ID"
    printf 'verified_at_utc=%s\n' "$VERIFIED_AT"
} > "$DIR/VERIFIED"
(
    cd "$DIR"
    find . -type f ! -name 'SEALED_CONTENT.sha256' -print | LC_ALL=C sort |
        while IFS= read -r FILE
        do
            shasum -a 256 "$FILE"
        done > SEALED_CONTENT.sha256
)
"$ROOT/verify-candidate-seal.sh" "$DIR"
