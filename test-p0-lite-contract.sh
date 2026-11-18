#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
MODE="${1:-all}"
TMP="$(mktemp -d)"
PROBE="$ROOT/src/test/java/savanna/__P0CacheDeletionProbe.java"
BROKEN="$ROOT/src/test/java/savanna/__P0CacheBrokenProbe.java"
cleanup() {
    rm -f "$PROBE" "$BROKEN"
    rm -rf "$TMP"
}
trap cleanup EXIT HUP INT TERM
fail() { echo "P0-lite contract FAILED: $1" >&2; exit 1; }
expect_reject() {
    NAME="$1"; NEEDLE="$2"; shift 2
    if "$@" >"$TMP/$NAME.log" 2>&1; then fail "$NAME unexpectedly succeeded"; fi
    grep -Fqi "$NEEDLE" "$TMP/$NAME.log" || { cat "$TMP/$NAME.log" >&2; fail "$NAME missing rejection reason"; }
    echo "PASS $NAME"
}

identity_checks() {
    "$ROOT/validate-machine-state.sh" > "$TMP/current.json"
    jq -e '.plan.state == "r0_completed_verified__r1_authorized_in_progress" and .next.node == "R1-PERFORMANCE-AND-REAL-TERRAIN" and .next.authorized == true' "$TMP/current.json" >/dev/null || fail 'current route is not the completed R0 / R1-authorized V2.7 route'
    jq '.current.plan.source = "wrong-plan.md"' "$ROOT/DEVELOPMENT_STATUS.json" > "$TMP/bad-state.json"
    expect_reject malformed_current 'malformed' "$ROOT/validate-machine-state.sh" "$TMP/bad-state.json"
    expect_reject promotion_blocked 'promotion blocked' "$ROOT/promote-dev-candidate.sh" p0-fixture
    echo 'P0_LITE_IDENTITY PASS: validation=2 promotion=blocked'
}

cache_initial() {
    rm -rf "$ROOT/build/dev-cache" "$ROOT/build/dev-classes" "$ROOT/build/dev-test-classes"
    "$ROOT/compile-dev.sh" > "$TMP/first.log"
    grep -Fq 'DEV_COMPILE main=compiled test=compiled' "$TMP/first.log" || fail 'first development compile did not compile both trees'
    "$ROOT/compile-dev.sh" > "$TMP/reuse.log"
    grep -Fq 'DEV_COMPILE main=reused test=reused' "$TMP/reuse.log" || fail 'unchanged development compile recompiled sources'
    echo 'P0_LITE_CACHE_INITIAL PASS: compile=2 javac=2'
}

cache_deletion() {
    test -f "$ROOT/build/dev-cache/test.ready" || fail 'cache-initial must run first'
    printf 'package savanna; final class __P0CacheDeletionProbe {}\n' > "$PROBE"
    "$ROOT/compile-dev.sh" > "$TMP/add.log"
    grep -Fq 'DEV_COMPILE main=reused test=compiled' "$TMP/add.log" || fail 'test-source addition did not invalidate test cache'
    rm -f "$PROBE"
    "$ROOT/compile-dev.sh" > "$TMP/delete.log"
    grep -Fq 'DEV_COMPILE main=reused test=compiled' "$TMP/delete.log" || fail 'test-source deletion did not invalidate test cache'
    test ! -e "$ROOT/build/dev-test-classes/savanna/__P0CacheDeletionProbe.class" || fail 'deleted test class remained in output'
    echo 'P0_LITE_CACHE_DELETION PASS: testJavac=2'
}

cache_failure() {
    test -f "$ROOT/build/dev-cache/test.ready" || fail 'cache-initial must run first'
    printf 'package savanna; final class __P0CacheBrokenProbe { broken }\n' > "$BROKEN"
    expect_reject failed_compile 'DEV_COMPILE main=reused test=failed' "$ROOT/compile-dev.sh"
    test ! -e "$ROOT/build/dev-cache/test.ready" || fail 'failed test compile was marked READY'
    rm -f "$BROKEN"
    "$ROOT/compile-dev.sh" > "$TMP/recovery.log"
    grep -Fq 'DEV_COMPILE main=reused test=compiled' "$TMP/recovery.log" || fail 'development cache did not recover after failed compile'
    echo 'P0_LITE_CACHE_FAILURE PASS: failedReady=0 recoveryJavac=1'
}

case "$MODE" in
    identity) identity_checks ;;
    cache-initial) cache_initial ;;
    cache-deletion) cache_deletion ;;
    cache-failure) cache_failure ;;
    group-core)
        "$ROOT/test-dev.sh" core > "$TMP/group-core.log"
        grep -Fq 'Savanna development group: core' "$TMP/group-core.log" || fail 'named core group did not execute'
        echo 'P0_LITE_GROUP PASS: core'
        ;;
    all)
        identity_checks
        cache_initial
        cache_deletion
        cache_failure
        "$ROOT/test-dev.sh" core > "$TMP/group-core.log"
        grep -Fq 'Savanna development group: core' "$TMP/group-core.log" || fail 'named core group did not execute'
        echo 'P0_LITE_CONTRACT PASS: validation=2 cache=6 promotion=blocked longRuns=0 packages=0'
        ;;
    *) echo 'Usage: test-p0-lite-contract.sh [identity|cache-initial|cache-deletion|cache-failure|group-core|all]' >&2; exit 2 ;;
esac
