import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Experimental thirst system. Animals drink from passable shoreline cells next
 * to waterholes; waterhole terrain itself remains impassable.
 */
public class SavannahThirstSystem implements ThirstSystem
{
    private static final double BASE_DRAIN = 0.24;
    private static final double THIRST_THRESHOLD = 35.0;
    private static final double CRITICAL_THRESHOLD = 12.0;
    private static final int DEHYDRATION_LIMIT = 18;

    private final TerrainMap terrain;
    private final int[][] shoreDistance;

    public SavannahThirstSystem(TerrainMap terrain)
    {
        this.terrain = terrain;
        shoreDistance = computeShoreDistances(terrain);
    }

    public void applyThirst(SavannahAnimal animal, SimulationContext context)
    {
        if(animal == null || !animal.isAlive()) {
            return;
        }

        double drain = BASE_DRAIN;
        WeatherType weather = context.getWeatherSystem().getCurrentWeather();
        if(weather == WeatherType.DROUGHT) {
            drain *= 1.65;
        }
        else if(weather == WeatherType.RAIN) {
            drain *= 0.55;
        }
        if(animal.getProfile().isPredator()) {
            drain *= 1.10;
        }
        animal.drainHydration(drain);

        if(animal.getHydrationPercent() <= CRITICAL_THRESHOLD) {
            animal.spendStamina(0.18);
        }
        if(animal.getHydrationPercent() <= 0.0) {
            animal.addDehydrationStep();
            if(animal.getDehydrationSteps() >= DEHYDRATION_LIMIT) {
                animal.dehydrateOneFood();
            }
        }
    }

    public boolean needsWater(SavannahAnimal animal)
    {
        return animal != null && animal.getHydrationPercent() < THIRST_THRESHOLD;
    }

    public boolean tryDrink(SavannahAnimal animal, SimulationContext context)
    {
        Location location = animal == null ? null : animal.getLocation();
        if(!isDrinkableShore(location)) {
            return false;
        }
        animal.rehydrate();
        animal.spendStamina(SavannahAnimal.STAMINA_GRAZE_COST);
        context.recordEvent(SimulationEvent.drink(context.getStep(), animal,
                                                  location));
        return true;
    }

    public Location stepTowardWater(SavannahAnimal animal,
                                    List<Location> freeLocations)
    {
        if(freeLocations == null || freeLocations.isEmpty()) {
            return null;
        }

        Location best = null;
        int bestDistance = Integer.MAX_VALUE;
        for(Location location : freeLocations) {
            int distance = distanceToShore(location);
            if(distance < bestDistance) {
                best = location;
                bestDistance = distance;
            }
        }
        return best;
    }

    public boolean isDrinkableShore(Location location)
    {
        return location != null &&
               terrain.isPassable(location) &&
               distanceToShore(location) == 0;
    }

    private int distanceToShore(Location location)
    {
        if(location == null ||
           location.row() < 0 || location.row() >= terrain.getDepth() ||
           location.col() < 0 || location.col() >= terrain.getWidth()) {
            return Integer.MAX_VALUE;
        }
        return shoreDistance[location.row()][location.col()];
    }

    private int[][] computeShoreDistances(TerrainMap terrain)
    {
        int[][] distances = new int[terrain.getDepth()][terrain.getWidth()];
        Queue<Location> queue = new LinkedList<>();
        for(int row = 0; row < terrain.getDepth(); row++) {
            for(int col = 0; col < terrain.getWidth(); col++) {
                distances[row][col] = Integer.MAX_VALUE;
                Location location = new Location(row, col);
                if(terrain.isPassable(location) && touchesWater(location)) {
                    distances[row][col] = 0;
                    queue.add(location);
                }
            }
        }

        while(!queue.isEmpty()) {
            Location current = queue.remove();
            int nextDistance = distances[current.row()][current.col()] + 1;
            for(int dr = -1; dr <= 1; dr++) {
                for(int dc = -1; dc <= 1; dc++) {
                    if(dr == 0 && dc == 0) {
                        continue;
                    }
                    Location next = new Location(current.row() + dr,
                                                 current.col() + dc);
                    if(inBounds(next) && terrain.isPassable(next) &&
                       distances[next.row()][next.col()] > nextDistance) {
                        distances[next.row()][next.col()] = nextDistance;
                        queue.add(next);
                    }
                }
            }
        }
        return distances;
    }

    private boolean touchesWater(Location location)
    {
        for(int dr = -1; dr <= 1; dr++) {
            for(int dc = -1; dc <= 1; dc++) {
                if(dr == 0 && dc == 0) {
                    continue;
                }
                Location next = new Location(location.row() + dr,
                                             location.col() + dc);
                if(inBounds(next) &&
                   terrain.getTerrainAt(next) == TerrainType.WATERHOLE) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean inBounds(Location location)
    {
        return location.row() >= 0 && location.row() < terrain.getDepth() &&
               location.col() >= 0 && location.col() < terrain.getWidth();
    }
}
