# Source layout

This repository uses a physical source split and the `savanna` Java package:

- `src/main/java/savanna/` — runtime production code; this is the only Java
  source compiled into a candidate or release JAR.
- `src/test/java/savanna/` — unit, integration, contract, diagnostic-probe and
  developer-tool code.
  It is compiled against the production classes only for verification.
- `build/classes/` — disposable production compilation output.
- `build/test-classes/` and `build/verification-classes/` — disposable local
  test output; neither is packaged.

`build.sh` creates only production classes. `test.sh` invokes the production
build, compiles the test tree into a separate output directory, and runs the
default verification suite. The canonical workspace commands are:

```bash
BUILD_ID=local-dev ./build.sh
java -cp build/classes savanna.VisualSimulationRunner
java -cp build/classes savanna.SimulationRunner 5000

./test.sh
java -cp build/classes:build/test-classes savanna.AllTests
java -cp build/classes:build/test-classes savanna.WaterSafetyProbe 1000 100
```

The first classpath is production-only. The combined classpath is valid only
after `./test.sh` (or equivalent separate test compilation), and is required
for tests, probes and developer tools. Candidate JARs contain no `*Test`,
`*Tests`, `*Probe`, `AllTests`, or test-support class.

The invalidated M1a–M1d source ZIPs are historical artifacts, not accepted
reproducibility evidence. A new source archive becomes supported only after
its contents can rebuild and verify the candidate in a clean round-trip check.

The BlueJ metadata under `legacy/bluej/` predates the `src/main`/`src/test`
split and the `savanna` package. It is pending a dedicated adapter and is not a
supported build, run or acceptance path. IntelliJ uses
`savanna-simulation.iml`, with `src/main/java` marked as production and
`src/test/java` marked as test source.

The remaining M1 work is deliberately separate: incremental responsibility
splits for the large Swing and animal classes.

## Context observation boundaries

`SimulationContext` remains the public entry point for ecological systems,
step lifecycle and observer registration. Its observation storage is split
into two package-private components:

- `SimulationEventJournal` owns current/previous event lists, the bounded
  recent-event buffer, exact actor/type event indices, frame interval counts
  and synchronous event listener delivery. Current/previous lists are
  read-only views tied to their originating step. Recent events and frame
  summaries are detached snapshots. Frame counts remain complete when either
  sample buffer is truncated.
- `StepObservationLedger` owns completed actor frames, the optional predator
  S0/S4 assessment join and completed-step trophic observers. It rejects
  duplicate, wrong-step and incomplete frame identities. Starting a step clears
  collected facts while preserving listener registrations; observation remains
  gated by the same listener presence checks.

Neither component owns ecological random sources or changes action execution.
The context retains the live population, death/birth accounting, position and
critical-water state, and the PF1-aF resource ledger. `Simulator.reset()` still
reattaches its registered observers to the newly constructed context.

`SimulationObservationTests` exercises these contracts through the existing
context/simulator API and is included in the `frame` group. For worktree checks,
compile with `./compile-dev.sh`, then run the standalone contracts with:

```bash
java -Djava.awt.headless=true \
  -cp build/dev-classes:build/dev-test-classes savanna.SimulationObservationTests
```

These development checks are separate from candidate/release qualification.

## A1a-R2 single position board

R2 makes the production simulator own exactly one position transaction per
step:

`step-start snapshot → availability view → claims → pure reservation plan → single commit`

`Simulator` creates `PositionStepCoordinator`, which captures a consistent
`StepStartPositionSnapshot`, lets each `SavannahAnimal` produce a proposal
against a read-only `PositionAvailabilityView`, resolves all adult and spawn
claims once through `PurePositionReservationResolver`, then commits the
validated plan to a fresh `Field`. Production does not call the legacy direct
`Animal.act` or `FieldTransition` path; those remain only as isolated
compatibility-test support.

The resolver protects living origins, supports stay/swap/cycle and bounded
fallback candidates, ranks critical water, water departure, survival, ordinary
movement and spawn claims, and uses waiting debt plus a step-salted tie break
without consuming ecology RNG. An adult killed during proposal receives a
release fact, so its origin is no longer protected. A spawn is only a token
until it wins: only commit creates the child, consumes its ID, charges its
parent and writes its BIRTH observation. Commit validates Field/list/ledger
agreement and emits exactly one transition plus the corresponding event.

The completed R2 candidate is
`artifacts/candidates/accepted/a1a-r2-single-position-board-20260724-perf/`.
It passes the R1 58-fixture kernel suite, R2 26-fixture position-board suite,
591/591 default tests, water safety/repeatability, R0 300-step ecology floors
and the 1.25× elapsed gate. A private Field animal-to-location index is an
equivalent commit-path optimization; it does not change priorities, candidate
order, fallbacks, salt, or ecology parameters.

The original failed A1a experiment remains archived at
`artifacts/candidates/failed/a1a-position-reservation-20260723-ecology-gate/`.
R2 is not whole-program certification. R3 preflight failed its first
five-seed ecology gate: at ecology seed 2111, R2 ends at balance 18.80 against
a limit of 12.00 and Cheetah=56 against R0=239. The complete R3 failure
handoff is `A1A_R3_PREFLIGHT_FAILURE_2026-07-24.zh.md`.

The later frozen-D2 investigation rejected H1/H2/H29-H35 and isolated a
pre-existing maternal prey-desert cohort: 307/505 seed-2111 first-active
Cheetah misses versus 26/366 at seed 1111. This is a BR1-b local
conception-eligibility defect, not another R2 routing defect. The user approved
the closed prerequisite corridor on 2026-07-29; no R2R semantic candidate
exists. L1-F and SOC1-aF are completed_verified with one step-start immutable
spatial owner, a complete canonical registry/capacity schema, exact frozen-D2
transparency, and zero RNG/claim differences. The current executable batch is
fact-only PF1-aF. The corridor retains
all R3 numeric gates, is specified in
`artifacts/history/investigations/diagnostics/a1a-r2r-natal-desert-dynamics-20260729/TARGETED_IMPLEMENTATION_PLAN.zh.md`.
Existing frozen output identifies only the BR1-P policy inputs and transaction
shape; its gestation, cooldown, credit, capacity/floor, and juvenile-reserve
numbers remain unidentified. A supplemental artifact-only probe compiles and
prints a non-simulating schema, but has executed no transparency or ecology
seed and cannot expose exact capacity, final legacy breeding probability, or
conception state.
The approval authority and stop-on-first-fail boundary are captured in the
`artifacts/history/investigations/diagnostics/a1a-r2r-natal-desert-dynamics-20260729/ROADMAP_AMENDMENT_PROPOSAL.zh.md`.
It pre-registers the fact-only run budget and requires completed_verified
BR1-P to become the future R3 semantic reference. The R3 gate spec now records
that reference and remains blocked on BR1-P. The accepted L1-F candidate is
`artifacts/candidates/accepted/l1-f-spatial-ecology-facts-20260729/`; PF1-aF
and later corridor batches remain gated until their immediate predecessor is
completed_verified.
The current R2 JAR cannot be rerun directly as R3, and broad A1b remains
blocked. The machine-readable certification contract is
`A1A_R3_GATE_SPEC.json`.
