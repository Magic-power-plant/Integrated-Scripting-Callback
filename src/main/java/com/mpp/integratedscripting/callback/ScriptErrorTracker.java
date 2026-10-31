package com.mpp.integratedscripting.callback;

import com.google.common.collect.Maps;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;

import java.nio.file.Path;
import java.util.Map;

/**
 * 1.12.2-only: rate limits the logging of script errors.
 *
 * <p>1.12 variables are evaluated lazily and {@link EvaluationException} has no
 * {@code resolve()} support, so the reference implementation's weak-reference
 * resolution machinery is not needed. Errors are localized at creation time and
 * this tracker only keeps the log readable (docs/PORTING-1.12.2.md §5.6).</p>
 *
 * @author rubensworks
 */
public final class ScriptErrorTracker {

    public static final long LOG_INTERVAL_MS = 5000L;

    private static final Logger LOGGER = LogManager.getLogger("IntegratedScriptingCallback");
    private static final Map<String, Long> LAST_LOG = Maps.newHashMap();

    private ScriptErrorTracker() {
    }

    /**
     * Log the given exception at most once per interval per disk/path pair.
     * @param exception The exception to log.
     * @param disk The disk id.
     * @param path The script path relative to the disk.
     * @return The given exception, for chaining.
     */
    public static synchronized EvaluationException track(EvaluationException exception, int disk, Path path) {
        String key = disk + ":" + (path == null ? "" : path.toString());
        long now = System.currentTimeMillis();
        Long last = LAST_LOG.get(key);
        if (last == null || now - last > LOG_INTERVAL_MS) {
            LAST_LOG.put(key, now);
            if (LOGGER.isEnabled(Level.WARN)) {
                LOGGER.warn(String.format("Script error on disk %s path %s: %s",
                        disk, path == null ? "" : path.toString(), exception.getMessage()));
            }
        }
        return exception;
    }

    /**
     * Forget all throttling state.
     */
    public static synchronized void reset() {
        LAST_LOG.clear();
    }

    /**
     * @return The number of disk/path pairs that have been logged. Diagnostics/tests only.
     */
    public static synchronized int getTrackedKeyCount() {
        return LAST_LOG.size();
    }

}
