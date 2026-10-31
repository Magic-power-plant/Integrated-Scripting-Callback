package com.mpp.integratedscripting.evaluate.translation.translator;

import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.callback.NbtBridge;
import com.mpp.integratedscripting.evaluate.translation.ValueTranslators;
import lombok.SneakyThrows;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.operator.IOperator;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.api.evaluate.variable.IVariable;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeNbt;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeOperator;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;
import org.cyclops.integrateddynamics.core.evaluate.variable.Variable;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import javax.annotation.Nullable;

/**
 * A Graal executable proxy for ID operator values.
 *
 * @author rubensworks
 */
public class OperatorProxyExecutable implements ProxyExecutable {

    private final Context context;
    private final ValueTypeOperator.ValueOperator value;
    private final IEvaluationExceptionFactory exceptionFactory;

    public OperatorProxyExecutable(Context context, ValueTypeOperator.ValueOperator value,
                                   IEvaluationExceptionFactory exceptionFactory) {
        this.context = context;
        this.value = value;
        this.exceptionFactory = exceptionFactory;
    }

    public ValueTypeOperator.ValueOperator getValue() {
        return value;
    }

    @SneakyThrows
    @Override
    public Object execute(Value... args) {
        IOperator operator = value.getRawValue();
        IValueType[] inputTypes = operator.getInputTypes();
        IVariable<IValue>[] variables = new IVariable[args.length];
        for (int i = 0; i < args.length; i++) {
            variables[i] = new Variable<>(translateArgument(
                    args[i], i < inputTypes.length ? inputTypes[i] : null));
        }
        return ValueTranslators.REGISTRY.translateToGraal(
                context, operator.evaluate(variables), exceptionFactory);
    }

    /**
     * Translate a single argument of this operator.
     *
     * <p>1.12.2's NBT value type can only hold a compound, so scalar script
     * values passed to an NBT-typed parameter go through {@link NbtBridge}
     * (which applies the {@code nbtScalarWrapper} policy).</p>
     *
     * @param arg A script value.
     * @param inputType The value type the operator expects for this argument, if known.
     * @return The translated value.
     * @throws EvaluationException If translation failed.
     */
    protected IValue translateArgument(Value arg, @Nullable IValueType<?> inputType)
            throws EvaluationException {
        if (inputType == ValueTypes.NBT) {
            if (arg.isNull()) {
                return ValueTypeNbt.ValueNbt.of((net.minecraft.nbt.NBTTagCompound) null);
            }
            IValue translated = ValueTranslators.REGISTRY.translateFromGraal(context, arg, exceptionFactory);
            if (translated.getType() == ValueTypes.NBT) {
                return translated;
            }
            return NbtBridge.toValueNbt(context, translated, exceptionFactory);
        }
        return ValueTranslators.REGISTRY.translateFromGraal(context, arg, exceptionFactory);
    }

}
