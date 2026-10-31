package com.mpp.integratedscripting.core.network;

import com.mpp.integratedscripting.api.network.IScript;
import com.mpp.integratedscripting.evaluate.translation.ValueTranslatorRegistry;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorInteger;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeInteger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for {@link GraalScript} after the invalidation rework
 * (docs/AUDIT.md D1).
 *
 * <p>The regression: GraalVM throws {@link IllegalStateException} for any use of
 * a {@link Value} whose {@link Context} was closed, and the old code closed the
 * context whenever a single consumer detached. These tests verify that (a) a
 * released script reports a localized {@link EvaluationException} instead, and
 * (b) detaching one consumer does not release the script at all.</p>
 */
public class GraalScriptTest {

    private ValueTranslatorRegistry registry;
    private Context context;

    @Before
    public void setUp() {
        this.registry = ValueTranslatorRegistry.getInstance();
        this.registry.reset();
        // ValueTranslators#load would also register the operator translator, which
        // initialises ID's operator registry and needs a Minecraft environment. Only
        // the translator used here is registered.
        this.registry.register(new ValueTranslatorInteger());
        this.context = Context.newBuilder("js").build();
    }

    @After
    public void tearDown() {
        if (this.context != null && !this.context.toString().isEmpty()) {
            try {
                this.context.close(true);
            } catch (RuntimeException e) {
                // Already closed by the test.
            }
        }
        this.registry.reset();
    }

    private GraalScript newScript(ScriptInvalidationDispatcher dispatcher, String expression) {
        Value value = this.context.eval("js", expression);
        return new GraalScript(this.context, value, dispatcher, 0, Paths.get("main.js"), null);
    }

    private static int integerOf(IValue value) {
        return ((ValueTypeInteger.ValueInteger) value).getRawValue();
    }

    @Test
    public void testValueIsReadableWhileTheScriptIsAlive() throws EvaluationException {
        ScriptInvalidationDispatcher dispatcher = new ScriptInvalidationDispatcher(() -> { });
        GraalScript script = newScript(dispatcher, "({ foo: 42 })");

        assertNotNull(script.getMember("foo"));
        assertEquals(42, integerOf(script.getMember("foo").getValue()));
        assertFalse(dispatcher.isReleased());
    }

    @Test
    public void testDetachingAConsumerKeepsTheScriptUsable() throws EvaluationException {
        // This detaches only one of two consumers of an unchanged script;
        // ScriptVariable#invalidate used to release the shared context here.
        ScriptInvalidationDispatcher dispatcher = new ScriptInvalidationDispatcher(() -> { });
        GraalScript script = newScript(dispatcher, "({ foo: 1 })");
        IScript.IInvalidateListener consumer = () -> { };
        script.addInvalidationListener(consumer);

        script.removeInvalidationListener(consumer);

        assertFalse(dispatcher.isReleased());
        assertNotNull(script.getMember("foo"));
        assertEquals(1, integerOf(script.getMember("foo").getValue()));
    }

    @Test
    public void testReleasedScriptFailsReadably() {
        ScriptInvalidationDispatcher[] holder = new ScriptInvalidationDispatcher[1];
        holder[0] = new ScriptInvalidationDispatcher(() -> this.context.close());
        GraalScript script = newScript(holder[0], "({ foo: 1 })");

        holder[0].releaseAndNotify();

        try {
            script.getValue();
            fail("A released script must not be evaluated again");
        } catch (EvaluationException e) {
            assertTrue("The error must name the script, got: " + e.getMessage(),
                    e.getMessage().contains("main.js"));
            assertTrue("The error must be the released-script error, got: " + e.getMessage(),
                    e.getMessage().contains("script_invalidated")
                            || e.getMessage().contains("released"));
        }
    }

    @Test
    public void testReleasedScriptMemberFailsReadably() {
        ScriptInvalidationDispatcher[] holder = new ScriptInvalidationDispatcher[1];
        holder[0] = new ScriptInvalidationDispatcher(() -> this.context.close());
        GraalScript script = newScript(holder[0], "({ foo: 1 })");

        holder[0].releaseAndNotify();

        try {
            script.getMember("foo");
            fail("A released script must not expose members");
        } catch (EvaluationException e) {
            assertTrue("The error must name the script, got: " + e.getMessage(),
                    e.getMessage().contains("main.js"));
        }
    }

}
