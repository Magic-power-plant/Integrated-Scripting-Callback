package com.mpp.integratedscripting.api.evaluate.translation;

import net.minecraft.nbt.NBTBase;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

/**
 * Translates ID values to and from Graal values.
 *
 * <p>1.12.2 difference (docs/PORTING-1.12.2.md §7.11): {@code translateToNbt}
 * returns the 1.12 {@link NBTBase} type, so that scalar NBT members and NBT
 * list elements can be represented. ID's NBT value type itself can only wrap a
 * {@link net.minecraft.nbt.NBTTagCompound}; see {@code callback.NbtBridge}.</p>
 *
 * @author rubensworks
 */
public interface IValueTranslator<V extends IValue> {

    public IValueType<?> getValueType();

    public boolean canHandleGraalValue(Value value);

    public boolean canTranslateNbt();

    public Value translateToGraal(Context context, V value, IEvaluationExceptionFactory exceptionFactory) throws EvaluationException;

    public V translateFromGraal(Context context, Value value, IEvaluationExceptionFactory exceptionFactory) throws EvaluationException;

    public NBTBase translateToNbt(Context context, V value, IEvaluationExceptionFactory exceptionFactory) throws EvaluationException;

}
