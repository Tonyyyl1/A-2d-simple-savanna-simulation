#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
ID="${1:?usage: promote-dev-candidate.sh <candidate-id>}"

# P0-lite deliberately does not repair the entire candidate lifecycle.  A
# partial promotion can make an unqualified artifact look current, so Q0 is
# the only route that may re-enable this script after its complete gate chain
# has been implemented and verified.
printf '%s\n' \
    "Candidate promotion blocked: Q0 qualification lifecycle is not implemented; CURRENT_DEV is protected."
exit 1

CANDIDATE_ROOT="$ROOT/artifacts/candidates"
CANDIDATE_TOOL_ROOT="$ROOT"
. "$ROOT/candidate-state-machine-lib.sh"
candidate_require_context
candidate_require_id "$ID"
STAGE="$(candidate_stage_path "$ID")"
ACCEPTED="$(candidate_accepted_path "$ID")"
test -d "$STAGE" || { printf 'Staging candidate not found: %s\n' "$STAGE" >&2; exit 1; }
test ! -e "$ACCEPTED" || { printf 'Accepted candidate already exists: %s\n' "$ACCEPTED" >&2; exit 1; }

PHASE=verification
MOVED_TO_ACCEPTED=false
PROMOTED=false
on_exit() {
    STATUS=$?
    trap - EXIT
    if test "$STATUS" -ne 0 && test "$PROMOTED" = false
    then
        if test -d "$STAGE"
        then
            candidate_archive_staging_failure "$ID" "$PHASE" \
                'candidate promotion gate failed' || true
        elif test "$MOVED_TO_ACCEPTED" = true && test -d "$ACCEPTED"
        then
            candidate_archive_accepted_failure "$ID" "$PHASE" \
                'candidate promotion failed after accepted move and before CURRENT_DEV update' || true
        fi
    fi
    exit "$STATUS"
}
trap on_exit EXIT

POINTER="$CANDIDATE_ROOT/CURRENT_DEV"
if test -f "$POINTER"
then
    POINTER_SHA_BEFORE="$(shasum -a 256 "$POINTER" | awk '{print $1}')"
else
    POINTER_SHA_BEFORE=MISSING
fi

"$ROOT/verify-runtime-identity.sh" "$STAGE"
PHASE=seal
"$ROOT/seal-candidate.sh" "$STAGE"
"$ROOT/verify-candidate-seal.sh" "$STAGE" >/dev/null
if test -f "$POINTER"
then
    POINTER_SHA_AFTER_VERIFY="$(shasum -a 256 "$POINTER" | awk '{print $1}')"
else
    POINTER_SHA_AFTER_VERIFY=MISSING
fi
test "$POINTER_SHA_BEFORE" = "$POINTER_SHA_AFTER_VERIFY" || {
    printf 'CURRENT_DEV changed before accepted promotion\n' >&2
    exit 1
}

PHASE=accepted_move
candidate_accept_verified "$ID" >/dev/null
MOVED_TO_ACCEPTED=true
set -- "$ACCEPTED"/savanna-simulation-*.jar
"$ROOT/run-dev.sh" "$1" --verify-only >/dev/null

PHASE=pointer_update
candidate_update_current_pointer "$ID"
PROMOTED=true
printf 'Candidate promotion PASS: accepted=%s current=true\n' "$ACCEPTED"
