package com.mpp.integratedscripting.callback;

import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeBase;
import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;

/**
 * Bridge for object types whose 1.12.2 {@code serialize} result is SNBT
 * (item stacks, fluid stacks, ingredients, recipes).
 *
 * @author rubensworks
 */
public class SnbtObjectValueBridge<V extends IValue> implements IObjectValueBridge<V> {

    @Override
    public NBTBase toNbt(ValueObjectTypeBase<V> valueType, V value, IEvaluationExceptionFactory exceptionFactory)
            throws EvaluationException {
        String serialized = valueType.serialize(value);
        if (serialized == null || serialized.isEmpty()) {
            return new NBTTagCompound();
        }
        try {
            return JsonToNBT.getTagFromJson(serialized);
        } catch (Exception e) {
            throw exceptionFactory.createError(L10NHelpers.localize(
                    "valuetype.integratedscripting.error.object_serialize",
                    L10NHelpers.localize(valueType.getTranslationKey()), serialized));
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public V fromNbt(ValueObjectTypeBase<V> valueType, NBTBase tag, IEvaluationExceptionFactory exceptionFactory)
            throws EvaluationException {
        if (!(tag instanceof NBTTagCompound)) {
            throw exceptionFactory.createError(L10NHelpers.localize(
                    "valuetype.integratedscripting.error.object_not_compound",
                    L10NHelpers.localize(valueType.getTranslationKey())));
        }
        return valueType.deserialize(tag.toString());
    }

}
