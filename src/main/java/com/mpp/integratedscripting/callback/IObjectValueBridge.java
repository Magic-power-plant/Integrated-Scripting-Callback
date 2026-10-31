package com.mpp.integratedscripting.callback;

import net.minecraft.nbt.NBTBase;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeBase;
import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;

/**
 * 1.12.2-only: bidirectional bridge between ID object values and raw NBT.
 *
 * <p>The 1.20 reference implementation assumes {@code ValueObjectTypeBase#serialize}
 * returns an NBT tag. In 1.12.2 it returns a plain {@link String} whose format
 * depends on the value type (SNBT for item stacks, {@code registryName$meta} for
 * blocks, a UUID string for entities), so one bridge is registered per object
 * type (docs/PORTING-1.12.2.md §5.5.1).</p>
 *
 * @author rubensworks
 */
public interface IObjectValueBridge<V extends IValue> {

    NBTBase toNbt(ValueObjectTypeBase<V> valueType, V value, IEvaluationExceptionFactory exceptionFactory)
            throws EvaluationException;

    V fromNbt(ValueObjectTypeBase<V> valueType, NBTBase tag, IEvaluationExceptionFactory exceptionFactory)
            throws EvaluationException;

}
