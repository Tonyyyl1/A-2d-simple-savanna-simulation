package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable batch of claims, cancellations, and released step-start adults. */
public final class PositionClaimBatch
{
    private final Map<PositionActorKey, PositionClaim> claims;
    private final Set<PositionActorKey> cancelled;
    private final Set<PositionActorKey> released;

    private PositionClaimBatch(Map<PositionActorKey, PositionClaim> claims,
                               Set<PositionActorKey> cancelled,
                               Set<PositionActorKey> released)
    {
        // The builder hands over its collections and is spent (see Builder.build), so the
        // batch keeps them without a second copy; iteration order is the insertion order.
        this.claims = Collections.unmodifiableMap(claims);
        this.cancelled = Collections.unmodifiableSet(cancelled);
        this.released = Collections.unmodifiableSet(released);
    }

    public static Builder builder()
    {
        return new Builder(0);
    }

    /** A builder whose claim table is sized for about {@code expectedClaims} claims. */
    public static Builder builder(int expectedClaims)
    {
        return new Builder(expectedClaims);
    }

    public static PositionClaimBatch of(List<PositionClaim> claims)
    {
        Builder builder = builder();
        if(claims != null) {
            for(PositionClaim claim : claims) {
                builder.add(claim);
            }
        }
        return builder.build();
    }

    public PositionClaim getClaim(PositionActorKey actor)
    {
        return claims.get(actor);
    }

    public boolean isCancelled(PositionActorKey actor)
    {
        return cancelled.contains(actor);
    }

    /**
     * A released key is a step-start adult whose sequential action phase
     * killed it. It must not protect or receive its former origin.
     */
    public boolean isReleased(PositionActorKey actor)
    {
        return released.contains(actor);
    }

    public List<PositionClaim> getClaims()
    {
        List<PositionClaim> result = new ArrayList<>(claims.values());
        result.sort((first, second) -> first.getActor().compareTo(
            second.getActor()));
        return Collections.unmodifiableList(result);
    }

    public Set<PositionActorKey> getCancelled()
    {
        return cancelled;
    }

    public Set<PositionActorKey> getReleased()
    {
        return released;
    }

    public static final class Builder
    {
        private final Map<PositionActorKey, PositionClaim> claims;
        private final Set<PositionActorKey> cancelled = new LinkedHashSet<>();
        private final Set<PositionActorKey> released = new LinkedHashSet<>();
        private boolean built;

        private Builder(int expectedClaims)
        {
            claims = expectedClaims <= 0 ? new LinkedHashMap<>()
                : new LinkedHashMap<>((int)(expectedClaims / 0.75f) + 1);
        }

        public Builder add(PositionClaim claim)
        {
            requireOpen();
            Objects.requireNonNull(claim, "position claim is required");
            PositionActorKey actor = claim.getActor();
            if(cancelled.contains(actor) || released.contains(actor)) {
                throw new IllegalArgumentException(
                    "Cannot add a cancelled or released position claim: " + actor);
            }
            if(claims.put(actor, claim) != null) {
                throw new IllegalArgumentException(
                    "Duplicate position claim: " + actor);
            }
            return this;
        }

        public Builder cancel(PositionActorKey actor)
        {
            requireOpen();
            Objects.requireNonNull(actor, "position actor is required");
            if(claims.containsKey(actor) || released.contains(actor)) {
                throw new IllegalArgumentException(
                    "Cannot cancel after adding or releasing a position claim: " + actor);
            }
            if(!cancelled.add(actor)) {
                throw new IllegalArgumentException(
                    "Duplicate position cancellation: " + actor);
            }
            return this;
        }

        /**
         * Release a dead step-start adult's origin. Spawn tokens cannot be
         * released because they never own an origin.
         */
        public Builder release(PositionActorKey actor)
        {
            requireOpen();
            Objects.requireNonNull(actor, "position actor is required");
            if(!actor.isAdult()) {
                throw new IllegalArgumentException(
                    "Only a step-start adult can release an origin: " + actor);
            }
            if(claims.containsKey(actor) || cancelled.contains(actor)) {
                throw new IllegalArgumentException(
                    "Cannot release an added or cancelled position claim: " + actor);
            }
            if(!released.add(actor)) {
                throw new IllegalArgumentException(
                    "Duplicate position release: " + actor);
            }
            return this;
        }

        /** The batch takes over this builder's collections, so a builder builds once. */
        public PositionClaimBatch build()
        {
            requireOpen();
            built = true;
            return new PositionClaimBatch(claims, cancelled, released);
        }

        private void requireOpen()
        {
            if(built) {
                throw new IllegalStateException("position claim batch already built");
            }
        }
    }
}
