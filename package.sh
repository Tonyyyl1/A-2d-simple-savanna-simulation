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
javac -Xlint:unchecked -Xlint:deprecation *.java
printf 'Main-Class: VisualSimulationRunner\n' > "$JAR_MANIFEST"
jar cfm "$JAR" "$JAR_MANIFEST" *.class

cd "$SRC"
zip -qr "$ZIP" . -i '*.java' '*.md' '*.txt' '*.bluej' '*.ctxt' '*.sh'

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
