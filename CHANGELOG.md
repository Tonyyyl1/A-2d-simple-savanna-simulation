# Changelog

All notable changes for the African Savanna Predator-Prey Simulation are
documented here.

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

