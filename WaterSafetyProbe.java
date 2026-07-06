public class WaterSafetyProbe
{
    private WaterSafetyProbe() {}

    public static void main(String[] args)
    {
        int steps = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        int interval = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        SimulationConfig config = args.length > 2 &&
            "baseline".equalsIgnoreCase(args[2])
                ? SimulationConfig.baseline()
                : SimulationConfig.default3x();
        Simulator simulator = new Simulator(config, false);
        simulator.setVerbose(false);
        auditOrExit("step 0", simulator);
        for(int index = 1; index <= steps && simulator.getField().isViable(); index++) {
            simulator.simulateOneStep();
            if(index % interval == 0 || index == steps) {
                auditOrExit("step " + simulator.getStep(), simulator);
            }
        }
        System.out.println("Water safety probe passed through step " +
                           simulator.getStep() + " using " +
                           config.describe());
    }

    public static int countLivingAnimalsOnWater(Field field, TerrainMap terrain)
    {
        int count = 0;
        for(Animal animal : field.getAnimals()) {
            if(animal.isAlive() && !terrain.isPassable(animal.getLocation())) {
                count++;
                System.out.println("Water animal: " + animal);
            }
        }
        return count;
    }

    private static void auditOrExit(String label, Simulator simulator)
    {
        int waterAnimals = countLivingAnimalsOnWater(simulator.getField(),
            simulator.getContext().getTerrainMap());
        int visualWaterSamples = countVisualActorSamplesOnWater(
            simulator.getField(), simulator.getContext().getTerrainMap(),
            simulator.getContext().getRecentEvents(), 200);
        int ordinaryVisualWaterSamples = countOrdinaryVisualSamplesOnWater(
            simulator.getField(), simulator.getContext().getTerrainMap());
        System.out.println(label + " water animals == " + waterAnimals);
        System.out.println(label + " ordinary visual water samples == " +
                           ordinaryVisualWaterSamples);
        System.out.println(label + " visual water samples == " +
                           visualWaterSamples);
        if(waterAnimals != 0 || ordinaryVisualWaterSamples != 0 ||
           visualWaterSamples != 0) {
            System.exit(1);
        }
    }

    /**
     * Audit the ordinary marker layer at several panel sizes. Sizes that are
     * not an exact multiple of the grid matter most: the old integer-scale
     * animal layer only drifted onto the painted water at those sizes.
     */
    public static int countOrdinaryVisualSamplesOnWater(Field field,
                                                        TerrainMap terrain)
    {
        int[][] panelSizes = {
            {field.getWidth() * 4, field.getDepth() * 4},
            {1387, 829},
            {1093, 731},
            {640, 480}
        };
        int count = 0;
        for(int[] panel : panelSizes) {
            count += countOrdinaryVisualSamplesOnWater(field, terrain,
                                                       panel[0], panel[1]);
        }
        return count;
    }

    public static int countOrdinaryVisualSamplesOnWater(Field field,
                                                        TerrainMap terrain,
                                                        int panelWidth,
                                                        int panelHeight)
    {
        VisualWaterMask mask = new VisualWaterMask(terrain, panelWidth,
                                                   panelHeight);
        int aggregateCellSize = 8;
        int aggregateColumns = (field.getWidth() + aggregateCellSize - 1) /
                               aggregateCellSize;
        int aggregateRows = (field.getDepth() + aggregateCellSize - 1) /
                            aggregateCellSize;
        int[][] aggregatePopulation = new int[aggregateRows][aggregateColumns];
        for(Animal animal : field.getAnimals()) {
            if(animal instanceof SavannahAnimal && animal.isAlive()) {
                Location location = animal.getLocation();
                aggregatePopulation[aggregateIndex(location.row(),
                    aggregateRows, aggregateCellSize)]
                    [aggregateIndex(location.col(), aggregateColumns,
                    aggregateCellSize)]++;
            }
        }

        int count = 0;
        for(Animal animal : field.getAnimals()) {
            if(!(animal instanceof SavannahAnimal) || !animal.isAlive()) {
                continue;
            }
            SavannahAnimal savannahAnimal = (SavannahAnimal)animal;
            Location location = savannahAnimal.getLocation();
            if(!terrain.isPassable(location) ||
               !shouldDrawOrdinaryAnimal(savannahAnimal, location,
                   aggregatePopulation, aggregateRows, aggregateColumns,
                   aggregateCellSize)) {
                continue;
            }
            if(mask.ordinaryAnimalFootprintTouchesWater(location)) {
                // The view hides this shoreline marker; nothing is painted.
                continue;
            }
            if(paintedFootprintTouchesWater(mask, terrain, location,
                                            panelWidth, panelHeight)) {
                count++;
                System.out.println("Ordinary water marker: " +
                    savannahAnimal.getProfile().getName() +
                    " at " + location + " panel " + panelWidth + "x" +
                    panelHeight);
            }
        }
        return count;
    }

    /**
     * Re-derive the drawn marker footprint from the shared geometry helper
     * and verify every pixel of it against the painted water surface.
     */
    private static boolean paintedFootprintTouchesWater(VisualWaterMask mask,
                                                        TerrainMap terrain,
                                                        Location location,
                                                        int panelWidth,
                                                        int panelHeight)
    {
        double cellWidth = (double)panelWidth / terrain.getWidth();
        double cellHeight = (double)panelHeight / terrain.getDepth();
        java.awt.Rectangle symbol = VisualWaterMask.markerBounds(
            location.row(), location.col(), cellWidth, cellHeight);
        int margin = VisualWaterMask.DECORATION_MARGIN_PIXELS;
        for(int y = symbol.y - margin; y <= symbol.y + symbol.height + margin;
            y++) {
            for(int x = symbol.x - margin;
                x <= symbol.x + symbol.width + margin; x++) {
                if(mask.isWaterPixel(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean shouldDrawOrdinaryAnimal(SavannahAnimal animal,
                                                    Location location,
                                                    int[][] aggregatePopulation,
                                                    int aggregateRows,
                                                    int aggregateColumns,
                                                    int aggregateCellSize)
    {
        int aggregateRow = aggregateIndex(location.row(), aggregateRows,
                                          aggregateCellSize);
        int aggregateColumn = aggregateIndex(location.col(), aggregateColumns,
                                             aggregateCellSize);
        int population = aggregatePopulation[aggregateRow][aggregateColumn];
        if(population <= 5 || animal.getProfile().isPredator() ||
           animal.isInfected() || animal.isSurvivalCritical() ||
           animal.getStaminaStage() == StaminaStage.LOW) {
            return true;
        }
        int spacing = Math.min(7, Math.max(2, population / 4));
        return Math.abs(location.col() * 31 + location.row() * 17) %
               spacing == 0;
    }

    private static int aggregateIndex(int value, int max, int aggregateCellSize)
    {
        return Math.max(0, Math.min(max - 1, value / aggregateCellSize));
    }

    public static int countVisualActorSamplesOnWater(Field field,
                                                     TerrainMap terrain,
                                                     java.util.List<SimulationEvent> events,
                                                     int sampleMs)
    {
        java.awt.Rectangle fullViewport = new java.awt.Rectangle(0, 0,
            field.getWidth(), field.getDepth());
        int count = 0;
        java.util.List<SceneDirector.SceneActor> ambient =
            SceneDirector.buildBehaviorActors(events, field, terrain,
                                             fullViewport);
        count += countActorSamplesOnWater(ambient, terrain,
            SceneDirector.AMBIENT_DURATION_MS, sampleMs);
        for(SceneDirector.Scene scene : SceneDirector.buildScene(events, field,
            terrain, fullViewport)) {
            count += countActorSamplesOnWater(scene.actors, terrain,
                scene.durationMs, sampleMs);
        }
        return count;
    }

    private static int countActorSamplesOnWater(
        java.util.List<SceneDirector.SceneActor> actors,
        TerrainMap terrain, double durationMs, int sampleMs)
    {
        int count = 0;
        int safeSampleMs = Math.max(1, sampleMs);
        for(SceneDirector.SceneActor actor : actors) {
            for(int time = 0; time <= durationMs; time += safeSampleMs) {
                SceneDirector.Keyframe frame = actor.at(time);
                if(frame.alpha > 0.01 &&
                   SceneDirector.visualFootprintTouchesWater(frame.row,
                                                             frame.col,
                                                             terrain)) {
                    count++;
                    System.out.println("Visual water actor: " + actor.species +
                        " id=" + actor.animalId + " t=" + time +
                        " row=" + frame.row + " col=" + frame.col);
                }
            }
        }
        return count;
    }
}
