package savanna;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lazy, duplicate-free candidate stream for one claim.
 *
 * <p>Requested and explicit candidates are emitted before the topology's
 * precomputed search order; an adult's origin is always last. The cursor never
 * performs a terrain search and never emits the same cell twice.</p>
 */
public final class PositionCandidateCursor
{
    private final PositionClaim claim;
    private final PositionTopology topology;
    private final Location adultOrigin;
    private final Set<Location> emittedLocations = new HashSet<>();
    private int stage;
    private int explicitIndex;
    private int searchIndex;
    private int emittedCount;
    private boolean emittedNull;
    private List<Location> searchFallbacks;
    private PositionCandidate prepared;

    PositionCandidateCursor(PositionClaim claim, PositionTopology topology,
                            Location adultOrigin)
    {
        this.claim = claim;
        this.topology = topology;
        this.adultOrigin = adultOrigin;
    }

    public boolean hasNext()
    {
        if(prepared == null) {
            prepared = prepareNext();
        }
        return prepared != null;
    }

    public PositionCandidate next()
    {
        if(!hasNext()) {
            throw new IllegalStateException("No remaining position candidate");
        }
        PositionCandidate result = prepared;
        prepared = null;
        return result;
    }

    public int getEmittedCount()
    {
        return emittedCount;
    }

    private PositionCandidate prepareNext()
    {
        while(stage < 4) {
            if(stage == 0) {
                stage = 1;
                PositionCandidate result = emit(claim.getRequested(),
                    PositionCandidateSource.REQUESTED);
                if(result != null) {
                    return result;
                }
            }
            else if(stage == 1) {
                List<Location> explicit = claim.getExplicitFallbacks();
                while(explicitIndex < explicit.size()) {
                    PositionCandidate result = emit(explicit.get(explicitIndex++),
                        PositionCandidateSource.EXPLICIT_FALLBACK);
                    if(result != null) {
                        return result;
                    }
                }
                stage = 2;
            }
            else if(stage == 2) {
                if(searchFallbacks == null) {
                    Location origin = claim.getSearchOrigin();
                    if(origin == null && claim.getActor().isAdult()) {
                        origin = adultOrigin;
                    }
                    searchFallbacks = topology.getSearchFallbacks(origin,
                        claim.getSearchCandidateBudget());
                }
                while(searchIndex < searchFallbacks.size()) {
                    PositionCandidate result = emit(searchFallbacks.get(searchIndex++),
                        PositionCandidateSource.SEARCH_FALLBACK);
                    if(result != null) {
                        return result;
                    }
                }
                stage = 3;
            }
            else {
                stage = 4;
                if(claim.getActor().isAdult()) {
                    PositionCandidate result = emit(adultOrigin,
                        PositionCandidateSource.ORIGIN_STAY);
                    if(result != null) {
                        return result;
                    }
                }
            }
        }
        return null;
    }

    private PositionCandidate emit(Location location,
                                   PositionCandidateSource source)
    {
        if(location == null) {
            if(emittedNull) {
                return null;
            }
            emittedNull = true;
        }
        else if(!emittedLocations.add(location)) {
            return null;
        }
        return new PositionCandidate(location, source, emittedCount++);
    }
}
