package com.mpp.integratedscripting.evaluate.translation.translator;

import com.google.common.collect.Sets;
import com.mpp.GeneralConfig;
import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslator;
import com.mpp.integratedscripting.callback.NbtBridge;
import com.mpp.integratedscripting.evaluate.translation.ValueTranslators;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagEnd;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagLongArray;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.operator.IOperator;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeNbt;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Translates ID's NBT value type to and from Graal values.
 *
 * <p>1.12.2 differences (docs/PORTING-1.12.2.md §5.5.2): ID's NBT value can only
 * wrap a {@link NBTTagCompound}, so a null compound represents the null NBT
 * value, and top-level scalars are handled by {@link NbtBridge} instead.</p>
 *
 * @author rubensworks
 */
public class ValueTranslatorNbt implements IValueTranslator<ValueTypeNbt.ValueNbt> {

    public static final String NBT_END_KEY = "nbt_end";

    @Override
    public IValueType<?> getValueType() {
        return ValueTypes.NBT;
    }

    @Override
    public boolean canHandleGraalValue(Value value) {
        return value.hasMembers();
    }

    @Override
    public boolean canTranslateNbt() {
        return true;
    }

    @Override
    public Value translateToGraal(Context context, ValueTypeNbt.ValueNbt value,
                                  IEvaluationExceptionFactory exceptionFactory) throws EvaluationException {
        NBTTagCompound tag = value.getRawValue();
        if (tag == null) {
            return context.asValue(null);
        }
        // Unwrap a compound produced by NbtBridge for a scalar, so scripts see the
        // original scalar again (§5.5.2).
        if (GeneralConfig.nbtScalarWrapper
                && tag.getKeySet().size() == 1
                && tag.hasKey(NbtBridge.WRAPPER_KEY)) {
            return translateTag(context, tag.getTag(NbtBridge.WRAPPER_KEY), exceptionFactory);
        }
        return translateTag(context, tag, exceptionFactory);
    }

    /**
     * Translate any NBT tag to a Graal value. Scalars become plain script values,
     * compounds become proxy objects and lists become arrays.
     * @param context A Graal context.
     * @param tag The tag, may be null.
     * @param exceptionFactory The exception factory.
     * @return The Graal value.
     * @throws EvaluationException If the tag type is unknown.
     */
    public Value translateTag(Context context, @Nullable NBTBase tag,
                              IEvaluationExceptionFactory exceptionFactory) throws EvaluationException {
        if (tag == null || tag instanceof NBTTagEnd) {
            return context.eval("js", "exports = { '" + NBT_END_KEY + "': true }");
        }
        if (tag instanceof NBTTagByte) {
            return context.asValue((int) ((NBTTagByte) tag).getByte());
        }
        if (tag instanceof NBTTagShort) {
            return context.asValue((int) ((NBTTagShort) tag).getShort());
        }
        if (tag instanceof NBTTagInt) {
            return context.asValue(((NBTTagInt) tag).getInt());
        }
        if (tag instanceof NBTTagLong) {
            return context.asValue(((NBTTagLong) tag).getLong());
        }
        if (tag instanceof NBTTagFloat) {
            return context.asValue((double) ((NBTTagFloat) tag).getFloat());
        }
        if (tag instanceof NBTTagDouble) {
            return context.asValue(((NBTTagDouble) tag).getDouble());
        }
        if (tag instanceof NBTTagByteArray) {
            return context.asValue(((NBTTagByteArray) tag).getByteArray());
        }
        if (tag instanceof NBTTagString) {
            return context.asValue(((NBTTagString) tag).getString());
        }
        if (tag instanceof NBTTagList) {
            NBTTagList listTag = (NBTTagList) tag;
            List<Value> list = new ArrayList<>();
            for (int i = 0; i < listTag.tagCount(); i++) {
                list.add(translateTag(context, listTag.get(i), exceptionFactory));
            }
            return context.asValue(list);
        }
        if (tag instanceof NBTTagCompound) {
            return translateCompoundTag(context, (NBTTagCompound) tag, exceptionFactory, null, null);
        }
        if (tag instanceof NBTTagIntArray) {
            return context.asValue(((NBTTagIntArray) tag).getIntArray());
        }
        if (tag instanceof NBTTagLongArray) {
            return context.asValue(getLongArray((NBTTagLongArray) tag));
        }
        throw exceptionFactory.createError(
                "valuetype.integratedscripting.error.translation.nbt_unknown",
                NBTBase.getTagTypeName(tag.getId()));
    }

    /**
     * 1.12.2's {@link NBTTagLongArray} has no public accessor for its payload.
     * Locate the backing {@code long[]} field by type so that this keeps working
     * after RFG reobfuscates the field name.
     */
    private static long[] getLongArray(NBTTagLongArray tag) {
        for (java.lang.reflect.Field field : NBTTagLongArray.class.getDeclaredFields()) {
            if (field.getType() == long[].class) {
                try {
                    field.setAccessible(true);
                    long[] data = (long[]) field.get(tag);
                    return data == null ? new long[0] : data;
                } catch (IllegalAccessException e) {
                    return new long[0];
                }
            }
        }
        return new long[0];
    }

    public Value translateCompoundTag(Context context, NBTTagCompound tag,
                                      IEvaluationExceptionFactory exceptionFactory,
                                      @Nullable Map<String, IOperator> methods,
                                      @Nullable IValue value) {
        return context.asValue(new NbtCompoundTagProxyObject(
                context, exceptionFactory, tag, methods, value));
    }

    @Override
    public ValueTypeNbt.ValueNbt translateFromGraal(Context context, Value value,
                                                    IEvaluationExceptionFactory exceptionFactory)
            throws EvaluationException {
        // Unwrap the value if it was translated in the opposite direction before.
        if (value.isProxyObject()) {
            try {
                NbtCompoundTagProxyObject proxy = value.asProxyObject();
                return ValueTypeNbt.ValueNbt.of(proxy.getTag());
            } catch (ClassCastException classCastException) {
                // Fallback to the generic case below.
            }
        }

        Set<String> memberKeys = value.getMemberKeys();
        if (memberKeys.equals(Sets.newHashSet(NBT_END_KEY))) {
            return ValueTypeNbt.ValueNbt.of((NBTTagCompound) null);
        }

        // In all other cases, assume we have a compound tag.
        NBTTagCompound tag = new NBTTagCompound();
        for (String memberKey : memberKeys) {
            Value memberValue = value.getMember(memberKey);
            if (!memberValue.isNull()) {
                IValue subValue = ValueTranslators.REGISTRY.translateFromGraal(
                        context, memberValue, exceptionFactory);
                NBTBase subTag = ValueTranslators.REGISTRY.translateToNbtTag(
                        context, subValue, exceptionFactory);
                if (subTag != null) {
                    tag.setTag(memberKey, subTag);
                }
            }
        }
        return ValueTypeNbt.ValueNbt.of(tag);
    }

    @Override
    public NBTBase translateToNbt(Context context, ValueTypeNbt.ValueNbt value,
                                  IEvaluationExceptionFactory exceptionFactory) {
        return value.getRawValue();
    }

}
