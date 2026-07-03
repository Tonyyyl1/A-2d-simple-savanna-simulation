/**
 * Keeps track of the time of day.
 */
public class SimulationClock
{
    private static final int HOURS_PER_DAY = 24;

    private int step;

    /**
     * Advance the clock to a simulation step.
     */
    public void setStep(int step)
    {
        this.step = step;
    }

    /**
     * @return The hour within the current simulated day.
     */
    public int getHour()
    {
        return step % HOURS_PER_DAY;
    }

    /**
     * @return The current coarse day phase.
     */
    public DayPhase getPhase()
    {
        int hour = getHour();
        if(hour >= 5 && hour <= 7) {
            return DayPhase.DAWN;
        }
        else if(hour >= 8 && hour <= 17) {
            return DayPhase.DAY;
        }
        else if(hour >= 18 && hour <= 20) {
            return DayPhase.DUSK;
        }
        else {
            return DayPhase.NIGHT;
        }
    }

    /**
     * @return A display string for the GUI and logs.
     */
    public String getDisplayText()
    {
        return "Hour " + getHour() + " (" + getPhase() + ")";
    }
}
