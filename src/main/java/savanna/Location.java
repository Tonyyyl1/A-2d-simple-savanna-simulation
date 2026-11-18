package savanna;

/**
 * Represent a location in a rectangular grid.
 *
 * @author David J. Barnes and Michael Kölling
 * @version 7.0
 */
public record Location(int row, int col)
{
    /**
     * The record default is {@code 31 * row + col}, which maps the 86,400 cells
     * of the default map onto about 7,800 distinct values. A linear form such
     * as {@code row * 8191 + col} is distinct but still lands every diagonal
     * neighbour in the same hash bucket, so the value is mixed (A-3a, A-3c).
     */
    @Override
    public int hashCode()
    {
        int hash = row * 0x9E3779B1 + col * 0x85EBCA6B;
        return hash ^ (hash >>> 15);
    }
}
