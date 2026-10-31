package com.mpp.integratedscripting.evaluate.translation.translator;

import com.google.common.collect.Lists;
import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslator;
import com.mpp.integratedscripting.evaluate.translation.ValueTranslators;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeList;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * @author rubensworks
 */
public class ValueTranslatorList implements IValueTranslator<ValueTypeList.ValueList> {

    @Override
    public IValueType<?> getValueType() {
        return ValueTypes.LIST;
    }

    @Override
    public boolean canHandleGraalValue(Value value) {
        return value.hasArrayElements();
    }

    @Override
    public boolean canTranslateNbt() {
        return true;
    }

    @Override
    public Value translateToGraal(Context context, ValueTypeList.ValueList value,
                                  IEvaluationExceptionFactory exceptionFactory) throws EvaluationException {
        if (value.getRawValue().isInfinite()) {
            throw exceptionFactory.createError(
                    "valuetype.integratedscripting.error.translation.list_infinite");
        }

        // The docs suggest context.asValue(proxy), but that does not work reliably;
        // build an explicit list of translated values instead.
        List<Value> list = new ArrayList<>();
        for (IValue innerValue : (Iterable<IValue>) value.getRawValue()) {
            list.add(ValueTranslators.REGISTRY.translateToGraal(context, innerValue, exceptionFactory));
        }
        return context.asValue(list);
    }

    @Override
    public ValueTypeList.ValueList translateFromGraal(Context context, Value value,
                                                      IEvaluationExceptionFactory exceptionFactory)
            throws EvaluationException {
        List<IValue> values = Lists.newArrayList();
        long length = value.getArraySize();
        for (long i = 0; i < length; i++) {
            values.add(ValueTranslators.REGISTRY.translateFromGraal(
                    context, value.getArrayElement(i), exceptionFactory));
        }
        return ValueTypeList.ValueList.ofList(ValueTypes.CATEGORY_ANY, values);
    }

    @Override
    public NBTBase translateToNbt(Context context, ValueTypeList.ValueList value,
                                  IEvaluationExceptionFactory exceptionFactory) throws EvaluationException {
        if (value.getRawValue().isInfinite()) {
            throw exceptionFactory.createError(
                    "valuetype.integratedscripting.error.translation.list_infinite");
        }

        NBTTagList tag = new NBTTagList();
        for (IValue subValue : (Iterable<IValue>) value.getRawValue()) {
            NBTBase subTag = ValueTranslators.REGISTRY.translateToNbtTag(context, subValue, exceptionFactory);
            // A null NBT value can not be represented inside a list; use an empty compound.
            tag.appendTag(subTag == null ? new NBTTagCompound() : subTag);
        }
        return tag;
    }

}
