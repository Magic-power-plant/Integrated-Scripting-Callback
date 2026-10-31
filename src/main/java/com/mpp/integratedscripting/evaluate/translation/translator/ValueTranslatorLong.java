package com.mpp.integratedscripting.evaluate.translation.translator;

import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslator;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagLong;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeLong;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

/**
 * @author rubensworks
 */
public class ValueTranslatorLong implements IValueTranslator<ValueTypeLong.ValueLong> {

    @Override
    public IValueType<?> getValueType() {
        return ValueTypes.LONG;
    }

    @Override
    public boolean canHandleGraalValue(Value value) {
        return value.fitsInLong();
    }

    @Override
    public boolean canTranslateNbt() {
        return true;
    }

    @Override
    public Value translateToGraal(Context context, ValueTypeLong.ValueLong value,
                                  IEvaluationExceptionFactory exceptionFactory) {
        return context.asValue(value.getRawValue());
    }

    @Override
    public ValueTypeLong.ValueLong translateFromGraal(Context context, Value value,
                                                      IEvaluationExceptionFactory exceptionFactory) {
        return ValueTypeLong.ValueLong.of(value.asLong());
    }

    @Override
    public NBTBase translateToNbt(Context context, ValueTypeLong.ValueLong value,
                                  IEvaluationExceptionFactory exceptionFactory) {
        return new NBTTagLong(value.getRawValue());
    }

}
