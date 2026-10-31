package com.mpp.integratedscripting.callback;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeNbt;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;
import com.mpp.GeneralConfig;
import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.evaluate.translation.ValueTranslators;
import org.graalvm.polyglot.Context;

/**
 * 1.12.2-only: adapts raw NBT tags to ID's NBT value type.
 *
 * <p>ID 1.12.2's {@code ValueTypeNbt.ValueNbt} can only hold an
 * {@link NBTTagCompound}. Scalar tags are therefore rejected by default; when
 * {@link GeneralConfig#nbtScalarWrapper} is enabled they are wrapped as
 * {@code {"value": <tag>}} (docs/PORTING-1.12.2.md §5.5.2).</p>
 *
 * @author rubensworks
 */
public final class NbtBridge {

    public static final String WRAPPER_KEY = "value";

    private NbtBridge() {
    }

    /**
     * Translate any ID value to an ID NBT value, applying the scalar policy.
     * @param context A Graal context.
     * @param value The value to translate.
     * @param exceptionFactory The exception factory.
     * @return The NBT value.
     * @throws EvaluationException If the value is not a compound and the scalar wrapper is disabled.
     */
    public static ValueTypeNbt.ValueNbt toValueNbt(Context context, IValue value,
                                                   IEvaluationExceptionFactory exceptionFactory)
            throws EvaluationException {
        NBTBase tag = ValueTranslators.REGISTRY.translateToNbtTag(context, value, exceptionFactory);
        if (tag instanceof NBTTagCompound) {
            return ValueTypeNbt.ValueNbt.of((NBTTagCompound) tag);
        }
        if (tag == null) {
            return ValueTypeNbt.ValueNbt.of((NBTTagCompound) null);
        }
        if (GeneralConfig.nbtScalarWrapper) {
            NBTTagCompound wrapper = new NBTTagCompound();
            wrapper.setTag(WRAPPER_KEY, tag);
            return ValueTypeNbt.ValueNbt.of(wrapper);
        }
        throw exceptionFactory.createError(L10NHelpers.localize(
                "valuetype.integratedscripting.error.translation.nbt_not_compound",
                L10NHelpers.localize(value.getType().getTranslationKey())));
    }

    /**
     * Unwrap the value if it was produced by the scalar wrapper.
     * @param valueNbt An NBT value.
     * @return The raw tag, or null for a null NBT value.
     */
    public static NBTBase unwrap(ValueTypeNbt.ValueNbt valueNbt) {
        NBTTagCompound tag = valueNbt.getRawValue();
        if (tag == null) {
            return null;
        }
        if (GeneralConfig.nbtScalarWrapper
                && tag.getKeySet().size() == 1
                && tag.hasKey(WRAPPER_KEY)) {
            return tag.getTag(WRAPPER_KEY);
        }
        return tag;
    }

    /**
     * @param value A value.
     * @return If the value is the ID NBT value type.
     */
    public static boolean isNbtValue(IValue value) {
        return value != null && value.getType() == ValueTypes.NBT;
    }

}
