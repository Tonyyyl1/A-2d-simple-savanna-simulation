#!/usr/bin/env sh
set -u

fail() {
    printf 'Runtime tree equivalence FAILED: %s\n' "$1" >&2
    exit 1
}

if test "$#" -lt 2 || test "$#" -gt 3
then
    fail 'usage: verify-runtime-tree-equivalence.sh <candidate-tree> <rebuilt-tree> [evidence-dir]'
fi

LEFT_INPUT="$1"
RIGHT_INPUT="$2"
EVIDENCE_INPUT="${3:-}"
if ! LEFT="$(cd "$LEFT_INPUT" 2>/dev/null && pwd)"
then
    fail "candidate runtime tree not found: $LEFT_INPUT"
fi
if ! RIGHT="$(cd "$RIGHT_INPUT" 2>/dev/null && pwd)"
then
    fail "rebuilt runtime tree not found: $RIGHT_INPUT"
fi
if test -n "$EVIDENCE_INPUT"
then
    if ! mkdir -p "$EVIDENCE_INPUT"
    then
        fail "cannot create evidence directory: $EVIDENCE_INPUT"
    fi
    if ! EVIDENCE="$(cd "$EVIDENCE_INPUT" && pwd)"
    then
        fail "cannot resolve evidence directory: $EVIDENCE_INPUT"
    fi
else
    EVIDENCE=''
fi
if ! TMP="$(mktemp -d)"
then
    fail 'cannot create runtime-tree temporary directory'
fi
trap 'rm -rf "$TMP"' EXIT HUP INT TERM

generate_manifest() {
    TREE="$1"
    OUTPUT="$2"
    if find "$TREE" -type l -print | grep -q .
    then
        printf 'runtime trees must not contain symbolic links: %s\n' "$TREE" >&2
        return 1
    fi
    if ! (
        cd "$TREE" || exit 1
        find . -type f \
            ! -path './META-INF/MANIFEST.MF' \
            ! -path './META-INF/savanna-build.properties' -print | \
            LC_ALL=C sort | while IFS= read -r file
        do
            if ! digest="$(shasum -a 256 "$file" | awk '{ print $1 }')"
            then
                exit 1
            fi
            printf '%s  %s\n' "$digest" "${file#./}"
        done
    ) > "$OUTPUT"
    then
        return 1
    fi
}

if ! generate_manifest "$LEFT" "$TMP/candidate-runtime-tree.sha256"
then
    fail 'cannot create candidate class/resource manifest'
fi
if ! generate_manifest "$RIGHT" "$TMP/rebuilt-runtime-tree.sha256"
then
    fail 'cannot create rebuilt class/resource manifest'
fi
if ! grep -Eq '  .*\.class$' "$TMP/candidate-runtime-tree.sha256"
then
    fail 'candidate runtime tree contains no class files'
fi

DIFF_PATH="$TMP/runtime-tree.diff"
if test -n "$EVIDENCE"
then
    cp "$TMP/candidate-runtime-tree.sha256" "$EVIDENCE/CANDIDATE_RUNTIME_TREE.sha256"
    cp "$TMP/rebuilt-runtime-tree.sha256" "$EVIDENCE/REBUILT_RUNTIME_TREE.sha256"
    DIFF_PATH="$EVIDENCE/RUNTIME_TREE_DIFF.txt"
fi
if ! diff -u "$TMP/candidate-runtime-tree.sha256" \
    "$TMP/rebuilt-runtime-tree.sha256" > "$DIFF_PATH"
then
    fail "class/resource tree differs; see $DIFF_PATH"
fi

property_value() {
    FILE="$1"
    KEY="$2"
    COUNT="$(awk -F= -v key="$KEY" '$1 == key { count++ } END { print count + 0 }' "$FILE")"
    if test "$COUNT" -ne 1
    then
        printf 'build-stamp property %s must occur exactly once; found %s\n' \
            "$KEY" "$COUNT" >&2
        return 1
    fi
    awk -F= -v key="$KEY" '$1 == key { sub(/^[^=]*=/, ""); print }' "$FILE"
}

manifest_value() {
    FILE="$1"
    KEY="$2"
    awk -v key="$KEY" '
        function emit() {
            if (collecting) {
                count++
                result = value
                collecting = 0
            }
        }
        {
            line = $0
            sub(/\r$/, "", line)
            if (line ~ /^ / && collecting) {
                value = value substr(line, 2)
                next
            }
            emit()
            prefix = key ": "
            if (index(line, prefix) == 1) {
                value = substr(line, length(prefix) + 1)
                collecting = 1
            }
        }
        END {
            emit()
            if (count != 1) {
                print "JAR manifest field " key " must occur exactly once; found " count > "/dev/stderr"
                exit 1
            }
            print result
        }
    ' "$FILE"
}

LEFT_STAMP="$LEFT/META-INF/savanna-build.properties"
RIGHT_STAMP="$RIGHT/META-INF/savanna-build.properties"
LEFT_MANIFEST="$LEFT/META-INF/MANIFEST.MF"
RIGHT_MANIFEST="$RIGHT/META-INF/MANIFEST.MF"
for required in "$LEFT_STAMP" "$RIGHT_STAMP" "$LEFT_MANIFEST" "$RIGHT_MANIFEST"
do
    if ! test -f "$required"
    then
        fail "runtime tree is missing identity resource: $required"
    fi
done

for key in build.id source.hash test.source.hash runtime.tree.hash main.class built.at.utc
do
    if ! left_value="$(property_value "$LEFT_STAMP" "$key")"
    then
        fail 'candidate build stamp is malformed'
    fi
    if ! right_value="$(property_value "$RIGHT_STAMP" "$key")"
    then
        fail 'rebuilt build stamp is malformed'
    fi
    case "$key" in
        build.id|built.at.utc)
            if test -z "$left_value" || test -z "$right_value"
            then
                fail "build-stamp property $key must not be empty"
            fi
            ;;
        *)
            if test "$left_value" != "$right_value"
            then
                fail "$key differs: candidate=$left_value rebuilt=$right_value"
            fi
            ;;
    esac
done

LEFT_SOURCE_HASH="$(property_value "$LEFT_STAMP" source.hash)"
RIGHT_SOURCE_HASH="$(property_value "$RIGHT_STAMP" source.hash)"
LEFT_MAIN_CLASS="$(property_value "$LEFT_STAMP" main.class)"
RIGHT_MAIN_CLASS="$(property_value "$RIGHT_STAMP" main.class)"
if ! LEFT_MANIFEST_SOURCE="$(manifest_value "$LEFT_MANIFEST" Savanna-Source-Hash)"
then
    fail 'candidate JAR manifest is malformed'
fi
if ! RIGHT_MANIFEST_SOURCE="$(manifest_value "$RIGHT_MANIFEST" Savanna-Source-Hash)"
then
    fail 'rebuilt JAR manifest is malformed'
fi
if ! LEFT_MANIFEST_MAIN="$(manifest_value "$LEFT_MANIFEST" Main-Class)"
then
    fail 'candidate JAR manifest is malformed'
fi
if ! RIGHT_MANIFEST_MAIN="$(manifest_value "$RIGHT_MANIFEST" Main-Class)"
then
    fail 'rebuilt JAR manifest is malformed'
fi
if test "$LEFT_MANIFEST_SOURCE" != "$RIGHT_MANIFEST_SOURCE"
then
    fail "JAR manifest source hash differs: candidate=$LEFT_MANIFEST_SOURCE rebuilt=$RIGHT_MANIFEST_SOURCE"
fi
if test "$LEFT_MANIFEST_SOURCE" != "$LEFT_SOURCE_HASH"
then
    fail 'candidate JAR manifest/source stamp hash differs'
fi
if test "$RIGHT_MANIFEST_SOURCE" != "$RIGHT_SOURCE_HASH"
then
    fail 'rebuilt JAR manifest/source stamp hash differs'
fi
if test "$LEFT_MANIFEST_MAIN" != "$RIGHT_MANIFEST_MAIN"
then
    fail "JAR manifest Main-Class differs: candidate=$LEFT_MANIFEST_MAIN rebuilt=$RIGHT_MANIFEST_MAIN"
fi
if test "$LEFT_MANIFEST_MAIN" != "$LEFT_MAIN_CLASS" || \
   test "$RIGHT_MANIFEST_MAIN" != "$RIGHT_MAIN_CLASS"
then
    fail 'JAR manifest Main-Class differs from build stamp main.class'
fi

printf 'Runtime tree equivalence PASS: files=%s\n' \
    "$(wc -l < "$TMP/candidate-runtime-tree.sha256" | tr -d ' ')"
