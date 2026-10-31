package com.mpp.integratedscripting.api.evaluate.translation;

import net.minecraft.nbt.NBTBase;
import org.cyclops.cyclopscore.init.IRegistry;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import javax.annotation.Nullable;

/**
 * Registry for {@link IValueTranslator}'s.
 *
 * <p>1.12.2 differences (docs/PORTING-1.12.2.md §7.11): NBT is {@link NBTBase},
 * and {@link #translateToNbtTag} is added for nested translation.</p>
 *
 * @author rubensworks
 */
public interface IValueTranslatorRegistry extends IRegistry {

    public void register(IValueTranslator translator);

    @Nullable
    public <V extends IValue> IValueTranslator getValueTypeTranslator(IValueType<V> valueType);

    public <V extends IValue> Value translateToGraal(Context context, V value, IEvaluationExceptionFactory exceptionFactory) throws EvaluationException;

    @Nullable
    public IValueTranslator getScriptValueTranslator(Value scriptValue);

    public <V extends IValue> V translateFromGraal(Context context, Value value, IEvaluationExceptionFactory exceptionFactory) throws EvaluationException;

    /**
     * Translate a value to a raw NBT tag, without the compound-only restriction of
     * ID's NBT value type.
     * @param context A Graal context.
     * @param value A value.
     * @param exceptionFactory The exception factory.
     * @param <V> The value type.
     * @return The raw NBT tag.
     * @throws EvaluationException If the value can not be translated to NBT.
     */
    public <V extends IValue> NBTBase translateToNbtTag(Context context, V value, IEvaluationExceptionFactory exceptionFactory) throws EvaluationException;

    /**
     * Translate a value to an NBT compound, as required by ID's NBT value type.
     * Non-compound values fail unless {@code GeneralConfig.nbtScalarWrapper} is enabled.
     * @param context A Graal context.
     * @param value A value.
     * @param exceptionFactory The exception factory.
     * @param <V> The value type.
     * @return The NBT compound.
     * @throws EvaluationException If the value can not be translated to a compound.
     */
    public <V extends IValue> net.minecraft.nbt.NBTTagCompound translateToNbt(Context context, V value, IEvaluationExceptionFactory exceptionFactory) throws EvaluationException;

}
