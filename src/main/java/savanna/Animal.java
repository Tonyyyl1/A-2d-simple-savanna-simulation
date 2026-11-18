package savanna;


/**
 * Common elements of foxes and rabbits.
 *
 * @author David J. Barnes and Michael Kölling
 * @version 7.0
 */
public abstract class Animal implements Actor
{
    private final long id;
    private final AnimalIdentityAllocator identityAllocator;
    // Whether the animal is alive or not.
    private boolean alive;
    // The animal's position.
    private Location location;
    private DeathCause deathCause;

    /**
     * Constructor for objects of class Animal.
     * @param location The animal's location.
     */
    public Animal(Location location)
    {
        this(location, new AnimalIdentityAllocator());
    }

    protected Animal(Location location,
                     AnimalIdentityAllocator identityAllocator)
    {
        if(identityAllocator == null) {
            throw new IllegalArgumentException(
                "animal identity allocator is required");
        }
        this.identityAllocator = identityAllocator;
        id = identityAllocator.allocate();
        this.alive = true;
        this.location = location;
        deathCause = DeathCause.NONE;
    }

    public long getId()
    {
        return id;
    }

    protected final AnimalIdentityAllocator getIdentityAllocator()
    {
        return identityAllocator;
    }
    
    /**
     * Legacy isolated-transition entry point retained for compatibility tests.
     * Production stepping is owned by {@link PositionStepCoordinator}; it does
     * not call this direct-placement method or expose a projected field.
     */
    abstract public void act(Field currentField, Field nextFieldState, SimulationContext context);
    
    /**
     * Check whether the animal is alive or not.
     * @return true if the animal is still alive.
     */
    public boolean isAlive()
    {
        return alive;
    }

    /**
     * Indicate that the animal is no longer alive.
     */
    protected void setDead()
    {
        setDead(DeathCause.UNKNOWN);
    }

    /** Mark the animal dead once while preserving the first known cause. */
    protected void setDead(DeathCause cause)
    {
        if(!alive) {
            return;
        }
        alive = false;
        deathCause = cause == null || cause == DeathCause.NONE
            ? DeathCause.UNKNOWN : cause;
        location = null;
    }

    public DeathCause getDeathCause()
    {
        return deathCause;
    }
    
    /**
     * Return the animal's location.
     * @return The animal's location.
     */
    public Location getLocation()
    {
        return location;
    }
    
    /**
     * Set the animal's location.
     * @param location The new location.
     */
    protected void setLocation(Location location)
    {
        this.location = location;
    }
}
