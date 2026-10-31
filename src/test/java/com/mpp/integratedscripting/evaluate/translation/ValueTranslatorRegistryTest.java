package com.mpp.integratedscripting.evaluate.translation;

import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslator;
import net.minecraft.nbt.NBTBase;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueCastRegistry;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.api.logicprogrammer.IValueTypeLogicProgrammerElement;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Unit tests for the translator registry semantics that do not need a running
 * Minecraft/ID runtime: registration, lookup and the registration-order
 * dispatch described in docs/PORTING-1.12.2.md §8.4.
 */
public class ValueTranslatorRegistryTest {

    private ValueTranslatorRegistry registry;
    private Context context;
    private IEvaluationExceptionFactory exceptionFactory;

    @Before
    public void setUp() {
        registry = ValueTranslatorRegistry.getInstance();
        registry.reset();
        context = Context.newBuilder("js").build();
        exceptionFactory = EvaluationException::new;
    }

    @After
    public void tearDown() {
        context.close();
        registry.reset();
    }

    @Test
    public void testRegistrationAndValueTypeLookup() {
        FakeType type = new FakeType("a");
        FakeTranslator translator = new FakeTranslator(type, 0);
        registry.register(translator);

        assertSame(translator, registry.getValueTypeTranslator(type));
        assertNull(registry.getValueTypeTranslator(new FakeType("b")));
    }

    @Test
    public void testDispatchFollowsRegistrationOrder() throws EvaluationException {
        FakeType first = new FakeType("first");
        FakeType second = new FakeType("second");
        registry.register(new FakeTranslator(first, 0));
        registry.register(new FakeTranslator(second, 1000));

        // Both translators accept JS numbers; the first registered must win.
        IValue translated = registry.translateFromGraal(context, context.asValue(7), exceptionFactory);
        assertEquals(first, translated.getType());
        assertEquals(7, ((FakeValue) translated).getRawValue());
    }

    @Test
    public void testTranslateToGraal() throws EvaluationException {
        FakeType type = new FakeType("a");
        registry.register(new FakeTranslator(type, 0));

        Value value = registry.translateToGraal(context, new FakeValue(type, 41), exceptionFactory);
        assertEquals(42, value.asInt());
    }

    @Test(expected = EvaluationException.class)
    public void testTranslateToGraalOfUnknownType() throws EvaluationException {
        registry.translateToGraal(context, new FakeValue(new FakeType("unknown"), 1), exceptionFactory);
    }

    @Test(expected = EvaluationException.class)
    public void testTranslateToNbtOfNonNbtTranslator() throws EvaluationException {
        FakeType type = new FakeType("a");
        registry.register(new FakeTranslator(type, 0));
        registry.translateToNbtTag(context, new FakeValue(type, 1), exceptionFactory);
    }

    private static class FakeType implements IValueType<FakeValue> {

        private final String name;

        private FakeType(String name) {
            this.name = name;
        }

        @Override
        public boolean isCategory() {
            return false;
        }

        @Override
        public boolean isObject() {
            return false;
        }

        @Override
        public FakeValue getDefault() {
            return new FakeValue(this, 0);
        }

        @Override
        public String getTypeName() {
            return name;
        }

        @Override
        public String getTranslationKey() {
            return "valuetype.integratedscripting.test." + name;
        }

        @Override
        public void loadTooltip(List<String> lines, boolean appendOptionalInfo, FakeValue value) {
        }

        @Override
        public String toCompactString(FakeValue value) {
            return String.valueOf(value.getRawValue());
        }

        @Override
        public int getDisplayColor() {
            return 0;
        }

        @Override
        public String getDisplayColorFormat() {
            return "";
        }

        @Override
        public boolean correspondsTo(IValueType<?> valueType) {
            return valueType == this;
        }

        @Override
        public String serialize(FakeValue value) {
            return String.valueOf(value.getRawValue());
        }

        @Override
        public L10NHelpers.UnlocalizedString canDeserialize(String value) {
            return null;
        }

        @Override
        public FakeValue deserialize(String value) {
            return new FakeValue(this, Integer.parseInt(value));
        }

        @Override
        public FakeValue materialize(FakeValue value) {
            return value;
        }

        @Override
        public IValueTypeLogicProgrammerElement createLogicProgrammerElement() {
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public FakeValue cast(IValue value) {
            return (FakeValue) value;
        }

    }

    private static class FakeValue implements IValue {

        private final FakeType type;
        private final int rawValue;

        private FakeValue(FakeType type, int rawValue) {
            this.type = type;
            this.rawValue = rawValue;
        }

        private int getRawValue() {
            return rawValue;
        }

        @Override
        public IValueType<?> getType() {
            return type;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <V extends IValue> V cast(IValueType<V> valueType) throws IValueCastRegistry.ValueCastException {
            return (V) this;
        }

        @Override
        public <V extends IValue> boolean canCast(IValueType<V> valueType) {
            return valueType == type;
        }

    }

    private static class FakeTranslator implements IValueTranslator<FakeValue> {

        private final FakeType type;
        private final int offset;

        private FakeTranslator(FakeType type, int offset) {
            this.type = type;
            this.offset = offset;
        }

        @Override
        public IValueType<?> getValueType() {
            return type;
        }

        @Override
        public boolean canHandleGraalValue(Value value) {
            return value.fitsInInt();
        }

        @Override
        public boolean canTranslateNbt() {
            return false;
        }

        @Override
        public Value translateToGraal(Context context, FakeValue value,
                                      IEvaluationExceptionFactory exceptionFactory) {
            return context.asValue(value.getRawValue() + 1);
        }

        @Override
        public FakeValue translateFromGraal(Context context, Value value,
                                            IEvaluationExceptionFactory exceptionFactory) {
            return new FakeValue(type, value.asInt() + offset);
        }

        @Override
        public NBTBase translateToNbt(Context context, FakeValue value,
                                      IEvaluationExceptionFactory exceptionFactory) {
            return null;
        }

    }

}
