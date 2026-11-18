#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
STATE="${1:-$ROOT/DEVELOPMENT_STATUS.json}"
case "$STATE" in /*) ;; *) STATE="$ROOT/$STATE" ;; esac
test -f "$STATE" || { printf 'Machine state is missing: %s\n' "$STATE" >&2; exit 1; }
IDENTITY_DIR="$(mktemp -d)"
cleanup() { rm -rf "$IDENTITY_DIR"; }
trap cleanup EXIT HUP INT TERM
"$ROOT/generate-source-manifest.sh" "$ROOT/src/main/java" \
    "$IDENTITY_DIR/main.manifest"
"$ROOT/generate-source-manifest.sh" "$ROOT/src/test/java" \
    "$IDENTITY_DIR/test.manifest"
ACTIVE_MAIN_HASH="$(shasum -a 256 "$IDENTITY_DIR/main.manifest" | awk '{print $1}')"
ACTIVE_TEST_HASH="$(shasum -a 256 "$IDENTITY_DIR/test.manifest" | awk '{print $1}')"
ACTIVE_MAIN_COUNT="$(wc -l < "$IDENTITY_DIR/main.manifest" | tr -d '[:space:]')"
ACTIVE_TEST_COUNT="$(wc -l < "$IDENTITY_DIR/test.manifest" | tr -d '[:space:]')"

# Deliberately project only .current: historical status fields cannot authorize a run.
jq -e '
  .current as $current |
  ($current | type == "object") and
  ($current.plan.source == "DEVELOPMENT_PLAN_V2_7_EXECUTABLE.zh.md") and
  ($current.plan.sourceSha256 == "7bc2c945e91647031a861655b6e78f2e58773af0b5dcb3e4e783d2586c71f36e") and
  (($current.plan.state == "r0_completed_verified__r1_authorized_in_progress") or
   ($current.plan.state == "r1_completed_verified__r2_authorized_in_progress") or
   ($current.plan.state == "r2_e01_completed__r2_e02_scope_review_required") or
   ($current.plan.state == "r2_e01_completed__r2_e02_authorized_in_progress") or
   ($current.plan.state == "r2_e02_completed__r2_e03_scope_review_required")) and
  ($current.node | type == "string") and
  ($current.acceptedBaseline.candidate | type == "string") and
  ($current.engineeringPredecessor.mainSourceCount == 229) and
  ($current.activeWorktree.mainSourceCount == $mainCount) and
  ($current.activeWorktree.testSourceCount == $testCount) and
  ($current.activeWorktree.mainSourceManifestFileSha256 == $mainHash) and
  ($current.activeWorktree.testSourceManifestFileSha256 == $testHash) and
  ((($current.next.node | type == "string" and startswith("R1-")) and
    ($current.next.authorized == true)) or
   ((($current.next.node | type == "string" and endswith("SCOPE-REVIEW-REQUIRED"))) and
    ($current.next.authorized == false)) or
   (($current.next.node | type == "string" and startswith("R2-")) and
    ($current.next.authorized == true))) and
  ($current.prohibitedOutsideCurrentR0R1Scope | type == "array")
' --arg mainHash "$ACTIVE_MAIN_HASH" --arg testHash "$ACTIVE_TEST_HASH" \
  --argjson mainCount "$ACTIVE_MAIN_COUNT" --argjson testCount "$ACTIVE_TEST_COUNT" \
  "$STATE" >/dev/null || {
    printf 'Machine state current route is malformed or not the V2.7 R0-completed/R1-authorized route.\n' >&2
    exit 1
}
jq -c '.current' "$STATE"
