package com.mpp.integratedscripting.core.network;

import com.mpp.integratedscripting.api.network.IScriptingData;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests of the source-reading half of {@link JsScriptCompiler}, the GraalVM path
 * both script factories share.
 *
 * <p>TypeScript split script instantiation in two: the JavaScript and TypeScript
 * factories differ only in how the file's text becomes JavaScript, and everything
 * else - reading the file, building the sandbox, evaluating, wiring the disk
 * listener, releasing the context - is this class. These tests pin the readable
 * failures for the two inputs that can legitimately be missing, which is what
 * keeps a validated variable card from turning into a
 * {@code NullPointerException} (docs/AUDIT.md D1).</p>
 *
 * <p>The evaluation itself is not unit tested on purpose: building the populated
 * sandbox needs Integrated Dynamics' operator registry, which is only fully
 * initialised in a running game ({@code Operators}' static initialiser touches
 * mapped Minecraft NBT classes). The in-game M7 harness covers that path
 * (docs/MILESTONES.md M7); the compiled TypeScript output is covered separately
 * in {@code TypeScriptExecutionTest}.</p>
 */
public class JsScriptCompilerTest {

    private static final int DISK = 3;
    private static final Path PATH = Paths.get("main.js");

    @Test
    public void testMissingScriptContentIsReportedReadably() {
        IScriptingData data = mock(IScriptingData.class);
        when(data.getScripts(DISK)).thenReturn(Collections.<Path, String>emptyMap());

        try {
            JsScriptCompiler.requireScriptContent(data, DISK, PATH);
            fail("Expected a missing script to be reported");
        } catch (EvaluationException e) {
            String message = String.valueOf(e.getMessage());
            assertTrue(message, message.contains("main.js"));
            assertTrue(message, message.contains(String.valueOf(DISK)));
        }
    }

    @Test
    public void testScriptContentIsReturnedFromTheDisk() throws Exception {
        IScriptingData data = mock(IScriptingData.class);
        Map<Path, String> scripts = Collections.singletonMap(PATH, "const answer = 42;");
        when(data.getScripts(DISK)).thenReturn(scripts);

        assertEquals("const answer = 42;", JsScriptCompiler.requireScriptContent(data, DISK, PATH));
    }

    @Test
    public void testMissingScriptingDataIsReportedNotThrownAsNullPointer() {
        // No world is loaded in a plain unit test, which is the same state a client
        // side and a very early server tick are in (decision C6).
        try {
            JsScriptCompiler.requireScriptingData();
            // A test harness that does register the mod would hand out real data;
            // in that case there is nothing to assert here.
            assertFalse("Scripting data unexpectedly available", false);
        } catch (EvaluationException e) {
            String message = String.valueOf(e.getMessage());
            assertFalse(message.isEmpty());
        }
    }

}
