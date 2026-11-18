#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
COUNT="$(find . -maxdepth 1 -type f -name '*.class' | wc -l | tr -d ' ')"
if test "$COUNT" -eq 0
then
    printf 'No root-level legacy .class files found.\n'
    exit 0
fi
STAMP="$(date '+%Y%m%d-%H%M%S')"
ARCHIVE="build/archive/root-classes-$STAMP"
mkdir -p "$ARCHIVE"
find . -maxdepth 1 -type f -name '*.class' -print | LC_ALL=C sort |
while IFS= read -r FILE
do
    shasum -a 256 "$FILE" | awk -v name="$(basename "$FILE")" \
        '{print $1 "  " name}'
done > "$ARCHIVE/SHA256SUMS"
find . -maxdepth 1 -type f -name '*.class' -exec mv {} "$ARCHIVE"/ \;
printf 'Archived %s root-level legacy classes to %s\n' "$COUNT" "$ARCHIVE"
