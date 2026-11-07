package com.mpp.integratedscripting.callback;

import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.core.evaluate.value.AddressedMemoryValues;
import com.mpp.integratedscripting.core.evaluate.value.ValueAddressedMemory;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeBase;

import javax.annotation.Nullable;

/**
 * Bridge between an addressed memory value and its script-visible identity
 * member.
 *
 * <p>Scripts see object values as proxy objects with one identifying member. For
 * an addressed memory that member is {@code id_memory}, holding
 * {@code { x, y, z, dim }} as an NBT compound. That also makes the value
 * constructible from a script literal, which is a second way to reach a memory
 * block:</p>
 *
 * <pre>
 * idContext.ops.addressedMemoryGet({ id_memory: { x: 4, y: 64, z: 4, dim: 0 } }, "a")
 * </pre>
 *
 * @author rubensworks
 */
public class AddressedMemoryObjectValueBridge implements IObjectValueBridge<ValueAddressedMemory> {

    public static final String KEY_X = "x";
    public static final String KEY_Y = "y";
    public static final String KEY_Z = "z";
    public static final String KEY_DIM = "dim";

    @Override
    public NBTBase toNbt(ValueObjectTypeBase<ValueAddressedMemory> valueType, ValueAddressedMemory value,
                         IEvaluationExceptionFactory exceptionFactory) {
        NBTTagCompound tag = new NBTTagCompound();
        DimPos position = value.getPosition();
        if (position != null) {
            BlockPos pos = position.getBlockPos();
            tag.setInteger(KEY_X, pos.getX());
            tag.setInteger(KEY_Y, pos.getY());
            tag.setInteger(KEY_Z, pos.getZ());
            tag.setInteger(KEY_DIM, position.getDimensionId());
        }
        return tag;
    }

    @Override
    public ValueAddressedMemory fromNbt(ValueObjectTypeBase<ValueAddressedMemory> valueType, NBTBase tag,
                                        IEvaluationExceptionFactory exceptionFactory) throws EvaluationException {
        if (!(tag instanceof NBTTagCompound)) {
            throw exceptionFactory.createError(L10NHelpers.localize(
                    "valuetype.integratedscripting.error.object_not_compound",
                    L10NHelpers.localize(valueType.getTranslationKey())));
        }
        NBTTagCompound compound = (NBTTagCompound) tag;
        if (!compound.hasKey(KEY_X) || !compound.hasKey(KEY_Y) || !compound.hasKey(KEY_Z)) {
            return valueType.getDefault();
        }
        return AddressedMemoryValues.of(DimPos.of(
                compound.hasKey(KEY_DIM) ? compound.getInteger(KEY_DIM) : 0,
                new BlockPos(compound.getInteger(KEY_X), compound.getInteger(KEY_Y), compound.getInteger(KEY_Z))));
    }

    /**
     * @param position A memory position, may be null.
     * @return The identity compound a script would see for it, for diagnostics.
     */
    public static NBTTagCompound identity(@Nullable DimPos position) {
        NBTTagCompound tag = new NBTTagCompound();
        if (position != null) {
            tag.setInteger(KEY_X, position.getBlockPos().getX());
            tag.setInteger(KEY_Y, position.getBlockPos().getY());
            tag.setInteger(KEY_Z, position.getBlockPos().getZ());
            tag.setInteger(KEY_DIM, position.getDimensionId());
        }
        return tag;
    }

}
