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
        above 12.0. It prints a readable ecosystem summary at the start, every
        100 steps, and at the final step, including weather, time, population,
        predator/prey pressure, grass, disease, average stamina, average
        survival, starvation pressure, trend changes, event readouts, and
        high-level signals.

    java SimulationRunner 5000
        Runs a shorter headless stability check.

    java SimulationExperimentRunner smoke
        Runs a quick map/disease pressure experiment across 2x, 3x, and 4x
        map scales.

    java SimulationExperimentRunner
        Runs the full map/disease pressure matrix. This can take much longer
        because large maps grow grass across many more cells.

    java SimulationExperimentRunner best3x
        Runs the selected 3x balanced candidate. Latest 5000-step result:
        Lion=299, Cheetah=213, Zebra=555, Buffalo=609, Gazelle=1014.
        Final total=2690, density=0.031, balance ratio=4.76.

    java StabilityProbe 5000 500
        Runs without the GUI and prints population snapshots every 500 steps.

No-dependency tests:
    javac *.java
    java AllTests
        Runs unit tests, system tests, and a 1000-step headless stability test.
        Latest working-tree result: 180/180 tests passed.

    java AllTests full
        Runs the default tests plus a 5000-step headless stability test.

    java WaterSafetyProbe 1000 100
        Audits real animal locations, ordinary marker footprints, and inspect
        actor footprints against the waterhole. All three printed counts must
        stay 0.

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

    Optional thirst experiment:
        The thirst/drinking system is wired in but turned off by default.
        It can be enabled from the startup dialog with the Experimental thirst
        system checkbox. When enabled, animals lose hydration over time,
        especially in drought, and thirsty animals seek passable shoreline
        cells to drink. The waterhole remains impassable; animals do not stand
        inside WATERHOLE cells. Drinking records DRINK events.

    Breeding:
        Every species has male and female animals. Females breed only if an
        adult male of the same species is within the configured breeding range.
        Breeding responds to grass, weather, crowding, and prey availability.

Graphics:
    Species use distinct colours. The simulation now draws a deterministic
    2.5D terrain background before drawing animals on a separate transparent
    animal layer. The terrain layer includes a left-center waterhole,
    surrounding grassland, bush belts, open plains, dry soil, and a seasonal
    lowland corridor. Large regions use shadows, highlighted borders,
    terrain-specific texture, and subtle labels so the map reads as distinct
    ecological zones.

    Animals are no longer full-cell blocks. Predators are triangular markers
    and herbivores are circular markers. Red dots mark disease, orange rings
    mark critical survival pressure, and small bars mark low stamina. Ordinary
    dense herbivore groups are sampled so the field stays readable, but the map
    no longer paints large population-pressure blocks. Ordinary animal marker
    drawing now goes through FieldRenderer, a shared renderer that reuses
    VisualGridGeometry and VisualFootprint. This keeps the live view aligned
    with future snapshot/render tools and the water-safety probe.

    Weather and time of day now tint the main field, and a compact on-map panel
    shows grass, disease, average survival, average stamina, active signals,
    and short trend changes. The panel uses the same SimulationDiagnostics
    snapshot as the terminal runner. Phase 1 keeps this terrain visual only;
    grass, movement, disease, predation, and breeding do not read terrain
    modifiers yet.
    The top panel shows weather, time of day, grass level, and disease count.
    Sex and life-stage details are recorded in the report rather than drawn on
    the main grid, so the simulation window stays readable.
    A separate chart window shows population, disease, average stamina,
    average survival, and grass trends.

Inspect/status:
    Zoomed inspect mode opens AnimalStatusLog when an animal is clicked. The
    dialog now has a summary panel plus the original testable table rows. It
    shows food, survival, stamina, hydration, disease, risk, terrain, weather,
    recent activity, and whether the thirst system is enabled.

Viewport controls:
    The GUI now uses an independent interaction controller. Mouse wheel zooms,
    mouse drag pans, and the default keyboard controls are WASD for panning,
    Q to zoom in, and E to zoom out. The controller supports mouse-only,
    keyboard-only, and hybrid modes for later UI expansion.

Recording:
    The visual simulator records every 100 steps in a separate window. Each line
    includes weather, time, grass level, disease count, total population, and
    per-species male/female, cub/juvenile/adult, infected, average survival,
    average stamina, low-survival, and starving counts. A detailed HTML document
    is generated automatically when simulate or runLongSimulation finishes. The
    initial and final states are always recorded even when they are not on a
    100-step boundary.

Current working-tree verification:
    ./test.sh
        180/180 tests passed.

    java WaterSafetyProbe 1000 100
        water animals == 0
        ordinary visual water samples == 0
        visual water samples == 0

    java SimulationRunner 5000
        Default thirst=off result matches the existing context-RNG baseline
        ecology, apart from the added config text and elapsed time.
        Final counts: Lion=358, Cheetah=109, Zebra=618, Buffalo=512,
        Gazelle=1046. Extinction=false. Final balance ratio=9.60.

Documentation:
    README.md      - main project overview.
    README.zh.md   - Chinese plan and delivery notes for FieldRenderer,
                     enhanced status dialog, and thirst experiment.
    CHANGELOG.md   - version history.
    CLAUDE.md      - maintainer/agent handoff notes.
