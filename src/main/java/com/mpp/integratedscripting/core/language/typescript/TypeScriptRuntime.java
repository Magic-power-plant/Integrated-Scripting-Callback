package com.mpp.integratedscripting.core.language.typescript;

import com.mpp.IntegratedScriptingCallback;
import org.apache.logging.log4j.Level;

/**
 * Availability probe for the vendored Translanguage transpiler.
 *
 * <p>Translanguage is shaded into the release jar and on the development
 * classpath, but the slim jar ({@code -slim.jar}) and a hand-assembled
 * {@code libs} setup may lack it. The language registration then skips
 * TypeScript instead of letting a {@link NoClassDefFoundError} escape from
 * {@code LanguageHandlers.load()}, which would take the JavaScipt language down
 * with it.</p>
 *
 * <p>Like {@code ScriptRuntime}, this class must never reference a Translanguage
 * type, not even in a method signature, so that it stays loadable when the
 * library is absent (docs/TYPESCRIPT-TRANSLANGUAGE.md §4).</p>
 *
 * @author rubensworks
 */
public final class TypeScriptRuntime {

    /** The class whose presence means "the Translanguage transpiler is installed". */
    public static final String PROBE_CLASS = "com.pingsu.transpiler.TypeScriptTranspiler";

    public static final String MISSING_RUNTIME_MESSAGE =
            "The Translanguage TypeScript transpiler is not on the classpath, so .ts scripts are "
            + "disabled. Use the release jar (which bundles it) or put "
            + "libs/Translanguage-2.0.3-SNAPSHOT-java8.jar on the classpath; JavaScript is unaffected.";

    private static Boolean available;
    private static String unavailableReason;

    private TypeScriptRuntime() {
    }

    /**
     * @return If the TypeScript transpiler can be used.
     */
    public static synchronized boolean isAvailable() {
        if (available == null) {
            try {
                Class.forName(PROBE_CLASS, false, TypeScriptRuntime.class.getClassLoader());
                available = true;
            } catch (ClassNotFoundException | LinkageError e) {
                available = false;
                unavailableReason = e.toString();
            }
        }
        return available;
    }

    /**
     * @return Why the transpiler is unavailable, or null when it is available.
     */
    public static synchronized String getUnavailableReason() {
        isAvailable();
        return unavailableReason;
    }

    /**
     * Log the one actionable line that explains a skipped TypeScript language.
     * Called once from the registration, never per script.
     */
    public static synchronized void logUnavailable() {
        IntegratedScriptingCallback.clog(Level.WARN, MISSING_RUNTIME_MESSAGE
                + " Cause: " + getUnavailableReason());
    }

    /**
     * Forget the probe result. Tests and reloads only.
     */
    public static synchronized void reset() {
        available = null;
        unavailableReason = null;
    }

}
