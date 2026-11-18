# Savanna Simulation v3.3-merge-preview

[中文](README.zh.md)

**Development preview**

P-1 herbivore defaults combined with the performance branch. Historical final T3 passed and 4/4 paired equivalence runs matched. Thirst-on ecology and predator tuning remained open.

A Java simulation of a savanna ecosystem with five animal species, weather, disease, grazing and predator–prey interactions. It extends the BlueJ foxes-and-rabbits teaching example by David J. Barnes and Michael Kölling.

## Run with Java 17 or newer

```sh
java -jar savanna-simulation-v3.3-merge-preview.jar
java -Djava.awt.headless=true -jar savanna-simulation-v3.3-merge-preview.jar --verify-only
```

## Browser preview

```sh
java -Xmx2g -cp savanna-simulation-v3.3-merge-preview.jar savanna.SimServer --no-open
```

Open http://127.0.0.1:8787/. The server binds to the loopback interface.

## Build from source

```sh
BUILD_ID=local-dev ./build.sh
./run.sh
```

Run `./check.sh` for the engineering test suite. Web-enabled snapshots need Node.js for the browser-module tests.
