#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
DIR="${1:?usage: verify-accepted-candidate.sh <accepted-candidate-dir>}"
case "$DIR" in /*) ;; *) DIR="$ROOT/$DIR" ;; esac
case "$DIR" in
    "$ROOT"/artifacts/candidates/accepted/*) ;;
    *) printf 'Accepted audit target is outside accepted/: %s\n' "$DIR" >&2; exit 1 ;;
esac
snapshot() {
    (
        cd "$DIR"
        find . -type f -print | LC_ALL=C sort | while IFS= read -r FILE
        do
            shasum -a 256 "$FILE"
        done
    )
}
BEFORE="$(snapshot)"
"$ROOT/verify-candidate-seal.sh" "$DIR"
"$ROOT/verify-artifact-checksums.sh" "$DIR"
set -- "$DIR"/savanna-simulation-*.jar
java -Djava.awt.headless=true -jar "$1" --verify-only
AFTER="$(snapshot)"
test "$BEFORE" = "$AFTER" || {
    printf 'Accepted candidate audit FAILED: candidate bytes changed\n' >&2
    exit 1
}
printf 'Accepted candidate read-only audit PASS: %s\n' "$DIR"
