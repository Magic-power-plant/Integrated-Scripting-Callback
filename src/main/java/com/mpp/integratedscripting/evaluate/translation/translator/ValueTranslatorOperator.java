package com.mpp.integratedscripting.evaluate.translation.translator;

import com.mpp.Reference;
import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslator;
import com.mpp.integratedscripting.evaluate.translation.ValueTranslators;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.operator.IOperator;
import org.cyclops.integrateddynamics.api.evaluate.operator.IOperatorSerializer;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.api.evaluate.variable.IVariable;
import org.cyclops.integrateddynamics.api.logicprogrammer.IConfigRenderPattern;
import org.cyclops.integrateddynamics.core.evaluate.operator.OperatorBase;
import org.cyclops.integrateddynamics.core.evaluate.operator.Operators;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeOperator;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import java.util.Arrays;

/**
 * Translates ID operator values to and from Graal functions.
 *
 * <p>1.12.2 differences (docs/PORTING-1.12.2.md §7.11): {@link OperatorBase}'s
 * constructor takes {@code (symbol, name, inputTypes, outputType, function,
 * renderPattern)}, and the operator serializer is String based.</p>
 *
 * @author rubensworks
 */
public class ValueTranslatorOperator implements IValueTranslator<ValueTypeOperator.ValueOperator> {

    public static final String UNIQUE_NAME = Reference.MOD_ID + ":graal";

    static {
        Operators.REGISTRY.registerSerializer(new GraalOperator.Serializer());
    }

    @Override
    public IValueType<?> getValueType() {
        return ValueTypes.OPERATOR;
    }

    @Override
    public boolean canHandleGraalValue(Value value) {
        return value.canExecute();
    }

    @Override
    public boolean canTranslateNbt() {
        return false;
    }

    @Override
    public Value translateToGraal(Context context, ValueTypeOperator.ValueOperator value,
                                  IEvaluationExceptionFactory exceptionFactory) {
        return context.asValue(new OperatorProxyExecutable(context, value, exceptionFactory));
    }

    @Override
    public ValueTypeOperator.ValueOperator translateFromGraal(Context context, Value value,
                                                              IEvaluationExceptionFactory exceptionFactory)
            throws EvaluationException {
        // Unwrap the value if it was translated in the opposite direction before.
        if (value.isProxyObject()) {
            try {
                OperatorProxyExecutable cast = value.asProxyObject();
                return cast.getValue();
            } catch (ClassCastException classCastException) {
                // Fallback to the generic case below.
            }
        }

        // Determine the input args of the function.
        // 1.12.2/1.12 port fix (docs/AUDIT.md D5): an executable that is not a guest
        // language function (a host lambda or proxy executable) has no "length"
        // member and GraalVM throws UnsupportedOperationException for it, so the
        // arity is probed defensively instead of assuming a JS function.
        int argCount = getArity(value);
        IValueType[] inputValueTypes = new IValueType[argCount];
        Arrays.fill(inputValueTypes, ValueTypes.CATEGORY_ANY);

        return ValueTypeOperator.ValueOperator.of(new GraalOperator(inputValueTypes, args -> {
            IVariable[] variables = args.getVariables();
            Value[] values = new Value[variables.length];
            context.resetLimits();
            for (int i = 0; i < variables.length; i++) {
                try {
                    values[i] = ValueTranslators.REGISTRY.translateToGraal(
                            context, variables[i].getValue(), exceptionFactory);
                } catch (PolyglotException e) {
                    throw exceptionFactory.createError(e.getMessage());
                }
            }
            Value returnValue;
            try {
                returnValue = value.execute((Object[]) values);
            } catch (PolyglotException e) {
                throw exceptionFactory.createError(e.getMessage());
            }
            return ValueTranslators.REGISTRY.translateFromGraal(context, returnValue, exceptionFactory);
        }));
    }

    @Override
    public NBTBase translateToNbt(Context context, ValueTypeOperator.ValueOperator value,
                                  IEvaluationExceptionFactory exceptionFactory) throws EvaluationException {
        throw exceptionFactory.createError(L10NHelpers.localize(
                "valuetype.integratedscripting.error.translation.unsupported_translateToNbt",
                L10NHelpers.localize(value.getType().getTranslationKey()),
                value.getType().toCompactString(value)));
    }

    /**
     * @param function An executable Graal value.
     * @return The declared arity of the function, or 0 when it cannot be determined.
     */
    private static int getArity(Value function) {
        try {
            if (function.hasMembers()) {
                Value length = function.getMember("length");
                if (length != null && length.fitsInInt()) {
                    return Math.max(0, length.asInt());
                }
            }
        } catch (RuntimeException e) {
            // A host executable without a "length" member: treat it as variadic zero-arity.
            return 0;
        }
        return 0;
    }

    /**
     * An ID operator wrapping a JavaScript function. Its input types are all
     * {@code CATEGORY_ANY}; the JS function determines the actual arity.
     *
     * <p>1.12.2's {@code OperatorBase} validates that the render pattern has
     * exactly one slot per input type (M7 finding: {@code IConfigRenderPattern.NONE}
     * throws {@code IllegalArgumentException} for any function with arguments).
     * The pattern only matters for the Logic Programmer UI, so a matching generic
     * pattern is generated for the arity of the JavaScript function.</p>
     */
    public static class GraalOperator extends OperatorBase {

        protected GraalOperator(IValueType[] inputTypes, IFunction function) {
            super("graal", "graal_function", inputTypes, ValueTypes.CATEGORY_ANY,
                    function, createRenderPattern(inputTypes.length));
        }

        /**
         * @param inputs The number of input types of the operator.
         * @return A render pattern with exactly that many slots.
         */
        private static IConfigRenderPattern createRenderPattern(int inputs) {
            switch (inputs) {
                case 0:
                    return IConfigRenderPattern.NONE;
                case 1:
                    return IConfigRenderPattern.PREFIX_1;
                case 2:
                    return IConfigRenderPattern.PREFIX_2;
                case 3:
                    return IConfigRenderPattern.PREFIX_3;
                default: {
                    // ID only ships fixed patterns up to three slots; script functions
                    // can have more, so build a generic one-slot-per-argument pattern.
                    @SuppressWarnings("unchecked")
                    org.apache.commons.lang3.tuple.Pair<Integer, Integer>[] slots =
                            new org.apache.commons.lang3.tuple.Pair[inputs];
                    for (int i = 0; i < inputs; i++) {
                        slots[i] = org.apache.commons.lang3.tuple.Pair.of(i * 20, 0);
                    }
                    return new IConfigRenderPattern.Base(inputs * 20, 18, slots,
                            org.apache.commons.lang3.tuple.Pair.of(0, 0));
                }
            }
        }

        @Override
        protected String getUnlocalizedType() {
            return "integratedscript";
        }

        @Override
        protected String getModId() {
            return Reference.MOD_ID;
        }

        @Override
        public String getUniqueName() {
            return UNIQUE_NAME;
        }

        public static class Serializer implements IOperatorSerializer<GraalOperator> {

            /**
             * 1.12.2's {@code OperatorRegistry#deserialize} splits the serialized value
             * on {@code ':'} and uses the first token as the serializer lookup key, so a
             * serializer name must not contain a colon (M7 finding: a colon-named
             * serializer produced "No serializer was found to deserialize").
             */
            public static final String SERIALIZER_NAME = Reference.MOD_ID + "_graal";

            @Override
            public boolean canHandle(IOperator operator) {
                return operator instanceof GraalOperator;
            }

            @Override
            public String getUniqueName() {
                return SERIALIZER_NAME;
            }

            @Override
            public String serialize(GraalOperator operator) {
                NBTTagList list = new NBTTagList();
                for (IValueType inputType : operator.getInputTypes()) {
                    list.appendTag(new NBTTagString(inputType.getTranslationKey()));
                }
                NBTTagCompound tag = new NBTTagCompound();
                tag.setTag("inputTypes", list);
                return tag.toString();
            }

            @Override
            public GraalOperator deserialize(String value) throws EvaluationException {
                try {
                    NBTTagCompound tag = net.minecraft.nbt.JsonToNBT.getTagFromJson(value);
                    // 8 = NBT string tag id in 1.12.2; no public constants exist.
                    NBTTagList list = tag.getTagList("inputTypes", 8);
                    IValueType[] inputTypes = new IValueType[list.tagCount()];
                    for (int i = 0; i < list.tagCount(); i++) {
                        inputTypes[i] = ValueTypes.REGISTRY.getValueType(list.getStringTagAt(i));
                    }
                    return new GraalOperator(inputTypes, variables -> {
                        throw new EvaluationException(L10NHelpers.localize(
                                "operator.integratedscripting.error.no_graal_serialization"));
                    });
                } catch (net.minecraft.nbt.NBTException e) {
                    throw new EvaluationException(e.getMessage());
                }
            }

        }

    }

}
