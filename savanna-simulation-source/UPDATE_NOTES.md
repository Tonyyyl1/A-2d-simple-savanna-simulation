# Update Notes

## Summary

This update adds a no-dependency testing module for the African Savanna
Predator-Prey Simulation. The new tests are designed for a BlueJ/plain Java
project and do not require JUnit, Maven, or Gradle.

The update also documents a layered tuning workflow that reduces repeated
parameter-tuning time by catching local errors before running the expensive
200000-step stability test.

## Added

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
Passed 47/47 tests in 5683 ms
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

