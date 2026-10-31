package com.mpp.integratedscripting.callback;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeBase;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeBlock.ValueBlock;
import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;

/**
 * Bridge for block values. 1.12.2 serializes a block state as
 * {@code registryName$meta}, which is not SNBT, so it is exposed to scripts as
 * {@code { name: "minecraft:stone", meta: 0 }} (docs/PORTING-1.12.2.md §5.5.1).
 *
 * @author rubensworks
 */
public class BlockObjectValueBridge implements IObjectValueBridge<ValueBlock> {

    public static final String KEY_NAME = "name";
    public static final String KEY_NAME_LEGACY = "Name";
    public static final String KEY_META = "meta";

    @Override
    public NBTBase toNbt(ValueObjectTypeBase<ValueBlock> valueType, ValueBlock value,
                         IEvaluationExceptionFactory exceptionFactory) {
        NBTTagCompound tag = new NBTTagCompound();
        String serialized = valueType.serialize(value);
        if (serialized == null || serialized.isEmpty()) {
            return tag;
        }
        String[] parts = serialized.split("\\$");
        tag.setTag(KEY_NAME, new NBTTagString(parts[0]));
        int meta = 0;
        if (parts.length > 1) {
            try {
                meta = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                meta = 0;
            }
        }
        tag.setInteger(KEY_META, meta);
        return tag;
    }

    @Override
    public ValueBlock fromNbt(ValueObjectTypeBase<ValueBlock> valueType, NBTBase tag,
                              IEvaluationExceptionFactory exceptionFactory) throws EvaluationException {
        if (!(tag instanceof NBTTagCompound)) {
            throw exceptionFactory.createError(L10NHelpers.localize(
                    "valuetype.integratedscripting.error.object_not_compound",
                    L10NHelpers.localize(valueType.getTranslationKey())));
        }
        NBTTagCompound compound = (NBTTagCompound) tag;
        String name = null;
        if (compound.hasKey(KEY_NAME)) {
            name = compound.getString(KEY_NAME);
        } else if (compound.hasKey(KEY_NAME_LEGACY)) {
            name = compound.getString(KEY_NAME_LEGACY);
        }
        if (name == null || name.isEmpty()) {
            return valueType.deserialize("");
        }
        int meta = compound.hasKey(KEY_META) ? compound.getInteger(KEY_META) : 0;
        return valueType.deserialize(name + "$" + meta);
    }

}
