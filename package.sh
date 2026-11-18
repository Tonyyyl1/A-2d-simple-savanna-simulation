#!/usr/bin/env sh
set -eu

TAG="${1:?usage: package.sh <version-tag>}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC="$ROOT"
OUT="$ROOT/releases/$TAG"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT HUP INT TERM
JAR="$OUT/savanna-simulation-$TAG.jar"
ZIP="$OUT/savanna-simulation-$TAG-source.zip"
JAR_MANIFEST="$WORK/MANIFEST.MF"
RELEASE_MANIFEST="$OUT/MANIFEST.txt"
SOURCE_FILES="$WORK/SOURCE_ARCHIVE_FILES.txt"
SOURCE_MANIFEST="$WORK/SOURCE_ARCHIVE_MANIFEST.sha256"

test ! -e "$OUT" || {
    printf 'Release directory already exists: %s\n' "$OUT" >&2
    exit 1
}
mkdir -p "$OUT"
cd "$SRC"
BUILD_ID="$TAG" ./build.sh
SOURCE_HASH="$(shasum -a 256 build/metadata/MAIN_SOURCE_MANIFEST.sha256 | awk '{print $1}')"
{
    printf 'Manifest-Version: 1.0\n'
    printf 'Main-Class: savanna.VisualSimulationRunner\n'
    printf 'Savanna-Source-Hash: %s\n' "$SOURCE_HASH"
    printf '\n'
} > "$JAR_MANIFEST"
jar cfm "$JAR" "$JAR_MANIFEST" -C build/classes .
./verify-production-jar-boundary.sh "$JAR"

{
    find src -type f ! -name '.DS_Store' ! -path '*/build/*' -print
    find scripts docs legacy -type f ! -name '.DS_Store' -print 2>/dev/null
    find . -maxdepth 1 -type f \( \
        -name '*.sh' -o -name '*.md' -o -name '*.TXT' -o \
        -name '*.txt' -o -name '*.json' -o -name '*.csv' -o \
        -name '*.iml' -o -name '*.bluej' -o -name '*.ctxt' \
    \) ! -name 'SOURCE_ARCHIVE_FILES.txt' \
       ! -name 'SOURCE_ARCHIVE_MANIFEST.sha256' -print
} | sed 's#^\./##' | LC_ALL=C sort -u > "$SOURCE_FILES"
: > "$SOURCE_MANIFEST"
while IFS= read -r SOURCE_FILE
do
    shasum -a 256 "$SOURCE_FILE" >> "$SOURCE_MANIFEST"
done < "$SOURCE_FILES"
zip -q "$ZIP" -@ < "$SOURCE_FILES"
zip -q -j "$ZIP" "$SOURCE_FILES" "$SOURCE_MANIFEST"

{
    printf 'savanna-simulation %s\n' "$TAG"
    printf '====================\n\n'
    printf 'Build date: %s\n' "$(date '+%F %T')"
    printf 'Jar: savanna-simulation-%s.jar\n' "$TAG"
    printf 'Source snapshot: savanna-simulation-%s-source.zip\n' "$TAG"
    printf 'Main-Class: savanna.VisualSimulationRunner\n'
    printf 'Verification: pending (run ./verify-jar.sh %s)\n' "$TAG"
} > "$RELEASE_MANIFEST"

printf '%s\n' "$JAR"
printf '%s\n' "$ZIP"
printf '%s\n' "$RELEASE_MANIFEST"
