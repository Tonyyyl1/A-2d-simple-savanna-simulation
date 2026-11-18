# Changelog

All notable changes for the African Savanna Predator-Prey Simulation are
documented here.

## Unreleased - v1.8 PLAN-2.4.1

### Changed

- Replaced the future-route source with
  `DEVELOPMENT_PLAN_V2_4_1_2026-08-07.zh.md` while preserving the v2.4 plan and
  its SHA-256 as immutable history.
- Kept `AllTests full` truthfully at 926/927 FAIL and registered its
  byte-identical predecessor balance failure as open debt `ECO-LONG-01`.
  FRAME is structurally verified because the debt has zero semantic difference
  and no new failure, but the debt is forbidden from semantic candidates,
  BR1-P2, R3, promotion, holdout, and release.
- Replaced the assumption that BR1-P2 must solve every long-horizon imbalance
  with OBS-1A recruitment facts, OBS-1B long-horizon trophic facts, and an
  ATTR-1 decision-only mechanism gate. Only a unique predator-recruitment
  result may continue to PRA-1; encounter, prey-capacity, mixed, or unknown
  results stop for a new plan.
- Preserved the 12.0 balance gate, default seed/configuration, old failed
  candidates, policy hashes, `CURRENT_DEV`, ecology parameters, and release
  artifacts. No implementation or simulation was authorized by this plan
  revision.

## Unreleased - v1.8 V24-FRAME-1

### Added

- Added immutable `HuntOpportunityFact` and `StepFrameLedger` facts plus a
  `StepFrameLedgerListener`, with exact S0 capture, S1 pure proposal, S2
  primary execution, S3 reproduction commit, and S4 publish phases.
- Added 49 FRAME fixtures for opportunity identity, pure proposal behavior,
  resource floors, same-step kill isolation, pregnancy/loss ordinals,
  complete per-actor ledgers, immutability, and observer transparency.
- Archived expected-red, default/full outputs, manifest delta, two-seed
  transparency evidence, and a zero-difference inherited-red ledger under
  `artifacts/history/investigations/v24-frame-1-20260802/`.

### Changed

- Execute HUNT from its frozen S0 opportunity instead of recomputing range
  after a same-frame stamina debit, and make reproduction selection a pure S1
  proposal with an atomic S3 commit.
- Require the production `Simulator` path to publish exactly one immutable S4
  ledger for every step-start actor while retaining the partial-board resolver
  as a legacy fixture adapter.
- Kept the BR1-P policy, ecology parameters, gate thresholds, candidates,
  `CURRENT_DEV`, holdouts, and releases unchanged.

### Verification

- `V24Frame1Tests` pass 49/49 and default `./test.sh` passes 924/924, including
  the 318-class production JAR boundary and 319-file runtime equivalence.
- Seed 1111 and 2111 FRAME transparency runs preserve 8/8 legacy sequences and
  11/11 profile components with zero duplicate ledgers; allowed differences
  are only the phase ledger and opportunity fact.
- `AllTests full` is 926/927. Its sole 5000-step balance red (`32.07`) and all
  six normalized semantic lines are byte-identical to the accepted
  CORE-REBASE diagnostic. PLAN-2.4.1 records the red as an open inherited
  ecological debt not attributable to FRAME; it does not relax the gate or
  rewrite full as green.

## Unreleased - v1.8 V24-GATE-1

### Added

- Added a single `GateResult` authority for console, JSON, Markdown, failure
  order, suite banners, and exit status, plus a read-only archived-output
  parser and fault-seeded result round-trip fixtures.
- Added versioned CORE, FRAME, and BR1-P2 gate profiles; fail-closed
  candidate-manifest-v2 validation; a D2 same-frame opportunity ledger; and a
  Java-AST-based A1b owner/consumer/dependency contract.
- Added a pure `test-v24-gate-1.sh` verification path and sealed evidence under
  `artifacts/history/investigations/v24-gate-1-20260802/`.

### Changed

- Replaced the future execution route with a versioned gate, same-frame
  transaction, full closed-loop observation, PRA-1 calibration, BR1-P2, and
  R3 requalification sequence.
- Registered all six BR1-P default-regression failures and corrected the first
  failure to D2 F03; the old policy and implementation authority are consumed
  and cannot be reused or tuned.
- Completed V24-GATE-1 with 74/74 gate assertions and 31/31 A1b kernel/AST
  assertions. Historical outputs parse as accepted 875/875 and failed
  1003/1009 with six ordered failures and D2 F03 first.
- At GATE-1 completion, kept `CURRENT_DEV` on accepted CORE-REBASE and left
  production code, simulation seeds, candidates, holdouts, releases, and
  immutable prior evidence unchanged. GATE-1's Simulator, official
  ecology-gate, and R3 holdout invocation counts were zero; FRAME-1 later
  received its own independent authority as recorded above.

## Unreleased - v1.8 C1a Hydration Policy Extraction

### Added

- Added immutable `HydrationPolicy` and `WaterNeedProfile` configuration,
  preserving the W4 thirst defaults without changing behavior.
- Added an explicit `WaterFoodSecondaryPolicy`; the W4-compatible
  `LEGACY_EXPLICIT` behavior remains the default after C1d comparison.
- Added deterministic per-pool shoreline topology, locked seek-cycle targets,
  arrival-deficit facts, and critical-water destination reservations.
- Added W3 post-drink departure and cooldown state, plus immutable step-start
  waterhole congestion facts used for safe target selection.
- Added deterministic zero-water grace handling: animals at a drinkable shore
  receive one final drink, while animals off shore die from dehydration.

### Changed

- Recorded C1b/C1c as evidence-gated no-change decisions: calibration and
  low-hydration breeding restrictions were not triggered by the baseline run.
- SEEK movement now accepts only strictly target-closer candidates and reports
  waiting instead of using a general movement fallback.
- Safe new seek cycles balance path cost against frozen pool load; critical
  animals continue to prefer the shortest reachable target.

## Unreleased - v1.8 W1 Behavior Outcomes And Rest Semantics

### Added

- Added immutable `BehaviorOutcome`, ordered `ExecutedEffect`, and
  `MovementDisposition` facts so primary intent, physical movement, executed
  work, display outcome, and recovery credit remain separately observable.
- Added explicit `WAIT` behavior and W1 regression coverage for drinking,
  blocked exits, failed hunts followed by movement, intentional rest, and
  render-fact preservation.
- Added the candidate-only `W1MultiSeedStabilityProbe` for fixed terrain,
  thirst-off ecological protection checks.

### Changed

- Restricted daily rest recovery credit to intentional successful rest;
  drinking, grazing, waiting, and other stationary effects no longer recover
  stamina as if they were rest.
- Updated snapshots, HUD labels, status logs, and interval reports to show
  outcome facts independently rather than treating the last intent as action.
- Replaced same-priority `ActionType.ordinal()` resolver ordering with an
  explicit, testable priority table.

## Unreleased - v1.8 O0b3 Window And Species Heatmaps

### Added

- Added `LAST_100`, `LAST_1000`, and `WHOLE_RUN` selections with actor-species
  filtering, bounded primitive sparse buckets, and immutable selected-cell facts.
- Added event/window/species/range/max disclosure plus per-species marker and
  priority/sampling disclosure in the heatmap HUD.

### Verification

- Candidate `v1.8-o0b3-window-species-heatmap-20260722-dev` passes 429/429
  tests, runtime identity, core/source hashes, classpath rejection, and the
  fixed 1,000-step ecology baseline.
- Snapshot proof passes 27/27, exact window/species proof passes 16/16, and
  the candidate pressure fixture records a 26.83 ms worst p95 and 28.84 MiB
  retained heap across LAST_1000 All/DRINK toggles.

## Unreleased - v1.8 O0b2 Frozen Heatmap Snapshots

### Added

- Added immutable same-step `HeatmapSnapshot`, `VisibleRenderFrameFactory`, and
  pure `HeatmapRenderer` for Whole-run / All-species heatmaps.
- Added O0b2 snapshot and 3×86,400-cell pressure probes, plus a candidate
  regression runner.

### Changed

- Swing and fixed PNG heatmap rendering now consume only a `RenderFrame`'s
  frozen snapshot. The Swing paint path no longer reads the live
  `EventAccumulator`.

### Verification

- Candidate `v1.8-o0b2-frozen-heatmap-20260722-dev` passes 413/413 tests,
  runtime identity, core/source hashes, classpath rejection, and the fixed
  1,000-step ecology baseline.
- Its headless pressure fixture records a 21.95 ms worst p95 and 35.26 MiB
  retained-heap delta across All/DRINK selection toggles; the snapshot proof
  passes 27/27 checks.

## Unreleased - v1.8 O0b1 Frame Event Facts

### Added

- Added immutable `FrameEventSummary` frame facts with producer-defined
  100-step boundaries, exact event-type × actor-species counts, bounded
  deterministic representative samples, and explicit sample truncation.
- Added O(1) `BoundedEventDeque` for best-effort recent-event diagnostics and
  a standalone O0b1 frame-event regression probe.

### Changed

- `RenderFrame` now carries complete interval facts. Its legacy
  `eventSummary` compatibility list is only the bounded representative sample;
  it no longer exposes the last 600 events as if they belonged to one frame.

### Verification

- 386/386 automated tests pass, including reset/non-boundary cadence, listener
  reconciliation, published-frame immutability, and a dense interval that
  exceeds both representative and recent-event capacities without losing totals.
- O0b1 stops before heatmap snapshots, windows, and species selection; ecology
  behavior and random consumption remain unchanged.

## Unreleased - v1.8 O0a Read-only Water Behaviour Baseline

### Added

- Added immutable post-decision `TransitionObservation` facts and listener
  registration, stable connected-water `WaterSourceIndex`, deterministic
  observable-state `SimulationFingerprint`, and `WaterBehaviorRegressionProbe`.
- Added frozen-seed O0a calibration runner and verifier. The probe reports
  physical movement separately from display action labels, water proximity,
  density, shore occupancy, drink exits, events, SEEK classifications, and
  stepwise identity conservation.

### Verification

- Probe/no-probe step fingerprints and same-seed reruns match. Five calibration
  1,000-step and three 5,000-step runs retain zero identity-conservation errors.
- Validation, S1 holdout, and sentinel seed sets remain unrun by design.

## Unreleased - v1.8 R0 Runtime Identity

### Added

- Added stamped `BuildIdentity`, immutable `RenderProvenance`, deterministic
  `LEGACY_V1` logical-terrain SHA-256 fingerprints, and a core CodeSource /
  duplicate-resource runtime guard.
- Added canonical clean build, candidate, verification, launcher, `CURRENT_DEV`,
  orphan-audit, source-manifest, core-class-hash, and legacy-class archive tools.
- Added build, short source hash, ecology seed, terrain seed, generator version,
  and terrain fingerprint to the shared HUD/PNG provenance; HTML and console
  output retain the full source hash and CodeSource.

### Verification

- 337/337 automated tests and the locked 1,000-step population baseline pass.
- Candidate/source/core hashes match, orphan audit is zero, root `.class` count
  is zero, and duplicate-candidate plus old-JAR-first classpaths are rejected.

## Unreleased - v1.7 Behavior Pipeline

### Added

- Added an immutable shared `RenderFrame` manifest consumed by separate
  `OverviewRenderer` and `InspectRenderer` implementations.
- Added `RenderSnapshotTool` for fixed-size PNG output through the same
  ordinary renderer used by the live Swing view.
- Added `HistoricalEventOverlay` so replay-only events never enter the current
  living-animal manifest, plus a complete bilingual `EventMetadataRegistry`.
- Added a deterministic shared-render pipeline probe, shoreline pixel audit,
  bytecode contract gate, and headless renderer performance diagnostic.
- Added explicit behavior intents and providers for drinking, seeking water,
  hunting, grazing, resting, moving, and breeding, plus overview action cues.
- Added two widely separated small pools: one at the original oasis and one in
  the southeast, each with independent visual treatment and shoreline access.
- Added a deterministic two-component terrain test and retained water-safety
  audits for both shorelines.
- Added `FieldTransition`, `MovementResolver`, explicit transition outcomes,
  collision-safe movement and birth reservations, and position-conflict events.
- Added `DeathCause` and a single death-recording path for age, starvation,
  dehydration, disease, predation, position conflict, and unknown deaths.
- Added typed death events plus cumulative cause totals in terminal diagnostics,
  animal activity details, heatmaps, experiments, and HTML step reports.

### Changed

- `SimulatorView` now receives the simulator's already-published immutable
  frame; ordinary and inspect rendering no longer traverse mutable `Field`.
- Ordinary shoreline clipping now clears water-pixel runs after marker drawing,
  avoiding per-animal one-pixel `Area` construction while retaining zero animal
  ink on water.
- `Field.placeAnimal()` no longer silently replaces a living occupant.
- Nearest free passable fallback selection now uses reachable path distance
  before deterministic row/column tie-breaking.

### Verification

- 337/337 automated tests pass. The fixed-seed 1,000-step run keeps all five
  species alive with a final balance ratio of 5.67.
- Native GUI stress passed 300 paints across five window sizes and both refresh
  boundaries. The final 1732x948 performance result was p95=25.96 ms,
  max=28.85 ms and queued-input=29.84 ms.

## [1.6] - Atomic Overview Frames And Display Fact Audit

### Added

- Added `OverviewFrameBuffer` and its deterministic atomicity probe. Ordinary
  overview frames now use private back buffers and commit only after terrain,
  animal markers, weather tint, and diagnostics are complete.
- Added B0 display-contract, B1 current-actor, B2 overview-frame, and B3
  display-fact verification gates.
- Added `DisplayFactAudit` / `DisplayFactAuditProbe` for immutable Field,
  ordinary marker candidate, and inspect actor consistency evidence.

### Changed

- `SimulatorView` now marshals overview construction to the EDT and keeps the
  previous complete frame visible during failed or expired builds and resize.
- Resize repaints/scales the last complete frame rather than traversing Field
  or starting a competing overview rebuild.
- Packaging now targets Java 17 and creates a top-level source snapshot only;
  build candidates and backups are excluded from release source archives.

### Verification

- B3 development acceptance: 253/253 automated tests, fixed-seed 1,000-step
  stability parity, native-window stress probe (300 paints), and native-window
  performance p95=122.30 ms at 1732×948.

## Post-1.5 Work Started - v1.6 Observability

- Added `ThirstBehaviorProbe` with shoreline counts, maximum reachable shore
  distance, per-species hydration, thirst counts, shoreline occupancy, and
  interval `DRINK` reporting.
- Added `SafeMarkerPlacement`; shoreline markers now remain visible while the
  renderer clips water pixels instead of hiding the entire animal.
- Extended `WaterSafetyProbe` to fail when any drinkable shoreline cell has no
  visible land pixel at the audited canvas sizes.
- The packaged product version remains 1.5 until the complete v1.6 plan is
  implemented and verified.
- Added compatible `SurvivalAssessment` / `SurvivalSystem` evaluation. Food,
  hydration, and overall values are exposed to diagnostics, status logs,
  reports, and the renderer; legacy food behavior methods remain unchanged.
- Added `HydrationStage` / `HydrationAssessment` with distance-aware reserve
  thresholds. `THIRSTY` makes water the primary action and `CRITICAL` adds
  strict waterward placement and blocked-water events.
- Added current-actor filtering for inspect scenes, a `DRINK` scene, interval
  thirst probe accounting, stronger drink heatmap contrast, and small-marker
  status rendering that preserves species color.

## Post-1.5 Planning - Documentation Only

- Added `DEVELOPMENT_PLAN.zh.md` as the canonical next-window handoff and
  staged development plan for thirst visibility, composite survival assessment,
  event observability, model calibration, behaviour intents, movement
  resolution, verification, and future release work.
- This planning entry does not change the current product version: the latest
  implemented and packaged release remains 1.5.

## [1.5] - Shared Renderer, Enhanced Status Dialog, And Thirst Experiment

### Added

- Added `FieldRenderer` as the shared ordinary-animal marker renderer for the
  live view and future snapshot/render tools. It uses the existing
  `VisualGridGeometry` and `VisualFootprint` path for predator/herbivore
  markers, disease dots, survival rings, stamina bars, and thirst drops.
- Added optional `ThirstSystem` / `SavannahThirstSystem` support. The feature
  is disabled by default, drains hydration only when enabled, records `DRINK`
  events, and lets animals drink from passable shoreline cells while
  `WATERHOLE` remains impassable.
- Added `SimulationConfig.Builder.thirstEnabled(boolean)` and
  `SimulationConfig.isThirstEnabled()`.
- Added an `Experimental thirst system` checkbox to `StartupConfigDialog`,
  defaulting to off and resetting to off.
- Added hydration and thirst-system rows to `AnimalStatusLog.rowsFor()` and a
  summary panel to the dialog UI.
- Added `README.zh.md` with the implementation plan, design boundaries, and
  verification notes for the shared renderer, enhanced status dialog, and
  thirst experiment.
- Added `releases/<tag>/` packaging with a runnable jar, matching source zip,
  and `MANIFEST.txt` for build and verification status.
- Added multi-panel-size ordinary-marker water auditing to `WaterSafetyProbe`,
  including non-integer grid scales such as `1387x829`, `1093x731`, and
  `640x480`.
- Added `SimulationConfig.default3x()` as the normal default configuration,
  including default random seed `1111` and terrain seed `20260629`.
- Added `StartupConfigDialog` so visual runs start with an initialization
  screen for steps, delay, tuning multipliers, random seed, and terrain seed.
- Added `Randomizer.reset(long)`, `getSeed()`, and `getDefaultSeed()` for
  reproducible seeded runs.
- Added `TerrainNavigator` with passable-neighbour lookup and one-BFS
  reachability distance maps for terrain-aware movement and hunting.
- Added `HabitatPreferences` with stronger species preferences for spawn,
  movement, grazing, and hunting scores.
- Added `WaterSafetyProbe` and system-test water audits for step 0 and every
  100 steps through step 1000.
- Added `SimulationConfig` so experiment runs can scale map size, initial
  creation, founding population, breeding, disease transmission, and disease
  fatality without changing the normal baseline entry points.
- Added `SimulationExperimentRunner` for `2x`, `3x`, and `4x` map/disease
  pressure experiments, plus a faster `smoke` mode.
- Added a selected `best3x` experiment configuration that keeps both predators
  above `200` after `5000` steps while avoiding single-prey runaway growth.
- Added independent viewport interaction classes for mouse-only, keyboard-only,
  and hybrid pan/zoom controls.
- Added a deterministic `TerrainMap` with a fixed seed and query support via
  `getTerrainAt(Location)`.
- Added `TerrainType` categories for waterholes, grassland, bush, open plains,
  and dry soil.
- Added a smooth terrain background renderer with a left-center waterhole,
  surrounding grassland, bush belts, open plains, dry soil, and a seasonal
  lowland corridor.
- Added 2.5D terrain styling with gradient lighting, region shadows, highlighted
  edges, dry-soil cracks, bush clumps, lowland depth, and water highlights.
- Added clearer animal symbols: predators are triangles, herbivores are circles,
  infected animals keep red disease dots, critical-survival animals show orange
  rings, and low-stamina animals show small stamina bars.
- Added representative animal sampling so dense ordinary herbivore groups stay
  readable while predators, infected animals, critical-survival animals, and
  low-stamina animals remain visible.
- Added low-interference terrain labels for the main background zones.
- Added weather and time-of-day tint overlays plus a compact on-map metric panel
  for grass, disease, survival, stamina, active signals, and short trends.
- Added `SimulationDiagnostics` for readable terminal snapshots during
  headless simulation runs, including trend changes since the previous
  diagnostic line, high-level signals, and short event readouts.
- Added system tests for deterministic map generation, map dimensions, terrain
  category coverage, and safe terrain lookup.
- Added `TerrainMap`, `TerrainType`, `TerrainTileSet`, and
  `SimulationDiagnostics` to the BlueJ package diagram.

### Changed

- `SimulatorView.FieldView` now delegates ordinary animal marker drawing to
  `FieldRenderer`; it still owns Swing image layers, viewport transforms,
  heatmap drawing, inspect actors, and overlays.
- `SimulationContext` now creates `SavannahThirstSystem` only when the active
  config enables it. Default runs keep `thirst=off`.
- `SavannahAnimal` now stores hydration state without consuming additional
  random numbers, preserving the existing default-off ecological random stream.
- `SimulationConfig.describe()` now includes `thirst=on/off`.
- Ecological randomness is now owned by `SimulationContext.getRandom()` and
  injected through simulator population, animal construction, weather, disease,
  food, predation, breeding, and terrain-aware candidate shuffling. Compatibility
  constructors remain for older tests, but production simulation paths use the
  context random stream.
- `package.sh` now requires an explicit version tag and builds from a clean
  temporary directory before writing release artifacts to `releases/<tag>/`.
- `verify-jar.sh` now validates the packaged jar itself and appends the result
  to the release manifest.
- The no-argument and visual simulator defaults now use the 3x
  `target3xBalanced` configuration instead of the old 1x baseline.
- `SimulationContext` now builds terrain from the configured terrain seed.
- `Simulator.reset()` now resets randomization from the configured random seed.
- Initial population, founder placement, movement, grazing, births, and
  defensive final placement now avoid waterhole cells.
- Predator hunting now computes reachability once per predator hunt instead of
  recomputing path distance for every candidate prey location.
- Grassland grazing scores now combine available grass with the animal's
  habitat preference.
- Lion and cheetah hunting scores now make bush/open-plain advantages more
  visible.
- `SimulatorView` window titles include the active configuration summary,
  including random and terrain seeds.
- Inspect mode now renders independently from the ordinary animal-dot layer,
  so behavior actors and `eat`/event labels are not overlaid on top of normal
  map dots.
- Inspect mode now keeps every visible viewport animal as a behavior actor
  instead of capping the actor layer at 20 and relying on normal dots to fill
  the gap.
- Dense inspect views now thin only low-priority `forage`/`prowl` text markers,
  not animal icons.
- Inspect animation keyframes are now terrain-sanitized, and any actor whose
  interpolated trajectory would cross water is collapsed onto a safe land
  footprint instead of sweeping through the waterhole.
- Inspect actors are anchored at cell centers and nudged away from water when
  their visual footprint would touch a waterhole edge.
- Ordinary map drawing skips any passability-violating animal location as a
  final visual guard while water audits continue to catch true Field bugs.
- Ordinary map drawing now also checks the final marker footprint against a
  visual water mask, so grassland shoreline cells whose marker would overlap
  the blue water surface are not drawn on top of the waterhole.
- `SimulationContext` owns the terrain map.
- `SimulatorView.FieldView` now draws two layers: a cached terrain background
  and a separate transparent animal layer.
- Empty field cells no longer cover the terrain background.
- Large population-pressure blocks are no longer drawn on the map; aggregation
  is retained only for ordinary-animal sampling.
- `SimulationRunner` now prints ecosystem diagnostics at the start, every
  `100` steps, and at the final step.
- Disease transmission and fatality pressure can now be multiplied through
  `SimulationConfig` for controlled experiment runs.
- `Simulator.getSummary()` now uses the same diagnostic format as the terminal
  runner.

### Test Coverage

- `./test.sh`
  - Latest recorded result: `180/180` tests passed.
- `java WaterSafetyProbe 1000 100`
  - Passed with `water animals == 0`, `ordinary visual water samples == 0`,
    and `visual water samples == 0` at every checkpoint.
- `java SimulationRunner 5000`
  - Default `thirst=off` output matches the pre-thirst baseline ecology except
    for the expected configuration-summary addition and `Elapsed ms` timing
    line. Final result: `Pop=2643`, `{Lion=358, Cheetah=109, Zebra=618,
    Buffalo=512, Gazelle=1046}`, final balance ratio `9.60`.
- `javac *.java`
  - Passed after the shared renderer and thirst-system changes.
- `./package.sh v1.5-renderer-status-thirst`
  - Generated runnable jar, matching source zip, and `MANIFEST.txt` under
    `releases/v1.5-renderer-status-thirst/`.
- `./verify-jar.sh v1.5-renderer-status-thirst`
  - Packaged jar passed `AllTests 180/180`, `WaterSafetyProbe 1000 100`, and
    `WaterSafetyProbe 18500 500`.
- `java WaterSafetyProbe 1000 100 baseline`
  - Passed with `water animals == 0` at step 0 and every 100 steps to 1000.
- `java WaterSafetyProbe 5000 500`
  - Passed with `water animals == 0` at step 0 and every 500 steps to 5000.
- `java WaterSafetyProbe 18500 500`
  - Passed with `water animals == 0`, `ordinary visual water samples == 0`,
    and `visual water samples == 0` at step 0 and every 500 steps to 18500.
- `java SimulationExperimentRunner smoke`
  - Latest smoke result completed `2x`, `3x`, and `4x` experiment rows.
- `java SimulationExperimentRunner best3x`
  - Latest recorded result after `5000` steps:
    `{Lion=299, Cheetah=213, Zebra=555, Buffalo=609, Gazelle=1014}`,
    density `0.031`, balance ratio `4.76`.

## [1.1] - Testing and Tuning Workflow

### Added

- Added a no-dependency test module that works with plain Java and BlueJ.
- Added `AllTests.java` as the main test entry point.
- Added `TestSupport.java` for assertions, pass/fail output, timing, and exit
  status.
- Added `SimulationUnitTests.java` for local rule checks.
- Added `SimulationSystemTests.java` for subsystem interaction checks.
- Added `SimulationIntegrationTests.java` for headless stability checks.
- Added `TUNING_EFFICIENCY.md` to document the layered tuning workflow and
  timing evidence.
- Added `UPDATE_NOTES.md` as a concise release/update summary.

### Test Coverage

- `java AllTests`
  - Runs unit tests, system tests, and a `1000` step headless stability test.
  - Latest recorded result: `47/47` tests passed in `5683 ms`.
- `java AllTests full`
  - Runs the default suite plus a `5000` step headless stability test.
  - Latest recorded result: `50/50` tests passed in `34205 ms`.
- `java SimulationRunner 200000`
  - Remains the final long-run validation command.
  - Previous validated result: no extinction, final balance ratio `6.03`,
    runtime `1116418 ms`.

### Improved

- Documented a staged tuning workflow:
  1. Run `java AllTests`.
  2. Run `java AllTests full`.
  3. Run `java SimulationRunner 200000` after major parameter changes.
- Demonstrated that `AllTests full` is about `96.94%` faster than one direct
  200000-step run.
- Demonstrated that two candidate tuning rounds plus one final 200000-step run
  reduce tuning time by about `46.94%` compared with repeatedly running 200000
  steps.

## [1.0] - Stamina, Survival, and Stable Visual Simulation

### Added

- Reworked the original foxes-and-rabbits style simulation into a five-species
  African savanna ecosystem.
- Added five species:
  - Lion
  - Cheetah
  - Zebra
  - Buffalo
  - Gazelle
- Added a species registry and species profiles so behaviour is driven by
  configuration rather than hard-coded species checks.
- Added interface-based ecosystem systems:
  - `WeatherSystem`
  - `DiseaseSystem`
  - `FoodSystem`
  - `PredationSystem`
  - `BreedingSystem`
  - `SimulationRecorder`

### Stamina and Survival

- Added stamina with high, medium, and low stages.
- Added stamina costs for movement, hunting, grazing, and breeding.
- Added daily stamina recovery based on two required factors:
  - food level
  - rest time
- Added survival value derived from `foodLevel / maxFoodLevel`.
- Added survival-pressure behaviour:
  - above `70%`: normal behaviour
  - `30%-70%`: increasing pressure
  - below `30%`: urgent hunting or grazing
- Added temporary `120%` effective stamina behaviour boost when survival is
  critical, without permanently increasing real stamina.
- Changed starvation so animals die only after three consecutive starving
  steps instead of immediately at `foodLevel <= 0`.

### Ecosystem Systems

- Added weather states:
  - Clear
  - Rain
  - Fog
  - Drought
- Linked rain, fog, and drought to infection risk, grass growth, and predator
  visibility.
- Added disease progression, environmental infection, close-contact infection,
  and infection through eating infected prey.
- Added grass growth and grazing for herbivores.
- Added food-chain predation with species-specific prey lists.
- Added stamina-sensitive hunting success and hunting stamina cost.
- Added rare-prey protection through prey-abundance weighting.
- Added male/female breeding with nearby adult mate requirements.
- Added cub, juvenile, and adult life stages.

### Visuals and Reporting

- Added a visual simulation runner with main simulation window.
- Added pause/resume and stop-and-exit controls.
- Simplified grid icons for readability.
- Added live interval record window.
- Added separate live chart window showing:
  - species populations
  - disease count
  - average stamina
  - average survival
  - grass level
- Changed visual refresh and report recording to every `100` steps while still
  calculating every ecological step.
- Added final HTML report generation.

### Stability

- Added `SimulationRunner` for headless long-run validation.
- Tuned the ecosystem to complete `200000` steps without extinction.
- Recorded a validated 200000-step result in
  `200k-stamina-balance-test-summary.txt`.
