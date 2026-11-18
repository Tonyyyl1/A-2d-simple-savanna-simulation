# Savanna Simulation v3.2-2d-final

[中文](README.zh.md)

**Stable 2D release**

Five thousand steps accepted with thirst off. Cheetah went extinct at step 10,900 in the historical 20,000-step run. Thirst-on ecology remained open.

A Java simulation of a savanna ecosystem with five animal species, weather, disease, grazing and predator–prey interactions. It extends the BlueJ foxes-and-rabbits teaching example by David J. Barnes and Michael Kölling.

## Run with Java 17 or newer

```sh
java -jar savanna-simulation-v3.2-2d-final.jar
java -Djava.awt.headless=true -jar savanna-simulation-v3.2-2d-final.jar --verify-only
```

## Browser preview

```sh
java -Xmx2g -cp savanna-simulation-v3.2-2d-final.jar savanna.SimServer --no-open
```

Open http://127.0.0.1:8787/. The server binds to the loopback interface.

## Build from source

```sh
BUILD_ID=local-dev ./build.sh
./run.sh
```

Run `./check.sh` for the engineering test suite. Web-enabled snapshots need Node.js for the browser-module tests.

## Versions

- The main branch points to the stable 2D release **v3.2-2d-final**.
- **v3.3-merge-preview** combines P-1 ecology and the performance work.
- Other preview and historical tags retain their own version notes.
