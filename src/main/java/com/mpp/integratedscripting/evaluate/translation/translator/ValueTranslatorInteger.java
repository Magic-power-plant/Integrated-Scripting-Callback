package com.mpp.integratedscripting.evaluate.translation.translator;

import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslator;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagInt;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeInteger;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

/**
 * @author rubensworks
 */
public class ValueTranslatorInteger implements IValueTranslator<ValueTypeInteger.ValueInteger> {

    @Override
    public IValueType<?> getValueType() {
        return ValueTypes.INTEGER;
    }

    @Override
    public boolean canHandleGraalValue(Value value) {
        return value.fitsInInt();
    }

    @Override
    public boolean canTranslateNbt() {
        return true;
    }

    @Override
    public Value translateToGraal(Context context, ValueTypeInteger.ValueInteger value,
                                  IEvaluationExceptionFactory exceptionFactory) {
        return context.asValue(value.getRawValue());
    }

    @Override
    public ValueTypeInteger.ValueInteger translateFromGraal(Context context, Value value,
                                                            IEvaluationExceptionFactory exceptionFactory) {
        return ValueTypeInteger.ValueInteger.of(value.asInt());
    }

    @Override
    public NBTBase translateToNbt(Context context, ValueTypeInteger.ValueInteger value,
                                  IEvaluationExceptionFactory exceptionFactory) {
        return new NBTTagInt(value.getRawValue());
    }

}
