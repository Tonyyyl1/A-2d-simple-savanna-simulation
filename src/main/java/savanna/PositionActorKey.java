package savanna;

import java.util.Objects;

/**
 * Stable identity for a participant in a position plan.
 *
 * <p>An adult key is the already-existing living animal ID. A spawn key is a
 * provisional parent/litter token and deliberately does not consume a child
 * ID. The type is part of equality, so an adult and a spawn token with the
 * same parent ID can never collide.</p>
 */
public final class PositionActorKey implements Comparable<PositionActorKey>
{
    private final Kind kind;
    private final long parentOrAdultId;
    private final int litterOrdinal;

    private PositionActorKey(Kind kind, long parentOrAdultId,
                             int litterOrdinal)
    {
        if(kind == null || parentOrAdultId <= 0L || litterOrdinal < 0) {
            throw new IllegalArgumentException("Invalid position actor key");
        }
        if(kind == Kind.ADULT && litterOrdinal != 0) {
            throw new IllegalArgumentException(
                "Adult position keys cannot have a litter ordinal");
        }
        this.kind = kind;
        this.parentOrAdultId = parentOrAdultId;
        this.litterOrdinal = litterOrdinal;
    }

    public static PositionActorKey adult(long animalId)
    {
        return new PositionActorKey(Kind.ADULT, animalId, 0);
    }

    public static PositionActorKey spawn(long parentId, int litterOrdinal)
    {
        return new PositionActorKey(Kind.SPAWN, parentId, litterOrdinal);
    }

    public boolean isAdult()
    {
        return kind == Kind.ADULT;
    }

    public boolean isSpawn()
    {
        return kind == Kind.SPAWN;
    }

    public Kind getKind()
    {
        return kind;
    }

    /** Adult ID for adult keys, or parent ID for provisional spawn keys. */
    public long getParentOrAdultId()
    {
        return parentOrAdultId;
    }

    public long getAdultId()
    {
        if(!isAdult()) {
            throw new IllegalStateException("Spawn token has no adult ID");
        }
        return parentOrAdultId;
    }

    public long getParentId()
    {
        if(!isSpawn()) {
            throw new IllegalStateException("Adult key has no parent ID");
        }
        return parentOrAdultId;
    }

    public int getLitterOrdinal()
    {
        return litterOrdinal;
    }

    @Override
    public int compareTo(PositionActorKey other)
    {
        if(other == null) {
            return -1;
        }
        int kindOrder = kind.compareTo(other.kind);
        if(kindOrder != 0) {
            return kindOrder;
        }
        int idOrder = Long.compare(parentOrAdultId, other.parentOrAdultId);
        return idOrder != 0 ? idOrder :
            Integer.compare(litterOrdinal, other.litterOrdinal);
    }

    @Override
    public boolean equals(Object value)
    {
        if(this == value) {
            return true;
        }
        if(!(value instanceof PositionActorKey)) {
            return false;
        }
        PositionActorKey other = (PositionActorKey)value;
        return kind == other.kind &&
            parentOrAdultId == other.parentOrAdultId &&
            litterOrdinal == other.litterOrdinal;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(kind, parentOrAdultId, litterOrdinal);
    }

    @Override
    public String toString()
    {
        return isAdult() ? "adult:" + parentOrAdultId :
            "spawn:" + parentOrAdultId + ':' + litterOrdinal;
    }

    public enum Kind
    {
        ADULT,
        SPAWN
    }
}
