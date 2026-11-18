package savanna;

import java.util.Objects;

/**
 * Immutable fixed-point resource quantities used by the disconnected
 * birth-resource transaction kernel.
 */
public final class ResourceVector
{
    public static final ResourceVector ZERO =
        new ResourceVector(0L, 0L, 0L, 0L);

    private final long foodUnits;
    private final long hydrationMicros;
    private final long staminaMicros;
    private final long reproductiveCreditUnits;

    public ResourceVector(long foodUnits, long hydrationMicros,
                          long staminaMicros,
                          long reproductiveCreditUnits)
    {
        if(foodUnits < 0L || hydrationMicros < 0L ||
           staminaMicros < 0L || reproductiveCreditUnits < 0L) {
            throw new IllegalArgumentException(
                "resource quantities must be non-negative");
        }
        this.foodUnits = foodUnits;
        this.hydrationMicros = hydrationMicros;
        this.staminaMicros = staminaMicros;
        this.reproductiveCreditUnits = reproductiveCreditUnits;
    }

    public long getFoodUnits()
    {
        return foodUnits;
    }

    public long getHydrationMicros()
    {
        return hydrationMicros;
    }

    public long getStaminaMicros()
    {
        return staminaMicros;
    }

    public long getReproductiveCreditUnits()
    {
        return reproductiveCreditUnits;
    }

    public ResourceVector plus(ResourceVector other)
    {
        Objects.requireNonNull(other, "resource vector is required");
        return new ResourceVector(
            Math.addExact(foodUnits, other.foodUnits),
            Math.addExact(hydrationMicros, other.hydrationMicros),
            Math.addExact(staminaMicros, other.staminaMicros),
            Math.addExact(reproductiveCreditUnits,
                          other.reproductiveCreditUnits));
    }

    public ResourceVector minus(ResourceVector other)
    {
        Objects.requireNonNull(other, "resource vector is required");
        return new ResourceVector(
            Math.subtractExact(foodUnits, other.foodUnits),
            Math.subtractExact(hydrationMicros, other.hydrationMicros),
            Math.subtractExact(staminaMicros, other.staminaMicros),
            Math.subtractExact(reproductiveCreditUnits,
                               other.reproductiveCreditUnits));
    }

    public boolean isZero()
    {
        return equals(ZERO);
    }

    @Override
    public boolean equals(Object other)
    {
        if(this == other) return true;
        if(!(other instanceof ResourceVector)) return false;
        ResourceVector vector = (ResourceVector)other;
        return foodUnits == vector.foodUnits &&
            hydrationMicros == vector.hydrationMicros &&
            staminaMicros == vector.staminaMicros &&
            reproductiveCreditUnits == vector.reproductiveCreditUnits;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(foodUnits, hydrationMicros, staminaMicros,
                            reproductiveCreditUnits);
    }

    @Override
    public String toString()
    {
        return "ResourceVector{" +
            "foodUnits=" + foodUnits +
            ", hydrationMicros=" + hydrationMicros +
            ", staminaMicros=" + staminaMicros +
            ", reproductiveCreditUnits=" +
            reproductiveCreditUnits + '}';
    }
}
