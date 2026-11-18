#!/usr/bin/env sh
set -eu

ROOT_DIR="${1:?usage: generate-runtime-tree-manifest.sh <runtime-root> <output>}"
OUTPUT="${2:?usage: generate-runtime-tree-manifest.sh <runtime-root> <output>}"
test -d "$ROOT_DIR" || {
    printf 'Runtime root not found: %s\n' "$ROOT_DIR" >&2
    exit 1
}
case "$OUTPUT" in
    /*) OUTPUT_ABS="$OUTPUT" ;;
    *) OUTPUT_ABS="$(pwd)/$OUTPUT" ;;
esac
OUTPUT_TEMP="$(mktemp "${TMPDIR:-/tmp}/savanna-runtime-tree.XXXXXX")"
trap 'rm -f "$OUTPUT_TEMP"' EXIT HUP INT TERM

if ! (
    cd "$ROOT_DIR"
    find . -type f \
        ! -path './META-INF/MANIFEST.MF' \
        ! -path './META-INF/savanna-build.properties' \
        ! -path './META-INF/savanna-runtime-tree.sha256' -print | \
        LC_ALL=C sort | while IFS= read -r file
    do
        digest="$(shasum -a 256 "$file" | awk '{ print $1 }')"
        printf '%s  %s\n' "$digest" "${file#./}"
    done
) > "$OUTPUT_TEMP"
then
    printf 'Could not generate runtime-tree manifest for %s\n' "$ROOT_DIR" >&2
    exit 1
fi
test -s "$OUTPUT_TEMP" || {
    printf 'Runtime-tree manifest would be empty: %s\n' "$ROOT_DIR" >&2
    exit 1
}
mkdir -p "$(dirname "$OUTPUT_ABS")"
mv "$OUTPUT_TEMP" "$OUTPUT_ABS"
trap - EXIT HUP INT TERM
