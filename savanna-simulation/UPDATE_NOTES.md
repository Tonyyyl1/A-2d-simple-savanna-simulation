# Update Notes

## Summary

This working update now also includes the shared ordinary-field renderer,
enhanced animal status dialog, and an optional default-off thirst/drinking
experiment. These changes are packaged as release
`v1.5-renderer-status-thirst`.

This working update adds Phase 1 of the terrain and feedback layer: an
independent 2.5D African savanna background, subtle terrain labels, clearer
animal symbols, visual weather/time overlays, live on-map metrics with signals
and short trends, and readable terminal diagnostics with trends and events.
The terrain is generated from deterministic coordinate rules and rendered
separately from the animals, so the ecological model remains unchanged for this
phase.

This update adds a no-dependency testing module for the African Savanna
Predator-Prey Simulation. The new tests are designed for a BlueJ/plain Java
project and do not require JUnit, Maven, or Gradle.

The update also documents a layered tuning workflow that reduces repeated
parameter-tuning time by catching local errors before running the expensive
200000-step stability test.

## Added

- `FieldRenderer.java`
  - Shared ordinary-animal marker renderer for the live view and future
    snapshot/render tools.
  - Reuses `VisualGridGeometry` and `VisualFootprint` for predator/herbivore
    markers, disease dots, survival rings, stamina bars, and thirst drops.
- `ThirstSystem.java` and `SavannahThirstSystem.java`
  - Optional experimental hydration and shoreline-drinking behaviour.
  - Disabled by default.
  - Animals drink only from passable shoreline cells; `WATERHOLE` remains
    impassable.
  - Drinking records `DRINK` events.
- `AnimalStatusLog` summary panel
  - Shows food, survival, stamina, hydration, disease, and risk at the top of
    the dialog.
  - Keeps `rowsFor()` as the testable data layer.
- `README.zh.md`
  - Chinese implementation plan and delivery notes for the shared renderer,
    enhanced status dialog, and thirst experiment.
- `SimulationConfig.java`
  - Lets experiment runs scale map size, starting density, founding
    populations, breeding, disease transmission, and disease fatality without
    changing the baseline simulator defaults.
  - Adds `thirstEnabled`, defaulting to `false`.
- `SimulationExperimentRunner.java`
  - Runs `2x`, `3x`, and `4x` map/disease pressure experiments and prints a
    compact stability table.
  - Includes `best3x` mode. Latest `5000` step result:
    `Lion=299`, `Cheetah=213`, `Zebra=555`, `Buffalo=609`,
    `Gazelle=1014`, density `0.031`, balance ratio `4.76`.
- Independent viewport interaction controls
  - Adds mouse-only, keyboard-only, and hybrid pan/zoom support.
  - Default keyboard controls are `WASD` pan, `Q` zoom in, and `E` zoom out.
- `TerrainMap.java`
  - Fixed-seed terrain generator and 2.5D background renderer.
  - Draws large terrain regions with lighting, shadows, highlighted borders,
    dry-soil cracks, bush clumps, lowland depth, and water highlights.
  - Provides `getTerrainAt(Location)` for future ecological integration.
- `TerrainType.java`
  - Defines waterhole, grassland, bush, open plain, and dry soil terrain
    categories.
- Two-layer field rendering
  - `SimulatorView.FieldView` caches the terrain image separately from the
    transparent animal image.
  - Empty cells no longer cover the terrain background.
- Visual status feedback
  - Predators use triangular markers and herbivores use circular markers.
  - Infected animals, critical-survival animals, and low-stamina animals have
    distinct visual indicators.
  - Dense ordinary herbivore groups are sampled for readability without drawing
    large population-pressure blocks.
  - Weather, dawn/day/dusk/night, grass, disease, survival, stamina, signals,
    and short trends are visible in the main simulation window.
  - The GUI metric panel uses the same `SimulationDiagnostics` snapshot as the
    terminal runner.
- Startup dialog option
  - Adds an `Experimental thirst system` checkbox.
  - Default and reset state are both off.
- `SimulationDiagnostics.java`
  - Builds compact command-line summaries for headless runs.
  - Shows population, grass, disease, and survival trend changes since the
    previous diagnostic line.
  - Adds short event readouts such as population recovery, fog visibility, and
    changing disease or survival pressure.
  - `SimulationRunner` prints these summaries at the start, every `100` steps,
    and at the final step.
- `AllTests.java`
  - Main test entry point.
  - Run with `java AllTests` or `java AllTests full`.
- `TestSupport.java`
  - Minimal assertion and reporting helper.
  - Prints pass/fail results and exits with failure if any test fails.
- `SimulationUnitTests.java`
  - Covers local rules such as time, starvation, survival pressure, stamina,
    and 100-step report intervals.
- `SimulationSystemTests.java`
  - Covers weather modifiers, grazing, disease transmission, stamina effects
    in predation, and mate-required breeding.
- `SimulationIntegrationTests.java`
  - Covers 1000-step and 5000-step headless stability tests.
- `TUNING_EFFICIENCY.md`
  - Documents the recommended tuning workflow and measured timing evidence.

## Test Commands

Compile:

```bash
javac *.java
```

Run the default test suite:

```bash
java AllTests
```

Latest result:

```text
Passed 180/180 tests in 43830 ms
```

Run the current water-footprint audit:

```bash
java WaterSafetyProbe 1000 100
```

Latest result:

```text
water animals == 0
ordinary visual water samples == 0
visual water samples == 0
Water safety probe passed through step 1000
```

Run the 5000-step default-off thirst stability check:

```bash
java SimulationRunner 5000
```

Latest result:

```text
Final population: 2643
Counts: Lion=358, Cheetah=109, Zebra=618, Buffalo=512, Gazelle=1046
Extinction: false
Final balance ratio: 9.60
```

Run the final long stability test:

```bash
java SimulationRunner 200000
```

Previous validated result:

```text
Passed 200000 steps without extinction.
Final balance ratio: 6.03
Runtime: 1116418 ms
```

## Tuning Workflow

Recommended parameter-tuning sequence:

1. Run `java AllTests`.
2. If it passes, run `java AllTests full`.
3. After major parameter changes, run `java SimulationRunner 200000`.

Measured evidence:

- `AllTests full`: `34205 ms`
- `SimulationRunner 200000`: `1116418 ms`
- `AllTests full` is about `96.94%` faster than one direct 200000-step run.
- Two candidate tuning rounds plus one final 200000-step validation reduce time
  by about `46.94%` compared with repeatedly running 200000-step checks.

This exceeds the target of reducing comprehensive tuning time to `80%` or less
of the previous repeated-200k workflow.

## Documentation Updates

- `README.md` now documents:
  - `java AllTests`
  - `java WaterSafetyProbe 1000 100`
  - shared `FieldRenderer`
  - default-off thirst experiment
  - the layered tuning workflow
  - `TUNING_EFFICIENCY.md`
- `README.zh.md` now documents this round's detailed plan and implementation
  notes in Chinese.
- `CLAUDE.md` now marks `FieldRenderer`, enhanced status dialog, and thirst
  experiment as completed in the working tree, with a new release as the next
  suggested step.
- `README.TXT` includes the same command-line testing instructions for BlueJ
  users.
- `package.bluej` now includes `FieldRenderer`, `ThirstSystem`, and
  `SavannahThirstSystem`, so they appear in BlueJ.

## Updated Deliverables

- `savanna-simulation.jar`
- `savanna-simulation-source.zip`
- `savanna-simulation-project.zip`

All deliverables include the new test module and tuning evidence document.

Latest packaged release:

- `releases/v1.5-renderer-status-thirst/savanna-simulation-v1.5-renderer-status-thirst.jar`
- `releases/v1.5-renderer-status-thirst/savanna-simulation-v1.5-renderer-status-thirst-source.zip`
- `releases/v1.5-renderer-status-thirst/MANIFEST.txt`

`./verify-jar.sh v1.5-renderer-status-thirst` passed jar `AllTests 180/180`,
`WaterSafetyProbe 1000 100`, and `WaterSafetyProbe 18500 500`.
