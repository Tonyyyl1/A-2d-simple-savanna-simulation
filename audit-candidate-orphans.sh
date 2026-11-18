#!/usr/bin/env sh
set -eu

CLASSES="${1:?usage: audit-candidate-orphans.sh <classes-dir> <source-root>}"
SOURCE_ROOT="${2:?usage: audit-candidate-orphans.sh <classes-dir> <source-root>}"
ORPHANS=0
AUDITED=0
CLASS_LIST="$(mktemp)"
trap 'rm -f "$CLASS_LIST"' EXIT HUP INT TERM
find "$CLASSES" -type f -name '*.class' ! -name '*$*' -print |
    LC_ALL=C sort > "$CLASS_LIST"
while IFS= read -r FILE
do
    test -n "$FILE" || continue
    AUDITED=$((AUDITED + 1))
    NAME="$(basename "$FILE" .class)"
    RELATIVE="${FILE#"$CLASSES"/}"
    EXPECTED_SOURCE="$SOURCE_ROOT/${RELATIVE%.class}.java"
    if test ! -f "$EXPECTED_SOURCE"
    then
        MATCHES="$(find "$SOURCE_ROOT" -type f -name "$NAME.java" -print)"
        case "$MATCHES" in
            *'
'*) EXPECTED_SOURCE="" ;;
            *) EXPECTED_SOURCE="$MATCHES" ;;
        esac
    fi
    if test ! -f "$EXPECTED_SOURCE" ||
       ! rg -q "^(public[[:space:]]+)?((abstract|final)[[:space:]]+)?(class|interface|enum|record)[[:space:]]+$NAME([[:space:]<({]|$)" \
           "$EXPECTED_SOURCE"
    then
        printf 'ORPHAN top-level class: %s (expected %s)\n' \
            "$RELATIVE" "$EXPECTED_SOURCE" >&2
        ORPHANS=$((ORPHANS + 1))
    fi
done < "$CLASS_LIST"
printf 'Orphan audit: audited=%s orphaned=%s\n' "$AUDITED" "$ORPHANS"
test "$AUDITED" -gt 0
test "$ORPHANS" -eq 0
