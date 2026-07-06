public class HabitatPreferences
{
    private HabitatPreferences() {}

    public static double spawnModifier(SpeciesProfile profile,
                                       TerrainType terrain)
    {
        String species = profile.getName();
        if(SpeciesRegistry.LION.equals(species)) {
            return score(terrain, 0.0, 1.10, 1.65, 0.85, 0.70);
        }
        if(SpeciesRegistry.CHEETAH.equals(species)) {
            return score(terrain, 0.0, 0.95, 0.55, 1.70, 0.80);
        }
        if(SpeciesRegistry.ZEBRA.equals(species)) {
            return score(terrain, 0.0, 1.60, 0.75, 1.10, 0.45);
        }
        if(SpeciesRegistry.BUFFALO.equals(species)) {
            return score(terrain, 0.0, 1.75, 0.95, 0.85, 0.40);
        }
        if(SpeciesRegistry.GAZELLE.equals(species)) {
            return score(terrain, 0.0, 1.30, 0.50, 1.65, 0.65);
        }
        return terrain == TerrainType.WATERHOLE ? 0.0 : 1.0;
    }

    public static double movementModifier(SavannahAnimal animal,
                                          TerrainType terrain,
                                          SimulationContext context)
    {
        double modifier = spawnModifier(animal.getProfile(), terrain);
        if(context.getWeatherSystem().getCurrentWeather() == WeatherType.DROUGHT &&
           !animal.getProfile().isPredator() &&
           terrain == TerrainType.GRASSLAND) {
            modifier *= 1.18;
        }
        return modifier;
    }

    public static double huntingModifier(SavannahAnimal predator,
                                         SavannahAnimal prey,
                                         TerrainType predatorTerrain,
                                         TerrainType preyTerrain)
    {
        String species = predator.getProfile().getName();
        double modifier = 1.0;
        if(SpeciesRegistry.LION.equals(species) &&
           predatorTerrain == TerrainType.BUSH) {
            modifier *= 1.32;
        }
        if(SpeciesRegistry.CHEETAH.equals(species)) {
            if(predatorTerrain == TerrainType.OPEN_PLAIN) {
                modifier *= 1.34;
            }
            if(preyTerrain == TerrainType.BUSH) {
                modifier *= 0.66;
            }
        }
        return modifier;
    }

    private static double score(TerrainType terrain, double waterhole,
                                double grassland, double bush,
                                double openPlain, double drySoil)
    {
        switch(terrain) {
            case WATERHOLE: return waterhole;
            case GRASSLAND: return grassland;
            case BUSH: return bush;
            case OPEN_PLAIN: return openPlain;
            case DRY_SOIL: return drySoil;
            default: return 1.0;
        }
    }
}
