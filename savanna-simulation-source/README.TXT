Project: African Savanna Predator/Prey Simulation

This project extends the original foxes-and-rabbits handout into a five-species
African savanna ecosystem.

Species:
    Lion      - predator
    Cheetah   - predator
    Zebra     - herbivore
    Buffalo   - herbivore
    Gazelle   - herbivore

How to start in BlueJ:
    Create a Simulator object.
    Then call one of:
        + simulateOneStep - for a single step.
        + simulate - and supply a number of steps.
        + runLongSimulation - for 200000 visual steps.

Visual demonstration runner:
    java VisualSimulationRunner
        Opens the simulation GUI and the live step-recording window together.
        By default it runs 200000 steps. To run a shorter visual check, supply
        a number, for example: java VisualSimulationRunner 5000
        When the run ends, it writes savanna-simulation-step-report.html in the
        current project folder.
        The simulation window has Pause/Resume and Stop & Exit buttons. Stop
        writes the final report before closing the application.
        The visual field refreshes every 100 simulation steps for speed while
        the ecological model still calculates every step.

Headless test runners:
    java SimulationRunner
        Runs 200000 steps without the GUI. It exits with an error if any
        species becomes extinct or if the final population balance ratio is
        above 12.0.

    java SimulationRunner 5000
        Runs a shorter headless stability check.

    java StabilityProbe 5000 500
        Runs without the GUI and prints population snapshots every 500 steps.

No-dependency tests:
    javac *.java
    java AllTests
        Runs unit tests, system tests, and a 1000-step headless stability test.

    java AllTests full
        Runs the default tests plus a 5000-step headless stability test.

    Recommended tuning workflow:
        1. Run java AllTests.
        2. If it passes, run java AllTests full.
        3. After major parameter changes, run java SimulationRunner 200000.

    This layered workflow is intended to reduce full tuning time to 80% or less
    of the previous approach by catching local errors before a long 200000-step
    run. See TUNING_EFFICIENCY.md for the latest timing evidence.

Implemented systems:
    Weather:
        Clear, rain, fog, and drought change over time. Rain increases disease
        risk and grass growth, while rain and fog reduce predator hunting range.

    Disease:
        Infection can appear from environmental exposure, spread through close
        contact, and pass from infected prey to predators during predation.
        Young animals have weaker resistance.

    Food chain:
        Lions and cheetahs compete for gazelles. Lions can also hunt zebras and
        buffalo. Rare prey are harder to encounter, so predation pressure shifts
        realistically rather than being hard-coded.

    Survival and stamina:
        Animals no longer die immediately when food reaches zero. They die only
        after three consecutive starving steps. Survival value is derived from
        foodLevel/maxFoodLevel. Above 70% behaviour is normal; from 30% to 70%
        survival pressure rises linearly; below 30% predators hunt urgently and
        herbivores focus on grazing. Stamina has HIGH, MEDIUM, and LOW stages.
        A configurable stamina gap threshold in FoodChainPredationSystem makes
        high-stamina prey harder for low-stamina predators to catch, and makes
        high-stamina predators more efficient against low-stamina prey.

    Breeding:
        Every species has male and female animals. Females breed only if an
        adult male of the same species is within the configured breeding range.
        Breeding responds to grass, weather, crowding, and prey availability.

Graphics:
    Species use distinct colours. Red dots mark disease. The top panel shows
    weather, time of day, grass level, and disease count. Sex and life-stage
    details are recorded in the report rather than drawn on the main grid, so
    the simulation window stays readable.
    A separate chart window shows population, disease, average stamina,
    average survival, and grass trends.

Recording:
    The visual simulator records every 100 steps in a separate window. Each line
    includes weather, time, grass level, disease count, total population, and
    per-species male/female, cub/juvenile/adult, infected, average survival,
    average stamina, low-survival, and starving counts. A detailed HTML document
    is generated automatically when simulate or runLongSimulation finishes. The
    initial and final states are always recorded even when they are not on a
    100-step boundary.
