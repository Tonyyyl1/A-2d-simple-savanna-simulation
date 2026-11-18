#!/usr/bin/env sh

candidate_require_context() {
    case "${CANDIDATE_ROOT:-}" in
        ""|/) printf 'Invalid CANDIDATE_ROOT\n' >&2; return 1 ;;
        /*) ;;
        *) printf 'CANDIDATE_ROOT must be absolute\n' >&2; return 1 ;;
    esac
    case "${CANDIDATE_TOOL_ROOT:-}" in
        ""|/) printf 'Invalid CANDIDATE_TOOL_ROOT\n' >&2; return 1 ;;
        /*) ;;
        *) printf 'CANDIDATE_TOOL_ROOT must be absolute\n' >&2; return 1 ;;
    esac
}

candidate_require_id() {
    case "$1" in
        ""|*[!A-Za-z0-9._-]*|.*|*..*)
            printf 'Invalid candidate ID: %s\n' "$1" >&2
            return 1
            ;;
    esac
}

candidate_stage_path() { printf '%s/staging/%s.tmp\n' "$CANDIDATE_ROOT" "$1"; }
candidate_accepted_path() { printf '%s/accepted/%s\n' "$CANDIDATE_ROOT" "$1"; }
candidate_failed_path() { printf '%s/failed/%s\n' "$CANDIDATE_ROOT" "$1"; }

candidate_require_new_id() {
    candidate_require_context && candidate_require_id "$1" || return 1
    test ! -e "$(candidate_stage_path "$1")" &&
    test ! -e "$(candidate_accepted_path "$1")" &&
    test ! -e "$(candidate_failed_path "$1")" || {
        printf 'Candidate ID has already been used: %s\n' "$1" >&2
        return 1
    }
}

candidate_archive_staging_failure() {
    ID="$1"
    PHASE="$2"
    REASON="$3"
    candidate_require_context && candidate_require_id "$ID" || return 1
    STAGE="$(candidate_stage_path "$ID")"
    FAILED="$(candidate_failed_path "$ID")"
    test -d "$STAGE" || { printf 'Staging candidate not found: %s\n' "$STAGE" >&2; return 1; }
    test ! -e "$FAILED" || { printf 'Failed archive already exists: %s\n' "$FAILED" >&2; return 1; }
    mkdir -p "$CANDIDATE_ROOT/failed"
    {
        printf 'candidate_id=%s\n' "$ID"
        printf 'state=failed\n'
        printf 'failed_phase=%s\n' "$PHASE"
        printf 'reason=%s\n' "$REASON"
        printf 'current_dev_updated=false\n'
    } > "$STAGE/FAILURE.txt"
    mv "$STAGE" "$FAILED"
    printf 'Candidate archived failed: %s\n' "$FAILED"
}

candidate_accept_verified() {
    ID="$1"
    candidate_require_context && candidate_require_id "$ID" || return 1
    STAGE="$(candidate_stage_path "$ID")"
    ACCEPTED="$(candidate_accepted_path "$ID")"
    test -d "$STAGE" || { printf 'Staging candidate not found: %s\n' "$STAGE" >&2; return 1; }
    test ! -e "$ACCEPTED" || { printf 'Accepted candidate already exists: %s\n' "$ACCEPTED" >&2; return 1; }
    "$CANDIDATE_TOOL_ROOT/verify-candidate-seal.sh" "$STAGE" >/dev/null || return 1
    mkdir -p "$CANDIDATE_ROOT/accepted"
    mv "$STAGE" "$ACCEPTED"
    printf '%s\n' "$ACCEPTED"
}

candidate_archive_accepted_failure() {
    ID="$1"
    PHASE="$2"
    REASON="$3"
    candidate_require_context && candidate_require_id "$ID" || return 1
    ACCEPTED="$(candidate_accepted_path "$ID")"
    FAILED="$(candidate_failed_path "$ID")"
    test -d "$ACCEPTED" || { printf 'Accepted candidate not found: %s\n' "$ACCEPTED" >&2; return 1; }
    test ! -e "$FAILED" || { printf 'Failed archive already exists: %s\n' "$FAILED" >&2; return 1; }
    mkdir -p "$CANDIDATE_ROOT/failed"
    mv "$ACCEPTED" "$FAILED"
    {
        printf 'candidate_id=%s\n' "$ID"
        printf 'state=failed\n'
        printf 'failed_phase=%s\n' "$PHASE"
        printf 'reason=%s\n' "$REASON"
        printf 'current_dev_updated=false\n'
    } > "$FAILED/FAILURE.txt"
    printf 'Accepted candidate rolled back to failed: %s\n' "$FAILED"
}

candidate_update_current_pointer() {
    # Q0 must first provide a complete lifecycle: frozen staged inputs,
    # per-gate evidence, qualification state, and a final seal.  Until then,
    # no caller (including a script that sources this library directly) may
    # write CURRENT_DEV.
    printf '%s\n' \
        'Candidate promotion blocked: Q0 qualification lifecycle is not implemented; CURRENT_DEV is protected.' >&2
    return 1
}
