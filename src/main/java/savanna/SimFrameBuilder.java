package savanna;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link SimFrame}s from the committed state (plan v3.2 V1). It keeps the
 * previous frame's positions and flags so the next frame can carry "where this
 * animal was". Read-only with respect to the kernel: it reads the field,
 * the context and the step's event list and draws no random numbers.
 */
final class SimFrameBuilder
{
    /** Quantized grass is sent on frame 0 and every this many steps. */
    static final int GRASS_EVERY_STEPS = 6;

    private Map<Long, int[]> previousCells = new HashMap<>();
    private Map<Long, Byte> previousFlags = new HashMap<>();

    void reset()
    {
        previousCells = new HashMap<>();
        previousFlags = new HashMap<>();
    }

    SimFrame build(int generation, int sequence, int step, SimulationContext context,
                   Field field, boolean last)
    {
        TerrainMap terrain = context.getTerrainMap();
        List<SavannahAnimal> animals = new ArrayList<>();
        for(Animal animal : field.getAnimals()) {
            if(animal instanceof SavannahAnimal && animal.isAlive() && animal.getLocation() != null) {
                animals.add((SavannahAnimal)animal);
            }
        }
        animals.sort((first, second) -> Long.compare(first.getId(), second.getId()));

        Map<Long, Long> motherOf = new HashMap<>();
        List<long[]> births = new ArrayList<>();
        List<long[]> kills = new ArrayList<>();
        List<int[]> killCells = new ArrayList<>();
        List<long[]> deaths = new ArrayList<>();
        List<int[]> deathMeta = new ArrayList<>();
        for(SimulationEvent event : context.getCurrentEvents()) {
            switch(event.type) {
                case BIRTH:
                    births.add(new long[] {event.actorId, event.targetId});
                    motherOf.put(event.targetId, event.actorId);
                    break;
                case HUNT:
                    kills.add(new long[] {event.actorId, event.targetId});
                    killCells.add(new int[] {event.col, event.row});
                    break;
                case DEATH:
                case DISEASE_DEATH:
                    deaths.add(new long[] {event.actorId});
                    deathMeta.add(new int[] {event.col, event.row,
                        speciesCode(event.actorSpecies),
                        causeCode(event.type == SimulationEvent.EventType.DISEASE_DEATH
                            ? DeathCause.DISEASE : event.deathCause)});
                    break;
                default:
                    break;
            }
        }

        int count = animals.size();
        SimFrame.Builder b = new SimFrame.Builder();
        b.generation = generation;
        b.sequence = sequence;
        b.step = step;
        b.hour = context.getClock().getHour();
        b.phase = phaseCode(context.getClock().getPhase());
        b.weather = weatherCode(context.getWeatherSystem().getCurrentWeather());
        boolean withGrass = sequence == 0 || step % GRASS_EVERY_STEPS == 0;
        b.flags = (context.isThirstEnabled() ? SimFrame.FLAG_THIRST : 0) |
            (withGrass && context.getFoodSystem() instanceof GrasslandFoodSystem
                ? SimFrame.FLAG_GRASS : 0) | (last ? SimFrame.FLAG_LAST : 0);
        b.id = new long[count];
        b.species = new byte[count];
        b.animalFlags = new byte[count];
        b.col = new short[count];
        b.row = new short[count];
        b.prevCol = new short[count];
        b.prevRow = new short[count];
        b.action = new byte[count];
        b.reason = new byte[count];
        b.effects = new byte[count];
        b.moveResult = new byte[count];
        b.age1000 = new short[count];
        b.food = new byte[count];
        b.stamina = new byte[count];
        b.water = new byte[count];
        b.pathLength = new byte[count];
        b.target = new long[count];
        List<Short> pathCols = new ArrayList<>();
        List<Short> pathRows = new ArrayList<>();
        int teleports = 0;
        Map<Long, int[]> cells = new HashMap<>();
        Map<Long, Byte> flagsNow = new HashMap<>();
        for(int index = 0; index < count; index++) {
            SavannahAnimal animal = animals.get(index);
            Location here = animal.getLocation();
            int speciesCode = speciesCode(animal.getProfile().getName());
            b.speciesCounts[speciesCode]++;
            b.id[index] = animal.getId();
            b.species[index] = (byte)speciesCode;
            int flags = flagsOf(animal);
            b.animalFlags[index] = (byte)flags;
            b.col[index] = (short)here.col();
            b.row[index] = (short)here.row();
            int[] before = previousCells.get(animal.getId());
            if(before == null) {
                Long mother = motherOf.get(animal.getId());
                before = mother == null ? null : previousCells.get(mother);
            }
            Location from = before == null ? here : new Location(before[0], before[1]);
            b.prevCol[index] = (short)from.col();
            b.prevRow[index] = (short)from.row();
            BehaviorOutcome outcome = animal.getLastBehaviorOutcome();
            b.action[index] = (byte)actionCode(animal.getLastBehaviorAction());
            b.reason[index] = (byte)reasonCode(animal.getLastBehaviorReason());
            b.effects[index] = (byte)(outcome == null ? 0 : effectBits(outcome.getExecutedEffects()));
            b.moveResult[index] = (byte)(outcome == null ? 0
                : moveCode(outcome.getMovementDisposition()));
            int maxAge = Math.max(1, animal.getProfile().getMaxAge());
            b.age1000[index] = (short)Math.max(0, Math.min(1000,
                Math.round(1000.0f * animal.getAge() / maxAge)));
            b.food[index] = (byte)percent(100.0 * Math.max(0, animal.getFoodLevel()) /
                Math.max(1, animal.getProfile().getMaxFoodLevel()));
            b.stamina[index] = (byte)percent(animal.getStaminaPercent());
            b.water[index] = (byte)percent(animal.getHydrationPercent());
            List<Location> path = MovePathResolver.between(terrain, from, here);
            if(path == null) {
                teleports++;
                path = java.util.Collections.emptyList();
            }
            b.pathLength[index] = (byte)Math.min(5, path.size());
            for(int step2 = 0; step2 < Math.min(5, path.size()); step2++) {
                pathCols.add((short)path.get(step2).col());
                pathRows.add((short)path.get(step2).row());
            }
            long targetId = 0L;
            if(animal.getLastBehaviorAction() == ActionType.HUNT) {
                long hunted = animal.getProductionTarget().getTargetAnimalId();
                if(hunted > 0L) targetId = hunted;
            }
            else if(animal.getLifeStage() != LifeStage.ADULT && animal.getMotherId() > 0L) {
                targetId = animal.getMotherId();
            }
            b.target[index] = targetId;
            cells.put(animal.getId(), new int[] {here.row(), here.col()});
            flagsNow.put(animal.getId(), (byte)flags);
        }
        b.teleportCount = teleports;
        b.pathCol = toShorts(pathCols);
        b.pathRow = toShorts(pathRows);

        b.killPredator = new long[kills.size()];
        b.killPrey = new long[kills.size()];
        b.killCol = new short[kills.size()];
        b.killRow = new short[kills.size()];
        for(int index = 0; index < kills.size(); index++) {
            b.killPredator[index] = kills.get(index)[0];
            b.killPrey[index] = kills.get(index)[1];
            b.killCol[index] = (short)killCells.get(index)[0];
            b.killRow[index] = (short)killCells.get(index)[1];
        }
        b.birthMother = new long[births.size()];
        b.birthChild = new long[births.size()];
        for(int index = 0; index < births.size(); index++) {
            b.birthMother[index] = births.get(index)[0];
            b.birthChild[index] = births.get(index)[1];
        }
        b.deathId = new long[deaths.size()];
        b.deathCol = new short[deaths.size()];
        b.deathRow = new short[deaths.size()];
        b.deathSpecies = new byte[deaths.size()];
        b.deathCause = new byte[deaths.size()];
        b.deathFlags = new byte[deaths.size()];
        for(int index = 0; index < deaths.size(); index++) {
            long deadId = deaths.get(index)[0];
            int[] meta = deathMeta.get(index);
            b.deathId[index] = deadId;
            b.deathCol[index] = (short)Math.max(0, meta[0]);
            b.deathRow[index] = (short)Math.max(0, meta[1]);
            b.deathSpecies[index] = (byte)meta[2];
            b.deathCause[index] = (byte)meta[3];
            Byte known = previousFlags.get(deadId);
            b.deathFlags[index] = (byte)(known == null ? 0 : (known & 0x07));
        }
        if((b.flags & SimFrame.FLAG_GRASS) != 0) {
            b.grass = ((GrasslandFoodSystem)context.getFoodSystem()).quantizedFill();
        }
        previousCells = cells;
        previousFlags = flagsNow;
        return b.build();
    }

    private static short[] toShorts(List<Short> list)
    {
        short[] result = new short[list.size()];
        for(int index = 0; index < result.length; index++) result[index] = list.get(index);
        return result;
    }

    private static int percent(double value)
    {
        return (int)Math.max(0, Math.min(100, Math.round(value)));
    }

    /** bit0 male; bit1-2 life stage; bit3 infected; bit4 thirsty; bit5 critical; bit6 pregnant; bit7 resting. */
    private static int flagsOf(SavannahAnimal animal)
    {
        BehaviorOutcome outcome = animal.getLastBehaviorOutcome();
        int flags = animal.getSex() == Sex.MALE ? 1 : 0;
        flags |= lifeStageCode(animal.getLifeStage()) << 1;
        if(animal.isInfected()) flags |= 1 << 3;
        if(animal.isThirsty()) flags |= 1 << 4;
        if(animal.isSurvivalCritical()) flags |= 1 << 5;
        if(animal.getReproductionState().isPregnant()) flags |= 1 << 6;
        if(outcome != null && outcome.receivesRestCredit()) flags |= 1 << 7;
        return flags;
    }

    // ---- explicit protocol code tables (never ordinal()) ----

    static int speciesCode(String name)
    {
        if(SpeciesRegistry.LION.equals(name)) return 0;
        if(SpeciesRegistry.CHEETAH.equals(name)) return 1;
        if(SpeciesRegistry.ZEBRA.equals(name)) return 2;
        if(SpeciesRegistry.BUFFALO.equals(name)) return 3;
        if(SpeciesRegistry.GAZELLE.equals(name)) return 4;
        throw new IllegalArgumentException("no protocol code for species " + name);
    }

    static int lifeStageCode(LifeStage stage)
    {
        switch(stage) {
            case CUB: return 0;
            case JUVENILE: return 1;
            case ADULT: return 2;
            default: throw new IllegalArgumentException("no protocol code for " + stage);
        }
    }

    static int phaseCode(DayPhase phase)
    {
        switch(phase) {
            case DAWN: return 0;
            case DAY: return 1;
            case DUSK: return 2;
            case NIGHT: return 3;
            default: throw new IllegalArgumentException("no protocol code for " + phase);
        }
    }

    static int weatherCode(WeatherType weather)
    {
        switch(weather) {
            case CLEAR: return 0;
            case RAIN: return 1;
            case FOG: return 2;
            case DROUGHT: return 3;
            default: throw new IllegalArgumentException("no protocol code for " + weather);
        }
    }

    static int actionCode(ActionType action)
    {
        if(action == null) return 0;
        switch(action) {
            case DRINK: return 1;
            case SEEK_WATER: return 2;
            case HUNT: return 3;
            case GRAZE: return 4;
            case REST: return 5;
            case BREED: return 6;
            case MOVE: return 7;
            case WAIT: return 8;
            default: throw new IllegalArgumentException("no protocol code for " + action);
        }
    }

    /** Declaration order of BehaviorReason at protocol v1; new constants may only be appended. */
    static int reasonCode(BehaviorReason reason)
    {
        if(reason == null) return 0;
        switch(reason) {
            case CRITICAL_DEHYDRATION: return 1;
            case WATER_DEPARTURE: return 2;
            case CRITICAL_FOOD: return 3;
            case THREAT_AVOIDANCE: return 4;
            case EGRESS: return 5;
            case THIRST: return 6;
            case FOOD: return 7;
            case REST: return 8;
            case ILLNESS_RECOVERY: return 9;
            case BLOCKED: return 10;
            case BREEDING: return 11;
            case ORDINARY_MOVEMENT: return 12;
            default: throw new IllegalArgumentException("no protocol code for " + reason);
        }
    }

    static int effectBits(List<ExecutedEffect> effects)
    {
        int bits = 0;
        for(ExecutedEffect effect : effects) {
            switch(effect) {
                case DRINK: bits |= 1; break;
                case HUNT: bits |= 2; break;
                case GRAZE: bits |= 4; break;
                case BREED: bits |= 8; break;
                case MOVE: bits |= 16; break;
                case REST: bits |= 32; break;
                case WAIT: bits |= 64; break;
                default: throw new IllegalArgumentException("no protocol bit for " + effect);
            }
        }
        return bits;
    }

    static int moveCode(MovementDisposition disposition)
    {
        switch(disposition) {
            case NOT_ATTEMPTED: return 0;
            case MOVED: return 1;
            case STAYED: return 2;
            case REROUTED: return 3;
            case BLOCKED: return 4;
            case CONFLICT_LOST: return 5;
            default: throw new IllegalArgumentException("no protocol code for " + disposition);
        }
    }

    static int causeCode(DeathCause cause)
    {
        if(cause == null) return 0;
        switch(cause) {
            case NONE: return 0;
            case AGE: return 1;
            case STARVATION: return 2;
            case DEHYDRATION: return 3;
            case DISEASE: return 4;
            case PREDATION: return 5;
            case POSITION_CONFLICT: return 6;
            case UNKNOWN: return 7;
            default: throw new IllegalArgumentException("no protocol code for " + cause);
        }
    }
}
