package com.mpp.integratedscripting.evaluate;

import com.mpp.GeneralConfig;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Smoke tests for the GraalVM 21.3 runtime on Java 8, which is the highest risk
 * item of the port (docs/PORTING-1.12.2.md R1) and the basis of §8.3.
 */
public class GraalRuntimeTest {

    @Test
    public void testEvaluateSimpleScript() {
        try (Context context = ScriptHelpers.createBaseContext(null)) {
            assertEquals(2, context.eval("js", "1 + 1").asInt());
            assertEquals("abc", context.eval("js", "'abc'").asString());
        }
    }

    @Test
    public void testStatementLimitIsEnforced() {
        int previousLimit = GeneralConfig.graalStatementLimit;
        GeneralConfig.graalStatementLimit = 100;
        try (Context context = ScriptHelpers.createBaseContext(null)) {
            try {
                context.eval("js", "var x = 0; while (true) { x = x + 1; }");
                fail("Expected the Graal statement limit to abort the infinite loop.");
            } catch (PolyglotException e) {
                assertTrue("Expected a resource limit error, got: " + e.getMessage(),
                        e.isResourceExhausted() || e.getMessage().toLowerCase().contains("limit"));
            }
        } finally {
            GeneralConfig.graalStatementLimit = previousLimit;
        }
    }

    @Test
    public void testPathExtension() {
        assertEquals("js", ScriptHelpers.getPathExtension(java.nio.file.Paths.get("a/b/c.js")));
        assertEquals("js", ScriptHelpers.getPathExtension(java.nio.file.Paths.get("c.JS")));
        assertNull(ScriptHelpers.getPathExtension(java.nio.file.Paths.get("noextension")));
    }

}
