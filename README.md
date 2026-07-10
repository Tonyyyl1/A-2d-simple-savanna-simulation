# African Savanna Predator-Prey Simulation

Version: **1.5**

An object-oriented Java and BlueJ simulation of an African savanna ecosystem.
The project extends the classic foxes-and-rabbits predator-prey model into a
five-species ecosystem with weather, disease, grass, food chains, breeding,
stamina, survival pressure, visual output, charts, reports, and a no-dependency
test suite.

## Version Highlights

- **1.5 / shared renderer + status dialog + thirst experiment**:
  ordinary animal marker rendering now goes through shared `FieldRenderer`,
  keeping live view and future snapshot tools on the same `VisualGridGeometry`
  / `VisualFootprint` path. Inspect clicks open an enhanced bilingual
  `AnimalStatusLog` with a summary panel, hydration rows, and recent activity
  data. An experimental thirst/drinking system is wired through
  `SimulationConfig` and `StartupConfigDialog`, but remains **off by default**;
  when enabled, animals drink from passable shoreline cells rather than entering
  `WATERHOLE`.
- **1.4 / context RNG + water probe**: ecological randomness now
  belongs to each `SimulationContext`, so simulator runs no longer depend on a
  shared global random stream. `WaterSafetyProbe` audits ordinary marker
  footprints across multiple panel sizes, including non-integer grid scales.
  Release builds now ship as `releases/<tag>/` with a jar, matching source zip,
  and `MANIFEST.txt`.
- **Earlier Phase 1 / terrain and feedback**: added an independent 2.5D savanna terrain
  background layer and clearer simulation feedback. The map has a deterministic
  waterhole, grassland belt, bush bands, open plain, dry soil, and a seasonal
  lowland corridor. The GUI now separates terrain, animal symbols, weather/time
  overlays, and system metrics. The terminal runner prints readable ecosystem
  snapshots during the run.
- **1.0**: completed the stamina, survival, visual simulation, reporting, and
  long-run stability work.
- **1.1**: added the no-dependency test system, `1000` and `5000` step stability
  gates, and documented the faster tuning workflow.

See [CHANGELOG.md](CHANGELOG.md) for the full version history.

## Species

- **Lion**: predator; hunts gazelles, zebras, and buffalo.
- **Cheetah**: predator; specialises in hunting gazelles.
- **Zebra**: herbivore.
- **Buffalo**: herbivore.
- **Gazelle**: herbivore.

## Main Features

- Five-species savanna ecosystem.
- Weather system with clear weather, rain, fog, and drought.
- Disease system with environmental infection, contact transmission, and
  infection through predation.
- Grass food system where herbivores graze and weather changes plant growth.
- Food-chain predation where hunting depends on prey abundance, weather,
  stamina, and vulnerability.
- Male/female breeding with nearby adult mate requirements.
- Cub, juvenile, and adult life stages.
- Survival value based on `foodLevel / maxFoodLevel`.
- Starvation delay: animals die only after three consecutive starving steps.
- Stamina system for movement, hunting, grazing, breeding, and daily recovery.
- Visual simulation with pause/resume and stop-and-exit controls.
- Live chart window for population, disease, stamina, survival, and grass.
- HTML report generation with interval records every `100` steps.
- Deterministic smooth terrain background, drawn separately from the animal
  layer.
- 2.5D visual feedback with terrain shadows, low-interference terrain labels,
  representative animal symbols, weather/time tinting, and live metric bars for
  grass, disease, survival, and stamina.
- Terminal diagnostics every `100` headless steps so command-line runs show
  population, weather, grass, disease, stamina, survival, starvation, and
  warning signals, plus trend changes and short event readouts since the
  previous diagnostic line.
- Experimental map/disease pressure runner for testing `2x`, `3x`, and `4x`
  linear map scales with configurable creation, founding, breeding,
  transmission, and fatality multipliers.
- Independent viewport interaction controller for mouse-only, keyboard-only,
  and hybrid pan/zoom input. Default keyboard controls are `WASD` pan, `Q`
  zoom in, and `E` zoom out.
- No-dependency test suite with unit, system, and integration checks.
- Headless long-run stability validation.
- Per-run ecological random stream via `SimulationContext.getRandom()`, while
  fixed visual random sources remain separate from simulation behaviour.
- Shared ordinary animal marker renderer via `FieldRenderer`, reusing
  `VisualGridGeometry` and `VisualFootprint`.
- Enhanced bilingual animal status dialog with summary metrics, hydration, and
  thirst-system state.
- Optional experimental thirst/drinking system, disabled by default and
  configurable from the startup dialog.

## Time Model

- `1 step = 1 simulated hour`
- `24 steps = 1 simulated day`

The visual display refreshes every `100` steps for performance. The ecological
model still calculates every single step.

## Visual And Terminal Feedback

Phase 1 adds a visual terrain layer without changing animal behaviour.
`SimulationContext` owns a `TerrainMap`, and `SimulatorView` renders that map as
a cached background image before drawing animals on a transparent animal layer.

The terrain map uses fixed-seed coordinate rules, not random pixel noise. It
contains:

- a rounded waterhole near the left-center of the map,
- a continuous grassland belt around the waterhole,
- bush belts near the waterhole, map edges, and lowland corridor,
- broad open plains through the center and upper-right areas,
- dry soil in the lower and lower-right areas,
- a gently curved seasonal lowland corridor crossing the map.

The renderer uses a 2.5D style: large regions have gradient lighting, shadow
offsets, highlighted borders, terrain-specific texture marks, and subtle
terrain labels. The animal layer draws representative individual symbols: all
predators, infected animals, critical-survival animals, and low-stamina animals
remain visible, while dense ordinary herbivore groups are sampled. Predators
use triangular markers, herbivores use circular markers, infected animals keep
red disease dots, critical-survival animals get an orange ring, and low-stamina
animals get a small stamina bar. Population aggregation is still used only for
sampling dense ordinary animals; it no longer paints large pressure blocks on
the map. Ordinary marker drawing now lives in `FieldRenderer`, while
`SimulatorView` keeps ownership of Swing layers, viewport transforms, heatmaps,
inspect actors, and overlays.

The visual layer also applies weather and time-of-day tinting. A compact
on-map metric panel shows grass, disease, survival, stamina, active signals,
and short trend changes so the current state is visible without reading the
full report. The GUI uses the same `SimulationDiagnostics` snapshot as the
terminal runner, keeping the two readouts consistent.

For headless runs, `SimulationRunner` prints a compact diagnostic line at the
start, every `100` steps, and at the final step. Each line includes population,
predator/prey pressure, grass, disease, average stamina, average survival,
low-survival count, low-stamina count, starving count, trend changes since the
previous line, a short event readout, and high-level signals.

The optional thirst experiment is deliberately conservative and disabled by
default. When enabled, animals lose hydration over time, lose more in drought,
lose less in rain, seek passable shoreline cells when thirsty, and record
`DRINK` events when they drink. The waterhole terrain itself remains
impassable.

## Quick Start

### Run the visual simulation from the JAR

```bash
java -jar savanna-simulation.jar
```

By default this runs `200000` simulation steps.

Run a shorter visual simulation:

```bash
java -jar savanna-simulation.jar 5000
```

The visual version opens:

- the main simulation window,
- a live interval record window,
- a live chart window.

### Run from source

Compile:

```bash
javac *.java
```

Run the visual simulation:

```bash
java VisualSimulationRunner
```

Run a shorter visual simulation:

```bash
java VisualSimulationRunner 5000
```

## Tests and Stability

The project includes a plain Java test suite. It does not require JUnit, Maven,
or Gradle.

Run the default test suite:

```bash
java AllTests
```

This runs:

- unit tests,
- system interaction tests,
- a `1000` step headless stability test.

Latest recorded result:

```text
Passed 180/180 tests in 43830 ms
```

Run the full daily test suite:

```bash
java AllTests full
```

This adds a `5000` step headless stability test.

Current note:

```text
Run after large tuning or behaviour changes; the current default gate is ./test.sh.
```

Run the current water-footprint audit:

```bash
java WaterSafetyProbe 1000 100
```

Latest recorded result:

```text
water animals == 0
ordinary visual water samples == 0
visual water samples == 0
Water safety probe passed through step 1000
```

Run the final long stability validation:

```bash
java SimulationRunner 200000
```

Run the map/disease pressure experiment smoke check:

```bash
java SimulationExperimentRunner smoke
```

Run the full experiment matrix:

```bash
java SimulationExperimentRunner
```

Run the selected balanced 3x candidate:

```bash
java SimulationExperimentRunner best3x
```

Latest default `java SimulationRunner 5000` result:

```text
Lion=358, Cheetah=109, Zebra=618, Buffalo=512, Gazelle=1046
Final total=2643, final balance ratio=9.60, thirst=off
```

Previous validated result:

```text
Completed 200000 steps without extinction.
Final balance ratio: 6.03
Runtime: 1116418 ms
```

## Recommended Tuning Workflow

1. Run `java AllTests`.
2. If it passes, run `java AllTests full`.
3. After major parameter changes, run `java SimulationRunner 200000`.

This layered workflow catches local errors before expensive long runs. Current
timing evidence shows that `AllTests full` is about `96.94%` faster than one
direct 200000-step run, and repeated tuning rounds can reduce overall tuning
time by about `46.94%`.

See [TUNING_EFFICIENCY.md](TUNING_EFFICIENCY.md) for the calculation.

## Visual Controls

The main simulation window includes:

- **Pause**: pauses simulation progress.
- **Resume**: continues after pausing.
- **Stop & Exit**: stops the simulation, writes the final report, and closes
  the application.

Closing the simulation window also writes the final report before exiting.

## Reporting

The visual simulation generates:

- a live text record window,
- a final `savanna-simulation-step-report.html` file.

Records are saved every `100` simulation steps, plus the initial and final
states. Each record includes:

- weather,
- time of day,
- grass level,
- disease count,
- total population,
- per-species population,
- male/female counts,
- cub/juvenile/adult counts,
- infected count,
- average survival,
- average stamina,
- low-survival count,
- starvation counters.

## Architecture

The project separates major ecosystem behaviours into interfaces and
implementation classes:

- `WeatherSystem` / `SeasonalWeatherSystem`
- `DiseaseSystem` / `SavannahDiseaseSystem`
- `FoodSystem` / `GrasslandFoodSystem`
- `PredationSystem` / `FoodChainPredationSystem`
- `BreedingSystem` / `MateFindingBreedingSystem`
- `SimulationRecorder` / `StepReportRecorder`
- `SimulationEventListener` / `EventAccumulator`
- `ThirstSystem` / `SavannahThirstSystem` (experimental, disabled by default)

Species parameters are centralised in `SpeciesRegistry` and `SpeciesProfile`.
Shared animal behaviour is implemented in `SavannahAnimal`.

`SimulationContext.getRecentEvents()` is intentionally capped at the latest
600 events for inspect-mode UI. Whole-run heat maps, replay indexes and long
event summaries should use `SimulationEventListener` with `EventAccumulator`,
or immutable `SimulationSnapshot` frames captured by `SnapshotRecorder`.
The visual window includes a `Heatmap / 热力图` toggle and event-type selector;
the overlay is drawn from accumulated whole-run events, not the 600-event
recent window.

The default visual startup dialog includes an `Experimental thirst system`
checkbox. It is unchecked by default, and reset returns it to off.

## Important Files

- `VisualSimulationRunner.java`: starts the GUI simulation.
- `SimulationRunner.java`: runs headless long-run stability validation.
- `AllTests.java`: runs the no-dependency test suite.
- `Simulator.java`: main simulation loop.
- `SimulatorView.java`: visual grid window.
- `FieldRenderer.java`: shared ordinary animal marker renderer.
- `ThirstSystem.java` / `SavannahThirstSystem.java`: optional shoreline
  drinking and hydration behaviour.
- `SimulationChartWindow.java`: live chart display.
- `StepReportRecorder.java`: interval recording and HTML report generation.
- `EventAccumulator.java`: whole-run event counts for heat maps and summaries.
- `SimulationSnapshot.java`: immutable world snapshot for future replay/timeline tools.
- `SnapshotRecorder.java`: collects immutable snapshots.
- `SpeciesRegistry.java`: species configuration.
- `SavannahAnimal.java`: shared animal behaviour.
- `CHANGELOG.md`: version history.
- `README.zh.md`: Chinese implementation plan and delivery notes for the
  shared renderer, enhanced status dialog, and thirst experiment.
- `TUNING_EFFICIENCY.md`: tuning workflow and timing evidence.
- `UPDATE_NOTES.md`: concise update summary.

## Deliverables

New releases are written to `releases/<version-tag>/`:

- `savanna-simulation-<version-tag>.jar`: runnable visual simulation.
- `savanna-simulation-<version-tag>-source.zip`: source snapshot matching that
  jar build.
- `MANIFEST.txt`: build date, main class, source snapshot, and jar verification
  status.

The older `outputs/` folder remains as historical evidence from previous
delivery rounds, but new jar releases should use `releases/<version-tag>/`.

## Notes

This is an educational simulation rather than a scientifically calibrated
ecological model. The values are tuned to create a stable, explainable
predator-prey ecosystem that can run for long periods without forced species
revival.
