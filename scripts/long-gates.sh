#!/usr/bin/env sh
# P-0: verdicts of the long-run gates G-ECO-6 and G-ECO-7 (docs/ECOLOGY_TARGETS_V3_2.zh.md §5)
# over a summary.txt of ECO_SET=long runs (thirst off, default map).
#   scripts/long-gates.sh <summary.txt> [--enforce]
# G-ECO-6: every run completed all requested steps with every species alive.
# G-ECO-7: for every run, each herbivore has lnSdRun <= 0.40 and a whole-run
#          minimum >= 0.40 of its target (Zebra 2000, Buffalo 1200, Gazelle 3000).
# Prints one line per run and one verdict per gate. Without --enforce the exit
# code is 0 whatever the verdicts; with it, any FAIL exits 1.
set -eu
SUMMARY="${1:?usage: long-gates.sh <summary.txt> [--enforce]}"
ENFORCE="${2:-}"
RESULT="$(awk '
function field(name,   i, n, parts) {
    for(i = 1; i <= NF; i++) {
        n = length(name) + 1
        if(substr($i, 1, n) == name "=") return substr($i, n + 1)
    }
    return ""
}
function pick(list, species,   n, items, i, kv) {
    n = split(list, items, ",")
    for(i = 1; i <= n; i++) {
        split(items[i], kv, ":")
        if(kv[1] == species) return kv[2]
    }
    return ""
}
BEGIN {
    target["Zebra"] = 2000; target["Buffalo"] = 1200; target["Gazelle"] = 3000
    herb[1] = "Zebra"; herb[2] = "Buffalo"; herb[3] = "Gazelle"
}
/^ECO_RESULT/ {
    runs++
    seed = field("seed"); alive = (field("extinctionStep") == "-1" && field("completed") == field("requested"))
    ok7 = 1; detail = ""
    for(h = 1; h <= 3; h++) {
        sp = herb[h]
        sd = pick(field("lnSdRun"), sp); lo = pick(field("min"), sp)
        if(sd == "" || sd == "-" || lo == "") { ok7 = 0; detail = detail " " sp ":no-data"; continue }
        if(sd + 0 > 0.40 || lo + 0 < 0.40 * target[sp]) ok7 = 0
        detail = detail " " sp ":sd=" sd ",min=" lo
    }
    if(!alive) bad6++
    if(!ok7) bad7++
    printf "RUN seed=%s extinct=%s requested=%s completed=%s G6=%s G7=%s%s\n", seed, \
        (alive ? "no" : field("extinctSpecies") "@" field("extinctionStep")), field("requested"), \
        field("completed"), (alive ? "PASS" : "FAIL"), (ok7 ? "PASS" : "FAIL"), detail
}
END {
    if(runs == 0) { print "VERDICT G-ECO-6=NO_RUNS G-ECO-7=NO_RUNS"; exit }
    printf "VERDICT G-ECO-6=%s G-ECO-7=%s runs=%d\n", (bad6 ? "FAIL" : "PASS"), (bad7 ? "FAIL" : "PASS"), runs
}' "$SUMMARY")"
printf '%s\n' "$RESULT"
if test "$ENFORCE" = --enforce
then
    printf '%s\n' "$RESULT" | grep -q '^VERDICT G-ECO-6=PASS G-ECO-7=PASS' || {
        echo 'LONG_GATES FAIL' >&2; exit 1; }
fi
exit 0
