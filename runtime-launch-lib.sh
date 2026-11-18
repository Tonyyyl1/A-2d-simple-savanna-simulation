#!/usr/bin/env sh

runtime_fail() {
    printf 'Runtime launch FAILED: %s\n' "$1" >&2
    exit 1
}

runtime_require_one_jar() {
    runtime_dir="$1"
    set -- "$runtime_dir"/savanna-simulation-*.jar
    test "$#" -eq 1 || runtime_fail "expected exactly one candidate JAR in $runtime_dir"
    test -f "$1" || runtime_fail "candidate JAR is missing in $runtime_dir"
    printf '%s\n' "$1"
}

runtime_validate_candidate_class() {
    runtime_class="$1"
    case "$runtime_class" in
        WORKTREE|ACCEPTED_BASELINE|TRANSITIONAL_CANDIDATE|INFRASTRUCTURE_CANDIDATE|ECOLOGY_CANDIDATE|RELEASE_CANDIDATE) ;;
        *) runtime_fail "unknown candidate class: $runtime_class" ;;
    esac
}

runtime_exec_jar() {
    runtime_class="$1"
    runtime_jar="$2"
    shift 2
    runtime_validate_candidate_class "$runtime_class"
    test -f "$runtime_jar" || runtime_fail "JAR is missing: $runtime_jar"
    printf 'Launch identity: candidate_class=%s jar=%s\n' "$runtime_class" "$runtime_jar"
    exec java "-Dsavanna.candidate.class=$runtime_class" -jar "$runtime_jar" "$@"
}

runtime_exec_classes() {
    runtime_class="$1"
    runtime_classes="$2"
    shift 2
    runtime_validate_candidate_class "$runtime_class"
    test -f "$runtime_classes/META-INF/savanna-build.properties" ||
        runtime_fail "worktree build stamp is missing; run BUILD_ID=<id> ./build.sh"
    for runtime_required in VisualSimulationRunner Simulator SimulationContext
    do
        test -f "$runtime_classes/savanna/$runtime_required.class" ||
            runtime_fail "worktree production class is missing: savanna.$runtime_required"
    done
    printf 'Launch identity: candidate_class=%s classes=%s\n' \
        "$runtime_class" "$runtime_classes"
    exec java "-Dsavanna.candidate.class=$runtime_class" -cp "$runtime_classes" \
        savanna.VisualSimulationRunner "$@"
}
