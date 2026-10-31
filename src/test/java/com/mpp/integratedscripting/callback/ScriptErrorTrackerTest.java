package com.mpp.integratedscripting.callback;

import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Unit tests for the script-error log throttling that replaces the 1.20
 * exception-resolution helper (docs/PORTING-1.12.2.md §5.6).
 */
public class ScriptErrorTrackerTest {

    @Before
    public void setUp() {
        ScriptErrorTracker.reset();
    }

    @Test
    public void testThrottlesPerDiskAndPath() {
        assertEquals(0, ScriptErrorTracker.getTrackedKeyCount());

        ScriptErrorTracker.track(new EvaluationException("first"), 0, Paths.get("a.js"));
        assertEquals(1, ScriptErrorTracker.getTrackedKeyCount());

        // Same disk/path within the interval must not create a second entry.
        ScriptErrorTracker.track(new EvaluationException("second"), 0, Paths.get("a.js"));
        assertEquals(1, ScriptErrorTracker.getTrackedKeyCount());

        // Another path is tracked separately.
        ScriptErrorTracker.track(new EvaluationException("third"), 0, Paths.get("b.js"));
        assertEquals(2, ScriptErrorTracker.getTrackedKeyCount());

        // Another disk is tracked separately.
        ScriptErrorTracker.track(new EvaluationException("fourth"), 1, Paths.get("a.js"));
        assertEquals(3, ScriptErrorTracker.getTrackedKeyCount());
    }

    @Test
    public void testReturnsTheGivenException() {
        EvaluationException exception = new EvaluationException("boom");
        assertSame(exception, ScriptErrorTracker.track(exception, 2, Paths.get("c.js")));
    }

    @Test
    public void testResetClearsState() {
        ScriptErrorTracker.track(new EvaluationException("boom"), 0, Paths.get("a.js"));
        ScriptErrorTracker.reset();
        assertEquals(0, ScriptErrorTracker.getTrackedKeyCount());
    }

}
