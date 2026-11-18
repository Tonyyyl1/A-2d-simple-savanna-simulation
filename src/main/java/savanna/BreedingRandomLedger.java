package savanna;

/** Named operation-level ledger for the unchanged legacy breeding RNG path. */
public final class BreedingRandomLedger
{
    private final boolean mateQueryShuffle;
    private final int mateQueryShuffleInputSize;
    private final boolean legacyBreedingRoll;
    private final boolean litterDraw;
    private final int litterDrawBound;

    public BreedingRandomLedger(boolean mateQueryShuffle,
                                int mateQueryShuffleInputSize,
                                boolean legacyBreedingRoll,
                                boolean litterDraw,
                                int litterDrawBound)
    {
        if(mateQueryShuffleInputSize < 0 || litterDrawBound < 0 ||
           (!mateQueryShuffle && mateQueryShuffleInputSize != 0) ||
           (!litterDraw && litterDrawBound != 0) ||
           (litterDraw && (!legacyBreedingRoll || litterDrawBound <= 0))) {
            throw new IllegalArgumentException(
                "invalid named breeding RNG ledger");
        }
        this.mateQueryShuffle = mateQueryShuffle;
        this.mateQueryShuffleInputSize = mateQueryShuffleInputSize;
        this.legacyBreedingRoll = legacyBreedingRoll;
        this.litterDraw = litterDraw;
        this.litterDrawBound = litterDrawBound;
    }

    public boolean isMateQueryShuffleRequested()
    {
        return mateQueryShuffle;
    }

    public int getMateQueryShuffleInputSize()
    {
        return mateQueryShuffleInputSize;
    }

    public boolean hasLegacyBreedingRoll()
    {
        return legacyBreedingRoll;
    }

    public boolean hasLitterDraw()
    {
        return litterDraw;
    }

    public int getLitterDrawBound()
    {
        return litterDrawBound;
    }
}
