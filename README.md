# Savanna Simulation v3.3-perf-preview

[中文](README.zh.md)

**Development preview**

S0–S4 performance branch plus sampled audit (2f), using the v3.2 ecology defaults.

A Java simulation of a savanna ecosystem with five animal species, weather, disease, grazing and predator–prey interactions. It extends the BlueJ foxes-and-rabbits teaching example by David J. Barnes and Michael Kölling.

## Run with Java 17 or newer

```sh
java -jar savanna-simulation-v3.3-perf-preview.jar
java -Djava.awt.headless=true -jar savanna-simulation-v3.3-perf-preview.jar --verify-only
```

## Browser preview

```sh
java -Xmx2g -cp savanna-simulation-v3.3-perf-preview.jar savanna.SimServer --no-open
```

Open http://127.0.0.1:8787/. The server binds to the loopback interface.

## Build from source

```sh
BUILD_ID=local-dev ./build.sh
./run.sh
```

Run `./check.sh` for the engineering test suite. Web-enabled snapshots need Node.js for the browser-module tests.
