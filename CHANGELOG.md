# Changelog

All notable changes for the African Savanna Predator-Prey Simulation are
documented here.

## [Unreleased] - v1.4 3x Startup, Water Safety, Habitat Tuning, Context RNG, Shared Renderer, And Thirst Experiment

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
