package com.mpp.integratedscripting.evaluate;

import org.apache.logging.log4j.Level;
import com.mpp.IntegratedScriptingCallback;

/**
 * Availability probe for the GraalVM JavaScript runtime.
 *
 * <p>1.12.2 port addition (docs/AUDIT.md D6). The GraalVM runtime is shipped as
 * a separate library mod (decision B2/B3 in docs/PORTING-1.12.2.md), so it is
 * {@code compileOnly} here and not a hard Forge dependency (dev runs get it from
 * the Gradle classpath, production gets it from
 * {@code integratedscriptingcallbacklibs}). Previously a missing library mod
 * made {@code init()} die with a bare {@link NoClassDefFoundError}; the probe
 * turns that into one actionable log line and a localized in-game error, while
 * discs, drives and the terminal keep working.</p>
 *
 * <p>This class must never reference a GraalVM type, not even in a method
 * signature, so that it stays loadable when the runtime is absent.</p>
 *
 * @author rubensworks
 */
public final class ScriptRuntime {

    /** The class whose presence means "the GraalVM runtime is installed". */
    public static final String PROBE_CLASS = "org.graalvm.polyglot.Context";

    public static final String MISSING_RUNTIME_MESSAGE =
            "The GraalVM JavaScript runtime is not on the classpath. Install the companion mod "
            + "\"Integrated Scripting Callback Libs\" (integratedscriptingcallbacklibs) next to this mod; "
            + "the rest of Integrated Scripting Callback works without it.";

    private static Boolean available;
    private static String unavailableReason;

    private ScriptRuntime() {
    }

    /**
     * @return If the GraalVM JavaScript runtime can be used.
     */
    public static synchronized boolean isAvailable() {
        if (available == null) {
            try {
                Class.forName(PROBE_CLASS, false, ScriptRuntime.class.getClassLoader());
                available = true;
            } catch (ClassNotFoundException | LinkageError e) {
                available = false;
                unavailableReason = e.toString();
            }
        }
        return available;
    }

    /**
     * @return Why the runtime is unavailable, or null when it is available.
     */
    public static synchronized String getUnavailableReason() {
        isAvailable();
        return unavailableReason;
    }

    /**
     * Record that initialising the runtime failed for a reason other than a
     * missing class (for example a Truffle linkage error), and log one
     * actionable line.
     * @param error The linkage failure.
     */
    public static synchronized void reportUnavailable(Throwable error) {
        available = false;
        unavailableReason = error.toString();
        IntegratedScriptingCallback.clog(Level.ERROR, MISSING_RUNTIME_MESSAGE
                + " Cause: " + error);
    }

    /**
     * Forget the probe result. Tests and reloads only.
     */
    public static synchronized void reset() {
        available = null;
        unavailableReason = null;
    }

}
