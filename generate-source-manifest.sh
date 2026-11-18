#!/usr/bin/env sh
set -eu

ROOT="${1:-.}"
OUTPUT="${2:-build/metadata/SOURCE_MANIFEST.sha256}"
CALLER_ROOT="$(pwd)"
case "$OUTPUT" in
    /*) ;;
    *) OUTPUT="$CALLER_ROOT/$OUTPUT" ;;
esac
ROOT="$(cd "$ROOT" && pwd)"
mkdir -p "$(dirname "$OUTPUT")"
TEMP="$(mktemp)"
NORMALIZED="$(mktemp)"
trap 'rm -f "$TEMP" "$NORMALIZED"' EXIT HUP INT TERM

find "$ROOT" -type f -name '*.java' -print | LC_ALL=C sort |
while IFS= read -r FILE
do
    sed 's/$//' "$FILE" > "$NORMALIZED"
    HASH="$(shasum -a 256 "$NORMALIZED" | awk '{print $1}')"
    printf '%s  %s\n' "$HASH" "${FILE#"$ROOT"/}"
done > "$TEMP"
mv "$TEMP" "$OUTPUT"
