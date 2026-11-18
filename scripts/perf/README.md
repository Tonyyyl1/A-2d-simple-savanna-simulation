# scripts/perf: verification and measurement for the kernel speed-up work

All of these assume the repository root is two levels up, and never run next to another simulation JVM.

| Script | Use |
|---|---|
| `stage.sh <tag> <0\|1>` | per-commit verification: `core` group, `./check.sh` (T2), the five reference traces with the audit on (and with `-Dsavanna.audit=off` when the second argument is 1). It recompiles `build/dev-classes`. |
| `verify-traces.sh <tag> <classpath> [jvm flags]` | the five reference traces (three LEGACY_V1, two PROCEDURAL_V2 seed 4201) against `build/perf-base/fp-*.txt`; prints `TRACE_RESULT tag=<tag> rc=0` when all are byte-identical |
| `longrun.sh <tag> <classpath>` | candidate A, seed 1111, 10000 steps, `--diag`, audit off, under `/usr/bin/time -l`; compares `ECO_RESULT` and `ECO_DIAG` with `build/v33-p1-confirm4` and prints the GC pause total |
| `measure-protocol.py <outdir> <rounds> name=classpath[@flag,flag] ...` | the plan author's 2026-11-15 measurement protocol: for the default (1000 steps) and candidate A (2000 steps) configs, `rounds` rounds of every variant in order, one JVM at a time; waits while another simulation JVM runs; a run is valid only with effective clock >= 3.97 GHz and real/(user+sys) <= 1.02 (up to 3 re-runs per variant); results in `<outdir>/results.jsonl`. `PROTO_IGNORE_BUSY=1` skips the wait and the retries (informal data only); `PROTO_CONFIGS=default` limits the configs. |
| `measure-summary.py <results.jsonl> [baseVariant]` | medians, min/max, spread (flags a group with fewer than 5 valid runs or more than 6% spread) and the ratios against the base variant |
| `record-jfr.sh` | the four S2-8 JFR recordings (`JFR_CP` selects the class directory); `jfr-top.py <samples.txt>` then lists the top leaf frames, inclusive savanna methods and the HashMap family share; `scripts/jfr-inclusive.py` gives the audit-overhead UNION |

Frozen class directories: `build/perf-base/classes` (baseline `8d0824f`, audit flag ignored, so the audit is always on), `build/perf-s1/classes` (S1-7), `build/perf-cur/classes` (copy of `build/dev-classes` at the commit named in its `classes.source`).
