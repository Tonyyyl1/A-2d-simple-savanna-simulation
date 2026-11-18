package savanna;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Every tunable ecology constant in one immutable set (plan v3.0 M3-0).
 *
 * <p>Production code reads {@link #current()}, whose values are the code
 * defaults below. Only screening tools ({@code EcologyMatrixRunner},
 * {@code EcologySweep}) replace them through {@link #override}. Changing a
 * default is an ecology rule change: bump {@link EcologyRules#VERSION} in the
 * same commit. Reading parameters never consumes ecology random numbers.</p>
 */
public final class EcologyParameters
{
    /** Stable keys; the declaration order is the canonical order. */
    public enum Key
    {
        // Predation (M3-1 family 1; v3-m3-f1-predation: cub 1.70 -> 1.0,
        // Lion skill x1.2, Cheetah skill x0.8, chosen over seeds 1111-4111).
        HUNT_SUCCESS_CAP("predation.successCap", 0.62, 0.0, 1.0, false),
        HUNT_SUCCESS_CAP_CRITICAL("predation.successCapCritical", 0.72, 0.0, 1.0, false),
        CUB_VULNERABILITY("predation.cubVulnerability", 1.0, 0.0, 10.0, false),
        JUVENILE_VULNERABILITY("predation.juvenileVulnerability", 1.32, 0.0, 10.0, false),
        // v3-m3-model-hydration (2026-10-21): Lion x1.5, Cheetah x1.0, refuge 10.
        LION_SKILL_SCALE("predation.lionSkillScale", 1.1, 0.0, 5.0, false),
        CHEETAH_SKILL_SCALE("predation.cheetahSkillScale", 1.0, 0.0, 5.0, false),
        // M3 model changes B and C (2026-10-20, user-approved; 0 = off).
        /** B: a non-critical predator at or above this share of max food does not hunt. */
        SATIATION_FRACTION("predation.satiationFraction", 0.0, 0.0, 1.0, false),
        /** C: success scales with min(1, local same-species prey / this count). */
        REFUGE_COUNT("predation.refugeCount", 10, 0, 1_000, true),
        /** C: success divided by 1 + this x (local same-species prey - 1). */
        VIGILANCE_PER_NEIGHBOUR("predation.vigilancePerNeighbour", 0.0, 0.0, 10.0, false),
        // M3 model changes E and F (2026-10-21, user-approved; defaults are off).
        /** E: target score x (prey food / mean edible food)^k. */
        PREY_SIZE_PREFERENCE("predation.preySizePreference", 0.0, 0.0, 5.0, false),
        /** F: food used per step relative to 1 (fractions carry over). */
        LION_METABOLISM("metabolism.lion", 1.0, 0.2, 2.0, false),
        CHEETAH_METABOLISM("metabolism.cheetah", 1.0, 0.2, 2.0, false),
        // M3 model change G (2026-10-22, user-approved; default off).
        /** G: 0 off; 1 Cheetah also hunts Zebra cubs and juveniles; 2 Zebra cubs only. */
        CHEETAH_ZEBRA_STAGE("predation.cheetahZebraStage", 0, 0, 2, true),
        // M3 model change H (2026-10-22, user-approved; defaults are off).
        /** H2: success x (1 + this x (local same-species prey - refuge count) / refuge count), 0 = off. */
        DENSITY_RESPONSE("predation.densityResponse", 0.0, 0.0, 10.0, false),
        // M3 model change W (2026-10-23, user-approved; defaults are off).
        /** W: a target within this Chebyshev distance of water is in the waterhole zone (0 = off). */
        WATER_ZONE_RADIUS("predation.waterZoneRadius", 0, 0, 100, true),
        /** W: hunt success against a target in the waterhole zone is multiplied by this (1 = off). */
        WATER_ZONE_SUCCESS("predation.waterZoneSuccess", 1.0, 0.0, 10.0, false),
        // M3 model change B (2026-10-23, user-approved; defaults are off).
        /** B1: 1 replaces the herbivore grass factor with a threshold gate (no floor). */
        GRASS_GATE("repro.grassGate", 0, 0, 1, true),
        /** B1: local grass fill at or below which herbivores do not conceive. */
        GRASS_MIN("repro.grassMin", 0.15, 0.0, 1.0, false),
        /** B1: local grass fill at which the gate is fully open (reaches 1.0). */
        GRASS_MAX("repro.grassMax", 0.60, 0.01, 2.0, false),
        /** B1: radius in cells of the window the grass fill is read from. */
        GRASS_WINDOW("repro.grassWindow", 3, 1, 30, true),
        /** B': 0 the grass gate applies to every herbivore; 1 only to Gazelle (others keep the old factor). */
        GRASS_GATE_SPECIES("repro.grassGateSpecies", 0, 0, 1, true),
        /** B2: extra food a pregnant mother uses per step (fractions carry over; 0 = off). */
        GESTATION_UPKEEP("repro.gestationUpkeep", 0.0, 0.0, 5.0, false),
        // M3 change C1 (2026-10-27, user-approved; default off).
        /** C1: cells added to a Cheetah's effective hunting range (0 = off). */
        CHEETAH_RANGE_BONUS("predation.cheetahRangeBonus", 0, 0, 20, true),
        // M3 change A1 (2026-10-28, user-approved; defaults are off).
        /** A1: births of Lion fall once this many Lions share the territory window (0 = off). */
        TERRITORY_LION("territory.lion", 3, 0, 10_000, true),
        /** A1: births of Cheetah fall once this many Cheetahs share the territory window (0 = off). */
        TERRITORY_CHEETAH("territory.cheetah", 2, 0, 10_000, true),
        /** A1: radius in cells of the territory window the conspecific count is read from. */
        TERRITORY_RADIUS("territory.radius", 10, 1, 30, true),
        /** C-2: per-species territory window radius (0 = use territory.radius). */
        TERRITORY_LION_RADIUS("territory.lionRadius", 24, 0, 30, true),
        TERRITORY_CHEETAH_RADIUS("territory.cheetahRadius", 16, 0, 30, true),
        // Water and threat avoidance (family 2).
        THREAT_RADIUS("avoidance.threatRadius", 4, 0, 30, true),
        /** 0: water priority never flees; 1: non-critical seekers flee; 2: drinkers too. */
        WATER_FLEE_MODE("avoidance.waterFleeMode", 0, 0, 2, true),
        // Hydration (added to the calibration set after the A-C sweeps;
        // v3-m3-model-hydration: drain 0.24 -> 0.16, thirsty 70 -> 50).
        HYDRATION_BASE_DRAIN("hydration.baseDrain", 0.16, 0.01, 5.0, false),
        HYDRATION_CRITICAL_THRESHOLD("hydration.criticalThreshold", 30.0, 1.0, 99.0, false),
        HYDRATION_THIRSTY_THRESHOLD("hydration.thirstyThreshold", 50.0, 1.0, 99.0, false),
        HYDRATION_PREDATOR_DRAIN("hydration.predatorDrainMultiplier", 1.10, 0.1, 5.0, false),
        /** M3 change A: 1 lets a thirsty but not critical animal conceive (0 = water first). */
        CONCEIVE_WHILE_THIRSTY("repro.conceiveWhileThirsty", 1, 0, 1, true),
        DRINK_PERCENT_PER_STEP("water.drinkPercentPerStep", 35.0, 1.0, 100.0, false),
        DRINK_COMPLETE_PERCENT("water.drinkCompletePercent", 95.0, 1.0, 100.0, false),
        WATER_QUEUE_RADIUS("water.queueRadius", 3, 0, 30, true),
        WATER_MAX_WAIT_STEPS("water.maxWaitSteps", 12, 0, 10_000, true),
        WATER_RETARGET_EXCLUSION_STEPS("water.retargetExclusionSteps", 24, 0, 10_000, true),
        // Herbivore reproduction and carrying capacity (family 3;
        // v3-m3-f3-herbivore: conception x2 -> x1.5, prey overload 30 -> 20,
        // grass capacity x0.8, chosen over seeds 1111-4111).
        HERBIVORE_CONCEPTION_MULTIPLIER("repro.herbivoreConceptionMultiplier", 1.5, 0.001, 100.0, false),
        HERBIVORE_GESTATION("repro.herbivoreGestation", 6, 1, 10_000, true),
        HERBIVORE_RESERVE_FRACTION("repro.herbivoreReserveFraction", 0.15, 0.001, 1.0, false),
        LOCAL_WINDOW_RADIUS("capacity.windowRadius", 6, 1, 30, true),
        PREY_OVERLOAD_COUNT("capacity.preyOverload", 10, 1, 10_000, true),
        PREDATOR_OVERLOAD_COUNT("capacity.predatorOverload", 8, 1, 10_000, true),
        OVERLOAD_EXPONENT("capacity.overloadExponent", 2.0, 0.0, 10.0, false),
        OVERLOAD_FLOOR("capacity.overloadFloor", 0.05, 0.0, 1.0, false),
        GRASS_GROWTH_RATE("grass.growthRate", 0.06, 0.0, 1.0, false),
        GRASS_FLOOR_RATE("grass.floorRate", 0.004, 0.0, 1.0, false),
        GRASS_CAPACITY_SCALE("grass.capacityScale", 0.4, 0.0, 10.0, false),
        // Predator reproduction and cubs (family 4; v3-m3-f4-predator:
        // conception x2 -> x3, adult margin 20 -> 0, mate sense 3 -> 5,
        // chosen over seeds 1111-4111).
        PREDATOR_CONCEPTION_MULTIPLIER("repro.predatorConceptionMultiplier", 4.0, 0.001, 100.0, false),
        PREDATOR_GESTATION("repro.predatorGestation", 8, 1, 10_000, true),
        COOLDOWN_FACTOR("repro.cooldownFactor", 0.25, 0.0, 100.0, false),
        PREDATOR_RESERVE_FRACTION("repro.predatorReserveFraction", 0.0, 0.0, 1.0, false),
        CHEETAH_RESERVE_FRACTION("repro.cheetahReserveFraction", 0.0, 0.0, 1.0, false),
        /** Predator reserve floor: adult age plus this many steps of food ({@code off} = none). */
        PREDATOR_ADULT_MARGIN("repro.predatorAdultMargin", 12, OFF_MARGIN, 100_000, true),
        PROVISION_RADIUS("repro.provisionRadius", 3, 0, 30, true),
        MATE_SENSE_FACTOR("social.mateSenseFactor", 5, 1, 30, true),
        // Egress (family 5). Herd cohesion was removed (M3 change D): it
        // never fired, because grazing moves always preceded it.
        PREDATOR_EXCLUSION_RADIUS("social.predatorExclusionRadius", 6, 0, 30, true),
        PREDATOR_EXCLUSION_COUNT("social.predatorExclusionCount", 3, 0, 10_000, true),
        // v3.2 B-1: most land steps a hunting predator may advance in one step.
        LION_SPEED("movement.lionSpeed", 2, 1, 6, true),
        CHEETAH_SPEED("movement.cheetahSpeed", 3, 1, 6, true),
        // v3.3 P-1 (N1, N2; user-approved 2026-11-13; defaults are off).
        /** N1: grass fill (biomass / capacity of the cell) at or above which a bite is full; below it the bite shrinks linearly to nothing at the refuge (0 = off). */
        GRAZING_FULL_INTAKE_FILL("grazing.fullIntakeFill", 0.5, 0.0, 1.0, false),
        /** N1: share of a cell's capacity that grazing never takes (a refuge the grass regrows from); only read when the full-intake fill is on. */
        GRAZING_REFUGE_FILL("grazing.refugeFill", 0.15, 0.0, 0.9, false),
        /** N2: a herbivore at or below this share of max food does not conceive; the factor rises linearly to 1 at the full share (0 = off). */
        HERBIVORE_CONDITION_MIN("repro.herbivoreConditionMin", 0.0, 0.0, 1.0, false),
        /** N2: share of max food at which the condition factor reaches 1. */
        HERBIVORE_CONDITION_FULL("repro.herbivoreConditionFull", 0.9, 0.01, 1.5, false),
        /** N3 (2026-11-15, user-approved): extra food a herbivore uses per drought step (1.0 = legacy +1; fractions carry over). */
        DROUGHT_HERBIVORE_UPKEEP("weather.droughtHerbivoreUpkeep", 0.6, 0.0, 3.0, false),
        // v3.2 C-1 levers (all 0 = off).
        /** C-1 lever 1: a herbivore at or above this share of max food does not graze; below it only grazes to fill up. */
        GRAZING_SATIATION("grazing.satiationFraction", 0.0, 0.0, 1.0, false),
        /** C-1 lever 5: grazing efficiency x(1+k) on the preferred terrain, x(1-k) elsewhere. */
        GRAZING_NICHE_PARTITION("grazing.nichePartition", 0.4, 0.0, 0.8, false),
        /** C-1 lever 4: births of the species fall once this many conspecifics share the capacity window. */
        OVERLOAD_ZEBRA("capacity.overload.zebra", 0, 0, 10_000, true),
        OVERLOAD_BUFFALO("capacity.overload.buffalo", 0, 0, 10_000, true),
        OVERLOAD_GAZELLE("capacity.overload.gazelle", 0, 0, 10_000, true),
        // v3.2 C-0: founding population per species on the default map (0 removes the species).
        INIT_LION("init.lion", 100, 0, 100_000, true),
        INIT_CHEETAH("init.cheetah", 60, 0, 100_000, true),
        INIT_ZEBRA("init.zebra", 2000, 0, 100_000, true),
        INIT_BUFFALO("init.buffalo", 1200, 0, 100_000, true),
        INIT_GAZELLE("init.gazelle", 3000, 0, 100_000, true),
        /** C-3 lever: success divided by 1 + this x (same-species predators within hunting range - 1); 0 = off. */
        INTERFERENCE_LION("predation.lionInterference", 0.0, 0.0, 5.0, false),
        INTERFERENCE_CHEETAH("predation.cheetahInterference", 1.0, 0.0, 5.0, false),
        /** C-3: 1 lets an adult that senses no mate follow the persistent search heading (B-2) instead of wandering. */
        MATE_SEARCH("social.mateSearch", 0, 0, 1, true),
        // v3.2 B-3: consecutive steps at zero food before starvation kills.
        STARVATION_HERBIVORE_STEPS("starvation.herbivoreSteps", 3, 1, 200, true),
        STARVATION_PREDATOR_STEPS("starvation.predatorSteps", 12, 1, 200, true),
        // v3.2 B-2: steps a hungry predator keeps one search direction.
        SEARCH_PERSISTENCE("movement.searchPersistence", 48, 1, 1_000, true),
        // v3.2 A-4: species life-history constants (defaults equal the former SpeciesRegistry values).
        LION_BREEDING_PROBABILITY("species.lion.breedingProbability", 0.012, 0.0, 1.0, false),
        LION_MAX_LITTER("species.lion.maxLitterSize", 2, 1, 6, true),
        LION_FOOD_VALUE("species.lion.foodValue", 105, 1, 1_000, true),
        LION_GRAZING_RATE("species.lion.grazingRate", 0.0, 0.0, 5.0, false),
        LION_PLANT_FOOD_VALUE("species.lion.plantFoodValue", 0, 0, 1_000, true),
        CHEETAH_BREEDING_PROBABILITY("species.cheetah.breedingProbability", 0.058, 0.0, 1.0, false),
        CHEETAH_MAX_LITTER("species.cheetah.maxLitterSize", 2, 1, 6, true),
        CHEETAH_FOOD_VALUE("species.cheetah.foodValue", 56, 1, 1_000, true),
        CHEETAH_GRAZING_RATE("species.cheetah.grazingRate", 0.0, 0.0, 5.0, false),
        CHEETAH_PLANT_FOOD_VALUE("species.cheetah.plantFoodValue", 0, 0, 1_000, true),
        ZEBRA_BREEDING_PROBABILITY("species.zebra.breedingProbability", 0.085, 0.0, 1.0, false),
        ZEBRA_MAX_LITTER("species.zebra.maxLitterSize", 1, 1, 6, true),
        ZEBRA_FOOD_VALUE("species.zebra.foodValue", 42, 1, 1_000, true),
        ZEBRA_GRAZING_RATE("species.zebra.grazingRate", 0.12, 0.0, 5.0, false),
        ZEBRA_PLANT_FOOD_VALUE("species.zebra.plantFoodValue", 9, 0, 1_000, true),
        BUFFALO_BREEDING_PROBABILITY("species.buffalo.breedingProbability", 0.04, 0.0, 1.0, false),
        BUFFALO_MAX_LITTER("species.buffalo.maxLitterSize", 1, 1, 6, true),
        BUFFALO_FOOD_VALUE("species.buffalo.foodValue", 66, 1, 1_000, true),
        BUFFALO_GRAZING_RATE("species.buffalo.grazingRate", 0.15, 0.0, 5.0, false),
        BUFFALO_PLANT_FOOD_VALUE("species.buffalo.plantFoodValue", 10, 0, 1_000, true),
        GAZELLE_BREEDING_PROBABILITY("species.gazelle.breedingProbability", 0.128, 0.0, 1.0, false),
        GAZELLE_MAX_LITTER("species.gazelle.maxLitterSize", 2, 1, 6, true),
        GAZELLE_FOOD_VALUE("species.gazelle.foodValue", 34, 1, 1_000, true),
        GAZELLE_GRAZING_RATE("species.gazelle.grazingRate", 0.12, 0.0, 5.0, false),
        GAZELLE_PLANT_FOOD_VALUE("species.gazelle.plantFoodValue", 9, 0, 1_000, true);

        final String id;
        final double defaultValue;
        final double min;
        final double max;
        final boolean integral;

        Key(String id, double defaultValue, double min, double max, boolean integral)
        {
            this.id = id;
            this.defaultValue = defaultValue;
            this.min = min;
            this.max = max;
            this.integral = integral;
        }

        public String id()
        {
            return id;
        }

        static Key byId(String id)
        {
            for(Key key : values()) {
                if(key.id.equals(id)) return key;
            }
            throw new IllegalArgumentException("unknown ecology parameter: " + id);
        }
    }

    /** {@code repro.predatorAdultMargin=off}: no adult-age reserve floor. */
    static final int OFF_MARGIN = -1_000_000;

    private static final EcologyParameters DEFAULTS = new EcologyParameters(defaultValues());
    private static volatile EcologyParameters current = DEFAULTS;

    private final double[] values;
    private final String canonical;

    private EcologyParameters(double[] values)
    {
        this.values = values;
        StringBuilder text = new StringBuilder();
        for(Key key : Key.values()) {
            if(text.length() > 0) text.append(',');
            text.append(key.id).append('=').append(format(key, values[key.ordinal()]));
        }
        this.canonical = text.toString();
    }

    public static EcologyParameters defaults()
    {
        return DEFAULTS;
    }

    public static EcologyParameters current()
    {
        return current;
    }

    /** Screening hook for experiment tools; production never calls it. */
    static void override(EcologyParameters replacement)
    {
        current = replacement == null ? DEFAULTS : replacement;
    }

    public double get(Key key)
    {
        return values[key.ordinal()];
    }

    /** Founding population of {@code species} on the default map; 0 when it is not a founding species. */
    public int initialCount(String species)
    {
        if(SpeciesRegistry.LION.equals(species)) return getInt(Key.INIT_LION);
        if(SpeciesRegistry.CHEETAH.equals(species)) return getInt(Key.INIT_CHEETAH);
        if(SpeciesRegistry.ZEBRA.equals(species)) return getInt(Key.INIT_ZEBRA);
        if(SpeciesRegistry.BUFFALO.equals(species)) return getInt(Key.INIT_BUFFALO);
        if(SpeciesRegistry.GAZELLE.equals(species)) return getInt(Key.INIT_GAZELLE);
        return 0;
    }

    public int getInt(Key key)
    {
        return (int)values[key.ordinal()];
    }

    /**
     * Returns a copy with {@code key=value,key=value} applied. Unknown keys,
     * repeated keys and out-of-range values are rejected.
     */
    public EcologyParameters with(String spec)
    {
        double[] next = values.clone();
        if(spec == null || spec.isBlank() || "default".equals(spec.trim())) {
            return new EcologyParameters(next);
        }
        Set<Key> seen = new HashSet<>();
        for(String item : spec.split(",")) {
            String entry = item.trim();
            if(entry.isEmpty()) continue;
            int equals = entry.indexOf('=');
            if(equals <= 0) throw new IllegalArgumentException("expected key=value: " + entry);
            Key key = Key.byId(entry.substring(0, equals).trim());
            if(!seen.add(key)) throw new IllegalArgumentException("repeated parameter: " + key.id);
            next[key.ordinal()] = parse(key, entry.substring(equals + 1).trim());
        }
        return new EcologyParameters(next);
    }

    /** Every key in canonical order, e.g. {@code predation.successCap=0.62,...}. */
    public String canonical()
    {
        return canonical;
    }

    /** Only the keys that differ from the code defaults, or {@code default}. */
    public String overrides()
    {
        StringBuilder text = new StringBuilder();
        for(Key key : Key.values()) {
            double value = values[key.ordinal()];
            if(Double.compare(value, DEFAULTS.values[key.ordinal()]) == 0) continue;
            if(text.length() > 0) text.append(',');
            text.append(key.id).append('=').append(format(key, value));
        }
        return text.length() == 0 ? "default" : text.toString();
    }

    /** First 12 hex digits of SHA-256 over {@link #canonical()}. */
    public String hash()
    {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexEncoding.lowercase(Arrays.copyOf(digest, 6));
        }
        catch(NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof EcologyParameters &&
            Arrays.equals(values, ((EcologyParameters)other).values);
    }

    @Override
    public int hashCode()
    {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString()
    {
        return canonical;
    }

    private static double[] defaultValues()
    {
        double[] result = new double[Key.values().length];
        for(Key key : Key.values()) result[key.ordinal()] = key.defaultValue;
        return result;
    }

    private static double parse(Key key, String raw)
    {
        double value;
        if(key == Key.PREDATOR_ADULT_MARGIN && "off".equals(raw)) {
            value = OFF_MARGIN;
        }
        else {
            try {
                value = Double.parseDouble(raw);
            }
            catch(NumberFormatException e) {
                throw new IllegalArgumentException("not a number for " + key.id + ": " + raw);
            }
        }
        if(Double.isNaN(value) || value < key.min || value > key.max) {
            throw new IllegalArgumentException(key.id + " out of range [" +
                format(key, key.min) + ", " + format(key, key.max) + "]: " + raw);
        }
        if(key.integral && value != Math.rint(value)) {
            throw new IllegalArgumentException(key.id + " must be an integer: " + raw);
        }
        return value;
    }

    private static String format(Key key, double value)
    {
        if(key == Key.PREDATOR_ADULT_MARGIN && value <= OFF_MARGIN) return "off";
        if(key.integral) return Long.toString((long)value);
        return Double.toString(value).toLowerCase(Locale.ROOT);
    }
}
