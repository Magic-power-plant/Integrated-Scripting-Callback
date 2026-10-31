package com.mpp.integratedscripting.callback;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeBase;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeEntity.ValueEntity;
import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;

/**
 * Bridge for entity values. 1.12.2 serializes an entity value as its UUID
 * string, so it is exposed to scripts as {@code { uuid: "..." }}. An entity
 * value without a UUID becomes an empty object (docs/PORTING-1.12.2.md §5.5.1).
 *
 * @author rubensworks
 */
public class EntityObjectValueBridge implements IObjectValueBridge<ValueEntity> {

    public static final String KEY_UUID = "uuid";

    @Override
    public NBTBase toNbt(ValueObjectTypeBase<ValueEntity> valueType, ValueEntity value,
                         IEvaluationExceptionFactory exceptionFactory) {
        NBTTagCompound tag = new NBTTagCompound();
        String serialized = valueType.serialize(value);
        if (serialized != null && !serialized.isEmpty()) {
            tag.setTag(KEY_UUID, new NBTTagString(serialized));
        }
        return tag;
    }

    @Override
    public ValueEntity fromNbt(ValueObjectTypeBase<ValueEntity> valueType, NBTBase tag,
                               IEvaluationExceptionFactory exceptionFactory) throws EvaluationException {
        if (!(tag instanceof NBTTagCompound)) {
            throw exceptionFactory.createError(L10NHelpers.localize(
                    "valuetype.integratedscripting.error.object_not_compound",
                    L10NHelpers.localize(valueType.getTranslationKey())));
        }
        NBTTagCompound compound = (NBTTagCompound) tag;
        String uuid = compound.hasKey(KEY_UUID) ? compound.getString(KEY_UUID) : "";
        // ValueObjectTypeEntity#deserialize tolerates an empty/invalid UUID.
        return valueType.deserialize(uuid);
    }

}
