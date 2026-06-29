# African Savanna Predator-Prey Simulation

An object-oriented Java simulation of an African savanna ecosystem, extended from the classic BlueJ foxes-and-rabbits predator-prey model. The project models five animal species, weather, disease, food chains, breeding, stamina, survival pressure, visual output, charts, and interval-based reporting.

## Species

- **Lion**: predator; hunts gazelles, zebras, and buffalo.
- **Cheetah**: predator; specialises in hunting gazelles.
- **Zebra**: herbivore.
- **Buffalo**: herbivore.
- **Gazelle**: herbivore.

## Main Features

- **Five-species ecosystem** with predator and herbivore behaviour.
- **Weather system** with clear weather, rain, fog, and drought.
- **Disease system** with environmental infection, contact transmission, and infection through predation.
- **Food chain system** where predators search for prey and hunting success depends on weather, prey abundance, stamina, and vulnerability.
- **Grass food system** where herbivores graze and weather changes plant growth.
- **Breeding system** with male/female animals, adult mate search, and species-specific reproduction.
- **Life stages** including cub, juvenile, and adult.
- **Survival value** based on `foodLevel / maxFoodLevel`.
- **Starvation delay**: animals die only after three consecutive starving steps.
- **Stamina system** where moving, hunting, grazing, and breeding consume stamina.
- **Visual simulation** with pause/resume and stop/exit controls.
- **Live chart window** showing population, disease, stamina, survival, and grass trends.
- **HTML report generation** with interval records every 100 steps.
- **Headless stability testing** for long simulations without opening the GUI.

## Time Model

The simulation uses a simple time model:

- `1 step = 1 simulated hour`
- `24 steps = 1 simulated day`

The visual display refreshes every 100 steps for performance, but the ecological model still calculates every single step.

## How To Run

### Run the visual simulation from the JAR

```bash
java -jar savanna-simulation.jar
```

By default this runs `200000` simulation steps.

To run a shorter visual test:

```bash
java -jar savanna-simulation.jar 5000
```

The visual version opens:

- the main simulation window,
- a live interval record window,
- a chart window.

### Run from source

Compile all Java files:

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

### Run a headless stability test

```bash
java SimulationRunner
```

This runs `200000` steps without the GUI and fails if a species becomes extinct or if the final population balance ratio is too high.

Run a shorter test:

```bash
java SimulationRunner 1000
```

Print regular population snapshots:

```bash
java StabilityProbe 5000 500
```

### Run the test suite

The project includes a no-dependency test suite, so it can be run without JUnit,
Maven, or Gradle.

```bash
javac *.java
java AllTests
```

`java AllTests` runs unit tests, system tests, and a `1000` step headless
stability test.

For a fuller daily check:

```bash
java AllTests full
```

`java AllTests full` also runs a `5000` step headless stability test. The
`200000` step validation remains available through `java SimulationRunner
200000`.

Recommended tuning workflow:

1. Run `java AllTests`.
2. If it passes, run `java AllTests full`.
3. After major parameter changes, run `java SimulationRunner 200000`.

This layered workflow is intended to reduce full tuning time to 80% or less of
the previous approach by catching local errors before a long 200000-step run.
The latest timing evidence and reduction calculation are documented in
`TUNING_EFFICIENCY.md`.

## Visual Controls

The main simulation window includes:

- **Pause**: pauses simulation progress.
- **Resume**: continues after pausing.
- **Stop & Exit**: stops the simulation, writes the final report, and closes the application.

Closing the simulation window also writes the final report before exiting.

## Reporting

The visual simulation generates:

- a live text record window,
- a final `savanna-simulation-step-report.html` file.

Records are saved every 100 simulation steps, plus the initial and final states. Each record includes:

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

The project separates major ecosystem behaviours into interfaces and implementation classes:

- `WeatherSystem` / `SeasonalWeatherSystem`
- `DiseaseSystem` / `SavannahDiseaseSystem`
- `FoodSystem` / `GrasslandFoodSystem`
- `PredationSystem` / `FoodChainPredationSystem`
- `BreedingSystem` / `MateFindingBreedingSystem`
- `SimulationRecorder` / `StepReportRecorder`

Species parameters are centralised in `SpeciesRegistry` and `SpeciesProfile`, while shared animal behaviour is implemented in `SavannahAnimal`.

## Project Files

- `VisualSimulationRunner.java`: starts the GUI simulation.
- `SimulationRunner.java`: runs long headless stability tests.
- `AllTests.java`: runs the no-dependency test suite.
- `TUNING_EFFICIENCY.md`: documents the layered tuning workflow and timing
  evidence.
- `Simulator.java`: main simulation loop.
- `SimulatorView.java`: visual grid window.
- `SimulationChartWindow.java`: live chart display.
- `StepReportRecorder.java`: interval recording and HTML report generation.
- `SpeciesRegistry.java`: species configuration.
- `SavannahAnimal.java`: shared animal behaviour.

## Notes

This is an educational simulation rather than a scientifically calibrated ecological model. The values are tuned to create a stable, explainable predator-prey ecosystem that can run for long periods without forced species revival.
