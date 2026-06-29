# Update Notes

## Summary

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
Passed 65/65 tests in 5744 ms
```

Run the full daily test suite:

```bash
java AllTests full
```

Latest result:

```text
Passed 50/50 tests in 34205 ms
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
  - `java AllTests full`
  - the layered tuning workflow
  - `TUNING_EFFICIENCY.md`
- `README.TXT` includes the same command-line testing instructions for BlueJ
  users.
- `package.bluej` now includes the test classes, so they appear in BlueJ.

## Updated Deliverables

- `savanna-simulation.jar`
- `savanna-simulation-source.zip`
- `savanna-simulation-project.zip`

All deliverables include the new test module and tuning evidence document.
