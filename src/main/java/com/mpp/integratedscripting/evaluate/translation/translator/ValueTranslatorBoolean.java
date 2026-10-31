package com.mpp.integratedscripting.evaluate.translation.translator;

import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslator;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeBoolean;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

/**
 * @author rubensworks
 */
public class ValueTranslatorBoolean implements IValueTranslator<ValueTypeBoolean.ValueBoolean> {

    @Override
    public IValueType<?> getValueType() {
        return ValueTypes.BOOLEAN;
    }

    @Override
    public boolean canHandleGraalValue(Value value) {
        return value.isBoolean();
    }

    @Override
    public boolean canTranslateNbt() {
        return true;
    }

    @Override
    public Value translateToGraal(Context context, ValueTypeBoolean.ValueBoolean value,
                                  IEvaluationExceptionFactory exceptionFactory) {
        // Values are bound to a Context, so they must not be cached across contexts.
        return context.asValue(value.getRawValue());
    }

    @Override
    public ValueTypeBoolean.ValueBoolean translateFromGraal(Context context, Value value,
                                                            IEvaluationExceptionFactory exceptionFactory) {
        return ValueTypeBoolean.ValueBoolean.of(value.asBoolean());
    }

    @Override
    public NBTBase translateToNbt(Context context, ValueTypeBoolean.ValueBoolean value,
                                  IEvaluationExceptionFactory exceptionFactory) {
        return new NBTTagByte((byte) (value.getRawValue() ? 1 : 0));
    }

}
