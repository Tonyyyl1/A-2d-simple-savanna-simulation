# Savanna Simulation v2.7-r2-e02-preview

[中文](README.zh.md)

**Development preview**

October 16 worktree after R2-E02. R2-E03 and formal ecology verification remained open.

A Java simulation of a savanna ecosystem with five animal species, weather, disease, grazing and predator–prey interactions. It extends the BlueJ foxes-and-rabbits teaching example by David J. Barnes and Michael Kölling.

## Run with Java 17 or newer

```sh
java -jar savanna-simulation-v2.7-r2-e02-preview.jar
java -Djava.awt.headless=true -cp savanna-simulation-v2.7-r2-e02-preview.jar savanna.SimulationRunner 3
```

## Build from source

```sh
BUILD_ID=local-dev ./build.sh
./run.sh
```
