package com.mpp.integratedscripting.evaluate.translation;

import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorBoolean;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorDouble;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorInteger;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorList;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorLong;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorNbt;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorString;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeBoolean;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeDouble;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeInteger;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeLong;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeString;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Round-trip tests for the scalar translators against a real GraalVM context
 * (§8.4, §11.1 {@code ValueTranslatorsJavaScriptTests}).
 *
 * <p>Only the translators used here are registered, because
 * {@link ValueTranslators#load()} also registers the operator translator, which
 * initialises ID's operator registry and needs a running game (see the M3 notes
 * in docs/MILESTONES.md). The list and NBT translators cannot be unit tested for
 * the same class of reason: ID's {@code ValueTypeList.ValueList.of*} and
 * {@code ValueTypeNbt.ValueNbt.of*} call SRG-named vanilla methods that only
 * exist after FML's runtime deobfuscation, so their round-trips stay covered
 * in-game by §11.2 items 19, 22 and 23 (see docs/AUDIT.md §Test coverage).</p>
 */
public class ValueTranslatorsRoundTripTest {

    private ValueTranslatorRegistry registry;
    private Context context;
    private IEvaluationExceptionFactory exceptionFactory;

    @Before
    public void setUp() {
        this.registry = ValueTranslatorRegistry.getInstance();
        this.registry.reset();
        this.registry.register(new ValueTranslatorBoolean());
        this.registry.register(new ValueTranslatorInteger());
        this.registry.register(new ValueTranslatorLong());
        this.registry.register(new ValueTranslatorDouble());
        this.registry.register(new ValueTranslatorString());
        this.registry.register(new ValueTranslatorList());
        this.registry.register(new ValueTranslatorNbt());
        this.context = Context.newBuilder("js").build();
        this.exceptionFactory = EvaluationException::new;
    }

    @After
    public void tearDown() {
        if (this.context != null) {
            try {
                this.context.close(true);
            } catch (RuntimeException e) {
                // Already closed.
            }
        }
        this.registry.reset();
    }

    private IValue fromScript(String expression) throws EvaluationException {
        return this.registry.translateFromGraal(
                this.context, this.context.eval("js", expression), this.exceptionFactory);
    }

    @Test
    public void testBooleanRoundTrip() throws EvaluationException {
        IValue value = fromScript("true");
        assertEquals(ValueTypes.BOOLEAN, value.getType());
        assertTrue(((ValueTypeBoolean.ValueBoolean) value).getRawValue());

        Value scriptValue = this.registry.translateToGraal(
                this.context, ValueTypeBoolean.ValueBoolean.of(false), this.exceptionFactory);
        assertTrue(scriptValue.isBoolean());
        assertEquals(false, scriptValue.asBoolean());
    }

    @Test
    public void testIntegerRoundTrip() throws EvaluationException {
        IValue value = fromScript("42");
        assertEquals(ValueTypes.INTEGER, value.getType());
        assertEquals(42, ((ValueTypeInteger.ValueInteger) value).getRawValue());

        Value scriptValue = this.registry.translateToGraal(
                this.context, ValueTypeInteger.ValueInteger.of(-7), this.exceptionFactory);
        assertEquals(-7, scriptValue.asInt());
    }

    @Test
    public void testIntegerIsPreferredOverLongAndDouble() throws EvaluationException {
        // Registration order gives int priority: a JS number that fits an int must
        // not become a long or a double (§8.4).
        assertEquals(ValueTypes.INTEGER, fromScript("1").getType());
        assertEquals(ValueTypes.INTEGER, fromScript("2147483647").getType());
    }

    @Test
    public void testLongIsUsedWhenItDoesNotFitAnInt() throws EvaluationException {
        IValue value = fromScript("2147483648");
        assertEquals(ValueTypes.LONG, value.getType());
        assertEquals(2147483648L, ((ValueTypeLong.ValueLong) value).getRawValue());
    }

    @Test
    public void testDoubleRoundTrip() throws EvaluationException {
        IValue value = fromScript("1.5");
        // 1.5 fits neither an int nor a long, so the double translator handles it.
        assertEquals(ValueTypes.DOUBLE, value.getType());
        assertEquals(1.5D, ((ValueTypeDouble.ValueDouble) value).getRawValue(), 0.0D);

        Value scriptValue = this.registry.translateToGraal(
                this.context, ValueTypeDouble.ValueDouble.of(0.25D), this.exceptionFactory);
        assertEquals(0.25D, scriptValue.asDouble(), 0.0D);
    }

    @Test
    public void testStringRoundTrip() throws EvaluationException {
        IValue value = fromScript("'hello'");
        assertEquals(ValueTypes.STRING, value.getType());
        assertEquals("hello", ((ValueTypeString.ValueString) value).getRawValue());

        Value scriptValue = this.registry.translateToGraal(
                this.context, ValueTypeString.ValueString.of("héllo\nworld"), this.exceptionFactory);
        assertEquals("héllo\nworld", scriptValue.asString());
    }

    @Test
    public void testUnknownScriptValueIsReported() {
        try {
            // A JS null is not handled by any translator registered here: it has no
            // members, is no array, is not a scalar and cannot be executed.
            this.registry.translateFromGraal(
                    this.context, this.context.eval("js", "null"), this.exceptionFactory);
            fail("An untranslatable script value must be reported");
        } catch (EvaluationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("unknown_from_graal")
                    || e.getMessage().toLowerCase().contains("translat"));
        }
    }
}
