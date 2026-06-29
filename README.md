# African Savanna Predator-Prey Simulation

Version: **1.1**

An object-oriented Java and BlueJ simulation of an African savanna ecosystem.
The project extends the classic foxes-and-rabbits predator-prey model into a
five-species ecosystem with weather, disease, grass, food chains, breeding,
stamina, survival pressure, visual output, charts, reports, and a no-dependency
test suite.

## Version Highlights

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
- No-dependency test suite with unit, system, and integration checks.
- Headless long-run stability validation.

## Time Model

- `1 step = 1 simulated hour`
- `24 steps = 1 simulated day`

The visual display refreshes every `100` steps for performance. The ecological
model still calculates every single step.

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
Passed 47/47 tests in 5683 ms
```

Run the full daily test suite:

```bash
java AllTests full
```

This adds a `5000` step headless stability test.

Latest recorded result:

```text
Passed 50/50 tests in 34205 ms
```

Run the final long stability validation:

```bash
java SimulationRunner 200000
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

Species parameters are centralised in `SpeciesRegistry` and `SpeciesProfile`.
Shared animal behaviour is implemented in `SavannahAnimal`.

## Important Files

- `VisualSimulationRunner.java`: starts the GUI simulation.
- `SimulationRunner.java`: runs headless long-run stability validation.
- `AllTests.java`: runs the no-dependency test suite.
- `Simulator.java`: main simulation loop.
- `SimulatorView.java`: visual grid window.
- `SimulationChartWindow.java`: live chart display.
- `StepReportRecorder.java`: interval recording and HTML report generation.
- `SpeciesRegistry.java`: species configuration.
- `SavannahAnimal.java`: shared animal behaviour.
- `CHANGELOG.md`: version history.
- `TUNING_EFFICIENCY.md`: tuning workflow and timing evidence.
- `UPDATE_NOTES.md`: concise update summary.

## Deliverables

- `savanna-simulation.jar`: runnable visual simulation.
- `savanna-simulation-source.zip`: source package for GitHub or review.
- `savanna-simulation-project.zip`: BlueJ project package.

## Notes

This is an educational simulation rather than a scientifically calibrated
ecological model. The values are tuned to create a stable, explainable
predator-prey ecosystem that can run for long periods without forced species
revival.

