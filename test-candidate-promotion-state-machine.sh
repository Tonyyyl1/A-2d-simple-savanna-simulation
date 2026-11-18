#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
CANDIDATE_ROOT="$TMP/candidates"
CANDIDATE_TOOL_ROOT="$ROOT"
. "$ROOT/candidate-state-machine-lib.sh"
mkdir -p "$CANDIDATE_ROOT/staging" "$CANDIDATE_ROOT/accepted" \
    "$CANDIDATE_ROOT/failed"
printf 'jar=unchanged\nsha256=unchanged\nbuild_id=unchanged\n' \
    > "$CANDIDATE_ROOT/CURRENT_DEV"
POINTER_SHA="$(shasum -a 256 "$CANDIDATE_ROOT/CURRENT_DEV" | awk '{print $1}')"
PASS_COUNT=0

assert_pointer_unchanged() {
    ACTUAL="$(shasum -a 256 "$CANDIDATE_ROOT/CURRENT_DEV" | awk '{print $1}')"
    test "$ACTUAL" = "$POINTER_SHA"
}

for PHASE in build test checksum ecology archive
do
    ID="v5-fixture-$PHASE"
    candidate_require_new_id "$ID"
    mkdir -p "$(candidate_stage_path "$ID")/evidence"
    printf 'partial=true\n' > "$(candidate_stage_path "$ID")/PARTIAL.txt"
    candidate_archive_staging_failure "$ID" "$PHASE" \
        "injected $PHASE failure" >/dev/null
    test ! -e "$(candidate_stage_path "$ID")"
    test ! -e "$(candidate_accepted_path "$ID")"
    test -f "$(candidate_failed_path "$ID")/FAILURE.txt"
    grep -q "failed_phase=$PHASE" \
        "$(candidate_failed_path "$ID")/FAILURE.txt"
    assert_pointer_unchanged
    if candidate_require_new_id "$ID" >/dev/null 2>&1
    then
        printf 'Reused failed candidate ID was accepted: %s\n' "$ID" >&2
        exit 1
    fi
    PASS_COUNT=$((PASS_COUNT + 1))
    printf 'PASS injected %s failure archives without accepted/current state\n' "$PHASE"
done

ID=v5-fixture-pointer
candidate_require_new_id "$ID"
mkdir -p "$(candidate_accepted_path "$ID")/evidence"
printf 'sealed-before-pointer=true\n' > "$(candidate_accepted_path "$ID")/SEALED.txt"
candidate_archive_accepted_failure "$ID" pointer \
    'injected pointer failure' >/dev/null
test ! -e "$(candidate_accepted_path "$ID")"
test -f "$(candidate_failed_path "$ID")/FAILURE.txt"
grep -q 'failed_phase=pointer' "$(candidate_failed_path "$ID")/FAILURE.txt"
assert_pointer_unchanged
PASS_COUNT=$((PASS_COUNT + 1))
printf 'PASS injected pointer failure rolls accepted back without CURRENT_DEV change\n'

printf 'CANDIDATE_PROMOTION_FAULT_FIXTURES PASS: %s/6\n' "$PASS_COUNT"
