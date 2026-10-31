package com.mpp.integratedscripting.evaluate.translation;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslator;
import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslatorRegistry;
import com.mpp.integratedscripting.callback.NbtBridge;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Registry of value translators.
 *
 * <p>Registration order is part of the semantics: the first translator whose
 * {@code canHandleGraalValue} matches is used (docs/PORTING-1.12.2.md §8.4).</p>
 *
 * @author rubensworks
 */
public class ValueTranslatorRegistry implements IValueTranslatorRegistry {

    private static final ValueTranslatorRegistry INSTANCE = new ValueTranslatorRegistry();

    private final List<IValueTranslator> translators = Lists.newArrayList();
    private final Map<IValueType<?>, IValueTranslator> valueTypeTranslators = Maps.newIdentityHashMap();

    private ValueTranslatorRegistry() {
    }

    /**
     * @return The unique instance.
     */
    public static ValueTranslatorRegistry getInstance() {
        return INSTANCE;
    }

    @Override
    public void register(IValueTranslator translator) {
        translators.add(translator);
        valueTypeTranslators.put(translator.getValueType(), translator);
    }

    @Nullable
    @Override
    public <V extends IValue> IValueTranslator getValueTypeTranslator(IValueType<V> valueType) {
        return valueTypeTranslators.get(valueType);
    }

    @Override
    public <V extends IValue> Value translateToGraal(Context context, V value,
                                                     IEvaluationExceptionFactory exceptionFactory)
            throws EvaluationException {
        IValueTranslator translator = getValueTypeTranslator(value.getType());
        if (translator == null) {
            throw exceptionFactory.createError(L10NHelpers.localize(
                    "valuetype.integratedscripting.error.translation.unknown_to_graal",
                    L10NHelpers.localize(value.getType().getTranslationKey())));
        }
        return translator.translateToGraal(context, value, exceptionFactory);
    }

    @Nullable
    @Override
    public IValueTranslator getScriptValueTranslator(Value scriptValue) {
        for (IValueTranslator translator : translators) {
            if (translator.canHandleGraalValue(scriptValue)) {
                return translator;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V extends IValue> V translateFromGraal(Context context, Value value,
                                                   IEvaluationExceptionFactory exceptionFactory)
            throws EvaluationException {
        IValueTranslator translator = getScriptValueTranslator(value);
        if (translator == null) {
            throw exceptionFactory.createError(L10NHelpers.localize(
                    "valuetype.integratedscripting.error.translation.unknown_from_graal",
                    String.valueOf(value)));
        }
        return (V) translator.translateFromGraal(context, value, exceptionFactory);
    }

    @Override
    public <V extends IValue> net.minecraft.nbt.NBTBase translateToNbtTag(
            Context context, V value, IEvaluationExceptionFactory exceptionFactory)
            throws EvaluationException {
        IValueTranslator translator = getValueTypeTranslator(value.getType());
        if (translator == null) {
            throw exceptionFactory.createError(L10NHelpers.localize(
                    "valuetype.integratedscripting.error.translation.unknown_to_graal_nbt",
                    L10NHelpers.localize(value.getType().getTranslationKey())));
        }
        if (!translator.canTranslateNbt()) {
            throw exceptionFactory.createError(L10NHelpers.localize(
                    "valuetype.integratedscripting.error.translation.nbt_unmatched",
                    L10NHelpers.localize(value.getType().getTranslationKey())));
        }
        return translator.translateToNbt(context, value, exceptionFactory);
    }

    @Override
    public <V extends IValue> net.minecraft.nbt.NBTTagCompound translateToNbt(
            Context context, V value, IEvaluationExceptionFactory exceptionFactory)
            throws EvaluationException {
        return NbtBridge.toValueNbt(context, value, exceptionFactory).getRawValue();
    }

    /**
     * @return A snapshot of the registered translators in registration order.
     */
    public List<IValueTranslator> getTranslators() {
        return Lists.newArrayList(translators);
    }

    /**
     * Remove all registered translators. Only used by tests.
     */
    public void reset() {
        translators.clear();
        valueTypeTranslators.clear();
    }

}
