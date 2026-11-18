#!/usr/bin/env sh
set -eu

ID="${1:?usage: audit-accepted-candidate.sh <candidate-id>}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
case "$ID" in ""|*[!A-Za-z0-9._-]*|.*|*..*) printf 'Invalid candidate ID: %s\n' "$ID" >&2; exit 1 ;; esac
CANDIDATE="$ROOT/artifacts/candidates/accepted/$ID"
test -d "$CANDIDATE" || { printf 'Accepted candidate not found: %s\n' "$CANDIDATE" >&2; exit 1; }
AUDIT_ROOT="$ROOT/artifacts/candidates/audits/$ID"
AUDIT_ID="$(date -u '+%Y%m%dT%H%M%SZ')-$$"
TMP="$AUDIT_ROOT/$AUDIT_ID.tmp"
FINAL="$AUDIT_ROOT/$AUDIT_ID"
mkdir -p "$TMP"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
"$ROOT/verify-accepted-candidate.sh" "$CANDIDATE" > "$TMP/READ_ONLY_AUDIT.txt" 2>&1
shasum -a 256 "$TMP/READ_ONLY_AUDIT.txt" > "$TMP/SHA256SUMS"
mv "$TMP" "$FINAL"
trap - EXIT HUP INT TERM
printf 'Accepted candidate audit PASS: %s\n' "$FINAL"
