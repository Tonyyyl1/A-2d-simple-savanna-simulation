#!/usr/bin/env sh
set -u

fail() {
    printf 'Artifact checksum verification FAILED: %s\n' "$1" >&2
    exit 1
}

if test "$#" -ne 1
then
    fail 'usage: verify-artifact-checksums.sh <candidate-dir>'
fi

DIR="$1"
case "$DIR" in
    /*) ;;
    *)
        if ! DIR="$(cd "$DIR" 2>/dev/null && pwd)"
        then
            fail "candidate directory not found: $1"
        fi
        ;;
esac
if ! test -d "$DIR"
then
    fail "candidate directory not found: $DIR"
fi

set -- "$DIR"/savanna-simulation-*.jar
if test "$#" -ne 1
then
    fail 'candidate must contain exactly one candidate JAR'
fi
if ! test -f "$1"
then
    fail 'candidate must contain exactly one candidate JAR'
fi
JAR="$1"

set -- "$DIR"/savanna-simulation-*-source.zip
if test "$#" -ne 1
then
    fail 'candidate must contain exactly one source ZIP'
fi
if ! test -f "$1"
then
    fail 'candidate must contain exactly one source ZIP'
fi
SOURCE_ZIP="$1"

CHECKSUMS="$DIR/SHA256SUMS"
if ! test -f "$CHECKSUMS"
then
    fail 'SHA256SUMS is missing'
fi
if ! LINE_COUNT="$(awk 'END { print NR + 0 }' "$CHECKSUMS")"
then
    fail 'cannot count SHA256SUMS entries'
fi
if test "$LINE_COUNT" -ne 2
then
    fail "SHA256SUMS must contain exactly two entries; found $LINE_COUNT"
fi

JAR_NAME="$(basename "$JAR")"
ZIP_NAME="$(basename "$SOURCE_ZIP")"
if ! awk -v jar="$JAR_NAME" -v zip="$ZIP_NAME" '
    BEGIN { jar_count = 0; zip_count = 0; invalid = 0 }
    {
        if (NF != 2 || length($1) != 64 || $1 ~ /[^0-9a-f]/) {
            print "SHA256SUMS entries must use lowercase SHA-256 followed by one bare basename" > "/dev/stderr"
            invalid = 1
            exit 1
        }
        if ($2 ~ /\// || $2 == "." || $2 == "..") {
            print "SHA256SUMS entries must use bare basenames, not paths" > "/dev/stderr"
            invalid = 1
            exit 1
        }
        if ($2 == jar) {
            jar_count++
        } else if ($2 == zip) {
            zip_count++
        } else {
            print "SHA256SUMS contains an unexpected artifact name: " $2 > "/dev/stderr"
            invalid = 1
            exit 1
        }
    }
    END {
        if (invalid) {
            exit 1
        }
        if (jar_count != 1 || zip_count != 1) {
            print "SHA256SUMS must name the JAR and source ZIP exactly once" > "/dev/stderr"
            exit 1
        }
    }
' "$CHECKSUMS"
then
    fail 'SHA256SUMS structure is invalid'
fi

if ! EXPECTED_JAR_SHA="$(awk -v name="$JAR_NAME" '$2 == name { print $1 }' "$CHECKSUMS")"
then
    fail 'cannot read expected JAR checksum'
fi
if ! EXPECTED_ZIP_SHA="$(awk -v name="$ZIP_NAME" '$2 == name { print $1 }' "$CHECKSUMS")"
then
    fail 'cannot read expected source ZIP checksum'
fi
if ! ACTUAL_JAR_LINE="$(shasum -a 256 "$JAR")"
then
    fail 'cannot hash candidate JAR'
fi
ACTUAL_JAR_SHA="${ACTUAL_JAR_LINE%% *}"
if ! ACTUAL_ZIP_LINE="$(shasum -a 256 "$SOURCE_ZIP")"
then
    fail 'cannot hash source ZIP'
fi
ACTUAL_ZIP_SHA="${ACTUAL_ZIP_LINE%% *}"

if test "$EXPECTED_JAR_SHA" != "$ACTUAL_JAR_SHA"
then
    fail "JAR checksum mismatch: expected=$EXPECTED_JAR_SHA actual=$ACTUAL_JAR_SHA"
fi
if test "$EXPECTED_ZIP_SHA" != "$ACTUAL_ZIP_SHA"
then
    fail "source ZIP checksum mismatch: expected=$EXPECTED_ZIP_SHA actual=$ACTUAL_ZIP_SHA"
fi

printf 'Artifact checksum verification PASS: jar=%s source_zip=%s\n' \
    "$ACTUAL_JAR_SHA" "$ACTUAL_ZIP_SHA"
