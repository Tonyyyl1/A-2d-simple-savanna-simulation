#!/usr/bin/env sh
set -eu

DIR="${1:?usage: verify-candidate-seal.sh <candidate-dir>}"
case "$DIR" in /*) ;; *) DIR="$(cd "$DIR" && pwd)" ;; esac
test -d "$DIR" || { printf 'Candidate seal FAILED: directory not found: %s\n' "$DIR" >&2; exit 1; }
test -f "$DIR/VERIFIED" || { printf 'Candidate seal FAILED: VERIFIED marker is missing\n' >&2; exit 1; }
test -f "$DIR/MANIFEST.json" || { printf 'Candidate seal FAILED: MANIFEST.json is missing\n' >&2; exit 1; }
test -f "$DIR/SEALED_CONTENT.sha256" || { printf 'Candidate seal FAILED: SEALED_CONTENT.sha256 is missing\n' >&2; exit 1; }
python3 -m json.tool "$DIR/MANIFEST.json" >/dev/null
grep -q '"state": "verified"' "$DIR/MANIFEST.json" || {
    printf 'Candidate seal FAILED: manifest state is not verified\n' >&2
    exit 1
}
MANIFEST_SCHEMA="$(python3 -c \
    'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["schemaVersion"])' \
    "$DIR/MANIFEST.json")"
case "$MANIFEST_SCHEMA" in
    candidate-manifest-v1) ;;
    candidate-manifest-v2)
        python3 -c '
import json, re, sys
data = json.load(open(sys.argv[1], encoding="utf-8"))
required = {
    "candidateClass", "gateProfile", "gateProfileSha256", "gateProfileId", "gateProfileKind"
}
missing = sorted(required - data.keys())
if missing:
    raise SystemExit("missing V24 gate manifest fields: " + ",".join(missing))
if not re.fullmatch(r"v24-[A-Za-z0-9._-]+\.properties", data["gateProfile"]):
    raise SystemExit("invalid V24 gate profile filename")
if not re.fullmatch(r"[0-9a-f]{64}", data["gateProfileSha256"]):
    raise SystemExit("invalid V24 gate profile checksum")
if not re.fullmatch(r"v24-[A-Za-z0-9._-]+", data["gateProfileId"]):
    raise SystemExit("invalid V24 gate profile ID")
if data["gateProfileKind"] not in {
    "CORE_REBASE_TRANSPARENCY", "FRAME_TRANSPARENCY", "BR1P2_SEMANTIC"
}:
    raise SystemExit("unknown V24 gate profile kind")
if data["candidateClass"] not in {
    "TRANSITIONAL_CANDIDATE", "INFRASTRUCTURE_CANDIDATE",
    "ECOLOGY_CANDIDATE", "RELEASE_CANDIDATE"
}:
    raise SystemExit("unknown candidate class")
' "$DIR/MANIFEST.json" || {
            printf 'Candidate seal FAILED: V24 gate manifest is invalid\n' >&2
            exit 1
        }
        ;;
    *)
        printf 'Candidate seal FAILED: unknown manifest schema %s\n' \
            "$MANIFEST_SCHEMA" >&2
        exit 1 ;;
esac

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
(
    cd "$DIR"
    find . -type f ! -name 'SEALED_CONTENT.sha256' -print | LC_ALL=C sort
) > "$TMP/actual-files.txt"
awk '
    NF != 2 || length($1) != 64 || $1 ~ /[^0-9a-f]/ ||
    $2 !~ /^\.\// || $2 ~ /(^|\/)\.\.($|\/)/ { exit 1 }
    { print $2 }
' "$DIR/SEALED_CONTENT.sha256" > "$TMP/declared-files.txt" || {
    printf 'Candidate seal FAILED: malformed sealed checksum manifest\n' >&2
    exit 1
}
LC_ALL=C sort "$TMP/declared-files.txt" > "$TMP/declared-sorted.txt"
if ! cmp "$TMP/actual-files.txt" "$TMP/declared-sorted.txt"
then
    printf 'Candidate seal FAILED: sealed file set differs from directory\n' >&2
    exit 1
fi
if ! (cd "$DIR" && shasum -a 256 -c SEALED_CONTENT.sha256 >/dev/null)
then
    printf 'Candidate seal FAILED: sealed content checksum mismatch\n' >&2
    exit 1
fi
printf 'Candidate seal PASS: files=%s\n' "$(wc -l < "$TMP/actual-files.txt" | tr -d ' ')"
