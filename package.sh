#!/usr/bin/env sh
set -eu

TAG="${1:?usage: package.sh <version-tag>}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$ROOT/work/savanna-simulation"
OUT="$ROOT/releases/$TAG"
WORK="$(mktemp -d)"
JAR="$OUT/savanna-simulation-$TAG.jar"
ZIP="$OUT/savanna-simulation-$TAG-source.zip"
JAR_MANIFEST="$WORK/MANIFEST.MF"
RELEASE_MANIFEST="$OUT/MANIFEST.txt"

mkdir -p "$OUT"

cp "$SRC"/*.java "$WORK"/
cd "$WORK"
javac --release 17 -Xlint:unchecked -Xlint:deprecation *.java
printf 'Main-Class: VisualSimulationRunner\n' > "$JAR_MANIFEST"
jar cfm "$JAR" "$JAR_MANIFEST" *.class

cd "$SRC"
find . -maxdepth 1 -type f \( \
    -name '*.java' -o -name '*.md' -o -iname '*.txt' -o \
    -name '*.bluej' -o -name '*.ctxt' -o -name '*.sh' \
\) -print | sort | zip -q "$ZIP" -@

{
    printf 'savanna-simulation %s\n' "$TAG"
    printf '====================\n\n'
    printf 'Build date: %s\n' "$(date '+%F %T')"
    printf 'Jar: savanna-simulation-%s.jar\n' "$TAG"
    printf 'Source snapshot: savanna-simulation-%s-source.zip\n' "$TAG"
    printf 'Main-Class: VisualSimulationRunner\n'
    printf 'Verification: pending (run ./verify-jar.sh %s)\n' "$TAG"
} > "$RELEASE_MANIFEST"

rm -rf "$WORK"
printf '%s\n' "$JAR"
printf '%s\n' "$ZIP"
printf '%s\n' "$RELEASE_MANIFEST"
