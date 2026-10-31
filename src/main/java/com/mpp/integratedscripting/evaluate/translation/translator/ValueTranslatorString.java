package com.mpp.integratedscripting.evaluate.translation.translator;

import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslator;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagString;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeString;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

/**
 * @author rubensworks
 */
public class ValueTranslatorString implements IValueTranslator<ValueTypeString.ValueString> {

    @Override
    public IValueType<?> getValueType() {
        return ValueTypes.STRING;
    }

    @Override
    public boolean canHandleGraalValue(Value value) {
        return value.isString();
    }

    @Override
    public boolean canTranslateNbt() {
        return true;
    }

    @Override
    public Value translateToGraal(Context context, ValueTypeString.ValueString value,
                                  IEvaluationExceptionFactory exceptionFactory) {
        return context.asValue(value.getRawValue());
    }

    @Override
    public ValueTypeString.ValueString translateFromGraal(Context context, Value value,
                                                          IEvaluationExceptionFactory exceptionFactory) {
        return ValueTypeString.ValueString.of(value.asString());
    }

    @Override
    public NBTBase translateToNbt(Context context, ValueTypeString.ValueString value,
                                  IEvaluationExceptionFactory exceptionFactory) {
        return new NBTTagString(value.getRawValue());
    }

}
