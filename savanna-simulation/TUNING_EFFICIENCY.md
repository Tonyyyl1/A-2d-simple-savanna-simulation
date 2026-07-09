# Tuning Efficiency Evidence

This project uses a layered tuning workflow to avoid running a 200000-step
stability test after every small parameter change.

## Commands

Recommended workflow:

```bash
javac *.java
java AllTests
java AllTests full
java SimulationRunner 200000
```

Use `java AllTests` after small local changes, `java AllTests full` for a fuller
daily tuning check, and `java SimulationRunner 200000` as the final long-run
validation after major parameter changes.

## Latest Measured Evidence

Measured on the current project state:

- `java AllTests`: `47/47` tests passed in `5683 ms`.
- `java AllTests full`: `50/50` tests passed in `34205 ms`.
- Previous `java SimulationRunner 200000`: passed in `1116418 ms`.

## Reduction Calculation

Comparing the full daily tuning gate with a direct 200000-step run:

```text
AllTests full / 200000-step run
= 34205 / 1116418
= 3.06%
```

So `java AllTests full` is about `96.94%` faster than one direct 200000-step
run while still covering unit tests, system tests, 1000-step stability, and
5000-step stability.

For repeated parameter tuning, assume two candidate tuning rounds and one final
200000-step validation:

```text
Old repeated-200k workflow:
2 * 1116418 = 2232836 ms

Layered workflow:
2 * 34205 + 1116418 = 1184828 ms

Reduction:
1 - (1184828 / 2232836) = 46.94%
```

This exceeds the target of reducing comprehensive tuning time to `80%` or less
of the previous repeated-200k approach.

## What Each Layer Catches

- `AllTests`: local rule errors, subsystem errors, and 1000-step ecosystem
  failures.
- `AllTests full`: the same checks plus a 5000-step stability run.
- `SimulationRunner 200000`: final long-run validation for major parameter
  changes.

