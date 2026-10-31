package com.mpp.integratedscripting.evaluate.translation.translator;

import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslator;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagDouble;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeDouble;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

/**
 * @author rubensworks
 */
public class ValueTranslatorDouble implements IValueTranslator<ValueTypeDouble.ValueDouble> {

    @Override
    public IValueType<?> getValueType() {
        return ValueTypes.DOUBLE;
    }

    @Override
    public boolean canHandleGraalValue(Value value) {
        return value.fitsInDouble();
    }

    @Override
    public boolean canTranslateNbt() {
        return true;
    }

    @Override
    public Value translateToGraal(Context context, ValueTypeDouble.ValueDouble value,
                                  IEvaluationExceptionFactory exceptionFactory) {
        return context.asValue(value.getRawValue());
    }

    @Override
    public ValueTypeDouble.ValueDouble translateFromGraal(Context context, Value value,
                                                          IEvaluationExceptionFactory exceptionFactory) {
        return ValueTypeDouble.ValueDouble.of(value.asDouble());
    }

    @Override
    public NBTBase translateToNbt(Context context, ValueTypeDouble.ValueDouble value,
                                  IEvaluationExceptionFactory exceptionFactory) {
        return new NBTTagDouble(value.getRawValue());
    }

}
