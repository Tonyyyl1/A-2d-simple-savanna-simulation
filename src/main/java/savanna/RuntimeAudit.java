package savanna;

/**
 * Runtime audit gate for high-frequency defensive integrity checks.
 *
 * <p>Controlled by system property {@code -Dsavanna.audit=on|off|every:N} and
 * environment variable {@code SAVANNA_AUDIT_FORCE} (same values; it wins over the
 * property). Default is full audit on every step.</p>
 *
 * <ul>
 *   <li>{@code on}: every audit runs on every step ({@link #FULL} is true).</li>
 *   <li>{@code off}: no audit runs ({@link #FULL} is false, no sampling).</li>
 *   <li>{@code every:N} ({@code N >= 1}): the full audit runs on step 1 (so a bad initial
 *       layout is reported at once) and on the steps whose number is a multiple of
 *       {@code N}, and is skipped on all other steps. The sweeps use
 *       {@code every:50}: a long run still checks the whole step contract periodically while
 *       paying about one fiftieth of the audit cost.</li>
 * </ul>
 *
 * <p>Call sites ask {@link #enabled()}; with {@code on} and {@code off} the answer is a
 * constant the JIT folds away. The audit decides only whether checks run, never what the
 * simulation does, so results are identical in every mode.</p>
 */
public final class RuntimeAudit
{
    private RuntimeAudit()
    {
    }

    private static final String PROPERTY = "savanna.audit";
    private static final String ENV_FORCE = "SAVANNA_AUDIT_FORCE";

    /** Parsed mode: {@code full} audits every step, {@code period > 0} audits every period-th step. */
    static final class Mode
    {
        final boolean full;
        final int period;

        Mode(boolean full, int period)
        {
            this.full = full;
            this.period = period;
        }
    }

    private static final Mode MODE = resolve();

    /** True when every step is audited (the default). */
    public static final boolean FULL = MODE.full;

    private static final int PERIOD = MODE.period;

    /** Set at the start of each step in sampling mode; read by the audit call sites. */
    private static volatile boolean sampledStepActive;

    /** True when the step now running must run its audits. */
    public static boolean enabled()
    {
        return FULL || (PERIOD > 0 && sampledStepActive);
    }

    /** Called once at the start of every step with the step number; a no-op unless sampling. */
    public static void beginStep(int step)
    {
        if(PERIOD > 0) {
            sampledStepActive = isSampledStep(step, PERIOD);
        }
    }

    static boolean isSampledStep(int step, int period)
    {
        return period > 0 && (step == 1 || step % period == 0);
    }

    private static Mode resolve()
    {
        String force = System.getenv(ENV_FORCE);
        if(force != null && !force.isBlank()) {
            return parse(force);
        }
        String property = System.getProperty(PROPERTY);
        if(property != null && !property.isBlank()) {
            return parse(property);
        }
        return new Mode(true, 0);
    }

    /** Parses {@code on|true}, {@code off|false} and {@code every:N}; anything else means on. */
    static Mode parse(String text)
    {
        String value = text.trim();
        if("off".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return new Mode(false, 0);
        }
        if(value.regionMatches(true, 0, "every:", 0, 6)) {
            try {
                int period = Integer.parseInt(value.substring(6).trim());
                if(period >= 1) {
                    return period == 1 ? new Mode(true, 0) : new Mode(false, period);
                }
            }
            catch(NumberFormatException ignored) {
                // fall through to the safe default
            }
        }
        return new Mode(true, 0);
    }
}
